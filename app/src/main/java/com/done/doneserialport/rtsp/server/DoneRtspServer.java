package com.done.doneserialport.rtsp.server;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.done.doneserialport.rtsp.server.constant.RtspMethod;
import com.done.doneserialport.rtsp.server.constant.RtspParseConstant;
import com.done.doneserialport.rtsp.server.constant.RtspServerConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * Created by Done on 2017/11/9.
 *
 * @author by Done
 */

public class DoneRtspServer extends Service {

    private static final String TAG = "DoneRtspServer";

    private final IBinder mBinder = new LocalBinder();

    private RequestListener mListenerThread;

    private Map<InetAddress, WorkerThread> clients = new ConcurrentHashMap<>();

    private Map<String, OutTrackSession> outTrackSessionMap = new ConcurrentHashMap<>();
    private Map<String, List<UdpServer>> trackUdpServers = new ConcurrentHashMap<>();
    private boolean isSetup = false;
    private String curTrackID = "";
    private static InetAddress clientAddress;
    private final Pattern REGEX_TRANSPORT = Pattern.compile("client_port=(\\d+)-\\d+;server_port=(\\d+)-\\d+", Pattern.CASE_INSENSITIVE);


    @Override
    public void onCreate() {
        start();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        stop();
        stopClinets();
        closeUdpServers();
        SmartRtspClient.getInstance().destroy();
    }

    private void stopClinets() {
        for (WorkerThread workerThread : clients.values()) {
            workerThread.interrupt();
        }
        clients.clear();
    }

    /**
     * Stops the RTSP server but not the Android Service.
     * To stop the Android Service you need to call {@link android.content.Context#stopService(Intent)};
     */
    public void stop() {
        if (mListenerThread != null) {
            try {
                mListenerThread.kill();
            } catch (Exception e) {
            } finally {
                mListenerThread = null;
            }
        }
    }

    private void start() {
        if (mListenerThread == null) {
            try {
                mListenerThread = new RequestListener();
            } catch (IOException e) {
                e.printStackTrace();
                mListenerThread = null;
            }
        }
        startConnectRTSPServer();

    }

    private void startConnectRTSPServer() {
        SmartRtspClient.getInstance().setOnRtspClientListener(new SmartRtspClient.OnRtspClientListener() {
            @Override
            public void onConnect() {
                Log.d(TAG, "已成功连接升迈RTSP服务器");
            }

            @Override
            public void onDisconnect() {
                Log.e(TAG, "和升迈的RTSP服务器断开");
            }

            @Override
            public void onTcpReceive(String data) {
                for (WorkerThread workerThread : clients.values()) {
                    data = changeServerPlayInfo(data);
                    if (isSetup) {
                        data = changeServerPort(data);
                    }
                    Log.d(TAG, "转发到外部客户端的数据:" + data);
                    workerThread.sendToRtspClient(data);
                }
            }

            @Override
            public void onUdpReceive(byte[] data, String trackID, boolean isRtp) {
                if (clients != null && clients.size() > 0) {
                    if (trackID.contains("trackID=0")) {
                        if (isRtp) {
                            if (trackUdpServers != null && trackUdpServers.size() > 0) {
                                UdpServer rtpServer = trackUdpServers.get(trackID).get(0).port % 2 == 0 ?
                                        trackUdpServers.get(trackID).get(0) : trackUdpServers.get(trackID).get(1);
                                if (rtpServer != null) {
                                    rtpServer.sendData(data, outTrackSessionMap.get(trackID).clientRtpPort);
                                }
                            }
                        } else {
                            if (trackUdpServers != null) {
                                UdpServer rtcpServer = trackUdpServers.get(trackID).get(0).port % 2 == 1 ?
                                        trackUdpServers.get(trackID).get(0) : trackUdpServers.get(trackID).get(1);
                                if (rtcpServer != null) {
                                    rtcpServer.sendData(data, outTrackSessionMap.get(trackID).clientRtcpPort);
                                }
                            }

                        }

                    } else if (trackID.contains("trackID=1")) {
                        if (isRtp) {
                            if (trackUdpServers != null) {
                                UdpServer rtpServer = trackUdpServers.get(trackID).get(0).port % 2 == 0 ?
                                        trackUdpServers.get(trackID).get(0) : trackUdpServers.get(trackID).get(1);
                                if (rtpServer != null) {
                                    rtpServer.sendData(data, outTrackSessionMap.get(trackID).clientRtpPort);
                                }
                            }

                        } else {
                            if (trackUdpServers != null) {
                                UdpServer rtcpServer = trackUdpServers.get(trackID).get(0).port % 2 == 1 ?
                                        trackUdpServers.get(trackID).get(0) : trackUdpServers.get(trackID).get(1);
                                if (rtcpServer != null) {
                                    rtcpServer.sendData(data, outTrackSessionMap.get(trackID).clientRtcpPort);
                                }
                            }

                        }
                    }
                }
            }

        });
        SmartRtspClient.getInstance().startCommunicate();
    }

