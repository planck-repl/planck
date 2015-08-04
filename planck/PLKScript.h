#import <Foundation/Foundation.h>

@interface PLKScript : NSObject

- (instancetype)initWithPath:(NSString *)path;
- (instancetype)initWithExpression:(NSString *)expression;
- (instancetype)initWithStdIn;

@property (nonatomic, readonly, copy) NSString *content;
@property (nonatomic, readonly, assign, getter=isExpression) BOOL expression;

@end
