package com.vitek.neteaselive.education.doodle;

import android.text.TextUtils;
import android.util.Log;

import com.netease.nimlib.sdk.rts.RTSManager2;
import com.netease.nimlib.sdk.rts.model.RTSTunData;
import com.vitek.neteaselive.base.util.log.LogUtil;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 白板数据收发中心
 * <p/>
 * Created by huangjun on 2015/6/29.
 */
public class TransactionCenter {

    private int index = 0;

    private final String TAG = "TransactionCenter";

    // sessionId to TransactionObserver
    private Map<String, TransactionObserver> observers = new HashMap<>(2);

    private Map<String, OnlineStatusObserver> onlineStatusObservers = new HashMap<>(2);

    // <sessionId, <account, transactions>>
    private Map<String, Map<String, List<Transaction>>> syncCache = new HashMap<>();

    private boolean isDoodleViewInited = false;

    public static TransactionCenter getInstance() {
        return TransactionCenterHolder.instance;
    }

    private static class TransactionCenterHolder {
        public static final TransactionCenter instance = new TransactionCenter();
    }

    public void registerObserver(String sessionId, TransactionObserver o) {
        this.observers.put(sessionId, o);
    }

    public void registerOnlineStatusObserver(String sessionId, OnlineStatusObserver o) {
        this.onlineStatusObservers.put(sessionId, o);
    }

    /**
     * 网络变化
     */
    public boolean onNetWorkChange(String sessionId, boolean isCreator) {
        if (onlineStatusObservers.containsKey(sessionId)) {
            return onlineStatusObservers.get(sessionId).onNetWorkChange(isCreator);
        }
        return false;
    }

    /**
     * 数据发送
     */
    public void sendToRemote(String sessionId, String toAccount, List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }

        String data = pack(transactions);
        try {
            RTSTunData channelData = new RTSTunData(sessionId, toAccount, data.getBytes
                    ("UTF-8"), data.getBytes().length);
            boolean isSend = RTSManager2.getInstance().sendData(channelData);
            Log.i(TAG, "SEND DATA = " + index + ", BYTES = " + data.getBytes().length + ", isSend=" + isSend);
        } catch (UnsupportedEncodingException e) {
            LogUtil.e("Transaction", "send to remote, getBytes exception : " + data);
        }
    }

    private String pack(List<Transaction> transactions) {
        StringBuilder sb = new StringBuilder();

        List<Transaction> tempList = new ArrayList<>();
        tempList.addAll(transactions);
        for (Transaction t : tempList) {
            sb.append(Transaction.pack(t));
        }

        // 打入序号
        sb.append(Transaction.packIndex(++index));

        return sb.toString();
    }

    /**
     * 数据接收
     */
    public void onReceive(String sessionId, String account, String data) {
        List<Transaction> transactions = unpack(data);
        if ((transactions != null ? transactions.size() : 0) <= 0) {
            return;
        }
        int step = transactions.get(0).getStep();

        if (observers.containsKey(sessionId)) {
            // 断网重连数据同步
            if (step == Transaction.ActionStep.SYNC) {
                observers.get(sessionId).onTransaction(transactions.get(0).getUid(), transactions);
            } else {
                // 接收数据
                observers.get(sessionId).onTransaction(account, transactions);
            }
        }

        if (step == Transaction.ActionStep.LASER_PEN || step == Transaction.ActionStep.LASER_PEN_END) {
            return;
        }
        saveCacheData(sessionId, account, transactions, step);

        RevokeOrClearCache(sessionId, account, step);
    }

    private void saveCacheData(String sessionId, String account, List<Transaction> transactions, int step) {
        if (step == Transaction.ActionStep.SYNC || !TransactionCenter.getInstance().isDoodleViewInited()) {
            String paintAccount;
            Transaction t = transactions.get(0);
            if (t.getStep() == Transaction.ActionStep.SYNC) {
                paintAccount = t.getUid();
            } else {
                paintAccount = account;
            }

            Map<String, List<Transaction>> map = syncCache.get(sessionId);
            if (map == null) {
                map = new HashMap<>();
                map.put(paintAccount, transactions);
            } else {
                List<Transaction> syncTrans = map.get(paintAccount);
                if (syncTrans == null) {
                    syncTrans = new ArrayList<>();
                }
                syncTrans.addAll(transactions);
                map.put(paintAccount, syncTrans);
            }
            syncCache.put(sessionId, map);
        }
    }

    private void RevokeOrClearCache(String sessionId, String account, int step) {
        // 白板还没有初始化，收到的信息，存储在syncCache中。若撤回或者清空，不处理，则会数据冗余
        if (!TransactionCenter.getInstance().isDoodleViewInited()) {
            if (step == Transaction.ActionStep.CLEAR) {
                syncCache.remove(sessionId);
            } else if (step == Transaction.ActionStep.REVOKE) {
                Map<String, List<Transaction>> map = syncCache.get(sessionId);
                if (map != null) {
                    List<Transaction> t = map.get(account);
                    for (int i = t.size() - 1; i >= 0; i--) {
                        if (t.get(i).getStep() != Transaction.ActionStep.START) {
                            t.remove(i);
                            continue;
                        }
                        t.remove(i);
                        break;
                    }
                    map.put(account, t);
                    syncCache.put(sessionId, map);
                }
            }
        }
    }

    private List<Transaction> unpack(String data) {
        if (TextUtils.isEmpty(data)) {
            return null;
        }

        List<Transaction> transactions = new ArrayList<>();
        String[] pieces = data.split(";");
        for (String p : pieces) {
            Transaction t = Transaction.unpack(p);
            if (t != null) {
                transactions.add(t);
            }
        }

        return transactions;
    }

    public Map<String, List<Transaction>> getSyncDataMap(String sessionId) {
        return syncCache.get(sessionId);
    }

    public Map<String, Map<String, List<Transaction>>> getSyncCache() {
        return syncCache;
    }

    public boolean isDoodleViewInited() {
        return isDoodleViewInited;
    }

    public void setDoodleViewInited(boolean doodleViewInited) {
        isDoodleViewInited = doodleViewInited;
    }
}
