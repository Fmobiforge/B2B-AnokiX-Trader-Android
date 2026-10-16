package com.anokix.traderapp.ui.fragment;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.OrderFormat;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.TraderDashboardData;
import com.anokix.traderapp.session.SessionManager;
import com.anokix.traderapp.ui.CartActivity;
import com.anokix.traderapp.ui.CartBadge;
import com.anokix.traderapp.ui.GlideGuard;
import com.anokix.traderapp.ui.MainActivity;
import com.anokix.traderapp.ui.MarketplaceActivity;
import com.anokix.traderapp.ui.NotificationBadge;
import com.anokix.traderapp.ui.NotificationsActivity;
import com.anokix.traderapp.ui.RewardsActivity;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Trader home screen. Mirrors the trader web-portal dashboard against
 * GET api/trader/dashboard: today's sales, anokiX wallet, anokiX rewards and
 * pending orders, then quick actions, a promotions carousel, recent orders
 * and top selling products.
 */
public class DashboardFragment extends Fragment {

    private String currencySymbol = "R";
    private boolean walletRefreshing;
    private final List<TraderDashboardData.Promotion> promotions = new ArrayList<>();
    private ViewPager2 promoPager;
    private LinearLayout promoIndicator;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).bindDrawerButton(view.findViewById(R.id.hamburgerButton));
        }
        view.findViewById(R.id.cartButton).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CartActivity.class)));
        view.findViewById(R.id.notificationsButton).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationsActivity.class)));

        bindWelcome(view);
        buildQuickActions(view);
        setupStaticActions(view);
        setupPromotionsCarousel(view);
        loadDashboard(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        View view = getView();
        if (view == null) {
            return;
        }
        NotificationBadge.refresh(getContext(), (TextView) view.findViewById(R.id.notificationBadge));
        CartBadge.refresh(getContext(), (TextView) view.findViewById(R.id.cartBadge));
        // Balances and pending counts move while the user is elsewhere in the app.
        loadDashboard(view);
    }

    // ---- Static navigation --------------------------------------------------

    private void setupStaticActions(View view) {
        view.findViewById(R.id.recentOrdersViewAll).setOnClickListener(v -> selectTab(R.id.nav_orders));
        view.findViewById(R.id.pendingViewOrders).setOnClickListener(v -> selectTab(R.id.nav_orders));
        view.findViewById(R.id.walletAddMoney).setOnClickListener(v -> openWallet());
        view.findViewById(R.id.walletSendMoney).setOnClickListener(v -> openWallet());
        view.findViewById(R.id.walletViewTransactions).setOnClickListener(v -> openWallet());
        view.findViewById(R.id.walletRefresh).setOnClickListener(v -> refreshWallet());
        view.findViewById(R.id.rewardsViewRewards).setOnClickListener(v -> openRewards());
        view.findViewById(R.id.rewardsViewHistory).setOnClickListener(v -> openRewards());
        view.findViewById(R.id.bannerViewRewards).setOnClickListener(v -> openRewards());
        view.findViewById(R.id.promotionsViewAll).setOnClickListener(v -> openMarketplace());
    }

    private void openRewards() {
        startActivity(new Intent(requireContext(), RewardsActivity.class));
    }

    /**
     * The wallet has no bottom-nav tab any more (hidden in {@link MainActivity}),
     * so ask the host to swap it in directly instead of selecting a tab.
     */
    private void openWallet() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showWallet();
        }
    }

    private void openMarketplace() {
        startActivity(new Intent(requireContext(), MarketplaceActivity.class));
    }

    private void bindWelcome(View view) {
        TextView welcome = view.findViewById(R.id.welcomeText);
        String firstName = SessionManager.get(requireContext()).getFirstName();
        welcome.setText(firstName.isEmpty()
                ? getString(R.string.welcome_back_generic)
                : getString(R.string.welcome_back_named, firstName));

        TextView initial = view.findViewById(R.id.profileInitial);
        initial.setText(firstName.isEmpty()
                ? "A"
                : firstName.substring(0, 1).toUpperCase(Locale.US));
    }

    // ---- Quick Actions ------------------------------------------------------

    private void buildQuickActions(View view) {
        GridLayout grid = view.findViewById(R.id.quickActionsGrid);
        if (grid == null || grid.getChildCount() > 0) {
            return;
        }
        String[] labels = {
                getString(R.string.sell_pos), getString(R.string.order_stock),
                getString(R.string.add_money), getString(R.string.airtime_vas),
                getString(R.string.reports), getString(R.string.scan_product)
        };
        int[] icons = {
                R.drawable.ic_shopping_bag, R.drawable.ic_package, R.drawable.ic_wallet,
                R.drawable.ic_promo_tag, R.drawable.ic_reports, R.drawable.ic_search
        };
        final int[] navs = {R.id.nav_sell, -1, -4, -2, -3, R.id.nav_sell};

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < labels.length; i++) {
            View item = inflater.inflate(R.layout.item_quick_action, grid, false);
            ((TextView) item.findViewById(R.id.quickLabel)).setText(labels[i]);
            ((ImageView) item.findViewById(R.id.quickIcon)).setImageResource(icons[i]);

            final int target = navs[i];
            item.setOnClickListener(v -> onQuickAction(target));

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(i % 3, 1f);
            lp.rowSpec = GridLayout.spec(i / 3);
            item.setLayoutParams(lp);
            grid.addView(item);
        }
    }

    private void onQuickAction(int target) {
        switch (target) {
            case -1: // Order Stock -> Marketplace
                openMarketplace();
                break;
            case -2: // Airtime & VAS
                startActivity(new Intent(requireContext(), com.anokix.traderapp.ui.AirtimeActivity.class));
                break;
            case -3: // Reports
                startActivity(new Intent(requireContext(), com.anokix.traderapp.ui.ReportsActivity.class));
                break;
            case -4: // Add Money -> anokiX wallet (no tab of its own any more)
                openWallet();
                break;
            default:
                selectTab(target);
                break;
        }
    }

    // ---- Load ----------------------------------------------------------------

    private void loadDashboard(View root) {
        ApiClient.get(requireContext()).getTraderDashboard(new ApiCallback<TraderDashboardData>() {
            @Override
            public void onSuccess(TraderDashboardData data) {
                if (!isAdded() || getView() == null || data == null || data.dashboard == null) {
                    return;
                }
                bindDashboard(getView(), data.dashboard);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                android.widget.Toast.makeText(requireContext(),
                        message == null ? getString(R.string.dashboard_load_failed) : message,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindDashboard(View root, TraderDashboardData.Dashboard d) {
        if (d.meta != null && d.meta.currency != null && d.meta.currency.symbol != null
                && !d.meta.currency.symbol.isEmpty()) {
            currencySymbol = d.meta.currency.symbol;
        }
        bindTodaySales(root, d.today_sales);
        bindWallet(root, d.wallet);
        bindRewards(root, d.rewards);
        bindPendingOrders(root, d.pending_orders);
        bindPromotions(root, d.promotions);
        bindRecentOrders(root, d.recent_orders);
        bindTopProducts(root, d.top_products);
    }

    // ---- Block 1: Today's Sales ---------------------------------------------

    private void bindTodaySales(View root, TraderDashboardData.TodaySales sales) {
        TextView amount = root.findViewById(R.id.salesAmount);
        TextView count = root.findViewById(R.id.salesCount);
        TextView items = root.findViewById(R.id.salesItems);
        TextView trend = root.findViewById(R.id.salesTrend);

        if (sales == null) {
            amount.setText(money(0));
            count.setText(getString(R.string.sales_today_zero));
            items.setText(getString(R.string.sales_items_count, 0));
            trend.setVisibility(View.GONE);
            return;
        }
        amount.setText(money(sales.amount));
        count.setText(getString(R.string.sales_today_count, sales.transactions));
        items.setText(getString(R.string.sales_items_count, sales.items));

        if (sales.delta_percent == null) {
            trend.setVisibility(View.GONE);
        } else {
            boolean up = !"down".equalsIgnoreCase(sales.trend);
            trend.setVisibility(View.VISIBLE);
            trend.setText(String.format(Locale.US, "%s %.1f%%", up ? "↑" : "↓",
                    Math.abs(sales.delta_percent)));
            int color = up ? 0xFF16A34A : 0xFFDC2626;
            trend.setTextColor(color);
            trend.setBackground(roundedRect((color & 0x00FFFFFF) | 0x1A000000, 20));
        }
    }

    // ---- Block 2: anokiX wallet ---------------------------------------------

    private void bindWallet(View root, TraderDashboardData.Wallet wallet) {
        TextView available = root.findViewById(R.id.walletAvailable);
        TextView breakdown = root.findViewById(R.id.walletBreakdown);
        TextView asOf = root.findViewById(R.id.walletAsOf);
        TextView status = root.findViewById(R.id.walletStatus);

        if (wallet == null || wallet.balance == null) {
            available.setText(money(0));
            breakdown.setText(getString(R.string.wallet_breakdown, money(0), money(0)));
            asOf.setVisibility(View.GONE);
        } else {
            available.setText(money(wallet.balance.available));
            breakdown.setText(getString(R.string.wallet_breakdown,
                    money(wallet.balance.current), money(wallet.balance.pending)));
            String time = timeOfDay(wallet.balance.as_of);
            if (time.isEmpty()) {
                asOf.setVisibility(View.GONE);
            } else {
                asOf.setVisibility(View.VISIBLE);
                asOf.setText(getString(R.string.wallet_as_of, time));
            }
        }

        if (wallet == null || wallet.status == null || wallet.status.isEmpty()) {
            status.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.VISIBLE);
            status.setText(OrderFormat.humanize(wallet.status));
            boolean active = "active".equalsIgnoreCase(wallet.status);
            int color = active ? 0xFF16A34A : 0xFFEA580C;
            status.setTextColor(color);
            status.setBackground(roundedRect((color & 0x00FFFFFF) | 0x1A000000, 20));
        }
    }

    /**
     * Re-reads just the wallet block from api/common/wallet (the balance moves
     * independently of the rest of the dashboard). Swaps the refresh icon for a
     * spinner and guards against double taps while the call is in flight.
     */
    private void refreshWallet() {
        View root = getView();
        if (root == null || walletRefreshing) {
            return;
        }
        walletRefreshing = true;
        setWalletRefreshing(root, true);

        ApiClient.get(requireContext()).getWallet(
                new ApiCallback<TraderDashboardData.Wallet>() {
                    @Override
                    public void onSuccess(TraderDashboardData.Wallet wallet) {
                        walletRefreshing = false;
                        View v = getView();
                        if (!isAdded() || v == null) {
                            return;
                        }
                        setWalletRefreshing(v, false);
                        bindWallet(v, wallet);
                        android.widget.Toast.makeText(requireContext(),
                                R.string.wallet_refreshed,
                                android.widget.Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        walletRefreshing = false;
                        View v = getView();
                        if (!isAdded() || v == null) {
                            return;
                        }
                        setWalletRefreshing(v, false);
                        android.widget.Toast.makeText(requireContext(),
                                message == null ? getString(R.string.wallet_refresh_failed) : message,
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setWalletRefreshing(View root, boolean busy) {
        View button = root.findViewById(R.id.walletRefresh);
        View progress = root.findViewById(R.id.walletRefreshProgress);
        button.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
        button.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    // ---- Block 3: anokiX rewards --------------------------------------------

    private void bindRewards(View root, TraderDashboardData.Rewards rewards) {
        TextView points = root.findViewById(R.id.rewardsPoints);
        TextView earned = root.findViewById(R.id.rewardsEarned);
        TextView value = root.findViewById(R.id.rewardsValue);

        long pts = rewards == null ? 0 : rewards.points;
        long month = rewards == null ? 0 : rewards.earned_this_month;
        points.setText(String.format(Locale.US, "%,d", pts));
        value.setText(money(rewards == null ? 0 : rewards.value));
        if (month <= 0) {
            earned.setVisibility(View.GONE);
        } else {
            earned.setVisibility(View.VISIBLE);
            earned.setText(getString(R.string.rewards_earned_this_month, month));
        }
    }

    // ---- Block 4: Pending Orders --------------------------------------------

    private void bindPendingOrders(View root, TraderDashboardData.PendingOrders pending) {
        TextView count = root.findViewById(R.id.pendingCount);
        TextView value = root.findViewById(R.id.pendingValue);
        count.setText(String.format(Locale.US, "%,d", pending == null ? 0 : pending.count));
        value.setText(getString(R.string.total_value_amount,
                money(pending == null ? 0 : pending.total_value)));
    }

    // ---- Promotions carousel -------------------------------------------------

    private void setupPromotionsCarousel(View root) {
        promoPager = root.findViewById(R.id.promotionsPager);
        promoIndicator = root.findViewById(R.id.promotionsIndicator);
        promoPager.setAdapter(new PromotionAdapter());
        promoPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicator(position);
            }
        });
    }

    private void bindPromotions(View root, List<TraderDashboardData.Promotion> list) {
        View section = root.findViewById(R.id.promotionsSection);
        promotions.clear();
        if (list != null) {
            promotions.addAll(list);
        }
        if (promotions.isEmpty()) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);
        if (promoPager.getAdapter() != null) {
            promoPager.getAdapter().notifyDataSetChanged();
        }
        buildIndicator();
        updateIndicator(promoPager.getCurrentItem());
    }

    /** A single dot per promotion; hidden entirely when there is only one slide. */
    private void buildIndicator() {
        promoIndicator.removeAllViews();
        if (promotions.size() < 2) {
            promoIndicator.setVisibility(View.GONE);
            return;
        }
        promoIndicator.setVisibility(View.VISIBLE);
        int density = (int) getResources().getDisplayMetrics().density;
        for (int i = 0; i < promotions.size(); i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(8 * density, 8 * density);
            lp.setMargins(4 * density, 0, 4 * density, 0);
            dot.setLayoutParams(lp);
            promoIndicator.addView(dot);
        }
    }

    private void updateIndicator(int selected) {
        for (int i = 0; i < promoIndicator.getChildCount(); i++) {
            View dot = promoIndicator.getChildAt(i);
            boolean active = i == selected;
            dot.setBackground(roundedRect(active ? 0xFF7C3AED : 0x33000000, 4));
            dot.setAlpha(active ? 1f : 0.6f);
        }
    }

    private class PromotionAdapter extends RecyclerView.Adapter<PromotionAdapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView name;
            final TextView description;
            final TextView validity;
            final TextView cta;

            Holder(View v) {
                super(v);
                image = v.findViewById(R.id.promoImage);
                name = v.findViewById(R.id.promoName);
                description = v.findViewById(R.id.promoDescription);
                validity = v.findViewById(R.id.promoValidity);
                cta = v.findViewById(R.id.promoCtaButton);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_dashboard_promotion, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            TraderDashboardData.Promotion p = promotions.get(position);
            h.name.setText(p.name == null ? "" : p.name);

            // INVISIBLE (not GONE): the description carries the page's layout_weight, so it
            // must keep absorbing the slack in the fixed-height slide even when empty.
            if (p.description == null || p.description.isEmpty()) {
                h.description.setVisibility(View.INVISIBLE);
            } else {
                h.description.setVisibility(View.VISIBLE);
                h.description.setText(p.description);
            }

            String until = OrderFormat.deliveryDate(p.end_date);
            if (until == null || until.isEmpty()) {
                h.validity.setVisibility(View.GONE);
            } else {
                h.validity.setVisibility(View.VISIBLE);
                h.validity.setText(getString(R.string.promo_valid_until, until));
            }

            String banner = p.bannerUrl();
            if (banner == null || banner.isEmpty()) {
                h.image.setVisibility(View.GONE);
            } else {
                h.image.setVisibility(View.VISIBLE);
                if (GlideGuard.canLoad(h.image)) {
                    Glide.with(h.image).load(banner).centerCrop().into(h.image);
                }
            }

            h.cta.setOnClickListener(v -> openMarketplace());
            h.itemView.setOnClickListener(v -> openMarketplace());
        }

        @Override
        public int getItemCount() {
            return promotions.size();
        }
    }

    // ---- Recent orders --------------------------------------------------------

    private void bindRecentOrders(View root, List<TraderDashboardData.RecentOrder> orders) {
        LinearLayout list = root.findViewById(R.id.recentOrdersList);
        View empty = root.findViewById(R.id.recentOrdersEmpty);
        list.removeAllViews();

        if (orders == null || orders.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < orders.size(); i++) {
            TraderDashboardData.RecentOrder order = orders.get(i);
            View row = inflater.inflate(R.layout.item_recent_order, list, false);

            ((TextView) row.findViewById(R.id.orderId))
                    .setText(order.order_number == null ? "" : order.order_number);

            TextView distributor = row.findViewById(R.id.orderTrader);
            String name = order.distributor == null ? null : order.distributor.display_name;
            if (name == null || name.isEmpty()) {
                distributor.setVisibility(View.GONE);
            } else {
                distributor.setVisibility(View.VISIBLE);
                distributor.setText(name);
            }

            ((TextView) row.findViewById(R.id.orderDate))
                    .setText(OrderFormat.createdAt(order.created_at));
            ((TextView) row.findViewById(R.id.orderAmount))
                    .setText(money(order.total_amount));

            TextView status = row.findViewById(R.id.orderStatus);
            int color = OrderFormat.statusColor(order.status_key);
            status.setText(order.status == null
                    ? OrderFormat.humanize(order.status_key) : order.status);
            status.setTextColor(color);
            status.setBackground(roundedRect((color & 0x00FFFFFF) | 0x1A000000, 20));

            row.setOnClickListener(v -> selectTab(R.id.nav_orders));
            list.addView(row);

            if (i < orders.size() - 1) {
                list.addView(divider());
            }
        }
    }

    // ---- Top selling products -------------------------------------------------

    private void bindTopProducts(View root, List<TraderDashboardData.TopProduct> products) {
        LinearLayout list = root.findViewById(R.id.topProductsList);
        View empty = root.findViewById(R.id.topProductsEmpty);
        list.removeAllViews();

        if (products == null || products.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < products.size(); i++) {
            TraderDashboardData.TopProduct p = products.get(i);
            View row = inflater.inflate(R.layout.item_top_product, list, false);
            ((TextView) row.findViewById(R.id.rankNum)).setText(String.valueOf(i + 1));
            ((TextView) row.findViewById(R.id.productName)).setText(p.name == null ? "" : p.name);
            ((TextView) row.findViewById(R.id.productSold))
                    .setText(String.format(Locale.US, "%,.0f sold", p.quantity));
            ((TextView) row.findViewById(R.id.productRevenue)).setText(money(p.revenue));
            list.addView(row);
        }
    }

    // ---- Helpers ---------------------------------------------------------------

    private View divider() {
        View separator = new View(requireContext());
        separator.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) getResources().getDisplayMetrics().density));
        separator.setBackgroundColor(0xFFE2E8F0);
        return separator;
    }

    private String money(double value) {
        return OrderFormat.money(value, currencySymbol);
    }

    /** "2026-07-20T02:58:47+00:00" → local "04:58". Empty when unparseable. */
    private String timeOfDay(String iso) {
        if (iso == null || iso.isEmpty()) {
            return "";
        }
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss"};
        for (String pattern : patterns) {
            try {
                Date d = new SimpleDateFormat(pattern, Locale.US).parse(iso);
                if (d != null) {
                    return new SimpleDateFormat("HH:mm", Locale.US).format(d);
                }
            } catch (java.text.ParseException ignored) {
                // try the next pattern
            }
        }
        return "";
    }

    private GradientDrawable roundedRect(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        return d;
    }

    private void selectTab(int navId) {
        if (getActivity() != null) {
            BottomNavigationView nav = getActivity().findViewById(R.id.bottomNav);
            if (nav != null) {
                nav.setSelectedItemId(navId);
            }
        }
    }
}
