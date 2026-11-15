package com.codrive.convoy.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.content.res.AppCompatResources;
import com.codrive.R;
import com.codrive.convoy.data.model.VehicleStatus;

/** Readable distance order, not a map or a proportional position plot. */
public final class RemainingDistanceTrack extends ScrollView {
    private final LinearLayout content;
    private String rendered;

    public RemainingDistanceTrack(Context context,AttributeSet attrs) {
        super(context,attrs);
        setFillViewport(false); setHorizontalScrollBarEnabled(false);
        content=new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        addView(content,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
    }
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private int color(int id) { return getContext().getColor(id); }
    private TextView text(String value,int size,int color,boolean strong) {
        TextView view=new TextView(getContext()); view.setText(value); view.setTextSize(size);
        view.setTextColor(color); view.setIncludeFontPadding(false);
        if(strong) view.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        view.setMaxLines(1); view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    public void bind(DrivingStatusUiState state) {
        StringBuilder key=new StringBuilder(state.trip.currentVehicleId);
        for(VehicleStatus vehicle:state.comparison.vehiclesByRemainingDistance)
            key.append('|').append(vehicle.vehicleId).append(vehicle.displayOrder).append(vehicle.displayName)
                    .append(vehicle.distance.value).append(state.eta(vehicle));
        for(VehicleStatus vehicle:state.comparison.excluded) key.append('|').append(vehicle.vehicleId).append(vehicle.displayName);
        if(key.toString().equals(rendered)) return;
        rendered=key.toString(); content.removeAllViews();
        int ink=color(R.color.convoy_ink), muted=color(R.color.convoy_muted);
        TextView heading=text(getContext().getString(R.string.convoy_distance_order),16,muted,false);
        heading.setAccessibilityHeading(true);
        content.addView(heading,new LinearLayout.LayoutParams(-1,dp(32)));
        if(state.comparison.vehiclesByRemainingDistance.isEmpty()) {
            TextView empty=text(getContext().getString(R.string.convoy_distance_waiting),18,muted,false);
            empty.setGravity(Gravity.CENTER); empty.setMinHeight(dp(120));
            content.addView(empty,new LinearLayout.LayoutParams(-1,-2));
        } else {
            LinearLayout destination=new LinearLayout(getContext()); destination.setGravity(Gravity.CENTER_VERTICAL);
            destination.setBaselineAligned(false);
            destination.addView(new RailIcon(true,false,false),new LinearLayout.LayoutParams(dp(64),-1));
            TextView goal=text(getContext().getString(R.string.convoy_destination_zero),18,ink,true);
            destination.addView(goal,new LinearLayout.LayoutParams(0,-2,1));
            content.addView(destination,new LinearLayout.LayoutParams(-1,dp(48)));
            for(int i=0;i<state.comparison.vehiclesByRemainingDistance.size();i++) {
                VehicleStatus vehicle=state.comparison.vehiclesByRemainingDistance.get(i);
                boolean own=vehicle.vehicleId.equals(state.trip.currentVehicleId);
                LinearLayout row=new LinearLayout(getContext()); row.setBaselineAligned(false);
                row.addView(new RailIcon(false,i==state.comparison.vehiclesByRemainingDistance.size()-1,own),
                        new LinearLayout.LayoutParams(dp(64),-1));
                LinearLayout box=new LinearLayout(getContext()); box.setGravity(Gravity.CENTER_VERTICAL);
                box.setBaselineAligned(false); box.setPadding(dp(16),dp(16),dp(16),dp(16)); box.setMinimumHeight(dp(88));
                box.setBackground(VehicleStatusCard.shape(getContext(),color(R.color.convoy_background),
                        color(own?R.color.convoy_ink:R.color.convoy_line),8));
                LinearLayout identity=new LinearLayout(getContext()); identity.setOrientation(LinearLayout.VERTICAL);
                identity.addView(text(vehicle.displayOrder+"  "+vehicle.displayName,20,ink,true),new LinearLayout.LayoutParams(-1,-2));
                TextView eta=text(state.eta(vehicle),14,muted,false);
                LinearLayout.LayoutParams etaSize=new LinearLayout.LayoutParams(-1,-2); etaSize.topMargin=dp(6);
                identity.addView(eta,etaSize);
                LinearLayout.LayoutParams identitySize=new LinearLayout.LayoutParams(0,-2,1); identitySize.setMarginEnd(dp(16));
                box.addView(identity,identitySize);
                LinearLayout distance=new LinearLayout(getContext()); distance.setOrientation(LinearLayout.VERTICAL);
                distance.setGravity(Gravity.END);
                distance.addView(text(DrivingText.distance(vehicle.distance.value),28,ink,true));
                TextView caption=text(getContext().getString(R.string.convoy_remaining_distance),14,muted,false);
                LinearLayout.LayoutParams captionSize=new LinearLayout.LayoutParams(-2,-2); captionSize.topMargin=dp(4);
                distance.addView(caption,captionSize); box.addView(distance,new LinearLayout.LayoutParams(-2,-2));
                box.setScreenReaderFocusable(true);
                box.setContentDescription(vehicle.displayName+". "+DrivingText.distance(vehicle.distance.value)+" 남음. "+state.eta(vehicle));
                LinearLayout.LayoutParams boxSize=new LinearLayout.LayoutParams(0,-2,1);
                boxSize.topMargin=dp(6); boxSize.bottomMargin=dp(6);
                row.addView(box,boxSize); content.addView(row,new LinearLayout.LayoutParams(-1,-2));
            }
        }
        if(!state.comparison.excluded.isEmpty()) {
            StringBuilder names=new StringBuilder();
            for(VehicleStatus vehicle:state.comparison.excluded) {
                if(names.length()>0) names.append(" · "); names.append(vehicle.displayName);
            }
            TextView excluded=text(names+" · 거리 확인 중",14,muted,false);
            excluded.setMaxLines(2);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2); size.topMargin=dp(12);
            content.addView(excluded,size);
        }
    }

    private final class RailIcon extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable icon;
        private final boolean first,last,own;
        RailIcon(boolean first,boolean last,boolean own) {
            super(RemainingDistanceTrack.this.getContext()); this.first=first; this.last=last; this.own=own;
            icon=AppCompatResources.getDrawable(getContext(),first?R.drawable.convoy_ic_flag:R.drawable.convoy_ic_drive).mutate();
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float x=getWidth()/2f, y=getHeight()/2f;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2));
            paint.setColor(color(R.color.convoy_line));
            canvas.drawLine(x,first?y:0,x,last?y:getHeight(),paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(color(own?R.color.convoy_ink:R.color.convoy_surface));
            canvas.drawCircle(x,y,dp(22),paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1));
            paint.setColor(color(own?R.color.convoy_ink:R.color.convoy_line)); canvas.drawCircle(x,y,dp(22),paint);
            icon.setTint(color(own?R.color.convoy_surface:R.color.convoy_ink));
            icon.setBounds(Math.round(x)-dp(13),Math.round(y)-dp(13),Math.round(x)+dp(13),Math.round(y)+dp(13));
            icon.draw(canvas);
        }
    }
}
