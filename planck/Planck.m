//
//  Planck.m
//  planck
//
//  Created by Mike Fikes on 7/16/15.
//  Copyright (c) 2015 FikesFarm. All rights reserved.
//

#include <stdio.h>

#import "Planck.h"
#import "ABYUtils.h"
#import "ABYContextManager.h"
#import "ABYServer.h"
#import "CljsRuntime.h"

@interface Planck()

@property (strong, nonatomic) CljsRuntime* cljsRuntime;

@end

@implementation Planck

-(NSString*)ensureSlash:(NSString*)s
{
    if (!s) {
        return nil;
    }
    if ([s hasSuffix:@"/"]) {
        return s;
    }
    return [s stringByAppendingString:@"/"];
}

-(NSString*)fullyQualify:(NSString*)path
{
    NSString* currentDirectory = [self ensureSlash:[NSFileManager defaultManager].currentDirectoryPath];
    if (path && ![path hasPrefix:@"/"]) {
        path = [currentDirectory stringByAppendingString:path];
    }
    return path;
}

-(void)runInit:(NSString*)initPath eval:(NSString*)evalArg srcPath:(NSString*)srcPath outPath:(NSString*)outPath verbose:(BOOL)verbose mainNsName:(NSString*)mainNsName repl:(BOOL)repl args:(NSArray*)args {
    
    BOOL useBundledOutput = YES;
    BOOL useSimpleOutput = NO;
    BOOL runAmblyReplServer = NO;
    BOOL measureTime = NO;
    
    NSDate *launchTime;
    if (measureTime) {
        NSLog(@"Launching");
        launchTime = [NSDate date];
    }
    
    initPath = [self fullyQualify:initPath];
    srcPath = [self ensureSlash:[self fullyQualify:srcPath]];
    outPath = [self ensureSlash:[self fullyQualify:outPath]];
    
    // For good UX, we display the first prompt immediately if we can
    BOOL initialPromptDisplayed = NO;
    if (!initPath && !evalArg && !mainNsName && (repl && args.count == 0)) {
        printf("cljs.user=> ");
        fflush(stdout);
        initialPromptDisplayed = YES;
    }
    
    if (runAmblyReplServer) {
        printf("Connect using script/repl\n");
        fflush(stdout);
    }
    
    self.cljsRuntime = [[CljsRuntime alloc] init];
    
    NSURL* outURL = [NSURL URLWithString:@"out"];
    
    if (outPath) {
        outURL = [NSURL URLWithString:outPath];
    }
    
    if (!useBundledOutput) {
        NSFileManager* fm = [NSFileManager defaultManager];
        if (![fm fileExistsAtPath:outURL.path isDirectory:nil]) {
            NSLog(@"ClojureScript compiler output directory not found at \"%@\".", outURL.path);
            NSLog(@"(Current working directory is \"%@\")", [fm currentDirectoryPath]);
            NSLog(@"If running from Xcode, set -o $PROJECT_DIR/planck-cljs/out");
            exit(1);
        }
    }
    
    ABYContextManager* contextManager = [[ABYContextManager alloc] initWithContext:JSGlobalContextCreate(NULL)
                                                           compilerOutputDirectory:outURL];
    [contextManager setUpConsoleLog];
    [contextManager setupGlobalContext];
    if (!useSimpleOutput) {
        if (useBundledOutput) {
            [self setUpAmblyImportScriptInContext:contextManager.context];
        } else {
            [contextManager setUpAmblyImportScript];
        }
    }
    
    NSString* mainJsFilePath = [[outURL URLByAppendingPathComponent:@"main" isDirectory:NO]
                                URLByAppendingPathExtension:@"js"].path;
    
    NSURL* googDirectory = [outURL URLByAppendingPathComponent:@"goog"];
    
    if (useSimpleOutput) {
        NSString *mainJsString;
        if (useBundledOutput) {
            mainJsString = [self.cljsRuntime getSourceForPath:@"main.js"];
        } else {
            mainJsString = [NSString stringWithContentsOfFile:mainJsFilePath encoding:NSUTF8StringEncoding error:nil];
        }
        NSAssert(mainJsString != nil, @"The main JavaScript text could not be loaded");
        [ABYUtils evaluateScript:mainJsString inContext:contextManager.context];
    } else {
        if (useBundledOutput) {
            [self bootstrapInContext:contextManager.context];
        } else {
            [contextManager bootstrapWithDepsFilePath:mainJsFilePath
                                         googBasePath:[[googDirectory URLByAppendingPathComponent:@"base" isDirectory:NO] URLByAppendingPathExtension:@"js"].path];
        }
    }
    if (measureTime) {
        NSDate* loadedTime = [NSDate date];
        NSTimeInterval executionTime = [loadedTime timeIntervalSinceDate:launchTime];
        NSLog(@"Loaded main JavaScript in %f s", executionTime);
    }
    
    JSContext* context = [JSContext contextWithJSGlobalContextRef:contextManager.context];
    
    if (!useSimpleOutput) {
        [self requireAppNamespaces:context];
    }
    
#ifdef DEBUG
    BOOL debugBuild = YES;
#else
    BOOL debugBuild = NO;
#endif
    
    // TODO look into this. Without it thngs won't work.
    [context evaluateScript:@"var window = global;"];
    
    JSValue* initAppEnvFn = [self getValue:@"init-app-env" inNamespace:@"planck.core" fromContext:context];
    [initAppEnvFn callWithArguments:@[@{@"debug-build": @(debugBuild),
                                        @"verbose": @(verbose)}]];
    
    JSValue* readEvalPrintFn = [self getValue:@"read-eval-print" inNamespace:@"planck.core" fromContext:context];
    NSAssert(!readEvalPrintFn.isUndefined, @"Could not find the read-eval-print function");
    
    JSValue* printPromptFn = [self getValue:@"print-prompt" inNamespace:@"planck.core" fromContext:context];
    NSAssert(!printPromptFn.isUndefined, @"Could not find the print-prompt function");
    
    JSValue* isReadableFn = [self getValue:@"is-readable?" inNamespace:@"planck.core" fromContext:context];
    NSAssert(!isReadableFn.isUndefined, @"Could not find the is-readable? function");
    
    context[@"PLANCK_LOAD"] = ^(NSString *path) {
        // First try in the srcPath
        
        NSString* fullPath = [NSURL URLWithString:path
                                    relativeToURL:[NSURL URLWithString:srcPath]].path;
        
        NSString* rv = [NSString stringWithContentsOfFile:fullPath
                                                 encoding:NSUTF8StringEncoding error:nil];
        // Now try in the outPath
        if (!rv) {
            if (useBundledOutput) {
                rv = [self.cljsRuntime getSourceForPath:path];
            } else {
                fullPath = [NSURL URLWithString:path
                                  relativeToURL:[NSURL URLWithString:outPath]].path;
                rv = [NSString stringWithContentsOfFile:fullPath
                                               encoding:NSUTF8StringEncoding error:nil];
            }
        }
        
        return rv;
    };
    
    context[@"PLANCK_READ_FILE"] = ^(NSString *file) {
        return [NSString stringWithContentsOfFile:file
                                         encoding:NSUTF8StringEncoding error:nil];
    };
    
    context[@"PLANCK_WRITE_FILE"] = ^(NSString *file, NSString* content) {
        [content writeToFile:file atomically:YES encoding:NSUTF8StringEncoding error:nil];
        return @"";
    };
    
    context[@"PLANCK_PRINT_FN"] = ^(NSString *message) {
        // supressing
    };
    
    const BOOL isTty = isatty(fileno(stdin));
    
    context[@"PLANCK_RAW_READ_STDIN"] = ^NSString*() {
        NSFileHandle *input = [NSFileHandle fileHandleWithStandardInput];
        NSData *inputData = [input readDataOfLength:isTty ? 1 : 1024];
        if (inputData.length) {
            return [[NSString alloc] initWithData:inputData encoding:NSUTF8StringEncoding];
        } else {
            return nil;
        }
    };
    
    context[@"PLANCK_RAW_WRITE_STDOUT"] = ^(NSString *s) {
        fprintf(stdout, "%s", [s cStringUsingEncoding:NSUTF8StringEncoding]);
    };
    
    context[@"PLANCK_RAW_FLUSH_STDOUT"] = ^() {
        fflush(stdout);
    };
    
    context[@"PLANCK_RAW_WRITE_STDERR"] = ^(NSString *s) {
        fprintf(stderr, "%s", [s cStringUsingEncoding:NSUTF8StringEncoding]);
    };
    
    context[@"PLANCK_RAW_FLUSH_STDERR"] = ^() {
        fflush(stderr);
    };
    
    [self setPrintFnsInContext:contextManager.context];

    // Set up the cljs.user namespace
    [context evaluateScript:@"goog.provide('cljs.user')"];
    [context evaluateScript:@"goog.require('cljs.core')"];
    
    if (runAmblyReplServer) {
        ABYServer* replServer = [[ABYServer alloc] initWithContext:contextManager.context
                                           compilerOutputDirectory:outURL];
        [replServer startListening];
        
        BOOL shouldKeepRunning = YES;
        NSRunLoop *theRL = [NSRunLoop currentRunLoop];
        while (shouldKeepRunning && [theRL runMode:NSDefaultRunLoopMode beforeDate:[NSDate     distantFuture]]);
        
    } else {
        context[@"PLANCK_PRINT_FN"] = ^(NSString *message) {
            if (![message isEqualToString:@"nil"]) {
                printf("%s", message.UTF8String);
            }
        };
        
        [self setPrintFnsInContext:contextManager.context];
        
        if (initPath) {
            [self executeScriptAtPath:initPath readEvalPrintFn:readEvalPrintFn];
        }
        
        if (evalArg) {
            
            NSDate *readyTime;
            if (measureTime) {
                readyTime = [NSDate date];
                NSTimeInterval executionTime = [readyTime timeIntervalSinceDate:launchTime];
                NSLog(@"Ready to eval in %f s", executionTime);
            }
            
            [readEvalPrintFn callWithArguments:@[evalArg]];
            
            if (measureTime) {
                NSDate *evalTime = [NSDate date];
                NSTimeInterval executionTime = [evalTime timeIntervalSinceDate:readyTime];
                NSLog(@"Evaluated in %f s", executionTime);
                
                NSDate *totalTime = [NSDate date];
                executionTime = [totalTime timeIntervalSinceDate:launchTime];
                NSLog(@"Total execcution in %f s", executionTime);
            }
        }
        
        if (mainNsName) {
        
            JSValue* runMainFn = [self getValue:@"run-main" inNamespace:@"planck.core" fromContext:context];
            [runMainFn callWithArguments:@[mainNsName, args]];
        
        } else if (!repl && args.count > 0) {
            
            // We treat the first arg as a path to a file to be executed (it can be '-', which means stdin)
            [self executeScriptAtPath:args[0] readEvalPrintFn:readEvalPrintFn];
        
        } else if (repl) {
            
            if (!initialPromptDisplayed) {
                printf("cljs.user=> ");
                fflush(stdout);
            }
            
            context[@"PLANCK_PRINT_FN"] = ^(NSString *message) {
                printf("%s", message.UTF8String);
            };
            
            [self setPrintFnsInContext:contextManager.context];
            
            NSString* input = nil;
            for (;;) {
                NSString* inputLine = [self getInput];
                if (inputLine == nil) {
                    printf("\n");
                    break;
                }
                
                if (input == nil) {
                    input = inputLine;
                } else {
                    input = [NSString stringWithFormat:@"%@\n%@", input, inputLine];
                }
                if ([input isEqualToString:@":cljs/quit"]) {
                    break;
                }
                
                BOOL isReadable = [isReadableFn callWithArguments:@[input]].toBool;
                if (isReadable) {
                    
                    NSCharacterSet *charSet = [NSCharacterSet whitespaceCharacterSet];
                    NSString *trimmedString = [input stringByTrimmingCharactersInSet:charSet];
                    if (![trimmedString isEqualToString:@""]) {
                        [readEvalPrintFn callWithArguments:@[input]];
                    } else {
                        printf("\n");
                    }
                    
                    input = nil;
                    
                    [printPromptFn callWithArguments:@[]];
                    fflush(stdout);
                    
                }
            }
        }
    }

}

