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

/**
 * Used by MPEdnParser to represent tagged values that have no reader
 * associated with them.
 */
@interface MPEdnTaggedValue : NSObject<NSCoding>

/**
 * The tag name.
 */
@property (readonly) NSString *tag;

/**
 * The tagged value.
 */
@property (readonly) id value;

/**
 * Create a new instance.
 *
 * NB: The tag is not checked for validity against the EDN spec (the
 * parser will have already checked this for normal usage). If, for
 * some reason you want to use this, it's up to you to ensure the tag
 * name is valid for EDN.
 */
- (instancetype) initWithTag: (NSString *) tag value: (id) value;

@end
