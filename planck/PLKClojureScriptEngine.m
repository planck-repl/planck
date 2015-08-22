#import "PLKClojureScriptEngine.h"
#import "PLKSh.h"
#import "ABYUtils.h"
#import "ABYContextManager.h"
#import "PLKBundledOut.h"
#import "PLKFileReader.h"
#import "PLKFileWriter.h"
#import "PLKIO.h"
#import "ZZArchive.h"
#import "ZZArchiveEntry.h"

@interface PLKClojureScriptEngine()

@property (nonatomic, strong) NSCondition* javaScriptEngineReadyCondition;
@property (nonatomic) BOOL javaScriptEngineReady;
@property (nonatomic) JSGlobalContextRef context;
@property (nonatomic, strong) ABYContextManager* contextManager;
@property (nonatomic, strong) PLKBundledOut* bundledOut;
@property (nonatomic, strong) NSMutableSet* loadedGoogLibs;
@property (nonatomic) int exitValue;
@property (nonatomic, strong) NSMutableDictionary* openArchives;

@property (nonatomic) int descriptorSequence;
@property (nonatomic, strong) NSMutableDictionary* descriptorToObject;

@end

@implementation PLKClojureScriptEngine

int JSArrayGetCount(JSContextRef ctx, JSObjectRef arr)
{
    JSStringRef pname = JSStringCreateWithUTF8CString("length");
    JSValueRef val = JSObjectGetProperty(ctx, arr, pname, NULL);
    JSStringRelease(pname);
    return JSValueToNumber(ctx, val, NULL);
}

JSValueRef JSArrayGetValueAtIndex(JSContextRef ctx, JSObjectRef arr, int index)
{
    return JSObjectGetPropertyAtIndex(ctx, arr, index, NULL);
}

JSStringRef JSStringRefFromNSString(NSString* string) {
    return JSStringCreateWithCFString((__bridge CFStringRef)string);
}

JSValueRef JSValueMakeStringFromNSString(JSContextRef ctx, NSString* string)
{
    return string ? JSValueMakeString(ctx, JSStringRefFromNSString(string)) : JSValueMakeNull(ctx);
}

NSString* NSStringFromJSValueRef(JSContextRef ctx, JSValueRef jsValueRef)
{
    if (JSValueIsNull(ctx, jsValueRef)) {
        return nil;
    }
    JSStringRef stringRef = JSValueToStringCopy(ctx, jsValueRef, NULL);
    return (__bridge_transfer NSString *)JSStringCopyCFString(kCFAllocatorDefault, stringRef);
}

-(JSValueRef)registerAndGetDescriptor:(NSObject*)o
{
    NSString* descriptor = [NSString stringWithFormat:@"PLANCK_DESCRIPTOR_%d", ++self.descriptorSequence];
    [self.descriptorToObject setObject:o forKey:descriptor];
    return JSValueMakeStringFromNSString(self.context, descriptor);
}

-(void)initalizeEngineReadyConditionVars
{
    self.javaScriptEngineReadyCondition = [[NSCondition alloc] init];
    self.javaScriptEngineReady = NO;
}

-(void)blockUntilEngineReady
{
    [self.javaScriptEngineReadyCondition lock];
    while (!self.javaScriptEngineReady)
        [self.javaScriptEngineReadyCondition wait];
    
    [self.javaScriptEngineReadyCondition unlock];
}

-(void)signalEngineReady
{
    [self.javaScriptEngineReadyCondition lock];
    self.javaScriptEngineReady = YES;
    [self.javaScriptEngineReadyCondition signal];
    [self.javaScriptEngineReadyCondition unlock];
}

