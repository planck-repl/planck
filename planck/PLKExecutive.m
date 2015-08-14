#import "PLKExecutive.h"
#import "PLKClojureScriptEngine.h"
#import "PLKRepl.h"
#import "PLKScript.h"

@interface PLKExecutive()

@property (strong, nonatomic) PLKClojureScriptEngine* clojureScriptEngine;

@end

@implementation PLKExecutive

-(void)runScripts:(NSArray*)scripts
          srcPath:(NSString*)srcPath
          verbose:(BOOL)verbose
       mainNsName:(NSString*)mainNsName
             repl:(BOOL)repl
          outPath:(NSString*)outPath
     dumbTerminal:(BOOL)dumbTerminal
             args:(NSArray*)args; {
    
    // Set up our engine
    
    [self setupClojureScriptEngineWithSrcPath:srcPath outPath:outPath verbose:verbose];
    
    // Process init arguments
    
    for (PLKScript *script in scripts) {
        [self executeScript:script];
    }
    
    // Process main arguments
    
    if (mainNsName) {
        [self.clojureScriptEngine runMainInNs:mainNsName args:args];
    } else if (!repl && args.count > 0) {
        PLKScript *script;
        // We treat the first arg as a path to a file to be executed (it can be '-', which means stdin)
        NSString *path = args[0];
        if ([path isEqualToString:@"-"]) {
            script = [[PLKScript alloc] initWithStdIn];
        } else {
            script = [[PLKScript alloc] initWithPath:[self fullyQualify:path]];
        }
        [self executeScript:script];
    } else if (repl) {
        [[[PLKRepl alloc] init] runUsingClojureScriptEngine:self.clojureScriptEngine dumbTerminal:dumbTerminal];
    }
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

-(void)executeScript:(PLKScript *)script
{
    [self.clojureScriptEngine executeClojureScript:script.content
                                        expression:script.expression
                                printNilExpression:script.printNilExpression];
}

@end
