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

#import <objc/runtime.h>

#import "MPEdn.h"
#import "MPEdnSymbol.h"
#import "MPEdnDateCodec.h"
#import "MPEdnUUIDCodec.h"
#import "MPEdnTaggedValue.h"

// A token is one of these or just a single character.
typedef enum
{
  TOKEN_NONE = 0xF00000,  // NB: outside UTF-16 range
  TOKEN_END,
  TOKEN_ERROR,
  TOKEN_SET_OPEN,
  TOKEN_NUMBER,
  TOKEN_STRING,
  TOKEN_NAME,
  TOKEN_KEYWORD,
  TOKEN_CHARACTER,
  TOKEN_TAG,
  TOKEN_DISCARD
} Token;

NSString * const MPEDN_TAG_NAME = @"MPEDNTag";

static NSCharacterSet *QUOTE_CHARS;

static NSMutableDictionary *defaultReaders;

static NSDictionary *copy (NSDictionary *dict)
{
  NSMutableDictionary *dictCopy = [dict mutableCopy];
  
  for (id k in dict)
  {
    id v = dictCopy [k];
    
    if ([v respondsToSelector: @selector (copyWithZone:)])
      dictCopy [k] = [v copy];
  }

  return dictCopy;
}

@implementation MPEdnParser
{
  NSString *inputStr;
  NSUInteger startIdx;
  NSUInteger endIdx;
  NSUInteger inputStrLen;
  NSUInteger token;
  id tokenValue;
  NSError *error;
  BOOL keywordsAsStrings;
  BOOL allowUnknownTags;
  NSDictionary *readers;
}

@synthesize keywordsAsStrings;
@synthesize allowUnknownTags;

#pragma mark - Init

+ (void) initialize
{
  if (self == [MPEdnParser class])
  {
    QUOTE_CHARS = [NSCharacterSet characterSetWithCharactersInString: @"\\\""];

    MPEdnDateCodec *dateCodec = [MPEdnDateCodec new];
    
    defaultReaders =
      [@{[dateCodec tagName] : dateCodec,
         [[MPEdnUUIDCodec sharedInstance] tagName] : [MPEdnUUIDCodec sharedInstance]} mutableCopy];
  }
}

+ (void) addGlobalTagReader: (id<MPEdnTaggedValueReader>) reader
{
  @synchronized (self)
  {
    [defaultReaders setObject: reader forKey: [reader tagName]];
  }
}

- (id) init
{
  if (self = [super init])
  {
    readers = copy (defaultReaders);

    allowUnknownTags = YES;
  }
  
  return self;
}

- (void) raiseError: (NSInteger) code message: (NSString *) message, ...
{
  if (!error)
  {
    va_list args;
    va_start (args, message);
    
    NSString *desc = [[NSString alloc] initWithFormat: message arguments: args];

    va_end (args);

    token = TOKEN_ERROR;
    
    error = [[NSError alloc]
             initWithDomain: @"MPEdn" code: code
                                  userInfo: @{NSLocalizedDescriptionKey : desc}];
  }
}

- (void) setInputString: (NSString *) str
{
  startIdx = 0;
  endIdx = 0;
  token = TOKEN_NONE;
  tokenValue = nil;
  error = nil;
  
  inputStr = str;
  inputStrLen = [inputStr length];
  
  [self nextToken];
}

- (NSString *) inputString
{
  return inputStr;
}

- (NSError *) error
{
  return error;
}

- (BOOL) complete
{
  if (error)
  {
    return true;
  } else
  {
    [self skipSpaceAndComments];
  
    return startIdx >= inputStrLen;
  }
}

- (void) addTagReader: (id<MPEdnTaggedValueReader>) reader
{
  NSMutableDictionary *newReaders = [readers mutableCopy];
  
  [newReaders setObject: reader forKey: [reader tagName]];
  
  readers = newReaders;
}

#pragma mark - Tokeniser

static BOOL is_sym_punct (unichar ch)
{
  // NB: '/' is added to the specified EDN set of punctuation allowed in
  // symbol names. The check for its valid use will be done by the parser.
  return (ch >= '!' && ch <= '_' &&
          (ch == '!' || ch == '$' || ch == '%' || ch == '&' || ch == '*' ||
           ch == '+' || ch == '-' || ch == '.' || ch == '=' || ch == '?' ||
           ch == '_' || ch == '/' || ch == ':' || ch == '#'));
}

