#import "PLKSocket.h"

#include <CoreFoundation/CoreFoundation.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

@interface PLKSocket()

@property (nonatomic) int descriptorSequence;
@property (nonatomic, strong) NSMutableDictionary* servers;
@property (nonatomic, strong) NSString* callback;
@property (nonatomic, strong) NSString* fd;
@property JSContextRef ctx;
@end

@implementation PLKSocket
{
    CFSocketRef                 socketRef;
    CFRunLoopSourceRef          runLoopSourceRef;
    
    in_addr_t                   group;
    int                         s;
    
    struct sockaddr_in          from;
    unsigned char               buffer[4096];
    
    BOOL keepRunning;
}



void setupSocket(PLKSocket* socket)
{
   
    PLKConnection * connection = [[PLKConnection alloc] initWithStuff:socket.ctx
                                                                   fd:socket.fd
                                                             callback:socket.callback
                                                          inputStream:socket.inputStream
                                                         outputStream:socket.outputStream];
    
    connection.clientId = [socket registerAndGetDescriptor:connection];
    [connection createiife];
}

void handleConnect (CFSocketRef s,
                          CFSocketCallBackType callbackType,
                          CFDataRef address,
                          const void *data,
                          void *info)
{
    
    if( callbackType & kCFSocketAcceptCallBack)
    {
        
        CFReadStreamRef clientInput = NULL;
        CFWriteStreamRef clientOutput = NULL;
        
        CFSocketNativeHandle nativeSocketHandle = *(CFSocketNativeHandle *)data;
        
        CFStreamCreatePairWithSocket(kCFAllocatorDefault, nativeSocketHandle, &clientInput, &clientOutput);
        
        CFReadStreamSetProperty(clientInput, kCFStreamPropertyShouldCloseNativeSocket, kCFBooleanTrue);
        CFWriteStreamSetProperty(clientOutput, kCFStreamPropertyShouldCloseNativeSocket, kCFBooleanTrue);
        PLKSocket * socket = (__bridge PLKSocket*)info;
        socket.inputStream = (__bridge NSInputStream*)clientInput;
        socket.outputStream = (__bridge NSOutputStream*)clientOutput;
        
        
        [socket.client setupSocket:socket];
    }
}

-(NSString *)registerAndGetDescriptor:(PLKConnection*)o
{
    NSString* descriptor = [NSString stringWithFormat:@"PLANCK_SOCKET_CONNECTION_%d", ++self.descriptorSequence];
    [self.servers setObject:o forKey:descriptor];
    return descriptor;
}


-(id)init:(NSString*) socketAddr
     port:(int) socketPort
   client:(NSObject<PLKSocketClient>*)client

{
    self.client = client;
    CFSocketContext socketCtx = {0, (__bridge void *)self, NULL, NULL, NULL};
    CFSocketRef myipv4cfsock = CFSocketCreate(
                                              kCFAllocatorDefault,
                                              PF_INET,
                                              SOCK_STREAM,
                                              IPPROTO_TCP,
                                              kCFSocketAcceptCallBack, handleConnect, &socketCtx);
    
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
        [client socketFail:socketPort socketAddr:socketAddr];
    } else {
        
        self.servers = [[NSMutableDictionary alloc] init];
    
        CFRelease(sincfd);
    
    
        CFRunLoopSourceRef socketsource = CFSocketCreateRunLoopSource(
                                                                  kCFAllocatorDefault,
                                                                  myipv4cfsock,
                                                                  0);
    
        CFRunLoopAddSource(
                            CFRunLoopGetCurrent(),
                            socketsource,
                            kCFRunLoopDefaultMode);
        [client socketSuccess:socketPort socketAddr:socketAddr];
    }
    return self;
}

+(PLKSocket*)open:(NSString*) socketAddr
             port:(int) port
           client:(NSObject<PLKSocketClient>*) client
{
    return [[PLKSocket alloc] init:socketAddr
                              port:port
                            client:client
            ];
}

-(void)registerCallback:(NSString *)callback
                    ctx:(JSContextRef)ctx
                     fd:(NSString* )fd
{
    NSLog(@"setting up callback");
    
    self.ctx = ctx;
    self.callback = callback;
    self.fd = fd;
    
    NSRunLoop *runLoop = [NSRunLoop currentRunLoop];
    keepRunning = YES;
    NSLog(@"starting runloop");
    while (keepRunning && [runLoop runMode:NSDefaultRunLoopMode
                                beforeDate:[NSDate dateWithTimeIntervalSinceNow:1]]);
}

-(NSData*)read:(NSString *) client
{
    PLKConnection* connection = self.servers[client];
    return connection.inputBuffer;
}

-(void)close
{
    keepRunning = NO;
    [self.inputStream close];
}

@end
