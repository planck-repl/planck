#import <Foundation/Foundation.h>

#include "PLKSocket.h"
#include <JavaScriptCore/JavaScriptCore.h>

@interface PLKConnection : NSObject
@property JSContextRef ctx;
@property NSString* callback;
@property NSString* fd;

- (id)init:(JSContextRef) ctx;




@end
