import React, { Component } from 'react';
import {
    View,
    Text,
    ScrollView,
    TouchableHighlight,
    Image,
    Platform,
    NativeModules,
    NativeAppEventEmitter,
    BackAndroid
} from 'react-native';

import Permissions from 'react-native-permissions';
import GroupCall from './GroupCall.js';
var Sound = require('react-native-sound');

var native = NativeModules.ConferenceActivity || NativeModules.ConferenceViewController;

const SESSION_DIAL = "dial";
const SESSION_ACCEPT = "accept";
const SESSION_CONNECTED = "connected";

export default class Conference extends GroupCall {
    constructor(props) {
        super(props);
        
        var sessionState = this.props.isInitiator ? SESSION_DIAL : SESSION_ACCEPT;
        this.state.sessionState = sessionState;
        
        this.canceled = false;
        this._onCancel = this._onCancel.bind(this);
        this._onRefuse = this._onRefuse.bind(this);
        this._onAccept = this._onAccept.bind(this);

        this._handleBack = this._handleBack.bind(this);


        this.name = "" + this.props.uid;
        this.room = this.props.channelID;

        console.log("uid:", this.props.uid, "channel id:", this.props.channelID);
        console.log("name:", this.name, " room:", this.room);        
    }


    /**
     * Inits new connection and conference when conference screen is entered.
     *
     * @inheritdoc
     * @returns {void}
     */
    componentWillMount() {
        this.acceptSubscription = NativeAppEventEmitter.addListener(
            'onRemoteAccept',
            (event) => {
                this._onRemoteAccept();
            }
        );

        this.subscription = NativeAppEventEmitter.addListener(
            'onAllRemoteRefuse',
            (event) => {
                console.log("on remote refuse");
                this._onAllRemoteRefuse();
            }
        );

        BackAndroid.addEventListener('hardwareBackPress', this._handleBack);
        
        if (!this.props.isInitiator) {
            this.play("call.mp3");
        } else {
            this.play("start.mp3");
        }

        //60s接听/呼叫超时
        this.timer = setTimeout(
            () => {
                if (this.whoosh) {
                    this.whoosh.stop();
                    this.whoosh.release();
                    this.whoosh = null;
                }
                native.dismiss();
            },
            60*1000
        );
        
        Promise.resolve()
               .then(() => this.requestPermission('camera'))
               .then(() => this.requestPermission('microphone'));
    }

    requestPermission(permission) {
        return Permissions.getPermissionStatus(permission)
                          .then(response => {
                              console.log("permission:" + permission + " " + response);
                              if (response == 'authorized') {
                                  
                              } else if (response == 'undetermined') {
                                  return response;
                              } else if (response == 'denied' || 
                                         response == 'restricted') {
                                  throw response;
                              }
                          })
                          .then(() => Permissions.requestPermission(permission))
                          .then((response) => {
                              console.log("permission:" + permission + " " + response);
                              if (response == 'authorized') {
                                  return response;
                              } else if (response == 'undetermined') {
                                  throw response;
                              } else if (response == 'denied' || 
                                         response == 'restricted') {
                                  throw response;
                              }                               
                          });        
    }

    componentWillUnmount() {
        console.log("conference component will unmount");

        if (this.timer) {
            clearTimeout(this.timer);
            this.timer = null;
        }

        if (this.whoosh) {
            this.whoosh.stop();
            this.whoosh.release();
            this.whoosh = null;
        }

        this.acceptSubscription.remove();
        this.subscription.remove();

        BackAndroid.removeEventListener('hardwareBackPress', this._handleBack)

    }

    /**
     * Implements React's {@link Component#render()}.
     *
     * @inheritdoc
     * @returns {ReactElement}
     */
    render() {
        console.log("session state:", this.state.sessionState);
        
        if (this.state.sessionState == SESSION_DIAL) {
            return this.renderDial();
        } else if (this.state.sessionState == SESSION_ACCEPT) {
            return this.renderAccept();
        } else if (this.state.sessionState == SESSION_CONNECTED) {
            return this.renderConference();
        }
        return null;
    }


    
    //呼叫界面
    renderDial() {
        console.log("render dial");
        return (
            <View style={{flex:1, backgroundColor:"white"}}>
                <View style={{flex:1}} >
                    <ScrollView
                        contentContainerStyle = {{flex:1,
                                                  justifyContent:"center",
                                                  alignItems:"center"}}
                        style={{flex:1}}
                        horizontal = { true }
                        showsHorizontalScrollIndicator = { false }
                        showsVerticalScrollIndicator = { false } >
                        {this.props.partipants.map(p => {
                             return <Image
                                        style = {{resizeMode:"stretch", width:120, height:120}}
                                        key = { p.uid }
                                        source = {p.avatar ? {uri:p.avatar} : require('../img/avatar_contact.png') } />})
                        }
                    </ScrollView>
                </View>
                <View style={{
                    flex:1,
                    flexDirection:"row",
                    justifyContent:'center',
                    alignItems: 'center'}}>
                    
                    <TouchableHighlight onPress={this._onCancel}
                                        style = {{
                                            backgroundColor:"blue",
                                            borderRadius: 35,
                                            height:60,
                                            width: 60,
                                            justifyContent: 'center'
                                            }}
                                        underlayColor="red" >
                        
                        <Image source={{uri: 'Call_hangup'}}
                               style={{width: 60, height: 60}} />
                    </TouchableHighlight>
                </View>
            </View>
        );
    }

