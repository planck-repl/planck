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

#import <Foundation/Foundation.h>

#import "MPEdnTaggedValueReader.h"
#import "MPEdnTaggedValueWriter.h"

/**
 * Read/write `NSURL` values. Since EDN has no standard tag for URL's, this
 * codec requires you you to specify a tag, which should be in its own
 * namespace (e.g "my-namespace/url").
 */
@interface MPEdnURLCodec :
  NSObject<MPEdnTaggedValueReader, MPEdnTaggedValueWriter, NSCopying>

- (instancetype) initWithTag: (NSString *) tag;

@end