- (unichar) currentEndIdxChar
{
  return (endIdx < inputStrLen) ? [inputStr characterAtIndex: endIdx] : 0;
}

- (unichar) charAt: (NSUInteger) index
{
  return (index < inputStrLen) ? [inputStr characterAtIndex: index] : 0;
}

- (unichar) advanceStartIdx
{
  startIdx++;
  
  return (startIdx < inputStrLen) ? [inputStr characterAtIndex: startIdx] : 0;
}

- (unichar) advanceEndIdx
{
  // TODO could use rangeOfComposedCharacterSequenceAtIndex here if we wanted
  // to support surrogate pairs outside of strings (and be slower)
  endIdx++;
  
  return (endIdx < inputStrLen) ? [inputStr characterAtIndex: endIdx] : 0;
}

- (unichar) advanceEndIdxToChar: (NSCharacterSet *) charset
{
  if (endIdx < inputStrLen)
  {
    NSRange range =
      [inputStr rangeOfCharacterFromSet: charset
                                options: NSLiteralSearch
                                  range: NSMakeRange (endIdx, inputStrLen - endIdx)];
    
    if (range.location == NSNotFound)
    {
      endIdx = inputStrLen;
      
      return 0;
    } else
    {
      endIdx = range.location;
      
      return [inputStr characterAtIndex: endIdx];
    }
  } else
  {
    return 0;
  }
}

- (unichar) skipSpaceAndComments
{
  unichar ch = [self charAt: startIdx];
  
  // skip white space and comments
  while ((isspace (ch) || ch == ',' || ch == ';') && startIdx < inputStrLen)
  {
    if (ch == ';')
    {
      while (ch != '\n' && startIdx < inputStrLen)
        ch = [self advanceStartIdx];
      
      ch = [self advanceStartIdx];         // skip \n
    } else
    {
      ch = [self advanceStartIdx];
    }
  }
  
  if (endIdx < startIdx)
    endIdx = startIdx;

  return ch;
}

- (id) consumeTokenValue
{
  id value = tokenValue;
  
  [self nextToken];
  
  return value;
}

// NB: this does not handle symbols and keywords containing Unicode code points
// > 0xFFFF (i.e. characters that require representation using UTF-16 surrogate
// pairs). It *does* however support all Unicode points in strings.
- (void) nextToken
{
  startIdx = endIdx;
  tokenValue = nil;
  unichar ch = [self skipSpaceAndComments];

  if (startIdx < inputStrLen)
  {
    if (ch == '{' || ch == '[' || ch == '(' || ch == '}' || ch == ']' || ch == ')')
    {
      token = ch;
      endIdx = startIdx + 1;
    } else if (ch == '"')
    {
      [self readStringToken];
    } else if (isdigit (ch))
    {
      [self readNumberToken];
    } else if (ch == '+' || ch == '-' || ch == '.')
    {
      unichar lookahead = [self charAt: startIdx + 1];
      
      if (isalpha (lookahead) || lookahead == 0)
        [self readNameToken];
      else
        [self readNumberToken];
    } else if (ch == ':')
    {
      [self readKeywordToken];
    } else if (ch == '\\')
    {
      [self readCharacterToken];
    } else if (ch == '#')
    {
      unichar lookahead = [self charAt: startIdx + 1];
      
      if (lookahead == '{')
      {
        token = TOKEN_SET_OPEN;
        endIdx = startIdx + 2;
      } else if (lookahead == '_')
      {
        token = TOKEN_DISCARD;
        endIdx = startIdx + 2;
      } else
      {
        [self readTagName];
      }
    } else if (isalpha (ch) || is_sym_punct (ch))
    {
      [self readNameToken];
    } else
    {
      [self raiseError: ERROR_INVALID_TOKEN
               message: @"Unparseable character %C", ch];
    }
  } else
  {
    token = TOKEN_END;
  }
}

