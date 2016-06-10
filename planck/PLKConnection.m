#import "PLKConnection.h"

@interface PLKConnection()


@end

@implementation PLKConnection

- (id)initWithStuff:(JSContextRef) ctx
                 fd:(NSString*)fd
           callback:(NSString*)callback
        inputStream:(NSInputStream*) inputStream
       outputStream:(NSOutputStream*) outputStream
{
    self.ctx = ctx;
    self.fd = fd;
    self.callback = callback;
    self.inputStream = inputStream;
    self.outputStream = outputStream;
    [self setUpStream:inputStream];
    [self setUpStream:outputStream];
    return self;
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
            [self tearDownStream:self.inputStream];
            NSLog(@"end encountered");
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
    [stream  scheduleInRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
    [stream  open];
}

- (void)tearDownStream:(NSStream*)stream
{
    [stream close];
    [stream removeFromRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
    [stream setDelegate:nil];
}

@end
