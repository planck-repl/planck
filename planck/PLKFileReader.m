#import "PLKFileReader.h"
#import "PLKUtils.h"

@interface PLKFileReader()

@property (nonatomic, strong) NSInputStream* inputStream;
@property (nonatomic) NSStringEncoding encoding;

@end

@implementation PLKFileReader

-(id)initWithPath:(NSString*)path encoding:(NSString*)encoding
{
    if (self = [super init]) {
        self.inputStream = [NSInputStream inputStreamWithFileAtPath:path];
        [self.inputStream open];
        self.encoding = encodingFromString(encoding, NSUTF8StringEncoding);
    }
    
    return self;
}

+(PLKFileReader*)open:(NSString*)path encoding:(NSString*)encoding
{
    return [[PLKFileReader alloc] initWithPath:path encoding:encoding];
}

-(NSString*)readWithError:(out NSError**)error
{
    NSMutableData *data=[[NSMutableData alloc] init];
    
    NSUInteger maxLength = 1024;
    uint8_t buf[maxLength];
    NSInteger length = [self.inputStream read:buf maxLength:maxLength];
    *error = [self.inputStream streamError];
    if (length > 0) {
        
        [data appendBytes:(const void *)buf length:length];
        
        NSString *string = [[NSString alloc] initWithData:data
                                                 encoding:self.encoding];
        if (string) {
            return string;
        } else {
            // Couldn't decode. Try reading up to 16 more bytes to see if
            // we can form a well-formed string
            int tries = 16;
            while (tries-- > 0) {
                length = [self.inputStream read:buf maxLength:1];
                if (length > 0) {
                    [data appendBytes:(const void *)buf length:1];
                    NSString *string = [[NSString alloc] initWithData:data
                                                             encoding:self.encoding];
                    if (string) {
                        return string;
                    }
                } else {
                    *error = [NSError errorWithDomain:@"planck"
                                                 code:0
                                             userInfo:@{NSLocalizedDescriptionKey:@"Failed to decode."}];
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
