package com.done.doneserialport.rtsp.server;

import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.done.doneserialport.rtsp.server.rtp.DoneRtpSocket;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Created by Done on 2017/11/8.
 *
 * @author by Done
 */

public class SmartRtspClient {

    private static final String TAG = "SmartRtspClient";

    private volatile static SmartRtspClient instance = null;

    private Socket socket;

    public static final String DEFAULT_RTSP_SERVER_IP = "192.168.42.1";//"192.168.43.119";
    public static final int DEFAULT_RTSP_SERVER_PORT = 554;//554;
    public static final int DEFAULT_RTSP_SERVER_TIMEOUT = 10 * 1000;

    private BufferedReader in;
    private DataOutputStream out;
    private DataInputStream inputStream;
    private OnRtspClientListener onRtspClientListener;

    private ThreadPoolExecutor pool = null;
    private BlockingQueue<Runnable> blockingQueue = null;
    private ClientThreadFactory threadFactory = null;
    private Sender sender = null;
    private Connector connector = null;
    private Receiver receiver = null;

    /**
     * 客户端服务端协商通信
     */
    private boolean isSetup = false;
    private String curTrackID = "";
    private Map<String, TrackSession> trackSessionMap = new ConcurrentHashMap<>();
    private Map<String, DoneRtpSocket> doneRtpSocketMap = new ConcurrentHashMap<>();

    public static SmartRtspClient getInstance() {
        if (null == instance) {
            synchronized (SmartRtspClient.class) {
                if (null == instance) {
                    instance = new SmartRtspClient();
                }
            }
        }
        return instance;
    }

    public void setOnRtspClientListener(OnRtspClientListener onRtspClientListener) {
        this.onRtspClientListener = onRtspClientListener;
    }

    public void startCommunicate() {
        if (onRtspClientListener == null) {
            return;
        }
        blockingQueue = new LinkedBlockingQueue<>(5);
        threadFactory = new ClientThreadFactory();
        pool = new ThreadPoolExecutor(7, 7, 0L, TimeUnit.MILLISECONDS, blockingQueue, threadFactory);
        connector = new Connector();
        sender = new Sender();
        receiver = new Receiver();
        pool.execute(connector);
    }

    public boolean sendSetupToServer(String data, String trackID) {
        isSetup = true;
        curTrackID = trackID;
        return sendToRtspServer(data);
    }

    public boolean sendToRtspServer(String data) {
        boolean bRet = false;
        if (isConnect()) {
            bRet = true;
        }
        if (bRet) {
            if (sender == null) {
                sender = new Sender();
            }
            sender.setSendData(data);
            pool.execute(sender);
        }
        return bRet;
    }

    public void sendToRtpServer(String trackID, byte[] data) {
        if (doneRtpSocketMap != null && doneRtpSocketMap.size() > 0) {
            DoneRtpSocket doneRtpSocket = doneRtpSocketMap.get(trackID);
            if (doneRtpSocket != null) {
//                pool.execute(() -> doneRtpSocket.sendData(data));
                new Thread(() -> doneRtpSocket.sendData(data)).start();
            }
        }
    }

    public void sendToRtcpServer(String trackID, byte[] data) {
        if (doneRtpSocketMap != null && doneRtpSocketMap.size() > 0) {
            DoneRtpSocket doneRtpSocket = doneRtpSocketMap.get(trackID);
            if (doneRtpSocket != null) {
//                pool.execute(() -> doneRtpSocket.sendRtcpData(data));
                new Thread(() -> doneRtpSocket.sendRtcpData(data)).start();
            }
        }
    }

    public boolean sendToRtspServer(byte[] datas) {
        boolean bRet = false;
        if (isConnect()) {
            bRet = true;
        }
        if (bRet) {
            if (sender == null) {
                sender = new Sender();
            }
            sender.setSendData(datas);
            pool.execute(sender);
        }
        return bRet;
    }

