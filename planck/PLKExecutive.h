#import <Foundation/Foundation.h>

@interface PLKExecutive : NSObject

-(int)runScripts:(NSArray*)scripts
         srcPath:(NSString*)srcPath
         verbose:(BOOL)verbose
      mainNsName:(NSString*)mainNsName
            repl:(BOOL)repl
         outPath:(NSString*)outPath
    dumbTerminal:(BOOL)dumbTerminal
            args:(NSArray*)args;

@end
