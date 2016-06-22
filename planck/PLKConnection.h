#import <Foundation/Foundation.h>
#include <JavaScriptCore/JavaScriptCore.h>
#import "PLKSocket.h"

@interface PLKConnection : NSObject<NSStreamDelegate, PLKSocketClient>
@property JSContextRef ctx;
@property NSString* callback;
@property NSString* fd;
@property NSInputStream* inputStream;
@property NSOutputStream* outputStream;
@property NSMutableData* inputBuffer;
@property NSString* clientId;
@property NSString* iife;

- (id)initWithStuff:(JSContextRef) ctx
                 fd:(NSString*)fd
           callback:(NSString*) callback
        inputStream:(NSInputStream*) inputStream
       outputStream:(NSOutputStream*) outputStream;

-(void)setUpStream:(NSStream*)stream;
-(void)createiife;

@end