    //接听界面
    renderAccept() {
        console.log("render accept");

        var p = this.props.partipants.find(p => p.uid == this.props.initiator);

        if (!p) {
            return;
        }
        
        return (
            <View style={{flex:1, backgroundColor:"white"}}>
                <View style={{
                    flex:1,
                    justifyContent:"center",
                    alignItems:"center"}}>
                    <Image
                        style = {{resizeMode:"stretch", width:120, height:120}}
                        key = { p.uid }
                        source = {p.avatar ? {uri:p.avatar} : require('../img/avatar_contact.png') } />
                    <Text style={{fontSize:32}}>{p.name}</Text>
                    <Text style={{fontSize:12}}>邀请你进行语音通话</Text>
                </View>
                <View style={{flex:1}}>
                    <View style={{alignItems: 'center'}}>
                        <Text>
                            通话成员
                        </Text>
                        <View style={{
                            flex:1,
                            flexDirection:"row",
                            justifyContent:"center"}}>
                            {this.props.partipants.map(p => {
                                 return <Image
                                            style = {{resizeMode:"stretch", width:32, height:32}}
                                            key = { p.uid }
                                            source = {p.avatar ? {uri:p.avatar} : require('../img/avatar_contact.png') } />})
                            }
                        </View>
                    </View>
                    <View style={{
                        flex:1,
                        flexDirection:"row",
                        justifyContent:'space-around',
                        alignItems: 'center' }}>
                        <TouchableHighlight onPress={this._onRefuse}
                                            style = {{
                                                backgroundColor:"blue",
                                                borderRadius: 35,
                                                height:60,
                                                width: 60,
                                                justifyContent: 'center'
                                            }}
                                            underlayColor="red">

                            <Image source={{uri: 'Call_hangup'}}
                                   style={{alignSelf: 'center', width: 60, height: 60}} />
                            
                        </TouchableHighlight>
                        

                        <TouchableHighlight onPress={this._onAccept}
                                            style = {{
                                                backgroundColor:"blue",
                                                borderRadius: 35,
                                                height:60,
                                                width: 60,
                                                justifyContent: 'center'
                                            }} >
                            <Image source={{uri: 'Call_Ans'}}
                                   style={{alignSelf: 'center', width: 60, height: 60}} />
                        </TouchableHighlight>
                    </View>
                </View>
            </View>
        );        
    }

    _handleBack() {
        console.log("session state:", this.state.sessionState);
        
        if (this.state.sessionState == SESSION_DIAL) {
            this._onCancel();
        } else if (this.state.sessionState == SESSION_ACCEPT) {
            this._onRefuse();
        } else if (this.state.sessionState == SESSION_CONNECTED) {
            this._onHangUp();
        }

        //不立刻退出界面
        return true;
    }

    
    _onRemoteAccept() {
        console.log("on remote accept");

        if (this.whoosh) {
            this.whoosh.stop();
            this.whoosh.release();
            this.whoosh = null;
        }

        if (this.timer) {
            clearTimeout(this.timer);
            this.timer = null;
        }
        
        if (this.state.sessionState == SESSION_DIAL) {
            this.setState({sessionState:SESSION_CONNECTED});            
            this.start();
        }
    }

    
    //所有人都拒绝接听
    _onAllRemoteRefuse() {
        this._onCancel();
    }

    _onHangUp() {
        super._onHangUp();
        this.dismiss();
    }
    
    _onCancel() {
        if (this.canceled) {
            return;
        }

        this.canceled = true;
        
        this.dismiss();
    }

    _onAccept() {
        console.log("accept...");

        native.accept();
        if (this.whoosh) {
            this.whoosh.stop();
            this.whoosh.release();
            this.whoosh = null;
        }

        if (this.timer) {
            clearTimeout(this.timer);
            this.timer = null;
        }

        this.setState({sessionState:SESSION_CONNECTED});
        this.start();
    }

    _onRefuse() {
        console.log("refuse...");
        native.refuse();
        this.dismiss();
    }

    dismiss() {
        if (this.timer) {
            clearTimeout(this.timer);
            this.timer = null;
        }

        if (this.whoosh) {
            this.whoosh.stop();
            this.whoosh.release();
            this.whoosh = null;
        }
        
        native.dismiss();
    }


    play(name) {
        console.log("play:" + name);
        // Load the sound file 'whoosh.mp3' from the app bundle
        // See notes below about preloading sounds within initialization code below.
        var whoosh = new Sound(name, Sound.MAIN_BUNDLE);
        
        // Loop indefinitely until stop() is called        
        whoosh.setNumberOfLoops(-1);
        whoosh.prepare((error) => {
            if (error) {
                console.log('failed to load the sound', error);
            } else { // loaded successfully
                console.log('duration in seconds: ' + whoosh.getDuration() +
                            'number of channels: ' + whoosh.getNumberOfChannels());
                // Get properties of the player instance
                console.log('volume: ' + whoosh.getVolume());
                console.log('pan: ' + whoosh.getPan());
                console.log('loops: ' + whoosh.getNumberOfLoops());
                
                whoosh.play((success) => {
                    if (success) {
                        console.log('successfully finished playing');
                    } else {
                        console.log('playback failed due to audio decoding errors');
                    }
                });
            }
        });

    
        this.whoosh = whoosh;
    }

}
