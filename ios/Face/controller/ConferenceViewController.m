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
#import "RCTBridge.h"
#import "RCTEventDispatcher.h"

#import <AVFoundation/AVFoundation.h>
#import "UserDB.h"
#import "UserPresent.h"
#import "ContactDB.h"
#import <voipsession/VOIPService.h>
#import "AppDelegate.h"

#define CONFERENCE_STATE_WAITING 1
#define CONFERENCE_STATE_ACCEPTED 2
#define CONFERENCE_STATE_REFUSED 3


@interface ConferenceViewController ()<RCTBridgeModule, RTMessageObserver>
@property(nonatomic) NSTimer *timer;

//当前用户是主叫方， 表示被叫方接听的状态 接受/拒绝／等待应答
@property(nonatomic) NSMutableDictionary *partipantStates;

//当前用户是被叫方,接听的状态 接受/拒绝／等待应答
@property(nonatomic) int state;

@property(nonatomic, weak) RCTBridge *bridge;
@end


@implementation ConferenceViewController


RCT_EXPORT_MODULE();


RCT_EXPORT_METHOD(accept) {
    self.state = CONFERENCE_STATE_ACCEPTED;
    [self sendAccept];
}

RCT_EXPORT_METHOD(refuse) {
    self.state = CONFERENCE_STATE_REFUSED;
    [self sendRefuse];
}

RCT_EXPORT_METHOD(dismiss)
{
    [AppDelegate instance].talking = NO;
    
    [self.timer invalidate];

    [[VOIPService instance] removeRTMessageObserver:self];
    
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
    self.bridge = bridge;
    
    [[VOIPService instance] addRTMessageObserver:self];

    [[UIApplication sharedApplication] setIdleTimerDisabled:YES];
    
    if (isInitiator) {
        self.partipantStates = [NSMutableDictionary dictionary];
        for (int i = 0; i < self.partipants.count; i++) {
            NSNumber *uid = [self.partipants objectAtIndex:i];
            if ([UserPresent instance].uid == [uid longLongValue]) {
                continue;
            }
            [self.partipantStates setObject:[NSNumber numberWithInt:CONFERENCE_STATE_WAITING] forKey:uid];
        }
        self.timer = [NSTimer scheduledTimerWithTimeInterval:1 repeats:YES block:^(NSTimer * _Nonnull timer) {
            [self sendInvite];
        }];
        [self sendInvite];
    } else {
        self.state = CONFERENCE_STATE_WAITING;
    }
    
    
    if (![self isHeadsetPluggedIn] && ![self isLoudSpeaker]) {
        NSError* error;
        [[AVAudioSession sharedInstance] overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker error:&error];
    }
    
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(didSessionRouteChange:) name:AVAudioSessionRouteChangeNotification object:nil];
    
}


-(int)setLoudspeakerStatus:(BOOL)enable {
    AVAudioSession* session = [AVAudioSession sharedInstance];
    NSString* category = session.category;
    AVAudioSessionCategoryOptions options = session.categoryOptions;
    // Respect old category options if category is
    // AVAudioSessionCategoryPlayAndRecord. Otherwise reset it since old options
    // might not be valid for this category.
    if ([category isEqualToString:AVAudioSessionCategoryPlayAndRecord]) {
        if (enable) {
            options |= AVAudioSessionCategoryOptionDefaultToSpeaker;
        } else {
            options &= ~AVAudioSessionCategoryOptionDefaultToSpeaker;
        }
    } else {
        options = AVAudioSessionCategoryOptionDefaultToSpeaker;
    }
    
    NSError* error = nil;
    [session setCategory:AVAudioSessionCategoryPlayAndRecord
             withOptions:options
                   error:&error];
    if (error != nil) {
        NSLog(@"set loudspeaker err:%@", error);
        return -1;
    }
    
    return 0;
}



