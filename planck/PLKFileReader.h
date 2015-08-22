#import <Foundation/Foundation.h>

@interface PLKFileReader : NSObject

+(PLKFileReader*)open:(NSString*)path encoding:(NSString*)encoding;
-(NSString*)read;
-(void)close;

@end