    public void destroy() {
        if (pool != null) {
            pool.shutdown();
        }
        if (in != null) {
            try {
                in.close();
                in = null;
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
        if (blockingQueue != null) {
            blockingQueue.clear();
            blockingQueue = null;
        }
        if (trackSessionMap != null) {
            trackSessionMap.clear();
        }
        stopRtpClient();
        threadFactory = null;
        connector = null;
        sender = null;
        receiver = null;
        callbackOnDisconnect();
    }

    public void stopRtpClient() {
        if (doneRtpSocketMap != null) {
            for (DoneRtpSocket doneRtpSocket : doneRtpSocketMap.values()) {
                doneRtpSocket.stopClient();
            }
            doneRtpSocketMap.clear();
        }
    }

    public boolean isConnect() {
        boolean bRet = false;
        if (socket != null) {
            bRet = socket.isConnected();
        }
        return bRet;
    }

    private void callbackOnConnect() {
        if (onRtspClientListener != null) {
            onRtspClientListener.onConnect();
        }
    }

    private void callbackOnTcpReceive(String data) {
        if (onRtspClientListener != null) {
            Log.d(TAG, "升迈服务端的原始数据:" + data);
            onRtspClientListener.onTcpReceive(data);
        }
    }

    private void callbackOnDisconnect() {
        if (onRtspClientListener != null) {
            onRtspClientListener.onDisconnect();
        }
    }


    private class Connector implements Runnable {

        @Override
        public void run() {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(DEFAULT_RTSP_SERVER_IP, DEFAULT_RTSP_SERVER_PORT), DEFAULT_RTSP_SERVER_TIMEOUT);
                if (socket.isConnected()) {
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out = new DataOutputStream(socket.getOutputStream());
                    inputStream = new DataInputStream(socket.getInputStream());
                    pool.execute(receiver);
                }
                callbackOnConnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private class Receiver implements Runnable {

        private final Pattern REGEX_STATUS = Pattern.compile("RTSP/\\d.\\d (\\d+) .+", Pattern.CASE_INSENSITIVE);

        private final Pattern REGEX_HEADER = Pattern.compile("(\\S+): (.+)", Pattern.CASE_INSENSITIVE);

        private final Pattern REGEX_TRANSPORT = Pattern.compile("client_port=(\\d+)-\\d+;server_port=(\\d+)-\\d+", Pattern.CASE_INSENSITIVE);


        int readLen = -1;
        byte[] data = new byte[1024];
        String receiveData = "";

        @Override
        public void run() {
            if (in != null) {
                try {
//                    Response response = parseResponse();
//                    if (response != null) {
//                        callbackOnTcpReceive(response.toString());
//                    }

                    while ((readLen = inputStream.read(data)) != -1) {
                        byte[] copyData = new byte[readLen];
                        System.arraycopy(data, 0, copyData, 0, readLen);
                        receiveData = new String(copyData);
//                        receiveData = receiveData.replace("192.168.42.1:554", "192.168.43.1:10010");
                        if (isSetup) {
                            parseSetupResponse(receiveData);
                        }
                        callbackOnTcpReceive(receiveData);
                    }
                } catch (SocketException e1) {
                    e1.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * 解析rtsp server对SETUP方法响应内容
         *
         * @param receiveData
         */
        private void parseSetupResponse(String receiveData) {
            String[] strings = receiveData.split("\r\n");
            String transportStr = "";
            String retStr = "";
            for (String string : strings) {
                if (string.startsWith("Transport: ")) {
                    transportStr = string;
                    break;
                }
            }
            if (!TextUtils.isEmpty(transportStr)) {
                Matcher matcher = REGEX_TRANSPORT.matcher(receiveData);
                if (matcher.find()) {
                    TrackSession trackSession = new TrackSession();
                    int baseClientPort = Integer.parseInt(matcher.group(1));
                    int baseServerPort = Integer.parseInt(matcher.group(2));
                    trackSession.clientRtpPort = baseClientPort;
                    trackSession.clientRtcpPort = baseClientPort + 1;
                    trackSession.serverRtpPort = baseServerPort;
                    trackSession.serverRtcpPort = baseServerPort + 1;
                    Log.d(TAG, "curTrackID:" + curTrackID + ",baseClientPort:" + baseClientPort + ",baseServerPort:" + baseServerPort);
                    trackSessionMap.put(curTrackID, trackSession);
                    isSetup = false;
                    DoneRtpSocket doneRtpSocket = new DoneRtpSocket(curTrackID, trackSession, onRtspClientListener);
                    doneRtpSocketMap.put(curTrackID, doneRtpSocket);
                    doneRtpSocket.startClient();
                }
            }
        }

        private Response parseResponse() throws IOException {
            String line;
            boolean hasEnd = false;
            int bodyCount = -1;
            StringBuilder stringBuilder = new StringBuilder("");
            StringBuilder body = new StringBuilder("");
            Matcher matcher;
            Response response = new Response();

            if ((line = in.readLine()) == null) {
                throw new SocketException("socket is lost");
            }
            matcher = REGEX_STATUS.matcher(line);
            if (matcher.find()) {
                response.state = Integer.parseInt(matcher.group(1));
                stringBuilder.append(line + "\r\n");
            } else {
                while ((line = in.readLine()) != null) {
                    matcher = REGEX_STATUS.matcher(line);
                    if (matcher.find()) {
                        response.state = Integer.parseInt(matcher.group(1));
                        stringBuilder.append(line + "\r\n");
                        break;
                    }
                }
            }

            while ((line = in.readLine()) != null) {
                stringBuilder.append(line + "\r\n");
                matcher = REGEX_HEADER.matcher(line);
                if (matcher.find()) {
                    response.headers.put(matcher.group(1).toLowerCase(Locale.US), matcher.group(2)); //$ to $
                }
                if (response.state == 200 && bodyCount < 0) {
//                    line = line.replace("192.168.42.1:554/live/ch01_0/", "192.168.43.1:10010/");
                    bodyCount = calcBodyLength(line);
                }
                if (!hasEnd) {
                    hasEnd = stringBuilder.toString().endsWith("\r\n\r\n");
                }
                if (hasEnd && bodyCount > 0 && !TextUtils.isEmpty(line)) {
                    body.append(line + "\r\n");
                }
                if (hasEnd) {
                    if (bodyCount < 0) {
                        response.header = stringBuilder.toString();
                        return response;
                    } else if (bodyCount > 0) {

                        if (body.toString().getBytes().length == bodyCount) {
                            response.header = stringBuilder.toString() + body.toString();
                            return response;
                        }
                    }
                }
            }
            throw new SocketException("socket is lost");
        }

        private int calcBodyLength(String line) {
            if (line.startsWith("Content-Length:")) {
                return Integer.parseInt(line.substring(line.indexOf(":") + 2));
            }
            return -1;
        }
    }

    private class Sender implements Runnable {

        private String sendData = "";

        private byte[] sendDatas = null;

        public void setSendData(String sendData) {
            this.sendData = sendData;
            this.sendDatas = null;
        }

        public void setSendData(byte[] sendDatas) {
            this.sendDatas = sendDatas;
            this.sendData = "";
        }

        @Override
        public void run() {
            send();
        }

        private void send() {
            try {
                if (!TextUtils.isEmpty(sendData)) {
                    Log.d(TAG, "send data to rtsp server:" + sendData);
                    out.write(sendData.getBytes("UTF-8"));
                    out.flush();
                    sendData = "";
                } else if (sendDatas != null) {
                    out.write(sendDatas);
                    out.flush();
                    sendData = "";
                }

            } catch (IOException e) {
                e.printStackTrace();
                try {
                    if (out != null) {
                        out.close();
                        out = null;
                    }
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    private class ClientThreadFactory implements ThreadFactory {

        int count = 0;

        String rootName = "ClientThread-";

        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread thread = new Thread(r, rootName + String.valueOf(++count));
            if (thread.isDaemon()) {
                thread.setDaemon(false);
            }
            return thread;
        }
    }

    class Response {
        private static final String CRLF = "\r\n";
        public int state;
        public String header;
        public String body;
        public HashMap<String, String> headers = new HashMap<>();

        @Override
        public String toString() {
            if (!TextUtils.isEmpty(body)) {
                return header + CRLF + body;
            } else {
                return header;
            }
        }
    }

    public class TrackSession {
        public int clientRtpPort = -1; //内部客户端口
        public int clientRtcpPort = -1; //内部客户端口
        public int serverRtpPort = -1;
        public int serverRtcpPort = -1;
    }

    public interface OnRtspClientListener {
        void onConnect();

        void onDisconnect();

        void onTcpReceive(String data);

        /**
         * local rtp/rtcp client receive data
         *
         * @param data    video or audio data
         * @param trackID rtsp communicate trackID
         * @param isRtp   if true is rtp port
         */
        void onUdpReceive(byte[] data, String trackID, boolean isRtp);
    }
}
