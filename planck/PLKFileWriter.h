#import <Foundation/Foundation.h>
#import <JavaScriptCore/JavaScriptCore.h>

@class PLKFileWriter;

@protocol PLKFileWriter

+(PLKFileWriter*)open:(NSString*)path append:(BOOL)shouldAppend;
-(void)write:(NSString*)s;
-(void)flush;
-(void)close;

@end

@interface PLKFileWriter : NSObject<PLKFileWriter>

@end
