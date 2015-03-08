//
//  VOIPViewController.h
//  Face
//
//  Created by houxh on 14-10-13.
//  Copyright (c) 2014年 beetle. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <AVFoundation/AVFoundation.h>

@interface VOIPViewController : UIViewController<AVAudioPlayerDelegate>
- (id)initWithCalledUID:(int64_t)uid;
-(id)initWithCallerUID:(int64_t)uid;
@end
