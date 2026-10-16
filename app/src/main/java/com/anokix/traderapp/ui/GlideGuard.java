package com.anokix.traderapp.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;

/**
 * Async API callbacks can arrive after the host activity is gone; Glide then
 * throws "You cannot start a load for a destroyed activity". Check
 * {@link #canLoad(View)} before every {@code Glide.with(...)} call.
 */
public final class GlideGuard {

    private GlideGuard() {}

    /** True when the view's host activity is still alive (safe to call Glide.with). */
    public static boolean canLoad(View view) {
        return view != null && canLoad(view.getContext());
    }

    /** True when the context's host activity is still alive (safe to call Glide.with). */
    public static boolean canLoad(Context ctx) {
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) {
                Activity a = (Activity) ctx;
                return !a.isFinishing() && !a.isDestroyed();
            }
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return ctx != null;
    }
}
