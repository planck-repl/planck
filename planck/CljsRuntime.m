#import "CljsRuntime.h"

unsigned char out_main_js[] = {
    0x67, 0x6f, 0x6f, 0x67, 0x2e, 0x61, 0x64, 0x64, 0x44, 0x65, 0x70, 0x65,
};
unsigned int out_main_js_len = 3539;

@implementation CljsRuntime


-(NSString*)getSourceForPath:(NSString*)path {
 
    NSDictionary* manifest = @{
                               @"out/main.js": @[[NSValue valueWithPointer:out_main_js], [NSNumber numberWithInt:out_main_js_len]]
                               
                            };
    
    unsigned char* p =[((NSValue*)(manifest[@"out/main.js"][0])) pointerValue];
    int len = [(NSNumber*)manifest[@"out/main.js"][1] intValue];
    
    NSData* data = [NSData dataWithBytes:p
                                  length:len];
    
    NSString* rv =
    [[NSString alloc] initWithData:data
                          encoding:NSUTF8StringEncoding];
    
    return rv;
}

@end
