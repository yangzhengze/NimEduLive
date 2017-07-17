package com.vitek.neteaselive.education.doodle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.vitek.neteaselive.NimCache;
import com.vitek.neteaselive.base.util.log.LogUtil;
import com.vitek.neteaselive.education.doodle.action.Action;
import com.vitek.neteaselive.education.doodle.action.MyFillCircle;
import com.vitek.neteaselive.education.doodle.action.MyPath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 涂鸦板控件（基类）
 * <p/>
 * Created by huangjun on 2015/6/24.
 */
public class DoodleView extends SurfaceView implements SurfaceHolder.Callback, TransactionObserver {

    public interface FlipListener {
        void onFlipPage(Transaction transaction);
    }

    public enum Mode {
        PAINT,
        PLAYBACK,
        BOTH
    }

    private final String TAG = "DoodleView";

    private SurfaceHolder surfaceHolder;

    private DoodleChannel paintChannel; // 绘图通道，自己本人使用

    private DoodleChannel laserChannel; // 激光笔绘图通道

    private Map<String, DoodleChannel> playbackChannelMap = new HashMap<>(); // 其余的人，一个人对应一个通道

    private TransactionManager transactionManager; // 数据发送管理器

    private int bgColor = Color.WHITE; // 背景颜色
    private int paintColor = Color.BLACK; // 默认画笔颜色

    private float xZoom = 1.0f; // 收发数据时缩放倍数（归一化）
    private float yZoom = 1.0f;

    private float paintOffsetY = 0.0f; // 绘制时的Y偏移（去掉ActionBar,StatusBar,marginTop等高度）
    private float paintOffsetX = 0.0f; // 绘制事的X偏移（去掉marginLeft的宽度）

    private float lastX = 0.0f;
    private float lastY = 0.0f;

    private boolean enableView = true;
    private boolean isSurfaceViewCreated = false;
    private boolean isSyncAlready = false;

    private String sessionId;

    private int paintSize;
    private int paintType;

    // <account, transactions>
    private Map<String, List<Transaction>> userDataMap = new HashMap<>();

    private FlipListener flipListener;

    public DoodleView(Context context) {
        super(context);
        init();
    }

