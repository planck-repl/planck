//
//  Sh.m
//  planck
//
//  Created by Benedikt Terhechte on 04/08/15.
//  Copyright (c) 2015 FikesFarm. All rights reserved.
//

#import "PLKSh.h"

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

NSDictionary* cljs_shell(NSArray *args, id arg_in, NSString *encoding_in, NSString *encoding_out, NSDictionary *env, NSString *dir) {
    
    NSTask *aTask = [[NSTask alloc] init];
    
    // make sure all args are strings
    NSMutableArray *stringArgs = @[].mutableCopy;
    for (id argument in args)[stringArgs addObject:[NSString stringWithFormat:@"%@", argument]];
    
    // set the executable, add a path if there isn't one
    NSString *executable = stringArgs.firstObject;
    NSFileManager *fileManager = [NSFileManager defaultManager];
    if ([fileManager fileExistsAtPath:executable])
        [aTask setLaunchPath:stringArgs.firstObject];
    else {
        NSString* pathString = [[[NSProcessInfo processInfo]environment]objectForKey:@"PATH"];
        NSArray * paths = [pathString componentsSeparatedByString:@":"];
        bool hasPath = false;
        for (NSString *path in paths) {
            NSString *fullPath = [path stringByAppendingPathComponent:executable];
            if ([fileManager fileExistsAtPath:fullPath]) {
                [aTask setLaunchPath:fullPath];
                hasPath = true;
                break;
            }
        }
        // If we couldn't find the executable, set it anyway, it may be a built-in shell command
        if (!hasPath) {
            [aTask setLaunchPath:stringArgs.firstObject];
        }
    }
    
    // Set the arguments
    if (stringArgs.count > 1) {
        [aTask setArguments:[stringArgs subarrayWithRange:NSMakeRange(1, stringArgs.count-1)]];
    }
    
    // if we have :in, set it based on the type
    // if arg_in is a string, see if it is a valid file path that exists, and if not, interpret it as a string
    // if arg_in is a pipe or a file handle, use it directly
    NSError *error = nil;
    if (arg_in != nil) {
        if ([arg_in isKindOfClass:[NSString class]]) {
            NSURL *fileURL = [NSURL URLWithString:arg_in];
            if (fileURL != nil) {
                // The Format is an URL
                [aTask setStandardInput: [NSFileHandle fileHandleForReadingFromURL:fileURL error:&error]];
            } else if ([[NSFileManager defaultManager] fileExistsAtPath:arg_in isDirectory:nil]) {
                // The args is an existing file path
                [aTask setStandardInput: [NSFileHandle fileHandleForReadingAtPath:arg_in]];
            } else {
                // The args is a string
                NSPipe *aPipe = [NSPipe pipe];
                
                // process the *in* encoding
                NSStringEncoding encoding = encodingFromString(encoding_in, NSUTF8StringEncoding);
                
                [aPipe.fileHandleForWriting writeData:[(NSString*)arg_in dataUsingEncoding:encoding]];
                [aPipe.fileHandleForWriting closeFile];
                [aTask setStandardInput: aPipe];
            }
        }
    } else if ([arg_in isKindOfClass:[NSPipe class]]) {
        [aTask setStandardInput:arg_in];
    } else if ([arg_in isKindOfClass:[NSFileHandle class]]) {
        [aTask setStandardInput:arg_in];
    }
    
    // If we had an error during arg_in setup, we fail here with the reason
    if (error) {
        return @{@"exit": @(-1),
                 @"out": @"",
                 @"err": [NSString stringWithFormat:@"Wrong argument for :in. Error: %@", error.localizedDescription]};
    }
    
    // Create the error and output pipes
    NSPipe *outPipe = [NSPipe pipe];
    NSPipe *errPipe = [NSPipe pipe];
    
    [aTask setStandardError: errPipe];
    [aTask setStandardOutput:outPipe];
    
    if (dir != nil) {
        [aTask setCurrentDirectoryPath:dir];
    }
    
    if (env != nil) {
        [aTask setEnvironment:env];
    }
    
    // We'll block during execution
    @try {
        [aTask launch];
        [aTask waitUntilExit];
    }
    @catch (NSException *exception) {
        return @{@"exit": @(-1),
                 @"err": exception.description};
    }
    
    // Read the data from the task
    NSData *outData = [outPipe.fileHandleForReading readDataToEndOfFile];
    NSData *errData = [outPipe.fileHandleForReading readDataToEndOfFile];
    
    NSStringEncoding encoding = encodingFromString(encoding_out, NSUTF8StringEncoding);
    
    // sh docs say sub-process's stdout (as byte[] or String), however we'll always return string
    NSString *outString = [[NSString alloc] initWithData:outData encoding:encoding];
    
    // sub-process's stderr (String via platform default encoding)
    NSString *errString = [[NSString alloc] initWithData:errData encoding:NSUTF8StringEncoding];
    
    return @{@"exit": @(aTask.terminationStatus),
             @"out": outString,
             @"err": errString};
}
