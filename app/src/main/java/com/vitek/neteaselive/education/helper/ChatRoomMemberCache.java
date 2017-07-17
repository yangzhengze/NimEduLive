package com.vitek.neteaselive.education.helper;

import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.Observer;
import com.netease.nimlib.sdk.RequestCallbackWrapper;
import com.netease.nimlib.sdk.ResponseCode;
import com.netease.nimlib.sdk.chatroom.ChatRoomService;
import com.netease.nimlib.sdk.chatroom.ChatRoomServiceObserver;
import com.netease.nimlib.sdk.chatroom.constant.MemberQueryType;
import com.netease.nimlib.sdk.chatroom.constant.MemberType;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMember;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMessage;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomNotificationAttachment;
import com.netease.nimlib.sdk.msg.MsgServiceObserve;
import com.netease.nimlib.sdk.msg.constant.MsgTypeEnum;
import com.netease.nimlib.sdk.msg.constant.NotificationType;
import com.netease.nimlib.sdk.msg.model.CustomNotification;
import com.netease.nimlib.sdk.msg.model.IMMessage;
import com.vitek.neteaselive.base.util.log.LogUtil;
import com.vitek.neteaselive.education.module.MeetingOptCommand;
import com.vitek.neteaselive.education.module.custom.PermissionAttachment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.netease.nimlib.sdk.msg.constant.NotificationType.ChatRoomInfoUpdated;

/**
 * 聊天室成员资料缓存
 * Created by huangjun on 2016/1/18.
 */
public class ChatRoomMemberCache {

    private static final String TAG = "ChatRoomMemberCache";

    public static ChatRoomMemberCache getInstance() {
        return InstanceHolder.instance;
    }

    private Map<String, Map<String, ChatRoomMember>> cache = new HashMap<>();

    private Map<String, List<SimpleCallback<ChatRoomMember>>> frequencyLimitCache = new HashMap<>(); // 重复请求处理

    private List<RoomMemberChangedObserver> roomMemberChangedObservers = new ArrayList<>();

    private List<RoomInfoChangedObserver> roomInfoChangedObservers = new ArrayList<>();

    private Map<String, Map<String, ChatRoomMember>> permissionCache = new HashMap<>(); // 有音视频权限的人员缓存

    private Map<String, Map<Integer, String>> imageCache = new HashMap<>(); // 画布是否已经显示

    private Map<String, Map<String, Boolean>> handsUpMemCache = new HashMap<>(); // 举手的成员缓存

    private List<MeetingControlObserver> meetingControlObservers = new ArrayList<>();

    private List<RoomMsgObserver> roomMsgObservers = new ArrayList<>();

    private Map<String, Boolean> myHandsUpCache = new HashMap<>();

    private boolean isRTSOpen = false; // 白板是否启启用

    public void clear() {
        cache.clear();
        frequencyLimitCache.clear();
        roomMemberChangedObservers.clear();
        roomInfoChangedObservers.clear();
        imageCache.clear();
        permissionCache.clear();
        handsUpMemCache.clear();
        meetingControlObservers.clear();
        roomMsgObservers.clear();
        myHandsUpCache.clear();
    }

    public void clearRoomCache(String roomId) {
        if (cache.containsKey(roomId)) {
            cache.remove(roomId);
        }
        if (imageCache.containsKey(roomId)) {
            imageCache.remove(roomId);
        }
        if (permissionCache.containsKey(roomId)) {
            permissionCache.remove(roomId);
        }
        if (handsUpMemCache.containsKey(roomId)) {
            handsUpMemCache.remove(roomId);
        }
        if (myHandsUpCache.containsKey(roomId)) {
            myHandsUpCache.remove(roomId);
        }

        setRTSOpen(false);
    }

    public boolean isRTSOpen() {
        return isRTSOpen;
    }

    public void setRTSOpen(boolean RTSOpen) {
        isRTSOpen = RTSOpen;
    }

    /************************ 群成员缓存 ****************************/

    public ChatRoomMember getChatRoomMember(String roomId, String account) {
        if (cache.containsKey(roomId)) {
            return cache.get(roomId).get(account);
        }

        return null;
    }

    public void saveMyMember(ChatRoomMember chatRoomMember) {
        saveMember(chatRoomMember);
    }

