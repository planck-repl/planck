#import <Foundation/Foundation.h>

@class PLKClojureScriptEngine;

@interface PLKRepl : NSObject<NSStreamDelegate>

-(int)runUsingClojureScriptEngine:(PLKClojureScriptEngine*)clojureScriptEngine
                     dumbTerminal:(BOOL)dumbTerminal
                            theme:(NSString*)theme
                       socketAddr:(NSString*)socketAddr
                       socketPort:(int)socketPort;

@end
