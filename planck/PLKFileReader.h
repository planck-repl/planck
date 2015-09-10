#import <Foundation/Foundation.h>

@interface PLKFileReader : NSObject

+(PLKFileReader*)open:(NSString*)path encoding:(NSString*)encoding;
-(NSString*)readWithError:(out NSError**)error;
-(void)close;

@end
