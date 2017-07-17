package com.vitek.neteaselive.education.fragment;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.netease.nimlib.sdk.chatroom.constant.MemberQueryType;
import com.netease.nimlib.sdk.chatroom.constant.MemberType;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMember;
import com.vitek.neteaselive.R;
import com.vitek.neteaselive.base.ui.TAdapter;
import com.vitek.neteaselive.base.ui.TAdapterDelegate;
import com.vitek.neteaselive.base.ui.TFragment;
import com.vitek.neteaselive.base.ui.TViewHolder;
import com.vitek.neteaselive.education.activity.ChatRoomActivity;
import com.vitek.neteaselive.education.adapter.OnlinePeopleAdapter;
import com.vitek.neteaselive.education.helper.ChatRoomMemberCache;
import com.vitek.neteaselive.education.helper.SimpleCallback;
import com.vitek.neteaselive.education.helper.VideoListener;
import com.vitek.neteaselive.education.viewholder.OnlinePeopleViewHolder;
import com.vitek.neteaselive.im.ui.ptr.PullToRefreshBase;
import com.vitek.neteaselive.im.ui.ptr.PullToRefreshListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 聊天室在线人数fragment
 * Created by hzxuwen on 2015/12/17.
 */
public class OnlinePeopleFragment extends TFragment implements TAdapterDelegate, VideoListener {
    private static final String TAG = OnlinePeopleFragment.class.getSimpleName();
    private static final int LIMIT = 100;

    private PullToRefreshListView listView;
    private TextView onlineText;
    private TAdapter<ChatRoomMember> adapter;
    private List<OnlinePeopleAdapter.OnlinePeopleItem> items = new ArrayList<>();
    private String roomId;
    private Map<String, OnlinePeopleAdapter.OnlinePeopleItem> memberCache = new ConcurrentHashMap<>();
    private long updateTime = 0; // 非游客的updateTime
    private long enterTime = 0; // 游客的enterTime

    private boolean isNormalEmpty = false; // 固定成员是否拉取完
    private VideoListener videoListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.online_people_fragment, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initAdapter();
        findViews();
        registerObservers(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        videoListener = (VideoListener) context;
    }

