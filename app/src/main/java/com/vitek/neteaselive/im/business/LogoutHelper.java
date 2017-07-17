package com.vitek.neteaselive.im.business;

import android.app.Activity;
import android.content.Context;

import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.auth.AuthService;
import com.vitek.neteaselive.NimCache;
import com.vitek.neteaselive.im.activity.LoginActivity;
import com.vitek.neteaselive.im.config.AuthPreferences;
import com.vitek.neteaselive.inject.FlavorDependent;

/**
 * 注销帮助类
 * Created by huangjun on 2015/10/8.
 */
public class LogoutHelper {
    public static void logout(Context context, boolean isKickOut) {
        AuthPreferences.saveUserToken("");
        // 清理缓存&注销监听&清除状态
        NimCache.getImageLoaderKit().clear();
        // flavor do logout
        FlavorDependent.getInstance().onLogout();
        NimCache.clear();

        NIMClient.getService(AuthService.class).logout();

        // 启动登录
        LoginActivity.start(context, isKickOut);
        ((Activity)context).finish();
    }
}
