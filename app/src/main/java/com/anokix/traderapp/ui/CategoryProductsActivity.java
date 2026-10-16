package com.anokix.traderapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.MarketCart;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.CartData;
import com.anokix.traderapp.network.dto.CategoryPageData;
import com.anokix.traderapp.network.dto.MarketplaceData;
import com.anokix.traderapp.ui.views.LoopingBannerVideoView;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.slider.RangeSlider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Marketplace › category detail. Browses and filters the products of one
 * category via GET /api/trader/marketplace/category, with a Sort-by menu and a
 * Filters bottom sheet (Brands, Categories, Pack Size, Price Range, Promotions,
 * Availability) driven by the facet counts returned with each response.
 */
public class CategoryProductsActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORY_ID = "category_id";
    public static final String EXTRA_CATEGORY_TITLE = "category_title";

    private static final int PER_PAGE = 60;

    // Sort options → sort_by param.
    private static final String[] SORT_KEYS =
            {"popularity", "price_asc", "price_desc", "newest", "name_asc"};
    private static final int[] SORT_LABELS = {R.string.sort_popularity, R.string.sort_price_low,
            R.string.sort_price_high, R.string.sort_newest, R.string.sort_name_az};

    private final ApiClient api = ApiClient.get(this);

    private String categoryId, categoryTitle;
    private String currency = "R";

    // Committed filter state (drives the query).
    private final Set<Long> selBrands = new HashSet<>();
    private final Set<Long> selSubCategories = new HashSet<>();
    private final Set<String> selPackSizes = new HashSet<>();
    private boolean onPromotion, inStock;
    private Double minPrice, maxPrice;
    private int sortIndex = 0;

    // Latest facets, used to build the sheet.
    private CategoryPageData.Filters facets;

    private TextView cartBadge, categoryTitleView, productCount, sortLabel, filterCount,
            emptyView, recentEmpty;
    private ImageView filterIcon;
    private ProgressBar progress;
    private RecyclerView grid;
    private View recentSection;
    private LinearLayout recentRow;
    private ProductAdapter adapter;
    private final List<MarketplaceData.Product> products = new ArrayList<>();

    // Banner slider (mirrors the Marketplace screen).
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private ViewPager2 bannerPager;
    private View containerBannerPager;
    private LinearLayout bannerDots;
    private Runnable bannerAdvance;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_products);

        categoryId = getIntent().getStringExtra(EXTRA_CATEGORY_ID);
        categoryTitle = getIntent().getStringExtra(EXTRA_CATEGORY_TITLE);

        cartBadge = findViewById(R.id.cartBadge);
        categoryTitleView = findViewById(R.id.categoryTitle);
        productCount = findViewById(R.id.productCount);
        sortLabel = findViewById(R.id.sortLabel);
        filterCount = findViewById(R.id.filterCount);
        emptyView = findViewById(R.id.emptyView);
        recentEmpty = findViewById(R.id.recentEmpty);
        recentSection = findViewById(R.id.recentSection);
        recentRow = findViewById(R.id.recentRow);
        progress = findViewById(R.id.progress);
        grid = findViewById(R.id.productsGrid);
        bannerPager = findViewById(R.id.bannerPager);
        containerBannerPager = findViewById(R.id.containerBannerPager);
        bannerDots = findViewById(R.id.bannerDots);
        containerBannerPager.setVisibility(View.GONE);

        if (categoryTitle != null) {
            categoryTitleView.setText(categoryTitle);
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.cartButton).setOnClickListener(v ->
                startActivity(new Intent(this, CartActivity.class)));
        findViewById(R.id.btnSort).setOnClickListener(this::showSortMenu);
        findViewById(R.id.btnFilters).setOnClickListener(v -> showFilterSheet());

        adapter = new ProductAdapter();
        grid.setLayoutManager(new GridLayoutManager(this, 2));
        grid.setAdapter(adapter);

        sortLabel.setText(SORT_LABELS[sortIndex]);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBannerAutoScroll();
    }

    // ---- Data ------------------------------------------------------------

    private void load() {
        progress.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        api.getCategoryPage(categoryId, null, csv(selBrands), csv(selSubCategories),
                joinStr(selPackSizes), fmtPrice(minPrice), fmtPrice(maxPrice), onPromotion,
                inStock, null, SORT_KEYS[sortIndex], 1, PER_PAGE,
                new ApiCallback<CategoryPageData>() {
                    @Override
                    public void onSuccess(CategoryPageData result) {
                        progress.setVisibility(View.GONE);
                        if (result == null) return;
                        currency = result.currencySymbol();
                        facets = result.filters;
                        if (categoryTitle == null && result.category != null) {
                            categoryTitle = result.category.title;
                            categoryTitleView.setText(categoryTitle);
                        }
                        int total = result.pagination != null ? result.pagination.total
                                : (result.products != null ? result.products.size() : 0);
                        productCount.setText(getString(R.string.products_found, total));

                        products.clear();
                        if (result.products != null) products.addAll(result.products);
                        adapter.notifyDataSetChanged();
                        emptyView.setVisibility(products.isEmpty() ? View.VISIBLE : View.GONE);

                        bindRecentlyPurchased(result.recently_purchased);
                        updateFilterBadge();
                        buildBanners(result.banners);
                    }

                    @Override
                    public void onError(String message) {
                        progress.setVisibility(View.GONE);
                        toast(message);
                    }
                });
    }

    private void bindRecentlyPurchased(List<MarketplaceData.Product> recent) {
        recentSection.setVisibility(View.VISIBLE);
        recentRow.removeAllViews();
        if (recent == null || recent.isEmpty()) {
            recentEmpty.setVisibility(View.VISIBLE);
            return;
        }
        recentEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (MarketplaceData.Product p : recent) {
            View card = inflater.inflate(R.layout.item_market_card, recentRow, false);
            ImageView img = card.findViewById(R.id.productImage);
            ((TextView) card.findViewById(R.id.productName)).setText(p.name != null ? p.name : "");
            ((TextView) card.findViewById(R.id.productPrice)).setText(p.formattedPrice(currency));
            String url = p.imageUrl();
            if (url != null && GlideGuard.canLoad(img)) Glide.with(img).load(url).centerCrop().into(img);
            card.setOnClickListener(v -> openProductDetail(p));
            card.findViewById(R.id.productAdd).setOnClickListener(v -> openProductDetail(p));
            recentRow.addView(card);
        }
    }

    // ---- Sort ------------------------------------------------------------

    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        for (int i = 0; i < SORT_LABELS.length; i++) {
            menu.getMenu().add(0, i, i, getString(SORT_LABELS[i]));
        }
        menu.setOnMenuItemClickListener(item -> {
            sortIndex = item.getItemId();
            sortLabel.setText(SORT_LABELS[sortIndex]);
            load();
            return true;
        });
        menu.show();
    }

    // ---- Filters bottom sheet -------------------------------------------

    private void showFilterSheet() {
        if (facets == null) {
            toast(getString(R.string.loading));
            return;
        }
        View sheet = getLayoutInflater().inflate(R.layout.sheet_category_filters, null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheet);
        // Cap the sheet at 88% of the screen and expand it so the pinned footer
        // (Reset / Apply) stays on-screen while the sections scroll inside.
        dialog.setOnShowListener(d -> {
            View parent = (View) sheet.getParent();
            int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.88f);
            parent.getLayoutParams().height = maxH;
            parent.requestLayout();
            com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(parent);
            behavior.setSkipCollapsed(true);
            behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        });

        // Working copies — committed only on Apply.
        final Set<Long> wBrands = new HashSet<>(selBrands);
        final Set<Long> wSubs = new HashSet<>(selSubCategories);
        final Set<String> wPacks = new HashSet<>(selPackSizes);
        final boolean[] wPromo = {onPromotion};
        final boolean[] wStock = {inStock};

        LinearLayout brandsBox = sheet.findViewById(R.id.brandsContainer);
        LinearLayout catsBox = sheet.findViewById(R.id.categoriesContainer);
        LinearLayout packBox = sheet.findViewById(R.id.packSizeContainer);
        LinearLayout promoBox = sheet.findViewById(R.id.promotionsContainer);
        LinearLayout availBox = sheet.findViewById(R.id.availabilityContainer);

        // Brands: "All Brands" clears; each brand toggles.
        Runnable renderBrands = new Runnable() {
            @Override
            public void run() {
                brandsBox.removeAllViews();
                addOption(brandsBox, getString(R.string.all_brands), null, wBrands.isEmpty(), () -> {
                    wBrands.clear();
                    this.run();
                });
                if (facets.brands != null) {
                    for (CategoryPageData.Brand b : facets.brands) {
                        addOption(brandsBox, b.name, b.count, wBrands.contains(b.id), () -> {
                            toggle(wBrands, b.id);
                            this.run();
                        });
                    }
                }
            }
        };
        renderBrands.run();

        // Categories (sub-categories).
        Runnable renderCats = new Runnable() {
            @Override
            public void run() {
                catsBox.removeAllViews();
                addOption(catsBox, getString(R.string.all), null, wSubs.isEmpty(), () -> {
                    wSubs.clear();
                    this.run();
                });
                if (facets.categories != null) {
                    for (CategoryPageData.SubCategory c : facets.categories) {
                        addOption(catsBox, c.title, c.count, wSubs.contains(c.id), () -> {
                            toggle(wSubs, c.id);
                            this.run();
                        });
                    }
                }
            }
        };
        renderCats.run();

        // Pack sizes.
        Runnable renderPacks = new Runnable() {
            @Override
            public void run() {
                packBox.removeAllViews();
                addOption(packBox, getString(R.string.all), null, wPacks.isEmpty(), () -> {
                    wPacks.clear();
                    this.run();
                });
                if (facets.pack_sizes != null) {
                    for (CategoryPageData.PackSize ps : facets.pack_sizes) {
                        addOption(packBox, ps.label, ps.count, wPacks.contains(ps.key), () -> {
                            toggleStr(wPacks, ps.key);
                            this.run();
                        });
                    }
                }
            }
        };
        renderPacks.run();

        // Promotions — only the on_promotion facet is wired to a backend param.
        Runnable renderPromos = new Runnable() {
            @Override
            public void run() {
                promoBox.removeAllViews();
                if (facets.promotions != null) {
                    for (CategoryPageData.Promotion pr : facets.promotions) {
                        boolean supported = "on_promotion".equals(pr.key);
                        boolean checked = supported && wPromo[0];
                        View row = addOption(promoBox, pr.label, pr.count, checked, supported ? () -> {
                            wPromo[0] = !wPromo[0];
                            this.run();
                        } : null);
                        if (!supported) row.setAlpha(0.45f);
                    }
                }
            }
        };
        renderPromos.run();

        // Availability.
        Runnable renderAvail = new Runnable() {
            @Override
            public void run() {
                availBox.removeAllViews();
                int inStockCount = facets.availability != null ? facets.availability.in_stock_count : 0;
                addOption(availBox, getString(R.string.in_stock_only), inStockCount, wStock[0], () -> {
                    wStock[0] = !wStock[0];
                    this.run();
                });
            }
        };
        renderAvail.run();

        // Price range slider.
        RangeSlider slider = sheet.findViewById(R.id.priceSlider);
        TextView priceMinLabel = sheet.findViewById(R.id.priceMin);
        TextView priceMaxLabel = sheet.findViewById(R.id.priceMax);
        // Slider bounds are data-driven from the category API's filters.price_range
        // (per-category). The max thumb at the ceiling means "and above", so no max_price
        // is sent (and the label shows "+"); the min thumb at the floor omits min_price.
        float rangeMin = facets.price_range != null ? (float) facets.price_range.min : 0f;
        float rangeMax = facets.price_range != null ? (float) facets.price_range.max : 1000f;
        if (rangeMax <= rangeMin) rangeMax = rangeMin + 1f;
        final float fRangeMin = rangeMin, fRangeMax = rangeMax;
        slider.setValueFrom(rangeMin);
        slider.setValueTo(rangeMax);
        slider.setStepSize(1f);
        float selLo = minPrice != null ? clamp((float) (double) minPrice, rangeMin, rangeMax) : rangeMin;
        float selHi = maxPrice != null ? clamp((float) (double) maxPrice, rangeMin, rangeMax) : rangeMax;
        if (selHi <= selLo) selHi = rangeMax;
        try {
            slider.setValues(selLo, selHi);
        } catch (Exception e) {
            slider.setValues(rangeMin, rangeMax);
        }
        priceMinLabel.setText(money(slider.getValues().get(0)));
        priceMaxLabel.setText(moneyMax(slider.getValues().get(1), fRangeMax));
        slider.addOnChangeListener((s, value, fromUser) -> {
            List<Float> vals = s.getValues();
            priceMinLabel.setText(money(vals.get(0)));
            priceMaxLabel.setText(moneyMax(vals.get(1), fRangeMax));
        });

        Runnable clearAll = () -> {
            wBrands.clear();
            wSubs.clear();
            wPacks.clear();
            wPromo[0] = false;
            wStock[0] = false;
            slider.setValues(fRangeMin, fRangeMax);
            priceMinLabel.setText(money(fRangeMin));
            priceMaxLabel.setText(moneyMax(fRangeMax, fRangeMax));
            renderBrands.run();
            renderCats.run();
            renderPacks.run();
            renderPromos.run();
            renderAvail.run();
        };
        sheet.findViewById(R.id.btnClearAll).setOnClickListener(v -> clearAll.run());
        sheet.findViewById(R.id.btnReset).setOnClickListener(v -> clearAll.run());

        sheet.findViewById(R.id.btnApply).setOnClickListener(v -> {
            selBrands.clear();
            selBrands.addAll(wBrands);
            selSubCategories.clear();
            selSubCategories.addAll(wSubs);
            selPackSizes.clear();
            selPackSizes.addAll(wPacks);
            onPromotion = wPromo[0];
            inStock = wStock[0];
            List<Float> vals = slider.getValues();
            float lo = vals.get(0), hi = vals.get(1);
            minPrice = lo > fRangeMin ? (double) lo : null;
            maxPrice = hi < fRangeMax ? (double) hi : null;
            dialog.dismiss();
            load();
        });

        dialog.show();
    }

    /** Builds a tappable filter row; a null onClick renders it non-interactive. */
    private View addOption(LinearLayout parent, String label, Integer count, boolean checked,
                           @Nullable Runnable onClick) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_filter_option, parent, false);
        ((TextView) row.findViewById(R.id.optionLabel)).setText(label != null ? label : "");
        ImageView check = row.findViewById(R.id.optionCheck);
        check.setImageResource(checked ? R.drawable.ic_check_purple : R.drawable.ic_circle_outline);
        TextView countView = row.findViewById(R.id.optionCount);
        countView.setText(count != null ? String.valueOf(count) : "");
        if (onClick != null) {
            row.setOnClickListener(v -> onClick.run());
        } else {
            row.setClickable(false);
        }
        parent.addView(row);
        return row;
    }

    private void updateFilterBadge() {
        int n = selBrands.size() + selSubCategories.size() + selPackSizes.size()
                + (onPromotion ? 1 : 0) + (inStock ? 1 : 0)
                + ((minPrice != null || maxPrice != null) ? 1 : 0);
        if (n > 0) {
            filterCount.setVisibility(View.VISIBLE);
            filterCount.setText(String.valueOf(n));
        } else {
            filterCount.setVisibility(View.GONE);
        }
    }

    // ---- Product detail --------------------------------------------------

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
        i.putExtra(ProductDetailActivity.EXTRA_CURRENCY, currency);
        startActivity(i);
    }

    // ---- Cart badge ------------------------------------------------------

    private void refreshCart() {
        refreshCartBadge();
        api.getCart(new ApiCallback<CartData>() {
            @Override
            public void onSuccess(CartData result) {
                MarketCart.get().hydrate(result);
                refreshCartBadge();
            }

            @Override
            public void onError(String message) { }
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

    private static void toggle(Set<Long> set, long id) {
        if (!set.remove(id)) set.add(id);
    }

    private static void toggleStr(Set<String> set, String key) {
        if (!set.remove(key)) set.add(key);
    }

    private static String csv(Set<Long> ids) {
        if (ids.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    private static String joinStr(Set<String> keys) {
        if (keys.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (sb.length() > 0) sb.append(',');
            sb.append(k);
        }
        return sb.toString();
    }

    private static String fmtPrice(Double v) {
        if (v == null) return null;
        return String.format(Locale.US, "%.2f", v);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private String money(float v) {
        return currency + String.format(Locale.US, "%,.2f", v);
    }

    /** Max-side price label; a value at the range ceiling reads as open-ended ("R1,000.00+"). */
    private String moneyMax(float v, float ceil) {
        return money(v) + (v >= ceil ? "+" : "");
    }

    private void toast(String msg) {
        if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ---- Banners ---------------------------------------------------------

    private void buildBanners(List<MarketplaceData.Banner> banners) {
        stopBannerAutoScroll();
        if (banners == null || banners.isEmpty()) {
            containerBannerPager.setVisibility(View.GONE);
            return;
        }
        containerBannerPager.setVisibility(View.VISIBLE);

        BannerAdapter bannerAdapter = new BannerAdapter(banners);
        bannerPager.setAdapter(bannerAdapter);
        buildDots(banners.size());
        bannerPager.setOffscreenPageLimit(banners.size());
        bannerPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                bannerAdapter.setActivePosition(position);
            }
        });
        bannerPager.post(bannerAdapter::playActiveBanner);
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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

    // ---- Adapter ---------------------------------------------------------

    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_category_product, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            MarketplaceData.Product p = products.get(position);
            h.name.setText(p.name != null ? p.name : "");
            h.price.setText(p.formattedPrice(currency));
            String url = p.imageUrl();
            if (url != null) {
                if (GlideGuard.canLoad(h.image)) Glide.with(h.image).load(url).centerCrop().into(h.image);
            } else {
                h.image.setImageDrawable(null);
            }
            boolean stocked = p.inStock();
            h.stockDot.setVisibility(stocked ? View.VISIBLE : View.GONE);
            if (stocked) {
                h.stockLine.setText(getString(R.string.stock_line,
                        getString(R.string.in_stock), p.stockCount()));
                h.stockLine.setTextColor(ContextCompat.getColor(CategoryProductsActivity.this,
                        R.color.text_secondary));
            } else {
                h.stockLine.setText(R.string.out_of_stock);
                h.stockLine.setTextColor(ContextCompat.getColor(CategoryProductsActivity.this,
                        R.color.text_secondary));
            }
            h.itemView.setOnClickListener(v -> openProductDetail(p));
            h.add.setOnClickListener(v -> openProductDetail(p));
        }

        @Override
        public int getItemCount() {
            return products.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView name, price, stockLine;
            final ImageView image, add;
            final View stockDot;

            VH(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.productName);
                price = v.findViewById(R.id.productPrice);
                stockLine = v.findViewById(R.id.stockLine);
                image = v.findViewById(R.id.productImage);
                add = v.findViewById(R.id.productAdd);
                stockDot = v.findViewById(R.id.stockDot);
            }
        }
    }
}