    private String changeServerPlayInfo(String data) {
        String retStr = data;
        retStr = data.replace("192.168.42.1:554/live/ch01_0", "192.168.43.1:10010/");
        return retStr;
    }

    private String changeServerPort(String data) {
        String retStr = data;
        String[] datas = data.split("\r\n");
        for (String s : datas) {
            if (s.startsWith("Transport:")) {
                Matcher matcher = REGEX_TRANSPORT.matcher(s);
                if (matcher.find()) {
                    int baseClientPort = Integer.parseInt(matcher.group(1));
                    int baseServerPort = Integer.parseInt(matcher.group(2));
                    outTrackSessionMap.get(curTrackID).serverRtpPort = baseServerPort - 2;
                    outTrackSessionMap.get(curTrackID).serverRtcpPort = baseServerPort - 1;
                    retStr = data.replace(baseServerPort + "-" + (baseServerPort + 1),
                            outTrackSessionMap.get(curTrackID).serverRtpPort + "-" +
                                    outTrackSessionMap.get(curTrackID).serverRtcpPort);
                    retStr = retStr.replace(baseClientPort + "-" + (baseClientPort + 1),
                            outTrackSessionMap.get(curTrackID).clientRtpPort + "-" +
                                    outTrackSessionMap.get(curTrackID).clientRtcpPort);
                    Log.d(TAG, "发往外部修改后的服务端数据:" + retStr);
                    List<UdpServer> udpServerList = new ArrayList<>();
                    UdpServer udpRtpServer = new UdpServer(outTrackSessionMap.get(curTrackID).serverRtpPort,
                            curTrackID + "udpRtpServer", curTrackID);
                    udpRtpServer.setOutsideUdpReceive((receiveData, port) -> {
                        Log.d(TAG, udpRtpServer.getTrackID() + "外部rtp传来数据需要转发");
                        SmartRtspClient.getInstance().sendToRtpServer(udpRtpServer.getTrackID(), receiveData);
                    });
                    UdpServer udpRtcpServer = new UdpServer(outTrackSessionMap.get(curTrackID).serverRtcpPort,
                            curTrackID + "udpRtcpServer", curTrackID);
                    udpRtcpServer.setOutsideUdpReceive((receiveData, port) -> {
                        Log.d(TAG, udpRtpServer.getTrackID() + "外部rtcp传来数据需要转发");
                        SmartRtspClient.getInstance().sendToRtcpServer(udpRtpServer.getTrackID(), receiveData);
                    });
                    Log.d(TAG, "开启" + curTrackID + " udpRtpServer" + ",port:" + outTrackSessionMap.get(curTrackID).serverRtpPort
                            + "\n" + "开启" + curTrackID + " udpRtcpServer" + ",port:" + outTrackSessionMap.get(curTrackID).serverRtcpPort);
                    udpServerList.add(udpRtpServer);
                    udpServerList.add(udpRtcpServer);
                    trackUdpServers.put(curTrackID, udpServerList);
                    udpRtpServer.start();
                    udpRtcpServer.start();
                }

            }
        }
        return retStr;
    }

    class RequestListener extends Thread implements Runnable {

        private final ServerSocket mServer;

        public RequestListener() throws IOException {
            super("RequestListener");
            try {
                mServer = new ServerSocket(RtspServerConfig.RTSP_SERVER_PORT);
                start();
            } catch (BindException e) {
                Log.e(TAG, "Port already in use !");
                //postError(e, ERROR_BIND_FAILED);
                throw e;
            }
        }

