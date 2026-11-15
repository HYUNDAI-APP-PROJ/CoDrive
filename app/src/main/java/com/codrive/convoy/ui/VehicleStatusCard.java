package com.codrive.convoy.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.widget.TextViewCompat;
import com.codrive.R;
import com.codrive.convoy.data.model.*;

public final class VehicleStatusCard {
    public final View view;
    private final boolean compact;
    private final Context context;
    public VehicleStatusCard(Context context,ViewGroup parent,boolean compact) {
        this.context=context; this.compact=compact;
        view=LayoutInflater.from(context).inflate(compact ? R.layout.convoy_vehicle_row : R.layout.convoy_vehicle_card,parent,false);
        view.setFocusable(true); view.setScreenReaderFocusable(true);
    }
    private TextView text(int id) { return view.findViewById(id); }
    private void show(int id,String value,int color) {
        TextView v=text(id); v.setText(value); v.setTextColor(color); v.setVisibility(value.isEmpty()?View.GONE:View.VISIBLE);
    }
    private int color(int id) { return context.getColor(id); }
    private int dp(float value) { return Math.round(value*context.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(Context c,int fill,int stroke,int radius) {
        GradientDrawable drawable=new GradientDrawable(); drawable.setColor(fill);
        drawable.setCornerRadius(radius*c.getResources().getDisplayMetrics().density);
        drawable.setStroke(Math.max(1,Math.round(c.getResources().getDisplayMetrics().density)),stroke); return drawable;
    }
    public void bind(DrivingStatusUiState state,VehicleStatus vehicle,boolean shortWindow,Runnable onDetails) {
        boolean own=vehicle.vehicleId.equals(state.trip.currentVehicleId);
        int ink=color(R.color.convoy_ink);
        int muted=color(R.color.convoy_muted);
        int line=color(R.color.convoy_line);
        GradientDrawable background=shape(context,color(R.color.convoy_surface),own?ink:line,8);
        if(own) background.setStroke(dp(2),ink);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(color(R.color.convoy_hover)),background,null));
        if(!compact) {
            ImageView icon=view.findViewById(R.id.convoy_card_icon);
            icon.setImageTintList(ColorStateList.valueOf(own?color(R.color.convoy_surface):ink));
            icon.setBackground(shape(context,own?ink:color(R.color.convoy_background),own?ink:line,6));
        }
        show(R.id.convoy_card_name,vehicle.displayOrder+"  "+vehicle.displayName,ink);
        String freshness=state.freshness(vehicle);
        String motion=state.motion(vehicle);
        if(compact && !freshness.isEmpty()) {
            if(vehicle.connection==VehicleStatus.Connection.OFFLINE) motion="연결 끊김 · "+DrivingText.age(state.now,vehicle.lastMessageAt);
            else if(vehicle.connection==VehicleStatus.Connection.RECONNECTING) motion="재접속 중";
            else motion="마지막 정보 · "+DrivingText.age(state.now,
                    vehicle.distance.quality==Measurement.Quality.STALE?vehicle.distance.observedAt:vehicle.remainingSeconds.observedAt);
        }
        show(R.id.convoy_card_motion,motion,muted);
        if(compact) {
            text(R.id.convoy_card_motion).setTextSize(shortWindow?14:16);
            text(R.id.convoy_card_motion).setMaxLines(1);
            text(R.id.convoy_card_motion).setEllipsize(TextUtils.TruncateAt.END);
        }
        String eta=state.eta(vehicle);
        show(R.id.convoy_card_eta,eta,ink);
        TextView etaView=text(R.id.convoy_card_eta);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(etaView,compact?20:24,compact?26:36,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        etaView.setMaxLines(1);
        show(R.id.convoy_card_arrival,state.arrivalDetail(vehicle),muted);
        show(R.id.convoy_card_relative,compact||shortWindow ? "" : state.relative(vehicle),muted);
        Measurement<Double> energy=DrivingStatusUiState.primaryEnergy(vehicle);
        String energyName=vehicle.powertrain==VehicleStatus.Powertrain.UNKNOWN ? "에너지" : DrivingStatusUiState.energyName(vehicle);
        String energyText=energyName+"  "+(energy.hasLastValue()?Math.round(energy.value)+"%":DrivingText.quality(energy,state.now));
        if(energy.quality==Measurement.Quality.STALE) energyText+=" · 마지막 정보";
        show(R.id.convoy_card_energy,energyText,ink);
        text(R.id.convoy_card_energy).setTextSize(energy.hasLastValue()?20:compact?16:18);
        ProgressBar meter=view.findViewById(R.id.convoy_card_meter);
        meter.setVisibility(energy.hasLastValue()?View.VISIBLE:View.GONE);
        if(energy.hasLastValue()) meter.setProgress((int)Math.round(energy.value));
        meter.setProgressTintList(ColorStateList.valueOf(ink)); meter.setProgressBackgroundTintList(ColorStateList.valueOf(line));
        meter.setContentDescription(energyText);
        String secondary="";
        if(vehicle.powertrain==VehicleStatus.Powertrain.PHEV) secondary="연료  "+(vehicle.fuel.hasLastValue()?Math.round(vehicle.fuel.value)+"%":DrivingText.quality(vehicle.fuel,state.now));
        else if(!compact && vehicle.range.available()) secondary="주행가능 "+DrivingText.distance(vehicle.range.value)+" · 시연";
        show(R.id.convoy_card_second_energy,secondary,muted);
        String driving=vehicle.drivingSeconds.hasLastValue()?DrivingText.duration(vehicle.drivingSeconds.value):DrivingText.quality(vehicle.drivingSeconds,state.now);
        show(R.id.convoy_card_trip,compact||shortWindow?"":"이번 주행  "+driving,muted);
        String notice=state.cardNotice(vehicle);
        if(compact) {
            notice=notice.replace(" · 마지막 수신 경고","").replace(" · 시연","");
            if(notice.contains("잔량")) notice="잔량 낮음";
        }
        show(R.id.convoy_card_notice,notice,ink);
        TextView noticeView=text(R.id.convoy_card_notice);
        noticeView.setMaxLines(1); noticeView.setEllipsize(TextUtils.TruncateAt.END);
        noticeView.setMinHeight(dp(20));
        noticeView.setVisibility(notice.isEmpty()?View.INVISIBLE:View.VISIBLE);
        show(R.id.convoy_card_connection,compact?"":freshness,muted);
        if(!compact) view.setPadding(dp(16),dp(shortWindow?12:16),dp(16),dp(shortWindow?12:16));
        view.setContentDescription(vehicle.displayName+". "+state.motion(vehicle)+". "+eta+". "+state.arrivalDetail(vehicle)
                +". "+state.relative(vehicle)+". "+energyText+". "+secondary+". 이번 주행 "+driving+". "+notice+". "+freshness);
        view.setClickable(state.trip.detailsAllowed);
        view.setOnClickListener(state.trip.detailsAllowed?v->onDetails.run():null);
    }
}
