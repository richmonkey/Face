//
//  CallViewController.m
//  Face
//
//  Created by houxh on 2016/12/27.
//  Copyright © 2016年 beetle. All rights reserved.
//

#import "CallViewController.h"
#import "History.h"
#import "HistoryDB.h"

@interface CallViewController()
@property(nonatomic) History *history;
@end
@implementation CallViewController

-(void)viewDidLoad {
    [super viewDidLoad];
    self.history = [[History alloc] init];
    self.history.peerUID = self.peerUID;
    if (self.isCaller) {
        self.history.flag = FLAG_OUT;
    }
    self.history.createTimestamp = time(NULL);
}

-(void)dismiss {
    [super dismiss];
    
    [[HistoryDB instance] addHistory:self.history];
    
    NSNotification* notification = [NSNotification notificationWithName:ON_NEW_CALL_HISTORY_NOTIFY object:self.history];
    [[NSNotificationCenter defaultCenter] postNotification:notification];
}

-(void)accept {
    [super accept];
    self.history.flag = self.history.flag | FLAG_ACCEPTED;
}

-(void)refuse {
    [super refuse];
    self.history.flag = self.history.flag | FLAG_REFUSED;
}

-(void)onRefuse {
    [super onRefuse];
    self.history.flag = self.history.flag | FLAG_REFUSED;
}

-(void)onConnected {
    [super onConnected];
    self.history.flag = self.history.flag | FLAG_ACCEPTED;
}

-(void)startStream {
    [super startStream];
    self.history.beginTimestamp = time(NULL);
}

-(void)stopStream {
    [super stopStream];
    self.history.endTimestamp = time(NULL);
}

@end