        @Override
        public void run() {
            Log.i(TAG, "RTSP server listening on port " + mServer.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                    Socket socket = mServer.accept();
                    if (clients.size() > 0) {
                        socket.close();
                    } else {
                        WorkerThread workerThread = new WorkerThread(socket);
                        clientAddress = socket.getInetAddress();
                        clients.put(clientAddress, workerThread);
                        workerThread.start();
                    }
//                    new WorkerThread(mServer.accept()).start();
                } catch (SocketException e) {
                    break;
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
            closeUdpServers();
            Log.i(TAG, "RTSP server stopped !");
        }

        public void kill() {
            try {
                mServer.close();
            } catch (IOException e) {
            }
            try {
                this.join();
            } catch (InterruptedException ignore) {
            }
        }

    }


    /**
     * The Binder you obtain when a connection with the Service is established.
     */
    public class LocalBinder extends Binder {
        public DoneRtspServer getService() {
            return DoneRtspServer.this;
        }
    }

    /**
     * One thread per client
     */
    class WorkerThread extends Thread implements Runnable {

        /**
         * Parse method & uri
         */
        private final Pattern REGEX_METHOD = Pattern.compile("(\\w+) (\\S+) RTSP", Pattern.CASE_INSENSITIVE);
        /**
         * Parse a request header
         */
        private final Pattern REGEX_HEADER = Pattern.compile("(\\S+):(.+)", Pattern.CASE_INSENSITIVE);

        private final Pattern REGEX_TRANSPORT = Pattern.compile("client_port=(\\d+)-\\d", Pattern.CASE_INSENSITIVE);


        private final Socket mClient;
        private final OutputStream mOutput;
        private final BufferedReader mInput;


        public WorkerThread(final Socket client) throws IOException {
            mInput = new BufferedReader(new InputStreamReader(client.getInputStream()));
            mOutput = client.getOutputStream();
            mClient = client;
        }

