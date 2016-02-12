#import "PLKTheme.h"

@implementation PLKTheme

static NSDictionary* fontToAnsiCode;
static NSDictionary* themes;

+ (void)initThemes
{
    fontToAnsiCode = @{@"no-font": [NSValue valueWithPointer:""],
                       @"black-font": [NSValue valueWithPointer:"\x1b[30m"],
                       @"red-font": [NSValue valueWithPointer:"\x1b[31m"],
                       @"green-font": [NSValue valueWithPointer:"\x1b[32m"],
                       @"yellow-font": [NSValue valueWithPointer:"\x1b[33m"],
                       @"blue-font": [NSValue valueWithPointer:"\x1b[34m"],
                       @"magenta-font": [NSValue valueWithPointer:"\x1b[35m"],
                       @"cyan-font": [NSValue valueWithPointer:"\x1b[36m"],
                       @"white-font": [NSValue valueWithPointer:"\x1b[37m"],
                       @"black-bold-font": [NSValue valueWithPointer:"\x1b[40m"],
                       @"red-bold-font": [NSValue valueWithPointer:"\x1b[41m"],
                       @"green-bold-font": [NSValue valueWithPointer:"\x1b[42m"],
                       @"yellow-bold-font": [NSValue valueWithPointer:"\x1b[43m"],
                       @"blue-bold-font": [NSValue valueWithPointer:"\x1b[44m"],
                       @"magenta-bold-font": [NSValue valueWithPointer:"\x1b[45m"],
                       @"cyan-bold-font": [NSValue valueWithPointer:"\x1b[46m"],
                       @"white-bold-font": [NSValue valueWithPointer:"\x1b[47m"],};
    
    themes =@{@"plain": @{@"prompt-font": @"no-font"},
              @"light": @{@"prompt-font": @"cyan-font"},
              @"dark": @{@"prompt-font": @"cyan-font"}};
    
}

+ (const char*)promptAnsiCodeForTheme:(NSString*)theme
{
    return [(NSValue*)fontToAnsiCode[themes[theme][@"prompt-font"]] pointerValue];
}

+ (BOOL)checkTheme:(NSString*)theme {
    if (themes[theme] || [theme isEqualToString:@"dumb"]) {
        return YES;
    }
    
    printf("Unsupported theme: %s\n", [theme cStringUsingEncoding:NSUTF8StringEncoding]);
    printf("Supported themes:\n");
    for (NSString* key in [themes allKeys]) {
        printf("  %s\n", [key cStringUsingEncoding:NSUTF8StringEncoding]);
    }
    
    return NO;
}

@end
