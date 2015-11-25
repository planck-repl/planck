#import <Foundation/Foundation.h>

@class PLKClojureScriptEngine;

@interface PLKRepl : NSObject<NSStreamDelegate>

-(int)runUsingClojureScriptEngine:(PLKClojureScriptEngine*)clojureScriptEngine
                     dumbTerminal:(BOOL)dumbTerminal
                       socketAddr:(NSString*)socketAddr
                       socketPort:(int)socketPort;

@end
