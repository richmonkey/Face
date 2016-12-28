//
//  ConferenceCreatorViewController.h
//  Face
//
//  Created by houxh on 2016/12/10.
//  Copyright © 2016年 beetle. All rights reserved.
//

#import <UIKit/UIKit.h>

@protocol ConferenceCreatorViewControllerDelegate <NSObject>

-(void)onConferenceCancel;
-(void)onConferenceCreated:(NSString*)channelID partipants:(NSArray*)partipants;

@end
@interface ConferenceCreatorViewController : UIViewController
@property(nonatomic, weak) id<ConferenceCreatorViewControllerDelegate> delegate;
@end
