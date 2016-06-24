#import <Foundation/Foundation.h>
#include <JavaScriptCore/JavaScriptCore.h>

@interface PLKSocketListener : NSObject<NSStreamDelegate>
@property JSContextRef ctx;
@property NSOutputStream* outputStream;
@property NSInputStream* inputStream;
@property NSMutableData* inputBuffer;
@property NSString* iife;

-(PLKSocketListener*)init:(NSInputStream *) inputStream
             outputStream:(NSOutputStream *) outputStream
                      ctx:(JSContextRef)ctx;

-(void)createiife:(NSString*)callback
               fd:(NSString*)fd
         clientId:(NSString*)clientId;

-(void)setupStream:(NSStream*)stream;

-(void)write:(NSString*) msg;

-(void)close;

@end
