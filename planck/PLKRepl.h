#import <Foundation/Foundation.h>

@class PLKClojureScriptEngine;

@interface PLKRepl : NSObject

-(int)runUsingClojureScriptEngine:(PLKClojureScriptEngine*)clojureScriptEngine dumbTerminal:(BOOL)dumbTerminal;

@end
