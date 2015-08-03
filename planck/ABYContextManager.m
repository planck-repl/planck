#include "ABYContextManager.h"
#include "ABYUtils.h"
#include <libkern/OSAtomic.h>

@interface ABYContextManager()

// The compiler output directory
@property (strong, nonatomic) NSURL* compilerOutputDirectory;

@end

@implementation ABYContextManager

-(id)initWithContext:(JSGlobalContextRef)context compilerOutputDirectory:(NSURL*)compilerOutputDirectory
{
    if (self = [super init]) {
        _context = JSGlobalContextRetain(context);
        self.compilerOutputDirectory = compilerOutputDirectory;
    }
    return self;
}

-(void)dealloc
{
    JSGlobalContextRelease(_context);
}

- (void)setupGlobalContext
{
    [ABYUtils evaluateScript:@"var global = this" inContext:_context];
}

- (void)setUpConsoleLog
{
    
    [ABYUtils installGlobalFunctionWithBlock: ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
        
        if (argc == 1)
        {
            NSLog(@"%@", [ABYUtils stringForValue:argv[0] inContext:ctx]);
        }
        
        return JSValueMakeUndefined(ctx);
    }
                                        name:@"AMBLY_NSLOG"
                                     argList:@"message"
                                   inContext:_context];
    
    
    [ABYUtils evaluateScript:@"var console = {}" inContext:_context];
    [ABYUtils evaluateScript:@"console.log = AMBLY_NSLOG" inContext:_context];

}

- (void)setUpTimerFunctionality
{
    
    static volatile int32_t counter = 0;
    
    NSString* callbackImpl = @"var callbackstore = {};\nvar setTimeout = function( fn, ms ) {\ncallbackstore[setTimeoutFn(ms)] = fn;\n}\nvar runTimeout = function( id ) {\nif( callbackstore[id] )\ncallbackstore[id]();\ncallbackstore[id] = null;\n}\n";
    
    [ABYUtils evaluateScript:callbackImpl inContext:_context];
    
    __weak typeof(self) weakSelf = self;
    
    [ABYUtils installGlobalFunctionWithBlock:
     
     ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
         if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeNumber)
         {
             int ms = (int)JSValueToNumber(ctx, argv[0], NULL);
             
             int32_t incremented = OSAtomicIncrement32(&counter);
             
             NSString *str = [NSString stringWithFormat:@"timer%d", incremented];
             
             dispatch_after(dispatch_time(DISPATCH_TIME_NOW, ms * NSEC_PER_MSEC), dispatch_get_main_queue(), ^{
                 [ABYUtils evaluateScript:[NSString stringWithFormat:@"runTimeout(\"%@\");", str] inContext:weakSelf.context];
             });
             
             JSStringRef strRef = JSStringCreateWithCFString((__bridge CFStringRef)str);
             JSValueRef rv = JSValueMakeString(ctx, strRef);
             JSStringRelease(strRef);
             return rv;
         }
         
         return JSValueMakeUndefined(ctx);
     }
                                        name:@"setTimeoutFn"
                                     argList:@"ms"
                                   inContext:_context];
    
}

-(void)setUpAmblyImportScript
{
    NSString* compilerOutputDirectoryPath = self.compilerOutputDirectory.path;

    [ABYUtils installGlobalFunctionWithBlock:
     
     ^JSValueRef(JSContextRef ctx, size_t argc, const JSValueRef argv[]) {
         
         if (argc == 1 && JSValueGetType (ctx, argv[0]) == kJSTypeString)
         {
             JSStringRef pathStrRef = JSValueToStringCopy(ctx, argv[0], NULL);
             NSString* path = (__bridge_transfer NSString *) JSStringCopyCFString( kCFAllocatorDefault, pathStrRef );
             JSStringRelease(pathStrRef);

#if TARGET_OS_IPHONE
             NSString* url = [NSURL fileURLWithPath:path].absoluteString;
#else
             NSString* url = [@"file:///" stringByAppendingString:path];
#endif
             JSStringRef urlStringRef = JSStringCreateWithCFString((__bridge CFStringRef)url);
             
             NSString* readPath = [NSString stringWithFormat:@"%@/%@", compilerOutputDirectoryPath, path];
             
             NSError* error = nil;
             NSString* sourceText = [NSString stringWithContentsOfFile:readPath encoding:NSUTF8StringEncoding error:&error];
             
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
                                   inContext:_context];
    
}

-(void)bootstrapWithDepsFilePath:(NSString*)depsFilePath googBasePath:(NSString*)googBasePath
{
    // This implementation mirrors the bootstrapping code that is in -setup
    
    // Setup CLOSURE_IMPORT_SCRIPT
    [ABYUtils evaluateScript:@"CLOSURE_IMPORT_SCRIPT = function(src) { AMBLY_IMPORT_SCRIPT('goog/' + src); return true; }" inContext:_context];
    
    // Load goog base
    NSString *baseScriptString = [NSString stringWithContentsOfFile:googBasePath encoding:NSUTF8StringEncoding error:nil];
     NSAssert(baseScriptString != nil, @"The goog base JavaScript text could not be loaded");
    [ABYUtils evaluateScript:baseScriptString inContext:_context];
    
    // Load the deps file
    NSString *depsScriptString = [NSString stringWithContentsOfFile:depsFilePath encoding:NSUTF8StringEncoding error:nil];
    NSAssert(depsScriptString != nil, @"The deps JavaScript text could not be loaded");
    [ABYUtils evaluateScript:depsScriptString inContext:_context];
    
    [ABYUtils evaluateScript:@"goog.isProvided_ = function(x) { return false; };" inContext:_context];
    
    [ABYUtils evaluateScript:@"goog.require = function (name) { return CLOSURE_IMPORT_SCRIPT(goog.dependencies_.nameToPath[name]); };" inContext:_context];
    
    [ABYUtils evaluateScript:@"goog.require('cljs.core');" inContext:_context];
    
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
     "};" inContext:_context];
}

@end
