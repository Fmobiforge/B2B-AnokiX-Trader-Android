package com.anokix.traderapp.ui;

import android.app.Activity;
import android.app.Dialog;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.dto.MarketplaceData;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen viewer for the marketplace "Stories" row (portal story modal). Each story
 * plays for {@link #STORY_MS}, then advances; tapping the left third goes back, anywhere
 * else skips ahead, and the last story closes the viewer. {@link Listener#onStoryShown} fires
 * once per story shown so the caller can record the campaign view.
 */
final class StoryViewer {

    interface Listener {
        void onStoryShown(@NonNull MarketplaceData.Promotion story);
    }

    private static final long STORY_MS = 5000L;
    private static final long TICK_MS = 50L;

    private StoryViewer() {
    }

    static void show(@NonNull Activity activity, @NonNull List<MarketplaceData.Promotion> stories,
                     int start, @NonNull Listener listener) {
        if (stories.isEmpty() || activity.isFinishing() || activity.isDestroyed()) return;

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_market_story, null, false);
        dialog.setContentView(root);

        ImageView image = root.findViewById(R.id.storyImage);
        TextView title = root.findViewById(R.id.storyTitle);
        TextView desc = root.findViewById(R.id.storyDesc);
        TextView distributor = root.findViewById(R.id.storyDistributor);
        LinearLayout barsRow = root.findViewById(R.id.storyBars);

        List<ProgressBar> bars = new ArrayList<>();
        for (int i = 0; i < stories.size(); i++) {
            ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            bar.setProgressDrawable(ContextCompat.getDrawable(activity, R.drawable.bg_story_progress));
            bar.setMax((int) STORY_MS);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) lp.setMarginStart(Math.round(4 * activity.getResources().getDisplayMetrics().density));
            barsRow.addView(bar, lp);
            bars.add(bar);
        }

        Handler handler = new Handler(Looper.getMainLooper());
        final int[] index = {Math.max(0, Math.min(start, stories.size() - 1))};
        final long[] elapsed = {0};

        Runnable[] showCurrent = new Runnable[1];
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                elapsed[0] += TICK_MS;
                bars.get(index[0]).setProgress((int) Math.min(elapsed[0], STORY_MS));
                if (elapsed[0] >= STORY_MS) {
                    if (index[0] + 1 < stories.size()) {
                        index[0]++;
                        showCurrent[0].run();
                    } else {
                        dialog.dismiss();
                    }
                    return;
                }
                handler.postDelayed(this, TICK_MS);
            }
        };

        showCurrent[0] = () -> {
            handler.removeCallbacks(tick);
            elapsed[0] = 0;
            for (int i = 0; i < bars.size(); i++) {
                bars.get(i).setProgress(i < index[0] ? (int) STORY_MS : 0);
            }
            MarketplaceData.Promotion story = stories.get(index[0]);
            title.setText(story.name == null ? "" : story.name);
            desc.setText(story.description == null ? "" : story.description);
            desc.setVisibility(story.description == null || story.description.trim().isEmpty()
                    ? View.GONE : View.VISIBLE);
            distributor.setText(story.distributorName());
            String url = story.mediaUrl();
            if (url != null && !story.isVideo() && GlideGuard.canLoad(image)) {
                Glide.with(image).load(url).fitCenter().into(image);
            } else {
                image.setImageResource(R.drawable.bg_banner_placeholder);
            }
            listener.onStoryShown(story);
            handler.postDelayed(tick, TICK_MS);
        };

        root.findViewById(R.id.storyClose).setOnClickListener(v -> dialog.dismiss());
        root.findViewById(R.id.storyNext).setOnClickListener(v -> {
            if (index[0] + 1 < stories.size()) {
                index[0]++;
                showCurrent[0].run();
            } else {
                dialog.dismiss();
            }
        });
        root.findViewById(R.id.storyPrev).setOnClickListener(v -> {
            if (index[0] > 0) index[0]--;
            showCurrent[0].run();
        });
        dialog.setOnDismissListener(d -> handler.removeCallbacksAndMessages(null));

        dialog.show();
        showCurrent[0].run();
    }
}
