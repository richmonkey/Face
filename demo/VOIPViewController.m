/*                                                                            
  Copyright (c) 2014-2015, GoBelieve     
    All rights reserved.		    				     			
 
  This source code is licensed under the BSD-style license found in the
  LICENSE file in the root directory of this source tree. An additional grant
  of patent rights can be found in the PATENTS file in the same directory.
*/

#include <arpa/inet.h>
#import <AVFoundation/AVAudioSession.h>
#import <UIKit/UIKit.h>
#import <voipengine/VOIPEngine.h>
#import "VOIPViewController.h"

#import <voipsession/VOIPSession.h>

#import "ReflectionView.h"
#import "UIView+Toast.h"

#import "VWWWaterView.h"



#define kBtnWidth  72
#define kBtnHeight 72

#define kBtnSqureWidth  200
#define kBtnSqureHeight 50

#define KheaderViewWH  100

#define kBtnYposition  (self.view.frame.size.height - 2.5*kBtnSqureHeight)

//RGB颜色
#define RGBCOLOR(r,g,b) [UIColor colorWithRed:(r)/255.0f green:(g)/255.0f blue:(b)/255.0f alpha:1]

@interface VOIPViewController ()<VOIPSessionDelegate>


@property(nonatomic) UIButton *hangUpButton;
@property(nonatomic) UIButton *acceptButton;
@property(nonatomic) UIButton *refuseButton;


@property(nonatomic) UIView *bkview;
@property(nonatomic) UILabel *durationLabel;
@property   (nonatomic) ReflectionView *headView;
@property   (nonatomic) NSTimer *refreshTimer;

@property(nonatomic) UInt64  conversationDuration;


@property(nonatomic) AVAudioPlayer *player;

@property(nonatomic) VOIPEngine *engine;
@property(nonatomic) VOIPSession *voip;
@property(nonatomic) BOOL isConnected;

@end

@implementation VOIPViewController

- (id)initWithNibName:(NSString *)nibNameOrNil bundle:(NSBundle *)nibBundleOrNil
{
    self = [super initWithNibName:nibNameOrNil bundle:nibBundleOrNil];
    if (self) {
        // Custom initialization
    }
    return self;
}


-(void)dealloc {
    NSLog(@"voip view controller dealloc");
}

-(BOOL)isP2P {
    if (self.voip.localNatMap.ip != 0 && self.voip.peerNatMap.ip != 0 ) {
        return YES;
    }

    return NO;
}

- (void)didReceiveMemoryWarning
{
    [super didReceiveMemoryWarning];
    
}

