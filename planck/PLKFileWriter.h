#import <Foundation/Foundation.h>

@interface PLKFileWriter : NSObject

+(PLKFileWriter*)open:(NSString*)path append:(BOOL)shouldAppend encoding:(NSString*)encoding;
-(void)write:(NSString*)s;
-(void)flush;
-(void)close;

@end
