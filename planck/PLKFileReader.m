#import "PLKFileReader.h"

@interface PLKFileReader()

@property (nonatomic, strong) NSString* path;
@property (nonatomic) int linesRemaining;

@end

@implementation PLKFileReader

-(id)initWithPath:(NSString*)path
{
    if (self = [super init]) {
        self.path = path;
        self.linesRemaining = 10;
    }
    
    return self;
}

+(PLKFileReader*)open:(NSString*)path
{
    return [[PLKFileReader alloc] initWithPath:path];
}

-(NSString*)read
{
    NSLog(@"simulated read from %@", self.path);
    if (self.linesRemaining) {
        return [NSString stringWithFormat:@"line %d", self.linesRemaining--];
    } else {
        return nil;
    }
}

-(void)close
{
    NSLog(@"Closing %@", self.path);
}

@end
