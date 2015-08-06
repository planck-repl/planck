#import "PLKFileReader.h"

@interface PLKFileReader()

@property (nonatomic, strong) NSInputStream* inputStream;

@end

@implementation PLKFileReader

-(id)initWithPath:(NSString*)path
{
    if (self = [super init]) {
        self.inputStream = [NSInputStream inputStreamWithFileAtPath:path];
        [self.inputStream open];
    }
    
    return self;
}

+(PLKFileReader*)open:(NSString*)path
{
    return [[PLKFileReader alloc] initWithPath:path];
}

-(NSString*)read
{
    NSMutableData *data=[[NSMutableData alloc] init];
    
    NSUInteger maxLength = 1024;
    uint8_t buf[maxLength];
    NSInteger length = [self.inputStream read:buf maxLength:maxLength];
    if (length > 0) {
        
        [data appendBytes:(const void *)buf length:length];
        
        NSString *string = [[NSString alloc] initWithData:data
                                                 encoding:NSUTF8StringEncoding];
        if (string) {
            return string;
        } else {
            // Couldn't decode UTF8. Try reading up to 6 more bytes to see if
            // we can form a well-formed UTF8 string
            int tries = 6;
            while (tries-- > 0) {
                length = [self.inputStream read:buf maxLength:1];
                if (length > 0) {
                    [data appendBytes:(const void *)buf length:1];
                    NSString *string = [[NSString alloc] initWithData:data
                                                             encoding:NSUTF8StringEncoding];
                    if (string) {
                        return string;
                    }
                } else {
                    NSLog(@"Failed to decode.");
                    return nil;
                }
            }
            
        }
    }
    
    return nil;
}

-(void)close
{
    [self.inputStream close];
}

@end
