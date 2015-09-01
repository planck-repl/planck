#import "PLKCommandLine.h"
#include <getopt.h>
#import "PLKExecutive.h"
#import "PLKScript.h"
#import "PLKLegal.h"

#define PLANCK_VERSION "1.7"

@implementation PLKCommandLine

+(int)processArgsCount:(int)argc vector:(char * const *)argv
{
    int exitValue = EXIT_SUCCESS;

    int indexOfScriptPathOrHyphen = argc;
    NSMutableArray* args = [[NSMutableArray alloc] init];

    BOOL (^shouldIgnoreArg)(char*) = ^(char* opt) {
        if (opt[0] != '-') {
            return NO;
        }
        
        // safely ignore any long opt
        if (opt[1] == '-') {
            return YES;
        }
        
        // opt is a short opt or clump of short opts. If the clump ends with i, e, m, s, c, or k then this opt
        // takes an argument.
        int idx = 0;
        char c = 0;
        char last_c = 0;
        while ((c = opt[idx]) != '\0') {
            last_c = c;
            idx++;
        }
        
        return (BOOL)(last_c == 'i' || last_c =='e' || last_c == 'm' || last_c =='s' || last_c =='c' || last_c =='k');
    };

    // A bare hyphen or a script path not preceded by -[iems] are the two types of mainopt not detected
    // by getopt_long(). If one of those two things is found, everything afterward is an earmuff arg.
    // If neither is found, then the first mainopt will be found with getopt_long, and earmuff args
    // will begin at optind + 1.
    for (int i = 1; i < argc; i++) {
        char* arg = argv[i];
        
        if (strcmp("-", arg) == 0) {
            // A bare dash means "run a script from standard input." Bind everything after the dash to *command-line-args*.
            indexOfScriptPathOrHyphen = i;
            break;
        } else if (arg[0] != '-') {
            // This could be a script path. If it is, bind everything after the path to *command-line-args*.
            char* previousOpt = argv[i - 1];
            if (!shouldIgnoreArg(previousOpt)) {
                indexOfScriptPathOrHyphen = i;
                break;
            }
        }
    }
    
    // Documented options
    BOOL help = NO;
    BOOL legal = NO;
    NSMutableArray* scripts = [NSMutableArray new]; // of PLKScript
    NSMutableArray* srcPaths = [[NSMutableArray alloc] init];
    NSString* mainNsName = nil;
    BOOL repl = NO;
    BOOL verbose = NO;
    BOOL dumbTerminal = NO;
    
    // Undocumented options, used for development.
    // The defaults set here are for release use.
    NSString* outPath = nil;
    NSString* cachePath = nil;
    
    int option = -1;
    static struct option longopts[] =
    {
        // Documented options
        {"help", no_argument, NULL, 'h'},
        {"legal", no_argument, NULL, 'l'},
        {"init", optional_argument, NULL, 'i'},
        {"eval", optional_argument, NULL, 'e'},
        {"src", optional_argument, NULL, 's'},
        {"classpath", optional_argument, NULL, 'c'},
        {"verbose", optional_argument, NULL, 'v'},
        {"dumb-terminal", optional_argument, NULL, 'd'},
        {"main", optional_argument, NULL, 'm'},
        {"repl", optional_argument, NULL, 'r'},
        
        // Undocumented options used for development
        {"out", optional_argument, NULL, 'o'},
        {"cache", optional_argument, NULL, 'k'},

        {0, 0, 0, 0}
    };
    
    const char *shortopts = "h?li:e:s:c:vdm:ro:bk:";
    BOOL didEncounterMainOpt = NO;
    // pass indexOfScriptPathOrHyphen instead of argc to guarantee that everything after a bare dash "-" or a script path gets earmuffed
    while (!didEncounterMainOpt && ((option = getopt_long(indexOfScriptPathOrHyphen, argv, shortopts, longopts, NULL)) != -1)) {
        switch (option) {
            case '?':
            {
                help = YES;
                break;
            }
            case 'h':
            {
                didEncounterMainOpt = YES;
                help = YES;
                break;
            }
            case 'l':
            {
                didEncounterMainOpt = YES;
                legal = YES;
                break;
            }
            case 'i':
            {
                [scripts addObject:[[PLKScript alloc] initWithPath:[NSString stringWithCString:optarg encoding:NSMacOSRomanStringEncoding]]];
                break;
            }
            case 'e':
            {
                [scripts addObject:[[PLKScript alloc] initWithExpression:[NSString stringWithCString:optarg encoding:NSMacOSRomanStringEncoding]]];
                break;
            }
            case 's':
            {
                fprintf(stderr, "The -s / --src option is deprecated. Use -c / --classpath instead.\n" );
                [srcPaths addObject:@[@"src", [NSString stringWithCString:optarg encoding:NSMacOSRomanStringEncoding]]];
                break;
            }
            case 'c':
            {
                NSString* classpath = [NSString stringWithCString:optarg encoding:NSMacOSRomanStringEncoding];
                for (NSString* element in [classpath componentsSeparatedByString: @":"]) {
                    if ([element hasSuffix:@".jar"] || [element hasSuffix:@"*"]) {
                        [srcPaths addObject:@[@"jar", element]];
                    } else if ([element hasSuffix:@"*"]) {
                        
                    } else {
                        [srcPaths addObject:@[@"src", element]];
                    }
                }
                break;
            }
            case 'v':
            {
                verbose = YES;
                break;
            }
            case 'd':
            {
                dumbTerminal = YES;
                break;
            }
            case 'm':
            {
                didEncounterMainOpt = YES;
                mainNsName = [NSString stringWithCString:optarg encoding:NSMacOSRomanStringEncoding];
                break;
            }
            case 'r':
            {
                didEncounterMainOpt = YES;
                repl = YES;
                break;
            }
            case 'o':
            {
                outPath = [NSString stringWithCString:optarg encoding:NSMacOSRomanStringEncoding];
                break;
            }
            case 'k':
            {
                cachePath = [NSString stringWithCString:optarg encoding:NSMacOSRomanStringEncoding];
                break;
            }
        }
    }

    // By this line, if optind is less than indexOfScriptPathOrHyphen, then there was an explicit
    // main opt. In that case, the hyphen or script path was not meant to be the main opt, but
    // rather a part of *command-line-args*.
    optind = MIN(optind, indexOfScriptPathOrHyphen);
    
    argc -= optind;
    argv += optind;
    
    while (argc-- > 0) {
        [args addObject:[NSString stringWithCString:*argv++ encoding:NSUTF8StringEncoding]];
    }
    
    // Argument validation
    
    if (scripts.count == 0 && !mainNsName && args.count==0) {
        repl = YES;
    }
    
    // Process arguments
    
    if (![srcPaths count]) {
        [srcPaths addObject:@[@"src", @"."]];
    }
        
    if (mainNsName && repl) {
        printf("Only one main-opt can be specified.");
    } else {
        if (help) {
            printf("planck %s\n", PLANCK_VERSION);
            printf("Usage:  planck [init-opt*] [main-opt] [args]\n");
            printf("\n");
            printf("  With no options or args, runs an interactive Read-Eval-Print Loop\n");
            printf("\n");
            printf("  init options:\n");
            printf("    -i, --init path     Load a file or resource\n");
            printf("    -e, --eval string   Evaluate expressions in string; print non-nil values\n");
            printf("    -c, --classpath cp  Use colon-delimited cp for source directories and JARs\n");
            printf("    -v, --verbose       Emit verbose diagnostic output\n");
            printf("    -d, --dumb-terminal Disables line editing / VT100 terminal control\n");
            printf("\n");
            printf("  main options:\n");
            printf("    -m, --main ns-name  Call the -main function from a namespace with args\n");
            printf("    -r, --repl          Run a repl\n");
            printf("    path                Run a script from a file or resource\n");
            printf("    -                   Run a script from standard input\n");
            printf("    -h, -?, --help      Print this help message and exit\n");
            printf("    -l, --legal         Show legal info (licenses and copyrights)\n");
            printf("\n");
            printf("  operation:\n");
            printf("\n");
            printf("    - Enters the cljs.user namespace\n");
            printf("    - Binds *command-line-args* to a seq of strings containing command line\n");
            printf("      args that appear after any main option\n");
            printf("    - Runs all init options in order\n");
            printf("    - Calls a -main function or runs a repl or script if requested\n");
            printf("\n");
            printf("  The init options may be repeated and mixed freely, but must appear before\n");
            printf("  any main option.\n");
            printf("\n");
            printf("  Paths may be absolute or relative in the filesystem.\n");
            printf("\n");
        } else if (legal) {
            [PLKLegal displayLegalese];
        } else {
            return [[[PLKExecutive alloc] init] runScripts:scripts
                                                  srcPaths:srcPaths
                                                   verbose:verbose
                                                mainNsName:mainNsName
                                                      repl:repl
                                                   outPath:outPath
                                                 cachePath:cachePath
                                              dumbTerminal:dumbTerminal
                                                      args:args];
        }
    }
    
    return exitValue;
}

@end
