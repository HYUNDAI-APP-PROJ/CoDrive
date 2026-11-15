package com.codrive.convoy.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.res.ColorStateList;
import com.codrive.R;

/** Native counterpart of the bundled pages' read-only current-route sidebar. */
public final class CurrentRoutePanel extends LinearLayout {
    private final TextView owner, empty;
    private final LinearLayout points;
    private final ScrollView scroll;

    public CurrentRoutePanel(Context context) { this(context,null); }
    public CurrentRoutePanel(Context context,AttributeSet attrs) {
        super(context,attrs); setOrientation(VERTICAL); setPadding(dp(24),dp(32),dp(24),dp(24));
        setBackgroundResource(R.drawable.convoy_sidebar_background);
        LinearLayout heading=new LinearLayout(context); heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView route=new ImageView(context); route.setImageResource(R.drawable.ic_route);
        route.setImageTintList(ColorStateList.valueOf(color(R.color.convoy_background)));
        route.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        heading.addView(route,new LayoutParams(dp(24),dp(24)));
        TextView title=text(context.getString(R.string.convoy_current_route),20,false);
        title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); title.setAccessibilityHeading(true);
        LayoutParams titleSize=new LayoutParams(0,-2,1); titleSize.setMarginStart(dp(12)); heading.addView(title,titleSize);
        addView(heading,new LayoutParams(-1,-2));
        owner=text("",18,true); LayoutParams ownerSize=new LayoutParams(-1,-2);
        ownerSize.topMargin=dp(8); ownerSize.setMarginStart(dp(36)); addView(owner,ownerSize);
        FrameLayout body=new FrameLayout(context); addView(body,new LayoutParams(-1,0,1));
        empty=text("",18,true); empty.setGravity(Gravity.CENTER); empty.setLineSpacing(dp(5),1);
        body.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        scroll=new ScrollView(context); points=new LinearLayout(context); points.setOrientation(VERTICAL);
        points.setPadding(0,dp(32),0,0); scroll.addView(points,new ScrollView.LayoutParams(-1,-2));
        body.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        bind(RoutePanelState.empty());
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private int color(int id) { return getContext().getColor(id); }
    private TextView text(String value,int size,boolean muted) {
        TextView view=new TextView(getContext()); view.setText(value); view.setTextSize(size);
        view.setTextColor(color(muted?R.color.convoy_nav_muted:R.color.convoy_background));
        view.setIncludeFontPadding(false); return view;
    }
    public void bind(RoutePanelState state) {
        boolean active=state.status==RoutePanelState.Status.ACTIVE;
        owner.setText(state.owner); owner.setVisibility(active && !state.owner.isEmpty()?VISIBLE:GONE);
        empty.setText(state.status==RoutePanelState.Status.LOADING?R.string.convoy_route_loading:R.string.convoy_route_empty);
        empty.setVisibility(active?GONE:VISIBLE); scroll.setVisibility(active?VISIBLE:GONE);
        points.removeAllViews();
        if(!active) return;
        if(!state.source.isEmpty()) {
            TextView source=text(state.source,16,true);
            LayoutParams sourceSize=new LayoutParams(-1,-2); sourceSize.bottomMargin=dp(28); points.addView(source,sourceSize);
        }
        for(int i=0;i<state.steps.size();i++) {
            RoutePanelState.Step step=state.steps.get(i);
            LinearLayout row=new LinearLayout(getContext()); row.setBaselineAligned(false);
            FrameLayout rail=new FrameLayout(getContext()); row.addView(rail,new LayoutParams(dp(32),-1));
            if(i<state.steps.size()-1) {
                View line=new View(getContext()); line.setBackgroundColor(color(R.color.convoy_nav_line));
                FrameLayout.LayoutParams lineSize=new FrameLayout.LayoutParams(dp(1),-1); lineSize.leftMargin=dp(8); lineSize.topMargin=dp(12);
                rail.addView(line,lineSize);
            }
            ImageView marker=new ImageView(getContext());
            marker.setImageResource(i==state.steps.size()-1?R.drawable.convoy_ic_flag:R.drawable.convoy_route_dot);
            marker.setImageTintList(ColorStateList.valueOf(color(i==0?R.color.convoy_background:R.color.convoy_nav_muted)));
            marker.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams markerSize=new FrameLayout.LayoutParams(dp(i==state.steps.size()-1?24:12),dp(i==state.steps.size()-1?24:12));
            markerSize.leftMargin=dp(i==state.steps.size()-1?0:2); markerSize.topMargin=dp(2); rail.addView(marker,markerSize);
            LinearLayout labels=new LinearLayout(getContext()); labels.setOrientation(VERTICAL);
            labels.addView(text(step.label,16,true),new LayoutParams(-1,-2));
            TextView name=text(step.name,20,false); name.setLineSpacing(dp(4),1);
            LayoutParams nameSize=new LayoutParams(-1,-2); nameSize.topMargin=dp(8); labels.addView(name,nameSize);
            if(i<state.steps.size()-1) labels.setPadding(0,0,0,dp(28));
            row.addView(labels,new LayoutParams(0,-2,1)); points.addView(row,new LayoutParams(-1,-2));
        }
    }
}
