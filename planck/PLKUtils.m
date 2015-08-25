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

NSDate* getLastModificationDate(NSString* path) {
    NSDate * fileLastModifiedDate = nil;
    
    NSError * error = nil;
    NSDictionary * attrs = [[NSFileManager defaultManager] attributesOfItemAtPath:path error:&error];
    if (attrs && !error) {
        fileLastModifiedDate = [attrs fileModificationDate];
    }
    return fileLastModifiedDate;
}

// Returns true if pathA is newer than pathB, false if not of something went wrong
BOOL fileIsNewer(NSString* pathA, NSString* pathB) {
    NSDate* dateA = getLastModificationDate(pathA);
    NSDate* dateB = getLastModificationDate(pathB);
    return dateA && dateB && [dateA compare:dateB] == NSOrderedDescending;
}

@implementation PLKUtils

@end
