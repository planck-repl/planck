//
//  main.m
//  planck
//
//  Created by Mike Fikes on 7/16/15.
//  Copyright (c) 2015 FikesFarm. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "Planck.h"

int main(int argc, const char * argv[]) {
    @autoreleasepool {
        NSString* evalArg = nil;
        if (argc==3 && !strncmp(argv[1], "-e", 2)) {
            evalArg = [NSString stringWithUTF8String:argv[2]];
        }
        [[[Planck alloc] init] runEval:evalArg];
    }
    return 0;
}


