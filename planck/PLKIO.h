#import <Foundation/Foundation.h>
#import <JavaScriptCore/JavaScriptCore.h>

@class PLKFile;

@protocol PLKFile <JSExport>

+(PLKFile*)file:(NSString*)path;
- (void) deleteFile;

@end

@interface PLKFile : NSObject<PLKFile>

@end

NSArray *cljs_file_seq(PLKFile *input);