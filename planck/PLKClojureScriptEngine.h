#import <Foundation/Foundation.h>

// Similar to EXIT_SUCCESS, but use this to
// indicate an internal success that shouldn't
// terminate a REPL session
#define	PLANK_EXIT_SUCCESS_NONTERMINATE	-257

@interface PLKClojureScriptEngine : NSObject

-(void)startInitializationWithSrcPaths:(NSArray*)srcPaths outPath:(NSString*)outPath cachePath:(NSString*)cachePath verbose:(BOOL)verbose staticFns:(BOOL)staticFns boundArgs:(NSArray*)boundArgs planckVersion:(NSString*)planckVersion;
-(int)executeSourceType:(NSString*)sourceType value:(NSString*)sourceValue expression:(BOOL)expression printNilExpression:(BOOL)printNilExpression inExitContext:(BOOL)inExitContext;
-(int)runMainInNs:(NSString*)mainNsName args:(NSArray*)args;
-(BOOL)isReadable:(NSString*)expression;
-(NSString*)getCurrentNs;
-(NSArray*)getCompletionsForBuffer:(NSString*)buffer;
-(NSArray*)getHighlightCoordsForPos:(int)pos buffer:(NSString*)buffer previousLines:(NSArray*)previousLines;
-(void)awaitShutdown;

@end
