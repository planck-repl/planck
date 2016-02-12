#import <Foundation/Foundation.h>

@interface PLKTheme : NSObject

+ (void)initThemes;
+ (BOOL)checkTheme:(NSString*)theme;
+ (const char*)promptAnsiCodeForTheme:(NSString*)theme;

@end
