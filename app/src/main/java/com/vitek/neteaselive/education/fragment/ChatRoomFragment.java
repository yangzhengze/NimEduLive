package com.vitek.neteaselive.education.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.avchat.AVChatCallback;
import com.netease.nimlib.sdk.avchat.AVChatManager;
import com.netease.nimlib.sdk.avchat.AVChatStateObserver;
import com.netease.nimlib.sdk.avchat.constant.AVChatResCode;
import com.netease.nimlib.sdk.avchat.constant.AVChatType;
import com.netease.nimlib.sdk.avchat.constant.AVChatVideoScalingType;
import com.netease.nimlib.sdk.avchat.model.AVChatAudioFrame;
import com.netease.nimlib.sdk.avchat.model.AVChatData;
import com.netease.nimlib.sdk.avchat.model.AVChatNetworkStats;
import com.netease.nimlib.sdk.avchat.model.AVChatOptionalConfig;
import com.netease.nimlib.sdk.avchat.model.AVChatParameters;
import com.netease.nimlib.sdk.avchat.model.AVChatSessionStats;
import com.netease.nimlib.sdk.avchat.model.AVChatVideoFrame;
import com.netease.nimlib.sdk.avchat.model.AVChatVideoRender;
import com.netease.nimlib.sdk.chatroom.ChatRoomService;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomInfo;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMember;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomNotificationAttachment;
import com.netease.nimlib.sdk.msg.model.IMMessage;
import com.netease.nimlib.sdk.rts.RTSCallback;
import com.netease.nimlib.sdk.rts.RTSManager2;
import com.vitek.neteaselive.NimCache;
import com.vitek.neteaselive.R;
import com.vitek.neteaselive.base.ui.TFragment;
import com.vitek.neteaselive.base.util.ScreenUtil;
import com.vitek.neteaselive.base.util.log.LogUtil;
import com.vitek.neteaselive.education.activity.ChatRoomActivity;
import com.vitek.neteaselive.education.adapter.ChatRoomTabPagerAdapter;
import com.vitek.neteaselive.education.fragment.tab.ChatRoomTab;
import com.vitek.neteaselive.education.helper.ChatRoomMemberCache;
import com.vitek.neteaselive.education.helper.MsgHelper;
import com.vitek.neteaselive.education.helper.VideoListener;
import com.vitek.neteaselive.education.module.ChatRoomHttpClient;
import com.vitek.neteaselive.education.module.FullScreenType;
import com.vitek.neteaselive.education.module.MeetingConstant;
import com.vitek.neteaselive.education.module.MeetingOptCommand;
import com.vitek.neteaselive.education.util.NonScrollViewPager;
import com.vitek.neteaselive.education.util.Preferences;
import com.vitek.neteaselive.im.session.ModuleProxy;
import com.vitek.neteaselive.im.ui.dialog.EasyAlertDialogHelper;
import com.vitek.neteaselive.im.ui.tab.FadeInOutPageTransformer;
import com.vitek.neteaselive.im.ui.tab.PagerSlidingTabStrip;
import com.vitek.neteaselive.im.ui.tab.reminder.ReminderId;
import com.vitek.neteaselive.im.ui.tab.reminder.ReminderItem;
import com.vitek.neteaselive.permission.MPermission;
import com.vitek.neteaselive.permission.annotation.OnMPermissionDenied;
import com.vitek.neteaselive.permission.annotation.OnMPermissionGranted;
import com.vitek.neteaselive.permission.annotation.OnMPermissionNeverAskAgain;
import com.vitek.neteaselive.permission.util.MPermissionUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by hzxuwen on 2016/2/29.
 */
public class ChatRoomFragment extends TFragment implements ViewPager.OnPageChangeListener, AVChatStateObserver, View.OnClickListener {
    private final String TAG = "ChatRoomFragment";
    private final int LIVE_PERMISSION_REQUEST_CODE = 100;

    private Activity activity;
    private ChatRoomInfo roomInfo;
    private String roomId;
    private String roomName;
    private String shareUrl; // 分享地址

    private boolean disconnected = false; // 是否断网（断网重连用）
    private boolean isPermissionInit = false; // 是否收到其他成员权限
    private boolean isBackLayoutShow = true; // 返回布局是否显示
    private boolean isCreate = false; // 是否是主播

    private VideoListener videoListener;
    private List<String> userJoinedList = new ArrayList<>(); // 已经onUserJoined的用户
    AVChatVideoRender masterRender; // 主播画布

    /**
     * 聊天室TAB（下）
     */
    private PagerSlidingTabStrip tabs;
    private NonScrollViewPager viewPager;
    private ChatRoomTabPagerAdapter adapter;
    private int scrollState;

    /**
     * 直播区域（上）
     */
    private RelativeLayout videoLayout; // 直播/播放区域
    private ViewGroup backLayout;
    private ViewGroup fullScreenView; // 全屏播放显示区域
    private ViewGroup fullScreenLayout;
    private ImageView videoPermissionBtn; // 视频权限按钮
    private ImageView audioPermissionBtn; // 音频权限按钮
    private TextView interactionStartCloseBtn; // 互动开始/结束按钮
    private TextView statusText;
    private TextView roomIdText;
    private SurfaceView selfRender;
    private long uid;
    private ViewGroup masterVideoLayout; // 左上，主播显示区域
    private ViewGroup firstRightVideoLayout; // 右上，第一个观众显示区域
    private ViewGroup secondLeftVideoLayout; // 左下，第二个观众显示区域
    private ViewGroup thirdRightVideoLayout; // 右下， 第三个观众显示区域
    private ViewGroup[] viewLayouts = new ViewGroup[3];
    private ImageView fullScreenImage; // 显示全屏按钮
    private ImageView cancelFullScreenImage; //取消全屏显示按钮

