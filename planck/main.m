//
//  main.m
//  planck
//
//  Created by Mike Fikes on 7/16/15.
//  Copyright (c) 2015 FikesFarm. All rights reserved.
//

#import <Foundation/Foundation.h>
#include <getopt.h>
#import "Planck.h"

int main(int argc,  char * const *argv) {
    
    @autoreleasepool {
        
        BOOL help = NO;
        NSString* evalArg;
        NSString* srcArg;
        NSString* outArg;
        
        int option = -1;
        static struct option longopts[] =
        {
            {"help", no_argument, NULL, 'h'},
            {"eval", optional_argument, NULL, 'e'},
            {"src", optional_argument, NULL, 's'},
            {"out", optional_argument, NULL, 'o'},
            {0, 0, 0, 0}
        };
        
        const char *shortopts = "he:s:o:";
        while ((option = getopt_long(argc, argv, shortopts, longopts, NULL)) != -1) {
            switch (option) {
                case 'h':
                {
                    help = YES;
                    break;
                }
                case 'e':
                {
                    evalArg = [NSString stringWithCString:optarg encoding:NSMacOSRomanStringEncoding];
                    break;
                }
                case 's':
                {
                    srcArg = [NSString stringWithCString:optarg encoding:NSMacOSRomanStringEncoding];
                    break;
                }
                case 'o':
                {
                    outArg = [NSString stringWithCString:optarg encoding:NSMacOSRomanStringEncoding];
                    break;
                }
            }
        }
        
        if (help) {
            printf("Usage:  planck [init-opt*] [main-opt] [args]\n");
            printf("\n");
            printf("  With no options or args, runs an interactive Read-Eval-Print Loop\n");
            printf("\n");
            printf("  init options:\n");
            printf("    -i, --init path     Load a file or resource\n");
            printf("    -e, --eval string   Evaluate expressions in string; print non-nil values\n");
            printf("    -s, --src  path     Use path for source. Default is \"src\"\n");
            printf("    -o, --out  path     Use path as compiler out directory. Default is \"out\"\n");
            printf("\n");
            printf("  main options:\n");
            printf("    -m, --main ns-name  Call the -main function from a namespace with args\n");
            printf("    -r, --repl          Run a repl\n");
            printf("    path                Run a script from a file or resource\n");
            printf("    -                   Run a script from standard input\n");
            printf("    -h, -?, --help      Print this help message and exit\n");
            printf("\n");
            printf("  The init options may be repeated and mixed freely, but must appear before\n");
            printf("  any main option.\n");
            printf("\n");
            printf("  Paths may be absolute or relative in the filesystem\n");
        } else {
            [[[Planck alloc] init] runEval:evalArg srcPath:srcArg outPath:outArg];
        }
    }
    return 0;
}


