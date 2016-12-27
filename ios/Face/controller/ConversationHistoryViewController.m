//
//  ConversationHistoryViewController.m
//  Face
//
//  Created by 杨朋亮 on 2/11/14.
//  Copyright (c) 2014年 beetle. All rights reserved.
//

#import "ConversationHistoryViewController.h"
#import "History.h"
#import "HistoryDB.h"
#import "ContactDB.h"
#import "UserDB.h"
#import "User.h"
#import "UserPresent.h"
#import "Token.h"
#import "PublicFunc.h"
#import "HistoryTableViewCell.h"
#import "VOIPViewController.h"
#import "UIView+Toast.h"

#import "VOIPVideoViewController.h"
#import "VOIPVoiceViewController.h"

#import "VOIPService.h"



#define kGreenColor         RGBCOLOR(48,176,87)
#define kRedColor           RGBCOLOR(207,6,6)

static NSString *HISTORYSTR = @"historyCell";

@interface ConversationHistoryViewController ()

@property (strong,nonatomic) UITableView *tableView;
@property (strong,nonatomic) NSMutableArray *historys;
@property (strong ,nonatomic) UILabel  *emputyLabel;
@property (strong,nonatomic) User *selectedUser;

@end

@implementation ConversationHistoryViewController

- (id)initWithNibName:(NSString *)nibNameOrNil bundle:(NSBundle *)nibBundleOrNil
{
    self = [super initWithNibName:nibNameOrNil bundle:nibBundleOrNil];
    if (self) {
        // Custom initialization
    }
    return self;
}

- (void)viewDidLoad
{
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    UIImage *backColor = [UIImage imageNamed:@"chatBack"];
    UIColor *color = [[UIColor alloc] initWithPatternImage:backColor];
    [self.view setBackgroundColor:color];
    
    CGRect frame = self.view.frame;
    self.tableView = [[UITableView alloc] initWithFrame:frame style:UITableViewStylePlain];
    [self.view addSubview:self.tableView];
    [self.tableView setSeparatorStyle:UITableViewCellSeparatorStyleNone];
    self.tableView.dataSource = self;
    self.tableView.delegate  = self;
    [self.tableView setBackgroundColor:[UIColor clearColor]];
    
    UINib *nib = [UINib nibWithNibName:@"HistoryTableViewCell" bundle:nil];
    [self.tableView registerNib:nib forCellReuseIdentifier: HISTORYSTR];
    
    self.historys = [[NSMutableArray alloc] initWithArray: [[HistoryDB instance] loadHistoryDB]];
    
    [self updateEmputyContentView];
    
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onNewHistory:) name:ON_NEW_CALL_HISTORY_NOTIFY object:nil];
    
     [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onClearAllHistory:) name:CLEAR_ALL_HISTORY object:nil];
}


