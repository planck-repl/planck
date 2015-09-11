#import <Foundation/Foundation.h>

@interface PLKFileWriter : NSObject

+(PLKFileWriter*)open:(NSString*)path append:(BOOL)shouldAppend encoding:(NSString*)encoding;
-(void)write:(NSString*)s error:(out NSError**)error;
-(void)flush;
-(void)close;

@end