- (void) readNumberToken
{
  unichar ch = [inputStr characterAtIndex: endIdx];
  BOOL isFloat = NO;
  
  if (ch == '+' || ch == '-')
    ch = [self advanceEndIdx];
  
  while (isdigit (ch))
    ch = [self advanceEndIdx];
  
  if (ch == '.')
  {
    isFloat = YES;
    
    do
    {
      ch = [self advanceEndIdx];
    } while (isdigit (ch));
  }

  if (ch == 'e' || ch == 'E')
  {
    ch = [self advanceEndIdx];

    if (ch == '+' || ch == '-')
      ch = [self advanceEndIdx];
    
    while (isdigit (ch))
      ch = [self advanceEndIdx];
  }

  NSString *numberStr =
    [inputStr substringWithRange: NSMakeRange (startIdx, endIdx - startIdx)];

  if (ch == 'm' || ch == 'M')
  {
    [self advanceEndIdx];

    token = TOKEN_NUMBER;
    tokenValue = [NSDecimalNumber decimalNumberWithString: numberStr];
  } else if (ch == 'n' || ch == 'N')
  {
    [self advanceEndIdx];
    
    [self raiseError: ERROR_UNSUPPORTED_FEATURE
             message: @"Big integers (N suffix) are not supported"];
  } else if (isFloat)
  {
    // NB: NSNumberFormatter throws exceptions on error and may or may not parse
    // floats according to the spec. Using strtod and and strtol instead.
  
    const char *numberStrUtf8 =
      [numberStr cStringUsingEncoding: NSUTF8StringEncoding];
    
    char *numberStrEnd = (char *)numberStrUtf8;

    double number = strtod (numberStrUtf8, &numberStrEnd);
    
    if (numberStrEnd - numberStrUtf8 == endIdx - startIdx)
    {
      if ([numberStr characterAtIndex: [numberStr length] - 1] == '.')
      {
        // strtod happily allows "1." as a legal number
        [self raiseError: ERROR_INVALID_NUMBER
                 message: @"Trailing '.' on number: \"%@\"", numberStr];
      } else
      {
        token = TOKEN_NUMBER;
        tokenValue = [NSNumber numberWithDouble: number];
      }
    } else
    {
      [self raiseError: ERROR_INVALID_NUMBER
               message: @"Invalid floating point number: \"%@\"", numberStr];
    }
  } else
  {
    const char *numberStrUtf8 =
      [numberStr cStringUsingEncoding: NSUTF8StringEncoding];
    
    char *numberStrEnd = (char *)numberStrUtf8;

    long int number = strtol (numberStrUtf8, &numberStrEnd, 10);
    
    if (numberStrEnd - numberStrUtf8 == endIdx - startIdx)
    {
      token = TOKEN_NUMBER;
      tokenValue = [NSNumber numberWithLong: number];
    } else
    {
      [self raiseError: ERROR_INVALID_NUMBER
               message: @"Invalid integer: \"%@\"", numberStr];
    }
  }
}

- (void) readCharacterToken
{
  if (startIdx + 1 >= inputStrLen)
  {
    [self raiseError: ERROR_INVALID_CHARACTER
             message: @"Missing character value following \\"];
  } else
  {
    [self advanceEndIdx];
    
    unichar ch;
    
    // read at least one character
    do
    {
      ch = [self advanceEndIdx];
    } while (isalpha (ch));
    
    NSUInteger length = endIdx - startIdx - 1;
    NSNumber *charValue = nil;
    
    if (length == 1)
    {
      unichar c = [inputStr characterAtIndex: startIdx + 1];
      
      if (c < 256)
      {
        charValue = [NSNumber numberWithUnsignedChar: c];
      } else
      {
        // TODO use [NSString stringWithFormat :@"%C", c]?
        [self raiseError: ERROR_INVALID_CHARACTER
                 message: @"MPEdn cannot represent Unicode character points "
                           "greater than 255"];
      }
    } else
    {
      NSString *name =
        [inputStr substringWithRange: NSMakeRange (startIdx + 1, length)];
      
      if ([name isEqualToString: @"newline"])
        charValue = @'\n';
      else if ([name isEqualToString: @"space"])
        charValue = @' ';
      else if ([name isEqualToString: @"tab"])
        charValue = @'\t';
      else if ([name isEqualToString: @"return"])
        charValue = @'\r';
      else
      {
        [self raiseError: ERROR_INVALID_CHARACTER
                 message: @"Unknown character name '%@'", name];
      }
    }
    
    if (charValue)
    {
      token = TOKEN_CHARACTER;
      tokenValue = charValue;
    }
  }
}

- (void) readNameToken
{
  unichar ch;
  
  do
  {
    ch = [self advanceEndIdx];
  } while (isalnum (ch) || is_sym_punct (ch));
  
  token = TOKEN_NAME;
  tokenValue =
    [inputStr substringWithRange: NSMakeRange (startIdx, endIdx - startIdx)];
}

