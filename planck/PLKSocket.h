#import <Foundation/Foundation.h>
#include <sys/socket.h>
#include <netinet/in.h>

@protocol PLKSocketClient;

@interface PLKSocket : NSObject
@property NSString* callback;
@property NSInputStream* inputStream;
@property NSOutputStream* outputStream;
@property NSObject<PLKSocketClient>* client;

+(PLKSocket*)open:(NSString*) socketAddr
             port:(int) port
           client:(NSObject<PLKSocketClient>*) client;

-(NSString *)registerAndGetDescriptor:(NSObject<PLKSocketClient>*)client;

-(void)close;

@end

@protocol PLKSocketClient
-(void) registerListener:(NSString*)listener
                socketId:socketId;
-(void) clientConnect:(PLKSocket*)socket;
-(void) socketFail:(int)socketPort
        socketAddr:(NSString*)socketAddr;
-(void) socketSuccess:(int)socketPort
        socketAddr:(NSString*)socketAddr;
-(void) close:(NSString*)clientId;
-(void) write:(NSString*)clientId
          msg:(NSString*)msg;
-(NSData*) read:(NSString*)msg;
@end