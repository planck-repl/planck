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

#import <Foundation/Foundation.h>

//#import "MPEdnTaggedValueWriter.h"
@protocol MPEdnTaggedValueWriter;

/**
 * DEPRECATED: it seems that this is fixed in iOS 7.1.
 *
 * Force a given number instance to be output as a character.
 *
 * NSNumber is quite broken for representing characters. For example,
 * `[NSNumber numberWithChar: 'a']` produces an NSNumber that
 * correctly indicates it wraps a character, but when using
 * numberWithChar with `\n` it does not (meaning `\n` will be emitted
 * as `10`).
 *
 * As as workaround, you can force a number to be seen as a character
 * using MPEdnTagAsCharacter.
 *
 * See this discussion for more information on the NSNumber issue:
 * http://www.cocoabuilder.com/archive/cocoa/136956-nsnumber-is-completely-broken.html.
 *
 * @see MPEdnIsCharacter()
 */
NSNumber *MPEdnTagAsCharacter (NSNumber *number);

/**
 * DEPRECATED: it seems that this is fixed in iOS 7.1.
 *
 * Test if MPEdnTagAsCharacter() has been used to tag a given number.
 * 
 * @see MPEdnTagAsCharacter()
 */
BOOL MPEdnIsCharacter (NSNumber *number);

/**
 * Converts Cocoa data objects into EDN-formatted strings.
 * 
 * If you want to simply turn an object into an EDN string, use
 * [NSObject(MPEdnWriter) objectToEdnString]. Example:
 *
 *	[myObject objectToEdnString];
 * 
 * See also [NSObject(MPEdnWriter) objectToEdnString] and
 * [NSObject(MPEdnWriter) objectToEdnStringAutoKeywords]
 */
@interface MPEdnWriter : NSObject

/**
 * Add a tag writer to the default global set.
 *
 * The global writer set is used as the base template for new writer
 * instances, which may further customise the set for local use.
 *
 * The default writer set handles the built-in EDN "#uuid" and "#inst"
 * tags.
 */
+ (void) addGlobalTagWriter: (id<MPEdnTaggedValueWriter>) writer;

/**
 * When true (defaults to false), automatically output string keys in
 * NSDictionary as EDN keywords where possible.
 *
 * This may be useful in the case where the dictionary keys are not
 * MPEdnKeyword instances, but you wish to treat them all as such.
 */
@property (readwrite) BOOL useKeywordsInMaps;

/**
 * Take a Cocoa object, and generate an EDN-formatted string.
 *
 * @see [NSObject(MPEdn) objectToEdnString]
 */
- (NSString *) serialiseToEdn: (id) value;

/**
 * Add a custom tag writer.
 *
 * You can use this to extend the EDN writer to support custom tagged
 * types. See MPEdnBase64Codec for an example.
 *
 * This method extends the default global writers: see
 * `addGlobalTagWriter`.
 *
 * Where writers overlap in what they may output, those added later
 * override previous ones.
 *
 * @see [MPEdnParser addTagReader:]
 */
- (void) addTagWriter: (id<MPEdnTaggedValueWriter>) writer;

/**
 * Output an object to this writer.
 *
 * Primarily for use by custom tag codecs.
 */
- (void) outputObject: (id) value;

@end

@interface NSObject (MPEdnWriter)

/**
 * Shortcut to generate an EDN-formatted string for an object.
 *
 *  @see [MPEdnWriter serialiseToEdn]
 */
- (NSString *) objectToEdnString;

/**
 * Shortcut to generate an EDN-formatted string for an object, with
 * automatic conversion of dictionary keys to keywords (i.e. the
 * `useKeywordsInMaps` property set to true).
 *
 *  @see [MPEdnWriter serialiseToEdn]
 */
- (NSString *) objectToEdnStringAutoKeywords;

@end