    /**
     * ********************************** 生命周期 **********************************
     */

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.chat_room_fragment, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        findViews();
        setupPager();
        setupTabs();
        registerObservers(true);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isPermissionInit) {
                    requestPermissionMembers();
                }
            }
        }, 5000);
        requestLivePermission();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.activity = (ChatRoomActivity) context;
        videoListener = (VideoListener) context;
    }

    // 向所有人请求成员权限
    private void requestPermissionMembers() {
        LogUtil.d(TAG, "request permission members");
        MsgHelper.getInstance().sendCustomMsg(roomId, MeetingOptCommand.GET_STATUS);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        registerObservers(false);

        if (roomId != null) {
            NIMClient.getService(ChatRoomService.class).exitChatRoom(roomId);
            clearChatRoom();
        }
    }

    public void onBackPressed() {
        logoutChatRoom();
    }

    public void onKickOut() {
        LogUtil.d(TAG, "chat room do kick out");
        activity.finish();
    }

    private void logoutChatRoom() {
        EasyAlertDialogHelper.createOkCancelDiolag(activity, null, getString(R.string.logout_confirm),
                getString(R.string.leave), getString(R.string.cancel), true,
                new EasyAlertDialogHelper.OnDialogActionListener() {
                    @Override
                    public void doCancelAction() {

                    }

                    @Override
                    public void doOkAction() {
                        if (roomInfo.getCreator().equals(NimCache.getAccount())) {
                            // 自己是主持人，则关闭聊天室
                            closeChatRoom();
                        }
                        activity.finish();
                    }
                }).show();

    }

    // 关闭聊天室
    private void closeChatRoom() {
        ChatRoomHttpClient.getInstance().closeRoom(roomId, roomInfo.getCreator(), new ChatRoomHttpClient.ChatRoomHttpCallback<String>() {
            @Override
            public void onSuccess(String s) {
                LogUtil.d(TAG, "close room success");
            }

            @Override
            public void onFailed(int code, String errorMsg) {
                LogUtil.d(TAG, "close room failed, code:" + code + ", errorMsg:" + errorMsg);
            }
        });
    }

    private void clearChatRoom() {
        LogUtil.d(TAG, "chat room do clear");
        AVChatManager.getInstance().leaveRoom(roomId, new AVChatCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                LogUtil.d(TAG, "leave channel success");
            }

            @Override
            public void onFailed(int i) {
                LogUtil.d(TAG, "leave channel failed, code:" + i);
            }

            @Override
            public void onException(Throwable throwable) {

            }
        });
        RTSManager2.getInstance().leaveSession(roomId, new RTSCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                LogUtil.d(TAG, "leave rts session success");
            }

            @Override
            public void onFailed(int i) {
                LogUtil.d(TAG, "leave rts session failed, code:" + i);
            }

            @Override
            public void onException(Throwable throwable) {

            }
        });
        ChatRoomMemberCache.getInstance().clearRoomCache(roomId);
    }

    /**
     * ********************************** View初始化 **********************************
     */

    private void findViews() {
        // 直播区域
        videoLayout = findView(R.id.view_layout);
        backLayout = findView(R.id.back_layout);
        fullScreenView = findView(R.id.full_screen_view);
        fullScreenLayout = findView(R.id.full_screen_layout);

        videoLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBackLayoutShow) {
                    isBackLayoutShow = false;
                    backLayout.setVisibility(View.GONE);
                } else {
                    isBackLayoutShow = true;
                    backLayout.setVisibility(View.VISIBLE);
                }
            }
        });

        masterVideoLayout = findView(R.id.master_video_layout);
        firstRightVideoLayout = findView(R.id.first_video_layout);
        secondLeftVideoLayout = findView(R.id.second_video_layout);
        thirdRightVideoLayout = findView(R.id.third_video_layout);
        fullScreenImage = findView(R.id.full_screen_image);
        cancelFullScreenImage = findView(R.id.cancel_full_screen_image);
        fullScreenImage.setOnClickListener(this);
        cancelFullScreenImage.setOnClickListener(this);

        viewLayouts[0] = firstRightVideoLayout;
        viewLayouts[1] = secondLeftVideoLayout;
        viewLayouts[2] = thirdRightVideoLayout;

        roomIdText = findView(R.id.room_id);
        statusText = findView(R.id.online_status);
        findView(R.id.back_arrow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        videoPermissionBtn = findView(R.id.video_permission_btn);
        audioPermissionBtn = findView(R.id.audio_permission_btn);
        interactionStartCloseBtn = findView(R.id.close_apply_btn);

        videoPermissionBtn.setOnClickListener(this);
        audioPermissionBtn.setOnClickListener(this);
        interactionStartCloseBtn.setOnClickListener(this);

        // 聊天室区域
        tabs = findView(R.id.chat_room_tabs);
        viewPager = findView(R.id.chat_room_viewpager);
    }


    // 初始化UI
    public void initLiveVideo(ChatRoomInfo roomInfo, String channelName, boolean isCreate, String shareUrl, ModuleProxy moduleProxy) {
        this.roomInfo = roomInfo;
        this.roomId = roomInfo.getRoomId();
        this.roomName = channelName;
        this.shareUrl = shareUrl;
        this.isCreate = isCreate;
        roomIdText.setText(String.format("房间:%s", roomId));
        AVChatOptionalConfig avChatOptionalParam = new AVChatOptionalConfig();
        String audioEffect = Preferences.getAudioEffectMode();
        LogUtil.i(TAG, "audio effect ns mode:" + audioEffect);
        avChatOptionalParam.setAudioEffectNSMode(audioEffect);
        if (isCreate) {
            avChatOptionalParam.enableAudienceRole(false);
            ChatRoomMemberCache.getInstance().savePermissionMemberbyId(roomId, roomInfo.getCreator());
        } else {
            avChatOptionalParam.enableAudienceRole(true);
        }
        AVChatManager.getInstance().joinRoom(roomId, AVChatType.VIDEO, avChatOptionalParam, new AVChatCallback<AVChatData>() {
            @Override
            public void onSuccess(AVChatData avChatData) {
                LogUtil.d(TAG, "join channel success, extra:" + avChatData.getExtra());
                // 设置音量信号监听, 通过AVChatStateObserver的onReportSpeaker回调音量大小
                AVChatParameters avChatParameters = new AVChatParameters();
                avChatParameters.setBoolean(AVChatParameters.KEY_AUDIO_REPORT_SPEAKER, true);
                AVChatManager.getInstance().setParameters(avChatParameters);
            }

            @Override
            public void onFailed(int i) {
                LogUtil.d(TAG, "join channel failed, code:" + i);
                Toast.makeText(activity, "join channel failed, code:" + i, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onException(Throwable throwable) {

            }
        });

        updateControlUI();
        switchHandsUpLayout();
        updateVideoAudioUI();
    }

    private void updateControlUI() {
        if (isCreate) {
            videoPermissionBtn.setVisibility(View.VISIBLE);
            audioPermissionBtn.setVisibility(View.VISIBLE);
            interactionStartCloseBtn.setVisibility(View.GONE);
        } else if (ChatRoomMemberCache.getInstance().hasPermission(roomId, NimCache.getAccount())) {
            videoPermissionBtn.setVisibility(View.VISIBLE);
            audioPermissionBtn.setVisibility(View.VISIBLE);
            interactionStartCloseBtn.setVisibility(View.VISIBLE);
            interactionStartCloseBtn.setText(R.string.finish);
        } else {
            videoPermissionBtn.setVisibility(View.GONE);
            audioPermissionBtn.setVisibility(View.GONE);
            interactionStartCloseBtn.setVisibility(View.VISIBLE);
            interactionStartCloseBtn.setText(R.string.interaction);
        }
    }

    public void onOnlineStatusChanged(boolean isOnline) {
        statusText.setVisibility(isOnline ? View.GONE : View.VISIBLE);
        NIMClient.getService(ChatRoomService.class).fetchRoomInfo(roomId).setCallback(new RequestCallback<ChatRoomInfo>() {
            @Override
            public void onSuccess(ChatRoomInfo chatRoomInfo) {
                roomInfo = chatRoomInfo;
                ((ChatRoomActivity) getActivity()).setRoomInfo(roomInfo);
                updateDeskShareUI();
            }

            @Override
            public void onFailed(int i) {

            }

            @Override
            public void onException(Throwable throwable) {

            }
        });
    }

    /**************************
     * 音视频权限控制
     ******************************/

    // 权限控制
    private static final String[] LIVE_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO};

    private void requestLivePermission() {
        MPermission.with(ChatRoomFragment.this)
                .addRequestCode(LIVE_PERMISSION_REQUEST_CODE)
                .permissions(LIVE_PERMISSIONS)
                .request();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        MPermission.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @OnMPermissionGranted(LIVE_PERMISSION_REQUEST_CODE)
    public void onLivePermissionGranted() {
        Toast.makeText(activity, "授权成功", Toast.LENGTH_SHORT).show();
    }

    @OnMPermissionDenied(LIVE_PERMISSION_REQUEST_CODE)
    public void onLivePermissionDenied() {
        List<String> deniedPermissions = MPermission.getDeniedPermissions(this, LIVE_PERMISSIONS);
        String tip = "您拒绝了权限" + MPermissionUtil.toString(deniedPermissions) + "，无法开启直播";
        Toast.makeText(activity, tip, Toast.LENGTH_SHORT).show();
    }

    @OnMPermissionNeverAskAgain(LIVE_PERMISSION_REQUEST_CODE)
    public void onLivePermissionDeniedAsNeverAskAgain() {
        List<String> deniedPermissions = MPermission.getDeniedPermissionsWithoutNeverAskAgain(this, LIVE_PERMISSIONS);
        List<String> neverAskAgainPermission = MPermission.getNeverAskAgainPermissions(this, LIVE_PERMISSIONS);
        StringBuilder sb = new StringBuilder();
        sb.append("无法开启直播，请到系统设置页面开启权限");
        sb.append(MPermissionUtil.toString(neverAskAgainPermission));
        if (deniedPermissions != null && !deniedPermissions.isEmpty()) {
            sb.append(",下次询问请授予权限");
            sb.append(MPermissionUtil.toString(deniedPermissions));
        }

        Toast.makeText(activity, sb.toString(), Toast.LENGTH_LONG).show();
    }

    /************************** 音视频权限控制 end ******************************/

    /*************************
     * 监听
     ************************************/

    private void registerObservers(boolean register) {
        AVChatManager.getInstance().observeAVChatState(this, register);
        ChatRoomMemberCache.getInstance().registerMeetingControlObserver(meetingControlObserver, register);
        ChatRoomMemberCache.getInstance().registerRoomMemberChangedObserver(roomMemberChangedObserver, register);
        ChatRoomMemberCache.getInstance().registerRoomInfoChangedObserver(roomInfoChangedObserver, register);
    }

    ChatRoomMemberCache.MeetingControlObserver meetingControlObserver = new ChatRoomMemberCache.MeetingControlObserver() {
        @Override
        public void onAccept(String roomID) {
            if (checkRoom(roomID)) {
                return;
            }
            chooseSpeechType();
        }

        @Override
        public void onReject(String roomID) {

        }

        @Override
        public void onPermissionResponse(String roomId, List<String> accounts) {
            if (checkRoom(roomId)) {
                return;
            }
            for (String a : accounts) {
                LogUtil.i(TAG, "on permission response, account:" + a);
                ChatRoomMemberCache.getInstance().savePermissionMemberbyId(roomId, a);
                onVideoOn(a);
            }
        }

        @Override
        public void onSendMyPermission(String roomID, String toAccount) {
            if (checkRoom(roomID)) {
                return;
            }

            if (ChatRoomMemberCache.getInstance().hasPermission(roomID, NimCache.getAccount())) {
                List<String> accounts = new ArrayList<>(1);
                accounts.add(NimCache.getAccount());
                MsgHelper.getInstance().sendP2PCustomNotification(roomID, MeetingOptCommand.STATUS_RESPONSE.getValue(),
                        toAccount, accounts);
            }
        }

        @Override
        public void onSaveMemberPermission(String roomID, List<String> accounts) {
            if (checkRoom(roomID)) {
                return;
            }
            saveMemberPermission(accounts);
        }

        @Override
        public void onHandsUp(String roomID, String account) {
            if (checkRoom(roomID)) {
                return;
            }
            ChatRoomMemberCache.getInstance().saveMemberHandsUpDown(roomId, account, true);
            onTabChange(true);
        }

        @Override
        public void onHandsDown(String roomID, String account) {
            if (checkRoom(roomID)) {
                return;
            }
            ChatRoomMemberCache.getInstance().saveMemberHandsUpDown(roomID, account, false);
            onTabChange(false);
            if (ChatRoomMemberCache.getInstance().hasPermission(roomID, account)) {
                removeMemberPermission(account);
            }
        }

        @Override
        public void onStatusNotify(String roomID, List<String> accounts) {
            if (checkRoom(roomID)) {
                return;
            }
            onPermissionChange(accounts);
            updateControlUI();
        }
    };

    private boolean checkRoom(String roomID) {
        return TextUtils.isEmpty(roomId) || !roomId.equals(roomID);
    }

    ChatRoomMemberCache.RoomMemberChangedObserver roomMemberChangedObserver = new ChatRoomMemberCache.RoomMemberChangedObserver() {
        @Override
        public void onRoomMemberIn(ChatRoomMember member) {
            onMasterJoin(member.getAccount());

            if (NimCache.getAccount().equals(roomInfo.getCreator())
                    && !member.getAccount().equals(NimCache.getAccount())) {
                // 主持人点对点通知有权限的成员列表
                // 主持人自己进来，不需要通知自己
                MsgHelper.getInstance().sendP2PCustomNotification(roomId, MeetingOptCommand.ALL_STATUS.getValue(),
                        member.getAccount(), ChatRoomMemberCache.getInstance().getPermissionMems(roomId));
            }

            if (member.getAccount().equals(roomInfo.getCreator())) {
                // 主持人重新进来,观众要取消自己的举手状态
                ChatRoomMemberCache.getInstance().saveMyHandsUpDown(roomId, false);
            }

            if (member.getAccount().equals(roomInfo.getCreator()) && NimCache.getAccount().equals(roomInfo.getCreator())) {
                // 主持人自己重新进来，清空观众的举手状态
                ChatRoomMemberCache.getInstance().clearAllHandsUp(roomId);
                // 重新向所有成员请求权限
                requestPermissionMembers();
            }
        }

        @Override
        public void onRoomMemberExit(ChatRoomMember member) {
            // 主持人要清空离开成员的举手
            if (NimCache.getAccount().equals(roomInfo.getCreator())) {
                ChatRoomMemberCache.getInstance().removeHandsUpMem(roomId, member.getAccount());
            }

            // 用户离开频道，如果是有权限用户，移除下画布
            if (member.getAccount().equals(roomInfo.getCreator())) {
                masterVideoLayout.removeAllViews();
            } else if (ChatRoomMemberCache.getInstance().hasPermission(roomId, member.getAccount())) {
                removeMemberPermission(member.getAccount());
            }
        }
    };

    ChatRoomMemberCache.RoomInfoChangedObserver roomInfoChangedObserver = new ChatRoomMemberCache.RoomInfoChangedObserver() {
        @Override
        public void onRoomInfoUpdate(IMMessage message) {
            ChatRoomNotificationAttachment attachment = (ChatRoomNotificationAttachment) message.getAttachment();
            if (attachment != null && attachment.getExtension() != null) {
                Map<String, Object> ext = attachment.getExtension();
                switchFullScreen(ext);
            }
        }
    };

    private void removeMemberPermission(String account) {
        ChatRoomMemberCache.getInstance().removePermissionMem(roomId, account);
        onVideoOff(account);
        if (NimCache.getAccount().equals(roomInfo.getCreator()) && !account.equals(NimCache.getAccount())) {
            MsgHelper.getInstance().sendCustomMsg(roomId, MeetingOptCommand.ALL_STATUS);
        }
    }

    /*************************
     * 监听 end
     ************************************/

    // 选择发言方式
    private void chooseSpeechType() {
        final CharSequence[] items = {"语音", "视频"}; // 设置选择内容
        final boolean[] checkedItems = {true, true};// 设置默认选中
        String content = "";
        if (ChatRoomMemberCache.getInstance().isMyHandsUp(roomId)) {
            content = "主持人已通过你的发言申请，\n";
        } else {
            content = "主持人开通了你的发言权限，\n";
        }
        CheckBox checkBox = new CheckBox(activity);
        checkBox.setText("白板互动(常开)");
        checkBox.setEnabled(false);
        checkBox.setChecked(true);
        new AlertDialog.Builder(activity)
                .setTitle(content +
                        "请选择发言方式：")
                .setMultiChoiceItems(items, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checkedItems[which] = isChecked;
                    }
                })
                .setView(checkBox, ScreenUtil.dip2px(20), 0, 0, 0)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!ChatRoomMemberCache.getInstance().hasPermission(roomId, NimCache.getAccount())) {
                            return;
                        }
                        AVChatManager.getInstance().enableAudienceRole(false);

                        if (!checkedItems[0]) {
                            AVChatManager.getInstance().muteLocalAudio(true);
                        } else {
                            AVChatManager.getInstance().muteLocalAudio(false);
                        }

                        if (!checkedItems[1]) {
                            AVChatManager.getInstance().muteLocalVideo(true);
                        } else {
                            AVChatManager.getInstance().muteLocalVideo(false);
                        }

                        ChatRoomMemberCache.getInstance().setRTSOpen(true);

                        videoListener.onAcceptConfirm();
                        updateControlUI();
                        updateVideoAudioUI();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void updateVideoAudioUI() {
        videoPermissionBtn.setBackgroundResource(!AVChatManager.getInstance().isLocalVideoMuted()
                ? R.drawable.chat_room_video_on_selector : R.drawable.chat_room_video_off_selector);
        audioPermissionBtn.setBackgroundResource(!AVChatManager.getInstance().isLocalAudioMuted()
                ? R.drawable.chat_room_audio_on_selector : R.drawable.chat_room_audio_off_selector);
    }

    // 全屏显示和最小化显示的切换
    private void switchFullScreen(Map<String, Object> ext) {
        if (ext.containsKey(MeetingConstant.FULL_SCREEN_TYPE)) {
            int fullScreenType = (int)ext.get(MeetingConstant.FULL_SCREEN_TYPE);
            if (fullScreenType == FullScreenType.CLOSE.getValue()) {
                cancelFullScreen();
                fullScreenImage.setVisibility(View.GONE);
            } else if (fullScreenType == FullScreenType.OPEN.getValue()) {
                doFullScreen();
                fullScreenImage.setVisibility(View.VISIBLE);
            }
        }
    }

    // 举手红点提醒
    public void onTabChange(boolean notify) {
        ReminderItem item = new ReminderItem(ReminderId.CONTACT);
        item.setIndicator(notify);
        ChatRoomTab tab = ChatRoomTab.fromReminderId(item.getId());
        if (tab != null) {
            tabs.updateTab(tab.tabIndex, item);
        }
    }

    // 更新成员权限缓存
    private void saveMemberPermission(List<String> accounts) {
        isPermissionInit = true;
        onPermissionChange(accounts);
    }

    /**
     * ************************************ 聊天室 ************************************
     */

    private void setupPager() {
        // 主播没有举手发言的tab
        adapter = new ChatRoomTabPagerAdapter(getFragmentManager(), activity, viewPager,
                ChatRoomTab.values().length);

        viewPager.setOffscreenPageLimit(adapter.getCacheCount());
        // page swtich animation
        viewPager.setPageTransformer(true, new FadeInOutPageTransformer());
        // ADAPTER
        viewPager.setAdapter(adapter);
        // TAKE OVER CHANGE
        viewPager.setOnPageChangeListener(this);
    }

    private void setupTabs() {
        tabs.setOnCustomTabListener(new PagerSlidingTabStrip.OnCustomTabListener() {
            @Override
            public int getTabLayoutResId(int position) {
                return R.layout.chat_room_tab_layout;
            }

            @Override
            public boolean screenAdaptation() {
                return true;
            }
        });
        tabs.setViewPager(viewPager);
        tabs.setOnTabClickListener(adapter);
        tabs.setOnTabDoubleTapListener(adapter);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        // TO TABS
        tabs.onPageScrolled(position, positionOffset, positionOffsetPixels);
        // TO ADAPTER
        adapter.onPageScrolled(position);
    }

    @Override
    public void onPageSelected(int position) {
        // TO TABS
        tabs.onPageSelected(position);

        selectPage(position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        // TO TABS
        tabs.onPageScrollStateChanged(state);

        scrollState = state;

        selectPage(viewPager.getCurrentItem());
    }

    private void selectPage(int page) {
        // TO PAGE
        if (scrollState == ViewPager.SCROLL_STATE_IDLE) {
            adapter.onPageSelected(viewPager.getCurrentItem());
        }
    }

    /*****************************
     * AVChatStateObserver
     *********************************/

    @Override
    public void onTakeSnapshotResult(String s, boolean b, String s1) {

    }

    @Override
    public void onConnectionTypeChanged(int i) {

    }

    @Override
    public void onAVRecordingCompletion(String s, String s1) {

    }

    @Override
    public void onAudioRecordingCompletion(String s) {

    }

    @Override
    public void onLowStorageSpaceWarning(long l) {

    }

    @Override
    public void onFirstVideoFrameAvailable(String s) {

    }

    @Override
    public void onVideoFpsReported(String s, int i) {

    }

    @Override
    public boolean onVideoFrameFilter(AVChatVideoFrame avChatVideoFrame, boolean b) {
        return false;
    }

    @Override
    public void onJoinedChannel(int i, String s, String s1) {
        LogUtil.d(TAG, "onJoinedChannel, res:" + i);
        if (i != AVChatResCode.JoinChannelCode.OK) {
            Toast.makeText(activity, "joined channel:" + i, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onLeaveChannel() {
        userJoinedList.remove(NimCache.getAccount());
    }

    @Override
    public void onUserJoined(String s) {
        userJoinedList.add(s);
        onMasterJoin(s);
        if (ChatRoomMemberCache.getInstance().hasPermission(roomId, s) && !s.equals(roomInfo.getCreator())) {
            onVideoOn(s);
        }
    }

    @Override
    public void onUserLeave(String s, int i) {
        // 用户离开频道，如果是有权限用户，移除下画布
        if (ChatRoomMemberCache.getInstance().hasPermission(roomId, s) && !s.equals(roomInfo.getCreator())) {
            onVideoOff(s);
        } else if (s.equals(roomInfo.getCreator())) {
            masterVideoLayout.removeAllViews();
        }
        ChatRoomMemberCache.getInstance().removePermissionMem(roomId, s);
        videoListener.onUserLeave(s);
        userJoinedList.remove(s);
    }

    @Override
    public void onProtocolIncompatible(int i) {

    }

    @Override
    public void onDisconnectServer() {

    }

    @Override
    public void onNetworkQuality(String s, int i, AVChatNetworkStats avChatNetworkStats) {

    }

    @Override
    public void onCallEstablished() {
        userJoinedList.add(NimCache.getAccount());
        onMasterJoin(NimCache.getAccount());
    }

    @Override
    public void onDeviceEvent(int i, String s) {

    }

    @Override
    public void onFirstVideoFrameRendered(String s) {

    }

    @Override
    public void onVideoFrameResolutionChanged(String s, int i, int i1, int i2) {

    }

    @Override
    public boolean onAudioFrameFilter(AVChatAudioFrame avChatAudioFrame) {
        return false;
    }

    @Override
    public void onAudioDeviceChanged(int i) {

    }

    @Override
    public void onReportSpeaker(Map<String, Integer> map, int i) {
        videoListener.onReportSpeaker(map);
    }

    @Override
    public void onAudioMixingEvent(int i) {

    }

    @Override
    public void onSessionStats(AVChatSessionStats avChatSessionStats) {

    }

    @Override
    public void onLiveEvent(int i) {

    }


    /**************************** AVChatStateObserver end ****************************/

    /***************************
     * 画布的显示和取消
     **********************************/

    // 主持人进入频道
    private void onMasterJoin(String s) {
        if (userJoinedList != null && userJoinedList.contains(s) && s.equals(roomInfo.getCreator())) {
            if (masterRender == null) {
                masterRender = new AVChatVideoRender(getActivity());
            }
            boolean isSetup = setupMasterRender(s, AVChatVideoScalingType.SCALE_ASPECT_BALANCED);
            if (isSetup && masterRender != null) {
                addIntoMasterPreviewLayout(masterRender);
                ChatRoomMemberCache.getInstance().savePermissionMemberbyId(roomId, roomInfo.getCreator());
                updateDeskShareUI();
            }
        }
    }

    private void updateDeskShareUI() {
        Map<String, Object> ext = roomInfo.getExtension();
        if (ext != null && ext.containsKey(MeetingConstant.FULL_SCREEN_TYPE)) {
            int fullScreenType = (int)ext.get(MeetingConstant.FULL_SCREEN_TYPE);
            if (fullScreenType == FullScreenType.CLOSE.getValue()) {
                fullScreenImage.setVisibility(View.GONE);
            } else if (fullScreenType == FullScreenType.OPEN.getValue()) {
                fullScreenImage.setVisibility(View.VISIBLE);
            }
        }
    }

    private boolean setupMasterRender(String s, int mode) {
        if (TextUtils.isEmpty(s)) {
            return false;
        }
        boolean isSetup = false;
        try {
            if (s.equals(NimCache.getAccount())) {
                // 设置本地用户视频画布
                isSetup = AVChatManager.getInstance().setupLocalVideoRender(masterRender, false, mode);
            } else {
                // 设置远端用户视频画布
                isSetup = AVChatManager.getInstance().setupRemoteVideoRender(s, masterRender, false, mode);
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "set up video render error:" + e.getMessage());
            e.printStackTrace();
        }
        return isSetup;
    }

    // 将主持人添加到主持人画布
    private void addIntoMasterPreviewLayout(SurfaceView surfaceView) {
        if (surfaceView.getParent() != null)
            ((ViewGroup) surfaceView.getParent()).removeView(surfaceView);
        masterVideoLayout.addView(surfaceView);
        surfaceView.setZOrderMediaOverlay(true);
    }

    private void doFullScreen() {
        cancelFullScreenImage.setVisibility(View.VISIBLE);
        fullScreenImage.setVisibility(View.GONE);
        fullScreenLayout.setVisibility(View.VISIBLE);
        if (masterRender == null) {
            masterRender = new AVChatVideoRender(getActivity());
        }
        if (masterRender.getParent() != null) {
            ((ViewGroup) masterRender.getParent()).removeView(masterRender);
        }
        setupMasterRender(roomInfo.getCreator(), AVChatVideoScalingType.SCALE_ASPECT_FIT);
        fullScreenView.addView(masterRender);
        masterRender.setZOrderMediaOverlay(true);
        removeViews();
    }

    private void cancelFullScreen() {
        fullScreenImage.setVisibility(View.VISIBLE);
        cancelFullScreenImage.setVisibility(View.GONE);
        fullScreenLayout.setVisibility(View.GONE);
        if (masterRender == null) {
            return;
        }
        if (masterRender.getParent() != null) {
            ((ViewGroup) masterRender.getParent()).removeView(masterRender);
        }
        setupMasterRender(roomInfo.getCreator(), AVChatVideoScalingType.SCALE_ASPECT_BALANCED);
        addIntoMasterPreviewLayout(masterRender);
        showView();
    }

    // 取消全屏显示共享桌面时，显示其他画面。
    private void showView() {
        Map<Integer, String> map = ChatRoomMemberCache.getInstance().getImageMap(roomId);
        if (map == null) {
            return;
        }
        for (Map.Entry<Integer, String> entry : map.entrySet()) {
            AVChatVideoRender render = new AVChatVideoRender(getActivity());
            if (NimCache.getAccount().equals(entry.getValue())) {
                AVChatManager.getInstance().setupLocalVideoRender(render, false, AVChatVideoScalingType.SCALE_ASPECT_BALANCED);
            } else {
                AVChatManager.getInstance().setupRemoteVideoRender(entry.getValue(), render, false, AVChatVideoScalingType.SCALE_ASPECT_BALANCED);
            }
            addIntoPreviewLayout(render, viewLayouts[entry.getKey()]);
        }
    }

    // 全屏显示共享桌面时，移除其他画面，否则会叠加显示
    private void removeViews() {
        for (int i = 0; i < viewLayouts.length; i++) {
            viewLayouts[i].removeAllViews();
        }
    }

    // 权限变化
    public void onPermissionChange(List<String> accounts) {
        List<String> oldAccounts = new ArrayList<>();
        if (ChatRoomMemberCache.getInstance().getPermissionMems(roomId) != null) {
            oldAccounts.addAll(ChatRoomMemberCache.getInstance().getPermissionMems(roomId));
        }
        // accounts是新的所有人权限。如果oldaccounts不在这个里面，就remove，在就add
        for (String a : oldAccounts) {
            if (a.equals(roomInfo.getCreator())) {
                continue;
            }
            if (accounts.contains(a)) {
                LogUtil.i(TAG, "on permission change, add:" + a);
                // 新增权限
                ChatRoomMemberCache.getInstance().savePermissionMemberbyId(roomId, a);
                onVideoOn(a);
            } else {
                LogUtil.i(TAG, "on permission change, remove:" + a);
                ChatRoomMemberCache.getInstance().removePermissionMem(roomId, a);
                onVideoOff(a);
                ChatRoomMemberCache.getInstance().setRTSOpen(false);
            }
        }

        accounts.removeAll(oldAccounts);
        for (String a : accounts) {
            ChatRoomMemberCache.getInstance().savePermissionMemberbyId(roomId, a);
            if (a.equals(roomInfo.getCreator())) {
                continue;
            }
            onVideoOn(a);
        }
    }

    // 将有权限的成员添加到画布
    public void onVideoOn(String account) {
        Map<Integer, String> imageMap = ChatRoomMemberCache.getInstance().getImageMap(roomId);
        if (imageMap == null) {
            imageMap = new HashMap<>();
        }

        showView(imageMap, account);

        ChatRoomMemberCache.getInstance().saveImageMap(roomId, imageMap);
    }

    // 显示成员图像
    private void showView(Map<Integer, String> imageMap, String a) {
        if (userJoinedList != null && userJoinedList.contains(a)
                && !roomInfo.getCreator().equals(a)
                && !imageMap.containsValue(a) && imageMap.size() < 3) {
            for (int i = 0; i < 3; i++) {
                if (!imageMap.containsKey(i)) {
                    AVChatVideoRender render = new AVChatVideoRender(getActivity());
                    boolean isSetup = false;
                    try {
                        if (NimCache.getAccount().equals(a)) {
                            isSetup = AVChatManager.getInstance().setupLocalVideoRender(render, false, AVChatVideoScalingType.SCALE_ASPECT_BALANCED);
                        } else {
                            isSetup = AVChatManager.getInstance().setupRemoteVideoRender(a, render, false, AVChatVideoScalingType.SCALE_ASPECT_BALANCED);
                        }
                        LogUtil.i(TAG, "setup render, creator account:" + roomInfo.getCreator() + ", render account:" + a + ", isSetup:" + isSetup);
                    } catch (Exception e) {
                        LogUtil.e(TAG, "set up video render error:" + e.getMessage());
                        e.printStackTrace();
                    }
                    if (isSetup && render != null) {
                        imageMap.put(i, a);
                        addIntoPreviewLayout(render, viewLayouts[i]);
                    }
                    break;
                }
            }
        }
    }

    // 添加到成员显示的画布
    private void addIntoPreviewLayout(SurfaceView surfaceView, ViewGroup viewLayout) {
        if (surfaceView == null) {
            return;
        }
        if (surfaceView.getParent() != null)
            ((ViewGroup) surfaceView.getParent()).removeView(surfaceView);
        viewLayout.addView(surfaceView);
        surfaceView.setZOrderMediaOverlay(true);
    }

    // 将被取消权限的成员从画布移除, 并将角色置为初始状态
    public void onVideoOff(String account) {
        Map<Integer, String> imageMap = ChatRoomMemberCache.getInstance().getImageMap(roomId);
        if (imageMap == null) {
            return;
        }
        removeView(imageMap, account);
        resetRole(account);
    }

    // 移除成员图像
    private void removeView(Map<Integer, String> imageMap, String account) {
        Iterator<Map.Entry<Integer, String>> it = imageMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, String> entry = it.next();
            if (entry.getValue().equals(account)) {
                viewLayouts[entry.getKey()].removeAllViews();
                it.remove();
                break;
            }
        }
    }

    // 恢复为观众角色
    private void resetRole(String account) {
        if (account.equals(NimCache.getAccount())) {
            AVChatManager.getInstance().muteLocalAudio(true);
            AVChatManager.getInstance().enableAudienceRole(true);
            AVChatManager.getInstance().muteLocalAudio(false);
            AVChatManager.getInstance().muteLocalVideo(false);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.video_permission_btn:
                if (ChatRoomMemberCache.getInstance().hasPermission(roomId, NimCache.getAccount())) {
                    setVideoState();
                }
                break;
            case R.id.audio_permission_btn:
                if (ChatRoomMemberCache.getInstance().hasPermission(roomId, NimCache.getAccount())) {
                    setAudioState();
                }
                break;
            case R.id.close_apply_btn:
                speakRequestCancel();
                switchHandsUpLayout();
                break;
            case R.id.full_screen_image:
                doFullScreen();
                break;
            case R.id.cancel_full_screen_image:
                cancelFullScreen();
                break;
        }
    }

    private void cancelInteractionConfirm() {
        EasyAlertDialogHelper.createOkCancelDiolag(activity, getString(R.string.operation_confirm),
                getString(R.string.exit_interaction), getString(R.string.exit), getString(R.string.cancel), true,
                new EasyAlertDialogHelper.OnDialogActionListener() {
                    @Override
                    public void doCancelAction() {

                    }

                    @Override
                    public void doOkAction() {
                        MsgHelper.getInstance().sendP2PCustomNotification(roomId, MeetingOptCommand.SPEAK_REQUEST_CANCEL.getValue(),
                                roomInfo.getCreator(), null);
                        ChatRoomMemberCache.getInstance().saveMyHandsUpDown(roomId, false);
                    }
                }).show();
    }

    // 设置自己的摄像头是否开启
    private void setVideoState() {
        if (AVChatManager.getInstance().isLocalVideoMuted()) {
            videoPermissionBtn.setBackgroundResource(R.drawable.chat_room_video_on_selector);
            AVChatManager.getInstance().muteLocalVideo(false);
        } else {
            videoPermissionBtn.setBackgroundResource(R.drawable.chat_room_video_off_selector);
            AVChatManager.getInstance().muteLocalVideo(true);
        }
    }

    // 设置自己的录音是否开启
    private void setAudioState() {
        if (AVChatManager.getInstance().isLocalAudioMuted()) {
            audioPermissionBtn.setBackgroundResource(R.drawable.chat_room_audio_on_selector);
            AVChatManager.getInstance().muteLocalAudio(false);
        } else {
            audioPermissionBtn.setBackgroundResource(R.drawable.chat_room_audio_off_selector);
            AVChatManager.getInstance().muteLocalAudio(true);
        }
    }

    private void speakRequestCancel() {
        if (ChatRoomMemberCache.getInstance().hasPermission(roomId, NimCache.getAccount())) {
            // 结束互动
            cancelInteractionConfirm();
        } else if (ChatRoomMemberCache.getInstance().isMyHandsUp(roomId)) {
            // 取消互动
            MsgHelper.getInstance().sendP2PCustomNotification(roomId, MeetingOptCommand.SPEAK_REQUEST_CANCEL.getValue(),
                    roomInfo.getCreator(), null);
            ChatRoomMemberCache.getInstance().saveMyHandsUpDown(roomId, false);
        } else {
            // 申请互动
            MsgHelper.getInstance().sendP2PCustomNotification(roomId, MeetingOptCommand.SPEAK_REQUEST.getValue(),
                    roomInfo.getCreator(), null);
            ChatRoomMemberCache.getInstance().saveMyHandsUpDown(roomId, true);
        }
    }

    // 举手布局/取消举手布局切换
    private void switchHandsUpLayout() {
        if (!ChatRoomMemberCache.getInstance().isMyHandsUp(roomId)) {
            // 没举手
            interactionStartCloseBtn.setText(R.string.interaction);
        } else if (ChatRoomMemberCache.getInstance().isMyHandsUp(roomId)
                && !ChatRoomMemberCache.getInstance().hasPermission(roomId, NimCache.getAccount())) {
            // 举手等待主播通过
            interactionStartCloseBtn.setText(R.string.cancel);
        } else if (ChatRoomMemberCache.getInstance().hasPermission(roomId, NimCache.getAccount())) {
            // 主播通过，进行互动
            interactionStartCloseBtn.setText(R.string.finish);
        }
    }
}
