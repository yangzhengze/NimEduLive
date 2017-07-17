package com.vitek.neteaselive.education.fragment.tab;


import com.vitek.neteaselive.R;
import com.vitek.neteaselive.im.ui.tab.reminder.ReminderId;

/**
 * Created by hzxuwen on 2015/12/14.
 */
public enum ChatRoomTab {
    RTS(0, ReminderId.RTS, RTSTabFragment.class, R.string.chat_room_rts, R.layout.chat_room_rts_tab),
    CHAT_ROOM_MESSAGE(1, ReminderId.SESSION, MessageTabFragment.class, R.string.chat_room_message, R.layout.chat_room_message_tab),
    ONLINE_PEOPLE(2, ReminderId.CONTACT, OnlinePeopleTabFragment.class, R.string.chat_room_online_people, R.layout.chat_room_people_tab);

    public final int tabIndex;

    public final int reminderId;

    public final Class<? extends ChatRoomTabFragment> clazz;

    public final int resId;

    public final int fragmentId;

    public final int layoutId;

    ChatRoomTab(int index, int reminderId, Class<? extends ChatRoomTabFragment> clazz, int resId, int layoutId) {
        this.tabIndex = index;
        this.reminderId = reminderId;
        this.clazz = clazz;
        this.resId = resId;
        this.fragmentId = index;
        this.layoutId = layoutId;
    }

    public static final ChatRoomTab fromTabIndex(int tabIndex) {
        for (ChatRoomTab value : ChatRoomTab.values()) {
            if (value.tabIndex == tabIndex) {
                return value;
            }
        }

        return null;
    }

    public static final ChatRoomTab fromReminderId(int reminderId) {
        for (ChatRoomTab value : ChatRoomTab.values()) {
            if (value.reminderId == reminderId) {
                return value;
            }
        }

        return null;
    }
}