    public void onCurrent() {
        clearCache();
        roomId = ((ChatRoomActivity) getActivity()).getRoomInfo().getRoomId();
        fetchData();
        videoListener.onTabChange(false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        registerObservers(false);
    }

    private void clearCache() {
        updateTime = 0;
        enterTime = 0;
        items.clear();
        memberCache.clear();
        isNormalEmpty = false;
    }

    private void initAdapter() {
        adapter = new OnlinePeopleAdapter(getActivity(), items, this, this);
    }

    private void findViews() {
        onlineText = findView(R.id.no_online_people);
        listView = findView(R.id.chat_room_online_list);
        listView.setAdapter(adapter);
        listView.setOnRefreshListener(new PullToRefreshBase.OnRefreshListener2<ListView>() {
            @Override
            public void onPullDownToRefresh(PullToRefreshBase<ListView> refreshView) {

            }

            @Override
            public void onPullUpToRefresh(PullToRefreshBase<ListView> refreshView) {
                fetchData();
            }
        });

    }

    private void stopRefreshing() {
        postDelayed(new Runnable() {
            @Override
            public void run() {
                listView.onRefreshComplete();
            }
        }, 50);
    }

    /*************************** TAdapterDelegate **************************/
    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public Class<? extends TViewHolder> viewHolderAtPosition(int position) {
        return OnlinePeopleViewHolder.class;
    }

    @Override
    public boolean enabled(int position) {
        return false;
    }

    private void fetchData() {
        if (!isNormalEmpty) {
            // 拉取固定在线成员
            getMembers(MemberQueryType.ONLINE_NORMAL, updateTime, 0);
        } else {
            // 拉取非固定成员
            getMembers(MemberQueryType.GUEST, enterTime, 0);
        }
    }

    /**
     * 获取成员列表
     */
    private void getMembers(final MemberQueryType memberQueryType, final long time, int limit) {
        ChatRoomMemberCache.getInstance().fetchRoomMembers(roomId, memberQueryType, time, (LIMIT - limit), new SimpleCallback<List<ChatRoomMember>>() {
            @Override
            public void onResult(boolean success, List<ChatRoomMember> result) {
                if (success) {
                    if (getActivity() == null) {
                        return;
                    }

                    if (onlineText.getVisibility() == View.VISIBLE || result == null || result.isEmpty()) {
                        onlineText.setVisibility(View.GONE);
                    }

                    addMembers(result);

                    if (memberQueryType == MemberQueryType.ONLINE_NORMAL && result.size() < LIMIT) {
                        isNormalEmpty = true; // 固定成员已经拉完
                        getMembers(MemberQueryType.GUEST, enterTime, result.size());
                    }
                }

                stopRefreshing();
            }
        });
    }

    private void addMembers(List<ChatRoomMember> members) {
        for (ChatRoomMember member : members) {
            if (!isNormalEmpty) {
                updateTime = member.getUpdateTime();
            } else {
                enterTime = member.getEnterTime();
            }

            if (memberCache.containsKey(member.getAccount())) {
                items.remove(memberCache.get(member.getAccount()));
            }
           OnlinePeopleAdapter.OnlinePeopleItem item = new OnlinePeopleAdapter.OnlinePeopleItem(((ChatRoomActivity)getActivity()).getRoomInfo().getCreator(),
                    member);

            memberCache.put(member.getAccount(), item);
            items.add(item);
        }
        Collections.sort(items, comp);
        adapter.notifyDataSetChanged();
    }

    /**
     * *************************** 成员操作监听 ****************************
     */
    private void registerObservers(boolean register) {
        ChatRoomMemberCache.getInstance().registerRoomMemberChangedObserver(roomMemberChangedObserver, register);
        ChatRoomMemberCache.getInstance().registerMeetingControlObserver(meetingControlObserver, register);
    }

    ChatRoomMemberCache.RoomMemberChangedObserver roomMemberChangedObserver = new ChatRoomMemberCache.RoomMemberChangedObserver() {
        @Override
        public void onRoomMemberIn(ChatRoomMember member) {
        }

        @Override
        public void onRoomMemberExit(ChatRoomMember member) {
            if (member == null) {
                return;
            }
            for (OnlinePeopleAdapter.OnlinePeopleItem item : items) {
                if (item.getChatRoomMember().getAccount().equals(member.getAccount())) {
                    items.remove(item);
                    break;
                }
            }
            adapter.notifyDataSetChanged();
        }
    };

    ChatRoomMemberCache.MeetingControlObserver meetingControlObserver = new ChatRoomMemberCache.MeetingControlObserver() {

        @Override
        public void onAccept(String roomID) {
            if (checkRoom(roomID)) {
                return;
            }
            adapter.notifyDataSetChanged();
        }

        @Override
        public void onReject(String roomID) {
            if (checkRoom(roomID)) {
                return;
            }
            adapter.notifyDataSetChanged();
        }

        @Override
        public void onPermissionResponse(String roomId, List<String> accounts) {

        }

        @Override
        public void onSendMyPermission(String roomID, String toAccount) {

        }

        @Override
        public void onSaveMemberPermission(String roomId, List<String> accounts) {

        }

        @Override
        public void onHandsUp(String roomID, String account) {
            if (checkRoom(roomID)) {
                return;
            }
            clearCache();
            fetchData();
        }

        @Override
        public void onHandsDown(String roomID, String account) {
            if (checkRoom(roomID)) {
                return;
            }
            adapter.notifyDataSetChanged();
        }

        @Override
        public void onStatusNotify(String roomID, List<String> accounts) {

        }
    };

    private boolean checkRoom(String roomID) {
        return TextUtils.isEmpty(roomId) || !roomId.equals(roomID);
    }

    private  static Map<MemberType, Integer> compMap = new HashMap<>();

    static {
        compMap.put(MemberType.CREATOR, 0);
        compMap.put(MemberType.ADMIN, 1);
        compMap.put(MemberType.NORMAL, 2);
        compMap.put(MemberType.LIMITED, 3);
        compMap.put(MemberType.GUEST, 4);
    }

    private static Comparator<OnlinePeopleAdapter.OnlinePeopleItem> comp = new Comparator<OnlinePeopleAdapter.OnlinePeopleItem>() {
        @Override
        public int compare(OnlinePeopleAdapter.OnlinePeopleItem lhs, OnlinePeopleAdapter.OnlinePeopleItem rhs) {
            if (lhs == null) {
                return 1;
            }

            if (rhs == null) {
                return -1;
            }

            return compMap.get(lhs.getChatRoomMember().getMemberType()) - compMap.get(rhs.getChatRoomMember().getMemberType());
        }
    };

    @Override
    public void onVideoOn(String account) {
        videoListener.onVideoOn(account);
    }

    @Override
    public void onVideoOff(String account) {
        videoListener.onVideoOff(account);
    }

    @Override
    public void onTabChange(boolean notify) {
        videoListener.onTabChange(notify);
    }

    @Override
    public void onKickOutSuccess(String account) {
        ChatRoomMemberCache.getInstance().removeHandsUpMem(roomId, account);
        for (OnlinePeopleAdapter.OnlinePeopleItem item : items) {
            if (item.getChatRoomMember().getAccount().equals(account)) {
                items.remove(item);
                break;
            }
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onUserLeave(String account) {

    }

    @Override
    public void onReportSpeaker(Map<String, Integer> map) {
        List<String> accounts = new ArrayList<>();
        for (OnlinePeopleAdapter.OnlinePeopleItem item : items) {
            accounts.add(item.getChatRoomMember().getAccount());
        }

        for (String key : map.keySet()) {
            for (OnlinePeopleAdapter.OnlinePeopleItem item : items) {
                if (item.getChatRoomMember().getAccount().equals(key)) {
                    updateView(findVolumeStep(map.get(key)), getItemIndex(key));
                    accounts.remove(key);
                }
            }
        }

        for (String a : accounts) {
            updateView(findVolumeStep(0), getItemIndex(a));
        }
    }

    @Override
    public void onAcceptConfirm() {
        adapter.notifyDataSetChanged();
    }

    private int findVolumeStep(int volume) {
        int volumeStep = 0;
        volume /= 40;
        while (volume > 0) {
            volumeStep++;
            volume /= 2;
        }
        if (volumeStep > 8) {
            volumeStep = 8;
        }
        return volumeStep;
    }

    private int getItemIndex(String account) {
        for (int i = 0; i < items.size(); i++) {
            OnlinePeopleAdapter.OnlinePeopleItem item = items.get(i);
            if (TextUtils.equals(item.getChatRoomMember().getAccount(), account)) {
                return i;
            }
        }

        return -1;
    }

    private int[] volumeImageRes = {R.drawable.volume_ic_1, R.drawable.volume_ic_2, R.drawable.volume_ic_3,
            R.drawable.volume_ic_4, R.drawable.volume_ic_5, R.drawable.volume_ic_6,
            R.drawable.volume_ic_7, R.drawable.volume_ic_8};

    private void updateView(int volume, int itemIndex) {
        Object tag = getViewHolderByIndex(listView, itemIndex);
        if (tag instanceof OnlinePeopleViewHolder) {
            OnlinePeopleViewHolder viewHolder = (OnlinePeopleViewHolder) tag;
            if (viewHolder != null) {
                // 音量信号
                if (volume == 0) {
                    viewHolder.volumeImage.setVisibility(View.GONE);
                } else {
                    viewHolder.volumeImage.setVisibility(View.VISIBLE);
                    viewHolder.volumeImage.setImageResource(volumeImageRes[volume - 1]);
                }
            }
        }
    }

    //index是items的index，不包含header
    public static Object getViewHolderByIndex(PullToRefreshListView listView, int index) {
        ListView refreshableView = listView.getRefreshableView();
        if (refreshableView == null) {
            return null;
        }

        int firstVisibleFeedPosition = refreshableView.getFirstVisiblePosition() - refreshableView.getHeaderViewsCount();
        int lastVisibleFeedPosition = refreshableView.getLastVisiblePosition() - refreshableView.getHeaderViewsCount();

        //只有获取可见区域的
        if (index >= firstVisibleFeedPosition  && index <= lastVisibleFeedPosition) {
            View view = refreshableView.getChildAt(index - firstVisibleFeedPosition);
            if (view == null) {
                return null;
            }
            Object tag = view.getTag();
            return tag;
        } else {
            return null;
        }
    }
}
