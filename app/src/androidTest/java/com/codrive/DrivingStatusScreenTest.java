package com.codrive;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.lifecycle.ViewModelProvider;
import com.codrive.app.DrivingStatusActivity;
import com.codrive.convoy.data.model.VehicleRequest;
import com.codrive.convoy.data.source.DemoScenario;
import com.codrive.convoy.ui.DrivingStatusScreen;
import com.codrive.convoy.ui.DrivingStatusViewModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Run on an emulator/device; local JVM checks cannot validate Android layout or lifecycle. */
@RunWith(AndroidJUnit4.class)
public class DrivingStatusScreenTest {
    @Test public void coreInformationAndActionsFitTheThreeSpecifiedWindows() {
        try(ActivityScenario<DrivingStatusActivity> activity=ActivityScenario.launch(DrivingStatusActivity.class)) {
            activity.onActivity(a->{
                for(int[] size:new int[][]{{1280,720},{960,600},{640,600}}) {
                    for(DemoScenario scenario:new DemoScenario[]{DemoScenario.NORMAL,DemoScenario.STALE,DemoScenario.OFFLINE,
                            DemoScenario.CLOSE_MARKERS,DemoScenario.ZERO_DISTANCE,DemoScenario.PRIVATE_UNSUPPORTED,DemoScenario.WARNINGS}) {
                        View root=LayoutInflater.from(a).inflate(R.layout.activity_driving_status,null,false);
                        DrivingStatusViewModel model=new DrivingStatusViewModel((delay,task)->{},null); model.scenario(scenario);
                        DrivingStatusScreen screen=new DrivingStatusScreen(root,model); screen.bind(model.state().getValue());
                        float density=a.getResources().getDisplayMetrics().density;
                        int w=Math.round(size[0]*density),h=Math.round(size[1]*density);
                        for(int i=0;i<3;i++) { root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY)); root.layout(0,0,w,h); }
                        verifyTextFits(root);
                        assertTrue(root.findViewById(R.id.convoy_actions).getBottom()<=h);
                        assertEquals(3,((ViewGroup)root.findViewById(R.id.convoy_cards)).getChildCount());
                        screen.close();
                    }
                }
            });
        }
    }
    @Test public void recreationKeepsTheTripAndDoesNotResubmitPendingRequest() {
        try(ActivityScenario<DrivingStatusActivity> activity=ActivityScenario.launch(DrivingStatusActivity.class)) {
            long[] requestId={0}; String[] tripId={null};
            activity.onActivity(a->{
                DrivingStatusViewModel vm=new ViewModelProvider(a).get(DrivingStatusViewModel.class);
                a.findViewById(R.id.convoy_rest).performClick();
                requestId[0]=vm.state().getValue().trip.mine().request.id; tripId[0]=vm.state().getValue().trip.tripId;
            });
            activity.recreate();
            activity.onActivity(a->{
                DrivingStatusViewModel vm=new ViewModelProvider(a).get(DrivingStatusViewModel.class);
                assertEquals(tripId[0],vm.state().getValue().trip.tripId);
                assertEquals(requestId[0],vm.state().getValue().trip.mine().request.id);
                assertNotEquals(VehicleRequest.Status.IDLE,vm.state().getValue().trip.mine().request.status);
            });
        }
    }
    private void verifyTextFits(View view) {
        if(view.getVisibility()!=View.VISIBLE) return;
        if(view instanceof TextView) {
            TextView text=(TextView)view;
            if(text.getLayout()!=null && text.length()>0) assertTrue("Clipped text: "+text.getText(),
                    text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom());
        }
        if(view instanceof Button) assertTrue(view.getHeight()>=Math.round(64*view.getResources().getDisplayMetrics().density));
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view; for(int i=0;i<group.getChildCount();i++) verifyTextFits(group.getChildAt(i));
        }
    }
}
