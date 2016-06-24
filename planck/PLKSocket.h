#import <Foundation/Foundation.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <JavaScriptCore/JavaScriptCore.h>
#include "PLKConnection.h"

@protocol PLKSocketClient;

@interface PLKSocket : NSObject
@property NSString* callback;
@property NSInputStream* inputStream;
@property NSOutputStream* outputStream;
@property NSObject<PLKSocketClient>* client;

+(PLKSocket*)open:(NSString*) socketAddr
             port:(int) port
           client:(NSObject<PLKSocketClient>*) client;

-(NSData*)read:(NSString*)descriptor;

-(NSString *)registerAndGetDescriptor:(NSObject<PLKSocketClient>*)client;

-(void)write:(NSString*)client
         msg:(NSString*)msg;

-(void)close:(NSString*)client;
-(void)close;

@end

@protocol PLKSocketClient
-(void) registerListener:(NSString*)listener
                socketId:socketId;
-(void) setupSocket:(PLKSocket*)socket;
-(void) socketFail:(int)socketPort
        socketAddr:(NSString*)socketAddr;
-(void) socketSuccess:(int)socketPort
        socketAddr:(NSString*)socketAddr;

@end