-(void)startInitializationWithSrcPaths:(NSArray*)srcPaths outPath:(NSString*)outPath verbose:(BOOL)verbose boundArgs:(NSArray*)boundArgs
{
    // By default we expect :none, but this can be set if :simple
    
    BOOL useSimpleOutput = NO;
    
    // Initialize out path / URL
    
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
    
    // ... and prepare for the "bundled" out location

    self.bundledOut = [[PLKBundledOut alloc] init];

    self.descriptorToObject = [[NSMutableDictionary alloc] init];
    
    // Now, start initializing JavaScriptCore in a background thread and return
    
    [self initalizeEngineReadyConditionVars];
    
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^() {
        
        self.contextManager = [[ABYContextManager alloc] initWithContext:JSGlobalContextCreate(NULL)
                                                 compilerOutputDirectory:outURL];
        
        [self.contextManager setUpConsoleLog];
        [self.contextManager setupGlobalContext];
        if (!useSimpleOutput) {
            if (outPath) {
                [self.contextManager setUpAmblyImportScript];
            } else {
                self.loadedGoogLibs = [[NSMutableSet alloc] init];
                [self setUpAmblyImportScriptInContext:self.contextManager.context];
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
                mainJsString = [self.bundledOut getSourceForPath:@"main.js"];
            }
            NSAssert(mainJsString != nil, @"The main JavaScript text could not be loaded");
            [ABYUtils evaluateScript:mainJsString inContext:self.contextManager.context];
        } else {
            if (outPath) {
                [self.contextManager bootstrapWithDepsFilePath:mainJsFilePath
                                                  googBasePath:[[googDirectory URLByAppendingPathComponent:@"base" isDirectory:NO] URLByAppendingPathExtension:@"js"].path];
            } else {
                [self bootstrapInContext:self.contextManager.context];
            }
        }
        
        self.context = self.contextManager.context;
        
        if (!useSimpleOutput) {
            [self requireAppNamespaces:self.context];
        }
        
        // TODO look into this. Without it thngs won't work.
        [ABYUtils evaluateScript:@"var window = global;" inContext:self.context];
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString) {
                 
                 NSString* path = NSStringFromJSValueRef(ctx, argv[0]);
                 
                 NSString* rv = nil;
                 
                 // First try in the srcPaths
                 
                 for (NSArray* srcPath in srcPaths) {
                     NSString* type = srcPath[0];
                     NSString* location = srcPath[1];
                     
                     if ([type isEqualToString:@"src"]) {
                         NSString* fullPath = [location stringByAppendingString:path];
                         
                         rv = [NSString stringWithContentsOfFile:fullPath
                                                        encoding:NSUTF8StringEncoding error:nil];
                     } else if ([type isEqualToString:@"jar"]) {
                         ZZArchive* archive = self.openArchives[path];
                         if (!archive) {
                             NSError* err = nil;
                             archive = [ZZArchive archiveWithURL:[NSURL fileURLWithPath:location]
                                                           error:&err];
                             if (err) {
                                 NSLog(@"%@", err);
                                 self.openArchives[path] = [NSNull null];
                             } else {
                                 self.openArchives[path] = archive;
                             }
                         }
                         NSData* data = nil;
                         if (![archive isEqual:[NSNull null]]) {
                             for (ZZArchiveEntry* entry in archive.entries)
                                 if ([entry.fileName isEqualToString:path]) {
                                     data = [entry newDataWithError:nil];
                                     break;
                                 }
                         }
                         if (data) {
                             rv = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
                         }
                     }
                     if (rv) {
                         break;
                     }
                 }
                 
                 // Now try to load the file from the output
                 if (!rv) {
                     if (outPath) {
                         NSString* fullPath = [outPath stringByAppendingString:path];
                         rv = [NSString stringWithContentsOfFile:fullPath
                                                        encoding:NSUTF8StringEncoding error:nil];
                     } else {
                         rv = [self.bundledOut getSourceForPath:path];
                     }
                 }
                 
                 return JSValueMakeStringFromNSString(ctx, rv);
             }
             
             return  JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_LOAD"
                                         argList:@"path"
                                       inContext:self.context];
       


        {
            JSValueRef  arguments[0];
            JSValueRef result;
            int num_arguments = 0;
            result = JSObjectCallAsFunction(self.context, [self getFunction:@"load-core-analysis-cache"], JSContextGetGlobalObject(self.context), num_arguments, arguments, NULL);
        }
        
        {
            JSValueRef  arguments[1];
            JSValueRef result;
            int num_arguments = 1;
            arguments[0] = JSValueMakeBoolean(self.context, verbose);
            result = JSObjectCallAsFunction(self.context, [self getFunction:@"init-app-env"], JSContextGetGlobalObject(self.context), num_arguments, arguments, NULL);
        }
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString) {
                 
                 NSString* file = NSStringFromJSValueRef(ctx, argv[0]);
                 
                 return JSValueMakeStringFromNSString(ctx,
                                                      [NSString stringWithContentsOfFile:file
                                                                                encoding:NSUTF8StringEncoding error:nil]);
             }
             
             return JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_READ_FILE"
                                         argList:@"file"
                                       inContext:self.context];
                
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 6) {
                 
                 int argsCount = JSArrayGetCount(ctx, (JSObjectRef)argv[0]);
                 NSMutableArray* args = [[NSMutableArray alloc] init];
                 for (int i=0; i<argsCount; i++) {
                     [args addObject:NSStringFromJSValueRef(ctx, JSArrayGetValueAtIndex(ctx, (JSObjectRef)argv[0], i))];
                 }
                 
                 NSString* arg_in = NSStringFromJSValueRef(ctx, argv[1]);
                 NSString *encoding_in = NSStringFromJSValueRef(ctx, argv[2]);
                 NSString *encoding_out = NSStringFromJSValueRef(ctx, argv[3]);
                 NSMutableDictionary *env = nil;
                 if (!JSValueIsNull(ctx, argv[4])) {
                     env = [[NSMutableDictionary alloc] init];
                     for (int i=0; i<JSArrayGetCount(ctx, (JSObjectRef)argv[4]); i++) {
                         JSObjectRef keyVal = (JSObjectRef)JSArrayGetValueAtIndex(ctx, (JSObjectRef)argv[4], i);
                         [env setObject:NSStringFromJSValueRef(ctx, JSArrayGetValueAtIndex(ctx, keyVal, 1))
                                 forKey:NSStringFromJSValueRef(ctx, JSArrayGetValueAtIndex(ctx, keyVal, 0))];
                     }
                 }
                 NSString *dir = NSStringFromJSValueRef(ctx, argv[5]);
                 
                 NSDictionary *result = cljs_shell(args, arg_in, encoding_in, encoding_out, env, dir);
                 
                 JSValueRef arguments[3];
                 arguments[0] = JSValueMakeNumber(self.context, ((NSNumber*)result[@"exit"]).doubleValue);
                 arguments[1] = JSValueMakeStringFromNSString(self.context, result[@"out"]);
                 arguments[2] = JSValueMakeStringFromNSString(self.context, result[@"err"]);
                 
                 return JSObjectMakeArray(ctx, 3, arguments, NULL);
             }
             
             return JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_SHELL_SH"
                                         argList:@"args, arg_in, encoding_in, encoding_out, env, dir"
                                       inContext:self.context];
        

        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString) {
                 
                 NSString* path = NSStringFromJSValueRef(ctx, argv[0]);
                 
                 [[NSFileManager defaultManager] removeItemAtPath:path error:nil];
                 
             }
             
             return  JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_DELETE"
                                         argList:@"path"
                                       inContext:self.context];
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString) {
                 
                 NSString* path = NSStringFromJSValueRef(ctx, argv[0]);
                 
                 BOOL isDir = NO;
                 BOOL result = [[NSFileManager defaultManager] fileExistsAtPath:path isDirectory:&isDir] && isDir;
                 
                 return JSValueMakeBoolean(ctx, result);
             }
             
             return  JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_IS_DIRECTORY"
                                         argList:@"path"
                                       inContext:self.context];
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString) {
                 
                 NSString* path = NSStringFromJSValueRef(ctx, argv[0]);
                
                 NSArray *dirFiles = [[NSFileManager defaultManager] contentsOfDirectoryAtPath:path error:nil];
                 
                 JSValueRef  arguments[dirFiles.count];
                 int num_arguments = (int)dirFiles.count;
                 for (int i=0; i<num_arguments; i++) {
                     arguments[i] = JSValueMakeStringFromNSString(self.context, [NSString stringWithFormat:@"%@/%@", path, dirFiles[i]]);
                 }
                 
                 return JSObjectMakeArray(self.context, num_arguments, arguments, NULL);
             }
             
             return  JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_LIST_FILES"
                                         argList:@"path"
                                       inContext:self.context];
        
        const BOOL isTty = isatty(fileno(stdin));
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             NSMutableData *data=[[NSMutableData alloc] init];
             
             NSFileHandle *input = [NSFileHandle fileHandleWithStandardInput];
             NSData *inputData = [input readDataOfLength:isTty ? 1 : 1024];
             if (inputData.length) {
                 
                 [data appendData:inputData];
                 
                 NSString *string = [[NSString alloc] initWithData:data
                                                          encoding:NSUTF8StringEncoding];
                 if (string) {
                     return JSValueMakeStringFromNSString(ctx, string);
                 } else {
                     // Couldn't decode UTF8. Try reading up to 6 more bytes to see if
                     // we can form a well-formed UTF8 string
                     int tries = 6;
                     while (tries-- > 0) {
                         inputData = [input readDataOfLength:1];
                         if (inputData.length > 0) {
                             [data appendData:inputData];
                             NSString *string = [[NSString alloc] initWithData:data
                                                                      encoding:NSUTF8StringEncoding];
                             if (string) {
                                 return JSValueMakeStringFromNSString(ctx, string);
                             }
                         } else {
                             NSLog(@"Failed to decode.");
                             return JSValueMakeNull(ctx);
                         }
                     }
                     
                 }
                 
             }
             
             return  JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_RAW_READ_STDIN"
                                         argList:@""
                                       inContext:self.context];
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString) {
                 
                 NSString* message = NSStringFromJSValueRef(ctx, argv[0]);
                 
                 fprintf(stdout, "%s", [message cStringUsingEncoding:NSUTF8StringEncoding]);
             }
             
             return JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_RAW_WRITE_STDOUT"
                                         argList:@"message"
                                       inContext:self.context];
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             fflush(stdout);
             
             return JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_RAW_FLUSH_STDOUT"
                                         argList:@""
                                       inContext:self.context];
        
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString) {
                 
                 NSString* message = NSStringFromJSValueRef(ctx, argv[0]);
                 
                 fprintf(stderr, "%s", [message cStringUsingEncoding:NSUTF8StringEncoding]);
             }
             
             return JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_RAW_WRITE_STDERR"
                                         argList:@"message"
                                       inContext:self.context];
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             fflush(stderr);
             
             return JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_RAW_FLUSH_STDERR"
                                         argList:@""
                                       inContext:self.context];
        

        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString) {
                 
                 NSString* message = NSStringFromJSValueRef(ctx, argv[0]);
                 
                 fprintf(stdout, "%s", [message cStringUsingEncoding:NSUTF8StringEncoding]);
             }
             
             return JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_PRINT_FN"
                                         argList:@"message"
                                       inContext:self.context];
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString) {
                 
                 NSString* message = NSStringFromJSValueRef(ctx, argv[0]);
                 
                 fprintf(stderr, "%s", [message cStringUsingEncoding:NSUTF8StringEncoding]);

             }
             
             return JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_PRINT_ERR_FN"
                                         argList:@"message"
                                       inContext:self.context];
        
        [self setPrintFnsInContext:self.contextManager.context];
        
        __weak typeof(self) weakSelf = self;
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeNumber) {
                 
                 weakSelf.exitValue = JSValueToNumber(ctx, argv[0], NULL);
                 
             }
             
             return  JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_SET_EXIT_VALUE"
                                         argList:@"exitValue"
                                       inContext:self.context];
        
        {
            JSValueRef  arguments[boundArgs.count];
            int num_arguments = (int)boundArgs.count;
            for (int i=0; i<num_arguments; i++) {
                arguments[i] = JSValueMakeStringFromNSString(self.context, boundArgs[i]);
            }
            
            [ABYUtils setValue:JSObjectMakeArray(self.context, num_arguments, arguments, NULL)
                      onObject:JSContextGetGlobalObject(self.context)
                   forProperty:@"PLANCK_INITIAL_COMMAND_LINE_ARGS" inContext:self.context];
        }
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
            return JSValueMakeNumber(ctx, weakSelf.exitValue);
        }
                                            name:@"PLANCK_GET_EXIT_VALUE"
                                            argList:@""
                                            inContext:self.context];

        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString) {
                 
                 NSString* path = NSStringFromJSValueRef(ctx, argv[0]);
                 
                 return [self registerAndGetDescriptor:[PLKFileReader open:path]];
             }
             
             return  JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_FILE_READER_OPEN"
                                         argList:@"path"
                                       inContext:self.context];

        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString) {
                 
                 NSString* descriptor = NSStringFromJSValueRef(ctx, argv[0]);
                 
                 PLKFileReader* fileReader = self.descriptorToObject[descriptor];
                 
                 return JSValueMakeStringFromNSString(ctx, [fileReader read]);
             }
             
             return  JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_FILE_READER_READ"
                                         argList:@"descriptor"
                                       inContext:self.context];
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString) {
                 
                 NSString* desciptor = NSStringFromJSValueRef(ctx, argv[0]);
                 
                 PLKFileReader* fileReader = self.descriptorToObject[desciptor];
                 
                 [fileReader close];
                 
                 [self.descriptorToObject removeObjectForKey:desciptor];
             }
             
             return  JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_FILE_READER_CLOSE"
                                         argList:@"descriptor"
                                       inContext:self.context];
        
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 2 && JSValueGetType (ctx, argv[0]) == kJSTypeString && JSValueGetType (ctx, argv[1]) == kJSTypeBoolean) {
                 
                 NSString* path = NSStringFromJSValueRef(ctx, argv[0]);
                 BOOL append = JSValueToBoolean(ctx, argv[1]);
                 
                 return [self registerAndGetDescriptor:[PLKFileWriter open:path append:append]];
             }
             
             return  JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_FILE_WRITER_OPEN"
                                         argList:@"path, append"
                                       inContext:self.context];
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 2 && JSValueGetType (ctx, argv[0]) == kJSTypeString && JSValueGetType (ctx, argv[0]) == kJSTypeString) {
                 
                 NSString* descriptor = NSStringFromJSValueRef(ctx, argv[0]);
                 NSString* content = NSStringFromJSValueRef(ctx, argv[1]);
                 
                 PLKFileWriter* fileWriter = self.descriptorToObject[descriptor];
                 
                 [fileWriter write:content];
             }
             
             return  JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_FILE_WRITER_WRITE"
                                         argList:@"descriptor, content"
                                       inContext:self.context];
        
        [ABYUtils installGlobalFunctionWithBlock:
         ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
             
             if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString) {
                 
                 NSString* desciptor = NSStringFromJSValueRef(ctx, argv[0]);
                 
                 PLKFileWriter* fileWriter = self.descriptorToObject[desciptor];
                 
                 [fileWriter close];
                 
                 [self.descriptorToObject removeObjectForKey:desciptor];
             }
             
             return  JSValueMakeNull(ctx);
         }
                                            name:@"PLANCK_FILE_WRITER_CLOSE"
                                         argList:@"descriptor"
                                       inContext:self.context];

        
        // Set up the cljs.user namespace
        
        [ABYUtils evaluateScript:@"goog.provide('cljs.user')" inContext:self.context];
        [ABYUtils evaluateScript:@"goog.require('cljs.core')" inContext:self.context];
        
        // Go for launch!
        
        [self signalEngineReady];
        
    });
}

