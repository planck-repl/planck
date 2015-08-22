#import "PLKFileInputStream.h"

@interface PLKFileInputStream()

@property (nonatomic, strong) NSInputStream* inputStream;

@end

@implementation PLKFileInputStream

-(id)initWithPath:(NSString*)path
{
    if (self = [super init]) {
        self.inputStream = [NSInputStream inputStreamWithFileAtPath:path];
        [self.inputStream open];
    }
    
    return self;
}

+(PLKFileInputStream*)open:(NSString*)path
{
    return [[PLKFileInputStream alloc] initWithPath:path];
}

-(NSData*)read
{
    NSMutableData *data=[[NSMutableData alloc] init];
    
    NSUInteger maxLength = 1024;
    uint8_t buf[maxLength];
    NSInteger length = [self.inputStream read:buf maxLength:maxLength];
    if (length > 0) {
        
        [data appendBytes:(const void *)buf length:length];
        return data;
    }
    
    return nil;
}

-(void)close
{
    [self.inputStream close];
}

@end
