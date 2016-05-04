/*
 *  MPEdn: An EDN (extensible data notation) I/O library for OS X and
 *  iOS. See https://github.com/scramjet/mpedn and
 *  https://github.com/edn-format/edn.
 *
 *  Copyright (c) 2013 Matthew Phillips <m@mattp.name>
 *
 *  The use and distribution terms for this software are covered by
 *  the Eclipse Public License 1.0
 *  (http://opensource.org/licenses/eclipse-1.0.php). By using this
 *  software in any fashion, you are agreeing to be bound by the terms
 *  of this license.
 *
 * You must not remove this notice, or any other, from this software.
 */

#import "MPEdnWriter.h"
#import "MPEdnSymbol.h"
#import "MPEdnKeyword.h"
#import "MPEdnTaggedValueWriter.h"
#import "MPEdnDateCodec.h"
#import "MPEdnUUIDCodec.h"
#import "MPEdnTaggedValue.h"

#import <objc/runtime.h>

const NSString *MPEDN_CHARACTER_TAG = @"MPEDN_CHARACTER_TAG";

NSNumber *MPEdnTagAsCharacter (NSNumber *number)
{
  objc_setAssociatedObject (number, (__bridge const void *)MPEDN_CHARACTER_TAG,
                            MPEDN_CHARACTER_TAG, OBJC_ASSOCIATION_ASSIGN);

  return number;
}

BOOL MPEdnIsCharacter (NSNumber *number)
{
  return objc_getAssociatedObject (number, (__bridge const void *)MPEDN_CHARACTER_TAG) != nil;
}

static NSMutableArray *copy (NSArray *array)
{
  NSMutableArray *arrayCopy = [NSMutableArray arrayWithCapacity: [array count]];
  
  for (id i in array)
  {
    if ([i respondsToSelector: @selector (copyWithZone:)])
      [arrayCopy addObject: [i copy]];
    else
      [arrayCopy addObject: i];
  }

  return arrayCopy;
}

static NSCharacterSet *QUOTE_CHARS;
static NSMutableArray *defaultWriters;

@implementation MPEdnWriter
{
  NSMutableString *outputStr;
  BOOL useKeywordsInMaps;
  NSArray *writers;
}

+ (void) initialize
{
  if (self == [MPEdnWriter class])
  {
    QUOTE_CHARS = [NSCharacterSet characterSetWithCharactersInString: @"\\\"\n\r"];
    
    defaultWriters =
      [@[[MPEdnDateCodec new], [MPEdnUUIDCodec sharedInstance]] mutableCopy];
  }
}

+ (void) addGlobalTagWriter: (id<MPEdnTaggedValueWriter>) writer
{
  @synchronized (self)
  {
    [defaultWriters insertObject: writer atIndex: 0];
  }
}

- (id) init
{
  if (self = [super init])
  {
    useKeywordsInMaps = NO;
    writers = copy (defaultWriters);
  }

  return self;
}

- (BOOL) useKeywordsInMaps
{
  return useKeywordsInMaps;
}

- (void) setUseKeywordsInMaps: (BOOL) newValue
{
  useKeywordsInMaps = newValue;
}

- (void) addTagWriter: (id<MPEdnTaggedValueWriter>) writer
{
  NSMutableArray *newWriters = [writers mutableCopy];
  
  [newWriters insertObject: writer atIndex: 0];
  
  writers = newWriters;
}

- (id<MPEdnTaggedValueWriter>) tagWriterFor: (id) value
{
  for (id<MPEdnTaggedValueWriter> writer in writers)
  {
    if ([writer canWrite: value])
      return writer;
  }
  
  return nil;
}

- (NSString *) serialiseToEdn: (id) value;
{
  outputStr = [NSMutableString new];
  
  [self outputObject: value];
  
  return outputStr;
}

- (void) outputObject: (id) value
{
  if (value == nil || value == [NSNull null])
    [outputStr appendString: @"nil"];
  else if ([value isKindOfClass: [NSNumber class]])
    [self outputNumber: value];
  else if ([value isKindOfClass: [NSString class]])
    [self outputString: value];
  else if ([value isKindOfClass: [MPEdnKeyword class]])
    [self outputKeyword: value];
  else if ([value isKindOfClass: [NSDictionary class]])
    [self outputDictionary: value];
  else if ([value isKindOfClass: [NSArray class]])
    [self outputArray: value];
  else if ([value isKindOfClass: [NSSet class]])
    [self outputSet: value];
  else if ([value isKindOfClass: [MPEdnSymbol class]])
    [self outputSymbol: value];
  else
  {
    id<MPEdnTaggedValueWriter> tagWriter = [self tagWriterFor: value];
    
    if (tagWriter)
    {
      [outputStr appendFormat: @"#%@ ", [tagWriter tagName]];
      
      [tagWriter writeValue: value toWriter: self];
    } else if ([value isKindOfClass: [MPEdnTaggedValue class]])
    {
      [outputStr appendFormat: @"#%@ ", ((MPEdnTaggedValue *)value).tag];
      
      [self outputObject: ((MPEdnTaggedValue *)value).value];
    } else
    {
      [NSException raise: @"MPEdnWriterException"
                  format: @"Don't know how to handle value of type %@ ", [value class]];
    }
  }
}

