package com.done.doneserialport.rtsp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.done.doneserialport.MyApplication;
import com.done.doneserialport.rtsp.server.ClientManager;
import com.done.doneserialport.rtsp.server.DoneRtspServer;
import com.done.doneserialport.rtsp.server.SmartRtspClient;
import com.done.doneserialport.util.WifiApManager;
import com.jl.carrecord.R;


import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.rtsp.RtspClient;
import net.majorkernelpanic.streaming.rtsp.RtspServer;
import net.majorkernelpanic.streaming.rtsp.UriParser;
import net.majorkernelpanic.streaming.video.VideoQuality;


import java.net.InetAddress;
import java.net.UnknownHostException;

public class RtspServerActivity extends Activity implements View.OnClickListener, RtspClient.Callback,
        Session.Callback {

    private static final String TAG = "RtspServerActivity";
    private EditText etIP;
    private EditText etPORT;
    private static TextView tvInfo;
    private Button btnStartServer;
    private Button btnStopServer;

    SurfaceView mSurfaceView;
    private Session mSession;
    private RtspClient mClient;
    private Button btnTest;

    private Context mContext;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rtsp_server);
        mContext = this;
        initView();
        initWifi();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
//        ClientManager.getInstance().showDown();
        stopService(new Intent(this, DoneRtspServer.class));
    }

    private void initView() {
        btnStartServer = (Button) findViewById(R.id.btn_server_start);
        btnStopServer = (Button) findViewById(R.id.btn_server_stop);
        btnTest = (Button) findViewById(R.id.btn_server_test);
        etIP = (EditText) findViewById(R.id.et_server_ip);
        etPORT = (EditText) findViewById(R.id.et_server_port);
        tvInfo = (TextView) findViewById(R.id.tv_server_info);
        tvInfo.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                tvInfo.setText("日志窗口");
                return true;
            }
        });
        etIP.clearFocus();
        etPORT.clearFocus();
        tvInfo.setText("日志窗口");
        btnStartServer.setOnClickListener(this);
        btnStopServer.setOnClickListener(this);
        btnTest.setOnClickListener(this);
        mSurfaceView = (SurfaceView) findViewById(R.id.sfv_lib);
    }

    private void initWifi() {
        WifiApManager.getInstance().startWifiAp("Done-RTSP", "12345678", false, false);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_server_start:
//                new Thread(() -> startServer()).start();
                startService(new Intent(mContext, DoneRtspServer.class));
                break;
            case R.id.btn_server_stop:
//                stopServer();
                stopService(new Intent(mContext, DoneRtspServer.class));
                break;
            case R.id.btn_server_test:
                WifiApManager.getInstance().setUsbTethering(true);
//                TestRtspClient();
//                startLibClient();
//                startLibServer();
                break;
            default:
                break;
        }
    }

    private void startLibServer() {
        // Sets the port of the RTSP server to 1234
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(RtspServer.KEY_PORT, String.valueOf(10000));
        editor.commit();

        // Configures the SessionBuilder
        SessionBuilder.getInstance()
                .setSurfaceView(mSurfaceView)
                .setPreviewOrientation(90)
                .setContext(getApplicationContext())
                .setAudioEncoder(SessionBuilder.AUDIO_NONE)
                .setVideoEncoder(SessionBuilder.VIDEO_H264);

        // Starts the RTSP server
        this.startService(new Intent(this, RtspServer.class));
    }

    private void startLibClient() {
        if (mSession == null) {
            // Configures the SessionBuilder
            mSession = SessionBuilder.getInstance()
                    .setContext(getApplicationContext())
                    .setAudioEncoder(SessionBuilder.AUDIO_AAC)
                    .setAudioQuality(new AudioQuality(8000, 16000))
                    .setVideoEncoder(SessionBuilder.VIDEO_H264)
                    .setSurfaceView(mSurfaceView)
                    .setPreviewOrientation(0)
                    .setCallback(this)
                    .build();
        }

        // Configures the RTSP client
        mClient = new RtspClient();
        mClient.setSession(mSession);
        mClient.setCallback(this);

        String ip = "192.168.42.1";//"192.168.42.1";
        String port = "554";
        String path = "live/ch01_0";
        mClient.setServerAddress(ip, Integer.parseInt(port));
        mClient.setStreamPath("/" + path);
        mClient.startStream();
    }

    private void TestRtspClient() {
        String request = "DESCRIBE rtsp://192.168.42.1:554/live/ch01_0 RTSP/1.0\r\n" + addHeaders() + "\r\n";

        SmartRtspClient.getInstance().sendToRtspServer(request);
    }

    int mCSeq = 0;
    String mSessionID = null;

    private String addHeaders() {
        return "CSeq: " + (++mCSeq) + "\r\n" +
                "Content-Length: 0\r\n" +
                "Session: " + mSessionID + "\r\n";
        // For some reason you may have to remove last "\r\n" in the next line to make the RTSP client work with your wowza server :/
        //(mAuthorization != null ? "Authorization: " + mAuthorization + "\r\n" : "") + "\r\n";
    }

    private void stopServer() {
        ClientManager.getInstance().showDown();
    }

    private void startServer() {
        InetAddress inetAddress = null;
        try {
            inetAddress = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        String ip = "NULL";
        if (inetAddress != null) {
            ip = inetAddress.getHostAddress();
        }
        ClientManager.getInstance().startServer(ip, new ClientManager.OnServerListener() {
            @Override
            public void onReceive(String ip, String data) {
                refreshInfo("Socket 服务端收到来自客户端" + ip + "数据:" + data, "#00FF00");
            }

            @Override
            public void onStart(String hostIP, int hostPORT) {
                refreshInfo("Server start hostIP:" + hostIP + ",hostPORT:" + hostPORT, "#0000FF");
                MyApplication.gHANDLER.post(() -> {
                    etIP.setText(hostIP);
                    etPORT.setText(String.valueOf(hostPORT));
                });

            }

            @Override
            public void onStop(String hostIP, int hostPORT) {
                refreshInfo("Server stop hostIP:" + hostIP + ",hostPORT:" + hostPORT, "#FF0000");
            }
        });
    }

    /**
     * 打印调试窗口信息
     *
     * @param message 数据内容
     * @param mode    0是普通调试信息，1是通信内容，2是错误提示
     */
    public static void refreshInfo(final String message, int mode) {
        switch (mode) {
            case 0:
                refreshInfo(message, "#00FF00");
                break;
            case 1:
                refreshInfo(message, "#FFFF00");
                break;
            case 2:
                refreshInfo(message, "#FF0000");
                break;
            default:
                break;
        }
    }

    private static void refreshInfo(final String message, String color) {
        SpannableStringBuilder builder = new SpannableStringBuilder(message);
        builder.setSpan(new ForegroundColorSpan(Color.parseColor(color)),
                0, builder.toString().length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        MyApplication.gHANDLER.post(() -> tvInfo.append("\n" + builder));
    }

    @Override
    public void onRtspUpdate(int message, Exception exception) {

    }

    @Override
    public void onBitrateUpdate(long bitrate) {

    }

    @Override
    public void onSessionError(int reason, int streamType, Exception e) {

    }

    @Override
    public void onPreviewStarted() {

    }

    @Override
    public void onSessionConfigured() {

    }

    @Override
    public void onSessionStarted() {

    }

    @Override
    public void onSessionStopped() {

    }
}
