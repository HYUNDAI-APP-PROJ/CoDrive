package com.codrive.group;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import com.codrive.R;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Layout bounds at the full and half Connect-S-17 app window sizes. */
public class GroupWindowLayoutTest {
    @Test public void lobbyControlsFitHalfWindowForCreateAndJoin() {
        onMain(() -> {
            ViewGroup root = inflate(R.layout.activity_group_lobby, 840, 1184);
            assertNull(root.findViewById(R.id.group_hero_panel));
            ((EditText) root.findViewById(R.id.group_name)).setText("강릉 주말 여행");
            ((EditText) root.findViewById(R.id.passengers)).setText("2");
            layout(root, 840, 1184);
            assertOnScreen(root, R.id.group_name);
            assertOnScreen(root, R.id.vehicle_alias);
            assertOnScreen(root, R.id.passengers_plus);
            assertOnScreen(root, R.id.rule_leader);
            assertOnScreen(root, R.id.submit);
            assertEquals(EditorInfo.IME_ACTION_DONE,
                    ((EditText) root.findViewById(R.id.group_name)).getImeOptions()
                            & EditorInfo.IME_MASK_ACTION);

            root.findViewById(R.id.name_field).setVisibility(View.GONE);
            root.findViewById(R.id.code_field).setVisibility(View.VISIBLE);
            root.findViewById(R.id.rule_field).setVisibility(View.GONE);
            root.findViewById(R.id.join_help).setVisibility(View.VISIBLE);
            root.findViewById(R.id.form_note).setVisibility(View.GONE);
            layout(root, 840, 1184);
            assertOnScreen(root, R.id.invite_code);
            assertOnScreen(root, R.id.vehicle_alias);
            assertOnScreen(root, R.id.submit);
        });
    }

    @Test public void waitingSummaryAndActionsFitHalfWindow() {
        onMain(() -> {
            ViewGroup root = inflate(R.layout.activity_group_waiting, 840, 1184);
            assertNull(root.findViewById(R.id.group_hero_panel));
            assertNotNull(root.findViewById(R.id.group_split_summary));
            ((TextView) root.findViewById(R.id.waiting_group_name)).setText("강릉 주말 여행");
            ((TextView) root.findViewById(R.id.waiting_code)).setText("ABC123");
            ((TextView) root.findViewById(R.id.waiting_cars)).setText("3대");
            ((TextView) root.findViewById(R.id.waiting_people)).setText("7명");
            ((TextView) root.findViewById(R.id.waiting_rule)).setText(
                    root.getResources().getString(R.string.group_rule_summary, "다수결"));
            ((TextView) root.findViewById(R.id.waiting_hint)).setText(R.string.group_waiting_hint);
            LinearLayout members = root.findViewById(R.id.member_list);
            for (int index = 0; index < 3; index++) {
                View member = LayoutInflater.from(root.getContext())
                        .inflate(R.layout.item_group_member, members, false);
                ((TextView) member.findViewById(R.id.member_alias)).setText("함께하는 차량");
                ((TextView) member.findViewById(R.id.member_status)).setText(R.string.group_ready);
                members.addView(member);
            }
            layout(root, 840, 1184);
            for (int id : new int[]{R.id.waiting_code, R.id.copy_code,
                    R.id.waiting_cars, R.id.waiting_people, R.id.toggle_ready,
                    R.id.start_trip, R.id.leave_group}) {
                assertOnScreen(root, id);
            }
        });
    }

    @Test public void switchingResourceConfigurationsRestoresExpandedPanels() {
        onMain(() -> {
            for (int layoutId : new int[]{R.layout.activity_group_lobby, R.layout.activity_group_waiting}) {
                for (int width : new int[]{1700, 840, 1700}) {
                    ViewGroup root = inflate(layoutId, width, 1184);
                    layout(root, width, 1184);
                    View hero = root.findViewById(R.id.group_hero_panel);
                    if (width == 840) {
                        assertNull(hero);
                    } else {
                        assertNotNull(hero);
                        Rect heroBounds = bounds(root, hero);
                        Rect formBounds = bounds(root, root.findViewById(R.id.group_form_scroll));
                        assertTrue("Expanded panels must not overlap", heroBounds.right <= formBounds.left);
                    }
                }
            }
        });
    }

    @Test public void narrowWindowStacksVehicleInputsWithoutHorizontalClipping() {
        onMain(() -> {
            ViewGroup root = inflate(R.layout.activity_group_lobby, 480, 640);
            layout(root, 480, 640);
            Rect alias = bounds(root, root.findViewById(R.id.vehicle_alias));
            Rect passengers = bounds(root, root.findViewById(R.id.passengers_plus));
            assertTrue("Vehicle alias stays inside the window", alias.left >= 0 && alias.right <= 480);
            assertTrue("Passenger controls stay inside the window",
                    passengers.left >= 0 && passengers.right <= 480);
            assertTrue("Passenger controls move below the alias", passengers.top >= alias.bottom);
        });
    }

    private ViewGroup inflate(int layoutId, int width, int height) {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration configuration = new Configuration(target.getResources().getConfiguration());
        configuration.screenWidthDp = width;
        configuration.screenHeightDp = height;
        configuration.smallestScreenWidthDp = Math.min(width, height);
        configuration.densityDpi = DisplayMetrics.DENSITY_DEFAULT;
        configuration.fontScale = 1f;
        configuration.orientation = width > height
                ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
        Context window = target.createConfigurationContext(configuration);
        Context themed = new ContextThemeWrapper(window, R.style.Theme_CoDrive_Group);
        return (ViewGroup) LayoutInflater.from(themed).inflate(layoutId, null, false);
    }

    private void layout(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private Rect bounds(ViewGroup root, View view) {
        assertNotNull(view);
        Rect rect = new Rect();
        view.getDrawingRect(rect);
        root.offsetDescendantRectToMyCoords(view, rect);
        return rect;
    }

    private void assertOnScreen(ViewGroup root, int id) {
        View view = root.findViewById(id);
        assertNotNull(view);
        assertEquals(View.VISIBLE, view.getVisibility());
        Rect rect = bounds(root, view);
        assertTrue("Control has size: " + id, rect.width() > 0 && rect.height() > 0);
        assertTrue("Control fits the app window: " + id,
                rect.left >= 0 && rect.top >= 0 && rect.right <= root.getWidth()
                        && rect.bottom <= root.getHeight());
    }

    private void onMain(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
