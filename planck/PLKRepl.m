#import "PLKRepl.h"
#import "PLKTheme.h"
#import "PLKClojureScriptEngine.h"
#include <stdio.h>
#include "linenoise.h"
#include <CoreFoundation/CoreFoundation.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include "MKBlockingQueue.h"
#include "MPEdn.h"

static PLKClojureScriptEngine* s_clojureScriptEngine = nil;
static NSMutableArray* s_previousLines; // Used when using linenoise
static BOOL s_shouldKeepRunning;
static NSLock* s_evalLock;

// Need to keep a map, one per session
int32_t socketReplSessionIdSequence = 0;
static NSMutableDictionary* s_socketRepls;

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
                                                                     previousLines:s_previousLines];
        
        
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

@interface PLKRepl()

@property (nonatomic) int socketReplSessionId;

@property (strong, nonatomic) NSInputStream* inputStream;
@property (strong, nonatomic) NSOutputStream* outputStream;

@property (strong, nonatomic) NSMutableData* inputBuffer;
@property (atomic) NSUInteger inputBufferBytesScanned;
@property (atomic) BOOL evaluating;
@property (strong, nonatomic) MKBlockingQueue* readQueue;

@property (strong, nonatomic) NSMutableArray* previousLines;

@property (strong, nonatomic) NSSet *exitCommands;
@property (strong, nonatomic) NSString* input;

@property (strong, nonatomic) NSString* currentNs;
@property (strong, nonatomic) NSString* currentPrompt;
@property (nonatomic) int indentSpaceCount;

@property (nonatomic) int exitValue;

@property (strong, nonatomic) NSString* historyFile;

@property (strong, nonatomic) NSObject* sendLock;

// Data currently being sent. (In flight iff dataBeingSent != nil)
@property (strong, atomic) NSData* dataBeingSent;
@property (atomic) NSUInteger dataBytesSent;

// Subsequent data to be transmitted in FIFO order
@property (strong, nonatomic) NSMutableArray* queuedData;

@end

@implementation PLKRepl

