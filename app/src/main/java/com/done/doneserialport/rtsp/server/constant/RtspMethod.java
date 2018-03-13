package com.done.doneserialport.rtsp.server.constant;

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

public class RtspMethod {
    public static final String DESCRIBE = "DESCRIBE";
    public static final String ANNOUNCE = "ANNOUNCE";
    public static final String GET_PARAMETER = "GET_PARAMETER";
    public static final String OPTIONS = "OPTIONS";
    public static final String PAUSE = "PAUSE";
    public static final String PLAY = "PLAY";
    public static final String RECORD = "RECORD";
    public static final String REDIRECT = "REDIRECT";
    public static final String SETUP = "SETUP";
    public static final String SET_PARAMETER = "SET_PARAMETER";
    public static final String TEARDOWN = "TEARDOWN";

    public static final String[] METHODS = new String[]{
            DESCRIBE, ANNOUNCE, GET_PARAMETER, OPTIONS, PAUSE, PLAY, RECORD, REDIRECT, SETUP, SET_PARAMETER, TEARDOWN
    };
}
