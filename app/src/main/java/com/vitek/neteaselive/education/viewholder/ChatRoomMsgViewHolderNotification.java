package com.vitek.neteaselive.education.viewholder;

import android.widget.TextView;

import com.netease.nimlib.sdk.chatroom.model.ChatRoomNotificationAttachment;
import com.vitek.neteaselive.R;
import com.vitek.neteaselive.education.helper.ChatRoomNotificationHelper;
import com.vitek.neteaselive.im.session.viewholder.MsgViewHolderBase;

public class ChatRoomMsgViewHolderNotification extends MsgViewHolderBase {

    protected TextView notificationTextView;

    @Override
    protected int getContentResId() {
        return R.layout.nim_message_item_notification;
    }

    @Override
    protected void inflateContentView() {
        notificationTextView = (TextView) view.findViewById(R.id.message_item_notification_label);
    }

    @Override
    protected void bindContentView() {
        notificationTextView.setText(ChatRoomNotificationHelper.getNotificationText((ChatRoomNotificationAttachment) message.getAttachment()));
    }

    @Override
    protected boolean isMiddleItem() {
        return true;
    }
}

