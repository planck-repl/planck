#import <Foundation/Foundation.h>

@interface PLKFileOutputStream : NSObject

+(PLKFileOutputStream*)open:(NSString*)path append:(BOOL)shouldAppend;
-(void)write:(NSData*)bytes;
-(void)flush;
-(void)close;

@end