- (void) readKeywordToken
{
  unichar ch;
  
  do
  {
    ch = [self advanceEndIdx];
  } while (isalnum (ch) || is_sym_punct (ch));
  
  NSInteger length = endIdx - startIdx - 1;
  
  if (length > 0)
  {
    token = TOKEN_KEYWORD;
    
    NSString *keyword =
      [inputStr substringWithRange: NSMakeRange (startIdx + 1, length)];
    
    tokenValue = keywordsAsStrings ? keyword : [keyword ednKeyword];
  } else
  {
    [self raiseError: ERROR_INVALID_KEYWORD
             message: @"Empty keyword not allowed"];
  }
}

- (void) readStringToken
{
  endIdx++;  // skip "
  
  unichar ch = [self advanceEndIdxToChar: QUOTE_CHARS];
  
  if (ch == '"')
  {
    token = TOKEN_STRING;
    tokenValue =
      [inputStr substringWithRange: NSMakeRange (startIdx + 1, endIdx - startIdx - 1)];
    
    [self advanceEndIdx]; // skip "
  } else
  {
    NSMutableString *stringValue = [NSMutableString new];
    startIdx++;
    
    do
    {
      if (endIdx > startIdx)
        [stringValue appendString: [inputStr substringWithRange: NSMakeRange (startIdx, endIdx - startIdx)]];
      
      if (ch == '\\')
      {
        ch = [self advanceEndIdx];
        
        switch (ch)
        {
          case 'n':
            [stringValue appendString: @"\n"];
            break;
          case 't':
            [stringValue appendString: @"\t"];
            break;
          case 'r':
            [stringValue appendString: @"\r"];
            break;
          case '\\':
            [stringValue appendString: @"\\"];
            break;
          case '"':
            [stringValue appendString: @"\""];
            break;
          case 0:
            [self raiseError: ERROR_INVALID_ESCAPE
                     message: @"Missing character value in escape"];
            break;
          default:
            [self raiseError: ERROR_INVALID_ESCAPE
                     message: @"Invalid escape sequence: \\%C", ch];
        }

        // swap start and end, advance end to next \ or "
        endIdx++;
        startIdx = endIdx;
        ch = [self advanceEndIdxToChar: QUOTE_CHARS];
      }
    } while (ch != '"' && endIdx < inputStrLen && !error);
    
    if (ch == '"')
    {
      if (endIdx > startIdx)
        [stringValue appendString: [inputStr substringWithRange: NSMakeRange (startIdx, endIdx - startIdx)]];
      
      [self advanceEndIdx]; // skip "
      
      if (!error)
      {
        token = TOKEN_STRING;
        tokenValue = stringValue;
      }
    } else
    {
      [self raiseError: ERROR_UNTERMINATED_STRING
               message: @"Unterminated string"];
    }
  }
}

- (void) readTagName
{
  unichar ch;
  
  do
  {
    ch = [self advanceEndIdx];
  } while (isalnum (ch) || is_sym_punct (ch));
  
  NSInteger tagLen = endIdx - startIdx - 1;
  
  if (tagLen > 0)
  {
    token = TOKEN_TAG;
    tokenValue = [inputStr substringWithRange: NSMakeRange (startIdx + 1, tagLen)];
  } else
  {
    [self raiseError: ERROR_INVALID_TAG
             message: @"Empty tag not allowed"];
  }
}

#pragma mark - Parser

- (NSMutableSet *) newSet
{
  return [NSMutableSet new];
}

- (NSMutableArray *) newArray
{
  return [NSMutableArray new];
}

- (NSMutableDictionary *) newDictionary
{
  return [NSMutableDictionary new];
}

- (id) parseString: (NSString *) str
{
  self.inputString = str;
  
  id value = [self parseNextValue];
  
  if (self.complete)
  {
    return value;
  } else
  {
    startIdx = endIdx = inputStrLen;

    [self raiseError: ERROR_MULTIPLE_VALUES
             message: @"More than one value supplied, but only one expected"];
    
    return nil;
  }
}

- (id) parseNextValue
{
  return [self parseExpr];
}

