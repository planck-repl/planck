#import <Foundation/Foundation.h>

@interface PLKCommandLine : NSObject

+(int)processArgsCount:(int)argc vector:(char * const *)argv;

@end
