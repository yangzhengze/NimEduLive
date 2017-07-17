package com.vitek.neteaselive.education.activity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.Observer;
import com.netease.nimlib.sdk.StatusCode;
import com.netease.nimlib.sdk.auth.AuthServiceObserver;
import com.netease.nimlib.sdk.avchat.AVChatCallback;
import com.netease.nimlib.sdk.avchat.AVChatManager;
import com.netease.nimlib.sdk.avchat.model.AVChatChannelInfo;
import com.vitek.neteaselive.NimCache;
import com.vitek.neteaselive.R;
import com.vitek.neteaselive.base.ui.TActivity;
import com.vitek.neteaselive.base.util.NetworkUtil;
import com.vitek.neteaselive.education.module.ChatRoomHttpClient;
import com.vitek.neteaselive.im.business.LogoutHelper;
import com.vitek.neteaselive.im.ui.dialog.DialogMaker;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by hzxuwen on 2016/4/19.
 */
public class EnterRoomActivity extends TActivity {
    private static final String EXTRA_DATA = "EXTRA_DATA";
    private static final int PERMISSION_REQUEST_CODE = 10;
    private static final String TAG = EnterRoomActivity.class.getSimpleName();
    @Bind(R.id.room_tip)
    TextView roomTip;
    @Bind(R.id.room_edit)
    EditText roomEdit;
    @Bind(R.id.done)
    TextView done;

    private boolean isCreate = false; // 主持人创建房间 true，其他成员进入房间 false

    public static final void startActivity(Context context, boolean isCreate) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_DATA, isCreate);
        intent.setClass(context, EnterRoomActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.enter_room_activity);
        ButterKnife.bind(this);

        isCreate = getIntent().getBooleanExtra(EXTRA_DATA, false);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (isCreate) {
            roomTip.setText(R.string.room_name);
            toolbar.setTitle(R.string.create_room);
        } else {
            roomTip.setText(R.string.enter_room_id);
            toolbar.setTitle(R.string.search_room);
        }
        toolbar.setLogo(R.drawable.actionbar_logo_white);
        setSupportActionBar(toolbar);

        registerObservers(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        registerObservers(false);
    }

    private void registerObservers(boolean register) {
        NIMClient.getService(AuthServiceObserver.class).observeOnlineStatus(userStatusObserver, register);
    }

    Observer<StatusCode> userStatusObserver = new Observer<StatusCode>() {
        @Override
        public void onEvent(StatusCode statusCode) {
            if (statusCode.wontAutoLogin()) {
                LogoutHelper.logout(EnterRoomActivity.this, true);
            }
        }
    };

    @OnClick(R.id.done)
    public void onClick() {
        if (!NetworkUtil.isNetAvailable(EnterRoomActivity.this)) {
            Toast.makeText(EnterRoomActivity.this, R.string.network_is_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(roomEdit.getText().toString().trim())) {
            Toast.makeText(this, "不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isCreate && !roomEdit.getText().toString().matches("[0-9]+")) {
            Toast.makeText(this, "房间号为数字", Toast.LENGTH_SHORT).show();
            return;
        }

        if(checkPermission()) {
            createOrEnterRoom();
        } else {
            requestPermissions();
        }

    }

    // 创建房间
    private void createRoom() {
        ChatRoomHttpClient.getInstance().createRoom(NimCache.getAccount(), roomEdit.getText().toString(), new ChatRoomHttpClient.ChatRoomHttpCallback<String>() {
            @Override
            public void onSuccess(String s) {
                createChannel(s);
            }

            @Override
            public void onFailed(int code, String errorMsg) {
                DialogMaker.dismissProgressDialog();
                Toast.makeText(EnterRoomActivity.this, "创建房间失败, code:" + code + ", errorMsg:" + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 创建会议频道
     */
    private void createChannel(final String roomId) {
        AVChatManager.getInstance().createRoom(roomId, "avchat test", new AVChatCallback<AVChatChannelInfo>() {
            @Override
            public void onSuccess(AVChatChannelInfo avChatChannelInfo) {
                DialogMaker.dismissProgressDialog();
                ChatRoomActivity.start(EnterRoomActivity.this, roomId, true);
                finish();
            }

            @Override
            public void onFailed(int i) {
                DialogMaker.dismissProgressDialog();
                Toast.makeText(EnterRoomActivity.this, "创建频道失败, code:" + i, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onException(Throwable throwable) {
                DialogMaker.dismissProgressDialog();
            }
        });
    }

    // 进入房间
    private void enterRoom() {
        ChatRoomActivity.start(EnterRoomActivity.this, roomEdit.getText().toString(), false);
        finish();
    }

    private void requestPermissions() {
        final List<String> missed = AVChatManager.checkPermission(this);
        List<String> showRationale = new ArrayList<>();
        for(String permission : missed) {
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                showRationale.add(permission);
            }
        }

        if(showRationale.size() > 0) {
            new AlertDialog.Builder(this)
                    .setMessage("You need to allow some permission")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(EnterRoomActivity.this, missed.toArray(new String[missed.size()]),
                                    PERMISSION_REQUEST_CODE);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show();
        } else {
            ActivityCompat.requestPermissions(this, missed.toArray(new String[missed.size()]), PERMISSION_REQUEST_CODE);
        }
    }

    //检查所有的权限
    private boolean checkPermission() {
        final List<String> missed = AVChatManager.checkPermission(this);
        if(missed.size() == 0) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if(AVChatManager.checkPermission(this).size() == 0) {
                    createOrEnterRoom();
                } else {
                    Toast.makeText(EnterRoomActivity.this, "Some Permission is Denied", Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

    }

    private void createOrEnterRoom() {
        DialogMaker.showProgressDialog(this, "", false);
        if (isCreate) {
            createRoom();
        } else {
            enterRoom();
        }
    }
}
