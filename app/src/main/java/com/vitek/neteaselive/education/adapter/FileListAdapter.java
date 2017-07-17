package com.vitek.neteaselive.education.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.document.DocumentManager;
import com.netease.nimlib.sdk.document.model.DMDocTransQuality;
import com.netease.nimlib.sdk.nos.NosService;
import com.vitek.neteaselive.R;
import com.vitek.neteaselive.base.util.log.LogUtil;
import com.vitek.neteaselive.education.model.Document;
import com.vitek.neteaselive.education.model.FileDownloadStatusEnum;
import com.vitek.neteaselive.im.ui.dialog.EasyAlertDialogHelper;
import com.vitek.neteaselive.im.util.file.AttachmentStore;
import com.vitek.neteaselive.im.util.storage.StorageType;
import com.vitek.neteaselive.im.util.storage.StorageUtil;

import java.util.List;
import java.util.Map;

/**
 * Created by hzxuwen on 2016/12/14.
 */

public class FileListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final static int TYPE_ITEM = 0;
    private final static int TYPE_FOOTER = 1;

    private Context context;
    private LayoutInflater layoutInflater;
    private List<Document> documentList;

    private ViewClickListener viewClickListener;

    public FileListAdapter(Context context, List<Document> documentList) {
        this.context = context;
        this.documentList = documentList;
        this.layoutInflater = LayoutInflater.from(context);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_ITEM) {
            return new FileViewHolder(layoutInflater.inflate(R.layout.file_item, parent, false), viewClickListener);
        } else if (viewType == TYPE_FOOTER) {
            return new FooterViewholder(layoutInflater.inflate(R.layout.file_footer, parent, false));
        }
        return null;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FileViewHolder) {
            FileViewHolder fileViewHolder = (FileViewHolder) holder;
            fileViewHolder.fileNameText.setText(documentList.get(position).getDmData().getDocName());
            FileDownloadStatusEnum fileDownloadStatusEnum = documentList.get(position).getFileDownloadStatusEnum();
            switch (fileDownloadStatusEnum) {
                case DownLoaded:
                    fileViewHolder.operationBtn.setText(R.string.use);
                    fileViewHolder.operationBtn.setBackgroundResource(R.drawable.nim_blue_btn);
                    break;
                case NotDownload:
                    fileViewHolder.operationBtn.setText(R.string.download_to_use);
                    fileViewHolder.operationBtn.setBackgroundResource(R.drawable.nim_blue_btn);
                    break;
                case Downloading:
                    fileViewHolder.operationBtn.setText(R.string.downloading);
                    fileViewHolder.operationBtn.setBackgroundResource(R.drawable.nim_blue_btn_pressed);
                    break;
                case Retry:
                    fileViewHolder.operationBtn.setText(R.string.retry);
                    fileViewHolder.operationBtn.setBackgroundResource(R.drawable.g_red_long_btn_nomal);
                    break;
            }

            if (documentList.get(position).getPathMap() != null
                    && !TextUtils.isEmpty(documentList.get(position).getPathMap().get(1))) {
                Bitmap bitmap = BitmapFactory.decodeFile(documentList.get(position).getPathMap().get(1));
                fileViewHolder.fileImageView.setImageBitmap(bitmap);
            }
        }
    }

    @Override
    public int getItemCount() {
        return documentList != null ? documentList.size() +1 : 0;
    }

    @Override
    public int getItemViewType(int position) {
        if (isFooterView(position)) {
            return TYPE_FOOTER;
        }
        return TYPE_ITEM;
    }

    private boolean isFooterView(int position) {
        return position == documentList.size();
    }

    protected class FileViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        // view
        public ImageView fileImageView;
        public TextView fileNameText;
        public TextView operationBtn;
        public TextView deletebtn;

        // data
        private ViewClickListener viewClickListener;

        public FileViewHolder(View itemView, ViewClickListener viewClickListener) {
            super(itemView);
            fileImageView = (ImageView) itemView.findViewById(R.id.file_image);
            fileNameText = (TextView) itemView.findViewById(R.id.file_name);
            operationBtn = (TextView) itemView.findViewById(R.id.operation_btn);
            deletebtn = (TextView) itemView.findViewById(R.id.delete_btn);
            this.viewClickListener = viewClickListener;

            setClickListener();
        }

        private void setClickListener() {
            operationBtn.setOnClickListener(this);
            deletebtn.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.operation_btn:
                    doOperation();
                    break;
                case R.id.delete_btn:
                    confirmDelete();
                    break;
            }
        }

        private void doOperation() {
            // check btn name.if use, then onclick
            // if download, then download
            // if retry, then download again
            Document document = documentList.get(getAdapterPosition());
            if (operationBtn.getText().equals(context.getString(R.string.use))) {
                if (document.getPathMap().size() != document.getDmData().getPageNum()){
                    Map<Integer, String> pathMap = document.getPathMap();
                    for (int i = 1; i <= document.getDmData().getPageNum(); i++) {
                        String path = StorageUtil.getWritePath(document.getDmData().getDocName() + i, StorageType.TYPE_FILE);
                        pathMap.put(i, path);
                    }
                    document.setPathMap(pathMap);
                }
                viewClickListener.onOperationClick(document, getAdapterPosition());
            } else if (operationBtn.getText().equals(context.getString(R.string.download_to_use))
                    || operationBtn.getText().equals(context.getString(R.string.retry))) {
                downLoadFile();
            }
        }

        private void downLoadFile() {
            Document dmData = documentList.get(getAdapterPosition());
            doDownLoad(dmData, 1);
        }

        private void doDownLoad(final Document document, final int pageNum) {
            updateOperationUI(FileDownloadStatusEnum.Downloading);
            if (document != null && document.getDmData() != null
                    && pageNum > document.getDmData().getPageNum()) {
                document.setFileDownloadStatusEnum(FileDownloadStatusEnum.DownLoaded);
                updateOperationUI(FileDownloadStatusEnum.DownLoaded);
                updateFileImageUI();
                return;
            }
            String path = StorageUtil.getWritePath(document.getDmData().getDocName() + pageNum, StorageType.TYPE_FILE);
            String url = document.getDmData().getTransCodedUrl(pageNum, DMDocTransQuality.MEDIUM);
            Map<Integer, String> pathMap = document.getPathMap();
            pathMap.put(pageNum, path);
            document.setPathMap(pathMap);
            NIMClient.getService(NosService.class).download(url, null, path).setCallback(new RequestCallback() {
                @Override
                public void onSuccess(Object o) {
                    LogUtil.i("FileViewHolder", "download success, page:" + pageNum);
                    doDownLoad(document, pageNum + 1);
                }

                @Override
                public void onFailed(int i) {
                    LogUtil.i("FileViewHolder", "download doc failed, code:" + i);
                    updateOperationUI(FileDownloadStatusEnum.Retry);
                }

                @Override
                public void onException(Throwable throwable) {
                    LogUtil.i("FileViewHolder", "download doc failed, error:" + throwable.toString());
                    updateOperationUI(FileDownloadStatusEnum.Retry);
                }
            });
        }

        private void confirmDelete() {
            EasyAlertDialogHelper.createOkCancelDiolag(context, context.getString(R.string.operation_confirm),
                    context.getString(R.string.confirm_to_delete), context.getString(R.string.delete), context.getString(R.string.cancel), true,
                    new EasyAlertDialogHelper.OnDialogActionListener() {
                        @Override
                        public void doCancelAction() {

                        }

                        @Override
                        public void doOkAction() {
                            deleteFile();
                        }
                    }).show();
        }

        private void deleteFile() {
            Document document = documentList.get(getAdapterPosition());
            deleteLocalFile(document);
            DocumentManager.getInstance().delete(document.getDmData().getDocId(), new RequestCallback<Void>() {
                @Override
                public void onSuccess(Void aVoid) {

                }

                @Override
                public void onFailed(int i) {
                }

                @Override
                public void onException(Throwable throwable) {
                }
            });
            viewClickListener.onDeleteClick(document.getDmData().getDocId(), getAdapterPosition());
        }

        private void deleteLocalFile(Document document) {
            Map<Integer, String> map = document.getPathMap();
            for (Map.Entry<Integer, String> entry : map.entrySet()) {
                AttachmentStore.delete(entry.getValue());
            }
        }

        private void updateOperationUI(FileDownloadStatusEnum fileDownloadStatusEnum) {
            int left = operationBtn.getPaddingLeft();
            int right = operationBtn.getPaddingRight();
            switch (fileDownloadStatusEnum) {
                case Downloading:
                    operationBtn.setText(R.string.downloading);
                    operationBtn.setBackgroundResource(R.drawable.nim_blue_btn_pressed);
                    break;
                case DownLoaded:
                    operationBtn.setText(R.string.use);
                    operationBtn.setBackgroundResource(R.drawable.nim_blue_btn);
                    break;
                case Retry:
                    operationBtn.setText(R.string.retry);
                    operationBtn.setBackgroundResource(R.drawable.g_red_long_btn_nomal);
                    break;
            }
            operationBtn.setPadding(left, 0, right, 0);
        }

        public void updateFileImageUI() {
            if (getAdapterPosition() == -1) {
                return;
            }
            Bitmap bitmap = BitmapFactory.decodeFile(documentList.get(getAdapterPosition()).getPathMap().get(1));
            fileImageView.setImageBitmap(bitmap);
        }
    }

    protected final static class FooterViewholder extends RecyclerView.ViewHolder {

        public FooterViewholder(View itemView) {
            super(itemView);
        }
    }

    public interface ViewClickListener {
        void onOperationClick(Document doc, int position);
        void onDeleteClick(String docId, int position);
    }

    public ViewClickListener getViewClickListener() {
        return viewClickListener;
    }

    public void setViewClickListener(ViewClickListener viewClickListener) {
        this.viewClickListener = viewClickListener;
    }
}