-(void)requireAppNamespaces:(JSContextRef)context
{
    [ABYUtils evaluateScript:[NSString stringWithFormat:@"goog.require('%@');", [self munge:@"planck.repl"]]
                   inContext:context];
}

- (JSValueRef)getValue:(NSString*)name inNamespace:(NSString*)namespace fromContext:(JSGlobalContextRef)context
{
    JSValueRef namespaceValue = nil;
    for (NSString* namespaceElement in [namespace componentsSeparatedByString: @"."]) {
        if (namespaceValue) {
            namespaceValue = [ABYUtils getValueOnObject:JSValueToObject(context, namespaceValue, NULL)
                                            forProperty:[self munge:namespaceElement]
                                              inContext:context];
        } else {
            
            namespaceValue = [ABYUtils getValueOnObject:JSContextGetGlobalObject(context)
                                            forProperty:[self munge:namespaceElement]
                                              inContext:context];
        }
    }
    
    return [ABYUtils getValueOnObject:JSValueToObject(context, namespaceValue, NULL)
                   forProperty:[self munge:name]
                     inContext:context];
}

-(NSString*)munge:(NSString*)s
{
    return [[[s stringByReplacingOccurrencesOfString:@"-" withString:@"_"]
             stringByReplacingOccurrencesOfString:@"!" withString:@"_BANG_"]
            stringByReplacingOccurrencesOfString:@"?" withString:@"_QMARK_"];
}

