#import <Foundation/Foundation.h>
#include <JavaScriptCore/JavaScriptCore.h>
#include "PLKSocket.h"

@protocol PLKSocketClient;

@interface PLKConnection : NSObject<NSStreamDelegate, PLKSocketClient>
@property JSContextRef ctx;
@property NSString* callback;
@property NSString* fd;
@property NSInputStream* inputStream;
@property NSOutputStream* outputStream;
@property NSMutableData* inputBuffer;
@property NSString* clientId;
@property NSString* iife;

- (id)init:(JSContextRef) ctx;

- (void)setupStuff:(NSInputStream*) inputStream
      outputStream:(NSOutputStream*) outputStream;

-(void)setUpStream:(NSStream*)stream;
-(void)createiife;
-(void)close;
-(void)write:(NSString*) msg;

@end
