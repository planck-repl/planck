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

@protocol MPEdnTaggedValueReader;

/**
 * Codes for parse errors reported in EdnParser.error.
 */
typedef enum
{
  ERROR_OK,
  ERROR_INVALID_TOKEN,
  ERROR_INVALID_NUMBER,
  ERROR_NO_EXPRESSION,
  ERROR_UNSUPPORTED_FEATURE,
  ERROR_MULTIPLE_VALUES,
  ERROR_INVALID_ESCAPE,
  ERROR_UNTERMINATED_STRING,
  ERROR_INVALID_KEYWORD,
  ERROR_INVALID_DISCARD,
  ERROR_INVALID_CHARACTER,
  ERROR_INVALID_TAG,
  ERROR_UNTERMINATED_COLLECTION,
  ERROR_NO_READER_FOR_TAG,
  ERROR_TAG_READER_ERROR
} MPEdnParserErrorCode;

/**
 * Parses values encoded in EDN (extensible data notation) into Cocoa
 * objects. See http://https://github.com/edn-format/edn.
 *
 * If you want to simply parse a string containing EDN-encoded data,
 * you can use the [NSString(MPEdn) ednStringToObject] shortcut.
 * 
 * Example: parse a string containing a single EDN-encoded value:
 * 
 * 	MPEdnParser *parser = [MPEdnParser new];
 *
 *	id value = [parser parseString: @"{:a 1 :b foo}"];
 *	// Or just [@"{:a 1 :b foo}" ednStringToObject]
 *
 *	NSLog (@"Value is a map: %@", value);
 *
 * Example: parse all values in a string that may have zero or more
 * EDN values using parseNextValue :
 *
 *	MPEdnParser *parser = [MPEdnParser new];
 *	parser.inputString = @"1 \"abc\" {:a 1, :foo [1 2 3]}";
 *
 *	while (!parser.complete)
 *	{
 *	  id value = [parser parseNextValue];
 *	   
 *	  NSLog (@"Value %@", value);
 *	}
 *
 * On a parse error, the error property will be set.
 *
 * See [NSString(MPEdnParser) ednStringToObject]
 * and [NSString(MPEdnParser) ednStringToObjectKeywordsAsStrings]
 */
@interface MPEdnParser : NSObject

/**
 * Add a tag reader to the default global set.
 *
 * The global tag reader set is used as the base template for new
 * parser instances, which may further customise the set for local
 * use.
 *
 * The default reader set handles the built-in EDN "#uuid" and "#inst"
 * tags.
 */
+ (void) addGlobalTagReader: (id<MPEdnTaggedValueReader>) reader;

/**
 * The string to parse.
 * 
 * You typically set this property and then use parseNextValue (usually in a 
 * loop gated by the complete property).
 */
@property (readwrite, retain) NSString *inputString;

/**
 * Set to true (the default is false) to instantiate all keywords as
 * `NSString` instances rather than `MPEdnKeyword`.
 */
@property (readwrite) BOOL keywordsAsStrings;

/** 
 * Set when the parser encounters a parse error.
 *
 * Nil when no error. Error codes are enumerated in
 * MPEdnParserErrorCode.
 *
 * @see MPEdnParserErrorCode
 */
@property (readonly) NSError *error;

/**
 * Becomes true when there are no more expressions to be parsed or the
 * parser encounters an error.
 *
 * This is usually as the loop control when parsing multiple
 * expressions with parseNextValue. See class documentation for
 * MPEdnParser for an example.
 */
@property (readonly) BOOL complete;

/**
 * When set (the default), allow any tag regardless of whether we have
 * a reader for it: in this case `MPEdnTaggedValue` instances will be
 * used to represent unknown tagged values.
 *
 * NB: MPEdnWriter handles outputting MPEdnTaggedValue's correctly, so
 * enabling allowUnknownTags allows round-tripping of any EDN,
 * regardless of whether all tag types are known.
 */
@property (readwrite) BOOL allowUnknownTags;

/**
 * Parse a string containing a single EDN value.
 *
 * This is essentially a shortcut for setting the inputString property
 * and then calling parseNextValue. It also checks that there is only
 * a single EDN value to be parsed and raises an error if not.
 *
 * This may be called multiple times for the same parser instance to
 * parse muliple EDN input strings.
 * 
 * @param str The string to parse.
 *
 * @return The parsed value, or nil on error (in which case the error
 * property will describe the problem).
 *
 * @see parseNextValue
 * @see [NSString(MPEdn) ednStringToObject]
 */
- (id) parseString: (NSString *) str;

/**
 * Parse and return the next value from the current input string.
 * 
 * Example: parse all values in a string that may have zero or more
 * EDN values:
 *
 *	MPEdnParser *parser = [MPEdnParser new];
 *	parser.inputString = @"1 \"abc\" {:a 1, :foo [1 2 3]}";
 *
 *	while (!parser.complete)
 *	{
 *	  id value = [parser parseNextValue];
 *	   
 *	  NSLog (@"Value %@", value);
 *	}
 *
 * @see parseString
 * @see [NSString(MPEdn) ednStringToObject]
 */
- (id) parseNextValue;

/**
 * Add a custom tag reader.
 *
 * You can use this to extend the EDN parser to support custom tagged
 * types. See MPEdnBase64Codec for an example.
 *
 * This method extends the default global readers: see
 * `addGlobalTagReader`.
 *
 * @see [MPEdnWriter addTagWriter:]
 */
- (void) addTagReader: (id<MPEdnTaggedValueReader>) reader;

/**
 * Called by parser to create a new set instance.
 *
 * May be overridden to to use a custom set implementation.
 */
- (NSMutableSet *) newSet;

/**
 * Called by parser to create a new array instance. 
 *
 * May be overridden to to use a custom array implementation.
 */
- (NSMutableArray *) newArray;

/**
 * Called by parser to create a new dictionary instance.
 * 
 * May be overridden to to use a custom dictionary implementation.
 */
- (NSMutableDictionary *) newDictionary;

@end

@interface NSString (MPEdnParser)

/**
 * Shortcut to parse a single string with [MPEdnParser parseString:].
 */
- (id) ednStringToObject;

/**
 * Shortcut to parse a single string with [MPEdnParser parseString:]
 * with the `keywordsAsStrings` property set to true.
 */
- (id) ednStringToObjectNoKeywords;

@end
