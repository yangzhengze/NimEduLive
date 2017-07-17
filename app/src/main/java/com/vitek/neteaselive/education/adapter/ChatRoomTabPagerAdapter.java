package com.vitek.neteaselive.education.adapter;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.vitek.neteaselive.education.fragment.tab.ChatRoomTab;
import com.vitek.neteaselive.education.fragment.tab.ChatRoomTabFragment;
import com.vitek.neteaselive.education.util.NonScrollViewPager;
import com.vitek.neteaselive.im.ui.tab.SlidingTabPagerAdapter;

import java.util.List;

/**
 * Created by hzxuwen on 2015/12/14.
 */
public class ChatRoomTabPagerAdapter extends SlidingTabPagerAdapter {
    private int length;
    public ChatRoomTabPagerAdapter(FragmentManager fm, Context context, NonScrollViewPager pager, int length) {
        super(fm, length, context.getApplicationContext(), pager);
        this.length = length;

        setDataList(fm, length);
    }

    public void setDataList(FragmentManager fm, int length) {
        for (int i = 0; i < length; i++) {
            try {
                ChatRoomTabFragment fragment = null;

                List<Fragment> fs = fm.getFragments();
                if (fs != null) {
                    for (Fragment f : fs) {
                        if (f.getClass() == ChatRoomTab.fromTabIndex(i).clazz) {
                            fragment = (ChatRoomTabFragment) f;
                            break;
                        }
                    }
                }

                if (fragment == null) {
                    fragment = ChatRoomTab.fromTabIndex(i).clazz.newInstance();
                }

                fragment.setState(this);
                fragment.attachTabData(ChatRoomTab.fromTabIndex(i));

                fragments[i] = fragment;
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public int getCacheCount() {
        return length;
    }

    @Override
    public int getCount() {
        return length;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        ChatRoomTab tab = ChatRoomTab.fromTabIndex(position);

        int resId = tab != null ? tab.resId : 0;

        return resId != 0 ? context.getText(resId) : "";
    }

    public void setLength(int length) {
        this.length = length;
    }
}
