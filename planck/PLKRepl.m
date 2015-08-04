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
    if (secondary) {
        return [[@"" stringByPaddingToLength:currentNs.length-2 withString:@" " startingAtIndex:0] stringByAppendingString:@"#_=> "];
    } else {
        return [NSString stringWithFormat:@"%@=> ", currentNs];
    }
}

-(NSString *) getInput
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

-(void)runPlainTerminal:(BOOL)plainTerminal usingClojureScriptEngine:(PLKClojureScriptEngine*)clojureScriptEngine
{
    if (plainTerminal) {
        printf("cljs.user=> ");
        fflush(stdout);
    }
    
    NSString* historyFile = nil;
    if (!plainTerminal) {
        char* homedir = getenv("HOME");
        if (homedir) {
            historyFile = [NSString stringWithFormat:@"%@/.planck_history", [NSString stringWithCString:homedir encoding:NSUTF8StringEncoding]];
            linenoiseHistoryLoad([historyFile cStringUsingEncoding:NSUTF8StringEncoding]);
        }
    }
    
    // Set up linenoise prompt
    NSString* currentNs = @"cljs.user";
    NSString* currentPrompt = @"";
    if (!plainTerminal) {
        currentPrompt = [self formPrompt:currentNs isSecondary:NO];
    }
    
    NSString* input = nil;
    char *line = NULL;
    
    if (!plainTerminal) {
        linenoiseSetMultiLine(1);
        s_clojureScriptEngine = clojureScriptEngine;
        linenoiseSetCompletionCallback(completion);
    }
    
    while(plainTerminal || (line = linenoise([currentPrompt cStringUsingEncoding:NSUTF8StringEncoding])) != NULL) {
        
        NSString* inputLine;
        
        if (!plainTerminal) {
            inputLine = [NSString stringWithCString:line encoding:NSUTF8StringEncoding];
            free(line);
        } else {
            inputLine = [self getInput];
            // Check if ^D has been pressed and if so, exit
            if (inputLine == nil) {
                printf("\n");
                break;
            }
        }
                
        // TODO arrange to have this occur only once
        [clojureScriptEngine setAllowPrintNils];
        
        if (input == nil) {
            input = inputLine;
            if (!plainTerminal) {
                currentPrompt = [self formPrompt:currentNs isSecondary:YES];
            }
        } else {
            input = [NSString stringWithFormat:@"%@\n%@", input, inputLine];
        }
        
        if ([input isEqualToString:@":cljs/quit"]) {
            break;
        } else {
            if (!plainTerminal) {
                linenoiseHistoryAdd([inputLine cStringUsingEncoding:NSUTF8StringEncoding]);
                if (historyFile) {
                    linenoiseHistorySave([historyFile cStringUsingEncoding:NSUTF8StringEncoding]);
                }
            }
        }
        
        if ([clojureScriptEngine isReadable:input]) {
            
            NSCharacterSet *charSet = [NSCharacterSet whitespaceCharacterSet];
            NSString *trimmedString = [input stringByTrimmingCharactersInSet:charSet];
            if (![trimmedString isEqualToString:@""]) {
                [clojureScriptEngine executeClojureScript:input expression:YES];
            } else {
                if (plainTerminal) {
                    printf("\n");
                }
            }
            
            input = nil;
            
            currentNs = [clojureScriptEngine getCurrentNs];
            if (!plainTerminal) {
                currentPrompt = [self formPrompt:currentNs isSecondary:NO];
            } else {
                printf("%s", [currentNs cStringUsingEncoding:NSUTF8StringEncoding]);
                printf("=> ");
                fflush(stdout);
            }
            
        }
    }
    
}

@end
