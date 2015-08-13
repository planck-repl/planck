#import <Foundation/Foundation.h>

@class PLKClojureScriptEngine;

@interface PLKRepl : NSObject

-(void)runUsingClojureScriptEngine:(PLKClojureScriptEngine*)clojureScriptEngine plainTerminal:(BOOL)plainTerminal;

@end