- (void)didReceiveMemoryWarning
{
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

#pragma mark - UITableViewDataSource
- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath{
    
    return 70.0f;
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section{
    
    return [self.historys count];
    
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath{

    HistoryTableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:HISTORYSTR];
    
    [cell setBackgroundColor:RGBCOLOR(253, 253, 253)];
    
    History *history = [self.historys objectAtIndex:indexPath.row];
    
    NSDate *createDate = [NSDate dateWithTimeIntervalSince1970:history.createTimestamp];
    NSString *creatTimeStr = [PublicFunc getConversationTimeString: createDate];
    [cell.timeLabel setText:creatTimeStr];
    
    int callDuration = history.endTimestamp - history.beginTimestamp;
    NSString *durationStr = [NSString stringWithFormat:@"%@",[PublicFunc getTimeStrFromSeconds:callDuration]];
    [cell.durationLabel setText:durationStr];
    
    User *theUser =  [[UserDB instance] loadUser:history.peerUID];
    if (!theUser) {
        [cell.nameLabel setText:@"未知用户"];
    }else{
        ABContact *c = [[ContactDB instance] loadContactWithNumber:theUser.phoneNumber];
        theUser.name = c.contactName;
        [cell.nameLabel setText:theUser.displayName];
    }
    
    bool isOut          = history.flag&FLAG_OUT;
    bool isCancel       = history.flag&FLAG_CANCELED;
    bool isRefused      = history.flag&FLAG_REFUSED;
    bool isAccepted     = history.flag&FLAG_ACCEPTED;
    bool isUnreceived   = history.flag&FLAG_UNRECEIVED;
    
    if (isOut) {
        [cell.iconView setImage:[UIImage imageNamed:@"callOutIcon"]];
        if(isCancel) {
            [cell.statusLabel setTextColor:[UIColor grayColor]];
            [cell.statusLabel setText:@"通话被取消"];
        }else if(isRefused) {
            [cell.statusLabel setTextColor:kRedColor];
            [cell.statusLabel setText:@"通话被拒绝"];
        }else if(isAccepted){
            [cell.statusLabel setTextColor:kGreenColor];
            [cell.statusLabel setText:@"通话成功"];
        }else if(isUnreceived){
            [cell.statusLabel setTextColor:kRedColor];
            [cell.statusLabel setText:@"对方未接听"];
        }
    }else{
         [cell.iconView setImage:[UIImage imageNamed:@"callInIcon"]];
        if(isCancel) {
            [cell.statusLabel setTextColor:[UIColor grayColor]];
            [cell.statusLabel setText:@"已取消"];
        }else if(isRefused) {
            [cell.statusLabel setTextColor:kRedColor];
            [cell.statusLabel setText:@"已拒绝"];
        }else if(isAccepted){
            [cell.statusLabel setTextColor:kGreenColor];
            [cell.statusLabel setText:@"通话成功"];
        }else if(isUnreceived){
            [cell.statusLabel setTextColor:[UIColor blueColor]];
            [cell.iconView setImage:[UIImage imageNamed:@"callInNotAnswerIcon"]];
            [cell.statusLabel setText:@"未接听"];
        }
    }
    return cell;
}
#pragma mark - UITableViewDelegate
- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath{
    
    //取消选中项
    [tableView deselectRowAtIndexPath:[tableView indexPathForSelectedRow] animated:YES];
    
    History *history = [self.historys objectAtIndex:indexPath.row];
    self.selectedUser = [[UserDB instance] loadUser:history.peerUID];
    
    UIActionSheet *actionSheet = [[UIActionSheet alloc]
                                  initWithTitle:nil
                                  delegate:self
                                  cancelButtonTitle:@"取消"
                                  destructiveButtonTitle:nil
                                  otherButtonTitles:@"语音呼叫", @"视频呼叫", nil];
    actionSheet.actionSheetStyle = UIActionSheetStyleBlackOpaque;
    [actionSheet showInView:self.view];
    
}

- (BOOL)tableView:(UITableView *)tableView canEditRowAtIndexPath:(NSIndexPath *)indexPath{
    if (tableView == self.tableView) {
        return YES;
    }
    return NO;
}

- (UITableViewCellEditingStyle)tableView:(UITableView *)tableView editingStyleForRowAtIndexPath:(NSIndexPath *)indexPath{
    return UITableViewCellEditingStyleDelete;
}

- (void)tableView:(UITableView *)tableView commitEditingStyle:(UITableViewCellEditingStyle)editingStyle forRowAtIndexPath:(NSIndexPath *)indexPath {
    if (editingStyle == UITableViewCellEditingStyleDelete) {
        
        History  *history = [self.historys objectAtIndex:indexPath.row];
        
        bool result = [[HistoryDB instance] removeHistory:history.hid];
        if (result) {
            
            [self.historys removeObject:history];
            
            /*IOS8中删除最后一个cell的时，报一个错误
             [RemindersCell _setDeleteAnimationInProgress:]: message sent to deallocated instance
             在重新刷新tableView的时候延迟一下*/
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.3 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                [self.tableView reloadData];
                
                if ([self.historys count] == 0) {
                    [self.tableView setHidden:YES];
                    [self.emputyLabel setHidden:NO];
                }else{
                    [self.emputyLabel setHidden:YES];
                    [self.tableView setHidden:NO];
                }
            });
        }
    }
}

