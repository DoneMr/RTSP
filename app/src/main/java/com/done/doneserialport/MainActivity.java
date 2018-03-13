package com.done.doneserialport;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.done.doneserialport.rtsp.RtspActivity;
import com.done.doneserialport.rtsp.RtspServerActivity;
import com.done.doneserialport.serial.OnSerialListener;
import com.done.doneserialport.serial.SerialPortSettings;
import com.done.doneserialport.serial.SerialPorter;
import com.done.doneserialport.socket.TcpClient;
import com.done.doneserialport.socket.TcpSetting;
import com.done.doneserialport.socket.constants.TcpConstants;
import com.done.doneserialport.socket.interfaces.OnTcpClientListener;
import com.done.doneserialport.util.ShellUtils;
import com.done.doneserialport.util.WifiApManager;
import com.jl.carrecord.R;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * @author Done
 */
public class MainActivity extends Activity implements View.OnClickListener {

    SerialPorter serialPorter;

    private static final int SHOW_TOAST_WHAT = 0;

    TextView tvInfo;

    TcpClient tcpClient;

    Handler uiHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_TOAST_WHAT:
                    Toast.makeText(MainActivity.this, msg.obj.toString(), Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        initSerialPort();
        initView();
    }


    private void initView() {
        tvInfo = (TextView) findViewById(R.id.tv_info);
        Button btnConnect = (Button) findViewById(R.id.btn_connect);
        Button btnDisconnect = (Button) findViewById(R.id.btn_disconnect);
        Button btnSend = (Button) findViewById(R.id.btn_send);
        Button btnIP = (Button) findViewById(R.id.btn_ip);
        Button btnOpenWifi = (Button) findViewById(R.id.btn_open_wifi);
        Button btnCloseWifi = (Button) findViewById(R.id.btn_close_wifi);
        Button btnRTSP = (Button) findViewById(R.id.btn_to_rtsp);
        Button btnRTSP2 = (Button) findViewById(R.id.btn_rtsp2);
        Button btnServer = (Button) findViewById(R.id.btn_rtsp_server);
        btnServer.setOnClickListener(this);
        btnConnect.setOnClickListener(this);
        btnRTSP2.setOnClickListener(this);
        btnRTSP.setOnClickListener(this);
        btnOpenWifi.setOnClickListener(this);
        btnCloseWifi.setOnClickListener(this);
        btnIP.setOnClickListener(this);
        btnDisconnect.setOnClickListener(this);
        btnSend.setOnClickListener(this);
        tvInfo.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                tvInfo.setText("NULL");
                return true;
            }
        });
    }

    private void initSerialPort() {
        SerialPortSettings settings = new SerialPortSettings();
        settings.setmDevFile("/dev/ttyMT1");
        serialPorter = new SerialPorter(settings, new OnSerialListener() {
            @Override
            public void onConnect() {
                showToast("onConnect连接成功");
            }

            @Override
            public void onDisconnect(int i, String s) {
                showToast("onDisconnect断开连接");
            }

            @Override
            public void onReceive(String s, byte[] bytes) {
                showToast("onReceive" + s);
            }

            @Override
            public void onError(int i, String s, Object o) {
                showToast("onError:" + i + "," + s);
            }
        });
        serialPorter.begin();
    }

    private void showToast(String message) {
        uiHandler.sendMessage(uiHandler.obtainMessage(SHOW_TOAST_WHAT, message));
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_disconnect:
//                tcpClient.disconnect();
                new Thread(() -> {
                    if (ShellUtils.checkRootPermission()) {
                        uiHandler.obtainMessage(SHOW_TOAST_WHAT, "well, u have root permission").sendToTarget();
                    } else {
                        uiHandler.obtainMessage(SHOW_TOAST_WHAT, "sorry, u do not have root permission!").sendToTarget();
                    }
                }).start();
                break;
            case R.id.btn_connect:
                initSocket();
                break;
            case R.id.btn_send:
                tcpClient.sendToServer("123", TcpConstants.SEND_DATA_TYPE.STRING);
                break;
            case R.id.btn_ip:
                getIP();
                formatIP("rtsp://184.72.239.149/vod/mp4://BigBuckBunny_175k.mov");
                break;
            case R.id.btn_open_wifi:
                openWifiAp();
                break;
            case R.id.btn_close_wifi:
                closeWifiAp();
                break;
            case R.id.btn_to_rtsp:
                toRTSP();
                break;
            case R.id.btn_rtsp2:
                break;
            case R.id.btn_rtsp_server:
                toRtspServer();
                break;
            default:
                break;
        }
    }

    private void toRtspServer() {
        Intent intent = new Intent(MainActivity.this, RtspServerActivity.class);
        startActivity(intent);
    }


    private void formatIP(String uriStr) {
        try {
            URI uri = new URI(uriStr);
            tvInfo.append("\n[url:" + uri.getHost() + ":" + uri.getPort() + "]");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void toRTSP() {
        Intent intent = new Intent(MainActivity.this, RtspActivity.class);
        startActivity(intent);
    }

    private void closeWifiAp() {
        WifiApManager.getInstance().closeWifiAp();
    }

    private void openWifiAp() {
        WifiApManager.getInstance().startWifiAp("Done-WIFI", "12345678", false, true);
    }

    private void getIP() {
        WifiManager mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        final String IPAddress = intToIp(wifiInfo.getIpAddress());
        DhcpInfo dhcpinfo = mWifiManager.getDhcpInfo();
        final String serverAddress = intToIp(dhcpinfo.serverAddress);
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                tvInfo.append("IPAddress:" + IPAddress + ",serverAddress:" + serverAddress);
            }
        });
    }

    private String intToIp(int paramInt) {
        return (paramInt & 0xFF) + "." + (0xFF & paramInt >> 8) + "." + (0xFF & paramInt >> 16) + "."
                + (0xFF & paramInt >> 24);
    }

    private void appendText(final String text) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (tvInfo != null) {
                    tvInfo.append(text + "\n");
                }
            }
        });
    }

    private void initSocket() {
        TcpSetting tcpSetting = new TcpSetting();
        tcpSetting.setHostIP("192.168.10.141");
        tcpSetting.setPort(60000);
        if (tcpClient == null) {
            tcpClient = new TcpClient();
        }
        tcpClient.connect(tcpSetting, new OnTcpClientListener() {
            @Override
            public void onConnect(TcpConstants.CLIENT_CODE code) {
                appendText("onConnect:" + code.getCode() + "," + code.getMessage());
            }

            @Override
            public void onDisconnect(TcpConstants.CLIENT_CODE code, InetAddress inetAddress) {
                appendText("onDisconnect:" + code.getCode() + "," + code.getMessage());
            }

            @Override
            public void onReceive(String s, byte[] bytes) {
                appendText("onReceive:" + s);
            }
        });
    }
}
