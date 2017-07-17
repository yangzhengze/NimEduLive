package com.vitek.neteaselive.education.fragment;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomInfo;
import com.netease.nimlib.sdk.document.DocumentManager;
import com.netease.nimlib.sdk.document.model.DMData;
import com.netease.nimlib.sdk.document.model.DMDocTransQuality;
import com.netease.nimlib.sdk.nos.NosService;
import com.vitek.neteaselive.NimCache;
import com.vitek.neteaselive.R;
import com.vitek.neteaselive.base.ui.TFragment;
import com.vitek.neteaselive.base.util.ScreenUtil;
import com.vitek.neteaselive.base.util.log.LogUtil;
import com.vitek.neteaselive.education.activity.FileListActivity;
import com.vitek.neteaselive.education.doodle.ActionTypeEnum;
import com.vitek.neteaselive.education.doodle.DoodleView;
import com.vitek.neteaselive.education.doodle.OnlineStatusObserver;
import com.vitek.neteaselive.education.doodle.SupportActionType;
import com.vitek.neteaselive.education.doodle.Transaction;
import com.vitek.neteaselive.education.doodle.TransactionCenter;
import com.vitek.neteaselive.education.doodle.action.MyPath;
import com.vitek.neteaselive.education.helper.ChatRoomMemberCache;
import com.vitek.neteaselive.education.helper.VideoListener;
import com.vitek.neteaselive.education.model.Document;
import com.vitek.neteaselive.education.model.FileDownloadStatusEnum;
import com.vitek.neteaselive.im.ui.dialog.EasyAlertDialogHelper;
import com.vitek.neteaselive.im.util.storage.StorageType;
import com.vitek.neteaselive.im.util.storage.StorageUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.os.Looper.getMainLooper;

/**
 * Created by hzxuwen on 2016/10/27.
 */

public class ChatRoomRTSFragment extends TFragment implements View.OnClickListener, VideoListener, OnlineStatusObserver, DoodleView.FlipListener {
    private static final String TAG = ChatRoomRTSFragment.class.getSimpleName();
    private View rootView;
    private DoodleView doodleView; // 画板
    // 控制条
    private TextView clearAllBtn;
    private TextView playbackBtn;
    private TextView chooseColorBtn;
    private View joinTipText;
    private ViewGroup previousPageBtn;
    private ViewGroup nextPageBtn;
    private ImageView previousPageImage;
    private ImageView nextPageImage;
    private TextView fileBtn;
    private ViewGroup pageslayout;
    private TextView pagesText;
    // 调色盘
    private ViewGroup palleteLayout;
    private ImageView blackImage;
    private ImageView redImage;
    private ImageView yellowImage;
    private ImageView greenImage;
    private ImageView blueImage;
    private ImageView purpleImage;
    // 文档分享
    private View closeFileBtn;
    private TextView fileLoadingText;

    // data
    private String sessionId; // 白板sessionId
    private ChatRoomInfo roomInfo;
    private HashMap<Integer, Integer> colorChoosedMap = new HashMap<>();
    private HashMap<Integer, Integer> colorMap = new HashMap<>();
    private int choosedColor;

    private int currentPageNum = 0;
    private int totalPageNum = 0;
    private Document document;

