//
//  MainTabBarController.m
//  Message
//
//  Created by houxh on 14-7-20.
//  Copyright (c) 2014年 daozhu. All rights reserved.
//

#import "MainTabBarController.h"
#import "ContactListTableViewController.h"
#import "ConversationHistoryViewController.h"
#import "SettingViewController.h"
#import "Token.h"
#import "VOIPService.h"
#import "UserPresent.h"
#import "UserDB.h"
#import "ContactDB.h"
#import "APIRequest.h"
#import "JSBadgeView.h"
#import "Constants.h"
#import "VOIPVoiceViewController.h"
#import "VOIPVideoViewController.h"
#import "UIView+Toast.h"
#import "VOIPCommand.h"
#import "APIRequest.h"
#import "ConferenceViewController.h"
#import "ConferenceCommand.h"
#import "AppDelegate.h"


@interface SyncKeyHandler : NSObject<IMSyncKeyHandler>
@property(nonatomic, copy) NSString *fileName;
@property(nonatomic, readonly) int64_t syncKey;


-(id)initWithFileName:(NSString*)fileName;

@end


@interface SyncKeyHandler()
@property(nonatomic, strong) NSMutableDictionary *dict;
@end

@implementation SyncKeyHandler


-(id)initWithFileName:(NSString*)fileName {
    self = [super init];
    if (self) {
        self.fileName = fileName;
        [self load];
    }
    return self;
}
-(void)load {
    NSAssert(self.fileName.length > 0, @"");
    NSDictionary *dict = [self loadDictionary];
    self.dict = [NSMutableDictionary dictionaryWithDictionary:dict];
}

-(BOOL)saveSyncKey:(int64_t)syncKey {
    NSAssert(self.fileName.length > 0, @"");
    [self.dict setObject:[NSNumber numberWithLongLong:syncKey] forKey:@"sync_key"];
    [self storeDictionary:self.dict];
    return YES;
}

-(BOOL)saveGroupSyncKey:(int64_t)syncKey gid:(int64_t)gid {
    return YES;
}


-(int64_t)syncKey {
    return [[self.dict objectForKey:@"sync_key"] longLongValue];
}



-(void)storeDictionary:(NSDictionary*) dictionaryToStore {
    if (dictionaryToStore != nil) {
        [dictionaryToStore writeToFile:self.fileName atomically:YES];
    }
}

-(NSDictionary*)loadDictionary {
    NSDictionary* panelLibraryContent = [NSDictionary dictionaryWithContentsOfFile:self.fileName];
    return panelLibraryContent;
}

@end



@interface MainTabBarController ()
@property(nonatomic)dispatch_source_t refreshTimer;
@property(nonatomic)int refreshFailCount;

@property(nonatomic) int bindFailCount;

//已经接听过的通话，不再重复接听
@property(nonatomic) NSMutableArray *conferenceIDs;

@property(nonatomic) NSMutableArray *channelIDs;
@end

@implementation MainTabBarController

- (id)initWithNibName:(NSString *)nibNameOrNil bundle:(NSBundle *)nibBundleOrNil
{
    self = [super initWithNibName:nibNameOrNil bundle:nibBundleOrNil];
    if (self) {
        // Custom initialization
    }
    return self;
}

