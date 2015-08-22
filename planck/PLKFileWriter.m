#import "PLKFileWriter.h"
#import "PLKUtils.h"

@interface PLKFileWriter()

@property (nonatomic, strong) NSOutputStream* outputStream;
@property (nonatomic) NSStringEncoding encoding;

@end

@implementation PLKFileWriter

-(id)initWithPath:(NSString*)path append:(BOOL)shouldAppend encoding:(NSString*)encoding
{
    if (self = [super init]) {
        self.outputStream = [NSOutputStream outputStreamToFileAtPath:path append:shouldAppend];
        [self.outputStream open];
        self.encoding = encodingFromString(encoding, NSUTF8StringEncoding);
    }
    
    return self;
}

+(PLKFileWriter*)open:(NSString*)path append:(BOOL)shouldAppend encoding:(NSString*)encoding
{
    return [[PLKFileWriter alloc] initWithPath:path append:shouldAppend encoding:encoding];
}

-(void)write:(NSString *)s
{
    NSData* data = [s dataUsingEncoding:self.encoding];
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
