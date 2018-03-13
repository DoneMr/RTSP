package com.done.doneserialport.rtsp.server.rtp;

import android.util.Log;

import com.done.doneserialport.rtsp.server.SmartRtspClient;
import com.done.doneserialport.rtsp.server.constant.RtspServerConfig;
import com.done.doneserialport.rtsp.server.model.RtpModel;
import com.done.doneserialport.rtsp.server.rtcp.DoneRtcpSocket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
 * Created by Done on 2017/11/10.
 *
 * @author by Done
 */

public class DoneRtpSocket implements Runnable {

    private static final String TAG = "DoneRtpSocket";

    private DatagramSocket mUdpSocket;
    private DatagramPacket mUdpPackets;
    private DoneRtcpSocket mDoneRtcpSocket;
    private Thread receiveThread;

    private String trackID;
    private int clinetPort = -1;
    private int serverPort = -1;

    private byte[] message = new byte[1024 * 10];

    private boolean isStop = false;

    private SmartRtspClient.OnRtspClientListener onRtspClientListener;
    private SmartRtspClient.TrackSession trackSession;

    public DoneRtpSocket(String curTrackID, SmartRtspClient.TrackSession trackSession, SmartRtspClient.OnRtspClientListener onRtspClientListener) {
        Log.d(TAG, "create a rtp client for " + curTrackID);
        this.trackID = curTrackID;
        this.clinetPort = trackSession.clientRtpPort;
        this.serverPort = trackSession.serverRtcpPort;
        this.trackSession = trackSession;
        this.onRtspClientListener = onRtspClientListener;
        try {
            setupUdpSocket(trackSession);
        } catch (SocketException e) {
            e.printStackTrace();
            Log.e(TAG, "create rtp/udp socket is failed!");
        }
    }

    private void setupUdpSocket(SmartRtspClient.TrackSession trackSession) throws SocketException {
        mUdpSocket = new DatagramSocket(clinetPort);
        mUdpPackets = new DatagramPacket(message, message.length);
        mDoneRtcpSocket = new DoneRtcpSocket(trackSession.clientRtcpPort, RtspServerConfig.RTSP_SERVER_IP, trackSession.serverRtcpPort, trackID, onRtspClientListener);
        mDoneRtcpSocket.startRtcp();
    }

    public void startClient() {
        receiveThread = new Thread(this, trackID + "-RTP");
        isStop = false;
        receiveThread.start();
    }

    public void stopClient() {
        isStop = true;
        if (mUdpSocket != null) {
            mUdpSocket.close();
            mUdpSocket = null;
        }
        if (mDoneRtcpSocket != null) {
            mDoneRtcpSocket.close();
        }
    }

    public void sendData(byte[] data) {
        try {
            DatagramPacket packet;
            packet = new DatagramPacket(data, data.length, InetAddress.getByName(RtspServerConfig.RTSP_SERVER_IP),
                    trackSession.serverRtpPort);
            mUdpSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendRtcpData(byte[] data) {
        mDoneRtcpSocket.sendReciverReport(data);
    }

    @Override
    public void run() {
        try {
            startUdpReceiving();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startUdpReceiving() throws IOException {
        byte[] buffer;
        while (!isStop) {
            mUdpSocket.receive(mUdpPackets);
            buffer = new byte[mUdpPackets.getLength()];
            System.arraycopy(mUdpPackets.getData(), 0, buffer, 0, mUdpPackets.getLength());
            RtpModel rtpModel = null;
            try {
                rtpModel = parseRtpPacket(buffer);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (rtpModel != null) {
                callbackReceive(buffer, trackID, true);
            }
        }
    }

    private RtpModel parseRtpPacket(byte[] buffer) throws Exception {
        RtpModel rtpModel = new RtpModel();
        rtpModel.V = (buffer[0] & 0xFF) >> 6;
        rtpModel.P = buffer[0] & 0x20;
        rtpModel.X = buffer[0] & 0x10;
        rtpModel.CC = buffer[0] & 0x0F;
        rtpModel.M = buffer[1] & 0x80;
        rtpModel.PT = buffer[1] & 0x7F;
        rtpModel.S_NUMBER = String.valueOf(((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF));
        rtpModel.TIMESTAMP = String.valueOf((buffer[4] & 0xFF << 24) | (buffer[5] & 0xFF << 16) | (buffer[6] & 0xFF << 8) | (buffer[7] & 0xFF));
        rtpModel.SSRC = String.valueOf((buffer[8] & 0xFF << 24) | (buffer[9] & 0xFF << 16) | (buffer[10] & 0xFF << 8) | (buffer[11] & 0xFF));
//        Log.d(TAG, "rtpModel.V:" + rtpModel.V +
//                ",rtpModel.P:" + rtpModel.P +
//                ",rtpModel.X:" + rtpModel.X +
//                ",rtpModel.CC:" + rtpModel.CC +
//                ",rtpModel.M:" + rtpModel.M +
//                ",rtpModel.PT:" + rtpModel.PT +
//                ",rtpModel.S_NUMBER:" + rtpModel.S_NUMBER +
//                ",rtpModel.TIMESTAMP:" + rtpModel.TIMESTAMP +
//                ",rtpModel.SSRC:" + rtpModel.SSRC);
        return rtpModel;
    }

    private void callbackReceive(byte[] data, String trackID, boolean isRtp) {
        if (onRtspClientListener != null) {
            onRtspClientListener.onUdpReceive(data, trackID, isRtp);
        }
    }
}
