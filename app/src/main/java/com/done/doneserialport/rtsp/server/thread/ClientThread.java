package com.done.doneserialport.rtsp.server.thread;

import android.text.TextUtils;
import android.util.Log;

import com.done.doneserialport.rtsp.server.SmartRtspClient;
import com.done.doneserialport.rtsp.server.constant.RtspMethod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;

/**
 * 　　　　　　　　┏┓　　　┏┓+ +
 * 　　　　　　　┏┛┻━━━┛┻┓ + +
 * 　　　　　　　┃　　　　　　　┃
 * 　　　　　　　┃　　　━　　　┃ ++ + + +
 * 　　　　　　 ████━████ ┃+
 * 　　　　　　　┃　　　　　　　┃ +
 * 　　　　　　　┃　　　┻　　　┃
 * 　　　　　　　┃　　　　　　　┃ + +
 * 　　　　　　　┗━┓　　　┏━┛
 * 　　　　　　　　　┃　　　┃
 * 　　　　　　　　　┃　　　┃ + + + +
 * 　　　　　　　　　┃　　　┃　　　　Code is far away from bug with the animal protecting
 * 　　　　　　　　　┃　　　┃ + 　　　　神兽保佑,代码无bug
 * 　　　　　　　　　┃　　　┃
 * 　　　　　　　　　┃　　　┃　　+
 * 　　　　　　　　　┃　 　　┗━━━┓ + +
 * 　　　　　　　　　┃ 　　　　　　　┣┓
 * 　　　　　　　　　┃ 　　　　　　　┏┛
 * 　　　　　　　　　┗┓┓┏━┳┓┏┛ + + + +
 * 　　　　　　　　　　┃┫┫　┃┫┫
 * 　　　　　　　　　　┗┻┛　┗┻┛+ + + +
 * Created by Done on 2017/11/9.
 *
 * @author by Done
 */

public class ClientThread extends Thread {

    private static final String TAG = "ClientThread";

    private Socket client;

    private OutputStream out = null;
    private BufferedReader reader = null;
    private String line = null;
    private OnClientStatus onClientStatus;
    private String ip;
    private boolean isRun = false;

    public ClientThread(Socket socket, String ip) {
        super(ip);
        client = socket;
        this.ip = ip;
        initTrans();
    }

    public void setOnClientStatus(OnClientStatus onClientStatus) {
        this.onClientStatus = onClientStatus;
    }

    private void initTrans() {

        try {
            out = client.getOutputStream();
            reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            isRun = true;
        } catch (IOException e) {
            e.printStackTrace();
            isRun = false;
        }

    }

    @Override
    public void run() {
        super.run();
        callbackConnect();
        while (isRun) {
            Log.d(TAG, "isReading...");
            try {
                parseRequest();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    public boolean sendToRtspClient(String data) {
        try {
            out.write(data.getBytes("UTF-8"));
            out.flush();
            Log.d(TAG, "发送给外部rtsp客户端数据:" + data);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "发送到外部rtsp客户端数据失败");
            e.printStackTrace();
        }
        return false;
    }

    private boolean isSocketClose() {
        try {
            client.sendUrgentData(0xFF);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void closeClient() {
        isRun = false;
        if (reader != null) {
            try {
                reader.close();
                reader = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (out != null) {
            try {
                out.close();
                out = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (client != null) {
            try {
                client.close();
                client = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.interrupt();
        callbackDisconnect();
    }


    private void parseRequest() throws Exception {
        boolean hasMethod = false;
        boolean hasEnd = false;
        int bodyCount = -1;
        StringBuilder stringBuilder = new StringBuilder("");
        StringBuffer body = new StringBuffer("");
        while ((line = reader.readLine()) != null) {
            Log.d(TAG, "收到外部rtsp客户端数据:" + line);
            if (!hasMethod) {
                hasMethod = isNewPackage(line);
            }
            if (hasMethod && bodyCount < 0) {
                line = line.replace("192.168.43.1:10010", "192.168.42.1:554/live/ch01_0");
                bodyCount = calcBodyLength(line);
            }
            stringBuilder.append(line + "\r\n");
            if (bodyCount > 0) {
                body.append(line + "\r\n");
            }
            if (hasMethod) {
                hasEnd = stringBuilder.toString().endsWith("\r\n\r\n");
            }
            if (hasMethod && hasEnd) {
                if (bodyCount < 0) {
                    callbackReceive(ip, stringBuilder.toString());
                    SmartRtspClient.getInstance().sendToRtspServer(stringBuilder.toString());
                    stringBuilder.setLength(0);
                    break;
                } else if (bodyCount > 0) {
                    if (body.toString().getBytes().length == bodyCount) {
                        callbackReceive(ip, stringBuilder.toString());
                        SmartRtspClient.getInstance().sendToRtspServer(stringBuilder.toString());
                        body.setLength(0);
                        break;
                    }
                }
            }
        }
        if (line == null) {
            throw new SocketException("read line is null");
        }
    }

    private int calcBodyLength(String line) {
        if (line.startsWith("Content-Length:")) {
            line = line.replace(" ", "");
            return Integer.parseInt(line.substring(line.indexOf(":") + 1));
        }
        return -1;
    }

    /**
     * 是否是一包新数据
     *
     * @param tmpPackage
     * @return
     */
    private boolean isNewPackage(String tmpPackage) {
        boolean bRet = false;
        if (TextUtils.isEmpty(tmpPackage)) {
            return false;
        }
        for (String method : RtspMethod.METHODS) {
            if (tmpPackage.startsWith(method)) {
                return true;
            }
        }
        return bRet;
    }

    private void callbackConnect() {
        if (onClientStatus != null) {
            Log.d(TAG, "已和外部rtsp客户端建立socket连接");
            onClientStatus.onConnect();
        }
    }

    private void callbackDisconnect() {
        if (onClientStatus != null) {
            Log.e(TAG, "已和外部rtsp客户端额断开连接");
            onClientStatus.onDisconnect();
        }
    }

    private void callbackReceive(String ip, String data) {
        if (onClientStatus != null) {
            Log.d(TAG, "收到外部客户端->" + ip + ",发送过来的一整包数据:" + data);
            onClientStatus.onReceive(ip, data);
        }
    }

    public interface OnClientStatus {
        void onConnect();

        void onDisconnect();

        void onReceive(String ip, String data);
    }
}
