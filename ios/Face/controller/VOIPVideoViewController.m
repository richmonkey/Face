//
//  VOIPVideoViewController.m
//  voip_demo
//
//  Created by houxh on 15/9/7.
//  Copyright (c) 2015年 beetle. All rights reserved.
//

#import "VOIPVideoViewController.h"
#import <voipsession/VOIPSession.h>
#import "UserPresent.h"
#import "Token.h"

@interface VOIPVideoViewController ()<RTCEAGLVideoViewDelegate>


@property BOOL showCancel;

@end

@implementation VOIPVideoViewController

- (void)viewDidLoad {
    self.isAudioOnly = NO;
    
    [super viewDidLoad];
    
    self.switchButton = [[UIButton alloc] initWithFrame:CGRectMake(240,27,42,24)];
    [self.switchButton setImage:[UIImage imageNamed:@"switch"] forState:UIControlStateNormal];
    [self.switchButton addTarget:self
                     action:@selector(switchCamera:)
           forControlEvents:UIControlEventTouchUpInside];
    [self.switchButton setHidden:YES];
    
    [self.view addSubview:self.switchButton];
    
    [self.hangUpButton setAlpha:0.6f];
    
    self.durationCenter = CGPointMake(self.view.frame.size.width/2, 40);
  

    
    RTCEAGLVideoView *remoteVideoView = [[RTCEAGLVideoView alloc] initWithFrame:self.view.bounds];
    remoteVideoView.delegate = self;
    
    self.remoteVideoView = remoteVideoView;
    [self.view insertSubview:self.remoteVideoView atIndex:0];
    
    
    CGRect rect = self.view.bounds;
    CGRect frame = CGRectMake(rect.size.width*0.72, rect.size.height*0.72, rect.size.width*0.25, rect.size.height*0.25);
    RTCCameraPreviewView *localVideoView = [[RTCCameraPreviewView alloc] initWithFrame:frame];
    self.localVideoView = localVideoView;
    [self.view insertSubview:self.localVideoView aboveSubview:self.remoteVideoView];
    
    
    self.localVideoView.hidden = YES;
    self.remoteVideoView.hidden = YES;
    
    UITapGestureRecognizer*tapGesture = [[UITapGestureRecognizer alloc]initWithTarget:self action:@selector(tapAction:)];
    [self.remoteVideoView addGestureRecognizer:tapGesture];
    
    self.showCancel = YES;
    
    AVAuthorizationStatus authStatus = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];
    if(authStatus == AVAuthorizationStatusAuthorized) {
        // do your logic
        AVAuthorizationStatus audioAuthStatus = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeAudio];
        if(audioAuthStatus == AVAuthorizationStatusAuthorized) {
            if (self.isCaller) {
                [self dial];
            } else {
                [self waitAccept];
            }
        } else if(audioAuthStatus == AVAuthorizationStatusDenied){
            // denied
        } else if(audioAuthStatus == AVAuthorizationStatusRestricted){
            // restricted, normally won't happen
        } else if(audioAuthStatus == AVAuthorizationStatusNotDetermined){
            [[AVAudioSession sharedInstance] requestRecordPermission:^(BOOL granted) {
                if (granted) {
                    if (self.isCaller) {
                        [self dial];
                    } else {
                        [self waitAccept];
                    }
                } else {
                    NSLog(@"can't grant record permission");
                }
            }];
            
        }
        
    } else if(authStatus == AVAuthorizationStatusDenied){
        // denied
    } else if(authStatus == AVAuthorizationStatusRestricted){
        // restricted, normally won't happen
    } else if(authStatus == AVAuthorizationStatusNotDetermined){
        // not determined?!
        [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo completionHandler:^(BOOL granted) {
            if(granted){
                NSLog(@"Granted access to %@", AVMediaTypeVideo);
                AVAuthorizationStatus audioAuthStatus = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeAudio];
                if(audioAuthStatus == AVAuthorizationStatusAuthorized) {
                    if (self.isCaller) {
                        [self dial];
                    } else {
                        [self waitAccept];
                    }
                } else if(audioAuthStatus == AVAuthorizationStatusDenied){
                    // denied
                } else if(audioAuthStatus == AVAuthorizationStatusRestricted){
                    // restricted, normally won't happen
                } else if(audioAuthStatus == AVAuthorizationStatusNotDetermined){
                    [[AVAudioSession sharedInstance] requestRecordPermission:^(BOOL granted) {
                        if (granted) {
                            
                            if (self.isCaller) {
                                [self dial];
                            } else {
                                [self waitAccept];
                            }
                        } else {
                            NSLog(@"can't grant record permission");
                        }
                    }];
                }
            } else {
                NSLog(@"Not granted access to %@", AVMediaTypeVideo);
            }
        }];
    }
}


