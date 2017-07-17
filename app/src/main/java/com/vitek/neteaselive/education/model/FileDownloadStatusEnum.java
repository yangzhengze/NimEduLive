package com.vitek.neteaselive.education.model;

/**
 * Created by winnie on 2016/12/25.
 */

public enum  FileDownloadStatusEnum {
    /**
     * 未下载
     */
    NotDownload(1),
    /**
     * 下载中
     */
    Downloading(2),
    /**
     * 已下载
     */
    DownLoaded(3),

    /**
     * 重试
     */
    Retry(4);

    private int value;

    FileDownloadStatusEnum(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static FileDownloadStatusEnum typeOfValue(int value) {
        for (FileDownloadStatusEnum e : values()) {
            if (e.getValue() == value) {
                return e;
            }
        }
        return NotDownload;
    }
}
