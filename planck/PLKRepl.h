#import <Foundation/Foundation.h>

@class PLKClojureScriptEngine;

@interface PLKRepl : NSObject

-(void)runPlainTerminal:(BOOL)plainTerminal usingClojureScriptEngine:(PLKClojureScriptEngine*)clojureScriptEngine;

@end
