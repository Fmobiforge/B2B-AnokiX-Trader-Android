package com.anokix.traderapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.MarketCart;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.MediaUrls;
import com.anokix.traderapp.network.dto.CartData;
import com.anokix.traderapp.network.dto.CollectionData;
import com.anokix.traderapp.network.dto.DistributorListData;
import com.anokix.traderapp.network.dto.MarketplaceData;
import com.anokix.traderapp.network.dto.ReferenceData;
import com.anokix.traderapp.ui.pos.PosScanDialogFragment;
import com.anokix.traderapp.ui.views.LoopingBannerVideoView;
import com.anokix.traderapp.ui.views.RobotoTextView;
import com.bumptech.glide.Glide;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Trader Marketplace — wired to GET /api/trader/marketplace and laid out after the Trader
 * Portal's /marketplace page, as a single mobile column:
 *
 * <ol>
 *   <li>Preferred distributor + delivery area (tap for details, "Change" to switch), and the
 *       Airtime &amp; Data / Electricity / POS &amp; Devices shortcuts.</li>
 *   <li>Campaign banner slider — each slide carries its campaign-type badge and opens the
 *       campaign sheet ({@link CampaignSheet}).</li>
 *   <li>Search (api/trader/marketplace/search, results inline) and barcode scan.</li>
 *   <li>Categories, Recommended for You, Best Deals, Exclusive Deals, Promotions.</li>
 *   <li>News from the distributor, Stories ({@link StoryViewer}), Top Suppliers, Best
 *       Sellers, My Distributors and Recent Orders.</li>
 * </ol>
 *
 * Campaign opens and campaign-driven product views are reported to the analytics endpoints
 * (campaign-click / campaign-event) exactly as the portal does. The header carries a live
 * cart badge backed by {@link MarketCart}.
 */
public class MarketplaceActivity extends AppCompatActivity implements PosScanDialogFragment.CodeHost {

    private static final long SEARCH_DEBOUNCE_MS = 400L;
    private static final int SEARCH_PAGE_SIZE = 24;
    private static final int RECENT_ORDERS_SHOWN = 4;

    private final ApiClient api = ApiClient.get(this);
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private final Handler searchHandler = new Handler(Looper.getMainLooper());

    private TextView cartBadge;
    private TextView distName, distArea, distAvatar;
    private ImageView distAvatarImage;
    private LinearLayout categoriesContainer, recommendedRow, bestDealsRow, exclusiveDealsRow,
            promotionsContainer, newsContainer, storiesRow, suppliersRow, sellersContainer,
            myDistributorsContainer, recentOrdersContainer, bannerDots, searchGrid;
    private View recommendedSection, bestDealsSection, exclusiveDealsSection, promotionsSection,
            newsSection, storiesSection, suppliersSection, sellersSection, myDistributorsSection,
            recentOrdersSection, categoriesSection, searchSection, searchProgress, searchClear;
    private TextView searchEmpty;
    private EditText searchInput;
    private ViewPager2 bannerPager;
    private View containerBannerPager;
    private NestedScrollView scroll;
    private SwipeRefreshLayout refresh;

    private MarketplaceData data;
    private List<MarketplaceData.Distributor> myDistributors = new ArrayList<>();
    private String selectedDistributorId;
    private String currency = "R";

    /**
     * The campaign the trader last opened. Like the portal, later product views on this
     * screen are attributed to it, so a "Shop Products" → browse → view still counts.
     */
    private long lastCampaignId;

