#import <Foundation/Foundation.h>

@interface PLKClojureScriptEngine : NSObject

-(void)startInitializationWithSrcPath:(NSString*)srcPath outPath:(NSString*)outPath verbose:(BOOL)verbose;
-(void)executeClojureScript:(NSString*)source expression:(BOOL)expression;
-(void)runMainInNs:(NSString*)mainNsName args:(NSArray*)args;
-(BOOL)isReadable:(NSString*)expression;
-(NSString*)getCurrentNs;
-(NSArray*)getCompletionsForBuffer:(NSString*)buffer;
-(void)setAllowPrintNils;

@end
