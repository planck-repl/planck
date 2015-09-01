#import <Foundation/Foundation.h>

@interface PLKScript : NSObject

- (instancetype)initWithPath:(NSString *)sourcePath;
- (instancetype)initWithExpression:(NSString *)sourceText;
- (instancetype)initWithStdIn;

@property (nonatomic, readonly, copy) NSString *sourceType;
@property (nonatomic, readonly, copy) NSString *sourceValue;
@property (nonatomic, readonly, assign, getter=isExpression) BOOL expression;

@end
