package com.beetle.face.api;

import com.beetle.face.Token;
import com.beetle.face.api.body.Call;
import com.beetle.face.api.body.PostAuthRefreshToken;
import com.beetle.face.api.body.PostAuthToken;
import com.beetle.face.api.body.PostDeviceToken;
import com.beetle.face.api.body.PostPhone;
import com.beetle.face.api.body.PostTextValue;
import com.beetle.face.api.types.Audio;
import com.beetle.face.api.types.Code;
import com.beetle.face.api.types.Image;
import com.beetle.face.api.types.User;
import com.beetle.face.api.types.Version;


import java.util.ArrayList;
import java.util.List;

import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.Header;
import retrofit.http.Multipart;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Part;
import retrofit.http.Query;
import retrofit.mime.TypedFile;
import rx.Observable;

/**
 * Created by tsung on 10/10/14.
 */
public interface IMHttp {
    @GET("/version/android")
    Observable<Version> getLatestVersion();

    @GET("/verify_code")
    Observable<Code> getVerifyCode(@Query("zone") String zone, @Query("number") String number);

    @POST("/auth/token")
    Observable<Token> postAuthToken(@Body PostAuthToken code);

    @POST("/auth/refresh_token")
    Observable<Token> postAuthRefreshToken(@Body PostAuthRefreshToken refreshToken);

    @POST("/images")
    Observable<Image> postImages(@Header("Content-Type") String contentType, @Body TypedFile file);
    
    @Multipart
    @PUT("/users/me/avatar")
    Observable<Image> putUsersMeAvatar(@Part("file") TypedFile file);

    @PUT("/users/me/nickname")
    Observable<Object> putUsersMeNickname(@Body PostTextValue nickname);

    @PUT("/users/me/state")
    Observable<Object> putUsersMeState(@Body PostTextValue state);

    @POST("/users")
    Observable<ArrayList<User>> postUsers(@Body List<PostPhone> phones);


    @POST("/device/bind")
    Observable<Object> bindDeviceToken(@Body PostDeviceToken token);

    @POST("/device/unbind")
    Observable<Object> unBindDeviceToken(@Body PostDeviceToken token);


    @POST("/calls")
    Observable<Object> postCall(@Body Call call);

}
