#import <Foundation/Foundation.h>

NSStringEncoding encodingFromString(NSString *encoding_in, NSStringEncoding defaultEncoding);
BOOL fileIsNewer(NSString* pathA, NSString* pathB);

@interface PLKUtils : NSObject

@end
