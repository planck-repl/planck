#import "PLKConnection.h"

@interface PLKConnection()


@end

@implementation PLKConnection {
    BOOL keepRunning;
}


- (id)init:(JSContextRef) ctx
{
    self.ctx = ctx;
    return self;
}

- (void)setupStuff:(NSInputStream*) inputStream
       outputStream:(NSOutputStream*) outputStream
{
    self.inputStream = inputStream;
    self.outputStream = outputStream;
    [self setUpStream:inputStream];
    [self setUpStream:outputStream];
}

- (void)createiife {
    NSMutableString* iife = [[NSMutableString alloc] init];
    
    [iife appendString:@"("];
    [iife appendString:self.callback];
    [iife appendString:@")(\""];
    [iife appendString:self.fd];
    [iife appendString:@"\", \""];
    [iife appendString:self.clientId];
    [iife appendString:@"\")"];
    self.iife = iife;
}

- (NSString *)executeIife {
    JSStringRef sref = JSStringCreateWithCFString((__bridge CFStringRef)(self.iife));
    JSValueRef rv = JSEvaluateScript(self.ctx, sref, NULL, NULL, 0, NULL);
    JSStringRef stringRef = JSValueToStringCopy(self.ctx, rv, NULL);
    NSString* result = (__bridge_transfer NSString *)JSStringCopyCFString(kCFAllocatorDefault, stringRef);
    return result;
}


- (void)stream:(NSStream *)stream handleEvent:(NSStreamEvent)eventCode {
    switch(eventCode) {
        case NSStreamEventHasBytesAvailable:
        {
            NSLog(@"has bytes available");
            uint8_t buf[1024];
            unsigned long len = [(NSInputStream *)stream read:buf maxLength:1024];
            if(!self.inputBuffer) {
                self.inputBuffer = [NSMutableData data];
            }
            if(len) {
                [self.inputBuffer setLength:0];
                [self.inputBuffer appendBytes:(const void *)buf length:len];
                
                NSString *result = [self executeIife];
                
                [self.outputStream write:[result UTF8String] maxLength:[result length] + 1];
                
            } else {
                NSLog(@"no buffer!");
            }
            break;
        }
        case NSStreamEventEndEncountered:
        {
            NSLog(@"end encountered");
            NSLog(self.clientId);
            break;
        }
        case NSStreamEventHasSpaceAvailable:
        {
            break;
        }
        case NSStreamEventNone:
        {
            break;
        }
        case NSStreamEventOpenCompleted:
        {
            break;
        }
        case NSStreamEventErrorOccurred:
        {
            NSLog(@"error");
            break;
        }
    }
}

-(void)setUpStream:(NSStream*)stream
{
    NSLog(@"Setting up stream");
    [stream setDelegate:self];
    [stream scheduleInRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
    [stream open];
}

- (void)tearDownStream:(NSStream*)stream
{
    keepRunning = NO;
    [stream close];
    [stream removeFromRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
    [stream setDelegate:nil];
}

- (void) setupSocket:(PLKSocket*) socket
{
    [self setupStuff:socket.inputStream
        outputStream:socket.outputStream];
    
    self.clientId = [socket registerAndGetDescriptor:self];
    [self createiife];
}

-(void) socketFail:(int)socketPort
        socketAddr:(NSString*)socketAddr
{
    
}

-(void) socketSuccess:(int)socketPort socketAddr:(NSString *)socketAddr
{
}

-(void)registerListener:(NSString *)listener
               socketId:(NSString *)socketId
{
    self.callback = listener;
    self.fd = socketId;
    
    NSRunLoop *runLoop = [NSRunLoop currentRunLoop];
    keepRunning = YES;
    NSLog(@"starting runloop");
    while (keepRunning && [runLoop runMode:NSDefaultRunLoopMode
                                beforeDate:[NSDate dateWithTimeIntervalSinceNow:1]]);
    

}

-(void)write:(NSString *)msg
{
    [self.outputStream write:[msg UTF8String] maxLength:[msg length] + 1];

}

-(void)close {
    [self.outputStream close];
    [self.inputStream close];
}

@end
