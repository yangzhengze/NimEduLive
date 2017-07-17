package com.vitek.neteaselive.education.activity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.Observer;
import com.netease.nimlib.sdk.StatusCode;
import com.netease.nimlib.sdk.auth.AuthServiceObserver;
import com.vitek.neteaselive.R;
import com.vitek.neteaselive.base.ui.TActivity;
import com.vitek.neteaselive.im.business.LogoutHelper;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;


/**
 * Created by hzxuwen on 2016/4/19.
 */
public class IdentifyActivity extends TActivity {
    @Bind(R.id.create_room_btn)
    Button teacherBtn;
    @Bind(R.id.search_room_btn)
    Button studentBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.identify_activity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.demo_name);
        toolbar.setLogo(R.drawable.actionbar_logo_white);
        setSupportActionBar(toolbar);

        ButterKnife.bind(this);

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
                LogoutHelper.logout(IdentifyActivity.this, true);
            }
        }
    };

    @OnClick({R.id.create_room_btn, R.id.search_room_btn})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.create_room_btn:
                EnterRoomActivity.startActivity(IdentifyActivity.this, true);
                break;
            case R.id.search_room_btn:
                EnterRoomActivity.startActivity(IdentifyActivity.this, false);
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_education, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_logout:
                LogoutHelper.logout(IdentifyActivity.this, false);
                break;
            case R.id.action_about:
                startActivity(new Intent(IdentifyActivity.this, AboutActivity.class));
                break;
            case R.id.action_setting:
                startActivity(new Intent(IdentifyActivity.this, SettingActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