-(NSString *) getInput
{
    NSFileHandle *input = [NSFileHandle fileHandleWithStandardInput];
    NSData *inputData = [input availableData];
    if (inputData.length) {
        NSString *inputString = [[NSString alloc] initWithData: inputData encoding:NSUTF8StringEncoding];
        return [inputString stringByTrimmingCharactersInSet: [NSCharacterSet newlineCharacterSet]];
    } else {
        return nil;
    }
}

-(void)requireAppNamespaces:(JSContext*)context
{
    [context evaluateScript:[NSString stringWithFormat:@"goog.require('%@');", [self munge:@"planck.core"]]];
}

- (JSValue*)getValue:(NSString*)name inNamespace:(NSString*)namespace fromContext:(JSContext*)context
{
    JSValue* namespaceValue = nil;
    for (NSString* namespaceElement in [namespace componentsSeparatedByString: @"."]) {
        if (namespaceValue) {
            namespaceValue = namespaceValue[[self munge:namespaceElement]];
        } else {
            namespaceValue = context[[self munge:namespaceElement]];
        }
    }
    
    return namespaceValue[[self munge:name]];
}

-(NSString*)munge:(NSString*)s
{
    return [[[s stringByReplacingOccurrencesOfString:@"-" withString:@"_"]
             stringByReplacingOccurrencesOfString:@"!" withString:@"_BANG_"]
            stringByReplacingOccurrencesOfString:@"?" withString:@"_QMARK_"];
}

