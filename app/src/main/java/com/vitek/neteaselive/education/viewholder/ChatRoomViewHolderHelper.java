package com.vitek.neteaselive.education.viewholder;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.netease.nimlib.sdk.chatroom.constant.MemberType;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMessage;
import com.netease.nimlib.sdk.msg.constant.MsgTypeEnum;
import com.vitek.neteaselive.R;
import com.vitek.neteaselive.education.helper.ChatRoomHelper;

import java.util.Map;

/**
 * 聊天室成员姓名
 * Created by hzxuwen on 2016/1/20.
 */
public class ChatRoomViewHolderHelper {

    public static void setNameTextView(ChatRoomMessage message, TextView text, ImageView imageView, Context context) {
        if (message.getMsgType() != MsgTypeEnum.notification) {
            // 聊天室中显示姓名
            text.setText(ChatRoomHelper.showDisplayName(message));

            text.setTextColor(context.getResources().getColor(R.color.color_black_ff999999));
            text.setVisibility(View.VISIBLE);
            setNameIconView(message, imageView);
        }
    }

    private static void setNameIconView(ChatRoomMessage message, ImageView nameIconView) {
        final String KEY = "type";
        Map<String, Object> ext = message.getRemoteExtension();
        if (ext == null || !ext.containsKey(KEY)) {
            nameIconView.setVisibility(View.GONE);
            return;
        }

        MemberType type = MemberType.typeOfValue((Integer) ext.get(KEY));
        if (type == MemberType.ADMIN) {
            nameIconView.setVisibility(View.VISIBLE);
        } else if (type == MemberType.CREATOR) {
            nameIconView.setVisibility(View.VISIBLE);
        } else {
            nameIconView.setVisibility(View.GONE);
        }
    }
}
