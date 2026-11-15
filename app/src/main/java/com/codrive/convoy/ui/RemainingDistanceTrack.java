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

/** Vehicle rows share one distance axis; their distance-based x coordinates never move. */
public final class RemainingDistanceTrack extends View {
    private static final class Marker {
        final String label;
        final boolean own;
        final float position;
        final RectF labelBounds = new RectF();
        float x, y;

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
    private float left, right, baseline, laneTop;
    private float labelSize, headingSize, axisSize, carScale, detailScale;

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
        float density = getResources().getDisplayMetrics().density;
        compact = getWidth() / density < 1000;
        float height = getHeight() / density;
        boolean spacious = getWidth() / density >= 900 && height >= 300;
        labelSize = spacious ? 28 : height >= 240 ? 26 : height >= 170 ? 24 : height >= 145 ? 18 : 16;
        headingSize = height >= 240 ? 24 : 20;
        axisSize = height >= 240 ? 20 : 16;
        detailScale = axisSize / 16;
        laneTop = dp(headingSize + 12);
        float bottomSpace = excludedLabel.isEmpty() ? axisSize <= 16 ? 32 : axisSize + 20 : axisSize * 2 + 22;
        baseline = Math.max(laneTop, getHeight() - dp(bottomSpace));
        float rowHeight = (baseline - laneTop) / Math.max(1, markers.size());
        carScale = Math.max(0.8f, Math.min(2.2f, Math.min(height / 125f, (rowHeight / density - 4) / 24f)));
        left = dp(23 * carScale + 12);
        right = Math.max(left, getWidth() - dp(compact ? 76 : spacious ? 132 : 108));
        paint.setFakeBoldText(true);
        paint.setTextSize(sp(labelSize));
        float textHeight = paint.descent() - paint.ascent();
        if (textHeight > rowHeight - dp(4)) {
            labelSize = Math.max(16, labelSize * Math.max(dp(16), rowHeight - dp(4)) / textHeight);
            paint.setTextSize(sp(labelSize));
            textHeight = paint.descent() - paint.ascent();
        }
        float labelHeight = Math.max(textHeight, Math.min(textHeight + dp(12), rowHeight - dp(1)));
        float labelOffset = dp(23 * carScale + 16);
        for (int i = 0; i < markers.size(); i++) {
            Marker marker = markers.get(i);
            marker.x = left + marker.position * (right - left);
            marker.y = laneTop + rowHeight * (i + 0.5f);
            float width = paint.measureText(marker.label) + dp(20);
            float labelLeft = marker.x + labelOffset;
            if (labelLeft + width > right - dp(12 * detailScale)) labelLeft = marker.x - labelOffset - width;
            labelLeft = Math.max(0, labelLeft);
            marker.labelBounds.set(labelLeft, marker.y - labelHeight / 2,
                    labelLeft + width, marker.y + labelHeight / 2);
        }
        paint.setFakeBoldText(false);
    }

    private void text(Canvas canvas, String text, float x, float y, int color, float size, Paint.Align align) {
        paint.setColor(color); paint.setTextSize(sp(size)); paint.setTextAlign(align); paint.setStyle(Paint.Style.FILL);
        canvas.drawText(text, x, y, paint);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int ink = color(R.color.convoy_ink), muted = color(R.color.convoy_muted), line = color(R.color.convoy_line);
        paint.setFakeBoldText(true);
        text(canvas, heading, 0, dp(headingSize + 2), ink, headingSize, Paint.Align.LEFT);
        paint.setFakeBoldText(false);
        if (markers.isEmpty()) {
            text(canvas, "거리 정보를 기다리는 중", getWidth() / 2f, getHeight() / 2f + dp(18), muted, labelSize, Paint.Align.CENTER);
            return;
        }

        text(canvas, "공동 목적지", getWidth(), dp(headingSize + 2), ink, axisSize, Paint.Align.RIGHT);
        float flagScale = 1.1f * detailScale;
        float flagX = getWidth() - paint.measureText("공동 목적지") - dp(15 * flagScale + 12);
        canvas.save(); canvas.translate(flagX, dp(headingSize + 4 - 20 * flagScale));
        canvas.scale(dp(flagScale), dp(flagScale));
        paint.setColor(ink); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.7f);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(destinationFlag, paint);
        canvas.drawLine(0, 0, 0, 20, paint); canvas.restore();

