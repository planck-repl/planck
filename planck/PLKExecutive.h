#import <Foundation/Foundation.h>

@interface PLKExecutive : NSObject

-(void)runScripts:(NSArray*)scripts
          srcPath:(NSString*)srcPath
          verbose:(BOOL)verbose
       mainNsName:(NSString*)mainNsName
             repl:(BOOL)repl
          outPath:(NSString*)outPath
    plainTerminal:(BOOL)plainTerminal
             args:(NSArray*)args;

@end
