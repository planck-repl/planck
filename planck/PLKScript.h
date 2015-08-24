#import <Foundation/Foundation.h>

@interface PLKScript : NSObject

- (instancetype)initWithPath:(NSString *)path;
- (instancetype)initWithExpression:(NSString *)source printIfNil:(BOOL)printIfNil;
- (instancetype)initWithStdIn;

@property (nonatomic, readonly, copy) NSString *source;
@property (nonatomic, readonly, copy) NSString *lang;
@property (nonatomic, readonly, copy) NSString *path;
@property (nonatomic, readonly, assign, getter=isExpression) BOOL expression;
@property (nonatomic, readonly, assign, getter=printNilExpression) BOOL printNilExpression;

@end
