//
//  MainTabBarController.h
//  Message
//
//  Created by houxh on 14-7-20.
//  Copyright (c) 2014年 daozhu. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "VOIPService.h"
@interface MainTabBarController : UITabBarController<SystemMessageObserver, RTMessageObserver>

@end
