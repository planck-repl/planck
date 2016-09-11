#import "PLKSh.h"
#import "PLKUtils.h"

id firstObject(NSArray* arr) {
    return arr.count ? arr[0] : nil;
}

NSDictionary* cljs_shell(NSArray *args, id arg_in, NSString *encoding_in, NSString *encoding_out, NSDictionary *env, NSString *dir) {
    
    NSTask *aTask = [[NSTask alloc] init];
    
    // make sure all args are strings
    NSMutableArray *stringArgs = @[].mutableCopy;
    for (id argument in args)[stringArgs addObject:[NSString stringWithFormat:@"%@", argument]];
    
    // set the executable, add a path if there isn't one
    NSString *executable = firstObject(stringArgs);
    NSFileManager *fileManager = [NSFileManager defaultManager];
    if ([fileManager fileExistsAtPath:executable])
        [aTask setLaunchPath:firstObject(stringArgs)];
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
            [aTask setLaunchPath:firstObject(stringArgs)];
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
    
    NSLock* outLock = [[NSLock alloc] init];
    NSMutableData* outData = [[NSMutableData alloc] init];
    [[aTask.standardOutput fileHandleForReading] setReadabilityHandler:^(NSFileHandle *file) {
        NSData *data = [file availableData];
        [outLock lock];
        [outData appendData:data];
        [outLock unlock];
    }];
    
    NSLock* errLock = [[NSLock alloc] init];
    NSMutableData* errData = [[NSMutableData alloc] init];
    [[aTask.standardError fileHandleForReading] setReadabilityHandler:^(NSFileHandle *file) {
        NSData *data = [file availableData];
        [errLock lock];
        [errData appendData:data];
        [errLock unlock];
    }];
    
    // We'll block during execution
    @try {
        [aTask launch];
        [aTask waitUntilExit];
    }
    @catch (NSException *exception) {
        [outPipe.fileHandleForReading closeFile];
        [errPipe.fileHandleForReading closeFile];
        return @{@"exit": @(-1),
                 @"err": exception.description};
    }
    
    [[aTask.standardOutput fileHandleForReading] setReadabilityHandler:nil];
    [[aTask.standardError fileHandleForReading] setReadabilityHandler:nil];
    
    NSStringEncoding encoding = encodingFromString(encoding_out, NSUTF8StringEncoding);
    
    // sh docs say sub-process's stdout (as byte[] or String), however we'll always return string
    [outLock lock];
    NSString *outString = [[NSString alloc] initWithData:outData encoding:encoding];
    if (outString == nil) {
        outString = [[NSString alloc] initWithData:outData encoding:NSASCIIStringEncoding];
    }
    [outLock unlock];

    // sub-process's stderr (String via platform default encoding)
    [errLock lock];
    NSString *errString = [[NSString alloc] initWithData:errData encoding:NSUTF8StringEncoding];
    if (errString == nil) {
        errString = [[NSString alloc] initWithData:errData encoding:NSASCIIStringEncoding];
    }
    [errLock unlock];
    
    [outPipe.fileHandleForReading closeFile];
    [errPipe.fileHandleForReading closeFile];
    
    outString = outString ? outString : @"";
    errString = errString ? errString : @"";

    return @{@"exit": @(aTask.terminationStatus),
             @"out": outString,
             @"err": errString};
}
