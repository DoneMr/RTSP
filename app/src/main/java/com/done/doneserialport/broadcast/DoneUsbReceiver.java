package com.done.doneserialport.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.util.Log;
import android.widget.Toast;

import com.done.doneserialport.rtsp.RtspServerActivity;
import com.done.doneserialport.util.WifiApManager;

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
 * Created by Done on 2017/11/14.
 *
 * @author by Done
 */

public class DoneUsbReceiver extends BroadcastReceiver {

    private static final String TAG = "DoneUsbReceiver";

    private static final String USB_HIDE_ACTION = "android.hardware.usb.action.USB_STATE";

    public static final String USB_CONNECTED = "connected";
    public static final String USB_HOST_CONNECTED = "host_connected";
    public static final String USB_CONFIGURED = "configured";
    public static final String USB_DATA_UNLOCKED = "unlocked";
    public static final String USB_FUNCTION_NONE = "none";
    public static final String USB_FUNCTION_ADB = "adb";
    public static final String USB_FUNCTION_RNDIS = "rndis";
    public static final String USB_FUNCTION_MTP = "mtp";
    public static final String USB_FUNCTION_PTP = "ptp";
    public static final String USB_FUNCTION_AUDIO_SOURCE = "audio_source";
    public static final String USB_FUNCTION_MIDI = "midi";
    public static final String USB_FUNCTION_ACCESSORY = "accessory";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_PORT_STATUS = "portStatus";
    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_ACCESSORY = "accessory";
    public static final String EXTRA_PERMISSION_GRANTED = "permission";

    private String showStr = "";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        ConnectivityManager connectivityManager;
        showStr = "NULL " + action;
        if (USB_HIDE_ACTION.equals(action)) {
            showStr = "usb state is change " + action;
            boolean connected = intent.getBooleanExtra(USB_CONNECTED, false);
            addText("connected : " + connected);
            boolean configured = intent.getBooleanExtra(USB_CONFIGURED, false);
            addText("configured : " + configured);
            boolean function_adb = intent.getBooleanExtra(USB_FUNCTION_ADB, false);
            addText("function_adb : " + function_adb);
            boolean function_rndis = intent.getBooleanExtra(USB_FUNCTION_RNDIS, false);
            addText("function_rndis : " + function_rndis);
            boolean function_mtp = intent.getBooleanExtra(USB_FUNCTION_MTP, false);
            addText("function_mtp : " + function_mtp);
            boolean function_ptp = intent.getBooleanExtra(USB_FUNCTION_PTP, false);
            addText("usb_function_ptp : " + function_ptp);
            boolean function_audio_source = intent.getBooleanExtra(USB_FUNCTION_AUDIO_SOURCE, false);
            addText("function_audio_source : " + function_audio_source);
            boolean function_midi = intent.getBooleanExtra(USB_FUNCTION_MIDI, false);
            addText("function_midi : " + function_midi);
            Log.d(TAG, showStr);
        }
//        Toast.makeText(context.getApplicationContext(), showStr, Toast.LENGTH_SHORT).show();
    }

    private void addText(String s) {
        showStr += ("," + s);
    }
}
