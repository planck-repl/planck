#import "PLKExecutive.h"
#import "PLKClojureScriptEngine.h"
#import "PLKRepl.h"
#import "PLKScript.h"

@interface PLKExecutive()

@property (strong, nonatomic) PLKClojureScriptEngine* clojureScriptEngine;

@end

@implementation PLKExecutive

-(int)runScripts:(NSArray*)scripts
         srcPaths:(NSArray*)srcPaths
         verbose:(BOOL)verbose
      mainNsName:(NSString*)mainNsName
            repl:(BOOL)repl
         outPath:(NSString*)outPath
    dumbTerminal:(BOOL)dumbTerminal
            args:(NSArray*)args; {
    
    int exitValue = EXIT_SUCCESS;
    
    NSArray* boundArgs = args;
    if (!repl && !mainNsName && args.count > 0) {
        // the first arg will be treated as a path to a file to be executed and should not be bound
        boundArgs = [args subarrayWithRange:NSMakeRange(1, args.count - 1)];
    }
    [self setupClojureScriptEngineWithSrcPaths:srcPaths outPath:outPath verbose:verbose boundArgs:boundArgs];
    
    // Process init arguments
    
    for (PLKScript *script in scripts) {
        exitValue = [self executeScript:script];
        if (exitValue != EXIT_SUCCESS) {
            return exitValue;
        }
    }
    
    // Process main arguments
    
    if (mainNsName) {
        exitValue = [self.clojureScriptEngine runMainInNs:mainNsName args:args];
    } else if (!repl && args.count > 0) {
        PLKScript *script;
        // We treat the first arg as a path to a file to be executed (it can be '-', which means stdin)
        NSString *path = args[0];
        if ([path isEqualToString:@"-"]) {
            script = [[PLKScript alloc] initWithStdIn];
        } else {
            script = [[PLKScript alloc] initWithPath:[self fullyQualify:path]];
        }
        exitValue = [self executeScript:script];
    } else if (repl) {
        exitValue = [[[PLKRepl alloc] init] runUsingClojureScriptEngine:self.clojureScriptEngine dumbTerminal:dumbTerminal];
    }
    
    return exitValue;
}

-(void)setupClojureScriptEngineWithSrcPaths:(NSArray*)srcPaths outPath:(NSString*)outPath verbose:(BOOL)verbose boundArgs:(NSArray*)boundArgs
{
    NSMutableArray* adjustedSrcPaths = [[NSMutableArray alloc] init];
    for (NSArray* srcPath in srcPaths) {
        NSString* type = srcPath[0];
        NSString* path = srcPath[1];
        path = [self fullyQualify:path];
        if ([type isEqualToString:@"src"]) {
            path = [self ensureTrailingSlash:path];
        }
        [adjustedSrcPaths addObject:@[type, path]];
    }
    outPath = [self ensureTrailingSlash:[self fullyQualify:outPath]];
    
    self.clojureScriptEngine = [[PLKClojureScriptEngine alloc] init];
    [self.clojureScriptEngine startInitializationWithSrcPaths:adjustedSrcPaths outPath:outPath verbose:verbose boundArgs:boundArgs];
}

-(NSString*)ensureTrailingSlash:(NSString*)s
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
    NSString* currentDirectory = [self ensureTrailingSlash:[NSFileManager defaultManager].currentDirectoryPath];
    if (path && ![path hasPrefix:@"/"]) {
        path = [currentDirectory stringByAppendingString:path];
    }
    return path;
}

-(int)executeScript:(PLKScript *)script
{
    return [self.clojureScriptEngine executeClojureScript:script.content
                                               expression:script.expression
                                       printNilExpression:script.printNilExpression
                                            inExitContext:YES];
}

@end
