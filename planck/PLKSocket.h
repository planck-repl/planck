#import <Foundation/Foundation.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include "PLKConnection.h"
#include <JavaScriptCore/JavaScriptCore.h>


@protocol PLKSocketClient;

@interface PLKSocket : NSObject
@property NSInputStream* inputStream;
@property NSOutputStream* outputStream;
@property NSObject<PLKSocketClient>* client;

+(PLKSocket*)open:(NSString*) socketAddr
             port:(int) port
           client:(NSObject<PLKSocketClient>*) client;

-(void)registerCallback:(NSString*) callback
                    ctx:(JSContextRef) ctx
                     fd:(NSString *)fd;

-(NSString *)registerAndGetDescriptor:(PLKConnection*)connection;

-(NSData*)read:(NSString*)descriptor;

-(void)close;

@end

@protocol PLKSocketClient
-(void) setupSocket:(PLKSocket*)socket;
-(void) socketFail:(int)socketPort
        socketAddr:(NSString*)socketAddr;
-(void) socketSuccess:(int)socketPort
        socketAddr:(NSString*)socketAddr;

@end