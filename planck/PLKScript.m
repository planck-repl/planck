#import "PLKScript.h"

@implementation PLKScript

- (instancetype)initWithPath:(NSString *)sourcePath
{
    self = [super init];
    if (self == nil) {
        return self;
    }
    
    _expression = NO;
    _sourceType = @"path";
    _sourceValue = sourcePath;
    
    return self;
}

- (instancetype)initWithExpression:(NSString *)sourceText
{
    self = [super init];
    if (self == nil) {
        return self;
    }
    
    _expression = YES;
    _sourceType = @"text";
    _sourceValue = sourceText;
    
    return self;
}

- (instancetype)initWithStdIn
{
    self = [super init];
    if (self == nil) {
        return self;
    }
    
    _expression = NO;
    _sourceType = @"text";
    
    NSFileHandle *input = [NSFileHandle fileHandleWithStandardInput];
    NSData *inputData = [input readDataToEndOfFile];
    if (inputData.length) {
        NSString *inputString = [[NSString alloc] initWithData: inputData encoding:NSUTF8StringEncoding];
        _sourceValue = [inputString stringByTrimmingCharactersInSet: [NSCharacterSet newlineCharacterSet]];
    }
    
    return self;
}

@end
