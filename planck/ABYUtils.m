#include "ABYUtils.h"

JSValueRef BlockFunctionCallAsFunction(JSContextRef ctx, JSObjectRef function, JSObjectRef thisObject, size_t argc, const JSValueRef argv[], JSValueRef* exception) {
    JSValueRef (^block)(JSContextRef ctx, size_t argc, const JSValueRef argv[]) = (__bridge JSValueRef (^)(JSContextRef ctx, size_t argc, const JSValueRef argv[]))JSObjectGetPrivate(function);
    JSValueRef ret = block(ctx, argc, argv);
    return ret ? ret : JSValueMakeUndefined(ctx);
}

@implementation ABYUtils

+(NSString*)stringForValue:(JSValueRef)value inContext:(JSContextRef)context
{
    JSStringRef JSString = JSValueToStringCopy(context, value, NULL);
    CFStringRef string = JSStringCopyCFString(kCFAllocatorDefault, JSString);
    JSStringRelease(JSString);
    
    return (__bridge_transfer NSString *)string;
}

+(void)setValue:(JSValueRef)value onObject:(JSObjectRef)object forProperty:(NSString*)property inContext:(JSContextRef)context
{
    JSStringRef propertyName = JSStringCreateWithCFString((__bridge CFStringRef)property);
    JSObjectSetProperty(context, object, propertyName, value, 0, NULL);
    JSStringRelease(propertyName);
}

+(JSValueRef)getValueOnObject:(JSObjectRef)object forProperty:(NSString*)property inContext:(JSContextRef)context
{
    JSStringRef propertyName = JSStringCreateWithCFString((__bridge CFStringRef)property);
    JSValueRef rv = JSObjectGetProperty(context, object, propertyName, NULL);
    JSStringRelease(propertyName);
    return rv;
}

+(JSValueRef)evaluateScript:(NSString*)script inContext:(JSContextRef)context
{
    JSStringRef scriptStringRef = JSStringCreateWithCFString((__bridge CFStringRef)script);
    JSValueRef rv = JSEvaluateScript(context, scriptStringRef, NULL, NULL, 0, NULL);
    JSStringRelease(scriptStringRef);
    return rv;
}

+(JSObjectRef)createFunctionWithBlock:(JSValueRef (^)(JSContextRef ctx, size_t argc, const JSValueRef argv[]))block inContext:(JSContextRef)context
{
    static JSClassRef jsBlockFunctionClass;
    if(!jsBlockFunctionClass) {
        JSClassDefinition blockFunctionClassDef = kJSClassDefinitionEmpty;
        blockFunctionClassDef.attributes = kJSClassAttributeNoAutomaticPrototype;
        blockFunctionClassDef.callAsFunction = BlockFunctionCallAsFunction;
        blockFunctionClassDef.finalize = nil;
        jsBlockFunctionClass = JSClassCreate(&blockFunctionClassDef);
    }
    
    return JSObjectMake(context, jsBlockFunctionClass, (void*)CFBridgingRetain(block));
}

+(void)installGlobalFunctionWithBlock:(JSValueRef (^)(JSContextRef ctx, size_t argc, const JSValueRef argv[]))block name:(NSString*)name argList:(NSString*)argList inContext:(JSContextRef)context
{
    NSString* internalObjectName = [NSString stringWithFormat:@"___AMBLY_INTERNAL_%@", name];
    
    [ABYUtils setValue:[ABYUtils createFunctionWithBlock:block inContext:context]
              onObject:JSContextGetGlobalObject(context) forProperty:internalObjectName inContext:context];
    [ABYUtils evaluateScript:[NSString stringWithFormat:@"var %@ = function(%@) { return %@(%@); };", name, argList, internalObjectName, argList] inContext:context];
}

@end