    private Runnable bannerAdvance;
    private Runnable pendingSearch;
    /** Guards against a slow, older search response overwriting a newer one. */
    private int searchSeq;
    /** Set while a scanned code is being looked up — a single hit opens it directly. */
    private String pendingScanCode;
    private boolean settingSearchText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_marketplace);

        cartBadge = findViewById(R.id.cartBadge);
        distName = findViewById(R.id.distName);
        distArea = findViewById(R.id.distArea);
        distAvatar = findViewById(R.id.distAvatar);
        distAvatarImage = findViewById(R.id.distAvatarImage);
        categoriesContainer = findViewById(R.id.categoriesContainer);
        recommendedRow = findViewById(R.id.recommendedRow);
        bestDealsRow = findViewById(R.id.bestDealsRow);
        exclusiveDealsRow = findViewById(R.id.exclusiveDealsRow);
        promotionsContainer = findViewById(R.id.promotionsContainer);
        newsContainer = findViewById(R.id.newsContainer);
        storiesRow = findViewById(R.id.storiesRow);
        suppliersRow = findViewById(R.id.suppliersRow);
        sellersContainer = findViewById(R.id.sellersContainer);
        myDistributorsContainer = findViewById(R.id.myDistributorsContainer);
        recentOrdersContainer = findViewById(R.id.recentOrdersContainer);
        bannerDots = findViewById(R.id.bannerDots);
        bannerPager = findViewById(R.id.bannerPager);
        containerBannerPager = findViewById(R.id.containerBannerPager);
        searchGrid = findViewById(R.id.searchGrid);
        searchInput = findViewById(R.id.searchInput);
        searchEmpty = findViewById(R.id.searchEmpty);
        searchProgress = findViewById(R.id.searchProgress);
        searchClear = findViewById(R.id.searchClear);

        scroll = findViewById(R.id.marketplaceScroll);
        refresh = findViewById(R.id.marketplaceRefresh);
        categoriesSection = findViewById(R.id.categoriesSection);
        searchSection = findViewById(R.id.searchSection);
        recommendedSection = findViewById(R.id.recommendedSection);
        bestDealsSection = findViewById(R.id.bestDealsSection);
        exclusiveDealsSection = findViewById(R.id.exclusiveDealsSection);
        promotionsSection = findViewById(R.id.promotionsSection);
        newsSection = findViewById(R.id.newsSection);
        storiesSection = findViewById(R.id.storiesSection);
        suppliersSection = findViewById(R.id.suppliersSection);
        sellersSection = findViewById(R.id.sellersSection);
        myDistributorsSection = findViewById(R.id.myDistributorsSection);
        recentOrdersSection = findViewById(R.id.recentOrdersSection);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.cartButton).setOnClickListener(v ->
                startActivity(new Intent(this, CartActivity.class)));
        findViewById(R.id.btnChangeDistributor).setOnClickListener(v -> showDistributorPicker());
        findViewById(R.id.distributorCard).setOnClickListener(v -> {
            if (data != null) openDistributorDetails(data.selected_distributor);
        });
        findViewById(R.id.scanButton).setOnClickListener(v -> openScanner());

        refresh.setColorSchemeResources(R.color.brand_blue);
        refresh.setOnRefreshListener(() -> {
            loadMarketplace(selectedDistributorId);
            refreshCart();
        });

        bindHeaders();
        bindServices();
        setUpSearch();
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
        searchHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---- Static wiring ---------------------------------------------------

    private void bindHeaders() {
        header(R.id.hdrCategories, getString(R.string.categories), null,
                () -> startActivity(new Intent(this, AllCategoriesActivity.class)));
        header(R.id.hdrRecommended, getString(R.string.recommended_for_you),
                getString(R.string.based_on_orders),
                () -> openCollection("recommended", getString(R.string.recommended_for_you)));
        header(R.id.hdrBestDeals, getString(R.string.market_best_deals), null,
                () -> openCollection("best_deals", getString(R.string.market_best_deals)));
        header(R.id.hdrExclusiveDeals, getString(R.string.market_exclusive_deals), null,
                () -> openCollection("exclusive_deals", getString(R.string.market_exclusive_deals)));
        header(R.id.hdrPromotions, getString(R.string.market_promotions), null,
                () -> startActivity(new Intent(this, PromotionsActivity.class)));
        header(R.id.hdrNews, getString(R.string.market_news), null, null);
        header(R.id.hdrStories, getString(R.string.market_stories), null, null);
        header(R.id.hdrSuppliers, getString(R.string.market_top_suppliers), null, null);
        header(R.id.hdrSellers, getString(R.string.market_best_sellers), null,
                () -> startActivity(new Intent(this, DistributorsActivity.class)));
        header(R.id.hdrMyDistributors, getString(R.string.market_my_distributors), null,
                () -> startActivity(new Intent(this, DistributorsActivity.class)));
        header(R.id.hdrRecentOrders, getString(R.string.market_recent_orders), null,
                () -> openMain("orders"));
        View searchHeader = header(R.id.hdrSearch, "", null, this::clearSearch);
        ((TextView) searchHeader.findViewById(R.id.sectionAction)).setText(R.string.market_search_clear);
    }

    /** Fills an {@code include_market_section_header}; a null action hides "View All". */
    private View header(int includeId, String title, @Nullable String subtitle,
                        @Nullable Runnable action) {
        View root = findViewById(includeId);
        ((TextView) root.findViewById(R.id.sectionTitle)).setText(title);
        TextView sub = root.findViewById(R.id.sectionSubtitle);
        sub.setVisibility(subtitle == null ? View.GONE : View.VISIBLE);
        if (subtitle != null) sub.setText(subtitle);
        TextView act = root.findViewById(R.id.sectionAction);
        act.setVisibility(action == null ? View.GONE : View.VISIBLE);
        if (action != null) act.setOnClickListener(v -> action.run());
        return root;
    }

    private void setHeaderTitle(int includeId, String title) {
        ((TextView) findViewById(includeId).findViewById(R.id.sectionTitle)).setText(title);
    }

    /** Airtime & Data / Electricity / POS & Devices — same targets as the portal tiles. */
    private void bindServices() {
        bindService(R.id.svcAirtime, R.drawable.ic_phone, R.color.brand_indigo,
                R.string.market_service_airtime, R.string.market_service_airtime_sub,
                () -> startActivity(new Intent(this, AirtimeActivity.class)));
        bindService(R.id.svcElectricity, R.drawable.ic_bolt, R.color.warning_strong,
                R.string.market_service_electricity, R.string.market_service_electricity_sub,
                () -> startActivity(new Intent(this, AirtimeActivity.class)));
        bindService(R.id.svcPos, R.drawable.ic_credit_card, R.color.brand_blue,
                R.string.market_service_pos, R.string.market_service_pos_sub,
                () -> openMain("sell"));
    }

    private void bindService(int includeId, int icon, int colorRes, int title, int sub, Runnable onTap) {
        View root = findViewById(includeId);
        int color = ContextCompat.getColor(this, colorRes);
        ImageView iv = root.findViewById(R.id.svcIcon);
        iv.setImageResource(icon);
        iv.setColorFilter(color);
        // Pale wash of the icon colour behind it (portal service tiles).
        root.findViewById(R.id.svcIconWrap).setBackgroundTintList(
                android.content.res.ColorStateList.valueOf((color & 0x00FFFFFF) | 0x1F000000));
        ((TextView) root.findViewById(R.id.svcTitle)).setText(title);
        ((TextView) root.findViewById(R.id.svcSub)).setText(sub);
        root.setOnClickListener(v -> onTap.run());
    }

    // ---- Data loading ----------------------------------------------------

    private void loadMarketplace(String distributorId) {
        api.getMarketplace(distributorId, new ApiCallback<MarketplaceData>() {
            @Override
            public void onSuccess(MarketplaceData result) {
                if (isFinishing() || isDestroyed()) return;
                refresh.setRefreshing(false);
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
                String distLabel = result.selected_distributor != null
                        ? result.selected_distributor.displayName() : "";

                bindDistributor(result.selected_distributor);
                buildBanners(result.banners);
                buildProductRow(recommendedRow, recommendedSection, result.recommended);
                buildDeals(bestDealsRow, bestDealsSection, result.best_deal_products, null);
                buildDeals(exclusiveDealsRow, exclusiveDealsSection,
                        result.exclusive_deal_products, distLabel);
                buildPromotions(result.promotions);
                buildNews(result.posts, distLabel);
                buildStories(result.stories);
                buildSuppliers(result.top_suppliers);
                buildSellers(result.top_sellers);
                buildMyDistributors(result.my_distributors);
                buildRecentOrders(result.recent_orders);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                refresh.setRefreshing(false);
                toast(message);
            }
        });
    }

    private void loadCategories() {
        api.getReference(new ApiCallback<ReferenceData>() {
            @Override
            public void onSuccess(ReferenceData result) {
                if (isFinishing() || isDestroyed()) return;
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
        distAvatar.setText(initial(d.displayName()));
        LogoLoader.load(distAvatarImage, d.logo_url);
        distArea.setText(d.address != null && !d.address.trim().isEmpty() ? d.address : "—");
    }

    /**
     * Opens the existing Distributor Details screen. The marketplace distributor object is
     * the same JSON shape as an api/trader/distributors row, so it converts directly.
     */
    private void openDistributorDetails(@Nullable MarketplaceData.Distributor d) {
        if (d == null) return;
        Gson gson = new Gson();
        DistributorListData.Distributor detail =
                gson.fromJson(gson.toJson(d), DistributorListData.Distributor.class);
        Intent i = new Intent(this, DistributorDetailActivity.class);
        i.putExtra(DistributorDetailActivity.EXTRA_DISTRIBUTOR, detail);
        startActivity(i);
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
            ((TextView) row.findViewById(R.id.optionAvatar)).setText(initial(d.displayName()));
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
                        if (searchSection.getVisibility() == View.VISIBLE) {
                            runSearch(searchInput.getText().toString());
                        }
                    }
                })
                .show();
    }

    private View wrapScroll(View child) {
        NestedScrollView sv = new NestedScrollView(this);
        sv.addView(child);
        return sv;
    }

    // ---- Campaigns -------------------------------------------------------

    /** Records the open and shows the campaign sheet (banner / promotion / news post). */
    private void openCampaign(@Nullable MarketplaceData.Promotion campaign) {
        if (campaign == null) return;
        api.recordCampaignClick(campaign.id);
        lastCampaignId = campaign.id;
        CampaignSheet.show(this, campaign, currency, this::openCampaignProduct);
    }

    private void openCampaignProduct(@NonNull MarketplaceData.CampaignProduct p, long campaignId) {
        api.recordCampaignProductView(campaignId, p.product_id);
        startActivity(CampaignSheet.productIntent(this, p, currency, selectedDistributorName()));
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
        if (count < 2) return;
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
            MarketplaceData.Promotion campaign = b.asCampaign();
            h.title.setText(b.name != null ? b.name : "");
            boolean hasDescription = b.description != null && !b.description.trim().isEmpty();
            h.description.setText(hasDescription ? b.description : "");
            h.description.setVisibility(hasDescription ? View.VISIBLE : View.GONE);
            String tag = campaign.typeLabel();
            h.tag.setText(tag);
            h.tag.setVisibility(tag.isEmpty() ? View.GONE : View.VISIBLE);

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
            // One CTA, worded like the portal: announcements have nothing to shop.
            h.shop.setText(campaign.isAnnouncement()
                    ? R.string.market_view_details : R.string.shop_products);
            h.shop.setOnClickListener(v -> openCampaign(campaign));
            h.promos.setVisibility(View.GONE);
            h.itemView.setOnClickListener(v -> openCampaign(campaign));
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
            final TextView title, description, tag, shop;
            final ImageView image;
            final LoopingBannerVideoView video;
            final View promos;

            VH(@NonNull View v) {
                super(v);
                title = v.findViewById(R.id.bannerTitle);
                description = v.findViewById(R.id.bannerDescription);
                tag = v.findViewById(R.id.bannerTag);
                image = v.findViewById(R.id.bannerImage);
                video = v.findViewById(R.id.bannerVideo);
                shop = v.findViewById(R.id.bannerShop);
                promos = v.findViewById(R.id.bannerPromos);
            }
        }
    }

    // ---- Search + scan ---------------------------------------------------

    private void setUpSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (settingSearchText) return;
                pendingScanCode = null;
                scheduleSearch(s.toString());
            }
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            searchHandler.removeCallbacksAndMessages(null);
            runSearch(searchInput.getText().toString());
            hideKeyboard();
            return true;
        });
        searchClear.setOnClickListener(v -> clearSearch());
    }

    private void scheduleSearch(String query) {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        pendingSearch = () -> runSearch(query);
        searchHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
    }

    private void runSearch(String raw) {
        String query = raw == null ? "" : raw.trim();
        int seq = ++searchSeq;
        if (query.isEmpty()) {
            showSearchMode(false);
            return;
        }
        showSearchMode(true);
        setHeaderTitle(R.id.hdrSearch, getString(R.string.market_searching));
        searchProgress.setVisibility(View.VISIBLE);
        searchEmpty.setVisibility(View.GONE);
        searchGrid.removeAllViews();

        api.searchMarketplace(query, selectedDistributorId, 1, SEARCH_PAGE_SIZE,
                new ApiCallback<CollectionData>() {
                    @Override
                    public void onSuccess(CollectionData result) {
                        if (seq != searchSeq || isFinishing() || isDestroyed()) return;
                        List<MarketplaceData.Product> products =
                                result != null && result.products != null
                                        ? result.products : new ArrayList<>();
                        int total = result != null && result.pagination != null
                                ? Math.max(result.pagination.total, products.size())
                                : products.size();
                        showSearchResults(query, products, total);
                    }

                    @Override
                    public void onError(String message) {
                        if (seq != searchSeq || isFinishing() || isDestroyed()) return;
                        pendingScanCode = null;
                        searchProgress.setVisibility(View.GONE);
                        setHeaderTitle(R.id.hdrSearch, getString(R.string.search_hint));
                        searchEmpty.setVisibility(View.VISIBLE);
                        searchEmpty.setText(message);
                    }
                });
    }

    private void showSearchResults(String query, List<MarketplaceData.Product> products, int total) {
        searchProgress.setVisibility(View.GONE);
        setHeaderTitle(R.id.hdrSearch, getResources().getQuantityString(
                R.plurals.market_search_results, total, total, query));

        String scanned = pendingScanCode;
        pendingScanCode = null;
        if (scanned != null && scanned.equals(query) && products.size() == 1) {
            // A scanned barcode / SKU that resolves to one product opens it straight away.
            openProductDetail(products.get(0), 0);
        }

        if (products.isEmpty()) {
            searchEmpty.setVisibility(View.VISIBLE);
            searchEmpty.setText(getString(scanned != null
                    ? R.string.market_scan_not_found : R.string.market_search_empty, query));
            return;
        }
        searchEmpty.setVisibility(View.GONE);
        // Two-column grid built from rows of the regular product card.
        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout row = null;
        for (int i = 0; i < products.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = dp(10);
                searchGrid.addView(row, rlp);
            }
            View card = bindProductCard(inflater, row, products.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 2 == 0) lp.setMarginEnd(dp(5));
            else lp.setMarginStart(dp(5));
            row.addView(card, lp);
        }
        if (products.size() % 2 == 1 && row != null) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        }
    }

    /** Search results take the place of Categories, as on the portal. */
    private void showSearchMode(boolean searching) {
        searchSection.setVisibility(searching ? View.VISIBLE : View.GONE);
        categoriesSection.setVisibility(searching ? View.GONE : View.VISIBLE);
        if (!searching) {
            searchGrid.removeAllViews();
            searchProgress.setVisibility(View.GONE);
            searchEmpty.setVisibility(View.GONE);
        }
    }

    private void clearSearch() {
        searchHandler.removeCallbacksAndMessages(null);
        pendingScanCode = null;
        settingSearchText = true;
        searchInput.setText("");
        settingSearchText = false;
        searchSeq++;
        showSearchMode(false);
        hideKeyboard();
    }

    private void openScanner() {
        if (getSupportFragmentManager().findFragmentByTag("market_scan") != null) return;
        PosScanDialogFragment.newInstance(PosScanDialogFragment.Mode.BARCODE)
                .show(getSupportFragmentManager(), "market_scan");
    }

    /** {@link PosScanDialogFragment.CodeHost}: look the code up through search. */
    @Override
    public void onCodeScanned(String code) {
        String query = code == null ? "" : code.trim();
        if (query.isEmpty()) return;
        searchHandler.removeCallbacksAndMessages(null);
        settingSearchText = true;
        searchInput.setText(query);
        searchInput.setSelection(query.length());
        settingSearchText = false;
        pendingScanCode = query;
        runSearch(query);
        scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, searchSection.getTop() - dp(80))));
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        searchInput.clearFocus();
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
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(76),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP;
            item.setLayoutParams(lp);

            android.widget.FrameLayout iconWrap = new android.widget.FrameLayout(this);
            iconWrap.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(52)));
            // White circle with a hairline border (portal category bubbles).
            iconWrap.setBackgroundResource(R.drawable.bg_category_circle);

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
                icon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
            }
            iconWrap.addView(icon);
            item.addView(iconWrap);

            RobotoTextView title = new RobotoTextView(this);
            title.setText(c.title != null ? c.title : "");
            title.setGravity(Gravity.CENTER);
            title.setMaxLines(2);
            title.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
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

    // ---- Product rows ----------------------------------------------------

    private void buildProductRow(LinearLayout row, View section, List<MarketplaceData.Product> products) {
        row.removeAllViews();
        if (products == null || products.isEmpty()) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (MarketplaceData.Product p : products) {
            row.addView(bindProductCard(inflater, row, p));
        }
    }

    /** The standard product card (image, name, price, orange "+"). */
    private View bindProductCard(LayoutInflater inflater, ViewGroup parent, MarketplaceData.Product p) {
        View card = inflater.inflate(R.layout.item_market_card, parent, false);
        ImageView img = card.findViewById(R.id.productImage);
        ((TextView) card.findViewById(R.id.productName)).setText(p.name != null ? p.name : "");
        ((TextView) card.findViewById(R.id.productPrice)).setText(p.formattedPrice(currency));
        String url = p.imageUrl();
        if (url != null && GlideGuard.canLoad(img)) {
            Glide.with(img).load(url).centerCrop().into(img);
        }
        card.setOnClickListener(v -> openProductDetail(p, 0));
        card.findViewById(R.id.productAdd).setOnClickListener(v -> openProductDetail(p, 0));
        return card;
    }

    /**
     * Best Deals / Exclusive Deals: deal cards with the green stock pill; exclusive cards
     * also carry "Only from {distributor}". Each product belongs to a campaign, so a tap
     * records the campaign open before the product view.
     */
    private void buildDeals(LinearLayout row, View section, List<MarketplaceData.Product> products,
                            @Nullable String onlyFrom) {
        row.removeAllViews();
        if (products == null || products.isEmpty()) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (MarketplaceData.Product p : products) {
            View card = inflater.inflate(R.layout.item_deal_card, row, false);
            ((TextView) card.findViewById(R.id.dealName)).setText(p.name != null ? p.name : "");
            ((TextView) card.findViewById(R.id.dealPrice)).setText(p.formattedPrice(currency));

            TextView stock = card.findViewById(R.id.dealStock);
            if (p.inStock()) {
                stock.setText(getString(R.string.market_in_stock_count,
                        MarketFormat.count(p.stockCount())));
                stock.setBackgroundResource(R.drawable.bg_stock_pill);
                stock.setTextColor(ContextCompat.getColor(this, R.color.success_strong));
            } else {
                stock.setText(R.string.market_out_of_stock);
                stock.setBackgroundResource(R.drawable.bg_badge_muted);
                stock.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.danger_bg)));
                stock.setTextColor(ContextCompat.getColor(this, R.color.danger));
            }

            TextView tag = card.findViewById(R.id.dealTag);
            if (onlyFrom != null && !onlyFrom.isEmpty()) {
                tag.setText(getString(R.string.market_only_from, onlyFrom));
                tag.setVisibility(View.VISIBLE);
            }

            ImageView img = card.findViewById(R.id.dealImage);
            String url = p.imageUrl();
            if (url != null && GlideGuard.canLoad(img)) {
                Glide.with(img).load(url).centerCrop().into(img);
            }
            card.setOnClickListener(v -> {
                if (p.campaign_id > 0) {
                    api.recordCampaignClick(p.campaign_id);
                    lastCampaignId = p.campaign_id;
                }
                openProductDetail(p, p.campaign_id);
            });
            row.addView(card);
        }
    }

    /**
     * Opens the product page. A view is attributed to {@code campaignId}, or failing that
     * to the campaign the trader last opened on this screen (portal behaviour).
     */
    private void openProductDetail(MarketplaceData.Product p, long campaignId) {
        long attributed = campaignId > 0 ? campaignId : lastCampaignId;
        api.recordCampaignProductView(attributed, p.id);

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
        i.putExtra(ProductDetailActivity.EXTRA_DISTRIBUTOR, selectedDistributorName());
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
            PromotionCardBinder.bind(this, card, p, () -> openCampaign(p));
            card.setOnClickListener(v -> openCampaign(p));
            promotionsContainer.addView(card);
        }
    }

    // ---- News / Stories --------------------------------------------------

    private void buildNews(List<MarketplaceData.Promotion> posts, String distributor) {
        newsContainer.removeAllViews();
        if (posts == null || posts.isEmpty()) {
            newsSection.setVisibility(View.GONE);
            return;
        }
        newsSection.setVisibility(View.VISIBLE);
        setHeaderTitle(R.id.hdrNews, distributor == null || distributor.isEmpty()
                ? getString(R.string.market_news) : getString(R.string.market_news_from, distributor));
        LayoutInflater inflater = LayoutInflater.from(this);
        for (MarketplaceData.Promotion post : posts) {
            if (newsContainer.getChildCount() > 0) addDivider(newsContainer);
            View row = inflater.inflate(R.layout.item_market_news, newsContainer, false);
            ((TextView) row.findViewById(R.id.newsTitle)).setText(post.name != null ? post.name : "");
            TextView desc = row.findViewById(R.id.newsDesc);
            boolean hasDesc = post.description != null && !post.description.trim().isEmpty();
            desc.setText(hasDesc ? post.description : "");
            desc.setVisibility(hasDesc ? View.VISIBLE : View.GONE);
            TextView date = row.findViewById(R.id.newsDate);
            boolean hasDate = post.end_date != null && !post.end_date.trim().isEmpty();
            date.setText(hasDate ? post.end_date : "");
            date.setVisibility(hasDate ? View.VISIBLE : View.GONE);
            row.setOnClickListener(v -> openCampaign(post));
            newsContainer.addView(row);
        }
    }

    private void buildStories(List<MarketplaceData.Promotion> stories) {
        storiesRow.removeAllViews();
        if (stories == null || stories.isEmpty()) {
            storiesSection.setVisibility(View.GONE);
            return;
        }
        storiesSection.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < stories.size(); i++) {
            MarketplaceData.Promotion story = stories.get(i);
            View item = inflater.inflate(R.layout.item_market_story, storiesRow, false);
            ((TextView) item.findViewById(R.id.storyLabel)).setText(story.name != null ? story.name : "");
            ImageView img = item.findViewById(R.id.storyImage);
            String url = story.mediaUrl();
            if (url != null && !story.isVideo() && GlideGuard.canLoad(img)) {
                Glide.with(img).load(url).centerCrop().into(img);
            }
            final int index = i;
            item.setOnClickListener(v -> StoryViewer.show(this, stories, index, shown -> {
                api.recordCampaignClick(shown.id);
                lastCampaignId = shown.id;
            }));
            storiesRow.addView(item);
        }
    }

    // ---- Suppliers / sellers / distributors / orders --------------------

    private void buildSuppliers(List<MarketplaceData.Supplier> suppliers) {
        suppliersRow.removeAllViews();
        if (suppliers == null || suppliers.isEmpty()) {
            suppliersSection.setVisibility(View.GONE);
            return;
        }
        suppliersSection.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (MarketplaceData.Supplier s : suppliers) {
            View chip = inflater.inflate(R.layout.item_market_supplier, suppliersRow, false);
            ((TextView) chip.findViewById(R.id.supplierName)).setText(s.name != null ? s.name : "");
            ((TextView) chip.findViewById(R.id.supplierCount))
                    .setText(getString(R.string.market_products_count, s.product_count));
            suppliersRow.addView(chip);
        }
    }

    private void buildSellers(List<MarketplaceData.Seller> sellers) {
        sellersContainer.removeAllViews();
        if (sellers == null || sellers.isEmpty()) {
            sellersSection.setVisibility(View.GONE);
            return;
        }
        sellersSection.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (MarketplaceData.Seller s : sellers) {
            if (sellersContainer.getChildCount() > 0) addDivider(sellersContainer);
            View row = inflater.inflate(R.layout.item_market_seller, sellersContainer, false);
            String name = s.name != null ? s.name : "";
            ((TextView) row.findViewById(R.id.sellerAvatar)).setText(initial(name));
            LogoLoader.load(row.findViewById(R.id.sellerAvatarImage), s.logo_url);
            ((TextView) row.findViewById(R.id.sellerName)).setText(name);
            row.findViewById(R.id.sellerYours).setVisibility(s.is_mine ? View.VISIBLE : View.GONE);
            ((TextView) row.findViewById(R.id.sellerCount))
                    .setText(getString(R.string.market_products_count, s.products_available));
            sellersContainer.addView(row);
        }
    }

    private void buildMyDistributors(List<MarketplaceData.Distributor> distributors) {
        myDistributorsContainer.removeAllViews();
        if (distributors == null || distributors.isEmpty()) {
            myDistributorsSection.setVisibility(View.GONE);
            return;
        }
        myDistributorsSection.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (MarketplaceData.Distributor d : distributors) {
            View card = inflater.inflate(R.layout.item_market_my_distributor, myDistributorsContainer, false);
            ((TextView) card.findViewById(R.id.mdAvatar)).setText(initial(d.displayName()));
            LogoLoader.load(card.findViewById(R.id.mdAvatarImage), d.logo_url);
            ((TextView) card.findViewById(R.id.mdName)).setText(d.displayName());
            ((TextView) card.findViewById(R.id.mdProducts)).setText(d.products_available + "+");
            ((TextView) card.findViewById(R.id.mdCoverage)).setText(
                    d.address != null && !d.address.trim().isEmpty() ? d.address : "—");
            card.findViewById(R.id.mdView).setOnClickListener(v -> openDistributorDetails(d));
            myDistributorsContainer.addView(card);
        }
    }

    private void buildRecentOrders(List<MarketplaceData.RecentOrder> orders) {
        recentOrdersContainer.removeAllViews();
        if (orders == null || orders.isEmpty()) {
            recentOrdersSection.setVisibility(View.GONE);
            return;
        }
        recentOrdersSection.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        int shown = Math.min(RECENT_ORDERS_SHOWN, orders.size());
        for (int i = 0; i < shown; i++) {
            MarketplaceData.RecentOrder o = orders.get(i);
            if (recentOrdersContainer.getChildCount() > 0) addDivider(recentOrdersContainer);
            View row = inflater.inflate(R.layout.item_market_recent_order, recentOrdersContainer, false);
            ((TextView) row.findViewById(R.id.roNumber)).setText(
                    "#" + (o.order_number != null ? o.order_number : String.valueOf(o.id)));
            ((TextView) row.findViewById(R.id.roDate)).setText(o.created_at != null ? o.created_at : "");
            bindStatusPill(row.findViewById(R.id.roStatus), o.status_key, o.status);
            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, OrderDetailActivity.class);
                intent.putExtra(OrderDetailActivity.EXTRA_ID, String.valueOf(o.id));
                startActivity(intent);
            });
            recentOrdersContainer.addView(row);
        }
    }

    private void bindStatusPill(TextView pill, String key, String label) {
        String k = key == null ? "" : key.toLowerCase(Locale.US);
        pill.setText(label != null && !label.isEmpty() ? label : k);
        int bg, fg;
        if (k.contains("deliver") || k.contains("complete") || k.contains("receiv")) {
            bg = R.drawable.bg_badge_success;
            fg = R.color.success_strong;
        } else if (k.contains("cancel") || k.contains("reject") || k.contains("fail")) {
            bg = R.drawable.bg_badge_muted;
            fg = R.color.danger;
            pill.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.danger_bg)));
        } else if (k.contains("pending") || k.contains("await")) {
            bg = R.drawable.bg_badge_warning;
            fg = R.color.warning;
        } else {
            bg = R.drawable.bg_badge_info;
            fg = R.color.info;
        }
        pill.setBackgroundResource(bg);
        pill.setTextColor(ContextCompat.getColor(this, fg));
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider));
        parent.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private void openMain(String tab) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_OPEN_TAB, tab);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
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
                if (isFinishing() || isDestroyed()) return;
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

    private String selectedDistributorName() {
        return data != null && data.selected_distributor != null
                ? data.selected_distributor.displayName() : "";
    }

    private static String initial(String name) {
        String n = name == null ? "" : name.trim();
        return n.isEmpty() ? "D" : n.substring(0, 1).toUpperCase(Locale.US);
    }

    private void toast(String msg) {
        if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