-(id)init {
    self = [super init];
    if (self) {
        
    }
    return self;
}
- (void)viewDidLoad
{
    [super viewDidLoad];

    self.conferenceIDs = [NSMutableArray array];
    self.channelIDs = [NSMutableArray array];
    
    ContactListTableViewController* contactViewController = [[ContactListTableViewController alloc] init];
    contactViewController.title = @"通讯录";
    
    contactViewController.tabBarItem.title = @"通讯录";
    contactViewController.tabBarItem.selectedImage = [UIImage imageNamed:@"ic_menu_contact_h"];
    contactViewController.tabBarItem.image = [UIImage imageNamed:@"ic_menu_contact_n"];

    UINavigationController *nav1 = [[UINavigationController alloc] initWithRootViewController:contactViewController];
    
    ConversationHistoryViewController *callHistoryViewController = [[ConversationHistoryViewController alloc] init];
    callHistoryViewController.title = @"通话记录";
    
    callHistoryViewController.tabBarItem.title = @"通话记录";
    callHistoryViewController.tabBarItem.selectedImage = [UIImage imageNamed:@"ic_menu_history_h"];
    callHistoryViewController.tabBarItem.image = [UIImage imageNamed:@"ic_menu_history_n"];
       UINavigationController *nav2 = [[UINavigationController alloc] initWithRootViewController:callHistoryViewController];
    
    
    
    SettingViewController *settingViewController = [[SettingViewController alloc] init];
    settingViewController.title = @"设置";
    
    settingViewController.tabBarItem.title = @"设置";
    settingViewController.tabBarItem.selectedImage = [UIImage imageNamed:@"ic_menu_setting_h"];
    settingViewController.tabBarItem.image = [UIImage imageNamed:@"ic_menu_setting_n"];
    UINavigationController *nav3 = [[UINavigationController alloc] initWithRootViewController:settingViewController];
    
    self.viewControllers = [NSArray arrayWithObjects:nav1,nav2, nav3,nil];
    self.selectedIndex = 0;
    
    dispatch_queue_t queue = dispatch_get_main_queue();
    self.refreshTimer = dispatch_source_create(DISPATCH_SOURCE_TYPE_TIMER, 0, 0,queue);
    dispatch_source_set_event_handler(self.refreshTimer, ^{
        [self refreshAccessToken];
    });
    
    [self startRefreshTimer];

    [VOIPService instance].token = [Token instance].accessToken;
    [VOIPService instance].uid = [Token instance].uid;
    NSString *dbPath = [self getDocumentPath];
    NSString *fileName = [NSString stringWithFormat:@"%@/synckey", dbPath];
    SyncKeyHandler *handler = [[SyncKeyHandler alloc] initWithFileName:fileName];
    [VOIPService instance].syncKeyHandler = handler;
    
    [VOIPService instance].syncKey = [handler syncKey];
    NSLog(@"sync key:%lld", [handler syncKey]);
    
    [[VOIPService instance] start];
    
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(appDidEnterBackground) name:UIApplicationDidEnterBackgroundNotification object:nil];
    
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(appWillEnterForeground) name:UIApplicationWillEnterForegroundNotification object:nil];
    
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(appWillResignActive) name:UIApplicationWillResignActiveNotification object:nil];
    

    [[self tabBar] setTintColor: RGBACOLOR(48,176,87, 1)];
    [[self tabBar] setBarTintColor: RGBACOLOR(245, 245, 246, 1)];
    
    [[VOIPService instance] addSystemMessageObserver:self];
    [[VOIPService instance] addRTMessageObserver:self];
    
    UIApplication *application = [UIApplication sharedApplication];
    UIUserNotificationSettings *settings = [UIUserNotificationSettings settingsForTypes:(UIUserNotificationTypeAlert
                                                                                         | UIUserNotificationTypeBadge
                                                                                         | UIUserNotificationTypeSound) categories:nil];
    [application registerUserNotificationSettings:settings];
    
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(didRegisterForRemoteNotificationsWithDeviceToken:) name:@"didRegisterForRemoteNotificationsWithDeviceToken" object:nil];
}

-(NSString*)getDocumentPath {
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *basePath = ([paths count] > 0) ? [paths objectAtIndex:0] : nil;
    return basePath;
}


-(void)didRegisterForRemoteNotificationsWithDeviceToken:(NSNotification*)notification {
    NSData *deviceToken = (NSData*)notification.object;
    
    NSString* newToken = [deviceToken description];
    newToken = [newToken stringByTrimmingCharactersInSet:[NSCharacterSet characterSetWithCharactersInString:@"<>"]];
    newToken = [newToken stringByReplacingOccurrencesOfString:@" " withString:@""];
    
    NSLog(@"device token is: %@:%@", deviceToken, newToken);

    self.bindFailCount = 0;
    [self bindDeviceToken:newToken];
}

