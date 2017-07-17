package com.vitek.neteaselive.education.module;

/**
 * Created by hzxuwen on 2016/12/12.
 */

public enum FullScreenType {
    /**
     * 全屏模式关闭
     */
    CLOSE(0),
    /**
     * 全屏模式开启
     */
    OPEN(1);

    private int value;

    FullScreenType(int value){
        this.value = value;
    }

    public static FullScreenType statusOfValue(int status) {
        for (FullScreenType e : values()) {
            if (e.getValue() == status) {
                return e;
            }
        }
        return CLOSE;
    }

    public int getValue(){
        return value;
    }
}
