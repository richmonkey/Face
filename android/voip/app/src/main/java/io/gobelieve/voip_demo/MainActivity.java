package io.gobelieve.voip_demo;


import android.app.ProgressDialog;
import android.content.Intent;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.beetle.voip.VOIPCommand;
import com.beetle.im.VOIPControl;
import com.beetle.im.VOIPObserver;
import com.beetle.voip.VOIPService;
import com.beetle.voip.VOIPSession;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;
import org.webrtc.MediaCodecVideoDecoder;
import org.webrtc.MediaCodecVideoEncoder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import android.util.Log;

public class MainActivity extends ActionBarActivity implements VOIPObserver {

    private EditText myEditText;
    private EditText peerEditText;


    private long myUID;
    private long peerUID;
    private String token;

    ProgressDialog dialog;
    AsyncTask mLoginTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myEditText = (EditText)findViewById(R.id.editText);
        peerEditText = (EditText)findViewById(R.id.editText2);


        String androidID = Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);

        //app可以单独部署服务器，给予第三方应用更多的灵活性
        //在开发阶段也可以配置成测试环境的地址 "sandbox.voipnode.gobelieve.io", "sandbox.imnode.gobelieve.io"
        String sdkHost = "imnode2.gobelieve.io";
        VOIPService.getInstance().setHost(sdkHost);
        VOIPService.getInstance().setIsSync(false);
        VOIPService.getInstance().registerConnectivityChangeReceiver(getApplicationContext());
        VOIPService.getInstance().setDeviceID(androidID);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void dialVideo(View v) {
        try {
            final long myUID = Long.parseLong(myEditText.getText().toString());
            final long peerUID = Long.parseLong(peerEditText.getText().toString());

            if (myUID == 0 || peerUID == 0) {
                return;
            }

            if (mLoginTask != null) {
                return;
            }

            final ProgressDialog dialog = ProgressDialog.show(this, null, "登录中...");

            mLoginTask = new AsyncTask<Void, Integer, String>() {
                @Override
                protected String doInBackground(Void... urls) {
                    return MainActivity.this.login(myUID);
                }

                @Override
                protected void onPostExecute(String result) {
                    mLoginTask = null;
                    dialog.dismiss();
                    if (result != null && result.length() > 0) {
                        //设置用户id,进入MainActivity
                        String token = result;
                        MainActivity.this.token = token;
                        VOIPService.getInstance().setToken(token);
                        VOIPService.getInstance().start();

                        Intent intent = new Intent(MainActivity.this, VOIPVideoActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("peer_uid", peerUID);
                        intent.putExtra("peer_name", "测试");
                        intent.putExtra("current_uid", myUID);
                        intent.putExtra("token", token);
                        intent.putExtra("is_caller", true);
                        startActivity(intent);

                    } else {
                        Toast.makeText(MainActivity.this, "登陆失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void dial(View v) {
        try {
            final long myUID = Long.parseLong(myEditText.getText().toString());
            final long peerUID = Long.parseLong(peerEditText.getText().toString());

            if (myUID == 0 || peerUID == 0) {
                return;
            }


            if (mLoginTask != null) {
                return;
            }

            final ProgressDialog dialog = ProgressDialog.show(this, null, "登录中...");

            mLoginTask = new AsyncTask<Void, Integer, String>() {
                @Override
                protected String doInBackground(Void... urls) {
                    return MainActivity.this.login(myUID);
                }

                @Override
                protected void onPostExecute(String result) {
                    mLoginTask = null;
                    dialog.dismiss();
                    if (result != null && result.length() > 0) {
                        //设置用户id,进入MainActivity
                        String token = result;
                        MainActivity.this.token = token;
                        VOIPService.getInstance().setToken(token);
                        VOIPService.getInstance().start();

                        Intent intent = new Intent(MainActivity.this, VOIPVoiceActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("peer_uid", peerUID);
                        intent.putExtra("peer_name", "测试");
                        intent.putExtra("current_uid", myUID);
                        intent.putExtra("token", token);
                        intent.putExtra("is_caller", true);
                        startActivity(intent);

                    } else {
                        Toast.makeText(MainActivity.this, "登陆失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void receiveCall(View v) {
        try {
            final long myUID = Long.parseLong(myEditText.getText().toString());
            final long peerUID = Long.parseLong(peerEditText.getText().toString());

            if (myUID == 0 || peerUID == 0) {
                return;
            }


            if (mLoginTask != null) {
                return;
            }

            final ProgressDialog dialog = ProgressDialog.show(this, null, "登录中...");

            mLoginTask = new AsyncTask<Void, Integer, String>() {
                @Override
                protected String doInBackground(Void... urls) {
                    return MainActivity.this.login(myUID);
                }

                @Override
                protected void onPostExecute(String result) {
                    dialog.dismiss();
                    mLoginTask = null;
                    if (result != null && result.length() > 0) {
                        //设置用户id,进入MainActivity
                        String token = result;
                        MainActivity.this.token = token;
                        VOIPService.getInstance().setToken(token);
                        VOIPService.getInstance().start();
                        VOIPService.getInstance().pushVOIPObserver(MainActivity.this);

                        ProgressDialog dialog = ProgressDialog.show(MainActivity.this, null, "等待中...");

                        dialog.setTitle("等待中...");

                        MainActivity.this.dialog = dialog;
                        MainActivity.this.myUID = myUID;
                        MainActivity.this.peerUID = peerUID;

                    } else {
                        dialog.dismiss();
                        Toast.makeText(MainActivity.this, "登陆失败", Toast.LENGTH_SHORT).show();
                    }
                }
            }.execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String login(long uid) {
        //调用app自身的登陆接口获取im服务必须的access token
        //sandbox地址："http://sandbox.demo.gobelieve.io"
        String URL = "http://demo.gobelieve.io";
        String uri = String.format("%s/auth/token", URL);
        try {
            HttpClient getClient = new DefaultHttpClient();
            HttpPost request = new HttpPost(uri);
            JSONObject json = new JSONObject();
            json.put("uid", uid);
            StringEntity s = new StringEntity(json.toString());
            s.setContentEncoding((Header) new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
            request.setEntity(s);

            HttpResponse response = getClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK){
                System.out.println("login failure code is:"+statusCode);
                return null;
            }
            int len = (int)response.getEntity().getContentLength();
            byte[] buf = new byte[len];
            InputStream inStream = response.getEntity().getContent();
            int pos = 0;
            while (pos < len) {
                int n = inStream.read(buf, pos, len - pos);
                if (n == -1) {
                    break;
                }
                pos += n;
            }
            inStream.close();
            if (pos != len) {
                return null;
            }
            String txt = new String(buf, "UTF-8");
            JSONObject jsonObject = new JSONObject(txt);
            String accessToken = jsonObject.getString("token");
            return accessToken;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public void onVOIPControl(VOIPControl ctl) {
        VOIPCommand command = new VOIPCommand(ctl.content);
        if (command.cmd == VOIPCommand.VOIP_COMMAND_DIAL) {
            if (ctl.sender == peerUID) {
                dialog.dismiss();

                Intent intent = new Intent(MainActivity.this, VOIPVoiceActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("peer_uid", peerUID);
                intent.putExtra("peer_name", "测试");
                intent.putExtra("current_uid", myUID);
                intent.putExtra("token", token);
                intent.putExtra("is_caller", false);
                startActivity(intent);
            }
        } else if (command.cmd == VOIPCommand.VOIP_COMMAND_DIAL_VIDEO) {
            if (ctl.sender == peerUID) {
                dialog.dismiss();

                Intent intent = new Intent(MainActivity.this, VOIPVideoActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("peer_uid", peerUID);
                intent.putExtra("peer_name", "测试");
                intent.putExtra("current_uid", myUID);
                intent.putExtra("token", token);
                intent.putExtra("is_caller", false);
                startActivity(intent);
            }
        }
    }
}
