package com.beetle.face.api;

import com.beetle.face.Config;
import com.beetle.face.Token;
import com.google.gson.Gson;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.converter.GsonConverter;

/**
 * Created by tsung on 10/10/14.
 */
class IMHttpRetrofit {
    final IMHttp service;

    final IMHttp sdkService;

    IMHttpRetrofit() {
        RestAdapter adapter = new RestAdapter.Builder()
                .setEndpoint(Config.API_URL)
                .setConverter(new GsonConverter(new Gson()))
                .setRequestInterceptor(new RequestInterceptor() {
                    @Override
                    public void intercept(RequestFacade request) {
                        if (Token.getInstance().accessToken != null && !Token.getInstance().accessToken.equals("")) {
                            request.addHeader("Authorization", "Bearer " + Token.getInstance().accessToken);
                        }
                    }
                })
                .build();

        service = adapter.create(IMHttp.class);


        RestAdapter sdkAdapter = new RestAdapter.Builder()
                .setEndpoint(Config.SDK_API_URL)
                .setConverter(new GsonConverter(new Gson()))
                .setRequestInterceptor(new RequestInterceptor() {
                    @Override
                    public void intercept(RequestFacade request) {
                        if (Token.getInstance().accessToken != null && !Token.getInstance().accessToken.equals("")) {
                            request.addHeader("Authorization", "Bearer " + Token.getInstance().accessToken);
                        }
                    }
                })
                .build();

        sdkService = sdkAdapter.create(IMHttp.class);
    }

    public IMHttp getService() {
        return service;
    }

    public IMHttp getSdkService() {
        return sdkService;
    }
}
