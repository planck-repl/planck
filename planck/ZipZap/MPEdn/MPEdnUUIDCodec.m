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

#import "MPEdnUUIDCodec.h"
#import "MPEdnWriter.h"
#import "MPEdnParser.h"

static MPEdnUUIDCodec *sharedInstance;

@implementation MPEdnUUIDCodec

+ (void) initialize
{
  if (self == [MPEdnUUIDCodec class])
  {
    sharedInstance = [MPEdnUUIDCodec new];
  }
}

+ (MPEdnUUIDCodec *) sharedInstance
{
  return sharedInstance;
}

- (NSString *) tagName
{
  return @"uuid";
}

- (BOOL) canWrite: (id) value
{
  return [value isKindOfClass: [NSUUID class]];
}

- (void) writeValue: (id) value toWriter: (MPEdnWriter *) writer
{
  [writer outputObject: [value UUIDString]];
}

- (id) readValue: (id) value
{
  if ([value isKindOfClass: [NSString class]])
  {
    NSUUID *uuid = [[NSUUID alloc] initWithUUIDString: value];

    if (uuid)
    {
      return uuid;
    } else
    {
      return [NSError errorWithDomain: @"MPEdn" code: ERROR_TAG_READER_ERROR
              userInfo: @{NSLocalizedDescriptionKey :
                          [NSString stringWithFormat: @"Bad UUID: %@", value]}];
    }
  } else
  {
    return [NSError errorWithDomain: @"MPEdn" code: ERROR_TAG_READER_ERROR
              userInfo: @{NSLocalizedDescriptionKey : @"Expected a string for UUID value"}];
  }
}

@end