-(NSString*)formPrompt:(NSString*)currentNs isSecondary:(BOOL)secondary dumbTerminal:(BOOL)dumbTerminal
{
    NSString* rv = nil;
    if (!secondary) {
        rv = [NSString stringWithFormat:@"%@=> ", currentNs];
    } else {
        if (!dumbTerminal) {
            rv = [[@"" stringByPaddingToLength:MAX((int)currentNs.length-2, 0)
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

void handleConnect (
                    CFSocketRef s,
                    CFSocketCallBackType callbackType,
                    CFDataRef address,
                    const void *data,
                    void *info
                    )
{
    if( callbackType & kCFSocketAcceptCallBack)
    {
        CFReadStreamRef clientInput = NULL;
        CFWriteStreamRef clientOutput = NULL;
        
        CFSocketNativeHandle nativeSocketHandle = *(CFSocketNativeHandle *)data;
        
        CFStreamCreatePairWithSocket(kCFAllocatorDefault, nativeSocketHandle, &clientInput, &clientOutput);
        
        CFReadStreamSetProperty(clientInput, kCFStreamPropertyShouldCloseNativeSocket, kCFBooleanTrue);
        CFWriteStreamSetProperty(clientOutput, kCFStreamPropertyShouldCloseNativeSocket, kCFBooleanTrue);
        
        PLKRepl* server = [[PLKRepl alloc] init];

        server.socketReplSessionId = OSAtomicAdd32(1, &socketReplSessionIdSequence);
        
        [s_socketRepls setObject:server forKey:@(server.socketReplSessionId)];
        
        server.previousLines = [[NSMutableArray alloc] init];
        
        server.exitValue = EXIT_SUCCESS;
        
        server.currentNs = @"cljs.user";
        server.currentPrompt = [server formPrompt:server.currentNs isSecondary:NO dumbTerminal:YES];
        server.exitCommands = [NSSet setWithObjects:@":cljs/quit", @"quit", @"exit", @":repl/quit", nil];
        server.input = nil;
        server.sendLock = @"";
        
        NSInputStream* inputStream = (__bridge NSInputStream*)clientInput;
        NSOutputStream* outputStream = (__bridge NSOutputStream*)clientOutput;
        
        [PLKRepl setUpStream:inputStream server:server];
        [PLKRepl setUpStream:outputStream server:server];
        
        server.inputStream = inputStream;
        server.outputStream = outputStream;
        
        [server sendData:[@"cljs.user=> " dataUsingEncoding:NSUTF8StringEncoding]];
    }
}

-(void)processInputBuffer:(NSUInteger)newlineIndex
{
    // Read the bytes in the input buffer, up to the first \n
    char* bytes = malloc(newlineIndex + 1);
    strncpy(bytes, self.inputBuffer.bytes, newlineIndex);
    bytes[newlineIndex] = 0;
    if (newlineIndex && bytes[newlineIndex -1] == '\r'){
        bytes[newlineIndex-1] = 0;
    }
    NSString* read = [NSString stringWithUTF8String:bytes];

    if (read == nil) {
        @synchronized(self.sendLock) {
            [self sendData:[@"#<failed to decode input line>\n" dataUsingEncoding:NSUTF8StringEncoding]];
        }
        read = @"";
    }
    
    // Discard initial segment of the buffer up to and including the \n character
    if (self.inputBuffer.length == newlineIndex + 1) {
        self.inputBuffer = [NSMutableData data];
    } else {
        self.inputBuffer = [NSMutableData dataWithBytes:self.inputBuffer.bytes + newlineIndex + 1
                                                 length:self.inputBuffer.length - newlineIndex - 1];
    }
    
    if (self.evaluating) {
        [self.readQueue enqueue:[NSString stringWithFormat:@"%@\n", read]];
    } else {
        BOOL tearDown = [self processLine:read dumbTerminal:YES theme:@"dumb"];
        if (tearDown) {
            [self tearDown];
        }
    }
}

- (void)stream:(NSStream *)stream handleEvent:(NSStreamEvent)eventCode
{
    if (eventCode == NSStreamEventHasBytesAvailable) {
        if(!self.inputBuffer) {
            self.inputBuffer = [NSMutableData data];
            self.inputBufferBytesScanned = 0;
        }
        const size_t BUFFER_SIZE = 1024;
        uint8_t buf[BUFFER_SIZE];
        NSInteger len = 0;
        len = [(NSInputStream *)stream read:buf maxLength:BUFFER_SIZE];
        if (len == -1) {
            NSLog(@"Error reading from REPL input stream");
        } else if (len > 0) {
            
            @synchronized(self.inputBuffer) {
                
                [self.inputBuffer appendBytes:(const void *)buf length:len];
                
                while (YES) {
                    BOOL found = NO;
                    size_t upperBound = self.inputBuffer.length - self.inputBufferBytesScanned;
                    const char* bytes = self.inputBuffer.bytes;
                    for (size_t i=0; i<upperBound; i++) {
                        if (bytes[i] == '\n') {
                            found = YES;
                            [self processInputBuffer:self.inputBufferBytesScanned + i];
                            break;
                        }
                    }
                    if (found) {
                        self.inputBufferBytesScanned = 0;
                    } else {
                        self.inputBufferBytesScanned += upperBound;
                        break;
                    }
                    
                }
            }
        }
        
    } else if (eventCode == NSStreamEventHasSpaceAvailable) {
        @synchronized(self.sendLock) {
            if (self.dataBeingSent) {
                if (self.dataBytesSent < self.dataBeingSent.length) {
                    [self sendDataBytes];
                } else {
                    [self sendNextData];
                }
            }
        }
    } else if (eventCode == NSStreamEventEndEncountered) {
        [self tearDown];
    }
}

-(void)sendData:(NSData*)data
{
    if (self.dataBeingSent == nil) {
        self.dataBeingSent = data;
        self.dataBytesSent = 0;
        if (self.outputStream.hasSpaceAvailable) {
            [self sendDataBytes];
        }
    } else {
        // Something is in flight. Queue data.
        if (!self.queuedData) {
            self.queuedData = [[NSMutableArray alloc] init];
        }
        [self.queuedData addObject:data];
    }
}

-(void)dequeAndSend
{
    if (self.queuedData.count) {
        NSData* data = self.queuedData[0];
        [self.queuedData removeObjectAtIndex:0];
        [self sendData:data];
    }
}

- (void)sendNextData
{
    self.dataBeingSent = nil;
    [self dequeAndSend];
}

-(void)sendDataBytes
{
    NSInteger result = 0;
    if (self.dataBeingSent.length) {
        result = [self.outputStream write:self.dataBeingSent.bytes + self.dataBytesSent
                                maxLength:self.dataBeingSent.length - self.dataBytesSent];
    }
    
    if (result < 0) {
        // NSLog(@"Error writing bytes to REPL output stream");
    } else {
        self.dataBytesSent += result;
    }
    
    if (self.dataBytesSent == self.dataBeingSent.length) {
        [self sendNextData];
    }
}

+(void)setUpStream:(NSStream*)stream server:(PLKRepl*)server
{
    [stream setDelegate:server];
    [stream  scheduleInRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
    [stream  open];
}

+(void)tearDownStream:(NSStream*)stream
{
    [stream close];
    [stream removeFromRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
    [stream setDelegate:nil];
}

-(void)tearDown
{
    if (self.inputStream) {
        [PLKRepl tearDownStream:self.inputStream];
        self.inputStream = nil;
    }
    
    if (self.outputStream) {
        [PLKRepl tearDownStream:self.outputStream];
        self.outputStream = nil;
    }
    
    [s_socketRepls removeObjectForKey:@(self.socketReplSessionId)];
}

-(BOOL)processLine:(NSString*)inputLine dumbTerminal:(BOOL)dumbTerminal theme:(NSString*)theme
{
    // Accumulate input lines
    
    if (self.input == nil) {
        self.input = inputLine;
    } else {
        self.input = [NSString stringWithFormat:@"%@\n%@", self.input, inputLine];
    }
    
    [self.previousLines addObject:inputLine];
    
    // Check for explicit exit
    
    if ([self.exitCommands containsObject:self.input]) {
        return YES;
    }
    
    // Add input line to history
    
    if (self.historyFile && ![self isWhitespace:self.input]) {
        linenoiseHistoryAdd([inputLine cStringUsingEncoding:NSUTF8StringEncoding]);
        linenoiseHistorySave([self.historyFile cStringUsingEncoding:NSUTF8StringEncoding]);
    }
    
    // Check if we now have readable forms
    // and if so, evaluate them.
    
    BOOL done = NO;
    NSString* balanceText = nil;
    
    while (!done) {
        if ((balanceText = [s_clojureScriptEngine isReadable:self.input theme:theme]) != nil) {
            
            self.input = [self.input substringToIndex:[self.input length] - [balanceText length]];
            
            if (![self isWhitespace:self.input]) {  // Guard against empty string being read
                
                [s_evalLock lock];
                
                if (self.outputStream) {
                    self.evaluating = YES;
                    self.readQueue = [[MKBlockingQueue alloc] init];
                    
                    [s_clojureScriptEngine setToPrintOnSender:^(NSString* msg){
                        @synchronized(self.sendLock) {
                            [self sendData:[msg dataUsingEncoding:NSUTF8StringEncoding]];
                        }
                    }];
                    
                    [s_clojureScriptEngine setToReadFrom:^NSString *{
                        while (self.evaluating) {
                            return (NSString*)[self.readQueue dequeue];
                        }
                        return nil;
                    }];
                }
                
                [s_clojureScriptEngine setHonorTermSizeRequest:!dumbTerminal];
                
                self.exitValue = [s_clojureScriptEngine executeSourceType:@"text"
                                                                    value:self.input
                                                               expression:YES
                                                       printNilExpression:YES
                                                            inExitContext:NO
                                                                    setNs:self.currentNs
                                                                    theme:theme
                                                          blockUntilReady:YES];
                
                [s_clojureScriptEngine setHonorTermSizeRequest:NO];
                
                if (self.outputStream) {
                    [s_clojureScriptEngine setToPrintOnSender:nil];
                    
                    [s_clojureScriptEngine setToReadFrom:nil];
                    
                    self.evaluating = NO;
                    [self.readQueue enqueue:@""];
                }
                
                [s_evalLock unlock];
                
                if (self.exitValue != PLANK_EXIT_SUCCESS_NONTERMINATE) {
                    return YES;
                }
                
            } else {
                
                if (self.outputStream) {
                    @synchronized(self.sendLock) {
                        [self sendData:[@"\n" dataUsingEncoding:NSUTF8StringEncoding]];
                    }
                } else {
                    printf("\n");
                }
                
            }
            
            // Now that we've evaluated the input, reset for next round
            
            self.input = balanceText;
            [self.previousLines removeAllObjects];
            
            // Fetch the current namespace and use it to set the prompt
            
            self.currentNs = [s_clojureScriptEngine getCurrentNs];
            self.currentPrompt = [self formPrompt:self.currentNs isSecondary:NO dumbTerminal:dumbTerminal];
            
            if ([self isWhitespace:balanceText]) {
                done = YES;
                self.input = nil;
            }
            
        } else {
            
            // Prepare for reading non-1st line of input with secondary prompt
            if (self.historyFile) {
                if (isPasting()) {
                    self.indentSpaceCount = 0;
                } else {
                    self.indentSpaceCount = [s_clojureScriptEngine getIndentSpaceCount:self.input];
                }
            }
            self.currentPrompt = [self formPrompt:self.currentNs isSecondary:YES dumbTerminal:dumbTerminal];
            done = YES;
        }
    }
    
    if (self.outputStream && self.currentPrompt) {
        @synchronized(self.sendLock) {
            [self sendData:[self.currentPrompt dataUsingEncoding:NSUTF8StringEncoding]];
        }
    }
    
    return NO;
}


-(void)runCommandLineLoopDumbTerminal:(BOOL)dumbTerminal theme:(NSString*)theme
{
    self.indentSpaceCount = 0;
    
    while(true) {
        
        // Get the current line of input
        
        NSString* inputLine;
        
        if (dumbTerminal) {
            [self displayPrompt:self.currentPrompt];
            inputLine = [self getInput];
            if (inputLine == nil) { // ^D has been pressed
                printf("\n");
                break;
            }
        } else {
            char *line = linenoise([self.currentPrompt cStringUsingEncoding:NSUTF8StringEncoding],
                                   [PLKTheme promptAnsiCodeForTheme:theme],
                                   self.indentSpaceCount);
            self.indentSpaceCount = 0;
            if (line == NULL) {
                if (errno == EAGAIN) { // Ctrl-C was pressed
                    errno = 0;
                    self.input = nil;
                    [self.previousLines removeAllObjects];
                    self.currentPrompt = [self formPrompt:self.currentNs isSecondary:NO dumbTerminal:NO];
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
        
        BOOL breakOut = [self processLine:inputLine dumbTerminal:dumbTerminal theme:theme];
        if (breakOut) {
            break;
        }
        
    }
}

NSString * hostAndPort(NSString *socketAddr, int socketPort)
{
    NSMutableString * tmp = [[NSMutableString alloc] init];
    
    if (socketAddr) {
        [tmp appendString:socketAddr];
    } else {
        [tmp appendString:@"localhost"];
    }
    [tmp appendString:@":"];
    [tmp appendString: [@(socketPort) stringValue]];
    
    return [NSString stringWithString:tmp];
}

- (void)runSocketRepl:(int)socketPort
           socketAddr:(NSString *)socketAddr
                theme:(NSString *)theme
         dumbTerminal:(BOOL)dumbTerminal
                quiet:(BOOL)quiet
{
    NSString * fullAddress = hostAndPort(socketAddr, socketPort);
    
    CFSocketContext socketCtxt = {0, (__bridge void *)self, NULL, NULL, NULL};
    
    CFSocketRef myipv4cfsock = CFSocketCreate(
                                              kCFAllocatorDefault,
                                              PF_INET,
                                              SOCK_STREAM,
                                              IPPROTO_TCP,
                                              kCFSocketAcceptCallBack, handleConnect, &socketCtxt);
    
    struct sockaddr_in sin;
    
    memset(&sin, 0, sizeof(sin));
    sin.sin_len = sizeof(sin);
    sin.sin_family = AF_INET; /* Address family */
    sin.sin_port = htons(socketPort);
    if (socketAddr) {
        inet_aton([socketAddr cStringUsingEncoding:NSUTF8StringEncoding], &sin.sin_addr);
    } else {
        sin.sin_addr.s_addr= INADDR_ANY;
    }
    CFDataRef sincfd = CFDataCreate(
                                    kCFAllocatorDefault,
                                    (UInt8 *)&sin,
                                    sizeof(sin));
    
    if (CFSocketSetAddress(myipv4cfsock, sincfd) != kCFSocketSuccess) {
        
        
        printf("Planck socket REPL could not bind to %s.\n", [fullAddress UTF8String]);
        self.exitValue = EXIT_FAILURE;
    } else {
        s_socketRepls = [[NSMutableDictionary alloc] init];
        s_evalLock = [[NSLock alloc] init];
        
        CFRelease(sincfd);
        
        CFRunLoopSourceRef socketsource = CFSocketCreateRunLoopSource(
                                                                      kCFAllocatorDefault,
                                                                      myipv4cfsock,
                                                                      0);
        
        CFRunLoopAddSource(
                           CFRunLoopGetCurrent(),
                           socketsource,
                           kCFRunLoopDefaultMode);
        
        
        dispatch_queue_t thread = dispatch_queue_create("CLIUI", NULL);
        dispatch_async(thread, ^{
            
            [self runCommandLineLoopDumbTerminal:dumbTerminal theme:theme];
            
            s_shouldKeepRunning = NO;
            
        });
        
        if (!quiet) {
            printf("Planck socket REPL listening at %s.\n", [fullAddress UTF8String]);
        }
        s_shouldKeepRunning = YES;
        
        NSRunLoop *runLoop = [NSRunLoop currentRunLoop];
        while (s_shouldKeepRunning &&
               [runLoop runMode:NSDefaultRunLoopMode
                     beforeDate:[NSDate dateWithTimeIntervalSinceNow:1]]);
        
    }
}

-(int)idForKeyMapAction:(MPEdnKeyword*)action
{
    NSString* actionName = [action ednName];
    if ([actionName isEqualToString:@"go-to-beginning-of-line"]) {
        return KM_GO_TO_START_OF_LINE;
    } else if ([actionName isEqualToString:@"go-back-one-space"]) {
        return KM_MOVE_LEFT;
    } else if ([actionName isEqualToString:@"go-forward-one-space"]) {
        return KM_MOVE_RIGHT;
    } else if ([actionName isEqualToString:@"delete-right"]) {
        return KM_DELETE_RIGHT;
    } else if ([actionName isEqualToString:@"delete-backwards"]) {
        return KM_DELETE;
    } else if ([actionName isEqualToString:@"delete-to-end-of-line"]) {
        return KM_DELETE_TO_END_OF_LINE;
    } else if ([actionName isEqualToString:@"go-to-end-of-line"]) {
        return KM_GO_TO_END_OF_LINE;
    } else if ([actionName isEqualToString:@"clear-screen"]) {
        return KM_CLEAR_SCREEN;
    } else if ([actionName isEqualToString:@"next-line"]) {
        return KM_HISTORY_NEXT;
    } else if ([actionName isEqualToString:@"previous-line"]) {
        return KM_HISTORY_PREVIOUS;
    } else if ([actionName isEqualToString:@"transpose-characters"]) {
        return KM_SWAP_CHARS;
    } else if ([actionName isEqualToString:@"undo-typing-on-line"]) {
        return KM_CLEAR_LINE;
    } else if ([actionName isEqualToString:@"delete-previous-word"]) {
        return KM_DELETE_PREVIOUS_WORD;
    } else {
        return -1;
    }
}

-(char)keyCodeFor:(MPEdnKeyword*)value{
    NSString* keyName = [value ednName];
    if ([keyName hasPrefix:@"ctrl-"] && [keyName length] == 6) {
        char c = [keyName characterAtIndex:5];
        if ('a' <= c && c <= 'z') {
            return c - 'a' + 1;
        } else {
            return -1;
        }
    } else {
        return -1;
    }
}

-(int)runUsingClojureScriptEngine:(PLKClojureScriptEngine*)clojureScriptEngine
                     dumbTerminal:(BOOL)dumbTerminal
                            quiet:(BOOL)quiet
                            theme:(NSString*)theme
                       socketAddr:(NSString*)socketAddr
                       socketPort:(int)socketPort
{
    s_clojureScriptEngine = clojureScriptEngine;
   
    
    self.previousLines = [[NSMutableArray alloc] init];
    s_previousLines = self.previousLines;
    
    self.exitValue = EXIT_SUCCESS;
    
    self.currentNs = @"cljs.user";
    self.currentPrompt = [self formPrompt:self.currentNs isSecondary:NO dumbTerminal:dumbTerminal];
    self.exitCommands = [NSSet setWithObjects:@":cljs/quit", @"quit", @"exit", nil];
    self.input = nil;
    
    // Per-type initialization
    
    if (!dumbTerminal) {
        
        char* homedir = getenv("HOME");
        if (homedir) {
            self.historyFile = [NSString stringWithFormat:@"%@/.planck_history", [NSString stringWithCString:homedir encoding:NSUTF8StringEncoding]];
            linenoiseHistoryLoad([self.historyFile cStringUsingEncoding:NSUTF8StringEncoding]);
            
            NSString* keymapFile = [NSString stringWithFormat:@"%@/.planck_keymap", [NSString stringWithCString:homedir encoding:NSUTF8StringEncoding]];
            NSString* keymapContents = [NSString stringWithContentsOfFile:keymapFile encoding:NSUTF8StringEncoding error:nil];
            if (keymapContents) {
                NSDictionary* keymapDict = [keymapContents ednStringToObject];
                if ([[NSSet alloc] initWithArray:[keymapDict allValues]].count != [keymapDict allValues].count) {
                    printf("%s: Duplicate keymap values.\n",
                           [keymapFile cStringUsingEncoding:NSUTF8StringEncoding]);
                }
                for(id key in keymapDict) {
                    if ([key class] == [MPEdnKeyword class]) {
                        int actionId = [self idForKeyMapAction:key];
                        if (actionId != -1) {
                            id value = [keymapDict objectForKey:key];
                            if ([value isKindOfClass:[MPEdnKeyword class]]) {
                                int keyCode = [self keyCodeFor:value];
                                if (keyCode != -1) {
                                    linenoiseSetKeymapEntry(actionId, (char)keyCode);
                                } else {
                                    printf("%s: Unrecognized keymap value: :%s\n",
                                           [keymapFile cStringUsingEncoding:NSUTF8StringEncoding],
                                           [[value ednName] cStringUsingEncoding:NSUTF8StringEncoding]);
                                }
                            } else {
                                printf("%s: Unrecognized keymap value: %s\n",
                                       [keymapFile cStringUsingEncoding:NSUTF8StringEncoding],
                                       [[value description] cStringUsingEncoding:NSUTF8StringEncoding]);
                            }
                        } else {
                            printf("%s: Unrecognized keymap key: :%s\n",
                                   [keymapFile cStringUsingEncoding:NSUTF8StringEncoding],
                                   [[key ednName] cStringUsingEncoding:NSUTF8StringEncoding]);
                        }
                    } else {
                        printf("%s: Unrecognized keymap key: %s\n",
                               [keymapFile cStringUsingEncoding:NSUTF8StringEncoding],
                               [[key description] cStringUsingEncoding:NSUTF8StringEncoding]);
                    }
                }
            }
        }
        
        linenoiseSetMultiLine(1);
        linenoiseSetCompletionCallback(completion);
        linenoiseSetHighlightCallback(highlight);
        linenoiseSetHighlightCancelCallback(highlightCancel);
    }
    
    if (socketPort == 0) {
        [self runCommandLineLoopDumbTerminal:dumbTerminal theme:theme];
    } else {
        [self runSocketRepl:socketPort socketAddr:socketAddr theme:theme dumbTerminal:dumbTerminal quiet:quiet];
    }

    
    // PLANK_EXIT_SUCCESS_NONTERMINATE is for internal use only, so to the rest of the world
    // it is a standard successful exit
    if (self.exitValue == PLANK_EXIT_SUCCESS_NONTERMINATE) {
        self.exitValue = EXIT_SUCCESS;
    }
    return self.exitValue;
}

@end
