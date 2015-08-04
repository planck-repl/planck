#import "PLKScript.h"

@implementation PLKScript

- (instancetype)initWithPath:(NSString *)path
{
    self = [super init];
    if (self == nil) {
        return self;
    }
    
    _expression = NO;
    _printNilExpression = NO;
    _content = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:nil];
    
    if (_content == nil) {
        NSLog(@"Could not read file at %@", path);
        exit(1);
    }
    
    return self;
}

- (instancetype)initWithExpression:(NSString *)expression printIfNil:(BOOL)printIfNil
{
    self = [super init];
    if (self == nil) {
        return self;
    }
    
    _expression = YES;
    _printNilExpression = printIfNil;
    _content = [expression copy];
    
    return self;
}

- (instancetype)initWithStdIn
{
    self = [super init];
    if (self == nil) {
        return self;
    }
    
    _expression = NO;
    _printNilExpression = NO;
    
    NSFileHandle *input = [NSFileHandle fileHandleWithStandardInput];
    NSData *inputData = [input readDataToEndOfFile];
    if (inputData.length) {
        NSString *inputString = [[NSString alloc] initWithData: inputData encoding:NSUTF8StringEncoding];
        _content = [inputString stringByTrimmingCharactersInSet: [NSCharacterSet newlineCharacterSet]];
    }
    
    return self;
}

@end
