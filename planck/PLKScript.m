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
    _source = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:nil];
    _path = path;
    _lang = [path hasSuffix:@".js"] ? @"js" : @"cljs";
    
    if (_source == nil) {
        NSLog(@"Could not read file at %@", path);
        exit(1);
    }
    
    return self;
}

- (instancetype)initWithExpression:(NSString *)source printIfNil:(BOOL)printIfNil
{
    self = [super init];
    if (self == nil) {
        return self;
    }
    
    _expression = YES;
    _printNilExpression = printIfNil;
    _source = [source copy];
    _path = nil;
    _lang = @"cljs";
    
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
    _path = nil;
    _lang = @"cljs";
    
    NSFileHandle *input = [NSFileHandle fileHandleWithStandardInput];
    NSData *inputData = [input readDataToEndOfFile];
    if (inputData.length) {
        NSString *inputString = [[NSString alloc] initWithData: inputData encoding:NSUTF8StringEncoding];
        _source = [inputString stringByTrimmingCharactersInSet: [NSCharacterSet newlineCharacterSet]];
    }
    
    return self;
}

@end