    /**
     * 从服务器获取聊天室成员资料（去重处理）（异步）
     */
    public void fetchMember(final String roomId, final String account, final SimpleCallback<ChatRoomMember> callback) {
        if (TextUtils.isEmpty(roomId) || TextUtils.isEmpty(account)) {
            callback.onResult(false, null);
            return;
        }

        // 频率控制
        if (frequencyLimitCache.containsKey(account)) {
            if (callback != null) {
                frequencyLimitCache.get(account).add(callback);
            }
            return; // 已经在请求中，不要重复请求
        } else {
            List<SimpleCallback<ChatRoomMember>> cbs = new ArrayList<>();
            if (callback != null) {
                cbs.add(callback);
            }
            frequencyLimitCache.put(account, cbs);
        }

        // fetch
        List<String> accounts = new ArrayList<>(1);
        accounts.add(account);
        NIMClient.getService(ChatRoomService.class).fetchRoomMembersByIds(roomId, accounts).setCallback(new RequestCallbackWrapper<List<ChatRoomMember>>() {
            @Override
            public void onResult(int code, List<ChatRoomMember> members, Throwable exception) {
                ChatRoomMember member = null;
                boolean hasCallback = !frequencyLimitCache.get(account).isEmpty();
                boolean success = code == ResponseCode.RES_SUCCESS && members != null && !members.isEmpty();

                // cache
                if (success) {
                    saveMembers(members);
                    member = members.get(0);
                } else {
                    LogUtil.e(TAG, "fetch chat room member failed, code=" + code);
                }

                // callback
                if (hasCallback) {
                    List<SimpleCallback<ChatRoomMember>> cbs = frequencyLimitCache.get(account);
                    for (SimpleCallback<ChatRoomMember> cb : cbs) {
                        cb.onResult(success, member);
                    }
                }

                frequencyLimitCache.remove(account);
            }
        });
    }

    public void fetchRoomMembers(String roomId, MemberQueryType memberQueryType, long time, int limit,
                                 final SimpleCallback<List<ChatRoomMember>> callback) {
        if (TextUtils.isEmpty(roomId)) {
            callback.onResult(false, null);
            return;
        }

        NIMClient.getService(ChatRoomService.class).fetchRoomMembers(roomId, memberQueryType, time, limit).setCallback(new RequestCallbackWrapper<List<ChatRoomMember>>() {
            @Override
            public void onResult(int code, List<ChatRoomMember> result, Throwable exception) {
                boolean success = code == ResponseCode.RES_SUCCESS;

                if (success) {
                    saveMembers(result);
                } else {
                    LogUtil.e(TAG, "fetch members by page failed, code:" + code);
                }

                if (callback != null) {
                    callback.onResult(success, result);
                }
            }
        });
    }

    private void saveMember(ChatRoomMember member) {
        if (member != null && !TextUtils.isEmpty(member.getRoomId()) && !TextUtils.isEmpty(member.getAccount())) {
            Map<String, ChatRoomMember> members = cache.get(member.getRoomId());

            if (members == null) {
                members = new HashMap<>();
                cache.put(member.getRoomId(), members);
            }

            members.put(member.getAccount(), member);
        }
    }

    private void saveMembers(List<ChatRoomMember> members) {
        if (members == null || members.isEmpty()) {
            return;
        }

        for (ChatRoomMember m : members) {
            saveMember(m);
        }
    }

    /******************* 拥有音视频权限的成员 ********************/

    /**
     * 存储拥有音视频权限的成员列表
     */
    public void savePermissionMemberbyId(String roomId, String account) {
        if (TextUtils.isEmpty(account)) {
            return;
        }

        ChatRoomMember member = getChatRoomMember(roomId, account);
        if (member != null) {
            savePermissionMem(member);
        } else {
            fetchMember(roomId, account, new SimpleCallback<ChatRoomMember>() {
                @Override
                public void onResult(boolean success, ChatRoomMember result) {
                    if (success) {
                        savePermissionMem(result);
                    }
                }
            });
        }
    }

    /**
     * 单独存储有用音视频权限的成员
     */
    private void savePermissionMem(ChatRoomMember member) {
        if (member != null && !TextUtils.isEmpty(member.getRoomId()) && !TextUtils.isEmpty(member.getAccount())) {
            Map<String, ChatRoomMember> members = permissionCache.get(member.getRoomId());

            if (members == null) {
                members = new HashMap<>();
                permissionCache.put(member.getRoomId(), members);
            }

            members.put(member.getAccount(), member);
        }
    }

