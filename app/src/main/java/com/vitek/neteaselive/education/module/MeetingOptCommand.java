package com.vitek.neteaselive.education.module;

/**
 * Created by hzxuwen on 2016/4/25.
 */
public enum MeetingOptCommand {
    /**
     * 未知
     */
    NONE(-1),
    /**
     * 主持人通知有权限的成员列表
     */
    ALL_STATUS(1),
    /**
     * 成员向所有人请求有权限的成员
     */
    GET_STATUS(2),
    /**
     * 有权限的成员向请求者返回自己有权限的通知
     */
    STATUS_RESPONSE(3),
    /**
     * 向主持人请求连麦权限
     */
    SPEAK_REQUEST(10),
    /**
     * 主持人同意连麦请求
     */
    SPEAK_ACCEPT(11),
    /**
     * 主持人拒绝或关闭连麦
     */
    SPEAK_REJECT(12),
    /**
     * 取消向主持人请求连麦权限
     */
    SPEAK_REQUEST_CANCEL(13);

    private int value;

    MeetingOptCommand(int value){
        this.value = value;
    }

    public static MeetingOptCommand statusOfValue(int status) {
        for (MeetingOptCommand e : values()) {
            if (e.getValue() == status) {
                return e;
            }
        }
        return NONE;
    }

    public int getValue(){
        return value;
    }
}
