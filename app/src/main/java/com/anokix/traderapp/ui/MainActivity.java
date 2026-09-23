package com.anokix.traderapp.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.traderapp.R;
import com.anokix.traderapp.data.MockData;
import com.anokix.traderapp.messaging.PushManager;
import com.anokix.traderapp.model.MenuItem;
import com.anokix.traderapp.pos.WiseCashier;
import com.anokix.traderapp.session.SessionManager;
import com.anokix.traderapp.ui.adapter.DrawerMenuAdapter;
import com.anokix.traderapp.ui.fragment.DashboardFragment;
import com.anokix.traderapp.ui.fragment.MoreFragment;
import com.anokix.traderapp.ui.fragment.OrdersFragment;
import com.anokix.traderapp.ui.fragment.SellFragment;
import com.anokix.traderapp.ui.views.TypefaceCache;
import com.anokix.traderapp.ui.wallet.WalletActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    /** Intent extra: open a specific bottom-nav tab ("home"/"sell"/"orders"/"wallet"/"more"). */
    public static final String EXTRA_OPEN_TAB = "open_tab";

    private DrawerLayout drawerLayout;
    private View drawerContent;
    private BottomNavigationView bottomNav;
    private DrawerMenuAdapter drawerAdapter;

    /** POST_NOTIFICATIONS prompt (Android 13+); result is ignored — pushes degrade gracefully. */
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawerLayout);
        drawerContent = findViewById(R.id.drawerContent);
        bottomNav = findViewById(R.id.bottomNav);

        setupDrawer();
        setupBottomNav();

        if (savedInstanceState == null) {
            if (!selectTabFromIntent(getIntent())) {
                bottomNav.setSelectedItemId(R.id.nav_home);
            }
        }

        // FCM: ensure the channel exists, ask for the runtime permission, register the
        // token, then apply any routing a notification tap carried in.
        PushManager.ensureChannel(this);
        requestNotificationPermission();
        PushManager.syncToken(this);
        applyPushRouting(getIntent());

        /*if (WiseCashier.isInstalled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Device Check")
                    .setMessage("This is a POS Terminal Device.")
                    .setPositiveButton("OK", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Device Check")
                    .setMessage("This is not POS Terminal Device or payment app was not installed!")
                    .setPositiveButton("OK", null)
                    .show();
        }*/
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        selectTabFromIntent(intent);
        applyPushRouting(intent);
    }

    /** Open the screen a tapped notification points at (order/return/stock), then clear it. */
    private void applyPushRouting(Intent intent) {
        PushManager.handleRouting(this, intent);
        PushManager.clearRouting(intent);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /** Returns true if the intent requested (and we applied) a specific tab. */
    private boolean selectTabFromIntent(Intent intent) {
        if (intent == null) return false;
        String tab = intent.getStringExtra(EXTRA_OPEN_TAB);
        if (tab == null) return false;
        switch (tab) {
            case "sell":   bottomNav.setSelectedItemId(R.id.nav_sell);   return true;
            case "orders": bottomNav.setSelectedItemId(R.id.nav_orders); return true;
            // The wallet is a screen of its own, so the tab underneath it stays on Home.
            case "wallet":
                bottomNav.setSelectedItemId(R.id.nav_home);
                showWallet();
                return true;
            case "more":   bottomNav.setSelectedItemId(R.id.nav_more);   return true;
            default:       bottomNav.setSelectedItemId(R.id.nav_home);   return true;
        }
    }

    private void setupBottomNav() {
        hideWalletTab();
        styleBottomNavMenu();

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment fragment;
            String key;
            if (id == R.id.nav_sell) {
                fragment = new SellFragment();
                key = "sell";
            } else if (id == R.id.nav_orders) {
                fragment = new OrdersFragment();
                key = "orders";
            } else if (id == R.id.nav_more) {
                fragment = new MoreFragment();
                key = "more";
            } else {
                fragment = new DashboardFragment();
                key = "home";
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
            if (drawerAdapter != null) {
                drawerAdapter.setSelectedByKey(key);
            }
            return true;
        });
    }

    /**
     * Hides the Wallet tab. The anokiX wallet stays in the build — the drawer entry and
     * the Dashboard wallet card both open it through {@link #showWallet()} — it is only
     * dropped from the bottom bar for this release.
     */
    private void hideWalletTab() {
        android.view.MenuItem wallet = bottomNav.getMenu().findItem(R.id.nav_wallet);
        if (wallet != null) {
            wallet.setVisible(false);
        }
    }

    /**
     * Opens the anokiX wallet. It is a screen of its own rather than a tab, so Back
     * returns the trader to whatever they were doing instead of dumping them on Home.
     */
    public void showWallet() {
        startActivity(new Intent(this, WalletActivity.class));
    }

    private void styleBottomNavMenu() {
        float textSizeSp = getResources().getDimension(R.dimen.bottom_nav_text_size)
                / getResources().getDisplayMetrics().scaledDensity;
        bottomNav.post(() -> TypefaceCache.applyFontToBottomNavigationViewMenu(bottomNav, this, textSizeSp));
    }

    private void setupDrawer() {
        List<MenuItem> items = MockData.getDrawerMenuItems();
        RecyclerView drawerList = findViewById(R.id.drawerMenuList);
        drawerList.setLayoutManager(new LinearLayoutManager(this));
        // The drawer is a fixed-length menu in a fixed-size list, so skip the requestLayout
        // pass on every bind and keep every row in the view cache — the drawer then slides
        // without having to inflate anything mid-animation.
        drawerList.setHasFixedSize(true);
        drawerList.setItemViewCacheSize(items.size());
        drawerAdapter = new DrawerMenuAdapter(items, this::onDrawerItemClicked);
        drawerList.setAdapter(drawerAdapter);
        bindDrawerProfile();
    }

    /**
     * Wires a screen's header hamburger to the drawer.
     *
     * <p>The button sits in the left 40dp of the header, which overlaps the edge strip
     * {@link DrawerLayout} watches for its open-by-swipe gesture. A tap that drifts by more
     * than the touch slop — i.e. most real thumb taps — was being claimed by that edge
     * detector, which dragged the drawer a few pixels and settled it closed again, so the
     * tap did nothing and the button never saw its click. Telling the parent chain not to
     * intercept as soon as the finger lands hands the whole gesture to the button.
     */
    // The touch listener returns false, so the view's own onTouchEvent still runs and
    // still calls performClick() — accessibility is unaffected.
    @SuppressLint("ClickableViewAccessibility")
    public void bindDrawerButton(@Nullable View button) {
        if (button == null) return;
        button.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return false;   // the OnClickListener below still runs
        });
        button.setOnClickListener(v -> openDrawer());
    }

    private void bindDrawerProfile() {
        SessionManager session = SessionManager.get(this);
        String storeName = session.getStoreDisplayName();
        if (storeName.isEmpty()) {
            storeName = "Sipho's Spaza";
        }
        String accountName = session.getFullName();
        if (accountName.isEmpty()) {
            accountName = session.getRoleLabel();
        }
        if (accountName.isEmpty()) {
            accountName = "Trader Account";
        }

        android.widget.TextView nameView = findViewById(R.id.drawerProfileName);
        android.widget.TextView emailView = findViewById(R.id.drawerProfileEmail);
        android.widget.TextView avatar = findViewById(R.id.drawerProfileAvatar);
        nameView.setText(storeName);
        emailView.setText(accountName);
        avatar.setText(storeName.substring(0, 1).toUpperCase());

        findViewById(R.id.panelUserInfo).setOnClickListener(v -> {});
        findViewById(R.id.drawerLogout).setOnClickListener(v -> confirmLogout());
    }

    private void confirmLogout() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.logout, (d, w) -> {
                    PushManager.unregister(this);   // drop this device's token server-side
                    SessionManager.get(this).clear();
                    Intent i = new Intent(this, LoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .show();
    }

    private void onDrawerItemClicked(MenuItem item) {
        closeDrawer();
        switch (item.key) {
            case "home":
                bottomNav.setSelectedItemId(R.id.nav_home);
                break;
            case "sell":
                bottomNav.setSelectedItemId(R.id.nav_sell);
                break;
            case "orders":
                bottomNav.setSelectedItemId(R.id.nav_orders);
                break;
            case "wallet":
                showWallet();
                break;
            case "inventory":
                startActivity(new Intent(this, InventoryActivity.class));
                break;
            case "goods_received":
                startActivity(new Intent(this, GoodsReceivedActivity.class));
                break;
            case "goods_returns":
                startActivity(new Intent(this, GoodsReturnsActivity.class));
                break;
            case "sales":
                startActivity(new Intent(this, SalesActivity.class));
                break;
            case "invoices":
                startActivity(new Intent(this, InvoicesActivity.class));
                break;
            case "finances":
                startActivity(new Intent(this, FinancesActivity.class));
                break;
            case "distributors":
                startActivity(new Intent(this, DistributorsActivity.class));
                break;
            case "marketplace":
                startActivity(new Intent(this, MarketplaceActivity.class));
                break;
            case "cart":
                startActivity(new Intent(this, CartActivity.class));
                break;
            case "payments":
                startActivity(new Intent(this, PaymentsActivity.class));
                break;
            case "airtime":
                startActivity(new Intent(this, AirtimeActivity.class));
                break;
            case "rewards":
                startActivity(new Intent(this, RewardsActivity.class));
                break;
            case "promotions":
                startActivity(new Intent(this, OffersActivity.class));
                break;
            case "customers":
                startActivity(new Intent(this, CustomersActivity.class));
                break;
            case "analytics":
                startActivity(new Intent(this, AnalyticsActivity.class));
                break;
            case "reports":
                startActivity(new Intent(this, ReportsActivity.class));
                break;
            case "staff":
                startActivity(new Intent(this, StaffActivity.class));
                break;
            case "notifications":
                startActivity(new Intent(this, NotificationsActivity.class));
                break;
            case "support":
                startActivity(new Intent(this, SupportActivity.class));
                break;
            case "settings":
                startActivity(new Intent(this, SettingsActivity.class));
                break;
        }
    }

    public void openDrawer() {
        if (drawerLayout == null || drawerContent == null) return;
        // Already open (or sliding open) — a second tap must not restart the animation,
        // which is what made a rushed double tap look like the drawer "stuck" half way.
        if (drawerLayout.isDrawerVisible(drawerContent)) return;
        // The soft keyboard steals the first tap on screens whose search field has focus
        // (POS, Finances, Inventory): the tap dismisses the IME and never reaches the
        // button. Drop focus ourselves so the drawer opens on that same tap.
        clearSearchFocus();
        drawerLayout.openDrawer(drawerContent);
    }

    public void closeDrawer() {
        if (drawerLayout != null) {
            drawerLayout.closeDrawers();
        }
    }

    /** Hides the IME and drops focus from whatever text field currently holds it. */
    private void clearSearchFocus() {
        View focused = getCurrentFocus();
        if (focused == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
        focused.clearFocus();
    }
}
