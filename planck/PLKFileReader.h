#import <Foundation/Foundation.h>

@interface PLKFileReader : NSObject

+(PLKFileReader*)open:(NSString*)path;
-(NSString*)read;
-(void)close;

@end