        @Override
        public void run() {
            Request request = null;
            Log.i(TAG, "Connection from " + mClient.getInetAddress().getHostAddress());

            while (!Thread.interrupted()) {
                try {
                    request = parseRequest2(mInput);
                } catch (SocketException e) {
                    e.printStackTrace();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (request != null) {
//                    if (request.rtspMethod.equals(RtspMethod.TEARDOWN)) {
//                        return;
//                    }
                    if (request.rtspMethod.equals(RtspMethod.SETUP)) {
                        isSetup = true;
                        SmartRtspClient.getInstance().sendSetupToServer(request.toString(), request.uri);
                    } else {
                        isSetup = false;
                        SmartRtspClient.getInstance().sendToRtspServer(request.toString());
                    }
                }
            }


            try {
                clients.remove(mClient.getInetAddress());
                mClient.close();
                closeUdpServers();
                SmartRtspClient.getInstance().stopRtpClient();
            } catch (IOException ignore) {
            }
            Log.e(TAG, "外部rtsp断开");

        }

        public void sendToRtspClient(String data) {
            try {
                mOutput.write(data.getBytes("UTF-8"));
                mOutput.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * Parse the method, uri & headers of a RTSP request
         */
        private Request parseRequest2(BufferedReader input) throws IOException, IllegalStateException, SocketException {
            Request request = new Request();
            String line;
            Matcher matcher;
            StringBuilder content = new StringBuilder("");
            // Parsing request method & uri
            if ((line = input.readLine()) == null) {
                throw new SocketException("Client disconnected");
            }
            matcher = REGEX_METHOD.matcher(line);
            matcher.find();
            request.rtspMethod = matcher.group(1);
            request.uri = matcher.group(2);
            if (line.contains("192.168.43.1:10010/")) {
                line = line.replace("192.168.43.1:10010/", "192.168.42.1:554/live/ch01_0");
            } else if (line.contains("192.168.43.1:10010")) {
                line = line.replace("192.168.43.1:10010", "192.168.42.1:554/live/ch01_0");
            }
            content.append(line + "\r\n");

            // Parsing headers of the request
            while ((line = input.readLine()) != null && line.length() > 3) {
                content.append(line + "\r\n");
                matcher = REGEX_HEADER.matcher(line);
                matcher.find();
                request.headers.put(matcher.group(1).toLowerCase(Locale.US), matcher.group(2));
            }
            if (line == null) {
                throw new SocketException("Client disconnected");
            }

            // It's not an error, it's just easier to follow what's happening in logcat with the request in red
            Log.e(TAG, request.rtspMethod + " " + request.uri);
            request.header = content.toString() + "\r\n";
            if (request.rtspMethod.equals(RtspMethod.SETUP)) {
                String transportStr = request.headers.get("transport");
                matcher = REGEX_TRANSPORT.matcher(request.headers.get("transport"));
                if (matcher.find()) {
                    curTrackID = request.uri;
                    OutTrackSession outTrackSession = new OutTrackSession();
                    int baseClientPort = Integer.parseInt(matcher.group(1));
                    outTrackSession.clientRtpPort = baseClientPort;
                    outTrackSession.clientRtcpPort = baseClientPort + 1;
                    Log.d(TAG, "外部客户端进来指定的客户端口:" + transportStr + ",内部偏移端口" + (baseClientPort - 2));
                    outTrackSessionMap.put(request.uri, outTrackSession);
                    request.header = request.header.replace(baseClientPort + "-" + (baseClientPort + 1),
                            (baseClientPort - 2) + "-" + (baseClientPort - 1));
                    Log.d(TAG, "修改后的请求报文:" + request.header);
                }
            }
            return request;
        }

        private Request parseRequest(BufferedReader mInput) throws IOException {
            Request request = new Request();
            String line;
            boolean hasMethod = false;
            boolean hasEnd = false;
            int bodyCount = -1;
            StringBuilder stringBuilder = new StringBuilder("");
            StringBuffer body = new StringBuffer("");
            while ((line = mInput.readLine()) != null) {
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
                        request.header = stringBuilder.toString();
                        return request;
                    } else if (bodyCount > 0) {
                        if (body.toString().getBytes().length == bodyCount) {
                            request.body = body.toString();
                            return request;
                        }
                    }
                }
            }
            if (line == null) {
                throw new SocketException("read line is null");
            }
            return request;
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

        /**
         * 计算请求包体的长度
         *
         * @param line
         * @return
         */
        private int calcBodyLength(String line) {
            if (line.startsWith("Content-Length:")) {
                line = line.replace(" ", "");
                return Integer.parseInt(line.substring(line.indexOf(":") + 1));
            }
            return -1;
        }

    }

    private void closeUdpServers() {
        if (null != trackUdpServers && trackUdpServers.size() > 0) {
            for (List<UdpServer> udpServerList : trackUdpServers.values()) {
                for (UdpServer udpServer : udpServerList) {
                    udpServer.close();
                }
            }
        }
        trackUdpServers.clear();
    }

    static class Request {
        public String header = "";
        public String body = "";

        public String rtspMethod = "";
        public String uri = "";
        public HashMap<String, String> headers = new HashMap<String, String>();


        private static final String CRLF = "\r\n";

        @Override
        public String toString() {
            if (!TextUtils.isEmpty(body)) {
                return header + CRLF + body;
            } else {
                return header;
            }
        }
    }

    class OutTrackSession {
        public int clientRtpPort = -1;
        public int clientRtcpPort = -1;
        public int serverRtpPort = -1;
        public int serverRtcpPort = -1;
    }

    private static class UdpServer extends Thread implements Runnable {
        private int port;
        private DatagramSocket socket;
        private boolean isStop = false;
        private byte[] message = new byte[1024 * 10];
        private DatagramPacket packet;
        private OutsideUdpReceive outsideUdpReceive;
        private String trackID;

        public UdpServer(int port, String name, String trackID) {
            super(name);
            this.port = port;
            this.trackID = trackID;
            initUdpSocket();
        }

        public String getTrackID() {
            return trackID;
        }

        public void setOutsideUdpReceive(OutsideUdpReceive outsideUdpReceive) {
            this.outsideUdpReceive = outsideUdpReceive;
        }

        public void callbackUdpReceive(byte[] data, int port) {
            if (outsideUdpReceive != null) {
                outsideUdpReceive.onReceive(data, port);
            }
        }

        @Override
        public void run() {
            while (!isStop) {
                try {
                    socket.receive(packet);
                    callbackUdpReceive(packet.getData(), packet.getPort());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void initUdpSocket() {
            try {
                socket = new DatagramSocket(port);
                packet = new DatagramPacket(message, message.length);
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        public void sendData(byte[] data, int destPort) {
            if (socket == null) {
                return;
            }
            new Thread(() -> {
                try {
                    DatagramPacket packet = new DatagramPacket(data, data.length, clientAddress, destPort);
                    socket.send(packet);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        public void close() {
            isStop = true;
            if (socket != null) {
                socket.close();
                socket = null;
            }
        }

        public interface OutsideUdpReceive {
            void onReceive(byte[] receiveData, int port);
        }
    }

}
