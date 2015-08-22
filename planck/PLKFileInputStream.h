#import <Foundation/Foundation.h>

@interface PLKFileInputStream : NSObject

+(PLKFileInputStream*)open:(NSString*)path;
-(NSData*)read;
-(void)close;

@end