// This implementation is just like Ambly's apart from the canonicalization "goog/../"
// and delegation to CljsRuntime

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
             
             BOOL canSkipLoad = NO;
             if ([path hasPrefix:@"goog/../"]) {
                 path = [path substringFromIndex:8];
             } else {
                 if ([self.loadedGoogLibs containsObject:path]) {
                     canSkipLoad = YES;
                 } else {
                     [self.loadedGoogLibs addObject:path];
                 }
             }
             
             if (!canSkipLoad) {
                 
                 NSError* error = nil;
                 NSString* sourceText = [self.bundledOut getSourceForPath:path];
                 
                 if (!error && sourceText) {
                     
                     JSValueRef jsError = NULL;
                     JSStringRef javaScriptStringRef = JSStringCreateWithCFString((__bridge CFStringRef)sourceText);
                     JSEvaluateScript(ctx, javaScriptStringRef, NULL, urlStringRef, 0, &jsError);
                     JSStringRelease(javaScriptStringRef);
                 }
             }
             
             JSStringRelease(urlStringRef);
         }
         
         return JSValueMakeUndefined(ctx);
     }
                                        name:@"AMBLY_IMPORT_SCRIPT"
                                     argList:@"path"
                                   inContext:context];
    
}

// This implementation is just like Ambly's apart from the delegation to CljsRuntime

