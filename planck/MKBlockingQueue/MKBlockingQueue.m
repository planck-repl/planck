//
//  MKBlockingQueue.m
//
//  Created by Min Kwon on 5/28/14.
//  Copyright (c) 2014 Min Kwon. All rights reserved.
//

#import "MKBlockingQueue.h"

@interface MKBlockingQueue()
@property (nonatomic, strong) NSMutableArray *queue;
@property (nonatomic, strong) NSCondition *lock;
@property (nonatomic) dispatch_queue_t dispatchQueue;
@end

@implementation MKBlockingQueue

- (id)init
{
    self = [super init];
    if (self)
    {
        self.queue = [[NSMutableArray alloc] init];
        self.lock = [[NSCondition alloc] init];
        self.dispatchQueue = dispatch_queue_create("com.min.kwon.mkblockingqueue", DISPATCH_QUEUE_SERIAL);
    }
    return self;
}

- (void)enqueue:(id)object
{
    [_lock lock];
    [_queue addObject:object];
    [_lock signal];
    [_lock unlock];
}

- (id)dequeue
{
    __block id object;
    dispatch_sync(_dispatchQueue, ^{
        [_lock lock];
        while (_queue.count == 0)
        {
            [_lock wait];
        }
        object = [_queue objectAtIndex:0];
        [_queue removeObjectAtIndex:0];
        [_lock unlock];
    });
    
    return object;
}

- (NSUInteger)count
{
    return [_queue count];
}

- (void)dealloc
{
    self.dispatchQueue = nil;
    self.queue = nil;
    self.lock = nil;
}

@end
