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

#import "MPEdnBase64Codec.h"
#import "MPEdnWriter.h"
#import "MPEdnParser.h"

static MPEdnBase64Codec *sharedInstance;

@implementation MPEdnBase64Codec

+ (void) initialize
{
  if (self == [MPEdnBase64Codec class])
  {
    sharedInstance = [MPEdnBase64Codec new];
  }
}

+ (MPEdnBase64Codec *) sharedInstance
{
  return sharedInstance;
}

- (NSString *) tagName
{
  return @"base64";
}

- (BOOL) canWrite: (id) value
{
  return [value isKindOfClass: [NSData class]];
}

- (void) writeValue: (id) value toWriter: (MPEdnWriter *) writer
{
  [writer outputObject: [value base64EncodedStringWithOptions: 0]];
}

- (id) readValue: (id) value
{
  if ([value isKindOfClass: [NSString class]])
  {
    NSData *data = [[NSData alloc] initWithBase64EncodedString: value options: 0];
    
    if (data)
    {
      return data;
    } else
    {
      return [NSError errorWithDomain: @"MPEdn" code: ERROR_TAG_READER_ERROR
              userInfo: @{NSLocalizedDescriptionKey : @"Bad Base64 data"}];
    }
  } else
  {
    return [NSError errorWithDomain: @"MPEdn" code: ERROR_TAG_READER_ERROR
              userInfo: @{NSLocalizedDescriptionKey : @"Expected a string for Base64-encoded tagged value"}];
  }
}

@end
