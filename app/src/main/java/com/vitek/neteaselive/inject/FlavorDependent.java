package com.vitek.neteaselive.inject;

import android.app.Activity;

import com.netease.nimlib.sdk.msg.attachment.MsgAttachmentParser;
import com.vitek.neteaselive.education.activity.IdentifyActivity;
import com.vitek.neteaselive.education.helper.ChatRoomHelper;
import com.vitek.neteaselive.education.module.custom.CustomAttachParser;

/**
 * Created by huangjun on 2016/3/15.
 */
public class FlavorDependent implements IFlavorDependent{

    @Override
    public String getFlavorName() {
        return "education";
    }

    @Override
    public Class<? extends Activity> getMainClass() {
        return IdentifyActivity.class;
    }

    @Override
    public MsgAttachmentParser getMsgAttachmentParser() {
        return new CustomAttachParser();
    }

    @Override
    public void onLogout() {
        ChatRoomHelper.logout();
    }

    public static FlavorDependent getInstance() {
        return InstanceHolder.instance;
    }

    public static class InstanceHolder {
        public final static FlavorDependent instance = new FlavorDependent();
    }

    @Override
    public void onApplicationCreate() {
        ChatRoomHelper.init();
    }
}
