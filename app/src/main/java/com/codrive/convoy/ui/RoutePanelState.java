package com.codrive.convoy.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Read-only display snapshot supplied by the app; it does not change a trip or navigation. */
public final class RoutePanelState {
    public enum Status { EMPTY, LOADING, ACTIVE }
    public static final class Step {
        public final String label, name;
        public Step(String label,String name) {
            this.label=clean(label,40); this.name=clean(name,160);
            if(this.label.isEmpty() || this.name.isEmpty()) throw new IllegalArgumentException("Empty route step");
        }
    }
    public final Status status;
    public final String owner, source;
    public final List<Step> steps;

    public RoutePanelState(Status status,String owner,String source,List<Step> steps) {
        Objects.requireNonNull(status); Objects.requireNonNull(steps);
        if(steps.size()>32) throw new IllegalArgumentException("Too many route steps");
        List<Step> copy=new ArrayList<>();
        for(Step step:steps) copy.add(Objects.requireNonNull(step));
        this.status=status==Status.ACTIVE && copy.isEmpty()?Status.EMPTY:status;
        this.owner=this.status==Status.ACTIVE?clean(owner,80):"";
        this.source=this.status==Status.ACTIVE?clean(source,80):"";
        this.steps=this.status==Status.ACTIVE?Collections.unmodifiableList(copy):Collections.emptyList();
    }
    public static RoutePanelState empty() {
        return new RoutePanelState(Status.EMPTY,"","",Collections.emptyList());
    }
    private static String clean(String value,int limit) {
        String text=value==null?"":value.trim();
        if(text.length()>limit) throw new IllegalArgumentException("Route text too long");
        return text;
    }
}