- (void)viewDidLoad
{
    [super viewDidLoad];

    
    self.conversationDuration = 0;
    
    // Do any additional setup after loading the view, typically from a nib.
    [self.view setBackgroundColor:[UIColor whiteColor]];
    
    self.bkview = [[VWWWaterView alloc]
                   initWithFrame:CGRectMake(0, 0, self.view.frame.size.width,
                                            self.view.frame.size.height)];
    [self.view addSubview:self.bkview];
    
    UIImageView *imgView = [[UIImageView alloc]
                            initWithFrame:CGRectMake(0,0, KheaderViewWH,
                                                     KheaderViewWH)];
    

    imgView.image = [UIImage imageNamed:@"PersonalChat"];
    
    CALayer *imageLayer = [imgView layer];  //获取ImageView的层
    [imageLayer setMasksToBounds:YES];
    [imageLayer setCornerRadius:imgView.frame.size.width / 2];
    
    self.headView = [[ReflectionView alloc] initWithFrame:CGRectMake((self.view.frame.size.width-KheaderViewWH)/2,80, KheaderViewWH,KheaderViewWH)];
    self.headView.alpha = 0.9f;
    self.headView.reflectionScale = 0.3f;
    self.headView.reflectionGap = 1.0f;
    [self.headView addSubview:imgView];
    
    [self.view addSubview:self.headView];
    
    
    self.durationLabel = [[UILabel alloc] init];
    [self.durationLabel setFont:[UIFont systemFontOfSize:23.0f]];
    [self.durationLabel setTextAlignment:NSTextAlignmentCenter];
    [self.durationLabel sizeToFit];
    [self.durationLabel setTextColor: RGBCOLOR(11, 178, 39)];
    [self.durationLabel setHidden:YES];
    [self.view addSubview:self.durationLabel];
    [self.durationLabel setCenter:CGPointMake((self.view.frame.size.width)/2, self.headView.frame.origin.y + self.headView.frame.size.height + 50)];
    [self.durationLabel setBackgroundColor:[UIColor clearColor]];
    
    
    self.acceptButton = [UIButton buttonWithType:UIButtonTypeCustom];

    self.acceptButton.frame = CGRectMake(30.0f, self.view.frame.size.height - kBtnHeight - kBtnHeight, kBtnWidth, kBtnHeight);
    
    [self.acceptButton setBackgroundImage: [UIImage imageNamed:@"Call_Ans"] forState:UIControlStateNormal];
    
    [self.acceptButton setBackgroundImage:[UIImage imageNamed:@"Call_Ans_p"] forState:UIControlStateHighlighted];
    [self.acceptButton setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
    [self.acceptButton addTarget:self
                   action:@selector(acceptCall:)
         forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:self.acceptButton];
    [self.acceptButton setCenter:CGPointMake(self.view.frame.size.width/4, kBtnYposition)];
    
    
    self.refuseButton = [UIButton buttonWithType:UIButtonTypeCustom];

    self.refuseButton.frame = CGRectMake(0,0, kBtnWidth, kBtnHeight);
    
    [self.refuseButton setBackgroundImage:[UIImage imageNamed:@"Call_hangup"] forState:UIControlStateNormal];
    [self.refuseButton setBackgroundImage:[UIImage imageNamed:@"Call_hangup_p"] forState:UIControlStateHighlighted];
    [self.refuseButton setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
    [self.refuseButton addTarget:self
                   action:@selector(refuseCall:)
         forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:self.refuseButton];
    [self.refuseButton setCenter:CGPointMake(self.view.frame.size.width/4 + self.view.frame.size.width/2, kBtnYposition)];
    
    
    self.hangUpButton = [[UIButton alloc] initWithFrame:CGRectMake(0,0, kBtnSqureWidth, kBtnSqureHeight)];
    [self.hangUpButton setBackgroundImage:[UIImage imageNamed:@"refuse_nor"] forState:UIControlStateNormal];
    [self.hangUpButton setBackgroundImage:[UIImage imageNamed:@"refuse_pre"] forState:UIControlStateHighlighted];
    [self.hangUpButton setTitle:@"挂断" forState:UIControlStateNormal];
    [self.hangUpButton.titleLabel setFont:[UIFont systemFontOfSize:20.0f]];
    [self.hangUpButton setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
    [self.hangUpButton addTarget:self
                   action:@selector(hangUp:)
         forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:self.hangUpButton];
    [self.hangUpButton setCenter:CGPointMake(self.view.frame.size.width / 2, kBtnYposition)];
    
   
    


    
    
    if (self.isCaller) {
        self.acceptButton.hidden = YES;
        self.refuseButton.hidden = YES;
    } else {
        self.hangUpButton.hidden = YES;
    }

    self.voip = [[VOIPSession alloc] init];
    self.voip.currentUID = self.currentUID;
    self.voip.peerUID = self.peerUID;
    self.voip.delegate = self;
    [self.voip holePunch];
    [[VOIPService instance] pushVOIPObserver:self.voip];
    
    [[AVAudioSession sharedInstance] requestRecordPermission:^(BOOL granted) {
        if (granted) {
            [[UIDevice currentDevice] setProximityMonitoringEnabled:YES];
            
            if (self.isCaller) {
                
                [self makeDialing:self.voip];
                
            } else {
                [self playDialIn];
            }
            
        } else {
            NSLog(@"can't grant record permission");
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                [self dismissViewControllerAnimated:NO completion:^{
                    [[VOIPService instance] popVOIPObserver:self.voip];
                    [[VOIPService instance] stop];
                }];
            });
        }
    }];
}

-(void)dismiss {
    [[UIDevice currentDevice] setProximityMonitoringEnabled:NO];

    [self dismissViewControllerAnimated:YES completion:^{
        [[VOIPService instance] popVOIPObserver:self.voip];
        [[VOIPService instance] stop];
    }];
}

-(void)refuseCall:(UIButton*)button {
    [self.voip refuse];
    [self.player stop];
    self.player = nil;
    
    self.refuseButton.enabled = NO;
    self.acceptButton.enabled = NO;
}

-(void)acceptCall:(UIButton*)button {
    //关闭外方
    AVAudioSession *session = [AVAudioSession sharedInstance];
    
    [session setCategory:AVAudioSessionCategoryPlayAndRecord
                   error:nil];
    
    [session overrideOutputAudioPort:AVAudioSessionPortOverrideNone
                               error:nil];
    
    [self.player stop];
    self.player = nil;
    
    [self.voip accept];
    
    self.refuseButton.enabled = NO;
    self.acceptButton.enabled = NO;
}

-(void)hangUp:(UIButton*)button {
    [self.voip hangUp];
    if (self.isConnected) {
        self.conversationDuration = 0;
        if (self.refreshTimer && [self.refreshTimer isValid]) {
            [self.refreshTimer invalidate];
            self.refreshTimer = nil;
            
        }
        [self stopStream];
        
        [self dismiss];
    } else {
        [self.player stop];
        self.player = nil;

        
        [self dismiss];
    }
}




- (BOOL)isHeadsetPluggedIn
{
    AVAudioSessionRouteDescription *route = [[AVAudioSession sharedInstance] currentRoute];
    
    BOOL headphonesLocated = NO;
    for( AVAudioSessionPortDescription *portDescription in route.outputs )
    {
        headphonesLocated |= ( [portDescription.portType isEqualToString:AVAudioSessionPortHeadphones] );
    }
    return headphonesLocated;
}

- (void)startStream {
    if (self.voip.localNatMap != nil) {
        struct in_addr addr;
        addr.s_addr = htonl(self.voip.localNatMap.ip);
        NSLog(@"local nat map:%s:%d", inet_ntoa(addr), self.voip.localNatMap.port);
    }
    if (self.voip.peerNatMap != nil) {
        struct in_addr addr;
        addr.s_addr = htonl(self.voip.peerNatMap.ip);
        NSLog(@"peer nat map:%s:%d", inet_ntoa(addr), self.voip.peerNatMap.port);
    }
    
    if (self.isP2P) {
        struct in_addr addr;
        addr.s_addr = htonl(self.voip.peerNatMap.ip);
        NSLog(@"peer address:%s:%d", inet_ntoa(addr), self.voip.peerNatMap.port);
        NSLog(@"start p2p stream");
    } else {
        NSLog(@"start stream");
    }

    if (self.engine != nil) {
        return;
    }
    
    BOOL isHeadphone = [self isHeadsetPluggedIn];
    
    self.engine = [[VOIPEngine alloc] init];
    NSLog(@"relay ip:%@", self.voip.relayIP);
    self.engine.relayIP = self.voip.relayIP;
    self.engine.voipPort = self.voip.voipPort;
    self.engine.caller = self.currentUID;
    self.engine.callee = self.peerUID;
    self.engine.token = self.token;
    if (self.isP2P) {
        self.engine.calleeIP = self.voip.peerNatMap.ip;
        self.engine.calleePort = self.voip.peerNatMap.port;
    }
    self.engine.isHeadphone = isHeadphone;
    
    [self.engine startStream];
    
    self.refreshTimer = [NSTimer scheduledTimerWithTimeInterval:1.0 target:self selector:@selector(refreshDuration) userInfo:nil repeats:YES];
    [self.refreshTimer fire];
    
}


-(void)stopStream {
    if (self.engine == nil) {
        return;
    }
    NSLog(@"stop stream");
    
    if (self.refreshTimer && [self.refreshTimer isValid]) {
        [self.refreshTimer invalidate];
        self.refreshTimer = nil;
    }
    
    [self.engine stopStream];
}



#pragma mark - AVAudioPlayerDelegate
- (void)audioPlayerDidFinishPlaying:(AVAudioPlayer *)player successfully:(BOOL)flag {
    NSLog(@"player finished");
    if (!self.isConnected) {
        [self.player play];
    }
}

- (void)audioPlayerDecodeErrorDidOccur:(AVAudioPlayer *)player error:(NSError *)error {
    NSLog(@"player decode error");
}


-(void)playDialIn {

    NSString *path = [[[NSBundle mainBundle] resourcePath] stringByAppendingPathComponent:@"start.mp3"];
    
    AVAudioSession *session = [AVAudioSession sharedInstance];
    [session setCategory:AVAudioSessionCategoryPlayAndRecord error:nil];
    
    //打开外放
    [session overrideOutputAudioPort:AVAudioSessionPortOverrideSpeaker
                               error:nil];
    
    
    NSURL *u = [NSURL fileURLWithPath:path];
    self.player = [[AVAudioPlayer alloc] initWithContentsOfURL:u error:nil];
    [self.player setDelegate:self];
    
    [self.player play];
}

-(void)playDialOut {
    
    NSString *path = [[[NSBundle mainBundle] resourcePath] stringByAppendingPathComponent:@"CallConnected.mp3"];
    BOOL r = [[NSFileManager defaultManager] fileExistsAtPath:path];
    NSLog(@"exist:%d", r);
    
    AVAudioSession *session = [AVAudioSession sharedInstance];
    [session setCategory:AVAudioSessionCategoryPlayAndRecord error:nil];
    
    NSURL *u = [NSURL fileURLWithPath:path];
    self.player = [[AVAudioPlayer alloc] initWithContentsOfURL:u error:nil];
    [self.player setDelegate:self];
    
    [self.player play];
}

/**
 *  创建拨号
 *
 *  @param voip  VOIP
 */
-(void) makeDialing:(VOIPSession*)voip{
    [voip dial];
    [self playDialOut];
}

-(NSString*) getTimeStrFromSeconds:(UInt64)seconds{
    if (seconds >= 3600) {
        return [NSString stringWithFormat:@"%02lld:%02lld:%02lld",seconds/3600,(seconds%3600)/60,seconds%60];
    }else{
        return [NSString stringWithFormat:@"%02lld:%02lld",(seconds%3600)/60,seconds%60];
    }
}

/**
 *  显示通话中
 */
-(void) setOnTalkingUIShow{

    [self.durationLabel setHidden:NO];
    [self.durationLabel setText:[self getTimeStrFromSeconds:self.conversationDuration]];
    [self.durationLabel sizeToFit];
    [self.durationLabel setTextAlignment:NSTextAlignmentCenter];
    [self.durationLabel setCenter:CGPointMake((self.view.frame.size.width)/2, self.headView.frame.origin.y + self.headView.frame.size.height + 50)];
}

/**
 *  刷新时间显示
 */
-(void) refreshDuration{
    self.conversationDuration += 1;
    [self.durationLabel setText:[self getTimeStrFromSeconds:self.conversationDuration]];
    [self.durationLabel sizeToFit];
    [self.durationLabel setTextAlignment:NSTextAlignmentCenter];
    [self.durationLabel setCenter:CGPointMake((self.view.frame.size.width)/2, self.headView.frame.origin.y + self.headView.frame.size.height + 50)];
}

#pragma mark - VOIPStateDelegate
-(void)onRefuse {
    [self.player stop];
    self.player = nil;
    
    [self dismiss];
}

-(void)onHangUp {
    if (self.isConnected) {
        if (self.refreshTimer && [self.refreshTimer isValid]) {
            [self.refreshTimer invalidate];
            self.refreshTimer = nil;
        }
        [self stopStream];
        [self dismiss];
    } else {
        [self.player stop];
        self.player = nil;
        [self dismiss];
    }
}

-(void)onTalking {
    [self.player stop];
    self.player = nil;
    
    [self.view makeToast:@"对方正在通话中!" duration:2.0 position:@"center"];

    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        [self dismiss];
    });
}

-(void)onDialTimeout {
    [self.player stop];
    self.player = nil;
    
    
    [self dismiss];
}

-(void)onAcceptTimeout {
    [self dismiss];
}

-(void)onConnected {
    self.isConnected = YES;
    
    [self setOnTalkingUIShow];
    [self.player stop];
    self.player = nil;
    
    NSLog(@"call voip connected");
    [self startStream];
    
    
    self.hangUpButton.hidden = NO;
    self.acceptButton.hidden = YES;
    self.refuseButton.hidden = YES;
}

-(void)onRefuseFinished {
    [self dismiss];
}

@end
