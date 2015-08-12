//
//  PLKIO.m
//  planck
//
//  Created by Benedikt Terhechte on 12/08/15.
//  Copyright © 2015 FikesFarm. All rights reserved.
//

#import "PLKIO.h"

@interface PLKFile()
@property (retain) NSString *path;
@end

@implementation PLKFile

+(PLKFile*)file:(NSString*)path {
    PLKFile *aFile = [[PLKFile alloc] init];
    aFile.path = path;
    return aFile;
}

- (void) deleteFile {
    NSFileManager *manager = [NSFileManager defaultManager];
    NSError *error = nil;
    [manager removeItemAtPath:self.path error:&error];
    
    if (error != nil) {
        // FIXME: Should throw an exception, or return something indicating that there
        // was an error
        NSLog(@"Error: %@", error);
        return;
    }
}
@end

NSArray *cljs_file_seq(PLKFile *input) {
    // Create a local file manager instance
    NSFileManager *localFileManager=[[NSFileManager alloc] init];
    
    BOOL isDirectory = NO;
    BOOL exists = [localFileManager fileExistsAtPath:input.path isDirectory:&isDirectory];
    
    
    if (!isDirectory || !exists) {
        NSLog(@"Error: file isn't a directory or doesn't exist");
        return @[];
    }
    
    // Request the two properties the method uses, name and isDirectory
    // Ignore hidden files
    NSURL *inputURL = [NSURL fileURLWithPath:input.path];
    NSDirectoryEnumerator *dirEnumerator = [localFileManager enumeratorAtURL:inputURL
                                                  includingPropertiesForKeys:[NSArray arrayWithObjects:NSURLNameKey,
                                                                              NSURLIsDirectoryKey,nil]
                                                                     options:NSDirectoryEnumerationSkipsHiddenFiles
                                                                errorHandler:nil];
    
    // An array to store the all the enumerated file names in
    NSMutableArray *theArray=[NSMutableArray array];
    
    // Enumerate the dirEnumerator results, each value is stored in allURLs
    for (NSURL *theURL in dirEnumerator) {
        
        // Retrieve the file name. From NSURLNameKey, cached during the enumeration.
        NSString *fileName;
        [theURL getResourceValue:&fileName forKey:NSURLNameKey error:NULL];
        
        // Retrieve whether a directory. From NSURLIsDirectoryKey, also
        // cached during the enumeration.
        NSNumber *isDirectory;
        [theURL getResourceValue:&isDirectory forKey:NSURLIsDirectoryKey error:NULL];
        
        [theArray addObject: [PLKFile file:theURL.path]];
    }
    
    return theArray.copy;
}

