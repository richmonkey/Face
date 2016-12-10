//
//  ConferenceViewController.h
//  Face
//
//  Created by houxh on 2016/12/7.
//  Copyright © 2016年 beetle. All rights reserved.
//

#import <UIKit/UIKit.h>

@interface ConferenceViewController : UIViewController
@property(nonatomic, assign) BOOL isInitiator;//当前用户是发起者
@property(nonatomic, assign) int64_t conferenceID;//会议号
@property(nonatomic) NSArray *partipants;//参会者id
@end
