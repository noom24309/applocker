package com.ads.control.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;


import com.ads.control.R;


public class InAppDialog extends Dialog {
    private Context mContext;
    private ICallback callback;

    public void setCallback(ICallback callback) {
        this.callback = callback;
    }

    public InAppDialog(Context context) {
        super(context, R.style.AppTheme);
        mContext = context;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_inapp);
        initView();
    }

    private void initView() {
        findViewById(R.id.iv_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });

        findViewById(R.id.tv_pucharse).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                callback.onPurcharse();
            }
        });
        TextView tvOldPrice = findViewById(R.id.tv_old_price);
        TextView tvPrice = findViewById(R.id.tv_price);
        tvOldPrice.setPaintFlags(tvOldPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
    }

    public interface ICallback {
        void onPurcharse();
    }
}