    public DoodleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public DoodleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        surfaceHolder = this.getHolder();
        surfaceHolder.addCallback(this);
        this.setFocusable(true);
    }

    /**
     * 初始化（必须调用）
     *
     * @param mode    设置板书模式
     * @param bgColor 设置板书的背景颜色
     */
    public void init(String sessionId, String toAccount, Mode mode, int bgColor, int paintColor, Context context, FlipListener flipListener) {
        TransactionCenter.getInstance().setDoodleViewInited(true);
        this.sessionId = sessionId;
        this.flipListener = flipListener;
        this.transactionManager = new TransactionManager(sessionId, toAccount, context);

        if (mode == Mode.PAINT || mode == Mode.BOTH) {
            this.paintChannel = new DoodleChannel();
            this.laserChannel = new DoodleChannel();
            laserChannel.setSize(10);
        }

        if (mode == Mode.PLAYBACK || mode == Mode.BOTH) {
            this.transactionManager.registerTransactionObserver(this);
        }

        this.bgColor = bgColor;
        this.paintColor = paintColor;
        paintChannel.setColor(paintColor);
        clearAll();
    }

    public void onResume() {
        new Handler(getContext().getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                Canvas canvas = surfaceHolder.lockCanvas();
                if (canvas == null) {
                    return;
                }
                drawHistoryActions(canvas);
                surfaceHolder.unlockCanvasAndPost(canvas);
                Map<String, List<Transaction>> map = TransactionCenter.getInstance().getSyncDataMap(sessionId);
                if (map != null && !isSyncAlready) {
                    isSyncAlready = true;
                    Map<String, List<Transaction>> tempMap = new HashMap<>();
                    tempMap.putAll(map);
                    for (Map.Entry<String, List<Transaction>> entry : tempMap.entrySet()) {
                        if (entry.getKey().equals(NimCache.getAccount())) {
                            // 异常退出的情况，里面可能有我自己画的数据
                            onSyncMyTransactionsDraw(entry.getValue());
                        } else {
                            DoodleChannel playbackChannel = initPlaybackChannel(entry.getKey());
                            onMultiTransactionsDraw(playbackChannel, entry.getKey(), entry.getValue());
                        }
                    }
                    clearSyncData();
                }
            }
        }, 50);
    }

    private void clearSyncData() {
        TransactionCenter.getInstance().getSyncCache().remove(sessionId);
        // 同步数据绘制完毕，需要将自己画笔颜色修改为初始化颜色。
        paintChannel.paintColor = this.paintColor;
    }

    /**
     * 退出涂鸦板时调用
     */
    public void end() {
        if (transactionManager != null) {
            transactionManager.end();
        }

        Map<String, Map<String, List<Transaction>>> syncCacheMap
                = TransactionCenter.getInstance().getSyncCache();
        if (syncCacheMap != null) {
            syncCacheMap.remove(sessionId);
        }

        TransactionCenter.getInstance().setDoodleViewInited(false);
    }

    /**
     * 设置绘制时画笔的偏移
     *
     * @param x DoodleView的MarginLeft的宽度
     * @param y ActionBar与StatusBar及DoodleView的MarginTop的高度的和
     */
    public void setPaintOffset(float x, float y) {
        this.paintOffsetX = x;
        this.paintOffsetY = y;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isSurfaceViewCreated = true;
        onPaintBackground();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.i(TAG, "surfaceView created, width = " + width + ", height = " + height);
        xZoom = width;
        yZoom = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isSurfaceViewCreated = false;
    }

    /**
     * ******************************* 绘图板 ****************************
     */

    /**
     * 设置绘制时的画笔颜色
     *
     * @param color
     */
    public void setPaintColor(int color) {
        this.paintChannel.setColor(convertRGBToARGB(color));
    }

    /**
     * 设置回放时的画笔颜色
     *
     * @param color
     */
    public void setPlaybackColor(DoodleChannel playbackChannel, int color) {
        playbackChannel.setColor(convertRGBToARGB(color));
    }

    /**
     * rgb颜色值转换为argb颜色值
     *
     * @param rgb
     * @return
     */
    public int convertRGBToARGB(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = (rgb >> 0) & 0xFF;

        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * 设置画笔的粗细
     *
     * @param size
     */
    public void setPaintSize(int size) {
        if (size > 0) {
            this.paintChannel.paintSize = size;
            this.paintSize = size;
        }
    }

    /**
     * 设置当前画笔的形状
     *
     * @param type
     */
    public void setPaintType(int type) {
        this.paintChannel.setType(type);
        this.paintType = type;
    }

    /**
     * 设置当前画笔为橡皮擦
     *
     * @param size 橡皮擦的大小（画笔的粗细)
     */
    public void setEraseType(int size) {
        this.paintChannel.setEraseType(this.bgColor, size);
    }

    public void setEnableView(boolean enableView) {
        this.enableView = enableView;
    }

    /**
     * 撤销一步
     *
     * @return 撤销是否成功
     */
    public boolean paintBack() {
        if (paintChannel == null) {
            return false;
        }

        boolean res = back(NimCache.getAccount(), true);
        transactionManager.sendRevokeTransaction();
        return res;
    }

    /**
     * 清空
     */
    public void clear() {
        clearAll();
        transactionManager.sendClearSelfTransaction();
    }

    /**
     * 发送同步准备指令
     */
    public void sendSyncPrepare() {
        transactionManager.sendSyncPrepareTransaction();
    }

    /**
     * 触摸绘图
     *
     * @param event
     * @return
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!enableView) {
            return true;
        }
        int action = event.getAction();
        if (action == MotionEvent.ACTION_CANCEL) {
            return false;
        }

        float touchX = event.getRawX();
        float touchY = event.getRawY();
        touchX -= paintOffsetX;
        touchY -= paintOffsetY;
        Log.i(TAG, "x=" + touchX + ", y=" + touchY);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                onPaintActionStart(touchX, touchY);
                break;
            case MotionEvent.ACTION_MOVE:
                onPaintActionMove(touchX, touchY);
                break;
            case MotionEvent.ACTION_UP:
                onPaintActionEnd(touchX, touchY);
                break;
            default:
                break;
        }

        return true;
    }

    private void onPaintActionStart(float x, float y) {
        if (paintChannel == null) {
            return;
        }

        onActionStart(x, y);
        transactionManager.sendStartTransaction(x / xZoom, y / yZoom, paintChannel.paintColor);
        saveUserData(NimCache.getAccount(), new Transaction(Transaction.ActionStep.START, x / xZoom, y / yZoom, paintChannel.paintColor), false, false, false);
    }

    private void onPaintActionMove(float x, float y) {
        if (paintChannel == null) {
            return;
        }

        if (!isNewPoint(x, y)) {
            return;
        }

        onActionMove(x, y);
        transactionManager.sendMoveTransaction(x / xZoom, y / yZoom, paintChannel.paintColor);
        saveUserData(NimCache.getAccount(), new Transaction(Transaction.ActionStep.MOVE, x / xZoom, y / yZoom, paintChannel.paintColor), false, false, false);
    }

    private void onPaintActionEnd(float x, float y) {
        if (paintChannel == null) {
            return;
        }

        onActionEnd();
        transactionManager.sendEndTransaction(lastX / xZoom, lastY / yZoom, paintChannel.paintColor);
        saveUserData(NimCache.getAccount(), new Transaction(Transaction.ActionStep.END, lastX / xZoom, lastY / yZoom, paintChannel.paintColor), false, false, false);
    }


    /**
     * ******************************* 回放板 ****************************
     */

    @Override
    public void onTransaction(String account, final List<Transaction> transactions) {
        if (transactions.size() > 0 && transactions.get(0).isSync()
                && transactions.get(0).getUid().equals(NimCache.getAccount())) {
            // 断网重连，主播同步数据，收到自己的数据
            if (!isSurfaceViewCreated) {
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        onSyncMyTransactionsDraw(transactions);
                    }
                }, 50);
            } else {
                onSyncMyTransactionsDraw(transactions);
            }
            isSyncAlready = true;
            return;
        }

        DoodleChannel playbackChannel = initPlaybackChannel(account);

        List<Transaction> cache = new ArrayList<>(transactions.size());
        for (Transaction t : transactions) {
            if (t == null) {
                continue;
            }
            if (t.isPaint()) {
                // 正常画笔
                cache.add(t);
            } else {
                onMultiTransactionsDraw(playbackChannel, account, cache);
                cache.clear();
                if (t.isRevoke()) {
                    back(account, false);
                } else if (t.isClearSelf()) {
                    clearAll();
                    transactionManager.sendClearAckTransaction();
                } else if (t.isClearAck()) {
                    clearAll();
                } else if (t.isSyncRequest()) {
                    sendSyncData(account);
                } else if (t.isSyncPrepare()) {
                    clearAll();
                    transactionManager.sendSyncPrepareAckTransaction();
                } else if (t.isFlip()) {
                    // 收到翻页消息。先清空白板，然后做翻页操作。
                    LogUtil.i(TAG, "receive flip msg");
                    flipListener.onFlipPage(t);
                }
            }
        }

        if (cache.size() > 0) {
            onMultiTransactionsDraw(playbackChannel, account, cache);
            cache.clear();
        }
    }

    private void setPlaybackEraseType(DoodleChannel playbackChannel, int size) {
        playbackChannel.setEraseType(this.bgColor, size);
    }

    private DoodleChannel initPlaybackChannel(String account) {
        DoodleChannel playbackChannel;
        if (playbackChannelMap.get(account) == null) {
            playbackChannel = new DoodleChannel();
            playbackChannel.paintSize = this.paintSize;
            playbackChannel.setType(this.paintType);
            playbackChannelMap.put(account, playbackChannel);
        } else {
            playbackChannel = playbackChannelMap.get(account);
        }

        return playbackChannel;
    }

    /**
     * ******************************* 基础绘图封装 ****************************
     */

    private void onPaintBackground() {
        Canvas canvas = surfaceHolder.lockCanvas();
        if (canvas == null) {
            return;
        }
        canvas.drawColor(bgColor);  // 涂鸦板背景颜色
        surfaceHolder.unlockCanvasAndPost(canvas);
    }

    private void onActionStart(float x, float y) {
        DoodleChannel channel = paintChannel;
        if (channel == null) {
            return;
        }

        lastX = x;
        lastY = y;
        Canvas canvas = surfaceHolder.lockCanvas();
        drawHistoryActions(canvas);
        channel.action = new MyPath(x, y, channel.paintColor, channel.paintSize);
        channel.action.onDraw(canvas);
        if (canvas != null) {
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void onActionMove(float x, float y) {
        DoodleChannel channel = paintChannel;
        if (channel == null) {
            return;
        }
        Canvas canvas = surfaceHolder.lockCanvas();
        drawHistoryActions(canvas);
        // 绘制当前Action
        if (channel.action == null) {
            // 有可能action被清空，此时收到move，重新补个start
            onPaintActionStart(x, y);
        }
        channel.action.onMove(x, y);
        channel.action.onDraw(canvas);
        if (canvas != null) {
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void onActionEnd() {
        DoodleChannel channel = paintChannel;
        if (channel == null || channel.action == null) {
            return;
        }

        channel.actions.add(channel.action);
        channel.action = null;
    }

    private void onSyncMyTransactionsDraw(List<Transaction> transactions) {
        int tempColor = paintChannel.paintColor;
        for (Transaction t : transactions) {
            switch (t.getStep()) {
                case Transaction.ActionStep.START:
                    paintChannel.paintColor = convertRGBToARGB(t.getRgb());
                    onActionStart(t.getX() * xZoom, t.getY() * yZoom);
                    break;
                case Transaction.ActionStep.MOVE:
                    onActionMove(t.getX() * xZoom, t.getY() * yZoom);
                    break;
                case Transaction.ActionStep.END:
                    onActionEnd();
                    break;
                default:
                    break;
            }
        }
        paintChannel.paintColor = tempColor;
    }

    private void onMultiTransactionsDraw(DoodleChannel playbackChannel, String account, List<Transaction> transactions) {
        if (transactions == null || transactions.size() == 0 || playbackChannel == null) {
            return;
        }
        Canvas canvas = surfaceHolder.lockCanvas();
        for (Transaction t : transactions) {
            switch (t.getStep()) {
                case Transaction.ActionStep.LASER_PEN:
                    // 绘制背景
                    canvas.drawColor(bgColor);
                    laserChannel.action = null;
                    laserChannel.action = new MyFillCircle(t.getX() * xZoom, t.getY() * yZoom, Color.RED,
                            laserChannel.paintSize, 10);
                    laserChannel.action.onDraw(canvas);
                    break;
                case Transaction.ActionStep.LASER_PEN_END:
                    laserChannel.action = null;
                    break;
            }
        }

        drawHistoryActions(canvas);
        // 绘制新的数据
        for (Transaction t : transactions) {
            switch (t.getStep()) {
                case Transaction.ActionStep.START:
                    if (playbackChannel.action != null) {
                        // 如果没有收到end包，在这里补提交
                        playbackChannel.actions.add(playbackChannel.action);
                    }

                    saveUserData(account, t, false, false, false);
                    setPlaybackColor(playbackChannel, t.getRgb());

                    playbackChannel.action = new MyPath(t.getX() * xZoom, t.getY() * yZoom, playbackChannel
                            .paintColor, playbackChannel.paintSize);
                    playbackChannel.action.onStart(canvas);
                    playbackChannel.action.onDraw(canvas);
                    break;
                case Transaction.ActionStep.MOVE:
                    saveUserData(account, t, false, false, false);
                    if (playbackChannel.action != null) {
                        playbackChannel.action.onMove(t.getX() * xZoom, t.getY() * yZoom);
                        playbackChannel.action.onDraw(canvas);
                    }
                    break;
                case Transaction.ActionStep.END:
                    if (playbackChannel.action != null) {

                        playbackChannel.actions.add(playbackChannel.action);
                        playbackChannel.action = null;
                    }
                    saveUserData(account, t, false, false, false);
                    break;
                default:
                    break;
            }
        }
        if (canvas != null) {
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private void saveUserData(String account, Transaction t, boolean isBack, boolean isClear, boolean isFlip) {
        List<Transaction> list = userDataMap.get(account);
        if (isBack) {
            while (list != null && list.size() > 0 && list.get(list.size() - 1).getStep() != Transaction.ActionStep.START) {
                list.remove(list.size() - 1);
            }
            if (list != null && list.size() > 0) {
                list.remove(list.size() - 1);
            }
            userDataMap.put(account, list);
        } else if (isClear) {
            userDataMap.clear();
        } else if (isFlip) {
            if (list == null) {
                list = new ArrayList<>();
                list.add(t);
            } else {
                for (Transaction transaction : list) {
                    if (transaction.getStep() == Transaction.ActionStep.Flip) {
                        list.remove(transaction);
                        break;
                    }
                }
                list.add(t);
            }
            userDataMap.put(account, list);
        } else {
            if (list == null) {
                list = new ArrayList<>();
                list.add(t);
            } else {
                list.add(t);
            }
            userDataMap.put(account, list);
        }
    }

    private void drawHistoryActions(Canvas canvas) {
        if (canvas == null) {
            return;
        }

        // 绘制背景
        canvas.drawColor(bgColor);
        if (bitmap != null) {
            int bitmapWidth = bitmap.getWidth();
            int bitmapHeight = bitmap.getHeight();
            float canvasRatio = (float) (Math.round((float) canvas.getHeight() / canvas.getWidth() * 100)) / 100;
            float bitmapRatio = (float) (Math.round((float) bitmapHeight / bitmapWidth * 100)) / 100;
            Matrix matrix = new Matrix();
            //获取缩放比例
            if (bitmapRatio > canvasRatio) {
                matrix.postScale(canvasRatio, bitmapRatio);
            } else {
                matrix.postScale(1.0f * canvas.getWidth() / bitmapWidth,
                        1.0f * canvas.getHeight() / bitmapHeight);
            }
            //按缩放比例生成适应屏幕的新的bitmap；
            Bitmap dstbmp = Bitmap.createBitmap(bitmap, 0, 0, bitmapWidth,
                    bitmapHeight, matrix, true);
            Rect src = new Rect(0, 0, dstbmp.getWidth(), dstbmp.getHeight());
            // Rect用于居中显示
            if (canvasRatio > bitmapRatio) {
                Rect dest = new Rect(0, canvas.getHeight() / 2 - dstbmp.getHeight() / 2,
                        canvas.getWidth(), canvas.getHeight() / 2 + dstbmp.getHeight() / 2);
                canvas.drawBitmap(dstbmp, src, dest, null);
            } else {
                Rect dest = new Rect(canvas.getWidth() / 2 - dstbmp.getWidth() / 2, 0,
                        canvas.getWidth() / 2 + dstbmp.getWidth() / 2, canvas.getHeight());
                canvas.drawBitmap(dstbmp, src, dest, null);
            }
        }

        Map<String, DoodleChannel> tempMap = new HashMap<>();
        tempMap.putAll(playbackChannelMap);
        Iterator<Map.Entry<String, DoodleChannel>> entries = tempMap.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, DoodleChannel> entry = entries.next();
            DoodleChannel playbackChannel = entry.getValue();
            if (playbackChannel != null && playbackChannel.actions != null) {
                CopyOnWriteArrayList<Action> tempActions = playbackChannel.actions;
                for (Iterator<Action> it = tempActions.iterator(); it.hasNext(); ) {
                    Action a = it.next();
                    a.onDraw(canvas);
                }

                // 绘制当前
                if (playbackChannel.action != null) {
                    playbackChannel.action.onDraw(canvas);
                }
            }
        }

        // 绘制所有历史Action
        if (paintChannel != null && paintChannel.actions != null) {
            for (Action a : paintChannel.actions) {
                a.onDraw(canvas);
            }

            // 绘制当前
            if (paintChannel.action != null) {
                paintChannel.action.onDraw(canvas);
            }
        }

        if (laserChannel != null && laserChannel.action != null) {
            laserChannel.action.onDraw(canvas);
        }
    }

    private boolean back(String account, boolean isPaintView) {
        DoodleChannel channel = isPaintView ? paintChannel : playbackChannelMap.get(account);
        if (channel == null) {
            return false;
        }

        if (channel.actions != null && channel.actions.size() > 0) {
            channel.actions.remove(channel.actions.size() - 1);
            saveUserData(account, null, true, false, false);
            Canvas canvas = surfaceHolder.lockCanvas();
            if (canvas == null) {
                return false;
            }
            drawHistoryActions(canvas);
            surfaceHolder.unlockCanvasAndPost(canvas);
            return true;
        }
        return false;
    }

    public void clearAll() {
        saveUserData(NimCache.getAccount(), null, false, true, false);
        // clear 回放的所有频道
        for (Map.Entry<String, DoodleChannel> entry : playbackChannelMap.entrySet()) {
            clear(entry.getValue(), false);
        }
        // clear 自己画的频道
        clear(paintChannel, true);
    }

    private void clear(DoodleChannel playbackChannel, boolean isPaintView) {
        DoodleChannel channel = isPaintView ? paintChannel : playbackChannel;
        if (channel == null) {
            return;
        }

        if (channel.actions != null) {
            channel.actions.clear();
        }
        channel.action = null;
        Canvas canvas = surfaceHolder.lockCanvas();
        if (canvas == null) {
            return;
        }
        drawHistoryActions(canvas);
        surfaceHolder.unlockCanvasAndPost(canvas);
    }

    private boolean isNewPoint(float x, float y) {
        if (Math.abs(x - lastX) <= 0.1f && Math.abs(y - lastY) <= 0.1f) {
            return false;
        }

        lastX = x;
        lastY = y;

        return true;
    }

    public void sendSyncData(String account) {
        for (String key : userDataMap.keySet()) {
            transactionManager.sendSyncTransaction(account, key, 1, userDataMap.get(key));
        }
    }

    /******************
     * 贴图
     *********************/
    private Bitmap bitmap;

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public void setImageView(Bitmap bitmap) {
        this.bitmap = bitmap;
        Canvas canvas = surfaceHolder.lockCanvas();
        if (canvas == null) {
            return;
        }

        drawHistoryActions(canvas);
        surfaceHolder.unlockCanvasAndPost(canvas);
    }

    public void sendFlipData(String docId, int currentPageNum, int pageCount, int type) {
        transactionManager.sendFlipTransaction(docId, currentPageNum, pageCount, type);
        saveUserData(NimCache.getAccount(), new Transaction().makeFlipTranscation(docId, currentPageNum, pageCount, type), false, false, true);
    }
}