- (void) outputNumber: (NSNumber *) value
{
  if ([value isKindOfClass: [NSDecimalNumber class]])
  {
    [outputStr appendString: [value stringValue]];
    [outputStr appendString: @"M"];
  } else
  {
    switch ([value objCType] [0])
    {
      case 'i':
      case 'q':
      case 's':
        if (MPEdnIsCharacter (value))
          [outputStr appendFormat: @"\\%c", [value charValue]];
        else
          [outputStr appendFormat: @"%@", value];
        break;
      case 'd':
        [outputStr appendFormat: @"%.15E", [value doubleValue]];
        break;
      case 'f':
        [outputStr appendFormat: @"%.7E", [value doubleValue]];
        break;
      case 'c':
      {
        if ([NSStringFromClass ([value class]) isEqualToString: @"__NSCFBoolean"])
          [outputStr appendString: [value boolValue] ? @"true" : @"false"];
        else
          [outputStr appendFormat: @"\\%c", [value charValue]];

        break;
      default:
        [NSException raise: @"MPEdnWriterException"
                    format: @"Don't know how to handle NSNumber "
                             "value %@, class %@", value, [value class]];
      }
    }
  }
}

- (void) outputString: (NSString *) value
{
  NSRange quoteRange = [value rangeOfCharacterFromSet: QUOTE_CHARS];
  
  if (quoteRange.location == NSNotFound)
  {
    [outputStr appendFormat: @"\"%@\"", value];
  } else
  {
    NSUInteger start = 0;
    NSUInteger valueLen = [value length];
    
    [outputStr appendString: @"\""];
    
    do
    {
      if (quoteRange.location > start)
        [outputStr appendString: [value substringWithRange: NSMakeRange (start, quoteRange.location - start)]];

      unichar quoteCh = [value characterAtIndex: quoteRange.location];
      
      switch (quoteCh)
      {
        case '\n':
          [outputStr appendString: @"\\n"];
          break;
        case '\r':
          [outputStr appendString: @"\\r"];
          break;
        default:
          [outputStr appendFormat: @"\\%C", quoteCh];
      }

      start = quoteRange.location + 1;
      
      if (start < valueLen)
      {
        quoteRange = [value rangeOfCharacterFromSet: QUOTE_CHARS
                                            options: NSLiteralSearch
                                              range: NSMakeRange (start, valueLen - start)];
      }
    } while (start < valueLen && quoteRange.location != NSNotFound);
    
    if (start < valueLen)
      [outputStr appendString: [value substringWithRange: NSMakeRange (start, valueLen - start)]];
    
    [outputStr appendString: @"\""];
  }
}

- (void) outputKeyword: (MPEdnKeyword *) value
{
  [outputStr appendString: @":"];
  [outputStr appendString: [value ednName]];
}

- (BOOL) outputKeywordNamed: (NSString *) name
{
  if ([MPEdnKeyword isValidKeyword: name])
  {
    [outputStr appendString: @":"];
    [outputStr appendString: name];
    
    return YES;
  } else
  {
    return NO;
  }
}

- (void) outputDictionary: (NSDictionary *) value
{
  BOOL firstItem = YES;

  [outputStr appendString: @"{"];

  for (id key in value)
  {
    if (!firstItem)
      [outputStr appendString: @","];
    
    if (useKeywordsInMaps && [key isKindOfClass: [NSString class]])
    {
      if (![self outputKeywordNamed: key])
        [self outputObject: key];
    } else
    {
      [self outputObject: key];
    }

    [outputStr appendString: @" "];
    
    [self outputObject: [value objectForKey: key]];
    
    firstItem = NO;
  }
  
  [outputStr appendString: @"}"];
}

- (void) outputArray: (NSArray *) value
{
  BOOL firstItem = YES;
  
  [outputStr appendString: @"["];
  
  for (id item in value)
  {
    if (!firstItem)
      [outputStr appendString: @","];
    
    [self outputObject: item];
    
    firstItem = NO;
  }
  
  [outputStr appendString: @"]"];
}

- (void) outputSet: (NSSet *) value
{
  BOOL firstItem = YES;
  
  [outputStr appendString: @"#{"];
  
  for (id item in value)
  {
    if (!firstItem)
      [outputStr appendString: @","];
    
    [self outputObject: item];
    
    firstItem = NO;
  }
  
  [outputStr appendString: @"}"];
}

- (void) outputSymbol: (MPEdnSymbol *) value
{
  [outputStr appendString: value.name];
}

@end

@implementation NSObject (MPEdnWriter)

- (NSString *) objectToEdnString
{
  return [[MPEdnWriter new] serialiseToEdn: self];
}

- (NSString *) objectToEdnStringAutoKeywords
{
  MPEdnWriter *writer = [MPEdnWriter new];

  writer.useKeywordsInMaps = YES;

  return [writer serialiseToEdn: self];
}

@end