        paint.setColor(line); paint.setStyle(Paint.Style.FILL);
        float axisHalfWidth = dp(2.5f * detailScale);
        canvas.drawRoundRect(left, baseline - axisHalfWidth, right, baseline + axisHalfWidth, axisHalfWidth, axisHalfWidth, paint);
        // The common destination is a prominent vertical rail shared by every vehicle row.
        canvas.drawRoundRect(right - dp(8 * detailScale), laneTop, right + dp(8 * detailScale), baseline + dp(4), dp(8), dp(8), paint);
        paint.setColor(ink);
        canvas.drawRoundRect(right - axisHalfWidth, laneTop, right + axisHalfWidth, baseline + dp(4), axisHalfWidth, axisHalfWidth, paint);
        text(canvas, maxDistanceLabel, 0, baseline + dp(axisSize + 8), muted, axisSize, Paint.Align.LEFT);
        text(canvas, "목적지 · 0km", getWidth(), baseline + dp(axisSize + 8), ink, axisSize, Paint.Align.RIGHT);

        // Draw connectors first so another vehicle's connector never crosses over a car or label.
        for (int i = 0; i < markers.size(); i++) {
            Marker marker = markers.get(i);
            paint.setColor(marker.own ? muted : line); paint.setStrokeWidth(dp(1.5f * detailScale));
            canvas.drawLine(marker.x, marker.y + dp(11 * carScale), marker.x, baseline, paint);
            paint.setColor(ink); paint.setStrokeWidth(dp(2 * detailScale));
            canvas.drawLine(marker.x, baseline - dp(4), marker.x, baseline + dp(4), paint);
        }
        for (int i = 0; i < markers.size(); i++) {
            Marker marker = markers.get(i);
            drawCar(canvas, marker, ink);
            int fill = color(marker.own ? R.color.convoy_own : R.color.convoy_surface);
            paint.setStyle(Paint.Style.FILL); paint.setColor(fill);
            float radius = dp(6);
            canvas.drawRoundRect(marker.labelBounds, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(detailScale));
            paint.setColor(marker.own ? fill : line);
            canvas.drawRoundRect(marker.labelBounds, radius, radius, paint);
            paint.setTextSize(sp(labelSize));
            paint.setFakeBoldText(true);
            float textY = marker.labelBounds.centerY() - (paint.ascent() + paint.descent()) / 2;
            text(canvas, marker.label, marker.labelBounds.centerX(), textY,
                    marker.own ? color(R.color.convoy_own_ink) : ink, labelSize, Paint.Align.CENTER);
            paint.setFakeBoldText(false);
        }
        if (!excludedLabel.isEmpty()) text(canvas, excludedLabel, 0, getHeight() - dp(6), muted, axisSize, Paint.Align.LEFT);
    }

    private void drawCar(Canvas canvas, Marker marker, int ink) {
        canvas.save(); canvas.translate(marker.x, marker.y); canvas.scale(dp(carScale), dp(carScale));
        paint.setStyle(Paint.Style.FILL); paint.setColor(color(R.color.convoy_surface)); canvas.drawPath(carBody, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.7f); paint.setColor(ink);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(carBody, paint);
        canvas.drawPath(carWindow, paint);
        canvas.drawLine(3, -9, 3, -4, paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(color(R.color.convoy_surface));
        canvas.drawCircle(-13, 7, 4, paint); canvas.drawCircle(15, 7, 4, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setColor(ink);
        canvas.drawCircle(-13, 7, 4, paint); canvas.drawCircle(15, 7, 4, paint);
        canvas.restore();
    }
}
