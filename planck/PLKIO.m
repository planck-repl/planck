#import "PLKIO.h"

NSArray *cljs_file_seq(NSString* path) {
    // Create a local file manager instance
    NSFileManager *localFileManager=[[NSFileManager alloc] init];
    
    BOOL isDirectory = NO;
    BOOL exists = [localFileManager fileExistsAtPath:path isDirectory:&isDirectory];
    
    
    if (!isDirectory || !exists) {
        NSLog(@"Error: file isn't a directory or doesn't exist");
        return @[];
    }
    
    // Request the two properties the method uses, name and isDirectory
    // Ignore hidden files
    NSURL *inputURL = [NSURL fileURLWithPath:path];
    NSDirectoryEnumerator *dirEnumerator = [localFileManager enumeratorAtURL:inputURL
                                                  includingPropertiesForKeys:[NSArray arrayWithObjects:NSURLNameKey,
                                                                              NSURLIsDirectoryKey,nil]
                                                                     options:NSDirectoryEnumerationSkipsHiddenFiles
                                                                errorHandler:nil];
    
    // An array to store the all the enumerated file names in
    NSMutableArray *theArray=[NSMutableArray array];
    [theArray addObject:path];
    
    // Enumerate the dirEnumerator results, each value is stored in allURLs
    for (NSURL *theURL in dirEnumerator) {
        
        // Retrieve the file name. From NSURLNameKey, cached during the enumeration.
        NSString *fileName;
        [theURL getResourceValue:&fileName forKey:NSURLNameKey error:NULL];
        
        // Retrieve whether a directory. From NSURLIsDirectoryKey, also
        // cached during the enumeration.
        NSNumber *isDirectory;
        [theURL getResourceValue:&isDirectory forKey:NSURLIsDirectoryKey error:NULL];
        
        [theArray addObject: theURL.path];
    }
    
    return theArray.copy;
}

