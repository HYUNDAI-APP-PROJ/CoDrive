package com.codrive.core.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import com.codrive.R;

/** 넓은 터치 영역 안에 마이크·음성 파형·인식 대기 링을 그립니다. */
public final class MicButton extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable mic;
    private final RectF ring = new RectF(-30, -30, 30, 30);
    private final ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
    private boolean listening;
    private boolean processing;
    private float phase;
    private float level;

    public MicButton(Context context) {
        super(context);
        mic = context.getDrawable(R.drawable.ic_mic);
        setContentDescription("음성으로 입력하기");
        setFocusable(true);
        setClickable(true);
        animator.setDuration(1600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
            invalidate();
        });
    }
    public void setRecording(boolean active, boolean waiting) {
        listening = active;
        processing = waiting;
        setContentDescription(active ? "음성 입력 취소" : "음성으로 입력하기");
        setSelected(active);
        if (active && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled())
            animator.start();
        else
            animator.cancel();
        invalidate();
    }
    public void setLevel(float rms) {
        level = Math.max(0, Math.min(1, rms / 12));
        invalidate();
    }
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float unit = Math.min(getWidth(), getHeight()) / 96f;
        canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.scale(unit, unit);
        paint.setStyle(Paint.Style.FILL);
        for (int r = 46; r > 23; r -= 2) {
            paint.setColor(Ui.MINT);
            paint.setAlpha((listening ? 6 : 2));
            canvas.drawCircle(0, 0, r, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1);
        paint.setColor(Ui.MINT);
        paint.setAlpha(listening ? (int) (130 * (1 - phase)) : 35);
        canvas.drawCircle(0, 0, listening ? 30 + phase * 14 : 30, paint);
        if (processing) {
            paint.setAlpha(230);
            paint.setStrokeWidth(2);
            canvas.drawArc(ring, phase * 360, 100, false, paint);
        } else if (listening) {
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
            for (int i = 0; i < 5; i++) {
                float h = 7 + (1 - Math.abs(2 - i) / 3f) * 19 * (.35f + level * .65f)
                        + (float) Math.sin(phase * Math.PI * 4 + i) * 4;
                canvas.drawRoundRect(i * 8 - 18, -h / 2, i * 8 - 14, h / 2, 2, 2, paint);
            }
        } else {
            mic.setBounds(-17, -17, 17, 17);
            mic.draw(canvas);
        }
        canvas.restore();
    }
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
    }
    @Override
    protected void onDetachedFromWindow() {
        animator.cancel();
        super.onDetachedFromWindow();
    }
}
