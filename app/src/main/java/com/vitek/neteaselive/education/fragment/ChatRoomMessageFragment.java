package com.vitek.neteaselive.education.fragment;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.ResponseCode;
import com.netease.nimlib.sdk.chatroom.ChatRoomService;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomInfo;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMember;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMessage;
import com.netease.nimlib.sdk.msg.constant.SessionTypeEnum;
import com.netease.nimlib.sdk.msg.model.IMMessage;
import com.vitek.neteaselive.NimCache;
import com.vitek.neteaselive.R;
import com.vitek.neteaselive.base.ui.TFragment;
import com.vitek.neteaselive.education.activity.ChatRoomActivity;
import com.vitek.neteaselive.education.activity.InputActivity;
import com.vitek.neteaselive.education.helper.ChatRoomMemberCache;
import com.vitek.neteaselive.education.helper.VideoListener;
import com.vitek.neteaselive.education.module.ChatRoomMsgListPanel;
import com.vitek.neteaselive.education.module.actions.GuessAction;
import com.vitek.neteaselive.im.session.Container;
import com.vitek.neteaselive.im.session.ModuleProxy;
import com.vitek.neteaselive.im.session.actions.BaseAction;
import com.vitek.neteaselive.im.session.emoji.MoonUtil;
import com.vitek.neteaselive.im.session.input.InputConfig;
import com.vitek.neteaselive.im.session.input.InputPanel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by hzxuwen on 2016/2/29.
 */
public class ChatRoomMessageFragment extends TFragment implements ModuleProxy {

    private static final String TAG = ChatRoomMessageFragment.class.getSimpleName();
    private String roomId;
    private String creator;
    private Context context;

    // modules
    private View rootView;
    private InputPanel inputPanel;
    private ChatRoomMsgListPanel messageListPanel;
    private EditText messageEditText;
    private InputConfig inputConfig = new InputConfig(false, true, true);
    private VideoListener videoListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.chat_room_message_fragment, container, false);
        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ChatRoomInfo roomInfo = ((ChatRoomActivity) getActivity()).getRoomInfo();
        this.roomId = roomInfo.getRoomId();
        this.creator = roomInfo.getCreator();
        init(getContext(), roomId, creator);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        videoListener = (VideoListener) context;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (inputPanel != null) {
            inputPanel.onPause();
        }
        if (messageListPanel != null) {
            messageListPanel.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (messageListPanel != null) {
            messageListPanel.onResume();
        }
    }

    public void init(Context context, String roomId, String creator) {
        this.context = context;
        this.roomId = roomId;
        this.creator = creator;
        registerObservers(true);
        findViews();
    }

    public boolean onBackPressed() {
        if (inputPanel != null && inputPanel.collapse(true)) {
            return true;
        }

        if (messageListPanel != null && messageListPanel.onBackPressed()) {
            return true;
        }
        return false;
    }

    public void onLeave() {
        if (inputPanel != null) {
            inputPanel.collapse(false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        registerObservers(false);

        if (messageListPanel != null) {
            messageListPanel.onDestroy();
        }

        inputPanel = null;
    }

    private void findViews() {
        Container container = new Container((Activity) context, roomId, SessionTypeEnum.ChatRoom, this);
        if (messageListPanel == null) {
            messageListPanel = new ChatRoomMsgListPanel(container, rootView);
        }

        if (inputPanel == null) {
            inputPanel = new InputPanel(container, rootView, getActionList(), inputConfig);
        } else {
            inputPanel.reload(container, inputConfig);
        }

        messageEditText = findView(R.id.editTextMessage);
        messageEditText.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    inputPanel.collapse(true);
                    //inputPanel.hide();
                    startInputActivity();
                }
                return false;
            }
        });
    }

    private void registerObservers(boolean register) {
        ChatRoomMemberCache.getInstance().registerRoomMsgObserver(roomMsgObserver, register);
    }

    ChatRoomMemberCache.RoomMsgObserver roomMsgObserver = new ChatRoomMemberCache.RoomMsgObserver() {
        @Override
        public void onMsgIncoming(List<ChatRoomMessage> messages) {
            messageListPanel.onIncomingMessage(messages);
        }
    };

    /************************** Module proxy ***************************/

    @Override
    public boolean sendMessage(IMMessage msg) {
        ChatRoomMessage message = (ChatRoomMessage) msg;

        Map<String, Object> ext = new HashMap<>();
        ChatRoomMember chatRoomMember = ChatRoomMemberCache.getInstance().getChatRoomMember(roomId, NimCache.getAccount());
        if (chatRoomMember != null && chatRoomMember.getMemberType() != null) {
            ext.put("type", chatRoomMember.getMemberType().getValue());
            message.setRemoteExtension(ext);
        }

        NIMClient.getService(ChatRoomService.class).sendMessage(message, false)
                .setCallback(new RequestCallback<Void>() {
                    @Override
                    public void onSuccess(Void param) {
                    }

                    @Override
                    public void onFailed(int code) {
                        if (code == ResponseCode.RES_CHATROOM_MUTED) {
                            Toast.makeText(NimCache.getContext(), "用户被禁言", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(NimCache.getContext(), "消息发送失败：code:" + code, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onException(Throwable exception) {
                        Toast.makeText(NimCache.getContext(), "消息发送失败！", Toast.LENGTH_SHORT).show();
                    }
                });
        messageListPanel.onMsgSend(msg);
        return true;
    }

    @Override
    public void onInputPanelExpand() {
        messageListPanel.scrollToBottom();
    }

    @Override
    public void shouldCollapseInputPanel() {
        inputPanel.collapse(false);
    }

    @Override
    public boolean isLongClickEnabled() {
        return !inputPanel.isRecording();
    }

    // 操作面板集合
    protected List<BaseAction> getActionList() {
        List<BaseAction> actions = new ArrayList<>();
        actions.add(new GuessAction());
        return actions;
    }

    /**
     * ***************************** 部分机型键盘弹出会造成布局挤压的解决方案 ***********************************
     */

    private void startInputActivity() {
        InputActivity.startActivityForResult(context, messageEditText.getText().toString(),
                inputConfig, new InputActivity.InputActivityProxy() {
                    @Override
                    public void onSendMessage(String text) {
                        inputPanel.onTextMessageSendButtonPressed(text);
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && requestCode == InputActivity.REQ_CODE) {
            // 设置EditText显示的内容
            String text = data.getStringExtra(InputActivity.EXTRA_TEXT);
            MoonUtil.identifyFaceExpression(NimCache.getContext(), messageEditText, text, ImageSpan.ALIGN_BOTTOM);
            messageEditText.setSelection(text.length());

            // 根据mode显示表情布局或者键盘布局
            int mode = data.getIntExtra(InputActivity.EXTRA_MODE, InputActivity.MODE_KEYBOARD_COLLAPSE);
            if (mode == InputActivity.MODE_SHOW_EMOJI) {
                inputPanel.toggleEmojiLayout();
            } else if (mode == InputActivity.MODE_SHOW_MORE_FUNC) {
                inputPanel.toggleActionPanelLayout();
            }

            //inputPanel.show();
        }
    }
}
