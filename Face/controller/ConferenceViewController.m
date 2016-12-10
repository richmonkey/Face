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



@interface ConferenceViewController ()<RCTBridgeModule>

@end






@implementation ConferenceViewController
RCT_EXPORT_MODULE();

RCT_EXPORT_METHOD(dismiss)
{
    [self dismissViewControllerAnimated:YES completion:nil];
}


- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

- (void)viewDidLoad {
    [super viewDidLoad];
    RCTBridgeModuleProviderBlock provider = ^NSArray<id<RCTBridgeModule>> *{
        return @[self];
    };
    
    NSURL *jsCodeLocation = [[RCTBundleURLProvider sharedSettings] jsBundleURLForBundleRoot:@"index.ios"
                                                                           fallbackResource:nil];
    
    
    RCTBridge *bridge = [[RCTBridge alloc] initWithBundleURL:jsCodeLocation
                                              moduleProvider:provider
                                               launchOptions:nil];
    
    NSDictionary *props = @{@"isInitiator":[NSNumber numberWithBool:self.isInitiator],
                            @"conferenceID":[NSNumber numberWithLongLong:self.conferenceID]};
    
    RCTRootView *rootView = [[RCTRootView alloc] initWithBridge:bridge moduleName:@"App" initialProperties:props];
    
    // Set a background color which is in accord with the JavaScript and Android
    // parts of the application and causes less perceived visual flicker than the
    // default background color.
    rootView.backgroundColor = [[UIColor alloc] initWithRed:.07f green:.07f blue:.07f alpha:1];

    self.view = rootView;
    
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
