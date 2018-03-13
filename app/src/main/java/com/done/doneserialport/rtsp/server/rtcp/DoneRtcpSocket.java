package com.done.doneserialport.rtsp.server.rtcp;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.done.doneserialport.rtsp.server.SmartRtspClient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

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
 * Created by Done on 2017/11/10.
 *
 * @author by Done
 */

public class DoneRtcpSocket {
    private static final String TAG = "DoneRtcpSocket";

    private DatagramSocket mSocket;
    private DatagramPacket mPacket;
    private Handler mHandler;
    private byte[] message = new byte[1024 * 10];
    private String serverIp;
    private int serverPort;
    private boolean isStoped;
    private HandlerThread thread;
    private String trackID;
    private SmartRtspClient.OnRtspClientListener onRtspClientListener;

    public DoneRtcpSocket(int port, String serverIp, int serverPort, String trackID, SmartRtspClient.OnRtspClientListener onRtspClientListener) {
        try {
            Log.d(TAG, "create a rtcp client for " + trackID + ",ip" + serverIp + ":" + port);
            mSocket = new DatagramSocket(port);
            this.serverIp = serverIp;
            this.serverPort = serverPort;
            this.trackID = trackID;
            this.onRtspClientListener = onRtspClientListener;
            mPacket = new DatagramPacket(message, message.length);
            thread = new HandlerThread("RTCP" + trackID);
            thread.start();
            isStoped = false;
            mHandler = new Handler(thread.getLooper());
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public void sendReciverReport(byte[] sendData) {
        try {
//            Log.d(TAG, "内部RTCP客户端发送数据到升迈服务端:" + serverIp + ":" + serverPort);
            if (mSocket != null) {
                mPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(serverIp), serverPort);
                mSocket.send(mPacket);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startRtcp() {
        if (mHandler != null) {
            mHandler.post(() -> {
                while (!isStoped) {
                    try {
                        mSocket.receive(mPacket);
//                        Log.d(TAG, trackID + "内部rtcp客户端收到数据:" + mPacket.getData().length);
                        callbackReceive(mPacket.getData(), trackID, false);
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                    }
                }
            });
        }
    }

    public void close() {
        if (mSocket != null) {
            mSocket.close();
            mSocket = null;
        }
        mPacket = null;
        isStoped = true;
        thread.quit();
    }

    private void callbackReceive(byte[] data, String trackID, boolean isRtp) {
        if (onRtspClientListener != null) {
            onRtspClientListener.onUdpReceive(data, trackID, isRtp);
        }
    }

}
