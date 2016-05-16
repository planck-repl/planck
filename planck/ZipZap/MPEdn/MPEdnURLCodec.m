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
 *  You must not remove this notice, or any other, from this software.
 */

#import "MPEdnURLCodec.h"
#import "MPEdnWriter.h"
#import "MPEdnParser.h"

@implementation MPEdnURLCodec
{
  NSString *tag;
}

- (instancetype) initWithTag: (NSString *) inTag
{
  if (self = [super init])
  {
    tag = inTag;
  }
  
  return self;
}

- (id) copyWithZone: (NSZone *) zone
{
  return [[MPEdnURLCodec alloc] initWithTag: tag];
}

- (NSString *) tagName
{
  return tag;
}

- (BOOL) canWrite: (id) value
{
  return [value isKindOfClass: [NSURL class]];
}

- (void) writeValue: (id) value toWriter: (MPEdnWriter *) writer
{
  NSAssert (tag, @"No tag specified for MPEdnURLCodec!");
  
  [writer outputObject: [((NSURL *)value) absoluteString]];
}

- (id) readValue: (id) value
{
  NSAssert (tag, @"No tag specified for MPEdnURLCodec!");
  
  if ([value isKindOfClass: [NSString class]])
  {
    NSURL *url = [[NSURL alloc] initWithString: value];

    if (url)
    {
      return url;
    } else
    {
      return [NSError errorWithDomain: @"MPEdn" code: ERROR_TAG_READER_ERROR
              userInfo: @{NSLocalizedDescriptionKey :
                          [NSString stringWithFormat: @"Bad URL: %@", value]}];
    }
  } else
  {
    return [NSError errorWithDomain: @"MPEdn" code: ERROR_TAG_READER_ERROR
              userInfo: @{NSLocalizedDescriptionKey : @"Expected a string for a tagged URL value"}];
  }
}

@end
