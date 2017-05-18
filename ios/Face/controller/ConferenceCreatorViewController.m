//
//  ConferenceCreatorViewController.m
//  Face
//
//  Created by houxh on 2016/12/10.
//  Copyright © 2016年 beetle. All rights reserved.
//

#import "ConferenceCreatorViewController.h"
#import <React/RCTBundleURLProvider.h>
#import <React/RCTRootView.h>
#import <React/RCTBridgeModule.h>

#import "ContactDB.h"
#import "UserDB.h"
#import "UserPresent.h"
#import "Token.h"
#import "Config.h"

@interface ConferenceCreatorViewController ()<RCTBridgeModule>
@property(nonatomic, copy) NSString *channelID;
@end

@implementation ConferenceCreatorViewController
RCT_EXPORT_MODULE();


RCT_EXPORT_METHOD(onCancel)
{
    NSLog(@"on cancel");
    [self.delegate onConferenceCancel];
}

RCT_EXPORT_METHOD(onCreate:(NSArray*)partipants)
{
    NSLog(@"on create:%@", partipants);
    [self.delegate onConferenceCreated:self.channelID partipants:partipants];
}

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}


- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    
    self.channelID = [[NSUUID UUID] UUIDString];
    
    RCTBridgeModuleProviderBlock provider = ^NSArray<id<RCTBridgeModule>> *{
        return @[self];
    };
    
    NSURL *jsCodeLocation = [[RCTBundleURLProvider sharedSettings] jsBundleURLForBundleRoot:@"index.ios"
                                                                           fallbackResource:nil];
    
    
    RCTBridge *bridge = [[RCTBridge alloc] initWithBundleURL:jsCodeLocation
                                              moduleProvider:provider
                                               launchOptions:nil];
    
    
    NSArray *users = [self loadData];
    User *profile = [UserPresent instance];
    Token *token = [Token instance];
    Config *config = [Config instance];
    NSDictionary *props = @{@"uid":[NSNumber numberWithLongLong:profile.uid],
                            @"token":token.accessToken,
                            @"url":config.URL,
                            @"users":users,
                            @"channelID":self.channelID};
    RCTRootView *rootView = [[RCTRootView alloc] initWithBridge:bridge moduleName:@"ConferenceCreator" initialProperties:props];
    
    // Set a background color which is in accord with the JavaScript and Android
    // parts of the application and causes less perceived visual flicker than the
    // default background color.
    rootView.backgroundColor = [[UIColor alloc] initWithRed:.07f green:.07f blue:.07f alpha:1];
    
    self.view = rootView;

    
}


-(NSArray*)loadData{
    NSMutableDictionary *dict = [NSMutableDictionary dictionary];
    NSArray *contacts = [[ContactDB instance] contactsArray];
    if([contacts count] == 0) {
        return @[];
    }
    
    for (IMContact *contact in contacts) {
        for (User *u in contact.users) {
            NSNumber *k = [NSNumber numberWithLongLong:u.uid];
            if (![dict objectForKey:k]) {
                NSDictionary *d = @{@"uid":[NSNumber numberWithLongLong:u.uid], @"name":u.displayName};
                [dict setObject:d forKey:k];
            }
        }
    }
    
    return [dict allValues];
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
