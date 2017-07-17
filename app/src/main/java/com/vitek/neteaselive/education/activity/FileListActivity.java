package com.vitek.neteaselive.education.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.document.DocumentManager;
import com.netease.nimlib.sdk.document.model.DMData;
import com.netease.nimlib.sdk.document.model.DMDocTransQuality;
import com.netease.nimlib.sdk.document.model.DocTransState;
import com.netease.nimlib.sdk.nos.NosService;
import com.vitek.neteaselive.R;
import com.vitek.neteaselive.base.ui.TActivity;
import com.vitek.neteaselive.base.util.log.LogUtil;
import com.vitek.neteaselive.education.adapter.FileListAdapter;
import com.vitek.neteaselive.education.model.Document;
import com.vitek.neteaselive.education.model.FileDownloadStatusEnum;
import com.vitek.neteaselive.im.util.storage.StorageType;
import com.vitek.neteaselive.im.util.storage.StorageUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by hzxuwen on 2016/12/14.
 */

public class FileListActivity extends TActivity implements FileListAdapter.ViewClickListener {
    private static final String TAG = FileListActivity.class.getSimpleName();

    public static final int REQUEST_CODE = 1000;
    public static final String EXTRA_DATA_DOC = "EXTRA_DATA_DOC";

    private RecyclerView fileList;

    private List<Document> docList;
    private FileListAdapter adapter;

    public static void startActivityForResult(Context context) {
        Intent intent = new Intent();
        intent.setClass(context, FileListActivity.class);
        ((Activity) context).startActivityForResult(intent, REQUEST_CODE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.file_list_activity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.file_list);
        toolbar.setLogo(R.drawable.actionbar_logo_white);
        setSupportActionBar(toolbar);

        toolbar.setNavigationIcon(R.drawable.actionbar_white_back_icon);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        findViews();
        queryDMData(null);
    }

    private void findViews() {
        fileList = findView(R.id.file_list);

        fileList.setLayoutManager(new LinearLayoutManager(this));
        docList = new ArrayList<>();
        adapter = new FileListAdapter(this, docList);
        adapter.setViewClickListener(this);
        fileList.setAdapter(adapter);
    }

    private void queryDMData(String marker) {
        DocumentManager.getInstance().queryDocumentDataList(marker, 10, new RequestCallback<List<DMData>>() {
            @Override
            public void onSuccess(List<DMData> dmDatas) {
                LogUtil.i(TAG, "query doc list size:" + dmDatas.size());
                if (dmDatas == null || dmDatas.size() <= 0) {
                    return;
                }

                queryDMData(dmDatas.get(dmDatas.size() - 1).getDocId());

                for (DMData data : dmDatas) {
                    // 1表示转码准备中，2表示转码中，3表示转码超时，4表示转码成功，5表示转码失败
                    if (data.getTransStat() != DocTransState.Completed) {
                        continue;
                    }

                    if (checkLocalFileHasDownload(data)) {
                        docList.add(new Document(data, new HashMap<Integer, String>(), FileDownloadStatusEnum.DownLoaded));
                    } else {
                        docList.add(new Document(data, new HashMap<Integer, String>(), FileDownloadStatusEnum.NotDownload));
                    }
                }
                adapter.notifyDataSetChanged();
                downloadFirstPage();
            }

            @Override
            public void onFailed(int i) {
                Toast.makeText(FileListActivity.this, "query doc faild, code:" + i, Toast.LENGTH_SHORT).show();
                LogUtil.d(TAG, "query doc data list failed, code:" + i);
            }

            @Override
            public void onException(Throwable throwable) {

            }
        });
    }

    @Override
    public void onOperationClick(Document doc, int position) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_DATA_DOC, doc);
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    @Override
    public void onDeleteClick(String docId, int position) {
        docList.remove(position);
        adapter.notifyDataSetChanged();
    }

    private boolean checkLocalFileHasDownload(DMData data) {
        String path = StorageUtil.getWritePath(data.getDocName() + data.getPageNum(), StorageType.TYPE_FILE);
        try {
            File file = new File(path);
            if (file.exists()) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    private void downloadFirstPage() {
        for (Document document : docList) {
            String path = StorageUtil.getWritePath(document.getDmData().getDocName() + 1, StorageType.TYPE_FILE);
            String url = document.getDmData().getTransCodedUrl(1, DMDocTransQuality.MEDIUM);
            Map<Integer, String> pathMap = document.getPathMap();
            pathMap.put(1, path);
            document.setPathMap(pathMap);
            NIMClient.getService(NosService.class).download(url, null, path).setCallback(new RequestCallback() {
                @Override
                public void onSuccess(Object o) {
                    LogUtil.i(TAG, "download success, page:" + 1);
                    adapter.notifyDataSetChanged();
                }

                @Override
                public void onFailed(int i) {
                    LogUtil.i(TAG, "download doc failed, code:" + i);
                }

                @Override
                public void onException(Throwable throwable) {

                }
            });
        }
    }
}


