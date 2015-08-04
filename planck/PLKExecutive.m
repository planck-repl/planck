#import "PLKExecutive.h"
#import "PLKClojureScriptEngine.h"
#import "PLKRepl.h"

@interface PLKExecutive()

@property (strong, nonatomic) PLKClojureScriptEngine* clojureScriptEngine;

@end

@implementation PLKExecutive

-(void)runInit:(NSString*)initPath
          eval:(NSString*)evalArg
       srcPath:(NSString*)srcPath
       verbose:(BOOL)verbose
    mainNsName:(NSString*)mainNsName
          repl:(BOOL)repl
       outPath:(NSString*)outPath
 plainTerminal:(BOOL)plainTerminal
          args:(NSArray*)args; {
    
    // Set up our engine
    
    [self setupClojureScriptEngineWithSrcPath:srcPath outPath:outPath verbose:verbose];
    
    // Process init arguments
    
    if (initPath) {
        [self executeScriptAtPath:initPath];
    }
    
    if (evalArg) {
        [self.clojureScriptEngine executeClojureScript:evalArg expression:YES];
    }
    
    // Process main arguments
    
    if (mainNsName) {
        [self.clojureScriptEngine runMainInNs:mainNsName args:args];
    } else if (!repl && args.count > 0) {
        // We treat the first arg as a path to a file to be executed (it can be '-', which means stdin)
        [self executeScriptAtPath:args[0]];
    } else if (repl) {
        [[[PLKRepl alloc] init] runPlainTerminal:plainTerminal usingClojureScriptEngine:self.clojureScriptEngine];
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

-(void)executeScriptAtPath:(NSString*)path
{
    NSString* source;
    if ([path isEqualToString:@"-"]) {
        NSFileHandle *input = [NSFileHandle fileHandleWithStandardInput];
        NSData *inputData = [input readDataToEndOfFile];
        if (inputData.length) {
            NSString *inputString = [[NSString alloc] initWithData: inputData encoding:NSUTF8StringEncoding];
            source = [inputString stringByTrimmingCharactersInSet: [NSCharacterSet newlineCharacterSet]];
        }
    } else {
        source = [NSString stringWithContentsOfFile:[self fullyQualify:path] encoding:NSUTF8StringEncoding error:nil];
    }
    
    if (source) {
        [self.clojureScriptEngine executeClojureScript:source expression:NO];
    } else {
        NSLog(@"Could not read file at %@", path);
        exit(1);
    }
}

@end
