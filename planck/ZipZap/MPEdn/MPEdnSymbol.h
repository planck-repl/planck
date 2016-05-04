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

/**
 * Instances of this class are created by MPEdnParser to represent
 * parsed EDN symbols.
 *
 * The MPEdnWriter class also supports outputting MPEdnSymbol's.
 */
@interface MPEdnSymbol : NSObject
{
  NSString *name;
}

/**
 * The symbol name.
 */
@property (readonly) NSString *name;

/**
 * Create a new symbol instance.
 */
+ (MPEdnSymbol *) symbolWithName: (NSString *) name;

/**
 * Convenience method that simply returns the symbol name. The
 * `ednName` method can be used to compare EDN keywords, symbols and
 * string names without regard to type.
 */
- (NSString *) ednName;

@end
