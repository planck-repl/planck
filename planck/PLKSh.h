#import <Foundation/Foundation.h>


/*!
 @param args: The executable and arguments to be executed. The executable should include the full absolute path. i.e.
   @[@"/bin/ls", @"-aul"]
 @param arg_in: The Standard Input (optional). Can be one of the following:
    NSString conforming to URL Syntax: "file:///tmp/test.txt"
    NSString pointing at an *existing* "file: "/tmp/test.txt"
    NSString with string input: "Printing input frmo stdin with funy chars like ' \" $@ &"
    NSFileHandle: A file handle to an open file
    NSPipe: A Pipe that can be written into from somewhere else.
 @param encoding_in: The encoding of the Standard Input in ISO Format. I.e. "windows-1255"
    The default is UTF8
 @param encoding_out: The encoding of the Standard Output in ISO Format. I.e. "windows-1255"
    The default is UTF8
 @param env: A NSDictionary (String: String) with the Environment to be used
 @param dir: A string defining the process dir
 
 @returns NSDictionary: Returns an NSDictionary with the following keys: "out", "err" and "exit".
    "out": NSString: The output of the command (the standard output)
    "err": NSString: The standard error
    "exit": NSNumber: The exit code
 */
NSDictionary* cljs_shell(NSArray *args, id arg_in, NSString *encoding_in, NSString *encoding_out, NSDictionary *env, NSString *dir);