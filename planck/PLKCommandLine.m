#import "PLKCommandLine.h"
#include <getopt.h>
#import "PLKExecutive.h"
#import "PLKScript.h"
#import "PLKLegal.h"
#import "PLKBundledOut.h"
#import "PLKTheme.h"

#define PLANCK_VERSION "1.10"

@implementation PLKCommandLine

+(int)processArgsCount:(int)argc vector:(char * const *)argv
{
    int exitValue = EXIT_SUCCESS;
    
    [PLKTheme initThemes];

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

        // opt is a short opt or clump of short opts. If the clump ends with i, e, m, c, n, k, or t then this opt
        // takes an argument.
        int idx = 0;
        char c = 0;
        char last_c = 0;
        while ((c = opt[idx]) != '\0') {
            last_c = c;
            idx++;
        }

        return (BOOL)(last_c == 'i' ||
                      last_c == 'e' ||
                      last_c == 'm' ||
                      last_c == 'c' ||
                      last_c == 'n' ||
                      last_c == 'k' ||
                      last_c == 't');
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
    NSString* socketAddr = nil;
    int socketPort = 0;
    BOOL staticFns = NO;
    BOOL noBanner = NO;
    NSString* theme = nil;

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
        {"init", required_argument, NULL, 'i'},
        {"eval", required_argument, NULL, 'e'},
        {"classpath", required_argument, NULL, 'c'},
        {"cache", required_argument, NULL, 'k'},
        {"verbose", no_argument, NULL, 'v'},
        {"dumb-terminal", no_argument, NULL, 'd'},
        {"theme", required_argument, NULL, 't'},
        {"socket-repl", required_argument, NULL, 'n'},
        {"main", required_argument, NULL, 'm'},
        {"repl", no_argument, NULL, 'r'},
        {"static-fns", no_argument, NULL, 's'},
        {"no-banner", no_argument, NULL, 'z'},

        // Undocumented options used for development
        {"out", required_argument, NULL, 'o'},

        {0, 0, 0, 0}
    };

    const char *shortopts = "h?li:e:c:vdt:n:sm:ro:k:";
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
            case 't':
            {
                theme = [NSString stringWithCString:optarg encoding:NSMacOSRomanStringEncoding];
                break;
            }
            case 'n':
            {
                NSNumberFormatter *numberFormatter = [[NSNumberFormatter alloc] init];
                numberFormatter.numberStyle = NSNumberFormatterDecimalStyle;
                NSString* bindParams = [NSString stringWithCString:optarg encoding:NSMacOSRomanStringEncoding];
                NSCharacterSet* notDigits = [[NSCharacterSet decimalDigitCharacterSet] invertedSet];
                if ([bindParams rangeOfCharacterFromSet:notDigits].location == NSNotFound) {
                    socketPort = [numberFormatter numberFromString:bindParams].intValue;
                } else {
                    NSUInteger colonLocation = [bindParams rangeOfString:@":" options:NSBackwardsSearch].location;
                    if (colonLocation != NSNotFound) {
                        socketAddr = [bindParams substringToIndex:colonLocation];
                        socketPort = [numberFormatter numberFromString:[bindParams substringFromIndex:colonLocation + 1]].intValue;
                    } else {
                        printf("Could not parse socket REPL params.");
                        socketPort = 0;
                    }
                }
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
            case 's':
            {
                staticFns = YES;
                break;
            }
            case 'z':
            {
                noBanner = YES;
                break;
            }
        }
    }
    
    if (theme == nil) {
        theme = @"light";
    }
    
    if (dumbTerminal) {
        theme = @"dumb";
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
    
    if (![PLKTheme checkTheme:theme]) {
        return exitValue;
    }
    
    if (mainNsName && repl) {
        printf("Only one main-opt can be specified.");
    } else {
        if (help) {
            printf("Planck %s\n", PLANCK_VERSION);
            printf("Usage:  planck [init-opt*] [main-opt] [arg*]\n");
            printf("\n");
            printf("  With no options or args, runs an interactive Read-Eval-Print Loop\n");
            printf("\n");
            printf("  init options:\n");
            printf("    -i path, --init=path     Load a file or resource\n");
            printf("    -e string, --eval=string Evaluate expressions in string; print non-nil\n");
            printf("                             values\n");
            printf("    -c cp, --classpath=cp    Use colon-delimited cp for source directories and\n");
            printf("                             JARs\n");
            printf("    -k path, --cache=path    Cache analysis/compilation artifacts in specified\n");
            printf("                             path\n");
            printf("    -v, --verbose            Emit verbose diagnostic output\n");
            printf("    -d, --dumb-terminal      Disables line editing / VT100 terminal control\n");
            printf("    -t theme, --theme=theme  Sets the color theme\n");
            printf("    -n x, --socket-repl=x    Enables socket REPL where x is port or IP:port\n");
            printf("    -s, --static-fns         Generate static dispatch function calls\n");
            printf("\n");
            printf("  main options:\n");
            printf("    -m ns-name, --main=ns-name Call the -main function from a namespace with\n");
            printf("                               args\n");
            printf("    -r, --repl                 Run a repl\n");
            printf("    path                       Run a script from a file or resource\n");
            printf("    -                          Run a script from standard input\n");
            printf("    -h, -?, --help             Print this help message and exit\n");
            printf("    -l, --legal                Show legal info (licenses and copyrights)\n");
            printf("\n");
            printf("  operation:\n");
            printf("\n");
            printf("    - Enters the cljs.user namespace\n");
            printf("    - Binds planck.core/*command-line-args* to a seq of strings containing\n");
            printf("      command line args that appear after any main option\n");
            printf("    - Runs all init options in order\n");
            printf("    - Calls a -main function or runs a repl or script if requested\n");
            printf("\n");
            printf("  The init options may be repeated and mixed freely, but must appear before\n");
            printf("  any main option.\n");
            printf("\n");
            printf("  Paths may be absolute or relative in the filesystem.\n");
            printf("\n");
            printf("  A comprehensive User Guide for Planck can be found at http://planck-repl.org\n");
            printf("\n");
        } else if (legal) {
            [PLKLegal displayLegalese];
        } else {
            PLKBundledOut* bundledOut = [[PLKBundledOut alloc] init];
            if (repl && !noBanner) {
                [PLKCommandLine printBanner:bundledOut];
            }
            return [[[PLKExecutive alloc] init] runScripts:scripts
                                                  srcPaths:srcPaths
                                                   verbose:verbose
                                                mainNsName:mainNsName
                                                      repl:repl
                                                   outPath:outPath
                                                 cachePath:cachePath
                                              dumbTerminal:dumbTerminal
                                                     theme:theme
                                                socketAddr:socketAddr
                                                socketPort:socketPort
                                                 staticFns:staticFns
                                                      args:args
                                             planckVersion:[NSString stringWithCString:PLANCK_VERSION
                                                                              encoding:NSUTF8StringEncoding]
                                                bundledOut:bundledOut];
        }
    }
    
    return exitValue;
}

+(void)printBanner:(PLKBundledOut*)bundledOut
{
    printf("Planck %s\n", PLANCK_VERSION);
    printf("ClojureScript %s\n", [[PLKCommandLine getClojureScriptVersion:bundledOut]
                                  cStringUsingEncoding:NSUTF8StringEncoding]);
    
    printf("    Docs: (doc function-name-here)\n");
    printf("          (find-doc \"part-of-name-here\")\n");
    printf("  Source: (source function-name-here)\n");
    printf("    Exit: Control+D or :cljs/quit or exit or quit\n");
    printf(" Results: Stored in vars *1, *2, *3, an exception in *e\n");
    
    printf("\n");
}

+(NSString*)getClojureScriptVersion:(PLKBundledOut*)bundledOut
{
    // Grab bundle.js; it is relatively small
    NSString* bundleJs = [bundledOut getSourceForPath:@"planck/bundle.js"];
    if (bundleJs) {
        return [[bundleJs substringFromIndex:29] substringToIndex:7];
    } else {
        return @"(Unknown)";
    }
}

@end
