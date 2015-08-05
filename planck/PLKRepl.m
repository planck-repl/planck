#import "PLKRepl.h"
#import "PLKClojureScriptEngine.h"
#include <stdio.h>
#include "linenoise.h"

static PLKClojureScriptEngine* s_clojureScriptEngine = nil;

void completion(const char *buf, linenoiseCompletions *lc) {
    
    NSArray* completions = [s_clojureScriptEngine getCompletionsForBuffer:[NSString stringWithCString:buf
                                                                                             encoding:NSUTF8StringEncoding]];
    for (NSString* completion in completions) {
        linenoiseAddCompletion(lc, [completion cStringUsingEncoding:NSUTF8StringEncoding]);
    }
}

@implementation PLKRepl

-(NSString*)formPrompt:(NSString*)currentNs isSecondary:(BOOL)secondary
{
    if (!secondary) {
        return [NSString stringWithFormat:@"%@=> ", currentNs];
    }
    if (currentNs.length == 1) {
        return @"#_=> ";
    }
    return [[@"" stringByPaddingToLength:currentNs.length-2
                              withString:@" "
                         startingAtIndex:0]
            stringByAppendingString:@"#_=> "];
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
    printf("%s", [prompt cStringUsingEncoding:NSUTF8StringEncoding]);
    fflush(stdout);
}

-(void)runPlainTerminal:(BOOL)plainTerminal usingClojureScriptEngine:(PLKClojureScriptEngine*)clojureScriptEngine
{
    NSString* currentNs = @"cljs.user";
    NSString* currentPrompt = [self formPrompt:currentNs isSecondary:NO];
    NSString* historyFile = nil;
    
    if (!plainTerminal) {
    
        char* homedir = getenv("HOME");
        if (homedir) {
            historyFile = [NSString stringWithFormat:@"%@/.planck_history", [NSString stringWithCString:homedir encoding:NSUTF8StringEncoding]];
            linenoiseHistoryLoad([historyFile cStringUsingEncoding:NSUTF8StringEncoding]);
        }
        
        linenoiseSetMultiLine(1);
        
        s_clojureScriptEngine = clojureScriptEngine;
        linenoiseSetCompletionCallback(completion);
    }
    
    NSString* input = nil;
    char *line = NULL;
    while(plainTerminal ||
          (line = linenoise([currentPrompt cStringUsingEncoding:NSUTF8StringEncoding])) != NULL) {

        // Get the current line of input
        
        NSString* inputLine;

        if (plainTerminal) {
            [self displayPrompt:currentPrompt];
            inputLine = [self getInput];
            if (inputLine == nil) { // ^D has been pressed
                printf("\n");
                break;
            }
        } else {
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
        
        // Check for explicit exit
        
        if ([input isEqualToString:@":cljs/quit"]) {
            break;
        }
        
        // Add input line to history
        
        if (!plainTerminal) {
            linenoiseHistoryAdd([inputLine cStringUsingEncoding:NSUTF8StringEncoding]);
            if (historyFile) {
                linenoiseHistorySave([historyFile cStringUsingEncoding:NSUTF8StringEncoding]);
            }
        }
        
        // Check if we now have a readable form
        // and if so, evaluate it.
        
        if ([clojureScriptEngine isReadable:input]) {
            
            // Guard against empty string being read
            
            NSCharacterSet *charSet = [NSCharacterSet whitespaceCharacterSet];
            NSString *trimmedString = [input stringByTrimmingCharactersInSet:charSet];
            if (![trimmedString isEqualToString:@""]) {
                [clojureScriptEngine executeClojureScript:input expression:YES printNilExpression:YES];
            } else {
                if (plainTerminal) {
                    printf("\n");
                }
            }
            
            // Now that we've evaluated the input, reset for next round
            
            input = nil;
            
            // Fetch the current namespace and use it to set the prompt
            
            currentNs = [clojureScriptEngine getCurrentNs];
            currentPrompt = [self formPrompt:currentNs isSecondary:NO];
            
        } else {
            
            // Prepare for reading non-1st line of input with secondary prompt
            
            currentPrompt = [self formPrompt:currentNs isSecondary:YES];
        }
        
    }
    
}

@end