-(void)sendRTMessage:(NSString*)command to:(int64_t)to {
    NSDictionary *dict = @{@"conference":@{@"id":[NSNumber numberWithLongLong:self.conferenceID],
                                           @"partipants":self.partipants,
                                           @"initiator":[NSNumber numberWithLongLong:self.initiator],
                                           @"command":command}};
    NSData *jsonData = [NSJSONSerialization dataWithJSONObject:dict options:0 error:nil];
    NSString* newStr = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
    
    RTMessage *rt = [[RTMessage alloc] init];
    rt.sender = [UserPresent instance].uid;
    rt.receiver = to;
    rt.content = newStr;
    
    [[VOIPService instance] sendRTMessage:rt];
}



-(void)sendAccept {
    [self sendRTMessage:@"accept" to:self.initiator];
}

-(void)sendRefuse {
    [self sendRTMessage:@"refuse" to:self.initiator];
}

-(void)sendWaiting {
//    [self sendRTMessage:@"waiting" to:self.initiator];
}

-(void)sendInvite:(int64_t)to {
    [self sendRTMessage:@"invite" to:to];
}

-(void)sendInvite {
    for (int i = 0; i < self.partipantStates.count; i++) {
        NSNumber *uid = [self.partipants objectAtIndex:i];
        if ([uid longLongValue] == [UserPresent instance].uid) {
            continue;
        }

        int state = [[self.partipantStates objectForKey:uid] intValue];
        if (state == CONFERENCE_STATE_WAITING) {
            [self sendInvite:[uid longLongValue]];
        }
    }
}

-(void)onRTMessage:(RTMessage*)rt {
    NSNumber *sender = [NSNumber numberWithLongLong:rt.sender];
    if (![self.partipants containsObject:sender]) {
        return;
    }
    
    const char *utf8 = [rt.content UTF8String];
    if (utf8 == nil) return;
    NSData *data = [NSData dataWithBytes:utf8 length:strlen(utf8)];
    NSDictionary *dict = [NSJSONSerialization JSONObjectWithData:data options:NSJSONReadingMutableLeaves error:nil];
    if (![dict isKindOfClass:[NSDictionary class]]) {
        return;
    }
    if (![dict objectForKey:@"conference"]) {
        return;
    };
    
    NSDictionary *c = [dict objectForKey:@"conference"];
    NSString *command = [c objectForKey:@"command"];
    NSLog(@"conference command:%@", command);
    BOOL isInitiator = ([UserPresent instance].uid == self.initiator);
    
    if (isInitiator) {
        if ([command isEqualToString:@"accept"]) {
            [self.partipantStates setObject:[NSNumber numberWithInt:CONFERENCE_STATE_ACCEPTED] forKey:sender];
        } else if ([command isEqualToString:@"refuse"]) {
            [self.partipantStates setObject:[NSNumber numberWithInt:CONFERENCE_STATE_REFUSED] forKey:sender];
        } else if ([command isEqualToString:@"waiting"]) {
            //等待用户接听
        }

        //所有人都拒绝
        BOOL refused = YES;
        for (int i = 0; i <self.partipants.count; i++) {
            NSNumber *uid = [self.partipants objectAtIndex:i];
            if ([uid longLongValue] == [UserPresent instance].uid) {
                continue;
            }
            int s = [[self.partipantStates objectForKey:uid] intValue];
            if (s != CONFERENCE_STATE_REFUSED) {
                refused = NO;
                break;
            }
        }
        
        if (refused) {
            [self.bridge.eventDispatcher sendAppEventWithName:@"onRemoteRefuse"
                                                         body:nil];
        }
    } else {
        if ([command isEqualToString:@"invite"]) {
            if (self.state == CONFERENCE_STATE_WAITING) {
                [self sendWaiting];
            } else if (self.state == CONFERENCE_STATE_ACCEPTED) {
                [self sendAccept];
            } else if (self.state == CONFERENCE_STATE_REFUSED){
                [self sendRefuse];
            }
        }
    }
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
