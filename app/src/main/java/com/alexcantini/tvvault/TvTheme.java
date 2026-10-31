// SPDX-License-Identifier: GPL-3.0-only
package com.alexcantini.tvvault;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

/** High-contrast controls shared by every TV screen, including platform dialogs. */
public final class TvTheme {
    public static final int BG = Color.rgb(8, 15, 24);
    public static final int TEXT = Color.WHITE;
    public static final int SECONDARY = Color.rgb(217, 229, 244);
    public static final int FOCUS = Color.rgb(9, 91, 171);
    public static final int EDGE = Color.rgb(103, 213, 255);
    public static final int BORDER = Color.rgb(155, 181, 207);
    private TvTheme() { }
    public static int dp(Context c, int value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }

    private static GradientDrawable shape(Context c, boolean focus, boolean transparent) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(focus ? FOCUS : transparent ? Color.TRANSPARENT : Color.rgb(23,43,67));
        shape.setCornerRadius(dp(c,8));
        shape.setStroke(dp(c,focus?3:2),focus?EDGE:transparent?Color.TRANSPARENT:BORDER);
        return shape;
    }
    private static StateListDrawable background(Context c, boolean transparent) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},shape(c,true,transparent));
        states.addState(new int[]{android.R.attr.state_pressed},shape(c,true,transparent));
        states.addState(new int[]{},shape(c,false,transparent));
        return states;
    }
    public static Button button(Context c,int text) { Button b=new Button(c); b.setText(text); styleButton(b); return b; }
    public static void styleButton(Button b) {
        Context c=b.getContext();
        b.setAllCaps(false); b.setTextSize(20); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},
                new int[]{Color.rgb(184,201,220),TEXT}));
        b.setBackgroundTintList(null); b.setBackground(background(c,false)); b.setStateListAnimator(null);
        b.setElevation(0); b.setMinHeight(dp(c,52)); b.setPadding(dp(c,16),dp(c,8),dp(c,16),dp(c,8));
        b.setFocusable(true); b.setFocusableInTouchMode(true); b.setAlpha(1f);
        b.setOnFocusChangeListener(null);
    }
    public static void styleField(EditText field) {
        field.setTextColor(TEXT); field.setHintTextColor(SECONDARY); field.setTextSize(20);
        field.setBackgroundTintList(ColorStateList.valueOf(BORDER));
        field.setOnFocusChangeListener((v,focus)->field.setBackgroundTintList(ColorStateList.valueOf(focus?EDGE:BORDER)));
    }
    public static void styleCheckbox(CheckBox b) {
        Context c=b.getContext(); b.setTextColor(TEXT); b.setTextSize(20);
        b.setPadding(dp(c,8),dp(c,6),dp(c,8),dp(c,6));
        b.setBackground(background(c,true));
        b.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{EDGE,SECONDARY}));
    }
    public static void styleDialog(AlertDialog dialog) { styleTree(dialog.getWindow().getDecorView()); }
    private static void styleTree(View view) {
        if(view instanceof Button) styleButton((Button)view);
        else if(view instanceof TextView) { ((TextView)view).setTextColor(TEXT); ((TextView)view).setTextSize(20); }
        if(view instanceof ViewGroup) {
            view.setBackgroundColor(BG);
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)styleTree(group.getChildAt(i));
        }
    }
}
