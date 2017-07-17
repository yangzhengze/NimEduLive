package com.vitek.neteaselive;

import android.content.Context;

import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.uinfo.UserService;
import com.netease.nimlib.sdk.uinfo.model.NimUserInfo;
import com.vitek.neteaselive.im.session.image.ImageLoaderKit;

/**
 * Created by huangjun on 2/25/16.
 */
public class NimCache {

    private static Context context;

    private static String account;

    private static NimUserInfo userInfo;

    // 图片加载、缓存与管理组件
    private static ImageLoaderKit imageLoaderKit;

    public static void clear() {
        account = null;
        userInfo = null;
    }

    public static String getAccount() {
        return account;
    }

    public static void setAccount(String account) {
        NimCache.account = account;
    }

    public static Context getContext() {
        return context;
    }

    public static void setContext(Context context) {
        NimCache.context = context.getApplicationContext();
    }

    public static NimUserInfo getUserInfo() {
        if (userInfo == null) {
            userInfo = NIMClient.getService(UserService.class).getUserInfo(account);
        }

        return userInfo;
    }

    public static ImageLoaderKit getImageLoaderKit() {
        return imageLoaderKit;
    }

    public static void initImageLoaderKit() {
        imageLoaderKit = new ImageLoaderKit(context, null);
    }
}
