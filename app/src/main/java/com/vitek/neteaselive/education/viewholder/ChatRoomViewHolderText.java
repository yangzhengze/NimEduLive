package com.vitek.neteaselive.education.viewholder;

import android.graphics.Color;
import android.text.method.LinkMovementMethod;
import android.text.style.ImageSpan;
import android.widget.TextView;

import com.netease.nimlib.sdk.chatroom.model.ChatRoomMessage;
import com.vitek.neteaselive.NimCache;
import com.vitek.neteaselive.R;
import com.vitek.neteaselive.base.util.ScreenUtil;
import com.vitek.neteaselive.im.session.emoji.MoonUtil;
import com.vitek.neteaselive.im.session.viewholder.MsgViewHolderText;

/**
 * Created by hzxuwen on 2016/1/18.
 */
public class ChatRoomViewHolderText extends MsgViewHolderText {
    @Override
    protected boolean isShowBubble() {
        return false;
    }

    @Override
    protected boolean isShowHeadImage() {
        return false;
    }

    @Override
    public void setNameTextView() {
        nameContainer.setPadding(ScreenUtil.dip2px(6), 0, 0, 0);
        ChatRoomViewHolderHelper.setNameTextView((ChatRoomMessage) message, nameTextView, nameIconView, context);
    }

    @Override
    protected void bindContentView() {
        TextView bodyTextView = findViewById(R.id.nim_message_item_text_body);
        bodyTextView.setTextColor(Color.BLACK);
        layoutDirection();
        MoonUtil.identifyFaceExpression(NimCache.getContext(), bodyTextView, getDisplayText(), ImageSpan.ALIGN_BOTTOM);
        bodyTextView.setMovementMethod(LinkMovementMethod.getInstance());
        bodyTextView.setOnLongClickListener(longClickListener);
    }

    private void layoutDirection() {
        TextView bodyTextView = findViewById(R.id.nim_message_item_text_body);
        bodyTextView.setPadding(ScreenUtil.dip2px(6), 0, 0, 0);
    }
}
