package com.vitek.neteaselive.education.module.custom;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.vitek.neteaselive.education.module.MeetingOptCommand;

import java.util.ArrayList;
import java.util.List;

public class PermissionAttachment extends CustomAttachment {
    private final static String KEY_COMMAND = "command";
    private final static String KEY_ROOM_ID = "room_id";
    private final static String KEY_UIDS = "uids";

    private String roomId;
    private MeetingOptCommand meetingOptCommand;
    private List<String> accounts = new ArrayList<>();

    public PermissionAttachment() {
        super(CustomAttachmentType.Permission);
    }

    public PermissionAttachment(String roomId, MeetingOptCommand optCommand) {
        this();
        this.roomId = roomId;
        this.meetingOptCommand = optCommand;
    }

    public PermissionAttachment(String roomId, MeetingOptCommand optCommand, List<String> accounts) {
        this();
        this.roomId = roomId;
        this.meetingOptCommand = optCommand;
        this.accounts = accounts;
    }

    @Override
    protected void parseData(JSONObject data) {
        roomId = data.getString(KEY_ROOM_ID);
        meetingOptCommand = MeetingOptCommand.statusOfValue(data.getIntValue(KEY_COMMAND));
        JSONArray array = data.getJSONArray(KEY_UIDS);
        for (int i = 0; i < array.size(); i++) {
            accounts.add(array.get(i).toString());
        }
    }

    @Override
    protected JSONObject packData() {
        JSONObject data = new JSONObject();
        data.put(KEY_COMMAND, meetingOptCommand.getValue());
        data.put(KEY_ROOM_ID, roomId);
        if (accounts != null && !accounts.isEmpty()) {
            data.put(KEY_UIDS, accounts);
        }
        return data;
    }

    public String getRoomId() {
        return roomId;
    }

    public MeetingOptCommand getMeetingOptCommand() {
        return meetingOptCommand;
    }

    public List<String> getAccounts() {
        return accounts;
    }
}
