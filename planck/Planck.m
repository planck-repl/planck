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

-(void)runEval:(NSString*)evalArg srcPath:(NSString*)srcPath outPath:(NSString*)outPath {
    
    BOOL useBundledOutput = NO;
    BOOL runAmblyReplServer = NO;
   
    if (!evalArg && isatty(fileno(stdin)) &&!runAmblyReplServer) {
        printf("cljs.user=> ");
        fflush(stdout);
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
            exit(1);
        }
    }
    
    ABYContextManager* contextManager = [[ABYContextManager alloc] initWithContext:JSGlobalContextCreate(NULL)
                                                           compilerOutputDirectory:outURL];
    [contextManager setUpConsoleLog];
    [contextManager setupGlobalContext];
    if (useBundledOutput) {
        [self setUpAmblyImportScriptInContext:contextManager.context];
    } else {
        [contextManager setUpAmblyImportScript];
    }
    
    NSString* mainJsFilePath = [[outURL URLByAppendingPathComponent:@"main" isDirectory:NO]
                                URLByAppendingPathExtension:@"js"].path;
    
    NSURL* googDirectory = [outURL URLByAppendingPathComponent:@"goog"];
    
    if (useBundledOutput) {
        [self bootstrapInContext:contextManager.context];
    } else {
        [contextManager bootstrapWithDepsFilePath:mainJsFilePath
                                     googBasePath:[[googDirectory URLByAppendingPathComponent:@"base" isDirectory:NO] URLByAppendingPathExtension:@"js"].path];
    }
    
    JSContext* context = [JSContext contextWithJSGlobalContextRef:contextManager.context];
    
    [self requireAppNamespaces:context];
    
#ifdef DEBUG
    BOOL debugBuild = YES;
#else
    BOOL debugBuild = NO;
#endif
    
    // TODO look into this. Without it thngs won't work.
    [context evaluateScript:@"var window = global;"];
    
    JSValue* initAppEnvFn = [self getValue:@"init-app-env" inNamespace:@"planck.core" fromContext:context];
    [initAppEnvFn callWithArguments:@[@{@"debug-build": @(debugBuild),
                                        @"src": srcPath ? srcPath : @"",
                                        @"out": outPath ? outPath : @""}]];
    
    JSValue* readEvalPrintFn = [self getValue:@"read-eval-print" inNamespace:@"planck.core" fromContext:context];
    NSAssert(!readEvalPrintFn.isUndefined, @"Could not find the read-eval-print function");
    
    JSValue* printPromptFn = [self getValue:@"print-prompt" inNamespace:@"planck.core" fromContext:context];
    NSAssert(!printPromptFn.isUndefined, @"Could not find the print-prompt function");
    
    JSValue* isReadableFn = [self getValue:@"is-readable?" inNamespace:@"planck.core" fromContext:context];
    NSAssert(!isReadableFn.isUndefined, @"Could not find the is-readable? function");
    
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
    
    [context evaluateScript:@"cljs.core.set_print_fn_BANG_.call(null,PLANCK_PRINT_FN);"];
    [context evaluateScript:@"cljs.core.set_print_err_fn_BANG_.call(null,PLANCK_PRINT_FN);"];
    

    context[@"PLANCK_PRINT_FN"] = ^(NSString *message) {
        if (!evalArg || ![message isEqualToString:@"nil"]) {
            printf("%s", message.cString);
        }
    };
    
    [context evaluateScript:@"cljs.core.set_print_fn_BANG_.call(null,PLANCK_PRINT_FN);"];

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
        if (evalArg) {
            [readEvalPrintFn callWithArguments:@[evalArg]];
        } else {
            NSString* input = nil;
            for (;;) {
                NSString* inputLine = [self getInput];
                
                if (input == nil) {
                    input = inputLine;
                } else {
                    input = [NSString stringWithFormat:@"%@\n%@", input, inputLine];
                }
                if ([input isEqualToString:@":cljs/quit"] || [input isEqualToString:@""]) {
                    break;
                }
                BOOL isReadable = [isReadableFn callWithArguments:@[input]].toBool;
                if (isReadable) {
                    [readEvalPrintFn callWithArguments:@[input]];
                    input = nil;
                    if (!evalArg && isatty(fileno(stdin))) {
                        [printPromptFn callWithArguments:@[]];
                        fflush(stdout);
                    }
                }
            }
        }
    }

}

-(NSString *) getInput
{
    NSFileHandle *input = [NSFileHandle fileHandleWithStandardInput];
    NSData *inputData = [input availableData];
    NSString *inputString = [[NSString alloc] initWithData: inputData encoding:NSUTF8StringEncoding];
    inputString = [inputString stringByTrimmingCharactersInSet: [NSCharacterSet newlineCharacterSet]];
    
    return inputString;
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

@end
