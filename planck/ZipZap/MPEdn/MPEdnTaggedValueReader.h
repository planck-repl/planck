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
 * Protocol implemented by custom EDN tag readers.
 *
 * Custom readers can be registered using [MPEdnParser addTagReader:].
 */
@protocol MPEdnTaggedValueReader<NSObject>

@required

/**
 * The name of the tag used to identify values handled by this reader
 * (not including the "#").
 */
- (NSString *) tagName;

/**
 * Read (translate) a value from the parser into the end-product
 * value.
 *
 * Return an instance of NSError with domain "MPEdn", code
 * `ERROR_TAG_READER_ERROR`, and user info with
 * `NSLocalizedDescriptionKey` set to the error message if the value
 * cannot be converted.
 */
- (id) readValue: (id) value;

@end