- (void)bindDeviceToken:(NSString*)deviceToken {
    [APIRequest bindDeviceToken:deviceToken
                        success:^{
                            NSLog(@"bind device token success");
                        }
                           fail:^{
                               NSLog(@"bind device token fail");
                               self.bindFailCount = self.bindFailCount + 1;
                               if (self.bindFailCount >= 10) {
                                   return;
                               }
                               
                               dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.1 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                                   [self bindDeviceToken:deviceToken];
                               });
                           }];
}

- (void)didReceiveMemoryWarning
{
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

-(void)appDidEnterBackground {
    //todo check voip state
    [[VOIPService instance] enterBackground];
}

-(void)appWillEnterForeground {
    [[VOIPService instance] enterForeground];
}

- (void)appWillResignActive {
    [[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];
}



-(void)refreshAccessToken {
    Token *token = [Token instance];
    [APIRequest refreshAccessToken:token.refreshToken
                           success:^(NSString *accessToken, NSString *refreshToken, int expireTimestamp) {
                               token.accessToken = accessToken;
                               token.refreshToken = refreshToken;
                               token.expireTimestamp = expireTimestamp;
                               [token save];
                               [self prepareTimer];
                               [VOIPService instance].token = accessToken;
                           }
                              fail:^{
                                  self.refreshFailCount = self.refreshFailCount + 1;
                                  int64_t timeout;
                                  if (self.refreshFailCount > 60) {
                                      timeout = 60*NSEC_PER_SEC;
                                  } else {
                                      timeout = (int64_t)self.refreshFailCount*NSEC_PER_SEC;
                                  }
                                  
                                  dispatch_after(dispatch_time(DISPATCH_TIME_NOW, timeout), dispatch_get_main_queue(), ^{
                                      [self prepareTimer];
                                  });
                                  
                              }];
}

-(void)prepareTimer {
    Token *token = [Token instance];
    int now = (int)time(NULL);
    if (now >= token.expireTimestamp - 1) {
        dispatch_time_t w = dispatch_walltime(NULL, 0);
        dispatch_source_set_timer(self.refreshTimer, w, DISPATCH_TIME_FOREVER, 0);
    } else {
        dispatch_time_t w = dispatch_walltime(NULL, (token.expireTimestamp - now - 1)*NSEC_PER_SEC);
        dispatch_source_set_timer(self.refreshTimer, w, DISPATCH_TIME_FOREVER, 0);
    }
}

-(void) onNewMessage:(NSNotification*)ntf{
    UITabBar *tabBar = self.tabBar;
    UITabBarItem * cc =  [tabBar.items objectAtIndex: 2];
    [cc setBadgeValue:@""];
}

-(void) clearNewMessage:(NSNotification*)ntf{
    UITabBar *tabBar = self.tabBar;
    UITabBarItem * cc =  [tabBar.items objectAtIndex: 2];
    [cc setBadgeValue:nil];
}

-(void)startRefreshTimer {
    [self prepareTimer];
    dispatch_resume(self.refreshTimer);
}

-(void)stopRefreshTimer {
    dispatch_suspend(self.refreshTimer);
}

-(User*)getUserName:(int64_t)uid {
    User *u = [[UserDB instance] loadUser:uid];
    ABContact *contact = [[ContactDB instance] loadContactWithNumber:u.phoneNumber];
    NSString *name = contact.contactName;
    if (name.length == 0) {
        name = u.phoneNumber.number;
    }
    u.name = name;
    
    return u;
}


#pragma mark RTMessageObserver
-(void)onRTMessage:(RTMessage*)rt {
    NSLog(@"rt message:%@", rt.content);
    const char *utf8 = [rt.content UTF8String];
    if (utf8 == nil) return;
    NSData *data = [NSData dataWithBytes:utf8 length:strlen(utf8)];
    NSDictionary *dict = [NSJSONSerialization JSONObjectWithData:data options:NSJSONReadingMutableLeaves error:nil];
    if (![dict isKindOfClass:[NSDictionary class]]) {
        return;
    }
    
    if ([VOIPViewController controllerCount] > 0) {
        return;
    }

    if ([ConferenceViewController controllerCount] > 0) {
        return;
    }
    
    if ([dict objectForKey:@"conference"]) {
        NSDictionary *c = [dict objectForKey:@"conference"];
        
        ConferenceCommand *command = [[ConferenceCommand alloc] initWithDictionary:c];
        
        if (![command.command isEqualToString:CONFERENCE_COMMAND_INVITE]) {
            return;
        }
        

        if ([self.channelIDs containsObject:command.channelID]) {
            NSLog(@"call already show");
            return;
        }
        
        [self.channelIDs addObject:command.channelID];
        
        //留下100次呼叫记录
        if (self.conferenceIDs.count > 100) {
            [self.conferenceIDs removeObjectAtIndex:0];
        }

        NSMutableArray *partipantNames = [NSMutableArray array];
        NSMutableArray *partipantAvatars = [NSMutableArray array];
        
        for (NSNumber *p in command.partipants) {
            User *u = [[UserDB instance] loadUser:[p longLongValue]];
            ABContact *contact = [[ContactDB instance] loadContactWithNumber:u.phoneNumber];
            
            NSString *name = contact.contactName;
            if (name.length == 0) {
                name = u.phoneNumber.number;
            }
            
            NSString *avatar = u.avatarURL ? u.avatarURL : @"";
            [partipantNames addObject:name];
            [partipantAvatars addObject:avatar];
        }
        
        ConferenceViewController *ctrl = [[ConferenceViewController alloc] init];
        ctrl.initiator = command.initiator;
        ctrl.channelID = command.channelID;
        ctrl.currentUID = [UserPresent instance].uid;
        
        ctrl.partipants = command.partipants;
        ctrl.partipantNames = partipantNames;
        ctrl.partipantAvatars = partipantAvatars;
        
        [self presentViewController:ctrl animated:YES completion:nil];
        
        return;
    } else if ([dict objectForKey:@"voip"]) {
        NSDictionary *obj = [dict objectForKey:@"voip"];
        VOIPCommand *command = [[VOIPCommand alloc] initWithContent:obj];
        if ([self.channelIDs containsObject:command.channelID]) {
            return;
        }
        
        if (command.cmd == VOIP_COMMAND_DIAL) {
            [self.channelIDs addObject:command.channelID];
            //留下100次呼叫记录
            if (self.conferenceIDs.count > 100) {
                [self.conferenceIDs removeObjectAtIndex:0];
            }
            
            User *user = [self getUserName:rt.sender];
            VOIPVoiceViewController *controller = [[VOIPVoiceViewController alloc] init];
            controller.currentUID = [UserPresent instance].uid;
            controller.channelID = command.channelID;
            controller.peerUID = rt.sender;
            controller.peerName = user.name ? user.name : @"";
            controller.peerAvatar = user.avatarURL ? user.avatarURL : @"";
            controller.token = [Token instance].accessToken;
            controller.isCaller = NO;
            
            [self presentViewController:controller animated:YES completion:nil];
        } else if (command.cmd == VOIP_COMMAND_DIAL_VIDEO) {
            [self.channelIDs addObject:command.channelID];
            //留下100次呼叫记录
            if (self.conferenceIDs.count > 100) {
                [self.conferenceIDs removeObjectAtIndex:0];
            }

            User *user = [self getUserName:rt.sender];
            VOIPVideoViewController *controller = [[VOIPVideoViewController alloc] init];
            controller.currentUID = [UserPresent instance].uid;
            controller.channelID = command.channelID;
            controller.peerUID = rt.sender;
            controller.peerName = user.name ? user.name : @"";
            controller.peerAvatar = user.avatarURL ? user.avatarURL : @"";
            controller.token = [Token instance].accessToken;
            controller.isCaller = NO;
            
            [self presentViewController:controller animated:YES completion:nil];
        }
    }
    
 
}

-(void)onSystemMessage:(NSString*)sm {

}

@end
