package com.vitek.neteaselive.education.activity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.WindowManager;
import android.widget.Toast;

import com.netease.nimlib.sdk.AbortableFuture;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.Observer;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.ResponseCode;
import com.netease.nimlib.sdk.StatusCode;
import com.netease.nimlib.sdk.auth.AuthServiceObserver;
import com.netease.nimlib.sdk.chatroom.ChatRoomService;
import com.netease.nimlib.sdk.chatroom.ChatRoomServiceObserver;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomInfo;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomKickOutEvent;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMember;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomStatusChangeData;
import com.netease.nimlib.sdk.chatroom.model.EnterChatRoomData;
import com.netease.nimlib.sdk.chatroom.model.EnterChatRoomResultData;
import com.netease.nimlib.sdk.msg.model.IMMessage;
import com.netease.nimlib.sdk.rts.RTSCallback;
import com.netease.nimlib.sdk.rts.RTSChannelStateObserver;
import com.netease.nimlib.sdk.rts.RTSManager2;
import com.netease.nimlib.sdk.rts.constant.RTSTunnelType;
import com.netease.nimlib.sdk.rts.model.RTSData;
import com.netease.nimlib.sdk.rts.model.RTSTunData;
import com.vitek.neteaselive.NimCache;
import com.vitek.neteaselive.R;
import com.vitek.neteaselive.base.ui.TActivity;
import com.vitek.neteaselive.base.util.log.LogUtil;
import com.vitek.neteaselive.education.doodle.Transaction;
import com.vitek.neteaselive.education.doodle.TransactionCenter;
import com.vitek.neteaselive.education.fragment.ChatRoomFragment;
import com.vitek.neteaselive.education.fragment.ChatRoomMessageFragment;
import com.vitek.neteaselive.education.fragment.ChatRoomRTSFragment;
import com.vitek.neteaselive.education.fragment.OnlinePeopleFragment;
import com.vitek.neteaselive.education.helper.ChatRoomMemberCache;
import com.vitek.neteaselive.education.helper.VideoListener;
import com.vitek.neteaselive.education.util.Preferences;
import com.vitek.neteaselive.im.session.ModuleProxy;
import com.vitek.neteaselive.im.ui.dialog.DialogMaker;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by hzxuwen on 2016/2/29.
 */
public class ChatRoomActivity extends TActivity implements VideoListener {
    private static final String TAG = ChatRoomActivity.class.getSimpleName();

    private final static String EXTRA_ROOM_ID = "ROOM_ID";
    private final static String EXTRA_MODE = "EXTRA_MODE";

    private final static String KEY_SHARE_URL = "webUrl";

    /**
     * 聊天室基本信息
     */
    private String roomId;
    private ChatRoomInfo roomInfo;

    private boolean isCreate; // true 主持人模式，false 观众模式
    private String shareUrl; // 分享地址
    private String sessionId; // 多人白板sessionid
    private String sessionName;

    /**
     * 子页面
     */
    private ChatRoomMessageFragment messageFragment;
    private ChatRoomFragment fragment;
    private ChatRoomRTSFragment rtsFragment;
    private OnlinePeopleFragment onlinePeopleFragment;

    private AbortableFuture<EnterChatRoomResultData> enterRequest;

    private boolean isFirstComing = true; // 主播是否首次进入房间

    public static void start(Context context, String roomId, boolean isCreate) {
        Intent intent = new Intent();
        intent.setClass(context, ChatRoomActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(EXTRA_ROOM_ID, roomId);
        intent.putExtra(EXTRA_MODE, isCreate);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat_room_activity);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        parseIntent();

        // 注册监听
        registerObservers(true);

        // 登录聊天室
        enterRoom();
    }

    @Override
    protected void onDestroy() {
        endSession();
        registerObservers(false);
        if (!TextUtils.isEmpty(sessionName)) {
            LogUtil.i(TAG, "unregister rts observers");
            registerRTSObservers(sessionName, false);
        }

        if (fragment != null) {
            fragment.onKickOut();
        }

        super.onDestroy();
    }

    private void parseIntent() {
        roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        isCreate = getIntent().getBooleanExtra(EXTRA_MODE, false);
    }

