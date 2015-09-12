/*                                                                            
  Copyright (c) 2014-2015, GoBelieve     
    All rights reserved.		    				     			
 
  This source code is licensed under the BSD-style license found in the
  LICENSE file in the root directory of this source tree. An additional grant
  of patent rights can be found in the PATENTS file in the same directory.
*/

#import <UIKit/UIKit.h>
#import <AVFoundation/AVFoundation.h>

@class VOIPEngine;
@class VOIPSession;

@interface VOIPViewController : UIViewController<AVAudioPlayerDelegate>
@property(nonatomic) int64_t currentUID;
@property(nonatomic) int64_t peerUID;
@property(nonatomic, copy) NSString *peerName;
@property(nonatomic, copy) NSString *token;
//当前用户是否是主动呼叫方
@property(nonatomic) BOOL isCaller;


@property(nonatomic) VOIPEngine *engine;
@property(nonatomic) VOIPSession *voip;


-(BOOL)isP2P;
-(int)SetLoudspeakerStatus:(BOOL)enable;

@end
