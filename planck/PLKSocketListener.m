#import "PLKSocketListener.h"


@interface PLKSocketListener()

@end

@implementation PLKSocketListener {
}

-(void)createiife:(NSString*)callback
               fd:(NSString*)fd
         clientId:(NSString*)clientId
{
    NSMutableString* iife = [[NSMutableString alloc] init];
    
    [iife appendString:@"("];
    [iife appendString:callback];
    [iife appendString:@")(\""];
    [iife appendString:fd];
    [iife appendString:@"\", \""];
    [iife appendString:clientId];
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
            [self tearDownStream:self.outputStream];
            [self tearDownStream:self.outputStream];
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

-(void)setupStream:(NSStream*)stream
{
    NSLog(@"Setting up stream");
    [stream setDelegate:self];
    [stream scheduleInRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
    [stream open];
}

- (void)tearDownStream:(NSStream*)stream
{
    [stream close];
    [stream removeFromRunLoop:[NSRunLoop currentRunLoop] forMode:NSDefaultRunLoopMode];
    [stream setDelegate:nil];
}

-(PLKSocketListener*)init:(NSInputStream*) inputStream
              outputStream:(NSOutputStream*) outputStream
                       ctx:(JSContextRef)ctx
{
    self.outputStream = outputStream;
    self.inputStream = inputStream;
    self.ctx = ctx;
    [self setupStream:inputStream];
    [self setupStream:outputStream];
    return self;
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