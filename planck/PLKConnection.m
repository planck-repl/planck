#import "PLKConnection.h"
#import "PLKSocketListener.h"
@interface PLKConnection()
@property (nonatomic) int descriptorSequence;
@property (nonatomic, strong) NSMutableDictionary* clients;

@end

@implementation PLKConnection {
    BOOL keepRunning;
 
}


- (id)init:(JSContextRef) ctx
{
    self.ctx = ctx;
    self.clients = [[NSMutableDictionary alloc] init];
    return self;
}

- (void) clientConnect:(PLKSocket*) socket
{
    PLKSocketListener* listener = [[PLKSocketListener alloc] init:socket.inputStream
                                                     outputStream:socket.outputStream
                                                              ctx:self.ctx];
    
    [listener createiife:self.callback
                      fd:self.fd
                clientId:[self registerAndGetDescriptor:listener]];
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

-(NSString *)registerAndGetDescriptor:(PLKSocketListener*)client
{
    NSString* descriptor = [NSString stringWithFormat:@"PLANCK_SOCKET_CLIENT_%d", ++self.descriptorSequence];
    [self.clients setObject:client forKey:descriptor];
    return descriptor;
}

-(NSData*)read:(NSString *) client
{
    PLKSocketListener* connection = self.clients[client];
    return connection.inputBuffer;
}

-(void)write:(NSString *) client
         msg:(NSString *)msg
{
    PLKSocketListener* connection = self.clients[client];
    [connection write:msg];
    
}

-(void)close:(NSString *) client;
{
    PLKSocketListener* connection = self.clients[client];
    [connection close];
    [self.clients removeObjectForKey:client];
}

@end
