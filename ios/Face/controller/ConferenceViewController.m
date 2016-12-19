//
//  ConferenceViewController.m
//  Face
//
//  Created by houxh on 2016/12/7.
//  Copyright © 2016年 beetle. All rights reserved.
//

#import "ConferenceViewController.h"
#import "RCTBundleURLProvider.h"
#import "RCTRootView.h"

#import "RCTBridgeModule.h"

#import <AVFoundation/AVFoundation.h>
#import "UserDB.h"
#import "UserPresent.h"
#import "ContactDB.h"

@interface ConferenceViewController ()<RCTBridgeModule>

@end






@implementation ConferenceViewController


RCT_EXPORT_MODULE();

RCT_EXPORT_METHOD(dismiss)
{

    [[UIApplication sharedApplication] setIdleTimerDisabled:NO];
    
    RCTRootView *rootView = (RCTRootView*)self.view;
    [rootView.bridge invalidate];
    [self dismissViewControllerAnimated:YES completion:nil];
}



//http://stackoverflow.com/questions/24595579/how-to-redirect-audio-to-speakers-in-the-apprtc-ios-example
- (void)didSessionRouteChange:(NSNotification *)notification
{
    NSDictionary *interuptionDict = notification.userInfo;
    NSInteger routeChangeReason = [[interuptionDict valueForKey:AVAudioSessionRouteChangeReasonKey] integerValue];
    NSLog(@"route change:%zd", routeChangeReason);
    if (![self isHeadsetPluggedIn] && ![self isLoudSpeaker]) {
        NSError* error;
        [[AVAudioSession sharedInstance] overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker error:&error];
    }
}

- (BOOL)isHeadsetPluggedIn {
    AVAudioSessionRouteDescription *route = [[AVAudioSession sharedInstance] currentRoute];
    
    BOOL headphonesLocated = NO;
    for( AVAudioSessionPortDescription *portDescription in route.outputs )
    {
        headphonesLocated |= ( [portDescription.portType isEqualToString:AVAudioSessionPortHeadphones] );
    }
    return headphonesLocated;
}


-(BOOL)isLoudSpeaker {
    AVAudioSession* session = [AVAudioSession sharedInstance];
    AVAudioSessionCategoryOptions options = session.categoryOptions;
    BOOL enabled = options & AVAudioSessionCategoryOptionDefaultToSpeaker;
    return enabled;
}


- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}


-(void)dealloc {
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

- (void)viewDidLoad {
    [super viewDidLoad];
    RCTBridgeModuleProviderBlock provider = ^NSArray<id<RCTBridgeModule>> *{
        return @[self];
    };
    
    NSLog(@"conference id:%lld", self.conferenceID);
    
    NSURL *jsCodeLocation = [[RCTBundleURLProvider sharedSettings] jsBundleURLForBundleRoot:@"index.ios"
                                                                           fallbackResource:nil];
    
    
    RCTBridge *bridge = [[RCTBridge alloc] initWithBundleURL:jsCodeLocation
                                              moduleProvider:provider
                                               launchOptions:nil];
    
    NSMutableArray *users = [NSMutableArray array];
    for (int i = 0; i < self.partipants.count; i++) {
        NSNumber *uid = [self.partipants objectAtIndex:i];
        
        User *u = [[UserDB instance] loadUser:[uid longLongValue]];
        ContactDB *cdb = [ContactDB instance];
        if (u.phoneNumber.isValid) {
            ABContact *contact = [cdb loadContactWithNumber:u.phoneNumber];
            u.name = contact.contactName;
        }
        
        NSDictionary *user = @{@"uid":uid,
                               @"name":u.displayName,
                               @"avatar":u.avatarURL?u.avatarURL:@""
                               };
        [users addObject:user];
    }
    
    BOOL isInitiator = ([UserPresent instance].uid == self.initiator);
    
    NSDictionary *props = @{@"initiator":[NSNumber numberWithLongLong:self.initiator],
                            @"isInitiator":[NSNumber numberWithBool:isInitiator],
                            @"conferenceID":[NSNumber numberWithLongLong:self.conferenceID],
                            @"partipants":users};
    
    RCTRootView *rootView = [[RCTRootView alloc] initWithBridge:bridge moduleName:@"App" initialProperties:props];
    
    // Set a background color which is in accord with the JavaScript and Android
    // parts of the application and causes less perceived visual flicker than the
    // default background color.
    rootView.backgroundColor = [[UIColor alloc] initWithRed:.07f green:.07f blue:.07f alpha:1];

    self.view = rootView;

    [[UIApplication sharedApplication] setIdleTimerDisabled:YES];
}



- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

/*
#pragma mark - Navigation

// In a storyboard-based application, you will often want to do a little preparation before navigation
- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender {
    // Get the new view controller using [segue destinationViewController].
    // Pass the selected object to the new view controller.
}
*/

@end