    private boolean isFileMode = false; // 是否是文档模式

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.chat_room_rts_fragment, container, false);
        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initData();
        findViews();
        setListener();
    }

    public void onCurrent() {
    }

    @Override
    public void onResume() {
        super.onResume();
        doodleView.onResume();
    }

    @Override
    public void onDestroy() {
        if (doodleView != null) {
            doodleView.end();
        }
        registerObservers(false);
        super.onDestroy();
    }

    private void registerObservers(boolean register) {
        ChatRoomMemberCache.getInstance().registerMeetingControlObserver(meetingControlObserver, register);
        TransactionCenter.getInstance().registerOnlineStatusObserver(sessionId, this);
    }

    ChatRoomMemberCache.MeetingControlObserver meetingControlObserver = new ChatRoomMemberCache.MeetingControlObserver() {
        @Override
        public void onAccept(String roomID) {

        }

        @Override
        public void onReject(String roomID) {
            if (!roomID.equals(roomInfo.getRoomId())) {
                return;
            }
            ChatRoomMemberCache.getInstance().setRTSOpen(false);
            initView();
        }

        @Override
        public void onPermissionResponse(String roomId, List<String> accounts) {

        }

        @Override
        public void onSaveMemberPermission(String roomID, List<String> accounts) {

        }

        @Override
        public void onSendMyPermission(String roomID, String toAccount) {

        }

        @Override
        public void onHandsUp(String roomID, String account) {

        }

        @Override
        public void onHandsDown(String roomID, String account) {

        }

        @Override
        public void onStatusNotify(String roomID, List<String> accounts) {
            if (roomInfo == null ||  TextUtils.isEmpty(roomInfo.getRoomId()) || !roomInfo.getRoomId().equals(roomID)) {
                return;
            }

            // 等权限处理完毕，刷新一下自己的界面
            getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                   initView();
                }
            }, 50);
        }
    };

    private void initData() {
        colorChoosedMap.put(R.id.black_color_image, R.drawable.choose_black_circle_shape);
        colorChoosedMap.put(R.id.red_color_image, R.drawable.choose_red_circle_shape);
        colorChoosedMap.put(R.id.yellow_color_image, R.drawable.choose_yellow_circle_shape);
        colorChoosedMap.put(R.id.green_color_image, R.drawable.choose_green_circle_shape);
        colorChoosedMap.put(R.id.blue_color_image, R.drawable.choose_blue_circle_shape);
        colorChoosedMap.put(R.id.purple_color_image, R.drawable.choose_purple_circle_shape);

        colorMap.put(R.id.black_color_image, Color.BLACK);
        colorMap.put(R.id.red_color_image, getResources().getColor(R.color.color_red_d1021c));
        colorMap.put(R.id.yellow_color_image, getResources().getColor(R.color.color_yellow_fddc01));
        colorMap.put(R.id.green_color_image, getResources().getColor(R.color.color_green_7dd21f));
        colorMap.put(R.id.blue_color_image, getResources().getColor(R.color.color_blue_228bf7));
        colorMap.put(R.id.purple_color_image, getResources().getColor(R.color.color_purple_9b0df5));
    }

    private void findViews() {
        // 画板
        doodleView = findView(R.id.doodle_view);
        // 控制条
        clearAllBtn = findView(R.id.clear_all_btn);
        playbackBtn = findView(R.id.play_back_btn);
        chooseColorBtn = findView(R.id.choose_color_btn);
        joinTipText = findView(R.id.join_tip_text);
        previousPageBtn = findView(R.id.previous_page);
        nextPageBtn = findView(R.id.next_page);
        previousPageImage = findView(R.id.previous_page_image);
        nextPageImage = findView(R.id.next_page_image);
        fileBtn = findView(R.id.file_btn);
        pageslayout = findView(R.id.pages_layout);
        pagesText = findView(R.id.pages);
        // 调色盘
        palleteLayout = findView(R.id.palette_layout);
        blackImage = findView(R.id.black_color_image);
        redImage = findView(R.id.red_color_image);
        yellowImage = findView(R.id.yellow_color_image);
        greenImage = findView(R.id.green_color_image);
        blueImage = findView(R.id.blue_color_image);
        purpleImage = findView(R.id.purple_color_image);
        // 文档
        closeFileBtn = findView(R.id.close_file_btn);
        fileLoadingText = findView(R.id.file_loading_text);
    }

    private void setListener() {
        clearAllBtn.setOnClickListener(this);
        playbackBtn.setOnClickListener(this);
        chooseColorBtn.setOnClickListener(this);
        previousPageBtn.setOnClickListener(this);
        nextPageBtn.setOnClickListener(this);
        fileBtn.setOnClickListener(this);
        // 调色盘
        palleteLayout.setOnClickListener(this);
        blackImage.setOnClickListener(colorClickListener);
        redImage.setOnClickListener(colorClickListener);
        yellowImage.setOnClickListener(colorClickListener);
        greenImage.setOnClickListener(colorClickListener);
        blueImage.setOnClickListener(colorClickListener);
        purpleImage.setOnClickListener(colorClickListener);
        // 文档
        closeFileBtn.setOnClickListener(this);
        fileLoadingText.setOnClickListener(this);
    }

    public void initRTSView(String sessionId, ChatRoomInfo roomInfo) {
        this.sessionId = sessionId;
        this.roomInfo = roomInfo;
        initView();
        initDoodleView(null);
        registerObservers(true);
    }

    // 初始化是否开启白板
    public void initView() {
        if (ChatRoomMemberCache.getInstance().isRTSOpen()) {
            clearAllBtn.setBackgroundResource(R.drawable.chat_room_clear_all_selector);
            playbackBtn.setBackgroundResource(R.drawable.chat_room_play_back_selector);
            clearAllBtn.setEnabled(true);
            playbackBtn.setEnabled(true);
            doodleView.setEnableView(true);
            // 调色盘默认颜色，主播为黑色，观众为蓝色。
            if (roomInfo != null && roomInfo.getCreator().equals(NimCache.getAccount())) {
                this.choosedColor = R.drawable.choose_black_circle_shape;
                chooseColorBtn.setBackgroundResource(R.drawable.choose_black_circle_shape);
            } else {
                this.choosedColor = R.drawable.choose_blue_circle_shape;
                chooseColorBtn.setBackgroundResource(R.drawable.choose_blue_circle_shape);
            }
            chooseColorBtn.setEnabled(true);
        } else {
            clearAllBtn.setBackgroundResource(R.drawable.ic_clear_all_disable);
            playbackBtn.setBackgroundResource(R.drawable.ic_play_back_disable);
            chooseColorBtn.setBackgroundResource(R.drawable.ic_choose_color_disable);
            chooseColorBtn.setEnabled(false);
            clearAllBtn.setEnabled(false);
            playbackBtn.setEnabled(false);
            doodleView.setEnableView(false);
        }

        if (roomInfo != null && roomInfo.getCreator().equals(NimCache.getAccount())) {
            clearAllBtn.setVisibility(View.VISIBLE);
            joinTipText.setVisibility(View.GONE);
            fileBtn.setVisibility(View.VISIBLE);
        } else {
            clearAllBtn.setVisibility(View.GONE);
            fileBtn.setVisibility(View.GONE);
            if (ChatRoomMemberCache.getInstance().isRTSOpen()) {
                joinTipText.setVisibility(View.GONE);
            } else {
                joinTipText.setVisibility(View.VISIBLE);
            }
        }
    }


    @Override
    public void onClick(View v) {
        if (v == clearAllBtn) {
            doodleView.clear();
        } else if (v == playbackBtn) {
            doodleView.paintBack();
        } else if (v == chooseColorBtn) {
            palleteLayout.setVisibility(View.VISIBLE);
        } else if (v == palleteLayout) {
            palleteLayout.setVisibility(View.GONE);
        } else if (v == fileBtn) {
            FileListActivity.startActivityForResult(getContext());
        } else if (v == previousPageBtn) {
            currentPageNum--;
            changePages(document);
        } else if (v == nextPageBtn) {
            currentPageNum++;
            changePages(document);
        } else if (v == closeFileBtn) {
            confirmCloseFileShare();
        } else if (v == fileLoadingText) {
            pageFlip(docTransaction);
        }
    }

    View.OnClickListener colorClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            palleteLayout.setVisibility(View.GONE);
            choosedColor = colorChoosedMap.get(v.getId());
            chooseColorBtn.setBackgroundResource(choosedColor);
            doodleView.setPaintColor(colorMap.get(v.getId()));
        }
    };

    /**
     * ***************************** 画板 ***********************************
     */

    private void initDoodleView(String account) {
        Toast.makeText(getContext(), "init doodle success", Toast.LENGTH_SHORT).show();
        // add support ActionType
        SupportActionType.getInstance().addSupportActionType(ActionTypeEnum.Path.getValue(), MyPath.class);
        if (roomInfo.getCreator().equals(NimCache.getAccount())) {
            doodleView.init(sessionId, account, DoodleView.Mode.BOTH, Color.WHITE, Color.BLACK, getContext(), this);
        } else {
            doodleView.init(sessionId, account, DoodleView.Mode.BOTH, Color.WHITE, colorMap.get(R.id.blue_color_image), getContext(), this);
        }

        doodleView.setPaintSize(3);
        doodleView.setPaintType(ActionTypeEnum.Path.getValue());

        // adjust paint offset
        new Handler(getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Rect frame = new Rect();
                getActivity().getWindow().getDecorView().getWindowVisibleDisplayFrame(frame);
                int statusBarHeight = frame.top;
                Log.i("Doodle", "statusBarHeight =" + statusBarHeight);

                int marginTop = doodleView.getTop();
                Log.i("Doodle", "doodleView marginTop =" + marginTop);

                int marginLeft = doodleView.getLeft();
                Log.i("Doodle", "doodleView marginLeft =" + marginLeft);

                float offsetX = marginLeft;
                float offsetY = statusBarHeight + marginTop + ScreenUtil.dip2px(220) + ScreenUtil.dip2px(40);

                doodleView.setPaintOffset(offsetX, offsetY);
                Log.i("Doodle", "client1 offsetX = " + offsetX + ", offsetY = " + offsetY);
            }
        }, 50);
    }

    @Override
    public void onVideoOn(String account) {

    }

    @Override
    public void onVideoOff(String account) {

    }

    @Override
    public void onTabChange(boolean notify) {

    }

    @Override
    public void onKickOutSuccess(String account) {

    }

    @Override
    public void onUserLeave(String account) {

    }

    @Override
    public void onReportSpeaker(Map<String, Integer> map) {

    }

    @Override
    public void onAcceptConfirm() {
        initView();
    }

    /*************************** 网络状态 *********************/

    @Override
    public boolean onNetWorkChange(boolean isCreator) {
        // 断网重连。主播断网重连上来，给观众发自己的同步数据
        // 观众先清空本地
        if (isCreator) {
            doodleView.sendSyncPrepare();
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    doodleView.sendSyncData(null);
                }
            }, 50);
        } else {
            initView();
            doodleView.clearAll();
        }
        return true;
    }

    /******************** activity result **************/
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        if (requestCode == FileListActivity.REQUEST_CODE) {
            Document doc = (Document) data.getSerializableExtra(FileListActivity.EXTRA_DATA_DOC);
            this.document = doc;
            enterDocMode(doc);
        }
    }

    /********************* 文档共享 **********************/

    private void enterDocMode(Document document) {
        isFileMode = true;
        updatePagesUI(document, 1);
        closeFileBtn.setVisibility(View.VISIBLE);
        Map<Integer, String> pathMap = document.getPathMap();
        if (pathMap == null) {
            return;
        }
        String path = pathMap.get(1);

        // 把正在使用文档，通知给观众
        masterSendFlipData(document, 1);

        // 主播自己界面显示文档信息
        try {
            final Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap != null) {
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        doodleView.setImageView(bitmap);
                    }
                }, 1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void updatePagesUI(Document document, int currentPageNum) {
        if (isFileMode) {
            pageslayout.setVisibility(View.VISIBLE);
            if (NimCache.getAccount().equals(roomInfo.getCreator())) {
                nextPageBtn.setVisibility(View.VISIBLE);
                previousPageBtn.setVisibility(View.VISIBLE);
            } else {
                nextPageBtn.setVisibility(View.GONE);
                previousPageBtn.setVisibility(View.GONE);
            }
            totalPageNum = document.getDmData().getPageNum();
            updatePages(currentPageNum, totalPageNum);
            updatePageBtnUI();
        } else {
            pageslayout.setVisibility(View.GONE);
        }
    }

    private void updatePages(int currentNum, int pagesNum) {
        this.currentPageNum = currentNum;
        pagesText.setText(String.format("%d/%d", currentNum, pagesNum));
    }

    /**
     * 上下翻页
     */
    private void changePages(Document document) {
        if (currentPageNum < 1) {
            currentPageNum = 1;
            return;
        }
        if (currentPageNum > totalPageNum) {
            currentPageNum = totalPageNum;
            return;
        }
        // 主播翻页
        // 1、自己doodleview显示新的内容
        // 2、发送控制命令给观众
        Bitmap bitmap = BitmapFactory.decodeFile(document.getPathMap().get(currentPageNum));
        doodleView.setImageView(bitmap);
        updatePages(currentPageNum, totalPageNum);
        updatePageBtnUI();
        masterSendFlipData(document, currentPageNum);
    }

    private void updatePageBtnUI() {
        if (currentPageNum == 1) {
            previousPageImage.setBackgroundResource(R.drawable.ic_previous_page_disable);
            previousPageBtn.setEnabled(false);
        } else {
            previousPageImage.setBackgroundResource(R.drawable.chat_room_previous_page_selector);
            previousPageBtn.setEnabled(true);
        }
        if (currentPageNum == totalPageNum) {
            nextPageImage.setBackgroundResource(R.drawable.ic_next_page_disable);
            nextPageBtn.setEnabled(false);
        } else {
            nextPageImage.setBackgroundResource(R.drawable.chat_room_next_page_selector);
            nextPageBtn.setEnabled(true);
        }
    }

    private void masterSendFlipData(Document document, int currentPageNum) {
        doodleView.clear();
        doodleView.sendFlipData(document.getDmData().getDocId(), currentPageNum, document.getDmData().getPageNum(), 1);
    }

    /************************ FlipListener *******************/

    @Override
    public void onFlipPage(Transaction transaction) {
        pageFlip(transaction);
    }

    private DMData docData;
    private Transaction docTransaction;

    private void pageFlip(final Transaction transaction) {
        this.docTransaction = transaction;
        if (transaction == null) {
            return;
        }

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showLoadingText();
            }
        });

        // 文档第0页，表示退出文档分享
        if (transaction.getCurrentPageNum() == 0) {
            isFileMode = false;
            closeFileShare();
            hideLoadingText();
            return;
        }
        // 如果文档信息已经下载过了，就不用载了。直接去载翻页图片
        isFileMode = true;

        if (docData != null && docData.getDocId().equals(transaction.getDocId())) {
            doDownloadPage(document, transaction.getCurrentPageNum());
            return;
        }
        LogUtil.i(TAG, "doc id:" + transaction.getDocId());
        DocumentManager.getInstance().querySingleDocumentData(transaction.getDocId(), new RequestCallback<DMData>() {
            @Override
            public void onSuccess(DMData dmData) {
                LogUtil.i(TAG, "query doc success");
                docData = dmData;
                document = new Document(dmData, new HashMap<Integer, String>(), FileDownloadStatusEnum.NotDownload);
                doDownloadPage(document, transaction.getCurrentPageNum());
            }

            @Override
            public void onFailed(int i) {
                LogUtil.i(TAG, "query doc failed, code:" + i);
                showRetryLoadingText();
            }

            @Override
            public void onException(Throwable throwable) {
                LogUtil.i(TAG, "query doc exception:" + throwable.toString());
            }
        });
    }

    private void doDownloadPage(final Document document, final int currentPage) {
        if (document == null || document.getDmData() == null) {
            return;
        }

        final String path = StorageUtil.getWritePath(document.getDmData().getDocName() + currentPage, StorageType.TYPE_FILE);
        String url = document.getDmData().getTransCodedUrl(currentPage, DMDocTransQuality.MEDIUM);
        Map<Integer, String> pathMap = document.getPathMap();
        pathMap.put(currentPage, path);
        document.setPathMap(pathMap);
        NIMClient.getService(NosService.class).download(url, null, path).setCallback(new RequestCallback() {
            @Override
            public void onSuccess(Object o) {
                hideLoadingText();
                Bitmap bitmap = BitmapFactory.decodeFile(path);
                LogUtil.i(TAG, "Audience download success, set bitmap:" + bitmap);
                doodleView.setImageView(bitmap);
                updatePagesUI(document, currentPage);
            }

            @Override
            public void onFailed(int i) {
                LogUtil.i(TAG, "Audience download file failed, code:" + i);
                showRetryLoadingText();
            }

            @Override
            public void onException(Throwable throwable) {

            }
        });
    }

    private void confirmCloseFileShare() {
        EasyAlertDialogHelper.createOkCancelDiolag(getActivity(), getString(R.string.operation_confirm),
                getString(R.string.confirm_to_close_file_share), getString(R.string.close_file_share), getString(R.string.cancel), true,
                new EasyAlertDialogHelper.OnDialogActionListener() {
                    @Override
                    public void doCancelAction() {

                    }

                    @Override
                    public void doOkAction() {
                        closeFileShare();
                    }
                }).show();
    }


    // 退出文档分享
    private void closeFileShare() {
        isFileMode = false;
        doodleView.setBitmap(null);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                closeFileBtn.setVisibility(View.GONE);
                updatePagesUI(null, -1);
            }
        });
        // 主播退出文档分享，通知观众
        if (NimCache.getAccount().equals(roomInfo.getCreator())) {
            doodleView.clear();
            doodleView.sendFlipData(document.getDmData().getDocId(), 0, 0, 1);
        }
    }

    private void showLoadingText() {
        fileLoadingText.setVisibility(View.VISIBLE);
        fileLoadingText.setText(R.string.file_loading);
        fileLoadingText.setEnabled(false);
    }

    private void hideLoadingText() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fileLoadingText.setVisibility(View.GONE);
            }
        });
    }

    private void showRetryLoadingText() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fileLoadingText.setVisibility(View.VISIBLE);
                fileLoadingText.setText(R.string.failed_to_retry);
                fileLoadingText.setEnabled(true);
            }
        });

    }
}