-(void)setUpAmblyImportScriptInContext:(JSContextRef)context
{
    [ABYUtils installGlobalFunctionWithBlock:
     
     ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
         
         if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString)
         {
             JSStringRef pathStrRef = JSValueToStringCopy(ctx, argv[0], NULL);
             NSString* path = (__bridge_transfer NSString *) JSStringCopyCFString( kCFAllocatorDefault, pathStrRef );
             if ([path hasPrefix:@"goog/../"]) {
                 path = [path substringFromIndex:8];
             }
             JSStringRelease(pathStrRef);
             
             NSString* url = [@"file:///" stringByAppendingString:path];

             JSStringRef urlStringRef = JSStringCreateWithCFString((__bridge CFStringRef)url);
             
             NSError* error = nil;
             NSString* sourceText = [self.cljsRuntime getSourceForPath:path];
             
             if (!error && sourceText) {
                 
                 JSValueRef jsError = NULL;
                 JSStringRef javaScriptStringRef = JSStringCreateWithCFString((__bridge CFStringRef)sourceText);
                 JSEvaluateScript(ctx, javaScriptStringRef, NULL, urlStringRef, 0, &jsError);
                 JSStringRelease(javaScriptStringRef);
             }
             
             JSStringRelease(urlStringRef);
         }
         
         return JSValueMakeUndefined(ctx);
     }
                                        name:@"AMBLY_IMPORT_SCRIPT"
                                     argList:@"path"
                                   inContext:context];
    
}

