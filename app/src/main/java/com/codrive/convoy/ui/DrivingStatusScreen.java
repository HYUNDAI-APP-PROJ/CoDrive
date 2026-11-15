package com.codrive.convoy.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import com.google.android.material.button.MaterialButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import com.codrive.BuildConfig;
import com.codrive.R;
import com.codrive.convoy.data.model.*;
import com.codrive.convoy.data.source.DemoScenario;
import java.util.ArrayList;
import java.util.List;

/** View-only composition; the ViewModel owns all state transitions. */
public final class DrivingStatusScreen {
    private final View root;
    private final Context context;
    private final DrivingStatusViewModel model;
    private final List<VehicleStatusCard> cards=new ArrayList<>();
    private DrivingStatusUiState state;
    private boolean compact, shortWindow, briefHeader;
    private int lastWidth=-1,lastHeight=-1,lastVehicleCount=-1;
    private boolean lastHasNotice;
    private AlertDialog dialog;
    public DrivingStatusScreen(View root,DrivingStatusViewModel model) {
        this.root=root; this.context=root.getContext(); this.model=model;
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(resize(r-l,b-t)) render();
        });
        root.findViewById(R.id.convoy_rest).setOnClickListener(v->model.request(VehicleRequest.Type.REST));
        root.findViewById(R.id.convoy_energy).setOnClickListener(v->{if(state!=null) model.request(state.energyRequest());});
        TextView demo=root.findViewById(R.id.convoy_demo);
        demo.setBackground(new RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.convoy_hover)),
                VehicleStatusCard.shape(context,context.getColor(R.color.convoy_surface),context.getColor(R.color.convoy_line),6),null));
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
    private boolean resize(int width,int height) {
        int windowWidth=width-root.getPaddingLeft()-root.getPaddingRight();
        int windowHeight=height-root.getPaddingTop()-root.getPaddingBottom();
        if(windowWidth<=0 || windowHeight<=0) return false;
        int vehicleCount=state==null?3:state.trip.vehicles.size();
        boolean hasNotice=state!=null && !noticeText().isEmpty();
        if(windowWidth==lastWidth && windowHeight==lastHeight
                && vehicleCount==lastVehicleCount && hasNotice==lastHasNotice) return false;
        lastWidth=windowWidth; lastHeight=windowHeight;
        lastVehicleCount=vehicleCount; lastHasNotice=hasNotice;
        float density=context.getResources().getDisplayMetrics().density;
        float windowDp=windowWidth/density;
        int railWidth=windowDp<=600?0:windowDp<=1100?80:220;
        int horizontal=windowDp<=600?16:windowDp<=1100?24:40;
        boolean lowWindow=windowHeight/density<700;
        int top=lowWindow?12:windowDp<=600?20:windowDp<=1100?24:28;
        int bottom=lowWindow?12:windowDp<=600?20:24;
        int availableWidth=Math.min(dp(1040),windowWidth-dp(railWidth+horizontal*2));
        int availableHeight=windowHeight-dp(top+bottom);
        View sidebar=root.findViewById(R.id.convoy_sidebar);
        sidebar.setVisibility(railWidth==0?View.GONE:View.VISIBLE);
        sidebar.getLayoutParams().width=dp(railWidth); sidebar.requestLayout();
        int railInset=dp(railWidth==220?16:8);
        root.findViewById(R.id.convoy_sidebar_content).setPadding(railInset,dp(28),railInset,dp(24));
        root.findViewById(R.id.convoy_main_host).setPadding(dp(horizontal),dp(top),dp(horizontal),dp(bottom));
        View content=root.findViewById(R.id.convoy_content);
        content.getLayoutParams().width=availableWidth; content.requestLayout();
        compact=availableWidth/density<1000;
        briefHeader=availableHeight/density<700;
        int topbar=64, alert=compact?36:40;
        int usableHeight=Math.round(availableHeight/density);
        int panelGap=briefHeader?12:24, actionsGap=briefHeader?12:24;
        int sectionGap=briefHeader?12:68;
        int cardGaps=Math.max(0,vehicleCount-1)*8;
        int cardHeight=compact?vehicleCount*(briefHeader?64:88)+cardGaps:briefHeader?220:260;
        int panelHeight=Math.max(0,usableHeight-topbar-cardHeight-64-(hasNotice?alert:0)-panelGap-actionsGap-sectionGap);
        shortWindow=compact||cardHeight<330;
        height(R.id.convoy_topbar,topbar);
        height(R.id.convoy_header,briefHeader?44:compact?76:96);
        height(R.id.convoy_destination_panel,panelHeight);
        height(R.id.convoy_cards,cardHeight);
        height(R.id.convoy_alert,alert);
        root.findViewById(R.id.convoy_cards_header).setVisibility(briefHeader?View.GONE:View.VISIBLE);
        View cards=root.findViewById(R.id.convoy_cards);
        ((LinearLayout.LayoutParams)cards.getLayoutParams()).topMargin=dp(briefHeader?12:0);
        cards.requestLayout();
        View actions=root.findViewById(R.id.convoy_actions);
        ((LinearLayout.LayoutParams)actions.getLayoutParams()).topMargin=dp(actionsGap); actions.requestLayout();
        View panel=root.findViewById(R.id.convoy_destination_panel);
        ((LinearLayout.LayoutParams)panel.getLayoutParams()).topMargin=dp(panelGap);
        int inset=dp(compact?16:24); panel.setPadding(inset,inset,inset,inset);
        ((TextView)root.findViewById(R.id.convoy_page_title)).setTextSize(28);
        ((TextView)root.findViewById(R.id.convoy_destination)).setTextSize(compact?28:36);
        ((TextView)root.findViewById(R.id.convoy_arrival_summary)).setTextSize(compact?24:32);
        ((TextView)root.findViewById(R.id.convoy_destination_summary)).setTextSize(compact?14:18);
        ((TextView)root.findViewById(R.id.convoy_comparison_scope)).setTextSize(compact?14:16);
        root.findViewById(R.id.convoy_destination_summary).setVisibility(briefHeader?View.GONE:View.VISIBLE);
        root.findViewById(R.id.convoy_comparison_scope).setVisibility(briefHeader?View.GONE:View.VISIBLE);
        rebuildCards();
        return true;
    }
    private void rebuildCards() {
        LinearLayout container=root.findViewById(R.id.convoy_cards); container.removeAllViews(); cards.clear();
        container.setOrientation(compact?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        if(state==null) return;
        for(int i=0;i<state.trip.vehicles.size();i++) {
            VehicleStatusCard card=new VehicleStatusCard(context,container,compact);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(compact?-1:0,compact?0:-1,1);
            if(i>0) { if(compact) params.topMargin=dp(8); else params.setMarginStart(dp(12)); }
            container.addView(card.view,params); cards.add(card);
        }
    }
    private void text(int id,String value) { ((TextView)root.findViewById(id)).setText(value); }
    public void bind(DrivingStatusUiState state) {
        this.state=state;
        resize(root.getWidth(),root.getHeight());
        render();
    }
    private String noticeText() {
        if(!state.trip.actionsAllowed) return "현재 화면 조작이 제한되어 있어요";
        return state.notices.isEmpty()?state.comparison.allArrived?"도착을 확인했어요. 여행은 계속 유지됩니다.":"":
                state.notices.get(0).text+(state.notices.size()>1?"  · 추가 "+(state.notices.size()-1)+"건":"");
    }
    private void render() {
        if(state==null) return;
        if(cards.size()!=state.trip.vehicles.size()) rebuildCards();
        text(R.id.convoy_destination,state.trip.destinationName);
        text(R.id.convoy_destination_summary,"함께하는 차량 "+state.trip.vehicles.size()+"대 · "+state.destinationSummary);
        StringBuilder members=new StringBuilder();
        for(VehicleStatus vehicle:state.trip.vehicles) { if(members.length()>0) members.append(" · "); members.append(vehicle.displayName); }
        text(R.id.convoy_members_summary,briefHeader?"차량 "+state.trip.vehicles.size()+"대 · "+state.destinationSummary:members.toString());
        text(R.id.convoy_card_count,context.getString(R.string.convoy_vehicle_count,state.trip.vehicles.size()));
        text(R.id.convoy_arrival_summary,state.arrivalSummary); text(R.id.convoy_comparison_scope,state.comparisonScope);
        ((RemainingDistanceTrack)root.findViewById(R.id.convoy_track)).bind(state);
        for(int i=0;i<cards.size();i++) {
            VehicleStatus v=state.trip.vehicles.get(i); cards.get(i).bind(state,v,shortWindow,()->showDetails(v));
        }
        String notice=noticeText();
        text(R.id.convoy_alert,notice);
        root.findViewById(R.id.convoy_alert).setVisibility(notice.isEmpty()?View.GONE:View.VISIBLE);
        root.findViewById(R.id.convoy_demo).setEnabled(BuildConfig.DEBUG && state.trip.detailsAllowed);
        VehicleRequest request=state.trip.mine().request;
        requestButton(R.id.convoy_rest,VehicleRequest.Type.REST,request);
        requestButton(R.id.convoy_energy,state.energyRequest(),request);
    }
    private void requestButton(int id,VehicleRequest.Type type,VehicleRequest request) {
        boolean pending=request.inFlight()&&(request.desired==type || (request.desired==null && request.shared==type));
        boolean failed=request.status==VehicleRequest.Status.FAILED&&(request.desired==type || (request.desired==null&&request.shared==type));
        String label=pending?request.status==VehicleRequest.Status.CANCELLING?"취소 전달 중":"전달 중":
                failed?"전달 실패 · 다시 시도":DrivingText.request(type)+(request.shared==type?" 요청 취소":" 요청");
        action(id,label,request.shared==type,state.trip.actionsAllowed&&!pending);
    }
    private void action(int id,String label,boolean selected,boolean enabled) {
        MaterialButton button=root.findViewById(id);
        button.setText(label); button.setContentDescription(label); button.setTextSize(compact?16:18);
        button.setEnabled(enabled); button.setSelected(selected); button.setAlpha(1f);
        int fill=context.getColor(!enabled?R.color.convoy_disabled:selected?R.color.convoy_ink:R.color.convoy_surface);
        int ink=context.getColor(!enabled?R.color.convoy_muted:selected?R.color.convoy_surface:R.color.convoy_ink);
        button.setTextColor(ink); button.setBackgroundTintList(ColorStateList.valueOf(fill));
        int iconId=id==R.id.convoy_rest?R.drawable.convoy_ic_rest:
                state.energyRequest()==VehicleRequest.Type.REFUEL?R.drawable.convoy_ic_fuel:R.drawable.convoy_ic_charge;
        Drawable icon=AppCompatResources.getDrawable(context,iconId).mutate();
        button.setIcon(icon); button.setIconTint(ColorStateList.valueOf(ink));
        button.setIconSize(dp(24)); button.setIconPadding(dp(8));
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        int border=context.getColor(!enabled?R.color.convoy_disabled:selected?R.color.convoy_ink:R.color.convoy_line);
        int pressed=context.getColor(selected?R.color.convoy_accent_hover:R.color.convoy_hover);
        button.setStrokeWidth(dp(1)); button.setStrokeColor(ColorStateList.valueOf(border));
        button.setCornerRadius(dp(6)); button.setRippleColor(ColorStateList.valueOf(pressed));
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
