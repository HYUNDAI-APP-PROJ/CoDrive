package com.codrive.convoy.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import com.codrive.R;
import com.codrive.convoy.data.model.VehicleStatus;
import java.util.ArrayList;
import java.util.List;

/** Cars and labels may change lanes vertically; their distance-based x coordinates never move. */
public final class RemainingDistanceTrack extends View {
    private static final class Marker {
        final String label;
        final boolean own;
        final float position;
        final RectF labelBounds = new RectF();
        final RectF bounds = new RectF();
        float x, y;
        int lane;

        Marker(String label, boolean own, float position) {
            this.label = label;
            this.own = own;
            this.position = position;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Marker> markers = new ArrayList<>(3);
    private final Path carBody = new Path();
    private final Path carWindow = new Path();
    private final Path destinationFlag = new Path();
    private final String heading;
    private String maxDistanceLabel = "", excludedLabel = "";
    private boolean compact;
    private float left, right, baseline;

    public RemainingDistanceTrack(Context context, AttributeSet attrs) {
        super(context, attrs);
        heading = context.getString(R.string.convoy_distance_heading);
        // A side-view car facing the destination, in reusable local coordinates.
        carBody.moveTo(-22, 7); carBody.lineTo(-22, 1); carBody.lineTo(-18, -3);
        carBody.lineTo(-10, -4); carBody.lineTo(-5, -11); carBody.quadTo(-4, -12, -2, -12);
        carBody.lineTo(9, -12); carBody.lineTo(16, -4); carBody.lineTo(21, -2);
        carBody.quadTo(23, -1, 23, 2); carBody.lineTo(23, 7); carBody.close();
        carWindow.moveTo(-7, -4); carWindow.lineTo(-3, -9);
        carWindow.lineTo(8, -9); carWindow.lineTo(13, -4); carWindow.close();
        destinationFlag.moveTo(0, 0); destinationFlag.lineTo(15, 3);
        destinationFlag.lineTo(15, 13); destinationFlag.lineTo(0, 10); destinationFlag.close();
    }

    public void bind(DrivingStatusUiState state) {
        markers.clear();
        StringBuilder description = new StringBuilder(heading);
        for (VehicleStatus v : state.trip.vehicles) {
            Double position = state.comparison.positions.get(v.vehicleId);
            String distance = position == null ? "위치 확인 중" : DrivingText.distance(v.distance.value);
            description.append(". ").append(v.displayName).append(" ").append(distance);
            if (position != null) markers.add(new Marker(v.displayOrder + " " + v.displayName + " · " + distance,
                    v.vehicleId.equals(state.trip.currentVehicleId), position.floatValue()));
        }
        description.append(". 공동 목적지 0km. ").append(getContext().getString(R.string.convoy_track_help));
        setContentDescription(description);
        maxDistanceLabel = DrivingText.distance(state.comparison.maxDistance) + " 남음";
        StringBuilder excluded = new StringBuilder();
        for (VehicleStatus v : state.comparison.excluded) {
            if (excluded.length() > 0) excluded.append(" · ");
            excluded.append(v.displayName);
        }
        excludedLabel = excluded.length() == 0 ? "" : excluded + " 위치 확인 중";
        layoutMarkers();
        invalidate();
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) {
        return android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                value, getResources().getDisplayMetrics());
    }
    private int color(int id) { return getContext().getColor(id); }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutMarkers();
    }

    private void layoutMarkers() {
        compact = getWidth() / getResources().getDisplayMetrics().density < 1000;
        left = dp(28); right = Math.max(left, getWidth() - dp(64));
        baseline = getHeight() - dp(excludedLabel.isEmpty() ? 24 : 42);
        paint.setTextSize(sp(compact ? 14 : 16));
        for (int i = 0; i < markers.size(); i++) {
            Marker marker = markers.get(i);
            marker.x = left + marker.position * (right - left);
            float width = paint.measureText(marker.label) + dp(16);
            float labelLeft = marker.x + dp(26);
            if (labelLeft + width > getWidth()) labelLeft = marker.x - dp(26) - width;
            labelLeft = Math.max(0, labelLeft);
            float start = Math.min(marker.x - dp(20), labelLeft);
            float end = Math.max(marker.x + dp(20), labelLeft + width);
            int lane = 0;
            for (; lane < 2; lane++) {
                boolean collision = false;
                for (int j = 0; j < i; j++) {
                    Marker previous = markers.get(j);
                    if (previous.lane == lane && start < previous.bounds.right + dp(8)
                            && end > previous.bounds.left - dp(8)) collision = true;
                }
                if (!collision) break;
            }
            marker.lane = lane;
            float top = dp(27 + lane * 24);
            marker.y = top + dp(11);
            marker.labelBounds.set(labelLeft, top, labelLeft + width, top + dp(22));
            marker.bounds.set(start, top, end, top + dp(22));
        }
    }

