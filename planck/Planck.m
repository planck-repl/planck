#include <stdio.h>

#import "Planck.h"
#import "ABYUtils.h"
#import "ABYContextManager.h"
#import "ABYServer.h"
#import "CljsRuntime.h"
#include "linenoise.h"

@interface Planck()

@property (strong, nonatomic) CljsRuntime* cljsRuntime;

@end

@implementation Planck

static NSCondition* javaScriptEngineReadyCondition;
static BOOL javaScriptEngineReady;
static JSValue* getCompletionsFn = nil;
static JSContext* context = nil;
static ABYContextManager* contextManager = nil;

void completion(const char *buf, linenoiseCompletions *lc) {
    
    if (getCompletionsFn) {
        NSArray* completions = [getCompletionsFn callWithArguments:@[[NSString stringWithCString:buf encoding:NSUTF8StringEncoding]]].toArray;
        for (NSString* completion in completions) {
            linenoiseAddCompletion(lc, [completion cStringUsingEncoding:NSUTF8StringEncoding]);
        }
    }
}

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

-(void)runInit:(NSString*)initPath
          eval:(NSString*)evalArg
       srcPath:(NSString*)srcPath
       verbose:(BOOL)verbose
    mainNsName:(NSString*)mainNsName
          repl:(BOOL)repl
       outPath:(NSString*)outPath
   amblyServer:(BOOL)amblyServer
 plainTerminal:(BOOL)plainTerminal
          args:(NSArray*)args; {
    
    BOOL useSimpleOutput = NO;
    BOOL measureTime = NO;
    
    NSDate *launchTime;
    if (measureTime) {
        NSLog(@"Launching");
        launchTime = [NSDate date];
    }
    
    initPath = [self fullyQualify:initPath];
    srcPath = [self ensureSlash:[self fullyQualify:srcPath]];
    outPath = [self ensureSlash:[self fullyQualify:outPath]];
        
    if (amblyServer) {
        printf("Connect with Ambly by using planck-cljs/script/repl\n");
        fflush(stdout);
    }
    
    self.cljsRuntime = [[CljsRuntime alloc] init];
    
    NSURL* outURL = [NSURL URLWithString:@"out"];
    
    if (outPath) {
        outURL = [NSURL URLWithString:outPath];
    }
    
    if (outPath) {
        NSFileManager* fm = [NSFileManager defaultManager];
        if (![fm fileExistsAtPath:outURL.path isDirectory:nil]) {
            NSLog(@"ClojureScript compiler output directory not found at \"%@\".", outURL.path);
            NSLog(@"(Current working directory is \"%@\")", [fm currentDirectoryPath]);
            NSLog(@"If running from Xcode, set -o $PROJECT_DIR/planck-cljs/out");
            exit(1);
        }
    }
    
    javaScriptEngineReadyCondition = [[NSCondition alloc] init];
    javaScriptEngineReady = NO;
    
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^() {
    
    
    contextManager = [[ABYContextManager alloc] initWithContext:JSGlobalContextCreate(NULL)
                                                           compilerOutputDirectory:outURL];
    [contextManager setUpConsoleLog];
    [contextManager setupGlobalContext];
    if (!useSimpleOutput) {
        if (outPath) {
            [contextManager setUpAmblyImportScript];
        } else {
            [self setUpAmblyImportScriptInContext:contextManager.context];
        }
    }
    
    NSString* mainJsFilePath = [[outURL URLByAppendingPathComponent:@"main" isDirectory:NO]
                                URLByAppendingPathExtension:@"js"].path;
    
    NSURL* googDirectory = [outURL URLByAppendingPathComponent:@"goog"];
    
    if (useSimpleOutput) {
        NSString *mainJsString;
        if (outPath) {
            mainJsString = [NSString stringWithContentsOfFile:mainJsFilePath encoding:NSUTF8StringEncoding error:nil];
        } else {
            mainJsString = [self.cljsRuntime getSourceForPath:@"main.js"];
        }
        NSAssert(mainJsString != nil, @"The main JavaScript text could not be loaded");
        [ABYUtils evaluateScript:mainJsString inContext:contextManager.context];
    } else {
        if (outPath) {
            [contextManager bootstrapWithDepsFilePath:mainJsFilePath
                                         googBasePath:[[googDirectory URLByAppendingPathComponent:@"base" isDirectory:NO] URLByAppendingPathExtension:@"js"].path];
        } else {
            [self bootstrapInContext:contextManager.context];
        }
    }
    if (measureTime) {
        NSDate* loadedTime = [NSDate date];
        NSTimeInterval executionTime = [loadedTime timeIntervalSinceDate:launchTime];
        NSLog(@"Loaded main JavaScript in %f s", executionTime);
    }
    
    context = [JSContext contextWithJSGlobalContextRef:contextManager.context];
    
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
    
    context[@"PLANCK_LOAD"] = ^(NSString *path) {
        // First try in the srcPath
        
        NSString* fullPath = [NSURL URLWithString:path
                                    relativeToURL:[NSURL URLWithString:srcPath]].path;
        
        NSString* rv = [NSString stringWithContentsOfFile:fullPath
                                                 encoding:NSUTF8StringEncoding error:nil];
        
        // Now try to load the file from the output
        if (!rv) {
            if (outPath) {
                fullPath = [NSURL URLWithString:path
                                  relativeToURL:[NSURL URLWithString:outPath]].path;
                rv = [NSString stringWithContentsOfFile:fullPath
                                               encoding:NSUTF8StringEncoding error:nil];
            } else {
                rv = [self.cljsRuntime getSourceForPath:path];
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
    
        
        // We presume we are not going to be in a REPL, so set it to print non-nil things.
        context[@"PLANCK_PRINT_FN"] = ^(NSString *message) {
            if (![message isEqualToString:@"nil"]) {
                printf("%s", message.UTF8String);
            }
        };
        
        
    [self setPrintFnsInContext:contextManager.context];

    // Set up the cljs.user namespace
    [context evaluateScript:@"goog.provide('cljs.user')"];
    [context evaluateScript:@"goog.require('cljs.core')"];
        
        
        
        
        [javaScriptEngineReadyCondition lock];
        javaScriptEngineReady = YES;
        [javaScriptEngineReadyCondition signal];
        [javaScriptEngineReadyCondition unlock];

            });
    
    if (amblyServer) {
        ABYServer* replServer = [[ABYServer alloc] initWithContext:contextManager.context
                                           compilerOutputDirectory:outURL];
        [replServer startListening];
        
        BOOL shouldKeepRunning = YES;
        NSRunLoop *theRL = [NSRunLoop currentRunLoop];
        while (shouldKeepRunning && [theRL runMode:NSDefaultRunLoopMode beforeDate:[NSDate     distantFuture]]);
        
    } else {
        
        if (initPath) {
            [self blockUntilEngineReady];
            [self executeScriptAtPath:initPath readEvalPrintFn:[self readEvalPrintFn]];
        }
        
        if (evalArg) {
            [self blockUntilEngineReady];
            NSDate *readyTime;
            if (measureTime) {
                readyTime = [NSDate date];
                NSTimeInterval executionTime = [readyTime timeIntervalSinceDate:launchTime];
                NSLog(@"Ready to eval in %f s", executionTime);
            }
            
            [[self readEvalPrintFn] callWithArguments:@[evalArg]];
            
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
        [self blockUntilEngineReady];
            JSValue* runMainFn = [self getValue:@"run-main" inNamespace:@"planck.core" fromContext:context];
            [runMainFn callWithArguments:@[mainNsName, args]];
        
        } else if (!repl && args.count > 0) {
            [self blockUntilEngineReady];
            // We treat the first arg as a path to a file to be executed (it can be '-', which means stdin)
            [self executeScriptAtPath:args[0] readEvalPrintFn:[self readEvalPrintFn]];
        
        } else if (repl) {
            
            if (plainTerminal) {
                printf("cljs.user=> ");
                fflush(stdout);
            }
            
            NSString* historyFile = nil;
            if (!plainTerminal) {
                char* homedir = getenv("HOME");
                if (homedir) {
                    historyFile = [NSString stringWithFormat:@"%@/.planck_history", [NSString stringWithCString:homedir encoding:NSUTF8StringEncoding]];
                    linenoiseHistoryLoad([historyFile cStringUsingEncoding:NSUTF8StringEncoding]);
                }
            }
            
            // Set up linenoise prompt
            NSString* currentNs = @"cljs.user";
            NSString* currentPrompt = @"";
            if (!plainTerminal) {
                currentPrompt = [self formPrompt:currentNs isSecondary:NO];
            }

            NSString* input = nil;
            char *line = NULL;
            
            if (!plainTerminal) {
                linenoiseSetMultiLine(1);
                linenoiseSetCompletionCallback(completion);
            }
            
            while(plainTerminal || (line = linenoise([currentPrompt cStringUsingEncoding:NSUTF8StringEncoding])) != NULL) {
                
                NSString* inputLine;
                
                if (!plainTerminal) {
                    inputLine = [NSString stringWithCString:line encoding:NSUTF8StringEncoding];
                    free(line);
                } else {
                    inputLine = [self getInput];
                    // Check if ^D has been pressed and if so, exit
                    if (inputLine == nil) {
                        printf("\n");
                        break;
                    }
                }
                
                [self blockUntilEngineReady];
                
                // TODO arrange to have this occur only once
                context[@"PLANCK_PRINT_FN"] = ^(NSString *message) {
                    printf("%s", message.UTF8String);
                };
                
                [self setPrintFnsInContext:contextManager.context];
                
                if (input == nil) {
                    input = inputLine;
                    if (!plainTerminal) {
                        currentPrompt = [self formPrompt:currentNs isSecondary:YES];
                    }
                } else {
                    input = [NSString stringWithFormat:@"%@\n%@", input, inputLine];
                }
                
                if ([input isEqualToString:@":cljs/quit"]) {
                    break;
                } else {
                    if (!plainTerminal) {
                        linenoiseHistoryAdd([inputLine cStringUsingEncoding:NSUTF8StringEncoding]);
                        if (historyFile) {
                            linenoiseHistorySave([historyFile cStringUsingEncoding:NSUTF8StringEncoding]);
                        }
                    }
                }
                
                BOOL isReadable = [[self isReadableFn] callWithArguments:@[input]].toBool;
                if (isReadable) {
                    
                    NSCharacterSet *charSet = [NSCharacterSet whitespaceCharacterSet];
                    NSString *trimmedString = [input stringByTrimmingCharactersInSet:charSet];
                    if (![trimmedString isEqualToString:@""]) {
                        [[self readEvalPrintFn] callWithArguments:@[input]];
                    } else {
                        if (plainTerminal) {
                            printf("\n");
                        }
                    }
                    
                    input = nil;
                    
                    currentNs = [[self getCurrentNsFn] callWithArguments:@[]].toString;
                    if (!plainTerminal) {
                        currentPrompt = [self formPrompt:currentNs isSecondary:NO];
                    } else {
                        printf("%s", [currentNs cStringUsingEncoding:NSUTF8StringEncoding]);
                        printf("=> ");
                        fflush(stdout);
                    }
                    
                }
            }
            
        }
    }

}

-(NSString*)formPrompt:(NSString*)currentNs isSecondary:(BOOL)secondary
{
    if (secondary) {
        return [[@"" stringByPaddingToLength:currentNs.length-2 withString:@" " startingAtIndex:0] stringByAppendingString:@"#_=> "];
    } else {
        return [NSString stringWithFormat:@"%@=> ", currentNs];
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

             JSStringRelease(pathStrRef);
             
             NSString* url = [@"file:///" stringByAppendingString:path];

             JSStringRef urlStringRef = JSStringCreateWithCFString((__bridge CFStringRef)url);
             
             if ([path hasPrefix:@"goog/../"]) {
                 path = [path substringFromIndex:8];
             }
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

-(JSValue*)readEvalPrintFn {
    JSValue* rv = [self getValue:@"read-eval-print" inNamespace:@"planck.core" fromContext:context];
    NSAssert(!rv.isUndefined, @"Could not find the read-eval-print function");
    return rv;
}

-(JSValue*)getCurrentNsFn {
    JSValue* rv = [self getValue:@"get-current-ns" inNamespace:@"planck.core" fromContext:context];
    NSAssert(!rv.isUndefined, @"Could not find the get-current-ns function");
    return rv;
}

-(JSValue*)isReadableFn {
    JSValue* rv = [self getValue:@"is-readable?" inNamespace:@"planck.core" fromContext:context];
    NSAssert(!rv.isUndefined, @"Could not find the is-readable? function");
    return rv;
}

-(JSValue*)completionsFn {
    JSValue* rv = [self getValue:@"get-completions" inNamespace:@"planck.core" fromContext:context];
    NSAssert(!rv.isUndefined, @"Could not find the is-readable? function");
    return rv;
}

-(void)blockUntilEngineReady
{
    [javaScriptEngineReadyCondition lock];
    while (!javaScriptEngineReady)
        [javaScriptEngineReadyCondition wait];
    
    [javaScriptEngineReadyCondition unlock];
}

@end