- (id) parseExpr
{
  switch (token)
  {
    case TOKEN_NUMBER:
    case TOKEN_STRING:
    case TOKEN_KEYWORD:
    case TOKEN_CHARACTER:
      return [self consumeTokenValue];
    case '{':
      return [self parseMap];
    case '[':
    case '(':
      return [self parseList];
    case TOKEN_SET_OPEN:
      return [self parseSet];
    case TOKEN_NAME:
      return [self parseName];
    case TOKEN_TAG:
      return [self parseTag];
    case TOKEN_DISCARD:
      return [self parseDiscard];
    default:
    {
      [self raiseError: ERROR_NO_EXPRESSION
               message: @"No value found in expression"];

      return nil;
    }
  }
}

- (NSSet *) parseSet
{
  NSMutableSet *set = [self newSet];
  
  [self nextToken];
  
  while (token != '}' && !error && endIdx < inputStrLen)
  {
    id value = [self parseExpr];
    
    if (value)
      [set addObject: value];
  }
  
  if (token == '}')
  {
    [self nextToken];
    
    return set;
  } else
  {
    [self raiseError: ERROR_UNTERMINATED_COLLECTION
             message: @"Unterminated set (missing '}')"];
    
    return nil;
  }
}

- (NSDictionary *) parseMap
{
  NSMutableDictionary *map = [self newDictionary];
  
  [self nextToken];
  
  while (token != '}' && !error && endIdx < inputStrLen)
  {
    id key = [self parseExpr];
    id value = [self parseExpr];

    if (key && value)
      [map setObject: value forKey: key];
  }
  
  if (token == '}')
  {
    [self nextToken];
    
    return map;
  } else
  {
    [self raiseError: ERROR_UNTERMINATED_COLLECTION
             message: @"Unterminated map (missing '}')"];
    
    return nil;
  }
}

- (NSArray *) parseList
{
  unichar end = token == '[' ? ']' : ')';
  
  NSMutableArray *list = [self newArray];
  
  [self nextToken];
  
  while (token != end && !error && endIdx < inputStrLen)
  {
    id value = [self parseExpr];
    
    if (value)
      [list addObject: value];
  }
  
  if (token == end)
  {
    [self nextToken];
    
    return list;
  } else
  {
    [self raiseError: ERROR_UNTERMINATED_COLLECTION
             message: @"Unterminated list (missing '%C')", end];
    
    return nil;
  }
}

- (id) parseName
{
  id value = [self consumeTokenValue];
  
  // TODO check symbol namespace ('/) validity
  if ([value isEqualToString: @"true"])
    return @YES;
  else if ([value isEqualToString: @"false"])
    return @NO;
  if ([value isEqualToString: @"nil"])
    return [NSNull null];
  else
    return [MPEdnSymbol symbolWithName: value];
}

- (id) parseTag
{
  NSString *tag = [self consumeTokenValue];
  
  if (token != TOKEN_TAG)
  {
    id value = [self parseExpr];
    
    if (!error)
    {
      id<MPEdnTaggedValueReader> reader = [readers objectForKey: tag];
      
      if (reader)
      {
        id tagValue = [reader readValue: value];
        
        if ([tagValue isKindOfClass: [NSError class]])
        {
          token = TOKEN_ERROR;
          error = (NSError *)tagValue;
          
          return nil;
        } else
        {
          return tagValue;
        }
      } else
      {
        if (allowUnknownTags)
        {
          return [[MPEdnTaggedValue alloc] initWithTag: tag value: value];
        } else
        {
          [self raiseError: ERROR_NO_READER_FOR_TAG
                message: @"Do not know how to handle value for tag #%@", tag];
        }
        
        return nil;
      }
    } else
    {
      return nil;
    }
  } else
  {
    [self raiseError: ERROR_INVALID_TAG
             message: @"Cannot follow a tag with another tag"];
    
    return nil;
  }
}

- (id) parseDiscard
{
  [self nextToken];
  [self parseExpr];
  
  if (token != TOKEN_END && token != TOKEN_ERROR)
  {
    return [self parseExpr];
  } else
  {
    [self raiseError: ERROR_INVALID_DISCARD
             message: @"No expression following discard (#_) symbol"];
    
    return nil;
  }
}

@end

@implementation NSString (MPEdnParser)

- (id) ednStringToObject
{
  return [[MPEdnParser new] parseString: self];
}

- (id) ednStringToObjectNoKeywords
{
  MPEdnParser *parser = [MPEdnParser new];
  
  parser.keywordsAsStrings = YES;
  
  return [parser parseString: self];
}

@end
