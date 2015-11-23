#import "PLKRepl.h"
#import "PLKClojureScriptEngine.h"
#include <stdio.h>
#include "linenoise.h"

static PLKClojureScriptEngine* s_clojureScriptEngine = nil;
static NSMutableArray* previousLines = nil;
void (^highlightRestore)() = nil;
int32_t highlightRestoreSequence = 0;

NSString* buf2str(const char *buf) {
    NSString* rv = @"";
    if (buf) {
        NSString* decoded = [NSString stringWithCString:buf
                                               encoding:NSUTF8StringEncoding];
        if (decoded) {
            rv = decoded;
        }
    }
    return rv;
}

void completion(const char *buf, linenoiseCompletions *lc) {
    
    NSArray* completions = [s_clojureScriptEngine getCompletionsForBuffer:buf2str(buf)];
    for (NSString* completion in completions) {
        linenoiseAddCompletion(lc, [completion cStringUsingEncoding:NSUTF8StringEncoding]);
    }
}

void highlight(const char* buf, int pos) {
    
    char current = buf[pos];
    
    if (current == ']' || current == '}' || current == ')') {
        
        NSArray* highlightCoords = [s_clojureScriptEngine getHighlightCoordsForPos:pos
                                                                            buffer:buf2str(buf)
                                                                     previousLines:previousLines];
        
        
        int numLinesUp = ((NSNumber*)highlightCoords[0]).intValue;
        int highlightPos = ((NSNumber*)highlightCoords[1]).intValue;
        
        int currentPos = pos + 1;
        
        if (numLinesUp != -1) {
            int relativeHoriz = highlightPos - currentPos;
            
            if (numLinesUp) {
                fprintf(stdout,"\x1b[%dA", numLinesUp);
            }
            
            if (relativeHoriz < 0) {
                fprintf(stdout,"\x1b[%dD", -relativeHoriz);
            } else if (relativeHoriz > 0){
                fprintf(stdout,"\x1b[%dC", relativeHoriz);
            }
            
            fflush(stdout);
            
            int highlightRestoreId = OSAtomicAdd32(1, &highlightRestoreSequence);
            
            void (^highlightRestoreLocal)() =  ^(void) {
                
                if (highlightRestoreId == OSAtomicAdd32(0, &highlightRestoreSequence)) {
                    
                    OSAtomicAdd32(1, &highlightRestoreSequence);
                    
                    if (numLinesUp) {
                        fprintf(stdout,"\x1b[%dB", numLinesUp);
                    }
                    
                    if (relativeHoriz < 0) {
                        fprintf(stdout,"\x1b[%dC", -relativeHoriz);
                    } else if (relativeHoriz > 0){
                        fprintf(stdout,"\x1b[%dD", relativeHoriz);
                    }
                    
                    fflush(stdout);
                }
                
            };
            
            highlightRestore = highlightRestoreLocal;
            
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.5*NSEC_PER_SEC)),
                           dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
                               highlightRestoreLocal();
                           });
            
            
        }
    }
}

void highlightCancel() {
    if (highlightRestore) {
        highlightRestore();
    }
}

@implementation PLKRepl

-(NSString*)formPrompt:(NSString*)currentNs isSecondary:(BOOL)secondary dumbTerminal:(BOOL)dumbTerminal
{
    NSString* rv = nil;
    if (!secondary) {
        rv = [NSString stringWithFormat:@"%@=> ", currentNs];
    } else {
        if (!dumbTerminal) {
            rv = [[@"" stringByPaddingToLength:MAX(currentNs.length-2, 0)
                                    withString:@" "
                               startingAtIndex:0]
                  stringByAppendingString:@"#_=> "];
        }
    }
    return rv;
}

-(NSString *)getInput
{
    NSFileHandle *input = [NSFileHandle fileHandleWithStandardInput];
    NSData *inputData = [input availableData];
    if (inputData.length) {
        NSString *inputString = [[NSString alloc] initWithData: inputData encoding:NSUTF8StringEncoding];
        return [inputString stringByTrimmingCharactersInSet: [NSCharacterSet newlineCharacterSet]];
    } else {
        return nil;
    }
}

-(void)displayPrompt:(NSString*)prompt
{
    if (prompt) {
        printf("%s", [prompt cStringUsingEncoding:NSUTF8StringEncoding]);
        fflush(stdout);
    }
}

-(BOOL)isWhitespace:(NSString*)s
{
    NSCharacterSet *charSet = [NSCharacterSet whitespaceCharacterSet];
    return [[s stringByTrimmingCharactersInSet:charSet] isEqualToString:@""];
}

