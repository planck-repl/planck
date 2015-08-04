//
//  PLKScript.m
//  planck
//
//  Created by Fabian Canas on 8/4/15.
//  Copyright (c) 2015 FikesFarm. All rights reserved.
//

#import "PLKScript.h"

@implementation PLKScript

- (instancetype)initWithPath:(NSString *)path
{
    self = [super init];
    if (self == nil) {
        return self;
    }
    
    _expression = NO;
    _content = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:nil];
    
    if (_content == nil) {
        NSLog(@"Could not read file at %@", path);
        exit(1);
    }
    
    return self;
}

- (instancetype)initWithExpression:(NSString *)expression
{
    self = [super init];
    if (self == nil) {
        return self;
    }
    
    _expression = YES;
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
    
    NSFileHandle *input = [NSFileHandle fileHandleWithStandardInput];
    NSData *inputData = [input readDataToEndOfFile];
    if (inputData.length) {
        NSString *inputString = [[NSString alloc] initWithData: inputData encoding:NSUTF8StringEncoding];
        _content = [inputString stringByTrimmingCharactersInSet: [NSCharacterSet newlineCharacterSet]];
    }
    
    return self;
}

@end
