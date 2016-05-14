#import <Foundation/Foundation.h>

// Similar to EXIT_SUCCESS, but use this to
// indicate an internal success that shouldn't
// terminate a REPL session
#define	PLANK_EXIT_SUCCESS_NONTERMINATE	-257

@class PLKBundledOut;

@interface PLKClojureScriptEngine : NSObject

-(void)startInitializationWithSrcPaths:(NSArray*)srcPaths outPath:(NSString*)outPath cachePath:(NSString*)cachePath verbose:(BOOL)verbose staticFns:(BOOL)staticFns elideAsserts:(BOOL)elideAsserts boundArgs:(NSArray*)boundArgs planckVersion:(NSString*)planckVersion repl:(BOOL)repl dumbTerminal:(BOOL)dumbTerminal bundledOut:(PLKBundledOut*)bundledOut;
-(int)executeSourceType:(NSString*)sourceType value:(NSString*)sourceValue expression:(BOOL)expression printNilExpression:(BOOL)printNilExpression inExitContext:(BOOL)inExitContext setNs:(NSString*)setNs theme:(NSString*)theme blockUntilReady:(BOOL)blockUntilReady;
-(int)runMainInNs:(NSString*)mainNsName args:(NSArray*)args;
-(NSString*)isReadable:(NSString*)expression theme:(NSString*)theme;
-(NSString*)getCurrentNs;
-(NSArray*)getCompletionsForBuffer:(NSString*)buffer;
-(NSArray*)getHighlightCoordsForPos:(int)pos buffer:(NSString*)buffer previousLines:(NSArray*)previousLines;
-(void)awaitShutdown;

-(void)setToPrintOnSender:(void (^)(NSString*))sender;
-(void)setToReadFrom:(NSString* (^)())input;
-(int)getIndentSpaceCount:(NSString*)text;
-(void)setHonorTermSizeRequest:(BOOL)honorTermSizeRequest;

@end
