#import <Foundation/Foundation.h>
#import "PLKCommandLine.h"

int main(int argc,  char * const *argv) {
    
    @autoreleasepool {
        [PLKCommandLine processArgsCount:argc vector:argv];
    }
    
    return 0;
}


