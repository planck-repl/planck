#import <Foundation/Foundation.h>

@interface PLKTheme : NSObject

+ (void)initThemes;
+ (NSString*)defaultThemeForTerminal;
+ (BOOL)checkTheme:(NSString*)theme;
+ (const char*)promptAnsiCodeForTheme:(NSString*)theme;

@end
