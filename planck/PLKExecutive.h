#import <Foundation/Foundation.h>

@interface PLKExecutive : NSObject

-(void)runInit:(NSString*)initPath
          eval:(NSArray*)evalArgs
       srcPath:(NSString*)srcPath
       verbose:(BOOL)verbose
    mainNsName:(NSString*)mainNsName
          repl:(BOOL)repl
       outPath:(NSString*)outPath
 plainTerminal:(BOOL)plainTerminal
          args:(NSArray*)args;

@end