-(NSString*)getUserName:(int64_t)uid {
    User *u = [[UserDB instance] loadUser:uid];
    ABContact *contact = [[ContactDB instance] loadContactWithNumber:u.phoneNumber];
    NSString *name = contact.contactName;
    if (name.length == 0) {
        name = u.phoneNumber.number;
    }
    return name;
}

#pragma mark - UIActionSheetDelegate

- (void)actionSheet:(UIActionSheet *)actionSheet clickedButtonAtIndex:(NSInteger)buttonIndex {
    if (buttonIndex == actionSheet.cancelButtonIndex) {
        return;
    }
    
    
    if ([[VOIPService instance] connectState] == STATE_CONNECTED) {
        if(buttonIndex == 0){
            NSString *name = [self getUserName:self.selectedUser.uid];
            
            VOIPVoiceViewController *controller = [[VOIPVoiceViewController alloc] init];
            controller.currentUID = [UserPresent instance].uid;
            controller.peerUID = self.selectedUser.uid;
            controller.peerName = name;
            controller.token = [Token instance].accessToken;
            controller.isCaller = YES;

            [self presentViewController:controller animated:YES completion:nil];
        }else if (buttonIndex == 1) {
            NSString *name = [self getUserName:self.selectedUser.uid];
            
            VOIPVideoViewController *controller = [[VOIPVideoViewController alloc] init];
            controller.currentUID = [UserPresent instance].uid;
            controller.peerUID = self.selectedUser.uid;
            controller.peerName = name;
            controller.token = [Token instance].accessToken;
            controller.isCaller = YES;
            
            [self presentViewController:controller animated:YES completion:nil];
        }
    }else if([[VOIPService instance] connectState] == STATE_CONNECTING){
        [self.tabBarController.view makeToast:@"正在连接,请稍等" duration:2.0f position:@"bottom"];
    }else if([[VOIPService instance] connectState] == STATE_UNCONNECTED){
        [self.tabBarController.view makeToast:@"连接出错,请检查" duration:2.0f position:@"bottom"];
    }

}


-(void) onNewHistory:(NSNotification*)notify{
   
    History *newHistory = notify.object;
    [self.historys insertObject:newHistory atIndex:0];
    [self.tableView reloadData];
    if ([self.historys count] == 0) {
        [self.tableView setHidden:YES];
        [self.emputyLabel setHidden:NO];
    }else{
        [self.emputyLabel setHidden:YES];
        [self.tableView setHidden:NO];
    }
}

-(void) onClearAllHistory:(NSNotification*)notify{
    [self.historys removeAllObjects];
    [self.tableView reloadData];
    if ([self.historys count] == 0) {
        [self.tableView setHidden:YES];
        [self.emputyLabel setHidden:NO];
    }else{
        [self.emputyLabel setHidden:YES];
        [self.tableView setHidden:NO];
    }
}

/**
 *  析构
 */
-(void) dealloc{
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

- (void) updateEmputyContentView{

        self.emputyLabel = [[UILabel alloc] initWithFrame:CGRectMake(0, 0, 250, 40)];
        [self.emputyLabel setFont:[UIFont systemFontOfSize:14.0f]];
        [self.emputyLabel setBackgroundColor:RGBACOLOR(240, 240, 240, 1.0f)];
        [self.emputyLabel setText:@"可以到通讯录选择一个人拨打电话"];
        [self.emputyLabel setTextAlignment:NSTextAlignmentCenter];
        [self.emputyLabel setTextColor:RGBACOLOR(20, 20, 20, 0.8f)];
        [self.emputyLabel setCenter:CGPointMake(self.view.center.x, self.view.center.y - 20)];
        CALayer *labelLayer = [self.emputyLabel layer];
        [labelLayer setMasksToBounds:YES];
        [labelLayer setCornerRadius: 16];
        [self.view addSubview:self.emputyLabel];
    
    if ([self.historys count] == 0) {
        [self.tableView setHidden:YES];
        [self.emputyLabel setHidden:NO];
    }else{
        [self.emputyLabel setHidden:YES];
        [self.tableView setHidden:NO];
    }
}


@end