    private void text(Canvas canvas, String text, float x, float y, int color, float size, Paint.Align align) {
        paint.setColor(color); paint.setTextSize(sp(size)); paint.setTextAlign(align); paint.setStyle(Paint.Style.FILL);
        canvas.drawText(text, x, y, paint);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int ink = color(R.color.convoy_ink), muted = color(R.color.convoy_muted), line = color(R.color.convoy_line);
        text(canvas, heading, 0, dp(18), ink, compact ? 16 : 18, Paint.Align.LEFT);
        if (markers.isEmpty()) {
            text(canvas, "거리 정보를 기다리는 중", getWidth() / 2f, getHeight() / 2f + dp(18), muted, 18, Paint.Align.CENTER);
            return;
        }

        text(canvas, "공동 목적지", getWidth(), dp(18), muted, 14, Paint.Align.RIGHT);
        float flagX = getWidth() - paint.measureText("공동 목적지") - dp(25);
        canvas.save(); canvas.translate(flagX, dp(2)); canvas.scale(dp(0.85f), dp(0.85f));
        paint.setColor(ink); canvas.drawPath(destinationFlag, paint);
        paint.setStrokeWidth(2); canvas.drawLine(0, 0, 0, 20, paint); canvas.restore();

        paint.setColor(line);
        canvas.drawRoundRect(left, baseline - dp(2), right, baseline + dp(2), dp(2), dp(2), paint);
        paint.setColor(muted); paint.setStrokeWidth(dp(2));
        canvas.drawLine(right, baseline - dp(4), right, baseline + dp(4), paint);
        text(canvas, maxDistanceLabel, 0, baseline + dp(20), muted, 14, Paint.Align.LEFT);
        text(canvas, "목적지 · 0km", getWidth(), baseline + dp(20), muted, 14, Paint.Align.RIGHT);

        // Draw connectors first so another vehicle's connector never crosses over a car or label.
        paint.setColor(line); paint.setStrokeWidth(dp(1));
        for (int i = 0; i < markers.size(); i++) {
            Marker marker = markers.get(i);
            canvas.drawLine(marker.x, marker.y + dp(10), marker.x, baseline, paint);
            canvas.drawLine(marker.x - dp(3), baseline, marker.x + dp(3), baseline, paint);
        }
        for (int i = 0; i < markers.size(); i++) {
            Marker marker = markers.get(i);
            drawCar(canvas, marker, ink);
            int fill = color(marker.own ? R.color.convoy_own : R.color.convoy_surface);
            paint.setStyle(Paint.Style.FILL); paint.setColor(fill);
            canvas.drawRoundRect(marker.labelBounds, dp(11), dp(11), paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1));
            paint.setColor(marker.own ? fill : line);
            canvas.drawRoundRect(marker.labelBounds, dp(11), dp(11), paint);
            paint.setTextSize(sp(compact ? 14 : 16));
            float textY = marker.labelBounds.centerY() - (paint.ascent() + paint.descent()) / 2;
            text(canvas, marker.label, marker.labelBounds.centerX(), textY,
                    marker.own ? color(R.color.convoy_own_ink) : ink, compact ? 14 : 16, Paint.Align.CENTER);
        }
        if (!excludedLabel.isEmpty()) text(canvas, excludedLabel, 0, getHeight() - dp(3), muted, 14, Paint.Align.LEFT);
    }

    private void drawCar(Canvas canvas, Marker marker, int ink) {
        int fill = color(marker.own ? R.color.convoy_own : R.color.convoy_surface);
        int window = color(marker.own ? R.color.convoy_own_ink : R.color.convoy_muted);
        canvas.save(); canvas.translate(marker.x, marker.y); canvas.scale(dp(0.8f), dp(0.8f));
        paint.setStyle(Paint.Style.FILL); paint.setColor(fill); canvas.drawPath(carBody, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f); paint.setColor(ink);
        canvas.drawPath(carBody, paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(window); canvas.drawPath(carWindow, paint);
        paint.setColor(fill); paint.setStrokeWidth(1.8f); canvas.drawLine(3, -9, 3, -4, paint);
        paint.setColor(ink); canvas.drawCircle(-13, 7, 4, paint); canvas.drawCircle(15, 7, 4, paint);
        paint.setColor(color(R.color.convoy_surface));
        canvas.drawCircle(-13, 7, 1.6f, paint); canvas.drawCircle(15, 7, 1.6f, paint);
        canvas.restore();
    }
}
