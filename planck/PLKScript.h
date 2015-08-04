#import <Foundation/Foundation.h>

@interface PLKScript : NSObject

- (instancetype)initWithPath:(NSString *)path;
- (instancetype)initWithExpression:(NSString *)expression printIfNil:(BOOL)printIfNil;
- (instancetype)initWithStdIn;

@property (nonatomic, readonly, copy) NSString *content;
@property (nonatomic, readonly, assign, getter=isExpression) BOOL expression;
@property (nonatomic, readonly, assign, getter=printNilExpression) BOOL printNilExpression;

@end
