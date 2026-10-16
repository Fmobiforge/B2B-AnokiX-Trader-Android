package com.anokix.traderapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.MarketCart;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.MediaUrls;
import com.anokix.traderapp.network.dto.CartData;
import com.anokix.traderapp.network.dto.MarketplaceData;
import com.anokix.traderapp.network.dto.ReferenceData;
import com.anokix.traderapp.ui.views.LoopingBannerVideoView;
import com.anokix.traderapp.ui.views.RobotoBoldTextView;
import com.anokix.traderapp.ui.views.RobotoTextView;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Trader Marketplace — wired to GET /api/trader/marketplace.
 *
 * Sections (top to bottom): selected distributor + constant stats with a
 * "Change Distributor" picker, an auto-advancing banner slider, business-insight
 * summary cards, search + scan, the 8 quick-menu buttons, parent categories,
 * a static Business Opportunities card, Recommended for You, Current Promotions,
 * Best Sellers, and the bottom feature strip. The header carries a live cart
 * badge backed by {@link MarketCart}.
 */
public class MarketplaceActivity extends AppCompatActivity {

    private final ApiClient api = ApiClient.get(this);
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());

    private TextView cartBadge;
    private TextView distName, distArea, distAvatar;
    private ImageView distAvatarImage;
    private LinearLayout insightsContainer, menuContainer, categoriesContainer,
            recommendedRow, promotionsContainer, bestSellersRow, bannerDots;
    private View recommendedSection, promotionsSection, bestSellersSection;
    private ViewPager2 bannerPager;
    private View containerBannerPager;
    private NestedScrollView scroll;

    private MarketplaceData data;
    private List<MarketplaceData.Distributor> myDistributors = new ArrayList<>();
    private String selectedDistributorId;
    private String currency = "R";

    private Runnable bannerAdvance;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_marketplace);

        cartBadge = findViewById(R.id.cartBadge);
        distName = findViewById(R.id.distName);
        distArea = findViewById(R.id.distArea);
        distAvatar = findViewById(R.id.distAvatar);
        distAvatarImage = findViewById(R.id.distAvatarImage);
        insightsContainer = findViewById(R.id.insightsContainer);
        menuContainer = findViewById(R.id.menuContainer);
        categoriesContainer = findViewById(R.id.categoriesContainer);
        recommendedRow = findViewById(R.id.recommendedRow);
        promotionsContainer = findViewById(R.id.promotionsContainer);
        bestSellersRow = findViewById(R.id.bestSellersRow);
        bannerDots = findViewById(R.id.bannerDots);
        bannerPager = findViewById(R.id.bannerPager);
        containerBannerPager = findViewById(R.id.containerBannerPager);
        // Hide first and show it after load
        containerBannerPager.setVisibility(View.GONE);

        scroll = findViewById(R.id.marketplaceScroll);
        recommendedSection = findViewById(R.id.recommendedSection);
        promotionsSection = findViewById(R.id.promotionsSection);
        bestSellersSection = findViewById(R.id.bestSellersSection);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.cartButton).setOnClickListener(v ->
                startActivity(new Intent(this, CartActivity.class)));
        findViewById(R.id.btnChangeDistributor).setOnClickListener(v -> showDistributorPicker());
        findViewById(R.id.btnViewAllCategories).setOnClickListener(v ->
                startActivity(new Intent(this, AllCategoriesActivity.class)));
        findViewById(R.id.btnViewAllRecommended).setOnClickListener(v ->
                openCollection("recommended", getString(R.string.recommended_for_you)));
        findViewById(R.id.btnViewAllBestSellers).setOnClickListener(v ->
                openCollection("best_sellers", getString(R.string.best_sellers)));
        findViewById(R.id.btnViewAllPromotions).setOnClickListener(v ->
                startActivity(new Intent(this, PromotionsActivity.class)));
        findViewById(R.id.scanButton).setOnClickListener(v ->
                toast(getString(R.string.scanner_coming_soon)));
        findViewById(R.id.btnOpportunity).setOnClickListener(v ->
                toast(getString(R.string.coming_soon)));

        buildMenuButtons();
        buildFeatureStrip();
        loadCategories();
        loadMarketplace(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCart();
    }

    @Override
    protected void onDestroy() {
        stopBannerAutoScroll();
        super.onDestroy();
    }

    // ---- Data loading ----------------------------------------------------

    private void loadMarketplace(String distributorId) {
        api.getMarketplace(distributorId, new ApiCallback<MarketplaceData>() {
            @Override
            public void onSuccess(MarketplaceData result) {
                data = result;
                if (result == null) return;
                currency = result.currencySymbol();
                if (result.my_distributors != null) {
                    myDistributors = result.my_distributors;
                }
                if (result.selected_distributor != null) {
                    selectedDistributorId = String.valueOf(result.selected_distributor.id);
                    // Drives the Make Order delivery-slot generation (per-distributor).
                    MarketCart.get().setDistributorDeliveryDays(
                            result.selected_distributor.preferred_delivery_days);
                }
                bindDistributor(result.selected_distributor);
                buildBanners(result.banners);
                buildInsights(result.business_insights);
                buildProductRow(recommendedRow, recommendedSection, result.recommended);
                buildProductRow(bestSellersRow, bestSellersSection, result.best_sellers);
                buildPromotions(result.promotions);
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    private void loadCategories() {
        api.getReference(new ApiCallback<ReferenceData>() {
            @Override
            public void onSuccess(ReferenceData result) {
                if (result != null) buildCategories(result.product_categories);
            }

            @Override
            public void onError(String message) {
                // Categories are non-critical; leave the row empty on failure.
            }
        });
    }

    // ---- Distributor -----------------------------------------------------

    private void bindDistributor(MarketplaceData.Distributor d) {
        if (d == null) return;
        distName.setText(d.displayName());
        distAvatar.setText(d.displayName().substring(0, 1).toUpperCase());
        LogoLoader.load(distAvatarImage, d.logo_url);
        if (d.address != null) distArea.setText(d.address);
    }

    private void showDistributorPicker() {
        if (myDistributors == null || myDistributors.isEmpty()) {
            toast(getString(R.string.no_distributors));
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        content.setPadding(pad, dp(8), pad, 0);

        final String[] chosen = {selectedDistributorId};
        final List<View> rows = new ArrayList<>();

        for (MarketplaceData.Distributor d : myDistributors) {
            View row = inflater.inflate(R.layout.item_distributor_option, content, false);
            String id = String.valueOf(d.id);
            ((TextView) row.findViewById(R.id.optionAvatar))
                    .setText(d.displayName().substring(0, 1).toUpperCase());
            LogoLoader.load(row.findViewById(R.id.optionAvatarImage), d.logo_url);
            ((TextView) row.findViewById(R.id.optionName)).setText(d.displayName());
            ((TextView) row.findViewById(R.id.optionAddress))
                    .setText(d.address != null ? d.address : "");
            View check = row.findViewById(R.id.optionCheck);
            View current = row.findViewById(R.id.optionCurrent);
            Runnable mark = () -> {
                for (int i = 0; i < rows.size(); i++) {
                    View r = rows.get(i);
                    boolean sel = String.valueOf(myDistributors.get(i).id).equals(chosen[0]);
                    r.findViewById(R.id.optionCheck).setVisibility(sel ? View.VISIBLE : View.GONE);
                    r.findViewById(R.id.optionCurrent).setVisibility(sel ? View.VISIBLE : View.GONE);
                    r.findViewById(R.id.optionRoot).setBackgroundResource(
                            sel ? R.drawable.bg_trader_type_card_selected : R.drawable.bg_summary_card);
                }
            };
            boolean sel = id.equals(chosen[0]);
            check.setVisibility(sel ? View.VISIBLE : View.GONE);
            current.setVisibility(sel ? View.VISIBLE : View.GONE);
            row.findViewById(R.id.optionRoot).setBackgroundResource(
                    sel ? R.drawable.bg_trader_type_card_selected : R.drawable.bg_summary_card);
            row.setOnClickListener(v -> {
                chosen[0] = id;
                mark.run();
            });
            rows.add(row);
            content.addView(row);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.change_distributor)
                .setView(wrapScroll(content))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.apply, (dlg, w) -> {
                    if (chosen[0] != null && !chosen[0].equals(selectedDistributorId)) {
                        selectedDistributorId = chosen[0];
                        loadMarketplace(chosen[0]);
                    }
                })
                .show();
    }

    private View wrapScroll(View child) {
        NestedScrollView sv = new NestedScrollView(this);
        sv.addView(child);
        return sv;
    }

    // ---- Banners ---------------------------------------------------------

    private void buildBanners(List<MarketplaceData.Banner> banners) {
        stopBannerAutoScroll();
        if (banners == null || banners.isEmpty()) {
            containerBannerPager.setVisibility(View.GONE);
            return;
        }
        containerBannerPager.setVisibility(View.VISIBLE);

        BannerAdapter adapter = new BannerAdapter(banners);
        bannerPager.setAdapter(adapter);
        buildDots(banners.size());
        bannerPager.setOffscreenPageLimit(banners.size());
        bannerPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                adapter.setActivePosition(position);
            }
        });
        bannerPager.post(adapter::playActiveBanner);
        if (banners.size() > 1) startBannerAutoScroll(banners.size());
    }

    private void buildDots(int count) {
        bannerDots.removeAllViews();
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(7), dp(7));
            lp.setMarginEnd(dp(5));
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(i == 0
                    ? R.drawable.bg_step_dot_active : R.drawable.bg_step_dot_inactive);
            bannerDots.addView(dot);
        }
    }

    private void updateDots(int active) {
        for (int i = 0; i < bannerDots.getChildCount(); i++) {
            bannerDots.getChildAt(i).setBackgroundResource(i == active
                    ? R.drawable.bg_step_dot_active : R.drawable.bg_step_dot_inactive);
        }
    }

    private void startBannerAutoScroll(int count) {
        bannerAdvance = new Runnable() {
            @Override
            public void run() {
                int next = (bannerPager.getCurrentItem() + 1) % count;
                bannerPager.setCurrentItem(next, true);
                bannerHandler.postDelayed(this, 8000);
            }
        };
        bannerHandler.postDelayed(bannerAdvance, 8000);
    }

    private void stopBannerAutoScroll() {
        if (bannerAdvance != null) bannerHandler.removeCallbacks(bannerAdvance);
    }

    private class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.VH> {
        private final List<MarketplaceData.Banner> items;
        private int activePosition = 0;

        BannerAdapter(List<MarketplaceData.Banner> items) {
            this.items = items;
        }

        void setActivePosition(int position) {
            if (position < 0 || position >= items.size()) return;
            if (activePosition == position) return;
            int previous = activePosition;
            activePosition = position;
            syncBannerVideoPlayback(previous, position);
        }

        void playActiveBanner() {
            int position = bannerPager.getCurrentItem();
            if (position < 0 || position >= items.size()) return;
            int previous = activePosition;
            activePosition = position;
            syncBannerVideoPlayback(previous, position);
        }

        private void syncBannerVideoPlayback(int previous, int current) {
            RecyclerView rv = (RecyclerView) bannerPager.getChildAt(0);
            if (rv == null) return;
            if (previous != current) {
                VH oldHolder = (VH) rv.findViewHolderForAdapterPosition(previous);
                if (oldHolder != null) {
                    oldHolder.video.pausePlayback();
                }
            }
            VH holder = (VH) rv.findViewHolderForAdapterPosition(current);
            if (holder == null) return;
            MarketplaceData.Banner banner = items.get(current);
            String url = banner.mediaUrl();
            if (banner.isVideo() && url != null) {
                holder.video.post(() -> holder.video.play(url));
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_banner, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            MarketplaceData.Banner b = items.get(position);
            h.title.setText(b.name != null ? b.name : "");
            h.description.setText(b.description != null ? b.description : "");
            String url = b.mediaUrl();
            if (b.isVideo() && url != null) {
                h.image.setVisibility(View.GONE);
                h.video.setVisibility(View.VISIBLE);
                if (position == activePosition) {
                    h.video.post(() -> h.video.play(url));
                } else {
                    h.video.pausePlayback();
                }
            } else {
                h.video.stopPlayback();
                h.video.setVisibility(View.GONE);
                h.image.setVisibility(View.VISIBLE);
                int placeholder = R.drawable.bg_banner_placeholder;
                if (url != null && GlideGuard.canLoad(h.image)) {
                    Glide.with(h.image)
                            .load(url)
                            .placeholder(placeholder)
                            .error(placeholder)
                            .centerCrop()
                            .into(h.image);
                } else {
                    h.image.setImageResource(placeholder);
                }
            }
            View.OnClickListener later = v -> toast(getString(R.string.coming_soon));
            h.shop.setOnClickListener(later);
            h.promos.setOnClickListener(later);
        }

        @Override
        public void onViewRecycled(@NonNull VH holder) {
            holder.video.stopPlayback();
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, description;
            final ImageView image;
            final LoopingBannerVideoView video;
            final View shop, promos;

            VH(@NonNull View v) {
                super(v);
                title = v.findViewById(R.id.bannerTitle);
                description = v.findViewById(R.id.bannerDescription);
                image = v.findViewById(R.id.bannerImage);
                video = v.findViewById(R.id.bannerVideo);
                shop = v.findViewById(R.id.bannerShop);
                promos = v.findViewById(R.id.bannerPromos);
            }
        }
    }

    // ---- Business insights ----------------------------------------------

    private void buildInsights(MarketplaceData.BusinessInsights insights) {
        insightsContainer.removeAllViews();
        if (insights == null || insights.summary_cards == null || insights.summary_cards.isEmpty()) {
            findViewById(R.id.insightsTitle).setVisibility(View.GONE);
            return;
        }
        findViewById(R.id.insightsTitle).setVisibility(View.VISIBLE);
        List<MarketplaceData.SummaryCard> cards = insights.summary_cards;
        for (int i = 0; i < cards.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(8);
            row.setLayoutParams(rowLp);
            row.addView(insightCard(cards.get(i)));
            if (i + 1 < cards.size()) {
                row.addView(insightCard(cards.get(i + 1)));
            } else {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                row.addView(spacer);
            }
            insightsContainer.addView(row);
        }
    }

    private View insightCard(MarketplaceData.SummaryCard card) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        box.setLayoutParams(lp);
        box.setBackgroundResource(R.drawable.bg_summary_card);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));

        RobotoTextView label = new RobotoTextView(this);
        label.setText(card.label != null ? card.label : "");
        label.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        label.setTextSize(11);
        box.addView(label);

        RobotoBoldTextView value = new RobotoBoldTextView(this);
        value.setText(card.formatted_value != null ? card.formatted_value : "");
        value.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        value.setTextSize(16);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = dp(2);
        value.setLayoutParams(vlp);
        box.addView(value);

        String sub = card.subtitle();
        if (sub != null) {
            RobotoTextView trend = new RobotoTextView(this);
            trend.setText(sub);
            trend.setTextColor(ContextCompat.getColor(this,
                    card.trendUp() ? R.color.success : R.color.text_secondary));
            trend.setTextSize(10);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = dp(2);
            trend.setLayoutParams(tlp);
            box.addView(trend);
        }
        return box;
    }

    // ---- Quick menu ------------------------------------------------------

    private void buildMenuButtons() {
        int[] icons = {R.drawable.ic_orders, R.drawable.ic_wallet, R.drawable.ic_credit,
                R.drawable.ic_deliveries, R.drawable.ic_promotions, R.drawable.ic_vas,
                R.drawable.ic_analytics, R.drawable.ic_more_vert};
        String[] labels = {getString(R.string.menu_my_orders), getString(R.string.menu_wallet),
                getString(R.string.menu_credit), getString(R.string.menu_deliveries),
                getString(R.string.menu_promotions), getString(R.string.menu_vas),
                getString(R.string.menu_analytics), getString(R.string.menu_more)};
        for (int i = 0; i < icons.length; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            item.setLayoutParams(lp);

            android.widget.FrameLayout iconWrap = new android.widget.FrameLayout(this);
            iconWrap.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
            iconWrap.setBackgroundResource(R.drawable.bg_menu_icon);

            ImageView icon = new ImageView(this);
            android.widget.FrameLayout.LayoutParams iconLp =
                    new android.widget.FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
            icon.setLayoutParams(iconLp);
            icon.setImageResource(icons[i]);
            icon.setColorFilter(ContextCompat.getColor(this, R.color.purple_primary));
            iconWrap.addView(icon);

            if (i == 4) { // Promotions → NEW badge
                RobotoBoldTextView badge = new RobotoBoldTextView(this);
                android.widget.FrameLayout.LayoutParams blp =
                        new android.widget.FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
                badge.setLayoutParams(blp);
                badge.setText(R.string.badge_new);
                badge.setTextColor(0xFFFFFFFF);
                badge.setTextSize(7);
                badge.setBackgroundResource(R.drawable.bg_pill_new);
                badge.setPadding(dp(4), dp(1), dp(4), dp(1));
                iconWrap.addView(badge);
            }
            item.addView(iconWrap);

            RobotoTextView label = new RobotoTextView(this);
            label.setText(labels[i]);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            label.setTextSize(10);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(6);
            label.setLayoutParams(llp);
            item.addView(label);

            item.setOnClickListener(v -> onMenuClick(idx));
            menuContainer.addView(item);
        }
    }

    private void onMenuClick(int idx) {
        switch (idx) {
            case 0: // My Orders
            case 3: // Deliveries
                openMain("orders");
                break;
            case 1: // Wallet
                openMain("wallet");
                break;
            case 2: // Credit
                toast(getString(R.string.coming_soon));
                break;
            case 4: // Promotions → scroll to section
                scrollToPromotions();
                break;
            case 5: // VAS Services
                startActivity(new Intent(this, AirtimeActivity.class));
                break;
            case 6: // Analytics
                startActivity(new Intent(this, AnalyticsActivity.class));
                break;
            default: // More
                toast(getString(R.string.coming_soon));
        }
    }

    private void openMain(String tab) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_OPEN_TAB, tab);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }

    private void scrollToPromotions() {
        if (promotionsSection.getVisibility() != View.VISIBLE) {
            toast(getString(R.string.no_promotions));
            return;
        }
        scroll.post(() -> scroll.smoothScrollTo(0, promotionsSection.getTop()));
    }

    // ---- Categories ------------------------------------------------------

    private void buildCategories(List<ReferenceData.ProductCategory> categories) {
        categoriesContainer.removeAllViews();
        categoriesContainer.setGravity(Gravity.TOP);
        if (categories == null) return;
        for (ReferenceData.ProductCategory c : categories) {
            if (c.parent_id != null) continue; // parents only
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(68),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP;
            item.setLayoutParams(lp);

            android.widget.FrameLayout iconWrap = new android.widget.FrameLayout(this);
            iconWrap.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
            iconWrap.setBackgroundResource(R.drawable.bg_menu_icon);

            ImageView icon = new ImageView(this);
            android.widget.FrameLayout.LayoutParams iconLp =
                    new android.widget.FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
            icon.setLayoutParams(iconLp);
            String logoUrl = MediaUrls.resolve(c.logo_url);
            if (logoUrl != null && GlideGuard.canLoad(icon)) {
                Glide.with(icon)
                        .load(logoUrl)
                        .centerCrop()
                        .placeholder(CategoryIcons.iconFor(c))
                        .error(CategoryIcons.iconFor(c))
                        .into(icon);
            } else {
                icon.setImageResource(CategoryIcons.iconFor(c));
                icon.setColorFilter(ContextCompat.getColor(this, R.color.purple_primary));
            }
            iconWrap.addView(icon);
            item.addView(iconWrap);

            RobotoTextView title = new RobotoTextView(this);
            title.setText(c.title != null ? c.title : "");
            title.setGravity(Gravity.CENTER);
            title.setMaxLines(2);
            title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            title.setTextSize(10);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = dp(6);
            title.setLayoutParams(tlp);
            item.addView(title);

            item.setOnClickListener(v -> openCategoryProducts(c));
            categoriesContainer.addView(item);
        }
    }

    private void openCategoryProducts(ReferenceData.ProductCategory c) {
        Intent i = new Intent(this, CategoryProductsActivity.class);
        i.putExtra(CategoryProductsActivity.EXTRA_CATEGORY_ID, String.valueOf(c.id));
        i.putExtra(CategoryProductsActivity.EXTRA_CATEGORY_TITLE, c.title);
        startActivity(i);
    }

    private void openCollection(String type, String title) {
        Intent i = new Intent(this, CollectionActivity.class);
        i.putExtra(CollectionActivity.EXTRA_TYPE, type);
        i.putExtra(CollectionActivity.EXTRA_TITLE, title);
        startActivity(i);
    }

    // ---- Product rows (recommended / best sellers) ----------------------

    private void buildProductRow(LinearLayout row, View section, List<MarketplaceData.Product> products) {
        row.removeAllViews();
        if (products == null || products.isEmpty()) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (MarketplaceData.Product p : products) {
            View card = inflater.inflate(R.layout.item_market_card, row, false);
            ImageView img = card.findViewById(R.id.productImage);
            ((TextView) card.findViewById(R.id.productName)).setText(p.name != null ? p.name : "");
            ((TextView) card.findViewById(R.id.productPrice)).setText(p.formattedPrice(currency));
            String url = p.imageUrl();
            if (url != null && GlideGuard.canLoad(img)) {
                Glide.with(img).load(url).centerCrop().into(img);
            }
            card.setOnClickListener(v -> openProductDetail(p));
            card.findViewById(R.id.productAdd).setOnClickListener(v -> openProductDetail(p));
            row.addView(card);
        }
    }

    private void openProductDetail(MarketplaceData.Product p) {
        Intent i = new Intent(this, ProductDetailActivity.class);
        i.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, p.id);
        i.putExtra(ProductDetailActivity.EXTRA_NAME, p.name);
        i.putExtra(ProductDetailActivity.EXTRA_IMAGE, p.imageUrl());
        i.putExtra(ProductDetailActivity.EXTRA_PRICE, p.priceValue());
        i.putExtra(ProductDetailActivity.EXTRA_BRAND, p.brand_name);
        i.putExtra(ProductDetailActivity.EXTRA_CATEGORY, p.category_title);
        i.putExtra(ProductDetailActivity.EXTRA_SKU, p.sku);
        i.putExtra(ProductDetailActivity.EXTRA_BARCODE, p.barcode);
        i.putExtra(ProductDetailActivity.EXTRA_DESCRIPTION, p.description);
        i.putExtra(ProductDetailActivity.EXTRA_STOCK, p.stockCount());
        i.putExtra(ProductDetailActivity.EXTRA_DISTRIBUTOR,
                data != null && data.selected_distributor != null
                        ? data.selected_distributor.displayName() : "");
        i.putExtra(ProductDetailActivity.EXTRA_CURRENCY, currency);
        startActivity(i);
    }

    // ---- Promotions ------------------------------------------------------

    private void buildPromotions(List<MarketplaceData.Promotion> promotions) {
        promotionsContainer.removeAllViews();
        if (promotions == null || promotions.isEmpty()) {
            promotionsSection.setVisibility(View.GONE);
            return;
        }
        promotionsSection.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (MarketplaceData.Promotion p : promotions) {
            View card = inflater.inflate(R.layout.item_promotion_card, promotionsContainer, false);
            PromotionCardBinder.bind(this, card, p, () -> toast(getString(R.string.coming_soon)));
            promotionsContainer.addView(card);
        }
    }

    // ---- Bottom feature strip -------------------------------------------

    private void buildFeatureStrip() {
        bindFeature(R.id.featureBestPrices, R.drawable.ic_star,
                getString(R.string.feature_best_prices), getString(R.string.feature_best_prices_sub));
        bindFeature(R.id.featureFastDelivery, R.drawable.ic_deliveries,
                getString(R.string.feature_fast_delivery), getString(R.string.feature_fast_delivery_sub));
        bindFeature(R.id.featureSecurePayments, R.drawable.ic_shield_check,
                getString(R.string.feature_secure_payments), getString(R.string.feature_secure_payments_sub));
        bindFeature(R.id.featureRewards, R.drawable.ic_promo_gift,
                getString(R.string.feature_rewards), getString(R.string.feature_rewards_sub));
    }

    private void bindFeature(int includeId, int icon, String title, String sub) {
        View root = findViewById(includeId);
        ((ImageView) root.findViewById(R.id.featureIcon)).setImageResource(icon);
        ((TextView) root.findViewById(R.id.featureTitle)).setText(title);
        ((TextView) root.findViewById(R.id.featureSub)).setText(sub);
    }

    // ---- Cart badge ------------------------------------------------------

    /**
     * Paint the badge from the local mirror immediately, then refresh it from the
     * server cart (GET /api/trader/cart) so it reflects the real backend state.
     */
    private void refreshCart() {
        refreshCartBadge();
        api.getCart(new ApiCallback<CartData>() {
            @Override
            public void onSuccess(CartData result) {
                MarketCart.get().hydrate(result);
                refreshCartBadge();
            }

            @Override
            public void onError(String message) {
                // Keep the local mirror on failure.
            }
        });
    }

    private void refreshCartBadge() {
        int count = MarketCart.get().itemCount();
        if (count > 0) {
            cartBadge.setVisibility(View.VISIBLE);
            cartBadge.setText(count > 99 ? "99+" : String.valueOf(count));
        } else {
            cartBadge.setVisibility(View.GONE);
        }
    }

    // ---- Helpers ---------------------------------------------------------

    private void toast(String msg) {
        if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
