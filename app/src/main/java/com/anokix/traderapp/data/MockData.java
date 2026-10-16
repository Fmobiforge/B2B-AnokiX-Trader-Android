package com.anokix.traderapp.data;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.Distributor;
import com.anokix.traderapp.model.InventoryItem;
import com.anokix.traderapp.model.ListItem;
import com.anokix.traderapp.model.MarketPromo;
import com.anokix.traderapp.model.MenuItem;
import com.anokix.traderapp.model.PointsEntry;
import com.anokix.traderapp.model.ProductItem;
import com.anokix.traderapp.model.RewardVoucher;
import com.anokix.traderapp.model.SupportTicket;
import com.anokix.traderapp.model.TraderNotification;
import com.anokix.traderapp.model.TraderReport;
import com.anokix.traderapp.model.Transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MockData {
    private MockData() {}

    /** Rich, adjustable trader inventory (name, SKU, qty, reorder, cost/retail, expiry, distributor). */
    /** Inventory lines extracted verbatim from the Trader Portal /inventory page. */
    public static List<InventoryItem> getInventoryItems() {
        List<InventoryItem> items = new ArrayList<>();
        items.add(new InventoryItem("Coca-Cola 500ml", "SKU-CC-500", "6001064001234", "Beverages", "Coca-Cola", "Shop Floor", 4320, 5000, 64800, "in_stock", "fast", "18 May, 14:32", "#dc2626"));
        items.add(new InventoryItem("Grandpa Headache Powders", "SKU-GP-001", "6001064002345", "Health", "Grandpa", "Shop Floor", 45, 500, 675, "low_stock", "slow", "18 May, 11:15", "#2563eb"));
        items.add(new InventoryItem("Albany Superior White Bread", "SKU-ALB-700", "6001064003456", "Bakery", "Albany", "Back Store", 890, 1200, 13350, "in_stock", "fast", "18 May, 09:45", "#f59e0b"));
        items.add(new InventoryItem("Sunlight Dishwashing Liquid", "SKU-SL-750", "6001064004567", "Household", "Sunlight", "Shop Floor", 3210, 4000, 48150, "in_stock", "fast", "17 May, 16:20", "#eab308"));
        items.add(new InventoryItem("KOO Baked Beans", "SKU-KOO-410", "6001064005678", "Grocery", "KOO", "Back Store", 0, 800, 0, "out_of_stock", "slow", "17 May, 10:05", "#ea580c"));
        items.add(new InventoryItem("Clover Fresh Full Cream Milk", "SKU-CLV-2L", "6001064006789", "Dairy", "Clover", "Cold Storage", 540, 600, 8100, "in_stock", "fast", "18 May, 08:30", "#16a34a"));
        items.add(new InventoryItem("OMO Auto Washing Powder", "SKU-OMO-2KG", "6001064007890", "Household", "OMO", "Back Store", 72, 400, 10800, "low_stock", "slow", "16 May, 15:50", "#0ea5e9"));
        items.add(new InventoryItem("Sasko Premium Brown Bread", "SKU-SKO-700", "6001064008901", "Bakery", "Sasko", "Shop Floor", 625, 900, 9375, "in_stock", "fast", "18 May, 07:10", "#a16207"));
        items.add(new InventoryItem("Dettol Soap 175g", "SKU-DT-175", "6001064009012", "Health", "Dettol", "Back Store", 28, 200, 840, "damaged", "slow", "15 May, 12:00", "#059669"));
        items.add(new InventoryItem("Expired Baked Beans 410g", "SKU-EXP-410", "6001064010123", "Grocery", "KOO", "Cold Storage", 15, 100, 225, "expired", "slow", "10 May, 09:00", "#78716c"));
        return items;
    }

    /** POS catalogue â€” verbatim from the Trader Portal /pos page. */
    public static List<ProductItem> getProductItems() {
        return Arrays.asList(
            new ProductItem("pos1",  "Coca Cola 2L",        "SKU: DRK001", "Beverages",     "R21.50",  "In Stock",     null,      "#c41e3a"),
            new ProductItem("pos2",  "Lay's Chips 150g",    "SKU: SNK003", "Snacks",        "R18.99",  "In Stock",     null,      "#f4c430"),
            new ProductItem("pos3",  "Vim Dish Liquid",     "SKU: HSH012", "Household",     "R32.50",  "In Stock",     null,      "#00a651"),
            new ProductItem("pos4",  "Coca Cola 1.5L",      "SKU: DRK002", "Beverages",     "R32.50",  "In Stock",     null,      "#c41e3a"),
            new ProductItem("pos5",  "OMO Washing Powder",  "SKU: HSH005", "Household",     "R89.99",  "In Stock",     "Popular", "#0066b3"),
            new ProductItem("pos6",  "Sunlight Bar Soap",   "SKU: PC001",  "Personal Care", "R12.99",  "In Stock",     null,      "#ffcc00"),
            new ProductItem("pos7",  "Albany Bread White",  "SKU: FR001",  "Others",        "R16.50",  "In Stock",     null,      "#caa472"),
            new ProductItem("pos8",  "Simba Niknaks",       "SKU: SNK007", "Snacks",        "R9.99",   "In Stock",     null,      "#e85d04"),
            new ProductItem("pos9",  "Fanta Orange 2L",     "SKU: DRK003", "Beverages",     "R19.99",  "In Stock",     null,      "#ff6b00"),
            new ProductItem("pos10", "Clover Fresh Milk 2L","SKU: FR002",  "Frozen Foods",  "R28.99",  "In Stock",     null,      "#0066cc"),
            new ProductItem("pos11", "Pampers Baby Dry",    "SKU: BB001",  "Baby Care",     "R189.99", "In Stock",     null,      "#00a0e3"),
            new ProductItem("pos12", "Knorr Soup Mix",      "SKU: SNK010", "Snacks",        "R14.50",  "Out of Stock", null,      "#2d6a4f")
        );
    }

    public static List<MenuItem> getMoreMenuItems() {
        return Arrays.asList(
            new MenuItem("marketplace", "Marketplace", R.drawable.ic_products),
            new MenuItem("inventory", "Inventory", R.drawable.ic_inventory),
            new MenuItem("payments", "Payments", R.drawable.ic_finances),
            new MenuItem("airtime", "Airtime & VAS", R.drawable.ic_promo_tag),
            new MenuItem("rewards", "Loyalty & Rewards", R.drawable.ic_promo_gift),
            new MenuItem("promotions", "Promotions", R.drawable.ic_promotions),
            new MenuItem("customers", "Customers", R.drawable.ic_traders),
            new MenuItem("analytics", "Analytics", R.drawable.ic_reports),
            new MenuItem("notifications", "Notifications", R.drawable.ic_notifications),
            new MenuItem("support", "Support", R.drawable.ic_headset),
            new MenuItem("settings", "Settings", R.drawable.ic_settings)
        );
    }

    /**
     * Drawer menu mirrors the Trader Portal sidebar (18 items, exact order, titles, subtitles).
     * The GRV/GRN/Sales/Invoices/Distributors entries have no screen yet â€” tapping is a no-op
     * (MainActivity.onDrawerItemClicked has no case for them), matching the portal nav for parity.
     */
    public static List<MenuItem> getDrawerMenuItems() {
        return Arrays.asList(
            new MenuItem("home", "Dashboard", R.drawable.ic_dashboard),
            new MenuItem("marketplace", "Marketplace", R.drawable.ic_stores),
            new MenuItem("cart", "Cart", R.drawable.ic_cart),
            new MenuItem("orders", "Orders", R.drawable.ic_orders),
            new MenuItem("goods_received", "Goods Received (GRV)", R.drawable.ic_deliveries),
            new MenuItem("goods_returns", "Goods Returns (GRN)", R.drawable.ic_returns),
            new MenuItem("inventory", "Inventory", R.drawable.ic_inventory),
            new MenuItem("sell", "POS", R.drawable.ic_shopping_bag),
            new MenuItem("sales", "Sales", R.drawable.ic_finances),
            new MenuItem("invoices", "Invoices", R.drawable.ic_document),
            new MenuItem("finances", "Finances", R.drawable.ic_finance_menu),
            new MenuItem("distributors", "Distributors", R.drawable.ic_traders),
            // Hidden for now — anokiX wallet and anokiX rewards are not part of the
            // current release. Keep these entries so the menu can be restored later.
            // (The wallet screen itself is still in the build — WalletActivity.)
            new MenuItem("wallet", "anokiX wallet", /*"Powered by IMB", */R.drawable.ic_wallet),
            new MenuItem("rewards", "anokiX rewards", /*"Powered by Limes", */R.drawable.ic_promo_gift),
            new MenuItem("airtime", "Airtime & VAS", /*"Powered by Limes", */R.drawable.ic_vas),
            new MenuItem("analytics", "Analytics", R.drawable.ic_reports),
            new MenuItem("reports", "Reports", R.drawable.ic_finance_report),
            new MenuItem("staff", "Staff", "Who can use this till", R.drawable.ic_staff),
            new MenuItem("settings", "Settings", R.drawable.ic_settings),
            new MenuItem("notifications", "Notifications", R.drawable.ic_notifications),
            new MenuItem("support", "Support Centre", R.drawable.ic_headset)
        );
    }

    // ---- Trader module mock lists ---------------------------------------

    /** Marketplace catalogue as orderable products (category field holds the distributor). */
    /** Marketplace bestsellers â€” verbatim from the Trader Portal /marketplace page. */
    public static List<ProductItem> getMarketplaceProducts() {
        return Arrays.asList(
            new ProductItem("p1", "Coca-Cola 2L",       "Case (6 x 2L)",    "Beverages", "R129", "In Stock â€¢ 120+ cases", "Popular",    "#e53935"),
            new ProductItem("p2", "Sunlight Liquid 2L", "Case (6 x 2L)",    "Household", "R189", "In Stock â€¢ 85+ cases",  "Bestseller", "#f9a825"),
            new ProductItem("p3", "White Bread Loaf",   "Pack (12 loaves)", "Staples",   "R96",  "Low Stock â€¢ 12 packs",  "Popular",    "#ff6f00"),
            new ProductItem("p4", "Cooking Oil 2L",     "Case (6 x 2L)",    "Staples",   "R215", "In Stock â€¢ 64+ cases",  "Bestseller", "#ffc107"),
            new ProductItem("p5", "Fresh Milk 2L",      "Case (6 x 2L)",    "Beverages", "R118", "In Stock â€¢ 200+ cases", null,         "#42a5f5"),
            new ProductItem("p6", "Simba Chips 150g",   "Case (24 x 150g)", "Snacks",    "R142", "In Stock â€¢ 90+ cases",  "Popular",    "#e85d04")
        );
    }

    /** Top distributors row â€” verbatim from the Trader Portal /marketplace page. */
    public static List<Distributor> getDistributors() {
        return Arrays.asList(
            new Distributor("Unilever",      "South Africa", "Distribution", "4.8", "2,450+", "#0d47a1"),
            new Distributor("Tiger Brands",  "South Africa", "Distribution", "4.7", "1,860+", "#c62828"),
            new Distributor("Coca-Cola",     "South Africa", "Distribution", "4.9", "920+",   "#e53935"),
            new Distributor("Pioneer Foods", "South Africa", "Distribution", "4.6", "1,580+", "#2e7d32"),
            new Distributor("Nestlé",        "South Africa", "Distribution", "4.8", "2,100+", "#1565c0")
        );
    }

    /** Support tickets â€” verbatim from the Trader Portal /support page. */
    public static List<SupportTicket> getSupportTickets() {
        return Arrays.asList(
            new SupportTicket("#14523", "Wallet settlement delay",      "Settlement to IMB account has not reflected after 24 hours.",        "open",        "13 Jun 2026, 09:30"),
            new SupportTicket("#14512", "POS scanner not connecting",   "Barcode scanner disconnects intermittently during peak hours.",     "in_progress", "12 Jun 2026, 14:15"),
            new SupportTicket("#14498", "Rewards points not credited",  "POS transaction on 10 Jun did not award expected Limes points.",    "resolved",    "11 Jun 2026, 16:45")
        );
    }

    /** Notifications â€” verbatim from the Trader Portal /notifications page. */
    public static List<TraderNotification> getTraderNotifications() {
        return Arrays.asList(
            new TraderNotification("orders",    "New Order Received",          "Tiger Brands accepted your order ORD-10548. Estimated delivery 15 Jun 2026.", "13 Jun, 10:28", false, false, "/orders"),
            new TraderNotification("wallet",    "Wallet Settlement Complete",  "R12,950.00 has been settled to your IMB Business Account.",                   "13 Jun, 09:15", false, false, "/wallet"),
            new TraderNotification("inventory", "Low Stock Alert",             "Coca-Cola 2L is below minimum stock level. Only 12 cases remaining.",          "13 Jun, 08:42", false, true,  "/inventory"),
            new TraderNotification("rewards",   "Double Points Promotion",     "Earn 2X Limes Points on all POS transactions this week.",                      "12 Jun, 16:30", false, false, "/rewards"),
            new TraderNotification("orders",    "Order Out for Delivery",      "Your order ORD-10544 from Pioneer Foods is on the way. ETA 14:00â€“17:00.",      "12 Jun, 11:20", true,  false, "/orders"),
            new TraderNotification("wallet",    "Money Received",              "R1,250.00 received from Thabo Mokoena via anokiX wallet.",                     "11 Jun, 14:05", true,  false, "/wallet"),
            new TraderNotification("system",    "POS Software Update",         "POS version 1.4.2 is available. Update recommended for best performance.",     "10 Jun, 10:30", true,  true,  "/pos"),
            new TraderNotification("rewards",   "Points Expiring Soon",        "5,200 Limes points will expire on 30 Jun 2026. Redeem them before they expire.","9 Jun, 18:45",  true,  true,  "/rewards")
        );
    }

    /** Recent reports â€” verbatim from the Trader Portal /reports page. */
    public static List<TraderReport> getTraderReports() {
        return Arrays.asList(
            new TraderReport("Monthly Sales Summary",     "Sales",       "Jun 2026",        "PDF",   "ready",      "2.1 MB", "#7c3aed"),
            new TraderReport("POS Shift Summary",         "POS",         "18 Jun 2026",     "Excel", "ready",      "1.4 MB", "#6366f1"),
            new TraderReport("Low Stock Alert Report",    "Inventory",   "18 Jun 2026",     "Excel", "ready",      "860 KB", "#2563eb"),
            new TraderReport("Marketplace Order Summary", "Marketplace", "13â€“19 Jun 2026",  "PDF",   "ready",      "2.8 MB", "#16a34a"),
            new TraderReport("Wallet Settlement Report",  "Wallet",      "Jun 2026",        "Excel", "generating", "â€”",      "#0891b2"),
            new TraderReport("Limes Points Activity",     "Rewards",     "Jun 2026",        "CSV",   "ready",      "380 KB", "#ea580c"),
            new TraderReport("Weekly Sales Trends",       "Sales",       "Week 24, 2026",   "PDF",   "ready",      "1.1 MB", "#7c3aed"),
            new TraderReport("Top Selling Products",      "POS",         "Jun 2026",        "PDF",   "failed",     "â€”",      "#6366f1"),
            new TraderReport("Stock Movement Analysis",   "Inventory",   "Q2 2026",         "Excel", "ready",      "4.2 MB", "#2563eb"),
            new TraderReport("Distributor Spend Report",  "Marketplace", "Jun 2026",        "PDF",   "ready",      "1.9 MB", "#16a34a")
        );
    }

    /** Quick Redeem vouchers â€” verbatim from the Trader Portal /rewards page. */
    public static List<RewardVoucher> getRewardVouchers() {
        return Arrays.asList(
            new RewardVoucher("MTN",        "R10 Airtime Voucher", 1000, "#ffcc00"),
            new RewardVoucher("Vodacom",    "R10 Airtime Voucher", 1000, "#e60000"),
            new RewardVoucher("Checkers",   "R50 Grocery Voucher", 5000, "#c62828"),
            new RewardVoucher("Shoprite",   "R50 Grocery Voucher", 5000, "#e53935"),
            new RewardVoucher("Pick n Pay", "R50 Grocery Voucher", 5000, "#1565c0")
        );
    }

    /** Points Activity â€” verbatim from the Trader Portal /rewards page. */
    public static List<PointsEntry> getPointsActivity() {
        return Arrays.asList(
            new PointsEntry("earned",   "Earned",          "Purchase at anokiX POS",        450,  true,  "Today, 10:32"),
            new PointsEntry("cashback", "Cashback Earned", "Marketplace order ORD-10543",   120,  true,  "Yesterday, 16:15"),
            new PointsEntry("redeemed", "Redeemed",        "MTN R10 Airtime Voucher",      1000,  false, "11 Jun 2026, 09:20"),
            new PointsEntry("earned",   "Earned",          "Utility bill payment",          100,  true,  "10 Jun 2026, 14:45")
        );
    }

    /** Exclusive promotions â€” verbatim from the Trader Portal /marketplace page. */
    public static List<MarketPromo> getMarketPromos() {
        return Arrays.asList(
            new MarketPromo("Sunlight Range",   "10% OFF",                     "On all Sunlight washing products", "Valid till 31 Dec 2024", "green"),
            new MarketPromo("Coca-Cola",        "Buy 10 cases Get 1 case FREE", "On selected Coca-Cola products",   "Valid till 15 Jan 2025", "purple"),
            new MarketPromo("White Star Maize", "Bulk Deal 5% OFF",            "On orders over 50 cases",          "Valid till 28 Jan 2025", "orange")
        );
    }

    /** Wallet transactions â€” verbatim from the Trader Portal /wallet page. */
    public static List<Transaction> getWalletTransactions() {
        return Arrays.asList(
            new Transaction("received",   "Money Received",   "From Thabo Mokoena",       1250, true,  "Today, 09:42",        "Success"),
            new Transaction("sent",       "Money Sent",       "To Nomsa Khumalo",          480, false, "Today, 08:15",        "Success"),
            new Transaction("settlement", "Settlement",       "To IMB Business Account",  3200, false, "Yesterday, 16:30",    "Success"),
            new Transaction("airtime",    "Airtime Purchase", "Vodacom R50",                50, false, "Yesterday, 11:20",    "Success"),
            new Transaction("received",   "Money Received",   "From Marketplace refund",   320, true,  "12 Jun 2026, 14:05",  "Success")
        );
    }

    public static List<ListItem> getPayments() {
        return Arrays.asList(
            new ListItem("Card payment · #PMT-3391", "R248.00 · Today 14:22", "Settled"),
            new ListItem("QR payment · #PMT-3390", "R96.50 · Today 13:05", "Settled"),
            new ListItem("Wallet payment · #PMT-3388", "R1,250.00 · Today 11:40", "Pending"),
            new ListItem("Card payment · #PMT-3385", "R75.00 · Yesterday", "Settled"),
            new ListItem("Refund · #PMT-3380", "-R42.00 · Yesterday", "Refunded")
        );
    }

    public static List<ListItem> getCustomers() {
        return Arrays.asList(
            new ListItem("Thandeka M.", "12 purchases · R3,450 lifetime", "Loyal"),
            new ListItem("Sipho K.", "8 purchases · R1,980 lifetime", "Regular"),
            new ListItem("Naledi P.", "21 purchases · R7,210 lifetime", "VIP"),
            new ListItem("Bongani Z.", "3 purchases · R540 lifetime", "New"),
            new ListItem("Ayanda D.", "15 purchases · R4,860 lifetime", "Loyal")
        );
    }

    /** Distributor/supplier offers a trader can claim. */
    public static List<ListItem> getTraderPromotions() {
        return Arrays.asList(
            new ListItem("10% off anokiX Maize Meal", "Cong Distributor · ends 30 Jun", "Active"),
            new ListItem("Buy 10 Get 1 Free · Coca-Cola 2L", "Beverage Co · ends 15 Jul", "Active"),
            new ListItem("R20 cashback on Sunlight 2kg", "Clean Co · ends 28 Jun", "Active"),
            new ListItem("Free delivery over R2,000", "anokiX · ongoing", "Active"),
            new ListItem("Summer Mega Deals", "Multiple distributors · from 1 Jul", "Upcoming")
        );
    }

}