- (void)videoView:(RTCEAGLVideoView *)videoView didChangeVideoSize:(CGSize)size {
    
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
}

-(void)switchCamera:(id)sender {
    NSLog(@"switch camera");
    
    RTCVideoSource* source = self.localVideoTrack.source;
    if ([source isKindOfClass:[RTCAVFoundationVideoSource class]]) {
        RTCAVFoundationVideoSource* avSource = (RTCAVFoundationVideoSource*)source;
        avSource.useBackCamera = !avSource.useBackCamera;
    }
}

-(void)tapAction:(id)sender{
    if (self.showCancel) {
        self.showCancel = NO;
        
        [self.headView setHidden:YES];
        
        [UIView animateWithDuration:1.0 animations:^{
            [self.hangUpButton setAlpha:0.0];
            [self.durationLabel setAlpha:0.0];
            [self.switchButton setAlpha:0.0];
            [self.switchButton setAlpha:0.0];
        } completion:^(BOOL finished){
            [self.hangUpButton setHidden:YES];
            [self.durationLabel setHidden:YES];
            [self.switchButton setHidden:YES];
            [self.switchButton setHidden:YES];
        }];
    }else {
        
        self.showCancel = YES;
        
        [self.hangUpButton setHidden:NO];
        [self.durationLabel setHidden:NO];
        [self.switchButton setHidden:NO];
        [self.switchButton setHidden:NO];
        
        [UIView animateWithDuration:1.0 animations:^{
            [self.hangUpButton setAlpha:0.6f];
            [self.durationLabel setAlpha:1.0];
            [self.switchButton setAlpha:1.0];
            [self.switchButton setAlpha:1.0];
        } completion:^(BOOL finished){

        }];
    }
    
}

-(void)playDialOut {
    AVAudioSession *session = [AVAudioSession sharedInstance];
    [session setCategory:AVAudioSessionCategoryPlayAndRecord withOptions:AVAudioSessionCategoryOptionDefaultToSpeaker error: nil];
    [super playDialOut];
}

- (void)dial {
    [super dial];
    [self.voip dialVideo];
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

-(BOOL)isLoudSpeaker {
    AVAudioSession* session = [AVAudioSession sharedInstance];
    AVAudioSessionCategoryOptions options = session.categoryOptions;
    BOOL enabled = options & AVAudioSessionCategoryOptionDefaultToSpeaker;
    return enabled;
}

- (void)startStream {
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(didSessionRouteChange:) name:AVAudioSessionRouteChangeNotification object:nil];
    
    [super startStream];
    [self tapAction:nil];
    [[UIApplication sharedApplication] setIdleTimerDisabled:YES];
    self.localVideoView.hidden = NO;
    self.remoteVideoView.hidden = NO;
    [self setLoudspeakerStatus:YES];
}


-(void)stopStream {
    [[NSNotificationCenter defaultCenter] removeObserver:self name:AVAudioSessionRouteChangeNotification object:nil];
    [super stopStream];

    
    [[UIApplication sharedApplication] setIdleTimerDisabled:NO];
}


@end
