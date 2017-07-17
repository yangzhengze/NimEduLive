package com.vitek.neteaselive.education.fragment.tab;

import android.os.Bundle;

import com.vitek.neteaselive.R;
import com.vitek.neteaselive.education.fragment.ChatRoomRTSFragment;


/**
 * Created by hzxuwen on 2016/10/27.
 */

public class RTSTabFragment extends ChatRoomTabFragment {
    private ChatRoomRTSFragment fragment;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        onCurrent();
    }

    @Override
    protected void onInit() {
        findViews();
    }

    @Override
    public void onCurrent() {
        super.onCurrent();
        if (fragment != null) {
            fragment.onCurrent();
        }
    }

    private void findViews() {
        if (fragment == null) {
            fragment = (ChatRoomRTSFragment) getActivity().getSupportFragmentManager().findFragmentById(R.id.chat_room_rts_fragment);
        }
    }
}
