#import <Foundation/Foundation.h>

@interface PLKClojureScriptEngine : NSObject

-(void)startInitializationWithSrcPath:(NSString*)srcPath outPath:(NSString*)outPath verbose:(BOOL)verbose;
-(int)executeClojureScript:(NSString*)source expression:(BOOL)expression printNilExpression:(BOOL)printNilExpression inExitContext:(BOOL)inExitContext;
-(int)runMainInNs:(NSString*)mainNsName args:(NSArray*)args;
-(BOOL)isReadable:(NSString*)expression;
-(NSString*)getCurrentNs;
-(NSArray*)getCompletionsForBuffer:(NSString*)buffer;
-(NSArray*)getHighlightCoordsForPos:(int)pos buffer:(NSString*)buffer previousLines:(NSArray*)previousLines;

@end
