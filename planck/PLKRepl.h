#import <Foundation/Foundation.h>
#import "PLKSocket.h"
@class PLKClojureScriptEngine;

@interface PLKRepl : NSObject<NSStreamDelegate, PLKSocketClient>

-(void)setupSocket:(PLKSocket *)socket;

-(int)runUsingClojureScriptEngine:(PLKClojureScriptEngine*)clojureScriptEngine
                     dumbTerminal:(BOOL)dumbTerminal
                            quiet:(BOOL)quiet
                            theme:(NSString*)theme
                       socketAddr:(NSString*)socketAddr
                       socketPort:(int)socketPort;

@end
