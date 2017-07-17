package com.vitek.neteaselive.education.activity;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.netease.nimlib.sdk.avchat.constant.AVChatAudioEffectMode;
import com.vitek.neteaselive.R;
import com.vitek.neteaselive.base.ui.TActivity;
import com.vitek.neteaselive.education.util.Preferences;

import java.util.HashMap;
import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by hzxuwen on 2016/11/18.
 */

public class SettingActivity extends TActivity {

    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.rts_record_open_radio)
    RadioButton rtsRecordOpenRadio;
    @Bind(R.id.rts_record_close_radio)
    RadioButton rtsRecordCloseRadio;
    @Bind(R.id.rts_record_group)
    RadioGroup rtsRecordGroup;
    @Bind(R.id.platform_builtin_radio)
    RadioButton platformBuiltinRadio;
    @Bind(R.id.sdk_builtin_radio)
    RadioButton sdkBuiltinRadio;
    @Bind(R.id.disable_audio_effect_radio)
    RadioButton disableAudioEffectRadio;
    @Bind(R.id.audio_effect_group)
    RadioGroup audioEffectGroup;

    // data
    private Map<Integer, String> audioEffectMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.setting_layout);
        ButterKnife.bind(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setLogo(R.drawable.actionbar_logo_white);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.actionbar_white_back_icon);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        updateRTSSwitch();
        initAudioEffect();

        rtsRecordGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rts_record_open_radio) {
                    Preferences.saveRTSRecord(true);
                } else if (checkedId == R.id.rts_record_close_radio) {
                    Preferences.saveRTSRecord(false);
                }
            }
        });

        audioEffectGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Preferences.saveAudioEffectMode(audioEffectMap.get(checkedId));
            }
        });
    }

    private void updateRTSSwitch() {
        boolean isOpen = Preferences.getRTSRecord();

        if (isOpen) {
            rtsRecordOpenRadio.setChecked(true);
            rtsRecordCloseRadio.setChecked(false);
        } else {
            rtsRecordOpenRadio.setChecked(false);
            rtsRecordCloseRadio.setChecked(true);
        }
    }

    private void initAudioEffect() {
        audioEffectMap.put(R.id.platform_builtin_radio, AVChatAudioEffectMode.PLATFORM_BUILTIN);
        audioEffectMap.put(R.id.sdk_builtin_radio, AVChatAudioEffectMode.SDK_BUILTIN);
        audioEffectMap.put(R.id.disable_audio_effect_radio, AVChatAudioEffectMode.DISABLE);

        for (Map.Entry<Integer, String> entry : audioEffectMap.entrySet()) {
            if (entry.getValue().equals(Preferences.getAudioEffectMode())) {
                audioEffectGroup.check(entry.getKey());
            }
        }
    }
}
