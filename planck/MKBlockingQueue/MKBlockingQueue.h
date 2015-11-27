//
//  MKBlockingQueue.h
//
//  Created by Min Kwon on 5/28/14.
//  Copyright (c) 2014 Min Kwon. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface MKBlockingQueue : NSObject

/**
 * Enqueues an object to the queue.
 * @param object Object to enqueue
 */
- (void)enqueue:(id)object;

/**
 * Dequeues an object from the queue.  This method will block.
 */
- (id)dequeue;

- (NSUInteger)count;

@end
