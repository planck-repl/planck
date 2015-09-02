#import <Foundation/Foundation.h>
#import "PLKCommandLine.h"

int main(int argc,  char * const *argv) {
    
    @autoreleasepool {
        return [PLKCommandLine processArgsCount:argc vector:argv];
    }
}
