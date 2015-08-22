#import "PLKUtils.h"

NSStringEncoding encodingFromString(NSString *encoding_in, NSStringEncoding defaultEncoding) {
    NSStringEncoding encoding = defaultEncoding;
    if (encoding_in) {
        CFStringEncoding cfEncoding = CFStringConvertIANACharSetNameToEncoding((__bridge CFStringRef)encoding_in);
        // If there was no valid encoding, continue with UTF8
        if (cfEncoding != kCFStringEncodingInvalidId) {
            encoding = CFStringConvertEncodingToNSStringEncoding(cfEncoding);
        }
    }
    return encoding;
}

@implementation PLKUtils

@end
