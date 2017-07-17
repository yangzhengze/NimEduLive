package com.vitek.neteaselive.education.model;


import com.netease.nimlib.sdk.document.model.DMData;

import java.io.Serializable;
import java.util.Map;

/**
 * Created by winnie on 2016/12/16.
 */

public class Document implements Serializable {
    /**
     * 文档详细信息
     */
    private DMData dmData;
    /**
     * 文档下载后存储的本地路径
     */
    private Map<Integer, String> pathMap;
    /**
     * 文件下载状态
     */
    private FileDownloadStatusEnum fileDownloadStatusEnum;

    public Document(DMData dmData, Map<Integer, String> pathMap,
                    FileDownloadStatusEnum fileDownloadStatusEnum) {
        this.dmData = dmData;
        this.pathMap = pathMap;
        this.fileDownloadStatusEnum = fileDownloadStatusEnum;
    }

    public DMData getDmData() {
        return dmData;
    }

    public void setDmData(DMData dmData) {
        this.dmData = dmData;
    }

    public Map<Integer, String> getPathMap() {
        return pathMap;
    }

    public void setPathMap(Map<Integer, String> pathMap) {
        this.pathMap = pathMap;
    }

    public FileDownloadStatusEnum getFileDownloadStatusEnum() {
        return fileDownloadStatusEnum;
    }

    public void setFileDownloadStatusEnum(FileDownloadStatusEnum fileDownloadStatusEnum) {
        this.fileDownloadStatusEnum = fileDownloadStatusEnum;
    }
}
