//
//  AppDelegate.h
//  Face
//
//  Created by houxh on 14-10-13.
//  Copyright (c) 2014年 beetle. All rights reserved.
//

#import <UIKit/UIKit.h>

@interface AppDelegate : UIResponder <UIApplicationDelegate>

@property (strong, nonatomic) UIWindow *window;

@property (strong, nonatomic) UITabBarController* tabBarController;
@property (nonatomic, copy) NSString *deviceToken;

@property(nonatomic, getter=isTalking) BOOL talking;

+(AppDelegate*)instance;
@end
