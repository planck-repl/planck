#import <Foundation/Foundation.h>

// Similar to EXIT_SUCCESS, but used to indicate that we need to terminate
#define	PLANK_EXIT_SUCCESS_TERMINATE_INTERNAL	-257

@class PLKBundledOut;

@interface PLKClojureScriptEngine : NSObject

-(void)startInitializationWithSrcPaths:(NSArray*)srcPaths outPath:(NSString*)outPath cachePath:(NSString*)cachePath verbose:(BOOL)verbose staticFns:(BOOL)staticFns elideAsserts:(BOOL)elideAsserts boundArgs:(NSArray*)boundArgs planckVersion:(NSString*)planckVersion repl:(BOOL)repl dumbTerminal:(BOOL)dumbTerminal bundledOut:(PLKBundledOut*)bundledOut;
-(int)executeSourceType:(NSString*)sourceType value:(NSString*)sourceValue expression:(BOOL)expression printNilExpression:(BOOL)printNilExpression setNs:(NSString*)setNs theme:(NSString*)theme blockUntilReady:(BOOL)blockUntilReady sessionId:(int)sessionId;
-(void)clearStateForSession:(int)sessionId;
-(int)runMainInNs:(NSString*)mainNsName args:(NSArray*)args;
-(NSString*)isReadable:(NSString*)expression theme:(NSString*)theme;
-(NSString*)getCurrentNs;
-(NSArray*)getCompletionsForBuffer:(NSString*)buffer;
-(NSArray*)getHighlightCoordsForPos:(int)pos buffer:(NSString*)buffer previousLines:(NSArray*)previousLines;
-(void)awaitShutdown:(BOOL)waitForTimers;

-(void)setToPrintOnSender:(void (^)(NSString*))sender;
-(void)setToReadFrom:(NSString* (^)())input;
-(int)getIndentSpaceCount:(NSString*)text;
-(void)setHonorTermSizeRequest:(BOOL)honorTermSizeRequest;
-(BOOL)isReady;
-(BOOL)printNewline;

@end