    /**
     * 取消成员的音视频权限
     */
    public void removePermissionMem(String roomId, String account) {
        if (TextUtils.isEmpty(account)) {
            return;
        }

        Map<String, ChatRoomMember> map = permissionCache.get(roomId);

        if (map == null || map.isEmpty()) {
            return;
        }

       if (map.containsKey(account)) {
           map.remove(account);
       }
    }

    /**
     * 判断成员是否拥有音视频权限
     */
    public boolean hasPermission(String roomId, String account) {
        if (TextUtils.isEmpty(account)) {
            return false;
        }

        Map<String, ChatRoomMember> map = permissionCache.get(roomId);
        if (map != null && !map.isEmpty() && map.containsKey(account)) {
            return true;
        }

        return false;
    }

    /**
     * 获取拥有音视频权限的成员帐号列表
     */
    public List<String> getPermissionMems(String roomId) {
        List<String> accounts = new ArrayList<>();
        Map<String, ChatRoomMember> map = permissionCache.get(roomId);

        if (map == null) {
            return null;
        }

        Iterator it = map.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next().toString();
            accounts.add(key);
        }
        return accounts;
    }

    /**
     * 获取拥有音视频权限的成员列表
     */
    public List<ChatRoomMember> getPermissionMemsEx(String roomId) {
        List<ChatRoomMember> members = new ArrayList<>();
        Map<String, ChatRoomMember> map = permissionCache.get(roomId);

        if (map == null) {
            return null;
        }

        Iterator it = map.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next().toString();
            members.add(map.get(key));
        }
        return members;
    }



    /********************************** 在线教育画布缓存 *******************************/

    /**
     * 获取画布缓存
     */
    public Map<Integer, String> getImageMap(String roomId) {
        if (imageCache.containsKey(roomId)) {
            return imageCache.get(roomId);
        }

        return null;
    }

    /**
     * 存储画布缓存
     */
    public void saveImageMap(String roomId, Map<Integer, String> imageMap) {
        imageCache.put(roomId, imageMap);
    }

    /************************** 成员是否举手缓存 *******************************/

    /**
     * 成员是否举手
     */
    public boolean isHansUp(String roomId, String account) {
        if (handsUpMemCache.containsKey(roomId)) {
            Map<String, Boolean> mem = handsUpMemCache.get(roomId);
            if (mem == null || mem.isEmpty()) {
                return false;
            }
            if (mem.containsKey(account)) {
                return mem.get(account);
            }
        }

        return false;
    }

    /**
     * 成员主动举手/取消举手
     */
    public void saveMemberHandsUpDown(String roomId, String account, boolean isUp) {
        Map<String, Boolean> membersState = handsUpMemCache.get(roomId);
        if (membersState == null) {
            membersState = new HashMap<>();
            handsUpMemCache.put(roomId, membersState);
        }

        membersState.put(account, isUp);
    }

    /**
     * 主持人清空成员的举手
     */
    public void removeHandsUpMem(String roomId, String account) {
        Map<String, Boolean> membersState = handsUpMemCache.get(roomId);

        if (membersState == null) {
            return;
        }

        if (membersState.containsKey(account)) {
            membersState.remove(account);
        }
    }

    /**
     * 存储自己举手状态
     */
    public void saveMyHandsUpDown(String roomId, boolean isHandsUp) {
        myHandsUpCache.put(roomId, isHandsUp);
    }

    /**
     * 自己是否举手
     */
    public boolean isMyHandsUp(String roomId) {
        if (myHandsUpCache.get(roomId) == null) {
            return false;
        }
        return myHandsUpCache.get(roomId);
    }

    /**
     * 清除该房间所有举手状态
     * @param roomId 房间id
     */
    public void clearAllHandsUp(String roomId) {
        if (handsUpMemCache.containsKey(roomId)) {
            handsUpMemCache.remove(roomId);
        }
    }

    /**
     * ************************************ 单例 ***************************************
     */
    static class InstanceHolder {
        final static ChatRoomMemberCache instance = new ChatRoomMemberCache();
    }

    /**
     * ********************************** 监听 ********************************
     */

    public void registerObservers(boolean register) {
        NIMClient.getService(ChatRoomServiceObserver.class).observeReceiveMessage(incomingChatRoomMsg, register);
        NIMClient.getService(MsgServiceObserve.class).observeCustomNotification(customNotification, register);
    }

    private Observer<List<ChatRoomMessage>> incomingChatRoomMsg = new Observer<List<ChatRoomMessage>>() {
        @Override
        public void onEvent(List<ChatRoomMessage> messages) {
            if (messages == null || messages.isEmpty()) {
                return;
            }

            for (IMMessage msg : messages) {
                if (msg == null) {
                    LogUtil.e(TAG, "receive chat room message null");
                    continue;
                }

                LogUtil.d(TAG, "receive msg type:" + msg.getMsgType());
                if (msg.getMsgType() == MsgTypeEnum.notification) {
                    handleNotification(msg);
                }

                // 成员权限
                if (sendReceiveMemPermissions(msg)) {
                    return;
                }

                for (RoomMsgObserver observer : roomMsgObservers) {
                    observer.onMsgIncoming(messages);
                }
            }
        }
    };

    // 收到/发送成员权限缓存
    private boolean sendReceiveMemPermissions(IMMessage message) {
        if (message.getAttachment() != null
                && message.getAttachment() instanceof PermissionAttachment) {
            LogUtil.d(TAG, "receive permission msg, return true");
            PermissionAttachment attachment = (PermissionAttachment) message.getAttachment();
            if (attachment.getMeetingOptCommand() == MeetingOptCommand.ALL_STATUS) {
                for (MeetingControlObserver observer : meetingControlObservers) {
                    observer.onStatusNotify(attachment.getRoomId(), attachment.getAccounts());
                }
            } else if (attachment.getMeetingOptCommand() == MeetingOptCommand.GET_STATUS) {
                // 收到请求有权限的成员列表，如果自己有音视频权限，则发送消息告知对方
                for (MeetingControlObserver observer : meetingControlObservers) {
                    observer.onSendMyPermission(attachment.getRoomId(), message.getFromAccount());
                }
            }
            return true;
        }
        return false;
    }

    private void handleNotification(IMMessage message) {
        if (message.getAttachment() == null) {
            return;
        }

        String roomId = message.getSessionId();
        final ChatRoomNotificationAttachment attachment = (ChatRoomNotificationAttachment) message.getAttachment();
        if (attachment.getType() == ChatRoomInfoUpdated) {
            for (RoomInfoChangedObserver o : roomInfoChangedObservers) {
                o.onRoomInfoUpdate(message);
            }
        } else {
            fetchRoomMember(message, roomId, attachment);
        }
    }

    private void fetchRoomMember(IMMessage message, String roomId, final ChatRoomNotificationAttachment attachment) {
        List<String> targets = attachment.getTargets();
        if (targets != null) {
            for (String target : targets) {
                ChatRoomMember member = getChatRoomMember(roomId, target);
                if (member != null) {
                    handleMemberChanged(attachment.getType(), member);
                } else {
                    fetchMember(roomId, message.getFromAccount(), new SimpleCallback<ChatRoomMember>() {
                        @Override
                        public void onResult(boolean success, ChatRoomMember result) {
                            if (success) {
                                handleMemberChanged(attachment.getType(), result);
                            }
                        }
                    });
                }

            }
        }
    }

    private void handleMemberChanged(NotificationType type, ChatRoomMember member) {
        if (member == null) {
            return;
        }

        switch (type) {
            case ChatRoomMemberIn:
                for (RoomMemberChangedObserver o : roomMemberChangedObservers) {
                    o.onRoomMemberIn(member);
                }
                break;
            case ChatRoomMemberExit:
                for (RoomMemberChangedObserver o : roomMemberChangedObservers) {
                    o.onRoomMemberExit(member);
                }
                break;
            case ChatRoomManagerAdd:
                member.setMemberType(MemberType.ADMIN);
                break;
            case ChatRoomManagerRemove:
                member.setMemberType(MemberType.NORMAL);
                break;
            case ChatRoomMemberBlackAdd:
                member.setInBlackList(true);
                break;
            case ChatRoomMemberBlackRemove:
                member.setInBlackList(false);
                break;
            case ChatRoomMemberMuteAdd:
                member.setMuted(true);
                break;
            case ChatRoomMemberMuteRemove:
                member.setMuted(false);
                member.setMemberType(MemberType.GUEST);
                break;
            case ChatRoomCommonAdd:
                member.setMemberType(MemberType.NORMAL);
                break;
            case ChatRoomCommonRemove:
                member.setMemberType(MemberType.GUEST);
                break;
            default:
                break;
        }

        saveMember(member);
    }

    private Observer<CustomNotification> customNotification = new Observer<CustomNotification>() {
        @Override
        public void onEvent(CustomNotification customNotification) {
            String content = customNotification.getContent();
            int command = 0;
            List<String> accounts = new ArrayList<>();
            String roomId = null;
            try {
                JSONObject json = JSON.parseObject(content);
                int id = json.getIntValue("type");
                if (id == 10) {
                    // 聊天室通知
                    JSONObject data = json.getJSONObject("data");
                    roomId = data.getString("room_id");
                    JSONArray array = data.getJSONArray("uids");
                    command = data.getIntValue("command");
                    for (int i = 0; i < array.size(); i++) {
                        accounts.add(array.get(i).toString());
                    }

                }
                LogUtil.d(TAG, "receive custom notification, command:" + command);

            } catch (Exception e) {

            }
            if (command == MeetingOptCommand.SPEAK_ACCEPT.getValue()) {
                for (MeetingControlObserver observer : meetingControlObservers) {
                    observer.onAccept(roomId);
                }
            } else if (command == MeetingOptCommand.ALL_STATUS.getValue()) {
                // 刚入群，主持人发所有成员权限给观众
                for (MeetingControlObserver observer : meetingControlObservers) {
                    observer.onSaveMemberPermission(roomId, accounts);
                }
            } else if (command == MeetingOptCommand.STATUS_RESPONSE.getValue()) {
                // 向所有人请求有权限的成员。有权限的成员返回的通知。
                for (MeetingControlObserver observer : meetingControlObservers) {
                    observer.onPermissionResponse(roomId, accounts);
                }
            } else if (command == MeetingOptCommand.SPEAK_REJECT.getValue()) {
                // 主持人拒绝/挂断连麦
                for (MeetingControlObserver observer : meetingControlObservers) {
                    observer.onReject(roomId);
                }
            } else
            if (command == MeetingOptCommand.SPEAK_REQUEST.getValue()) {
                // 有人举手发言
                for (MeetingControlObserver observer : meetingControlObservers) {
                    observer.onHandsUp(roomId, customNotification.getFromAccount());
                }
            } else if (command == MeetingOptCommand.SPEAK_REQUEST_CANCEL.getValue()) {
                // 取消举手发言
                for (MeetingControlObserver observer : meetingControlObservers) {
                    observer.onHandsDown(roomId, customNotification.getFromAccount());
                }
            }
        }
    };

    public interface MeetingControlObserver {
        void onAccept(String roomID);
        void onReject(String roomID);
        void onPermissionResponse(String roomId, List<String> accounts);
        void onSaveMemberPermission(String roomID, List<String> accounts);
        void onSendMyPermission(String roomID, String toAccount);
        void onHandsUp(String roomID, String account);
        void onHandsDown(String roomID, String account);
        void onStatusNotify(String roomID, List<String> accounts);
    }

    public void registerMeetingControlObserver(MeetingControlObserver o, boolean register) {
        if (o == null) {
            return;
        }

        if (register) {
            if (!meetingControlObservers.contains(o)) {
                meetingControlObservers.add(o);
            }
        } else {
            meetingControlObservers.remove(o);
        }
    }

    public interface RoomMsgObserver {
        void onMsgIncoming(List<ChatRoomMessage> messages);
    }


    public void registerRoomMsgObserver(RoomMsgObserver o, boolean register) {
        if (o == null) {
            return;
        }

        if (register) {
            if (!roomMsgObservers.contains(o)) {
                roomMsgObservers.add(o);
            }
        } else {
            roomMsgObservers.remove(o);
        }
    }

    /**
     * ************************** 在线用户变化通知 ****************************
     */

    public interface RoomMemberChangedObserver {
        void onRoomMemberIn(ChatRoomMember member);

        void onRoomMemberExit(ChatRoomMember member);
    }

    public void registerRoomMemberChangedObserver(RoomMemberChangedObserver o, boolean register) {
        if (o == null) {
            return;
        }

        if (register) {
            if (!roomMemberChangedObservers.contains(o)) {
                roomMemberChangedObservers.add(o);
            }
        } else {
            roomMemberChangedObservers.remove(o);
        }
    }

    /**
     * ******************* 聊天室信息变化通知 ***********************
     */

    public interface RoomInfoChangedObserver {
        void onRoomInfoUpdate(IMMessage message);
    }

    public void registerRoomInfoChangedObserver(RoomInfoChangedObserver o, boolean register) {
        if (o == null) {
            return;
        }

        if (register) {
            if (!roomInfoChangedObservers.contains(o)) {
                roomInfoChangedObservers.add(o);
            }
        } else {
            roomInfoChangedObservers.remove(o);
        }
    }
}