-(void)bootstrapInContext:(JSContextRef)context
{
    // This implementation mirrors the bootstrapping code that is in -setup
    
    // Setup CLOSURE_IMPORT_SCRIPT
    [ABYUtils evaluateScript:@"CLOSURE_IMPORT_SCRIPT = function(src) { AMBLY_IMPORT_SCRIPT('goog/' + src); return true; }" inContext:context];
    
    // Load goog base
    NSString *baseScriptString = [self.cljsRuntime getSourceForPath:@"goog/base.js"];
    NSAssert(baseScriptString != nil, @"The goog base JavaScript text could not be loaded");
    [ABYUtils evaluateScript:baseScriptString inContext:context];
    
    // Load the deps file
    NSString *depsScriptString = [self.cljsRuntime getSourceForPath:@"main.js"];
    NSAssert(depsScriptString != nil, @"The deps JavaScript text could not be loaded");
    [ABYUtils evaluateScript:depsScriptString inContext:context];
    
    [ABYUtils evaluateScript:@"goog.isProvided_ = function(x) { return false; };" inContext:context];
    
    [ABYUtils evaluateScript:@"goog.require = function (name) { return CLOSURE_IMPORT_SCRIPT(goog.dependencies_.nameToPath[name]); };" inContext:context];
    
    [ABYUtils evaluateScript:@"goog.require('cljs.core');" inContext:context];
    
    // redef goog.require to track loaded libs
    [ABYUtils evaluateScript:@"cljs.core._STAR_loaded_libs_STAR_ = cljs.core.into.call(null, cljs.core.PersistentHashSet.EMPTY, [\"cljs.core\"]);\n"
     "goog.require = function (name, reload) {\n"
     "    if(!cljs.core.contains_QMARK_(cljs.core._STAR_loaded_libs_STAR_, name) || reload) {\n"
     "        var AMBLY_TMP = cljs.core.PersistentHashSet.EMPTY;\n"
     "        if (cljs.core._STAR_loaded_libs_STAR_) {\n"
     "            AMBLY_TMP = cljs.core._STAR_loaded_libs_STAR_;\n"
     "        }\n"
     "        cljs.core._STAR_loaded_libs_STAR_ = cljs.core.into.call(null, AMBLY_TMP, [name]);\n"
     "        CLOSURE_IMPORT_SCRIPT(goog.dependencies_.nameToPath[name]);\n"
     "    }\n"
     "};" inContext:context];
}

-(void)executeScriptAtPath:(NSString*)path readEvalPrintFn:(JSValue*)readEvalPrintFn
{
    NSString* source;
    if ([path isEqualToString:@"-"]) {
        source = [self getInput];
    } else {
        source = [NSString stringWithContentsOfFile:[self fullyQualify:path] encoding:NSUTF8StringEncoding error:nil];
    } if (source) {
        [readEvalPrintFn callWithArguments:@[source, @(NO)]];
    } else {
        NSLog(@"Could not read file at %@", path);
        exit(1);
    }
}

-(void)setPrintFnsInContext:(JSContextRef)context
{
    [ABYUtils evaluateScript:@"cljs.core.set_print_fn_BANG_.call(null,PLANCK_PRINT_FN);" inContext:context];
    [ABYUtils evaluateScript:@"cljs.core.set_print_err_fn_BANG_.call(null,PLANCK_PRINT_FN);" inContext:context];
}

@end
