#import <Foundation/Foundation.h>

@interface Planck : NSObject

-(void)runInit:(NSString*)initPath
          eval:(NSString*)evalArg
       srcPath:(NSString*)srcPath
       verbose:(BOOL)verbose
    mainNsName:(NSString*)mainNsName
          repl:(BOOL)repl
       outPath:(NSString*)outPath
   amblyServer:(BOOL)amblyServer
 plainTerminal:(BOOL)plainTerminal
          args:(NSArray*)args;

@end