    @Override
    public void onBackPressed() {
        if (messageFragment == null || !messageFragment.onBackPressed()) {
            if (fragment != null) {
                fragment.onBackPressed();
            }
        }
    }

    private void registerObservers(boolean register) {
        NIMClient.getService(ChatRoomServiceObserver.class).observeOnlineStatus(onlineStatus, register);
        NIMClient.getService(ChatRoomServiceObserver.class).observeKickOutEvent(kickOutObserver, register);
        NIMClient.getService(AuthServiceObserver.class).observeOnlineStatus(userStatusObserver, register);
    }

    Observer<StatusCode> userStatusObserver = new Observer<StatusCode>() {
        @Override
        public void onEvent(StatusCode statusCode) {
            if (statusCode.wontAutoLogin()) {
                NIMClient.getService(ChatRoomService.class).exitChatRoom(roomId);
                if(fragment != null) {
                    fragment.onKickOut();
                }
            }
        }
    };

    Observer<ChatRoomStatusChangeData> onlineStatus = new Observer<ChatRoomStatusChangeData>() {
        @Override
        public void onEvent(ChatRoomStatusChangeData chatRoomStatusChangeData) {
            if (chatRoomStatusChangeData.status == StatusCode.CONNECTING) {
                DialogMaker.updateLoadingMessage("连接中...");
            } else if (chatRoomStatusChangeData.status == StatusCode.UNLOGIN) {
                if(NIMClient.getService(ChatRoomService.class).getEnterErrorCode(roomId) == ResponseCode.RES_CHATROOM_STATUS_EXCEPTION) {
                    // 聊天室连接状态异常
                    Toast.makeText(ChatRoomActivity.this, R.string.chatroom_status_exception, Toast.LENGTH_SHORT).show();
                    NIMClient.getService(ChatRoomService.class).exitChatRoom(roomId);
                    if(fragment != null) {
                        fragment.onKickOut();
                    }
                } else {
                    Toast.makeText(ChatRoomActivity.this, R.string.nim_status_unlogin, Toast.LENGTH_SHORT).show();
                    if (fragment != null) {
                        fragment.onOnlineStatusChanged(false);
                    }
                }
            } else if (chatRoomStatusChangeData.status == StatusCode.LOGINING) {
                DialogMaker.updateLoadingMessage("登录中...");
            } else if (chatRoomStatusChangeData.status == StatusCode.LOGINED) {
                if (fragment != null) {
                    fragment.onOnlineStatusChanged(true);
                }
            } else if (chatRoomStatusChangeData.status.wontAutoLogin()) {
            } else if (chatRoomStatusChangeData.status == StatusCode.NET_BROKEN) {
                Toast.makeText(ChatRoomActivity.this, R.string.net_broken, Toast.LENGTH_SHORT).show();
                if (fragment != null) {
                    fragment.onOnlineStatusChanged(false);
                }
            }
            LogUtil.i(TAG, "Chat Room Online Status:" + chatRoomStatusChangeData.status.name());
        }
    };

    Observer<ChatRoomKickOutEvent> kickOutObserver = new Observer<ChatRoomKickOutEvent>() {
        @Override
        public void onEvent(ChatRoomKickOutEvent chatRoomKickOutEvent) {
            if (chatRoomKickOutEvent.getReason() == ChatRoomKickOutEvent.ChatRoomKickOutReason.CHAT_ROOM_INVALID) {
                if (!roomInfo.getCreator().equals(NimCache.getAccount()))
                    Toast.makeText(ChatRoomActivity.this, R.string.meeting_closed, Toast.LENGTH_SHORT).show();
            } else if (chatRoomKickOutEvent.getReason() == ChatRoomKickOutEvent.ChatRoomKickOutReason.KICK_OUT_BY_MANAGER) {
                Toast.makeText(ChatRoomActivity.this, R.string.kick_out_by_master, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ChatRoomActivity.this, "被踢出聊天室，reason:" + chatRoomKickOutEvent.getReason(), Toast.LENGTH_SHORT).show();
            }

            if (fragment != null) {
                fragment.onKickOut();
            }
        }
    };