-(int)runUsingClojureScriptEngine:(PLKClojureScriptEngine*)clojureScriptEngine dumbTerminal:(BOOL)dumbTerminal
{
    int exitValue = EXIT_SUCCESS;
    
    NSString* currentNs = @"cljs.user";
    NSString* currentPrompt = [self formPrompt:currentNs isSecondary:NO dumbTerminal:dumbTerminal];
    NSString* historyFile = nil;
    
    if (!dumbTerminal) {
        
        char* homedir = getenv("HOME");
        if (homedir) {
            historyFile = [NSString stringWithFormat:@"%@/.planck_history", [NSString stringWithCString:homedir encoding:NSUTF8StringEncoding]];
            linenoiseHistoryLoad([historyFile cStringUsingEncoding:NSUTF8StringEncoding]);
        }
        
        linenoiseSetMultiLine(1);
        
        s_clojureScriptEngine = clojureScriptEngine;
        linenoiseSetCompletionCallback(completion);
        linenoiseSetHighlightCallback(highlight);
        linenoiseSetHighlightCancelCallback(highlightCancel);
    }
    
    NSSet *exitCommands = [NSSet setWithObjects:@":cljs/quit", @"quit", @"exit", nil];
    NSString* input = nil;
    previousLines = [[NSMutableArray alloc] init];
    char *line = NULL;
    while(true) {
        
        // Get the current line of input
        
        NSString* inputLine;
        
        if (dumbTerminal) {
            [self displayPrompt:currentPrompt];
            inputLine = [self getInput];
            if (inputLine == nil) { // ^D has been pressed
                printf("\n");
                break;
            }
        } else {
            line = linenoise([currentPrompt cStringUsingEncoding:NSUTF8StringEncoding]);
            if (line == NULL) {
                if (errno == EAGAIN) { // Ctrl-C was pressed
                    errno = 0;
                    input = nil;
                    [previousLines removeAllObjects];
                    currentPrompt = [self formPrompt:currentNs isSecondary:NO dumbTerminal:NO];
                    printf("\n");
                    continue;
                } else { // Ctrl-D was pressed
                    break;
                }
            }
            
            inputLine = line ? [NSString stringWithCString:line encoding:NSUTF8StringEncoding] : nil;
            // If the input line couldn't be decoded, replace it with emtpy string
            if (inputLine == nil) {
                printf("#<failed to decode input line>\n");
                inputLine = @"";
            }
            free(line);
        }
        
        // Accumulate input lines
        
        if (input == nil) {
            input = inputLine;
        } else {
            input = [NSString stringWithFormat:@"%@\n%@", input, inputLine];
        }
        
        [previousLines addObject:inputLine];
        
        // Check for explicit exit
        
        if ([exitCommands containsObject:input]) {
            break;
        }
        
        // Add input line to history
        
        if (!dumbTerminal && ![self isWhitespace:input]) {
            linenoiseHistoryAdd([inputLine cStringUsingEncoding:NSUTF8StringEncoding]);
            if (historyFile) {
                linenoiseHistorySave([historyFile cStringUsingEncoding:NSUTF8StringEncoding]);
            }
        }
        
        // Check if we now have a readable form
        // and if so, evaluate it.
        
        if ([clojureScriptEngine isReadable:input]) {
            
            if (![self isWhitespace:input]) {  // Guard against empty string being read
                
                exitValue = [clojureScriptEngine executeSourceType:@"text"
                                                             value:input
                                                        expression:YES
                                                printNilExpression:YES
                                                     inExitContext:NO];
                if (exitValue != PLANK_EXIT_SUCCESS_NONTERMINATE) {
                    break;
                }
                
            } else {
                
                printf("\n");
                
            }
            
            // Now that we've evaluated the input, reset for next round
            
            input = nil;
            [previousLines removeAllObjects];
            
            // Fetch the current namespace and use it to set the prompt
            
            currentNs = [clojureScriptEngine getCurrentNs];
            currentPrompt = [self formPrompt:currentNs isSecondary:NO dumbTerminal:dumbTerminal];
            
        } else {
            
            // Prepare for reading non-1st line of input with secondary prompt
            
            currentPrompt = [self formPrompt:currentNs isSecondary:YES dumbTerminal:dumbTerminal];
        }
        
    }
    
    // PLANK_EXIT_SUCCESS_NONTERMINATE is for internal use only, so to the rest of the world
    // it is a standard successful exit
    if (exitValue == PLANK_EXIT_SUCCESS_NONTERMINATE) {
        exitValue = EXIT_SUCCESS;
    }
    return exitValue;
}

@end
