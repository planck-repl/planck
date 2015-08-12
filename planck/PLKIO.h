//
//  PLKIO.h
//  planck
//
//  Created by Benedikt Terhechte on 12/08/15.
//  Copyright © 2015 FikesFarm. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <JavaScriptCore/JavaScriptCore.h>

@class PLKFile;

@protocol PLKFile <JSExport>

+(PLKFile*)file:(NSString*)path;
- (void) deleteFile;

@end

@interface PLKFile : NSObject<PLKFile>

@end