    private void enterRoom() {
        DialogMaker.showProgressDialog(this, null, "", true, new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (enterRequest != null) {
                    enterRequest.abort();
                    onLoginDone();
                    finish();
                }
            }
        }).setCanceledOnTouchOutside(false);
        EnterChatRoomData data = new EnterChatRoomData(roomId);
        enterRequest = NIMClient.getService(ChatRoomService.class).enterChatRoom(data);
        enterRequest.setCallback(new RequestCallback<EnterChatRoomResultData>() {
            @Override
            public void onSuccess(EnterChatRoomResultData result) {
                onLoginDone();
                roomInfo = result.getRoomInfo();
                ChatRoomMember member = result.getMember();
                member.setRoomId(roomInfo.getRoomId());
                ChatRoomMemberCache.getInstance().saveMyMember(member);
                if (roomInfo.getCreator().equals(NimCache.getAccount())) {
                    isCreate = true;
                }
                if (roomInfo.getExtension() != null) {
                    shareUrl = (String) roomInfo.getExtension().get(KEY_SHARE_URL);
                }
                initChatRoomFragment(roomInfo.getName());
                initRTSFragment(roomInfo);
                initRTSSession();
                registerRTSObservers(roomInfo.getRoomId(), true);
            }

            @Override
            public void onFailed(int code) {
                onLoginDone();
                if (code == ResponseCode.RES_CHATROOM_BLACKLIST) {
                    Toast.makeText(ChatRoomActivity.this, "你已被拉入黑名单，不能再进入", Toast.LENGTH_SHORT).show();
                } else if (code == ResponseCode.RES_ENONEXIST){
                    Toast.makeText(ChatRoomActivity.this, "该聊天室不存在", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ChatRoomActivity.this, "enter chat room failed, code=" + code, Toast.LENGTH_SHORT).show();
                }
                finish();
            }

            @Override
            public void onException(Throwable exception) {
                onLoginDone();
                Toast.makeText(ChatRoomActivity.this, "enter chat room exception, e=" + exception.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void onLoginDone() {
        enterRequest = null;
        DialogMaker.dismissProgressDialog();
    }

    private void initChatRoomFragment(final String roomName) {
        fragment = (ChatRoomFragment) getSupportFragmentManager().findFragmentById(R.id.chat_rooms_fragment);
        if (fragment != null) {
            fragment.initLiveVideo(roomInfo, roomName, isCreate, shareUrl, new ModuleProxy() {
                @Override
                public boolean sendMessage(IMMessage msg) {
                    return false;
                }

                @Override
                public void onInputPanelExpand() {

                }

                @Override
                public void shouldCollapseInputPanel() {
                    if(messageFragment != null) {
                        messageFragment.shouldCollapseInputPanel();
                    }
                }

                @Override
                public boolean isLongClickEnabled() {
                    return false;
                }
            });
        } else {
            // 如果Fragment还未Create完成，延迟初始化
            getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    initChatRoomFragment(roomName);
                }
            }, 50);
        }
    }

    private void initRTSFragment(final ChatRoomInfo roomInfo) {
        rtsFragment = (ChatRoomRTSFragment) getSupportFragmentManager().findFragmentById(R.id.chat_room_rts_fragment);
        if (rtsFragment != null) {
            rtsFragment.initRTSView(roomInfo.getRoomId(), roomInfo);
        } else {
            getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    initRTSFragment(roomInfo);
                }
            }, 50);
        }
    }

    private void updateRTSFragment() {
        rtsFragment = (ChatRoomRTSFragment) getSupportFragmentManager().findFragmentById(R.id.chat_room_rts_fragment);
        if (rtsFragment != null) {
            rtsFragment.initView();
        } else {
            getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateRTSFragment();
                }
            }, 50);
        }
    }


    // 初始化多人白板
    private void initRTSSession() {
        // 主播创建并进入多人白板session。观众进入多人白板session
        if (roomInfo.getCreator().equals(NimCache.getAccount())) {
            RTSManager2.getInstance().createSession(roomInfo.getRoomId(), "test", new RTSCallback<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    Toast.makeText(ChatRoomActivity.this, "创建多人白板房间成功", Toast.LENGTH_SHORT).show();
                    joinRTSSession();
                }

                @Override
                public void onFailed(int i) {
                    if (i == 417) {
                        Toast.makeText(ChatRoomActivity.this, "多人白板房间已经被预定", Toast.LENGTH_SHORT).show();
                        joinRTSSession();
                    }
                    LogUtil.d(TAG, "create rts session failed, code:" + i);
                }

                @Override
                public void onException(Throwable throwable) {

                }
            });
        } else {
            joinRTSSession();
        }
    }

    // 加入多人白板session
    private void joinRTSSession() {
        boolean isOpen = Preferences.getRTSRecord();
        LogUtil.i(TAG, "rts record is open->" + isOpen);
        RTSManager2.getInstance().joinSession(roomInfo.getRoomId(), isOpen, new RTSCallback<RTSData>() {
            @Override
            public void onSuccess(RTSData rtsData) {
                LogUtil.i(TAG, "rts extra:" + rtsData.getExtra());
                // 主播的白板默认为开启状态
                if (roomInfo.getCreator().equals(NimCache.getAccount())) {
                    ChatRoomMemberCache.getInstance().setRTSOpen(true);
                    updateRTSFragment();
                }
                Toast.makeText(ChatRoomActivity.this, "加入多人白板房间成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailed(int i) {
                LogUtil.d(TAG, "join rts session failed, code:" + i);
                Toast.makeText(ChatRoomActivity.this, "join rts session failed, code:" + i, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onException(Throwable throwable) {

            }
        });

        sessionId = roomInfo.getRoomId();
    }

    private void registerRTSObservers(String sessionName, boolean register) {
        this.sessionName = sessionName;
        RTSManager2.getInstance().observeChannelState(sessionName, channelStateObserver, register);
        RTSManager2.getInstance().observeReceiveData(sessionName, receiveDataObserver, register);
    }


    private void endSession() {
        RTSManager2.getInstance().leaveSession(sessionName, new RTSCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                LogUtil.i(TAG, "leave session success");
            }

            @Override
            public void onFailed(int code) {
                LogUtil.i(TAG, "leave session failed, code:" + code);
            }

            @Override
            public void onException(Throwable exception) {

            }
        });
    }

    /**
     * 监听当前会话的状态
     */
    private RTSChannelStateObserver channelStateObserver = new RTSChannelStateObserver() {

        @Override
        public void onConnectResult(String localSessionId, RTSTunnelType tunType, long channelId, int code, String recordFile) {
            Toast.makeText(ChatRoomActivity.this, "onConnectResult, tunType=" + tunType.toString() +
                    ", channelId=" + channelId +
                    ", code=" + code, Toast.LENGTH_SHORT).show();
            if (code != 200) {
                RTSManager2.getInstance().leaveSession(sessionId, null);
                return;
            }

            // 主播进入，
            // 1、第一次进入，或者异常退出发送白板清空指令。
            // 2、网络变化，发送主播的同步数据。
            List<Transaction> cache = new ArrayList<>(1);
            if (roomInfo.getCreator().equals(NimCache.getAccount())) {
                if (isFirstComing) {
                    isFirstComing = false;
                    cache.add(new Transaction().makeClearSelfTransaction());
                    cache.add(new Transaction().makeFlipTranscation("", 0, 0, 1));
                    TransactionCenter.getInstance().sendToRemote(sessionId, null, cache);
                } else {
                    TransactionCenter.getInstance().onNetWorkChange(sessionId, true);
                }
            } else {
                // 非主播进入房间，发送同步请求，请求主播向他同步之前的白板笔记
                Toast.makeText(ChatRoomActivity.this, "send sync request", Toast.LENGTH_SHORT).show();
                TransactionCenter.getInstance().onNetWorkChange(sessionId, false);
                cache.add(new Transaction().makeSyncRequestTransaction());
                TransactionCenter.getInstance().sendToRemote(sessionId, roomInfo.getCreator(), cache);
            }
        }

        @Override
        public void onChannelEstablished(String sessionId, RTSTunnelType tunType) {
            Toast.makeText(ChatRoomActivity.this, "onCallEstablished,tunType=" + tunType.toString(), Toast
                    .LENGTH_SHORT).show();
        }

        @Override
        public void onUserJoin(String sessionId, RTSTunnelType tunType, String account) {
            LogUtil.i(TAG, "On User Join, account:" + account);
        }

        @Override
        public void onUserLeave(String sessionId, RTSTunnelType tunType, String account, int event) {
            LogUtil.i(TAG, "On User Leave, account:" + account);
        }

        @Override
        public void onDisconnectServer(String sessionId, RTSTunnelType tunType) {
            Toast.makeText(ChatRoomActivity.this, "onDisconnectServer, tunType=" + tunType.toString(), Toast
                    .LENGTH_SHORT).show();
            if (tunType == RTSTunnelType.DATA) {
                // 如果数据通道断了，那么关闭会话
                Toast.makeText(ChatRoomActivity.this, "TCP通道断开，自动结束会话", Toast.LENGTH_SHORT).show();
                RTSManager2.getInstance().leaveSession(sessionId, null);
            } else if (tunType == RTSTunnelType.AUDIO) {
            }
        }

        @Override
        public void onError(String sessionId, RTSTunnelType tunType, int code) {
            Toast.makeText(ChatRoomActivity.this, "onError, tunType=" + tunType.toString() + ", error=" + code,
                    Toast.LENGTH_LONG).show();
        }

        @Override
        public void onNetworkStatusChange(String sessionId, RTSTunnelType tunType, int value) {
            // 网络信号强弱
            LogUtil.i(TAG, "network status:" + value);
        }
    };

    /**
     * 监听收到对方发送的通道数据
     */
    private Observer<RTSTunData> receiveDataObserver = new Observer<RTSTunData>() {
        @Override
        public void onEvent(RTSTunData rtsTunData) {
            LogUtil.i(TAG, "receive data");
            String data = "[parse bytes error]";
            try {
                data = new String(rtsTunData.getData(), 0, rtsTunData.getLength(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            TransactionCenter.getInstance().onReceive(sessionId, rtsTunData.getAccount(), data);
        }
    };

    public ChatRoomInfo getRoomInfo() {
        return roomInfo;
    }

    public void setRoomInfo(ChatRoomInfo roomInfo) {
        this.roomInfo = roomInfo;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (messageFragment != null) {
            messageFragment.onActivityResult(requestCode, resultCode, data);
        }

        if (rtsFragment != null) {
            rtsFragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onVideoOn(String account) {
        if (fragment != null) {
            fragment.onVideoOn(account);
        }
    }

    @Override
    public void onVideoOff(String account) {
        if (fragment != null) {
            fragment.onVideoOff(account);
        }
    }

    @Override
    public void onTabChange(boolean notify) {
        if (fragment != null) {
            fragment.onTabChange(notify);
        }
    }

    @Override
    public void onKickOutSuccess(String account) {

    }

    @Override
    public void onUserLeave(String account) {

    }

    @Override
    public void onReportSpeaker(Map<String, Integer> map) {
        if (onlinePeopleFragment == null) {
            onlinePeopleFragment = (OnlinePeopleFragment) getSupportFragmentManager().findFragmentById(R.id.online_people_fragment);
        }

        if (onlinePeopleFragment != null) {
            onlinePeopleFragment.onReportSpeaker(map);
        }
    }

    @Override
    public void onAcceptConfirm() {
        if (onlinePeopleFragment == null) {
            onlinePeopleFragment = (OnlinePeopleFragment) getSupportFragmentManager().findFragmentById(R.id.online_people_fragment);
        }

        if (onlinePeopleFragment != null) {
            onlinePeopleFragment.onAcceptConfirm();
        }

        if (rtsFragment == null) {
            rtsFragment = (ChatRoomRTSFragment) getSupportFragmentManager().findFragmentById(R.id.chat_room_rts_fragment);
        }

        if (rtsFragment != null) {
            rtsFragment.onAcceptConfirm();
        }
    }

    public boolean isCreate() {
        return isCreate;
    }

    public String getSessionId() {
        return sessionId;
    }
}
