package com.foobnix.ui2;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.foobnix.android.utils.Dips;

/** D-pad focus helpers for TV (dangbei-tv patch). */
public class TvFocus {

    public static final View.OnFocusChangeListener SCALE_BUTTON = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            v.animate().scaleX(hasFocus ? 1.25f : 1.0f).scaleY(hasFocus ? 1.25f : 1.0f).setDuration(100).start();
            if (Build.VERSION.SDK_INT >= 21) {
                v.setTranslationZ(hasFocus ? Dips.dpToPx(10) : 0);
            }
        }
    };

    public static boolean inside(View ancestor, View v) {
        if (ancestor == null || v == null) {
            return false;
        }
        ViewParent p = v.getParent();
        while (p != null) {
            if (p == ancestor) {
                return true;
            }
            p = p.getParent();
        }
        return false;
    }

    /** Make every visible clickable view focusable with a visible focus effect. */
    public static void makeFocusable(ViewGroup vg) {
        if (vg == null) {
            return;
        }
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c == null || c.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (c.isClickable()) {
                if (!c.isFocusable()) {
                    c.setFocusable(true);
                }
                c.setOnFocusChangeListener(SCALE_BUTTON);
            }
            if (c instanceof ViewGroup) {
                makeFocusable((ViewGroup) c);
            }
        }
    }

    /** Depth-first, preferring leaf buttons over focusable containers. */
    public static View firstFocusable(ViewGroup vg) {
        if (vg == null) {
            return null;
        }
        View fallback = null;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View c = vg.getChildAt(i);
            if (c == null || c.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (c instanceof ViewGroup) {
                View f = firstFocusable((ViewGroup) c);
                if (f != null) {
                    return f;
                }
            }
            if (c.isFocusable() && fallback == null) {
                fallback = c;
            }
        }
        return fallback;
    }

    private static ViewGroup asGroup(View v) {
        return v instanceof ViewGroup ? (ViewGroup) v : null;
    }

    /** If focus is not already in one of the bars, move it to the first bar button. */
    public static boolean routeToBars(Activity a, View... bars) {
        if (a == null) {
            return false;
        }
        for (View bar : bars) {
            ViewGroup g = asGroup(bar);
            if (g != null) {
                makeFocusable(g);
            }
        }
        View focused = a.getCurrentFocus();
        for (View bar : bars) {
            if (bar != null && inside(bar, focused)) {
                return false;
            }
        }
        for (View bar : bars) {
            ViewGroup g = asGroup(bar);
            if (g == null) {
                continue;
            }
            View f = firstFocusable(g);
            if (f != null) {
                f.requestFocus();
                return true;
            }
        }
        return false;
    }
}