-(void)bootstrapInContext:(JSContextRef)context
{
    // This implementation mirrors the bootstrapping code that is in -setup
    
    // Setup CLOSURE_IMPORT_SCRIPT
    [ABYUtils evaluateScript:@"CLOSURE_IMPORT_SCRIPT = function(src) { AMBLY_IMPORT_SCRIPT('goog/' + src); return true; }" inContext:context];
    
    // Load goog base
    NSString *baseScriptString = [self.bundledOut getSourceForPath:@"goog/base.js"];
    NSAssert(baseScriptString != nil, @"The goog base JavaScript text could not be loaded");
    [ABYUtils evaluateScript:baseScriptString inContext:context];
    
    // Load the deps file
    NSString *depsScriptString = [self.bundledOut getSourceForPath:@"main.js"];
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

-(void)setPrintFnsInContext:(JSContextRef)context
{
    [ABYUtils evaluateScript:@"cljs.core.set_print_fn_BANG_.call(null,PLANCK_PRINT_FN);" inContext:context];
    [ABYUtils evaluateScript:@"cljs.core.set_print_err_fn_BANG_.call(null,PLANCK_PRINT_ERR_FN);" inContext:context];
}

-(JSObjectRef)getFunction:(NSString*)name
{
    JSValueRef rv = [self getValue:name inNamespace:@"planck.repl" fromContext:self.context];
    NSAssert(!JSValueIsUndefined(self.context, rv) , name);
    return JSValueToObject(self.context, rv, NULL);
}

-(int)executeClojureScript:(NSString*)source expression:(BOOL)expression printNilExpression:(BOOL)printNilExpression inExitContext:(BOOL)inExitContext
{
    [self blockUntilEngineReady];
    if (!inExitContext) {
        // Default return value will indicate non-terminating successful exit
        self.exitValue = PLANK_EXIT_SUCCESS_NONTERMINATE;
    } else {
        self.exitValue = EXIT_SUCCESS;
    }
    
    JSValueRef  arguments[4];
    JSValueRef result;
    int num_arguments = 4;
    arguments[0] = JSValueMakeStringFromNSString(self.context, source);
    arguments[1] = JSValueMakeBoolean(self.context, expression);
    arguments[2] = JSValueMakeBoolean(self.context, printNilExpression);
    arguments[3] = JSValueMakeBoolean(self.context, inExitContext);
    result = JSObjectCallAsFunction(self.context, [self getFunction:@"read-eval-print"], JSContextGetGlobalObject(self.context), num_arguments, arguments, NULL);

    return self.exitValue;
}

-(int)runMainInNs:(NSString*)mainNsName args:(NSArray*)args
{
    [self blockUntilEngineReady];
    self.exitValue = EXIT_SUCCESS;
    
    JSValueRef  arguments[args.count+1];
    JSValueRef result;
    int num_arguments = (int)args.count+1;
    arguments[0] = JSValueMakeStringFromNSString(self.context, mainNsName);
    for (int i=1; i<num_arguments; i++) {
        arguments[i] = JSValueMakeStringFromNSString(self.context, args[i-1]);
    }

    result = JSObjectCallAsFunction(self.context, [self getFunction:@"run-main"], JSContextGetGlobalObject(self.context), num_arguments, arguments, NULL);
    
    return self.exitValue;
}

-(BOOL)isReadable:(NSString*)expression
{
    [self blockUntilEngineReady];
    JSValueRef  arguments[1];
    JSValueRef result;
    int num_arguments = 1;
    arguments[0] = JSValueMakeStringFromNSString(self.context, expression);
    result = JSObjectCallAsFunction(self.context, [self getFunction:@"is-readable?"], JSContextGetGlobalObject(self.context), num_arguments, arguments, NULL);
    return JSValueToBoolean(self.context, result);
}

-(NSString*)getCurrentNs
{
    [self blockUntilEngineReady];
    JSValueRef  arguments[0];
    JSValueRef result;
    int num_arguments = 0;
    result = JSObjectCallAsFunction(self.context, [self getFunction:@"get-current-ns"], JSContextGetGlobalObject(self.context), num_arguments, arguments, NULL);
    JSStringRef currentNsStringRef = JSValueToStringCopy(self.context, result, NULL);
    NSString* currentNs = (__bridge_transfer NSString *)JSStringCopyCFString(kCFAllocatorDefault, currentNsStringRef);
    return currentNs;
}

-(NSArray*)getCompletionsForBuffer:(NSString*)buffer
{
    [self blockUntilEngineReady];
    
    JSValueRef  arguments[1];
    arguments[0] = JSValueMakeStringFromNSString(self.context, buffer);
    JSValueRef result;
    int num_arguments = 1;
    result = JSObjectCallAsFunction(self.context, [self getFunction:@"get-completions"], JSContextGetGlobalObject(self.context), num_arguments, arguments, NULL);
    NSMutableArray* results = [[NSMutableArray alloc] init];
    for (int i=0; i<JSArrayGetCount(self.context, result); i++) {
        [results addObject:NSStringFromJSValueRef(self.context, JSArrayGetValueAtIndex(self.context, result, i))];
    }
    return results;
}

-(NSArray*)getHighlightCoordsForPos:(int)pos buffer:(NSString*)buffer previousLines:(NSArray*)previousLines
{
    [self blockUntilEngineReady];
    JSValueRef  arguments[3];
    arguments[0] = JSValueMakeNumber(self.context, pos);
    arguments[1] = JSValueMakeStringFromNSString(self.context, buffer);
    {
        JSValueRef prevLines[previousLines.count];
        for (int i=0; i<previousLines.count; i++) {
            prevLines[i] = JSValueMakeStringFromNSString(self.context, previousLines[i]);
        }
        arguments[2] = JSObjectMakeArray(self.context, previousLines.count, prevLines, NULL);
    }
    JSValueRef result;
    int num_arguments = 3;
    result = JSObjectCallAsFunction(self.context, [self getFunction:@"get-highlight-coords"], JSContextGetGlobalObject(self.context), num_arguments, arguments, NULL);
    
    return @[@((int)JSValueToNumber(self.context, JSArrayGetValueAtIndex(self.context, result, 0), NULL)),
      @((int)JSValueToNumber(self.context, JSArrayGetValueAtIndex(self.context, result, 1), NULL))];
}

#if JSC_OBJC_API_ENABLED
+(id)valueOfType:(Class)type fromJSValue:(JSValue*)value
{
    if (value.isUndefined)return nil;
    if (value.isNull)return nil;
    return [value toObjectOfClass:type];
}
#endif

@end
