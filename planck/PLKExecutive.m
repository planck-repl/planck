#import "PLKExecutive.h"
#import "PLKClojureScriptEngine.h"
#import "PLKRepl.h"
#import "PLKScript.h"

@interface PLKExecutive()

@property (strong, nonatomic) PLKClojureScriptEngine* clojureScriptEngine;

@end

@implementation PLKExecutive

-(int)runScripts:(NSArray*)scripts
         srcPath:(NSString*)srcPath
         verbose:(BOOL)verbose
      mainNsName:(NSString*)mainNsName
            repl:(BOOL)repl
         outPath:(NSString*)outPath
    dumbTerminal:(BOOL)dumbTerminal
            args:(NSArray*)args; {
    
    int exitValue = EXIT_SUCCESS;
    
    // Set up our engine
    
    [self setupClojureScriptEngineWithSrcPath:srcPath outPath:outPath verbose:verbose];
    
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

-(void)setupClojureScriptEngineWithSrcPath:(NSString*)srcPath outPath:(NSString*)outPath verbose:(BOOL)verbose
{
    srcPath = [self ensureTrailingSlash:[self fullyQualify:srcPath]];
    outPath = [self ensureTrailingSlash:[self fullyQualify:outPath]];
    
    self.clojureScriptEngine = [[PLKClojureScriptEngine alloc] init];
    [self.clojureScriptEngine startInitializationWithSrcPath:srcPath outPath:outPath verbose:verbose];
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
