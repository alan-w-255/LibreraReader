package com.foobnix.ui2;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.foobnix.android.utils.Dips;
import com.foobnix.pdf.info.R;

/** D-pad focus helpers for TV (dangbei-tv patch). */
public class TvFocus {

    /** Accent overlay for focused TV controls (translucent orange). */
    public static final int HIGHLIGHT = 0xB3FF9800;

    /**
     * Color highlight on focus (no magnification, per user request).
     * Saves the original background in a tag and restores it on blur.
     */
    public static final View.OnFocusChangeListener COLOR_BUTTON = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            Object prev = v.getTag(R.id.tvfocus_orig_bg);
            if (hasFocus) {
                if (prev == null) {
                    v.setTag(R.id.tvfocus_orig_bg, v.getBackground());
                    GradientDrawable d = new GradientDrawable();
                    d.setColor(HIGHLIGHT);
                    d.setCornerRadius(Dips.dpToPx(4));
                    v.setBackground(d);
                }
            } else if (prev != null) {
                v.setBackground((android.graphics.drawable.Drawable) prev);
                v.setTag(R.id.tvfocus_orig_bg, null);
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
                c.setOnFocusChangeListener(COLOR_BUTTON);
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

    /**
     * Explicit UP/DOWN hop between the two reading bars. Default focus search
     * prefers the huge page view over the opposite bar, dropping focus to the page.
     */
    public static boolean hopBetweenBars(Activity a, View from, View to) {
        if (a == null) {
            return false;
        }
        if (!inside(from, a.getCurrentFocus())) {
            return false;
        }
        ViewGroup g = asGroup(to);
        if (g == null) {
            return false;
        }
        View f = firstFocusable(g);
        if (f != null) {
            f.requestFocus();
            return true;
        }
        return false;
    }
}
