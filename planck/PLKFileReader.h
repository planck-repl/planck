#import <Foundation/Foundation.h>
#import <JavaScriptCore/JavaScriptCore.h>

@class PLKFileReader;

@protocol PLKFileReader <JSExport>

+(PLKFileReader*)open:(NSString*)path;
-(NSString*)read;
-(void)close;

@end

@interface PLKFileReader : NSObject<PLKFileReader>

@end
