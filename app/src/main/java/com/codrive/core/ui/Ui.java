package com.codrive.core.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 목업 화면에서 공유하는 기본 뷰 생성과 크기 변환입니다. */
public final class Ui {
    public static final int TEXT = Color.rgb(237, 246, 255);
    public static final int MUTED = Color.rgb(151, 170, 189);
    public static final int MINT = Color.rgb(93, 229, 205);
    private Ui() {}
    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    public static LinearLayout column(Context c) {
        return layout(c, LinearLayout.VERTICAL);
    }
    public static LinearLayout row(Context c) {
        return layout(c, LinearLayout.HORIZONTAL);
    }
    private static LinearLayout layout(Context c, int orientation) {
        LinearLayout view = new LinearLayout(c);
        view.setOrientation(orientation);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    public static TextView text(Context c, String text, float size, int color) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setFontFeatureSettings("kern");
        return v;
    }
    public static TextView button(Context c, String text, int background) {
        TextView v = text(c, text, 14, TEXT);
        v.setGravity(Gravity.CENTER);
        v.setMinHeight(dp(c, 56));
        pad(v, 16, 10, 16, 10);
        v.setBackground(
                new RippleDrawable(ColorStateList.valueOf(0x335de5cd), shape(c, background, 12), null));
        v.setFocusable(true);
        v.setClickable(true);
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                    View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
        return v;
    }
    public static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        return d;
    }
    public static void bold(TextView v) {
        v.setTypeface(v.getTypeface(), Typeface.BOLD);
    }
    public static void pad(View v, int left, int top, int right, int bottom) {
        Context c = v.getContext();
        v.setPadding(dp(c, left), dp(c, top), dp(c, right), dp(c, bottom));
    }
    public static LinearLayout.LayoutParams size(Context c, int w, int h) {
        return new LinearLayout.LayoutParams(w < 0 ? w : dp(c, w), h < 0 ? h : dp(c, h));
    }
    public static void gap(LinearLayout parent, int height) {
        parent.addView(new View(parent.getContext()), size(parent.getContext(), 1, height));
    }
}
