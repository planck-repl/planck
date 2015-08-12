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

