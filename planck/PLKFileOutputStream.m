#import "PLKFileOutputStream.h"

@interface PLKFileOutputStream()

@property (nonatomic, strong) NSOutputStream* outputStream;

@end

@implementation PLKFileOutputStream

-(id)initWithPath:(NSString*)path append:(BOOL)shouldAppend
{
    if (self = [super init]) {
        self.outputStream = [NSOutputStream outputStreamToFileAtPath:path append:shouldAppend];
        [self.outputStream open];
    }
    
    return self;
}

+(PLKFileOutputStream*)open:(NSString*)path append:(BOOL)shouldAppend
{
    return [[PLKFileOutputStream alloc] initWithPath:path append:shouldAppend];
}

-(void)write:(NSData *)data
{
    uint8_t buf[data.length];
    [data getBytes:buf length:data.length];
    [self.outputStream write:buf maxLength:data.length];
}

-(void)flush
{
    //[self.outputStream flush];
}

-(void)close
{
    [self.outputStream close];
}

@end
