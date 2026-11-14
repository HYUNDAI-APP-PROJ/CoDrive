package com.codrive.convoy.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.codrive.BuildConfig;
import com.codrive.R;
import com.codrive.convoy.data.model.*;
import com.codrive.convoy.data.source.DemoScenario;
import com.codrive.voice.data.model.VoiceSession;
import java.util.ArrayList;
import java.util.List;

/** View-only composition; the ViewModel owns all state transitions. */
public final class DrivingStatusScreen {
    private final View root;
    private final Context context;
    private final DrivingStatusViewModel model;
    private final List<VehicleStatusCard> cards=new ArrayList<>();
    private DrivingStatusUiState state;
    private boolean compact, shortWindow;
    private int lastWidth=-1,lastHeight=-1;
    private AlertDialog dialog;
    public DrivingStatusScreen(View root,DrivingStatusViewModel model) {
        this.root=root; this.context=root.getContext(); this.model=model;
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->resize(r-l,b-t));
        root.findViewById(R.id.convoy_rest).setOnClickListener(v->model.request(VehicleRequest.Type.REST));
        root.findViewById(R.id.convoy_energy).setOnClickListener(v->{if(state!=null) model.request(state.energyRequest());});
        root.findViewById(R.id.convoy_voice).setOnClickListener(v->model.voiceAction());
        root.findViewById(R.id.convoy_mic).setOnClickListener(v->model.microphone());
        TextView demo=root.findViewById(R.id.convoy_demo);
        if(BuildConfig.DEBUG) {
            demo.setClickable(true); demo.setFocusable(true);
            demo.setContentDescription(context.getString(R.string.convoy_demo_settings));
            demo.setOnClickListener(v->showScenarios());
        }
        root.findViewById(R.id.convoy_track).setOnClickListener(v->{
            if(state!=null && state.trip.detailsAllowed) showDialog(new AlertDialog.Builder(context)
                    .setTitle(R.string.convoy_distance_heading).setMessage(R.string.convoy_track_help).setPositiveButton(R.string.convoy_done,null));
        });
    }
    private int dp(float n) { return Math.round(n*context.getResources().getDisplayMetrics().density); }
    private void height(int id,int value) { View v=root.findViewById(id); v.getLayoutParams().height=dp(value); v.requestLayout(); }
    private void resize(int width,int height) {
        int availableWidth=width-root.getPaddingLeft()-root.getPaddingRight();
        int availableHeight=height-root.getPaddingTop()-root.getPaddingBottom();
        if(availableWidth==lastWidth && availableHeight==lastHeight) return;
        lastWidth=availableWidth; lastHeight=availableHeight;
        float density=context.getResources().getDisplayMetrics().density;
        compact=availableWidth/density<1000;
        shortWindow=availableHeight/density<680;
        height(R.id.convoy_topbar,compact?32:40); height(R.id.convoy_header,compact?66:76);
        height(R.id.convoy_track,compact?122:144); height(R.id.convoy_alert,compact?36:40);
        ((TextView)root.findViewById(R.id.convoy_destination)).setTextSize(compact?24:28);
        ((TextView)root.findViewById(R.id.convoy_arrival_summary)).setTextSize(compact?20:24);
        rebuildCards(); if(state!=null) bind(state);
    }
    private void rebuildCards() {
        LinearLayout container=root.findViewById(R.id.convoy_cards); container.removeAllViews(); cards.clear();
        container.setOrientation(compact?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        if(state==null) return;
        for(int i=0;i<state.trip.vehicles.size();i++) {
            VehicleStatusCard card=new VehicleStatusCard(context,container,compact);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(compact?-1:0,compact?0:-1,1);
            if(i>0) { if(compact) params.topMargin=dp(8); else params.setMarginStart(dp(16)); }
            container.addView(card.view,params); cards.add(card);
        }
    }
    private void text(int id,String value) { ((TextView)root.findViewById(id)).setText(value); }
    public void bind(DrivingStatusUiState state) {
        this.state=state;
        if(cards.size()!=state.trip.vehicles.size()) rebuildCards();
        text(R.id.convoy_destination,state.trip.destinationName);
        text(R.id.convoy_destination_summary,"함께하는 차량 "+state.trip.vehicles.size()+"대 · "+state.destinationSummary);
        text(R.id.convoy_arrival_summary,state.arrivalSummary); text(R.id.convoy_comparison_scope,state.comparisonScope);
        ((RemainingDistanceTrack)root.findViewById(R.id.convoy_track)).bind(state);
        for(int i=0;i<cards.size();i++) {
            VehicleStatus v=state.trip.vehicles.get(i); cards.get(i).bind(state,v,shortWindow,()->showDetails(v));
        }
        String notice=state.notices.isEmpty()?state.comparison.allArrived?"도착을 확인했어요. 여행은 계속 유지됩니다.":"":
                "⚠  "+state.notices.get(0).text+(state.notices.size()>1?"  · 추가 "+(state.notices.size()-1)+"건":"");
        if(!state.trip.actionsAllowed) notice="현재 화면 조작이 제한되어 있어요";
        text(R.id.convoy_alert,notice);
        root.findViewById(R.id.convoy_demo).setEnabled(state.trip.detailsAllowed);
        VehicleRequest request=state.trip.mine().request;
        requestButton(R.id.convoy_rest,VehicleRequest.Type.REST,request);
        requestButton(R.id.convoy_energy,state.energyRequest(),request);
        boolean joined=state.voice.state==VoiceSession.State.JOINED;
        String voice=joined?"음성 나가기":state.voice.state==VoiceSession.State.CONNECTING?"연결 중 · 취소":state.voice.state==VoiceSession.State.FAILED?"음성 다시 시도":"음성 참여";
        action(R.id.convoy_voice,voice+"  "+state.joinedCount+"/"+state.trip.vehicles.size(),true,state.trip.actionsAllowed);
        root.findViewById(R.id.convoy_mic).setVisibility(joined?View.VISIBLE:View.GONE);
        action(R.id.convoy_mic,state.voice.muted?"마이크 켜기":"마이크 끄기",!state.voice.muted,state.trip.actionsAllowed);
    }
    private void requestButton(int id,VehicleRequest.Type type,VehicleRequest request) {
        boolean pending=request.inFlight()&&(request.desired==type || (request.desired==null && request.shared==type));
        boolean failed=request.status==VehicleRequest.Status.FAILED&&(request.desired==type || (request.desired==null&&request.shared==type));
        String label=pending?request.status==VehicleRequest.Status.CANCELLING?"취소 전달 중":"전달 중":
                failed?"전달 실패 · 다시 시도":DrivingText.request(type)+(request.shared==type?" 요청 취소":" 요청");
        action(id,label,request.shared==type,state.trip.actionsAllowed&&!pending);
    }
    private void action(int id,String label,boolean selected,boolean enabled) {
        Button button=root.findViewById(id); button.setText(label); button.setTextSize(compact?16:18);
        button.setEnabled(enabled); button.setSelected(selected); button.setAlpha(enabled?1f:0.55f);
        int fill=context.getColor(selected?R.color.convoy_ink:R.color.convoy_surface);
        int ink=context.getColor(selected?R.color.convoy_surface:R.color.convoy_ink);
        button.setTextColor(ink); button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33888888),VehicleStatusCard.shape(context,fill,context.getColor(R.color.convoy_line),16),null));
    }
    private void showScenarios() {
        if(!BuildConfig.DEBUG || state==null || !state.trip.detailsAllowed) return;
        DemoScenario[] values=DemoScenario.values(); String[] labels=new String[values.length];
        for(int i=0;i<values.length;i++) labels[i]=values[i].label;
        showDialog(new AlertDialog.Builder(context).setTitle(R.string.convoy_demo_title)
                .setSingleChoiceItems(labels,model.scenario().ordinal(),(d,i)->{model.scenario(values[i]);d.dismiss();})
                .setPositiveButton(R.string.convoy_done,null).setNeutralButton(R.string.convoy_recover,(d,w)->model.recover())
                .setNegativeButton(R.string.convoy_advance,(d,w)->model.advance(11000)));
    }
    private void showDetails(VehicleStatus v) {
        if(!state.trip.detailsAllowed) return;
        String driving=v.drivingSeconds.hasLastValue()?DrivingText.duration(v.drivingSeconds.value):DrivingText.quality(v.drivingSeconds,state.now);
        String detail=state.eta(v)+"\n"+state.arrivalDetail(v)+"\n"+state.relative(v)+"\n이번 주행 "+driving
                +"\n"+state.cardNotice(v)+"\n"+state.freshness(v)+"\n\n"+context.getString(R.string.convoy_demo_description);
        showDialog(new AlertDialog.Builder(context).setTitle(v.displayName+" · "+context.getString(R.string.convoy_detail))
                .setMessage(detail.trim()).setPositiveButton(R.string.convoy_done,null));
    }
    private void showDialog(AlertDialog.Builder builder) { close(); dialog=builder.show(); }
    public void close() { if(dialog!=null) { dialog.dismiss(); dialog=null; } }
}
