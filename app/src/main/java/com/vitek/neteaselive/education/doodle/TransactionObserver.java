package com.vitek.neteaselive.education.doodle;

import java.util.List;

/**
 * Created by huangjun on 2015/6/24.
 */
public interface TransactionObserver {
    void onTransaction(String account, List<Transaction> transactions);
}
