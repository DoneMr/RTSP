package com.done.doneserialport.rtsp.server;

import android.util.Log;

import com.done.doneserialport.rtsp.RtspServerActivity;
import com.done.doneserialport.rtsp.server.thread.ClientThread;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

public class ClientManager {

    private static Map<String, ClientThread> clientList = new ConcurrentHashMap<>();
    private static ServerThread serverThread = null;
    private OnServerListener onServerListener = null;
    private static final String TAG = "ClientManager";
    private volatile static ClientManager instance = null;
    private int port = 10010;
    private String localAddress = "NULL";

    public static ClientManager getInstance() {
        if (null == instance) {
            synchronized (ClientManager.class) {
                if (null == instance) {
                    instance = new ClientManager();
                }
            }
        }
        return instance;
    }

    public void startServer(String serverName, OnServerListener onServerListener) {
        Log.d(TAG, "START SERVER");
        if (serverThread != null) {
            showDown();
        }
        serverThread = new ServerThread(port);
        new Thread(serverThread, serverName).start();
        Log.d(TAG, "START SERVER SUCCESS");
        this.onServerListener = onServerListener;
        try {
            localAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        callbackStart(localAddress, port);
    }

    private void callbackStart(String hostIP, int hostPORT) {
        if (onServerListener != null) {
            onServerListener.onStart(hostIP, hostPORT);
        }
    }

    private void callbackStop(String hostIP, int hostPORT) {
        if (onServerListener != null) {
            onServerListener.onStop(hostIP, hostPORT);
        }
    }

    private void callbackReceive(String ip, String data) {
        if (onServerListener != null) {
            onServerListener.onReceive(ip, data);
        }
    }

    // 关闭所有server socket 和 清空Map
    public void showDown() {
        for (ClientThread socket : clientList.values()) {
            try {
                socket.closeClient();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        serverThread.stop();
        clientList.clear();
        SmartRtspClient.getInstance().destroy();
        callbackStop(localAddress, port);
    }

    // 群发的方法
    public boolean sendMsgAll(String msg) {
        try {
            for (ClientThread socket : clientList.values()) {
                socket.sendToRtspClient(msg);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public interface OnServerListener {
        void onReceive(String ip, String data);

        void onStart(String hostIP, int hostPORT);

        void onStop(String hostIP, int hostPORT);
    }

    private class ServerThread implements Runnable {

        private boolean isExit = false;
        private ServerSocket server;

        public ServerThread(int port) {
            try {
                server = new ServerSocket(port);
                RtspServerActivity.refreshInfo("启动服务成功" + "port:" + port, 0);
                connectRtspServer();
            } catch (IOException e) {
                System.out.println();
                RtspServerActivity.refreshInfo("启动server失败，错误原因：" + e.getMessage(), 2);
            }
        }

        private void connectRtspServer() {
            SmartRtspClient.getInstance().setOnRtspClientListener(new SmartRtspClient.OnRtspClientListener() {
                @Override
                public void onConnect() {
                    RtspServerActivity.refreshInfo("内部连接rtsp服务端成功", 0);
                }

                @Override
                public void onDisconnect() {
                    RtspServerActivity.refreshInfo("内部客户端和rtsp服务端的连接断开了", 2);
                }

                @Override
                public void onTcpReceive(String data) {
                    Log.d(TAG, "内部客户端收到的rtsp服务端Tcp通道数据:" + data);
                    RtspServerActivity.refreshInfo("内部客户端收到的rtsp服务端Tcp通道数据:" + data, 1);
                    sendMsgAll(data);
                }

                @Override
                public void onUdpReceive(byte[] data, String trackID, boolean isRtp) {

                }
            });
            SmartRtspClient.getInstance().startCommunicate();
        }

        @Override
        public void run() {
            try {
                while (!isExit) {
                    // 进入等待环节
                    RtspServerActivity.refreshInfo("等待手机的连接... ...", 0);

                    final Socket socket = server.accept();
                    // 获取手机连接的地址及端口号
                    final String address = socket.getRemoteSocketAddress().toString();

                    ClientThread clientThread = new ClientThread(socket, address);
                    clientThread.setOnClientStatus(new ClientThread.OnClientStatus() {
                        @Override
                        public void onConnect() {

                        }

                        @Override
                        public void onDisconnect() {

                        }

                        @Override
                        public void onReceive(String ip, String data) {
                        }
                    });
                    clientThread.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        private void stop() {
            isExit = true;
            if (server != null) {
                try {
                    server.close();
                    System.out.println("已关闭server");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

//    private class ClientThread implements Runnable {
//
//        private Socket client = null;
//        private OutputStream out = null;
//        private BufferedReader reader = null;
//        private String line = null;
//        private InputStream in = null;
//
//
//        public ClientThread(Socket client) {
//            if (client != null && client.isConnected()) {
//                this.client = client;
//            }
//        }
//
//        public void sendData(String data) {
//            new Thread(() -> {
//                try {
//                    if (out == null) {
//                        out = client.getOutputStream();
//                    }
//                    Log.d(TAG, "发往外部客户端的数据:" + data);
//                    out.write(data.getBytes("UTF-8"));
//                    out.flush();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }).start();
//        }
//
//        public void close() throws Exception {
//            if (client != null) {
//                client.close();
//                client = null;
//            }
//            if (reader != null) {
//                reader.close();
//                reader = null;
//            }
//            if (out != null) {
//                out.close();
//                out = null;
//            }
//            if (in != null) {
//                in.close();
//                in = null;
//            }
//        }
//
//
//        @Override
//        public void run() {
//            if (client != null) {
//                try {
//                    reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
//                    in = client.getInputStream();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            if (reader != null) {
//                while (!Thread.currentThread().isInterrupted()) {
//                    try {
//                        parseRequest(reader);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                        try {
//                            close();
//                        } catch (Exception e1) {
//                            e1.printStackTrace();
//                        }
//                        clientList.remove(this);
//                        break;
//                    }
//                }
//            }
//        }
//
//        private void parseRequest(BufferedReader reader) throws Exception {
//            boolean hasMethod = false;
//            boolean hasEnd = false;
//            int bodyCount = -1;
//            StringBuilder stringBuilder = new StringBuilder("");
//            StringBuffer body = new StringBuffer("");
//            while ((line = reader.readLine()) != null) {
//                if (!hasMethod) {
//                    hasMethod = isNewPackage(line);
//                }
//                if (hasMethod && bodyCount < 0) {
//                    line = line.replace("192.168.43.1:10010", "192.168.42.1:554/live/ch01_0");
//                    bodyCount = calcBodyLength(line);
//                }
//                stringBuilder.append(line + "\r\n");
//                if (bodyCount > 0) {
//                    body.append(line + "\r\n");
//                }
//                if (hasMethod) {
//                    hasEnd = stringBuilder.toString().endsWith("\r\n\r\n");
//                }
//                if (hasMethod && hasEnd) {
//                    if (bodyCount < 0) {
//                        SmartRtspClient.getInstance().sendToRtspServer(stringBuilder.toString());
//                        callbackReceive(client.getInetAddress().getHostAddress(), stringBuilder.toString());
//                        stringBuilder.setLength(0);
//                        break;
//                    } else if (bodyCount > 0) {
//                        if (body.toString().getBytes().length == bodyCount) {
//                            callbackReceive(client.getInetAddress().getHostAddress(), stringBuilder.toString());
//                            body.setLength(0);
//                            break;
//                        }
//                    }
//                }
//            }
//            if (in.read() == -1) {
//                throw new SocketException("read line is null");
//            }
//        }
//
//        private int calcBodyLength(String line) {
//            if (line.startsWith("Content-Length:")) {
//                line = line.replace(" ", "");
//                return Integer.parseInt(line.substring(line.indexOf(":") + 1));
//            }
//            return -1;
//        }
//
//        private boolean isBody(String line) {
//            if (line.startsWith("Content-Length:")) {
//                return true;
//            }
//            return false;
//        }
//
//        /**
//         * 这包数据是否完整
//         *
//         * @param tmpPackage
//         * @return
//         */
//        private boolean isComplete(String tmpPackage) {
//            boolean bRet = false;
//            if (tmpPackage.endsWith("\r\n\r\n")) {
//                if (tmpPackage.contains("Content-length:")) {
//
//                }
//            }
//            for (String method : RtspMethod.METHODS) {
//                if (method.contains(tmpPackage)) {
//                    return true;
//                }
//            }
//            return bRet;
//        }
//
//        /**
//         * 是否是一包新数据
//         *
//         * @param tmpPackage
//         * @return
//         */
//        private boolean isNewPackage(String tmpPackage) {
//            boolean bRet = false;
//            if (TextUtils.isEmpty(tmpPackage)) {
//                return false;
//            }
//            for (String method : RtspMethod.METHODS) {
//                if (tmpPackage.startsWith(method)) {
//                    return true;
//                }
//            }
//            return bRet;
//        }
//
//    }


}
