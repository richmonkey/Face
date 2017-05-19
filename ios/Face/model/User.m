//
//  User.m
//  Message
//
//  Created by houxh on 14-7-6.
//  Copyright (c) 2014年 daozhu. All rights reserved.
//

#import "User.h"

@implementation User
@synthesize avatarURL = _avatarURL;

-(NSString*) displayName{
    if (self.name.length == 0) {
        return  self.phoneNumber.number;
    }
    return self.name;
}

-(void)setAvatarURL:(NSString *)avatarURL {
    if ([avatarURL hasPrefix:@"http://"]) {
        _avatarURL = [NSString stringWithFormat:@"https://%@", [avatarURL substringFromIndex:7]];
     } else {
         _avatarURL = [avatarURL copy];
     }
}
@end

