#import "PLKFileWriter.h"

@interface PLKFileWriter()

@property (nonatomic, strong) NSOutputStream* outputStream;

@end

@implementation PLKFileWriter

-(id)initWithPath:(NSString*)path append:(BOOL)shouldAppend
{
    if (self = [super init]) {
        self.outputStream = [NSOutputStream outputStreamToFileAtPath:path append:shouldAppend];
        [self.outputStream open];
    }
    
    return self;
}

+(PLKFileWriter*)open:(NSString*)path append:(BOOL)shouldAppend
{
    return [[PLKFileWriter alloc] initWithPath:path append:shouldAppend];
}

-(void)write:(NSString *)s
{
    NSData* data = [s dataUsingEncoding:NSUTF8StringEncoding];
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
