// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Native modal popup, with the approved two-column layout and original donation QR. */
final class AboutDialog {
    static final String LIGHTNING_ADDRESS = "pumba22@blink.sv";
    private static final int PANEL = Color.rgb(13,25,41);
    private static final int COPY = Color.rgb(231,237,245);
    private static final int RULE = Color.rgb(52,74,99);
    private AboutDialog() { }
    private static int dp(Context c,int value) { return TvTheme.dp(c,value); }

    static AlertDialog show(LockedActivity owner) {
        DisplayMetrics metrics = owner.getResources().getDisplayMetrics();
        int width = Math.min(dp(owner,736),metrics.widthPixels-dp(owner,64));
        int maxHeight = metrics.heightPixels-dp(owner,64);
        Panel root = new Panel(owner,maxHeight);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(owner,24),dp(owner,18),dp(owner,24),dp(owner,18));
        root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        GradientDrawable background = new GradientDrawable(); background.setColor(PANEL);
        background.setCornerRadius(dp(owner,18)); background.setStroke(dp(owner,1),Color.rgb(113,134,159));
        root.setBackground(background);

        TextView heading = text(owner,owner.getString(R.string.about_title),27,true);
        root.addView(heading);
        divider(root,16,0);

        LinearLayout content = new LinearLayout(owner);
        content.setOrientation(LinearLayout.HORIZONTAL); content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(0,dp(owner,16),0,dp(owner,16));
        LinearLayout copy = new LinearLayout(owner); copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0,-2,1f);
        copyParams.rightMargin = dp(owner,24); content.addView(copy,copyParams);

        SpannableString introduction = new SpannableString(owner.getString(R.string.about_intro));
        introduction.setSpan(new StyleSpan(Typeface.BOLD),0,"TV Vault".length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView intro = text(owner,introduction,18,false); copy.addView(intro);
        paragraph(copy,R.string.about_support,21,true,17);
        paragraph(copy,R.string.about_free,18,false,14);
        paragraph(copy,R.string.about_donate,18,false,14);
        paragraph(copy,R.string.about_scan,18,true,14);
        paragraph(copy,R.string.about_thanks,18,false,14);

        LinearLayout qrColumn = new LinearLayout(owner); qrColumn.setOrientation(LinearLayout.VERTICAL);
        qrColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        int qrSize = dp(owner,228);
        content.addView(qrColumn,new LinearLayout.LayoutParams(qrSize,-2));
        LightningQr qr = new LightningQr(owner);
        qr.setContentDescription(owner.getString(R.string.about_qr_description));
        qrColumn.addView(qr,new LinearLayout.LayoutParams(qrSize,qrSize));
        TextView address = text(owner,LIGHTNING_ADDRESS,19,true);
        address.setTextColor(Color.rgb(162,231,255)); address.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(-1,-2); addressParams.topMargin=dp(owner,14);
        qrColumn.addView(address,addressParams);
        TextView network = text(owner,"Bitcoin Lightning ⚡",14,false); network.setGravity(Gravity.CENTER);
        network.setTextColor(Color.rgb(191,206,222));
        LinearLayout.LayoutParams networkParams=new LinearLayout.LayoutParams(-1,-2); networkParams.topMargin=dp(owner,8);
        qrColumn.addView(network,networkParams);

        // The body alone can scroll for enlarged system fonts; the title and Close stay visible.
        ScrollView scroll = new ScrollView(owner); scroll.setFillViewport(false); scroll.setFocusable(false);
        scroll.addView(content,new ViewGroup.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,-2,1f));
        divider(root,0,16);
        Button close = TvTheme.button(owner,R.string.about_close);
        root.addView(close,new LinearLayout.LayoutParams(-1,dp(owner,48)));

        AlertDialog dialog = new AlertDialog.Builder(owner).create();
        dialog.setView(root,0,0,0,0);
        dialog.setCanceledOnTouchOutside(false);
        close.setOnClickListener(v -> dialog.dismiss());
        android.view.inputmethod.InputMethodManager keyboard = owner.getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(owner.getWindow().getDecorView().getWindowToken(),0);
        owner.showCustomSecureDialog(dialog);
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0,0,0,0);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.55f); window.setGravity(Gravity.CENTER);
        window.setLayout(width,WindowManager.LayoutParams.WRAP_CONTENT);
        close.requestFocus();
        return dialog;
    }
    private static TextView text(Context context,CharSequence value,int size,boolean bold) {
        TextView view = new TextView(context); view.setText(value); view.setTextSize(size);
        view.setTextColor(COPY); view.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);
        view.setIncludeFontPadding(false); view.setLineSpacing(0,1.35f);
        return view;
    }
    private static void paragraph(LinearLayout parent,int resource,int size,boolean bold,int top) {
        TextView view=text(parent.getContext(),parent.getContext().getString(resource),size,bold);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.topMargin=dp(parent.getContext(),top);
        parent.addView(view,params);
    }
    private static void divider(LinearLayout parent,int top,int bottom) {
        View line=new View(parent.getContext()); line.setBackgroundColor(RULE);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(parent.getContext(),1));
        params.topMargin=dp(parent.getContext(),top);params.bottomMargin=dp(parent.getContext(),bottom);
        parent.addView(line,params);
    }
    private static final class Panel extends LinearLayout {
        private final int maxHeight;
        Panel(Context context,int maxHeight) { super(context); this.maxHeight=maxHeight; }
        @Override protected void onMeasure(int width,int height) {
            int limit=MeasureSpec.getMode(height)==MeasureSpec.UNSPECIFIED?maxHeight:Math.min(maxHeight,MeasureSpec.getSize(height));
            super.onMeasure(width,MeasureSpec.makeMeasureSpec(limit,MeasureSpec.AT_MOST));
        }
    }
    private static final class LightningQr extends View {
        private final Bitmap image;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect originalQr = new Rect(45,192,879,1026);
        LightningQr(Context context) { super(context);image=BitmapFactory.decodeResource(getResources(),R.drawable.donation_lightning); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(Color.WHITE); paint.setFilterBitmap(false);
            canvas.drawRoundRect(new RectF(0,0,getWidth(),getHeight()),dp(getContext(),16),dp(getContext(),16),paint);
            float inset=getWidth()*21f/240f;
            RectF destination = new RectF(inset,inset,getWidth()-inset,getHeight()-inset);
            Path rounded = new Path(); rounded.addRoundRect(destination,dp(getContext(),14),dp(getContext(),14),Path.Direction.CW);
            canvas.save(); canvas.clipPath(rounded);
            canvas.drawBitmap(image,originalQr,destination,paint);
            canvas.restore();
        }
    }
}
