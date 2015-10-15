#import <Foundation/Foundation.h>

@interface PLKExecutive : NSObject

-(int)runScripts:(NSArray*)scripts
        srcPaths:(NSArray*)srcPaths
         verbose:(BOOL)verbose
      mainNsName:(NSString*)mainNsName
            repl:(BOOL)repl
         outPath:(NSString*)outPath
       cachePath:(NSString*)cachePath
    dumbTerminal:(BOOL)dumbTerminal
            args:(NSArray*)args
planckVersion:(NSString*)planckVersion;

@end
