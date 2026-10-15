package com.anokix.traderapp.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.anokix.traderapp.network.dto.AutocompleteData;
import com.anokix.traderapp.network.dto.BaseInfoData;
import com.anokix.traderapp.network.dto.BusinessProfileData;
import com.anokix.traderapp.network.dto.CartData;
import com.anokix.traderapp.network.dto.CategoryPageData;
import com.anokix.traderapp.network.dto.CollectionData;
import com.anokix.traderapp.network.dto.PromotionCollectionData;
import com.anokix.traderapp.network.dto.CommonProductData;
import com.anokix.traderapp.network.dto.CoordinateData;
import com.anokix.traderapp.network.dto.MarketplaceData;
import com.anokix.traderapp.network.dto.OrderDetailData;
import com.anokix.traderapp.network.dto.OrdersData;
import com.anokix.traderapp.network.dto.PosIntentData;
import com.anokix.traderapp.network.dto.PosProductsData;
import com.anokix.traderapp.network.dto.PreferencesData;
import com.anokix.traderapp.network.dto.PosSaleData;
import com.anokix.traderapp.network.dto.PosSalesData;
import com.anokix.traderapp.network.dto.PosTerminalsData;
import com.anokix.traderapp.network.dto.VasCategoriesData;
import com.anokix.traderapp.network.dto.VasCustomersData;
import com.anokix.traderapp.network.dto.VasOnboardCustomerData;
import com.anokix.traderapp.network.dto.VasOnboardPreviewData;
import com.anokix.traderapp.network.dto.VasProductsData;
import com.anokix.traderapp.network.dto.VasSelectCustomerData;
import com.anokix.traderapp.network.dto.VasSubscriptionProductsData;
import com.anokix.traderapp.network.dto.VasSubscriptionsData;
import com.anokix.traderapp.network.dto.VasTransactionsData;
import com.anokix.traderapp.network.dto.CreateTraderData;
import com.anokix.traderapp.network.dto.DebitRequestsData;
import com.anokix.traderapp.network.dto.DistributorListData;
import com.anokix.traderapp.network.dto.TopupData;
import com.anokix.traderapp.network.dto.GrvDetailData;
import com.anokix.traderapp.network.dto.GrvListData;
import com.anokix.traderapp.network.dto.GrvPdfData;
import com.anokix.traderapp.network.dto.InvoiceListData;
import com.anokix.traderapp.network.dto.InvoicePdfData;
import com.anokix.traderapp.network.dto.InventoryAnalyticsData;
import com.anokix.traderapp.network.dto.InventoryData;
import com.anokix.traderapp.network.dto.InventoryHistoryData;
import com.anokix.traderapp.network.dto.InventorySummaryData;
import com.anokix.traderapp.network.dto.LoginData;
import com.anokix.traderapp.network.dto.NotificationsData;
import com.anokix.traderapp.network.dto.ReturnsListData;
import com.anokix.traderapp.network.dto.UnreadCountData;
import com.anokix.traderapp.network.dto.ProductDetailData;
import com.anokix.traderapp.network.dto.ProductsData;
import com.anokix.traderapp.network.dto.PromotionDetailData;
import com.anokix.traderapp.network.dto.PromotionsData;
import com.anokix.traderapp.network.dto.ReferenceData;
import com.anokix.traderapp.network.dto.RegisterTraderData;
import com.anokix.traderapp.network.dto.ReportMutationData;
import com.anokix.traderapp.network.dto.ReportsData;
import com.anokix.traderapp.network.dto.RewardsOverviewData;
import com.anokix.traderapp.network.dto.StaffData;
import com.anokix.traderapp.network.dto.StaffMemberData;
import com.anokix.traderapp.network.dto.SupportArticlesData;
import com.anokix.traderapp.network.dto.SupportOverviewData;
import com.anokix.traderapp.network.dto.SupportTicketData;
import com.anokix.traderapp.network.dto.StoreDetailData;
import com.anokix.traderapp.network.dto.StoresData;
import com.anokix.traderapp.network.dto.TradersData;
import com.anokix.traderapp.session.SessionManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Typed facade over {@link Http}. Each method runs the call on a background thread and
 * delivers the parsed {@code data} block (or an error message) back on the main thread.
 *
 * Flow per call:
 *   1. build path + params
 *   2. {@link Http} performs the request (logged under "AxApi")
 *   3. Gson parses the {@link ApiResponse} envelope
 *   4. callback fires on the UI thread
 */
public final class ApiClient {

    public static final String BASE_URL = "https://api.anokix.com/";  //"http://109.176.30.129/ax/";

    /**
     * Report files are served by a separate download service on its own port, and it
     * authenticates with the token in the request body rather than a Bearer header.
     */
    public static final String REPORT_DOWNLOAD_URL = "https://api.anokix.com:5720/api/report-download";;//"http://109.176.30.129:5720/api/report-download";

    private static ApiClient instance;

    private final ExecutorService io = Executors.newCachedThreadPool();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private final SessionManager session;

    private ApiClient(Context context) {
        this.session = SessionManager.get(context.getApplicationContext());
    }

    public static synchronized ApiClient get(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }

    // ---- Endpoints -------------------------------------------------------

    public void login(String email, String password, ApiCallback<LoginData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("email", email);
        form.put("password", password);
        form.put("portal_type", "trader");
        io.execute(() -> {
            // login itself needs no token
            Http.Result r = Http.postForm(BASE_URL, "api/common/login", form, null);
            deliver(r, LoginData.class, cb);
        });
    }

    /**
     * Request a password-reset link (api/common/forgot-password). Needs no token;
     * sends the typed email + portal_type "trader". Only status/message matter.
     */
    public void forgotPassword(String email, ApiCallback<Void> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("email", email == null ? "" : email);
        form.put("portal_type", "trader");
        io.execute(() -> {
            Http.Result r = Http.postForm(BASE_URL, "api/common/forgot-password", form, null);
            deliverStatusOnly(r, cb);
        });
    }

    /** Trader home screen: today's sales, wallet, rewards, pending orders, promotions, recent orders. */
    public void getTraderDashboard(ApiCallback<com.anokix.traderapp.network.dto.TraderDashboardData> cb) {
        getAuthed("api/trader/dashboard", null,
                com.anokix.traderapp.network.dto.TraderDashboardData.class, cb);
    }

    /**
     * Live wallet balance (api/common/wallet). The {@code data} block is the wallet object
     * itself — the same shape as the dashboard's {@code wallet} node — so it reuses that DTO.
     */
    public void getWallet(ApiCallback<com.anokix.traderapp.network.dto.TraderDashboardData.Wallet> cb) {
        getWallet(false, cb);
    }

    /**
     * As {@link #getWallet(ApiCallback)}, but {@code refresh} sends {@code refresh=1} to
     * bypass the server's short-lived balance cache — what the manual refresh button on
     * the Wallet screen wants, so a trader who has just been paid sees it immediately.
     */
    public void getWallet(boolean refresh,
                          ApiCallback<com.anokix.traderapp.network.dto.TraderDashboardData.Wallet> cb) {
        Map<String, String> q = null;
        if (refresh) {
            q = new HashMap<>();
            q.put("refresh", "1");
        }
        getAuthed("api/common/wallet", q,
                com.anokix.traderapp.network.dto.TraderDashboardData.Wallet.class, cb);
    }

    // ---- Wallet: top-ups + debit orders ----------------------------------

    /**
     * Start a PayCloud Hosted Checkout to fund the wallet. The response carries the
     * {@code pay_url} to open in a browser and the {@code merchant_order_no} to poll
     * {@link #getTopupStatus} with afterwards.
     *
     * {@code returnUrl} is where PayCloud sends the trader once the checkout page is
     * finished with.
     */
    public void createTopup(String amount, String returnUrl, ApiCallback<TopupData.Create> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("amount", amount == null ? "" : amount);
        form.put("portal", "trader");
        if (returnUrl != null && !returnUrl.isEmpty()) form.put("return_url", returnUrl);
        postAuthed("api/common/topup/create", form, TopupData.Create.class, cb);
    }

    /**
     * Where a top-up got to. Poll this after the trader comes back from the checkout
     * page: {@code status=paid} only means the card was taken — the money is in the
     * wallet when {@code credit_status=credited}.
     */
    public void getTopupStatus(String merchantOrderNo, ApiCallback<TopupData.Status> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("merchant_order_no", merchantOrderNo == null ? "" : merchantOrderNo);
        getAuthed("api/common/topup/status", q, TopupData.Status.class, cb);
    }

    /** Debit orders standing against the trader's own wallet, plus the create-form limits. */
    public void getDebitRequests(ApiCallback<DebitRequestsData> cb) {
        getAuthed("api/common/debit-requests", null, DebitRequestsData.class, cb);
    }

    /**
     * Authorise IMB to debit the trader's own wallet. {@code onceOff} true collects once
     * and both dates are ignored; false is a monthly recurring debit and BOTH dates are
     * then required ("yyyy-MM-dd") — IMB debits monthly from {@code nextDebitDate} up to
     * and including {@code finalDebitDate}.
     *
     * The account debited is resolved server-side from the caller's wallet, so nothing
     * here identifies an account.
     */
    public void createDebitRequest(String amount, String reference, boolean onceOff,
                                   String nextDebitDate, String finalDebitDate,
                                   ApiCallback<DebitRequestsData.DebitRequest> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("amount", amount == null ? "" : amount);
        form.put("reference", reference == null ? "" : reference);
        form.put("once_off", onceOff ? "true" : "false");
        form.put("portal", "trader");
        if (!onceOff) {
            form.put("next_debit_date", nextDebitDate == null ? "" : nextDebitDate);
            form.put("final_debit_date", finalDebitDate == null ? "" : finalDebitDate);
        }
        postAuthed("api/common/debit-requests", form, DebitRequestsData.DebitRequest.class, cb);
    }

    public void getStores(String keyword, ApiCallback<StoresData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("offset", "0");
        q.put("page_size", "50");
        q.put("keyword", keyword == null ? "" : keyword);
        getAuthed("api/distributor/stores/all", q, StoresData.class, cb);
    }

    public void getTraders(String search, ApiCallback<TradersData> cb) {
        getTraders(search, "", "", cb);
    }

    public void getTraders(String search, String status, String registrationStatus,
                           ApiCallback<TradersData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("search", search == null ? "" : search);
        q.put("status", status == null ? "" : status);
        q.put("registration_status", registrationStatus == null ? "" : registrationStatus);
        q.put("credit_status", "");
        getAuthed("api/distributor/traders", q, TradersData.class, cb);
    }

    public void getProducts(ApiCallback<ProductsData> cb) {
        getAuthed("api/distributor/products", null, ProductsData.class, cb);
    }

    /** Promotions/campaigns list + KPI summary. Pass "" for all campaign types. */
    public void getPromotions(String campaignType, ApiCallback<PromotionsData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("campaign_type", campaignType == null ? "" : campaignType);
        getAuthed("api/distributor/promotions", q, PromotionsData.class, cb);
    }

    /** Create a campaign (multipart: text fields + optional banner image / video). */
    public void createPromotion(Map<String, String> form, List<Http.FilePart> files,
                                ApiCallback<PromotionDetailData> cb) {
        io.execute(() -> {
            Http.Result r = Http.postMultipart(BASE_URL, "api/distributor/promotions/create",
                    form, files, session.getToken());
            deliver(r, PromotionDetailData.class, cb);
        });
    }

    /** Full campaign detail (+ selected_products, media, analytics) for the edit/view flow. */
    public void getPromotionById(String id, ApiCallback<PromotionDetailData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("id", id == null ? "" : id);
        getAuthed("api/distributor/promotions/get-by-id", q, PromotionDetailData.class, cb);
    }

    /** Update an existing campaign (partial — only the sent fields change). */
    public void updatePromotion(Map<String, String> form, List<Http.FilePart> files,
                                ApiCallback<PromotionDetailData> cb) {
        io.execute(() -> {
            Http.Result r = Http.postMultipart(BASE_URL, "api/distributor/promotions/update",
                    form, files, session.getToken());
            deliver(r, PromotionDetailData.class, cb);
        });
    }

    /** Upload / replace the banner and/or video for an existing campaign. */
    public void uploadPromotionMedia(String id, List<Http.FilePart> files,
                                     ApiCallback<PromotionDetailData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("id", id == null ? "" : id);
        io.execute(() -> {
            Http.Result r = Http.postMultipart(BASE_URL, "api/distributor/promotions/upload-media",
                    form, files, session.getToken());
            deliver(r, PromotionDetailData.class, cb);
        });
    }

    public void deletePromotion(String id, ApiCallback<PromotionDetailData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("id", id == null ? "" : id);
        io.execute(() -> {
            Http.Result r = Http.postForm(BASE_URL, "api/distributor/promotions/delete",
                    form, session.getToken());
            // The delete endpoint returns "data": [] (an empty list); only status/message matter.
            deliverStatusOnly(r, cb);
        });
    }

    public void getProductById(String id, ApiCallback<ProductDetailData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("id", id == null ? "" : id);
        getAuthed("api/distributor/products/get-by-id", q, ProductDetailData.class, cb);
    }

    /** Create a product (multipart: text fields + primary image, additional images, datasheet). */
    public void createProduct(Map<String, String> form, List<Http.FilePart> files,
                              ApiCallback<ProductDetailData> cb) {
        io.execute(() -> {
            Http.Result r = Http.postMultipart(BASE_URL, "api/distributor/products/create",
                    form, files, session.getToken());
            deliver(r, ProductDetailData.class, cb);
        });
    }

    /** Update an existing product (partial — only the sent fields change). */
    public void updateProduct(Map<String, String> form, List<Http.FilePart> files,
                              ApiCallback<ProductDetailData> cb) {
        io.execute(() -> {
            Http.Result r = Http.postMultipart(BASE_URL, "api/distributor/products/update",
                    form, files, session.getToken());
            deliver(r, ProductDetailData.class, cb);
        });
    }

    public void deleteProduct(String id, ApiCallback<ProductDetailData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("id", id == null ? "" : id);
        io.execute(() -> {
            Http.Result r = Http.postForm(BASE_URL, "api/distributor/products/delete",
                    form, session.getToken());
            // The delete endpoint returns "data": [] (an empty list), so it must not be
            // parsed into a ProductDetailData object — only the status/message matter.
            deliverStatusOnly(r, cb);
        });
    }

    public void getInventorySummary(ApiCallback<InventorySummaryData> cb) {
        getAuthed("api/distributor/inventory/summary", null, InventorySummaryData.class, cb);
    }

    public void getBaseInfo(ApiCallback<BaseInfoData> cb) {
        getAuthed("api/common/base-info", null, BaseInfoData.class, cb);
    }

    // ---- Settings (account / preferences / security) --------------------

    /** Display + display-format preferences (api/common/preferences). */
    public void getPreferences(ApiCallback<PreferencesData> cb) {
        getAuthed("api/common/preferences", null, PreferencesData.class, cb);
    }

    /** Read-only business profile incl. documents (api/trader/business/profile). */
    public void getBusinessProfile(ApiCallback<BusinessProfileData> cb) {
        getAuthed("api/trader/business/profile", null, BusinessProfileData.class, cb);
    }

    /**
     * Partial business-profile update (api/trader/business/profile/update, multipart).
     * Send only the fields/files that changed — the backend updates just those.
     * Field names match the trader register payload.
     */
    public void updateBusinessProfile(Map<String, String> form, List<Http.FilePart> files,
                                      ApiCallback<Void> cb) {
        io.execute(() -> deliverStatusOnly(
                Http.postMultipart(BASE_URL, "api/trader/business/profile/update",
                        form, files, session.getToken()), cb));
    }

    /** Update the signed-in user's personal profile (api/common/profile/update). */
    public void updateProfile(String firstName, String lastName, String email, String phone,
                              ApiCallback<Void> cb) {
        String body;
        try {
            body = new org.json.JSONObject()
                    .put("first_name", firstName == null ? "" : firstName)
                    .put("last_name", lastName == null ? "" : lastName)
                    .put("email", email == null ? "" : email)
                    .put("phone_number", phone == null ? "" : phone)
                    .toString();
        } catch (Exception e) {
            body = "{}";
        }
        final String json = body;
        io.execute(() -> deliverStatusOnly(
                Http.postJson(BASE_URL, "api/common/profile/update", json, session.getToken()), cb));
    }

    /**
     * Save the regional preferences only (api/common/preferences/update) — the
     * Preferences screen. Mirrors the portal: sends just language/currency/timezone/date_format.
     */
    public void updatePreferenceValues(String language, String currency, String timezone,
                                       String dateFormat, String vasPaymentMode,
                                       ApiCallback<Void> cb) {
        String body;
        try {
            body = new org.json.JSONObject()
                    .put("language", language == null ? "" : language)
                    .put("currency", currency == null ? "" : currency)
                    .put("timezone", timezone == null ? "" : timezone)
                    .put("date_format", dateFormat == null ? "" : dateFormat)
                    .put("vas_payment_mode", vasPaymentMode == null ? "" : vasPaymentMode)
                    .toString();
        } catch (Exception e) {
            body = "{}";
        }
        final String json = body;
        io.execute(() -> deliverStatusOnly(
                Http.postJson(BASE_URL, "api/common/preferences/update", json, session.getToken()), cb));
    }

    /**
     * Save the notification toggles only (api/common/preferences/update) — the
     * Notifications screen. Sends just the nested {@code notifications} object.
     */
    public void updateNotifications(org.json.JSONObject notifications, ApiCallback<Void> cb) {
        String body;
        try {
            body = new org.json.JSONObject()
                    .put("notifications", notifications == null ? new org.json.JSONObject() : notifications)
                    .toString();
        } catch (Exception e) {
            body = "{}";
        }
        final String json = body;
        io.execute(() -> deliverStatusOnly(
                Http.postJson(BASE_URL, "api/common/preferences/update", json, session.getToken()), cb));
    }

    /** Change the account password (api/common/profile/password). */
    public void changePassword(String currentPassword, String newPassword, ApiCallback<Void> cb) {
        String body;
        try {
            body = new org.json.JSONObject()
                    .put("current_password", currentPassword == null ? "" : currentPassword)
                    .put("new_password", newPassword == null ? "" : newPassword)
                    .toString();
        } catch (Exception e) {
            body = "{}";
        }
        final String json = body;
        io.execute(() -> deliverStatusOnly(
                Http.postJson(BASE_URL, "api/common/profile/password", json, session.getToken()), cb));
    }

    /** Lookup data for the Create Trader wizard (categories, distributors, trader types). */
    public void getReference(ApiCallback<ReferenceData> cb) {
        getAuthed("api/common/reference", null, ReferenceData.class, cb);
    }

    /** Google Places autocomplete (proxied) for the business address search. */
    public void getAddressAutocomplete(String keyword, ApiCallback<AutocompleteData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("keyword", keyword == null ? "" : keyword);
        q.put("str_countries", "country:ZA");
        q.put("search_type", "address");
        getAuthed("api/common/google-auto-complete", q, AutocompleteData.class, cb);
    }

    /** Resolve a place_id from autocomplete into latitude/longitude + formatted address. */
    public void getCoordinateFromPlaceId(String placeId, ApiCallback<CoordinateData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("place_id", placeId == null ? "" : placeId);
        getAuthed("api/common/get-coordinate-from-placeid", q, CoordinateData.class, cb);
    }

    /**
     * Public self-registration as a trader (multipart: owner/business text fields,
     * company logo, and KYC documents). No auth token required.
     */
    public void registerTrader(Map<String, String> form, List<Http.FilePart> files,
                               ApiCallback<RegisterTraderData> cb) {
        io.execute(() -> {
            Http.Result r = Http.postMultipart(BASE_URL, "api/trader/register",
                    form, files, null);
            deliver(r, RegisterTraderData.class, cb);
        });
    }

    /** Create a trader under the logged-in distributor (multipart, with KYC documents). */
    public void createTrader(Map<String, String> form, List<Http.FilePart> files,
                             ApiCallback<CreateTraderData> cb) {
        io.execute(() -> {
            Http.Result r = Http.postMultipart(BASE_URL, "api/distributor/traders/create",
                    form, files, session.getToken());
            deliver(r, CreateTraderData.class, cb);
        });
    }

    // ---- Trader marketplace & cart --------------------------------------

    /** Marketplace page for the trader. Pass null/empty to use the default distributor. */
    public void getMarketplace(String distributorId, ApiCallback<MarketplaceData> cb) {
        Map<String, String> q = new HashMap<>();
        if (distributorId != null && !distributorId.isEmpty()) {
            q.put("distributor_id", distributorId);
        }
        getAuthed("api/trader/marketplace", q, MarketplaceData.class, cb);
    }

    /**
     * Category detail page (api/trader/marketplace/category): filtered/sorted/paged
     * products for one category plus filter facets. All arguments except
     * {@code categoryId} are optional; pass null/empty to omit. Comma-separated
     * lists (brands / sub-categories / pack sizes) are passed straight through.
     */
    public void getCategoryPage(String categoryId, String distributorId, String brands,
                                String subCategories, String packSizes, String minPrice,
                                String maxPrice, boolean onPromotion, boolean inStock,
                                String search, String sortBy, int page, int perPage,
                                ApiCallback<CategoryPageData> cb) {
        Map<String, String> q = new HashMap<>();
        if (categoryId != null && !categoryId.isEmpty()) q.put("category_id", categoryId);
        if (distributorId != null && !distributorId.isEmpty()) q.put("distributor_id", distributorId);
        if (brands != null && !brands.isEmpty()) q.put("brands", brands);
        if (subCategories != null && !subCategories.isEmpty()) q.put("sub_categories", subCategories);
        if (packSizes != null && !packSizes.isEmpty()) q.put("pack_sizes", packSizes);
        if (minPrice != null && !minPrice.isEmpty()) q.put("min_price", minPrice);
        if (maxPrice != null && !maxPrice.isEmpty()) q.put("max_price", maxPrice);
        if (onPromotion) q.put("on_promotion", "1");
        if (inStock) q.put("in_stock", "1");
        if (search != null && !search.trim().isEmpty()) q.put("search", search.trim());
        q.put("sort_by", sortBy != null && !sortBy.isEmpty() ? sortBy : "popularity");
        q.put("page", String.valueOf(page));
        q.put("per_page", String.valueOf(perPage));
        getAuthed("api/trader/marketplace/category", q, CategoryPageData.class, cb);
    }

    /**
     * Titled, paginated product collection (api/trader/marketplace/collection),
     * e.g. type=recommended / best_sellers — opened from a Marketplace "View All".
     */
    public void getCollection(String type, int page, int perPage, ApiCallback<CollectionData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("type", type);
        q.put("page", String.valueOf(page));
        q.put("per_page", String.valueOf(perPage));
        getAuthed("api/trader/marketplace/collection", q, CollectionData.class, cb);
    }

    /**
     * Titled, paginated "Current Promotions" list (api/trader/marketplace/promotions),
     * opened from the Marketplace "Current Promotions" View All.
     */
    public void getPromotions(int page, int perPage, ApiCallback<PromotionCollectionData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("page", String.valueOf(page));
        q.put("per_page", String.valueOf(perPage));
        getAuthed("api/trader/marketplace/promotions", q, PromotionCollectionData.class, cb);
    }

    /** Full product detail used by the Add to Cart screen. */
    public void getCommonProduct(String id, ApiCallback<CommonProductData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("id", id == null ? "" : id);
        getAuthed("api/common/product-detail", q, CommonProductData.class, cb);
    }

    public void getCart(ApiCallback<CartData> cb) {
        getAuthed("api/trader/cart", null, CartData.class, cb);
    }

    public void addToCart(String productId, int quantity, double unitPrice,
                          String barcode, String sku, ApiCallback<CartData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("product_id", productId == null ? "" : productId);
        form.put("quantity", String.valueOf(quantity));
        form.put("unit_price", String.format(java.util.Locale.US, "%.2f", unitPrice));
        form.put("barcode", barcode == null ? "" : barcode);
        form.put("sku", sku == null ? "" : sku);
        io.execute(() -> {
            Http.Result r = Http.postForm(BASE_URL, "api/trader/cart/add", form, session.getToken());
            deliverStatusOnly(r, cb);
        });
    }

    public void updateCartItem(String cartItemId, int quantity, double unitPrice,
                               ApiCallback<CartData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("cart_item_id", cartItemId == null ? "" : cartItemId);
        form.put("quantity", String.valueOf(quantity));
        form.put("unit_price", String.format(java.util.Locale.US, "%.2f", unitPrice));
        io.execute(() -> {
            Http.Result r = Http.postForm(BASE_URL, "api/trader/cart/update", form, session.getToken());
            deliverStatusOnly(r, cb);
        });
    }

    /** Soft-cancel a cart line (api/trader/cart/remove). */
    public void removeCartItem(String cartItemId, ApiCallback<CartData> cb) {
        cartItemAction("api/trader/cart/remove", cartItemId, cb);
    }

    /** Hard-delete a cart line (api/trader/cart/delete). */
    public void deleteCartItem(String cartItemId, ApiCallback<CartData> cb) {
        cartItemAction("api/trader/cart/delete", cartItemId, cb);
    }

    private void cartItemAction(String path, String cartItemId, ApiCallback<CartData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("cart_item_id", cartItemId == null ? "" : cartItemId);
        io.execute(() -> {
            Http.Result r = Http.postForm(BASE_URL, path, form, session.getToken());
            deliverStatusOnly(r, cb);
        });
    }

    /**
     * Place an order (api/trader/orders/create) from the selected cart lines.
     *
     * @param distributorId    distributor the selected lines belong to
     * @param cartItemIds      JSON array of cart_item_ids to convert into the order
     * @param deliveryDate     chosen slot date, "yyyy-MM-dd"
     * @param startTime        slot start, "HH:mm:ss"
     * @param endTime          slot end, "HH:mm:ss"
     * @param notes            optional special instructions
     * @param deliveryAddress  formatted delivery address
     * @param paymentMethod    chosen payment-method key (wallet/credit/card/bank)
     *
     * The {@code data} block (orders[] + cart{}) is not modelled; only the
     * status/message decide success, so it is parsed status-only.
     */
    public void createOrder(String distributorId, String cartItemIds, String deliveryDate,
                            String startTime, String endTime, String notes,
                            String deliveryAddress, String paymentMethod,
                            ApiCallback<Void> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("distributor_id", distributorId == null ? "" : distributorId);
        form.put("cart_item_ids", cartItemIds == null ? "" : cartItemIds);
        form.put("delivery_date", deliveryDate == null ? "" : deliveryDate);
        form.put("delivery_start_time", startTime == null ? "" : startTime);
        form.put("delivery_end_time", endTime == null ? "" : endTime);
        form.put("notes", notes == null ? "" : notes);
        form.put("delivery_address", deliveryAddress == null ? "" : deliveryAddress);
        form.put("payment_method", paymentMethod == null ? "" : paymentMethod);
        io.execute(() -> {
            Http.Result r = Http.postForm(BASE_URL, "api/trader/orders/create", form, session.getToken());
            deliverStatusOnly(r, cb);
        });
    }

    // ---- Trader orders --------------------------------------------------

    /**
     * List the trader's orders (api/trader/orders). All filters are optional;
     * pass null/empty to omit. {@code status} of {@code "all"} is omitted so the
     * server returns every status.
     */
    public void getOrders(String distributorId, String search, String dateFrom, String dateTo,
                          String status, int page, int perPage, ApiCallback<OrdersData> cb) {
        Map<String, String> q = new HashMap<>();
        if (distributorId != null && !distributorId.isEmpty()) q.put("distributor_id", distributorId);
        if (search != null && !search.trim().isEmpty()) q.put("search", search.trim());
        if (dateFrom != null && !dateFrom.isEmpty()) q.put("date_from", dateFrom);
        if (dateTo != null && !dateTo.isEmpty()) q.put("date_to", dateTo);
        if (status != null && !status.isEmpty() && !"all".equals(status)) q.put("status", status);
        q.put("page", String.valueOf(page));
        q.put("per_page", String.valueOf(perPage));
        getAuthed("api/trader/orders", q, OrdersData.class, cb);
    }

    /** Full detail for one order (api/trader/orders?id=) — includes the items[] list. */
    public void getOrderById(String id, ApiCallback<OrderDetailData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("id", id == null ? "" : id);
        getAuthed("api/trader/orders", q, OrderDetailData.class, cb);
    }

    /** Purchase-order PDF (api/trader/orders/{id}/pdf) — { filename, mime, base64 data }. */
    public void getOrderPdf(String id, ApiCallback<GrvPdfData> cb) {
        getAuthed("api/trader/orders/" + id + "/pdf", null, GrvPdfData.class, cb);
    }

    // ---- Trader Goods Received (GRV) ------------------------------------

    /** List the trader's Goods Received Vouchers + KPI summary (api/trader/grvs). */
    public void getGrvs(ApiCallback<GrvListData> cb) {
        getAuthed("api/trader/grvs", null, GrvListData.class, cb);
    }

    /** Single GRV with its line items (api/trader/grvs/{id}). */
    public void getGrv(String id, ApiCallback<GrvDetailData> cb) {
        getAuthed("api/trader/grvs/" + id, null, GrvDetailData.class, cb);
    }

    /**
     * Confirm receipt of a GRV (api/trader/grvs/{id}/confirm). Supports full or
     * partial receive; the accepted quantity is added to inventory server-side.
     * {@code jsonBody} is the raw JSON: {@code {"items":[{"grv_item_id":..,
     * "received_quantity":..,"damaged_quantity":..}],"note":".."}}.
     */
    public void confirmGrv(String id, String jsonBody, ApiCallback<GrvDetailData> cb) {
        io.execute(() -> {
            Http.Result r = Http.postJson(BASE_URL, "api/trader/grvs/" + id + "/confirm",
                    jsonBody, session.getToken());
            deliver(r, GrvDetailData.class, cb);
        });
    }

    /** Generated GRV PDF (api/trader/grvs/{id}/pdf) — { filename, mime, base64 data }. */
    public void getGrvPdf(String id, ApiCallback<GrvPdfData> cb) {
        getAuthed("api/trader/grvs/" + id + "/pdf", null, GrvPdfData.class, cb);
    }

    // ---- Trader Invoices ------------------------------------------------

    /** List the trader's invoices (api/trader/invoices). */
    public void getInvoices(ApiCallback<InvoiceListData> cb) {
        getAuthed("api/trader/invoices", null, InvoiceListData.class, cb);
    }

    /** Generated invoice PDF (api/trader/invoices/{id}/pdf) — { filename, mime, base64 data }. */
    public void getInvoicePdf(String id, ApiCallback<InvoicePdfData> cb) {
        getAuthed("api/trader/invoices/" + id + "/pdf", null, InvoicePdfData.class, cb);
    }

    // ---- Trader Finances ------------------------------------------------

    /** The trader's anokiX wallet ledger + KPI summary (api/trader/finances). */
    public void getFinances(ApiCallback<com.anokix.traderapp.network.dto.FinancesData> cb) {
        getAuthed("api/trader/finances", null,
                com.anokix.traderapp.network.dto.FinancesData.class, cb);
    }

    // ---- Notifications (common) -----------------------------------------

    /** Live unread-notification count (api/common/notifications/unread-count). */
    public void getUnreadCount(ApiCallback<UnreadCountData> cb) {
        getAuthed("api/common/notifications/unread-count", null, UnreadCountData.class, cb);
    }

    /**
     * Notification feed (api/common/notifications). {@code filter} is "all" (omitted)
     * or "unread"; paged by {@code page}/{@code perPage}.
     */
    public void getNotifications(String filter, int page, int perPage,
                                ApiCallback<NotificationsData> cb) {
        Map<String, String> q = new HashMap<>();
        if (filter != null && !filter.isEmpty() && !"all".equals(filter)) q.put("filter", filter);
        q.put("page", String.valueOf(page));
        q.put("per_page", String.valueOf(perPage));
        getAuthed("api/common/notifications", q, NotificationsData.class, cb);
    }

    /** Mark one notification read (api/common/notifications/read, id=). */
    public void markNotificationRead(String id, ApiCallback<Void> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("id", id == null ? "" : id);
        io.execute(() -> {
            Http.Result r = Http.postForm(BASE_URL, "api/common/notifications/read", form, session.getToken());
            deliverStatusOnly(r, cb);
        });
    }

    /** Mark every notification read (api/common/notifications/read, all=true). */
    public void markAllNotificationsRead(ApiCallback<Void> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("all", "true");
        io.execute(() -> {
            Http.Result r = Http.postForm(BASE_URL, "api/common/notifications/read", form, session.getToken());
            deliverStatusOnly(r, cb);
        });
    }

    // ---- Push / device tokens (FCM) -------------------------------------

    /**
     * Register this device's FCM token with the backend so the server can target it
     * with pushes (api/common/device-tokens). Best-effort: only status/message matter,
     * and a failure (e.g. the endpoint not yet built) is surfaced via {@code onError}
     * but never crashes the app. {@code platform} is "android".
     */
    public void registerDeviceToken(String token, String deviceId, ApiCallback<Void> cb) {
        // Field is `device_token` (not `token` — that key is reserved by the API gateway).
        Map<String, String> form = new HashMap<>();
        form.put("device_token", token == null ? "" : token);
        form.put("platform", "android");
        io.execute(() -> deliverStatusOnly(
                Http.postForm(BASE_URL, "api/common/device-tokens", form, session.getToken()), cb));
    }

    /** Remove this device's FCM token on logout (api/common/device-tokens/delete). */
    public void unregisterDeviceToken(String token, ApiCallback<Void> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("device_token", token == null ? "" : token);
        io.execute(() -> deliverStatusOnly(
                Http.postForm(BASE_URL, "api/common/device-tokens/delete", form, session.getToken()), cb));
    }

    // ---- Trader Distributors --------------------------------------------

    /** List the distributors the trader is partnered with (api/trader/distributors). */
    public void getDistributors(ApiCallback<DistributorListData> cb) {
        getAuthed("api/trader/distributors", null, DistributorListData.class, cb);
    }

    /**
     * Request to change the trader's distributor (api/trader/distributor/change-request).
     * Sends {@code current_distributor_id} + an optional {@code reason}; the request goes
     * to the back office for review. Only status/message decide success.
     */
    public void requestDistributorChange(String currentDistributorId, String reason,
                                         ApiCallback<Void> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("current_distributor_id", currentDistributorId == null ? "" : currentDistributorId);
        form.put("reason", reason == null ? "" : reason);
        io.execute(() -> {
            Http.Result r = Http.postForm(BASE_URL, "api/trader/distributor/change-request",
                    form, session.getToken());
            deliverStatusOnly(r, cb);
        });
    }

    // ---- Trader Inventory -----------------------------------------------

    /** Live inventory dashboard: summary, chart, fast movers, rows, movements. */
    public void getInventory(ApiCallback<InventoryData> cb) {
        getAuthed("api/trader/inventory", null, InventoryData.class, cb);
    }

    /**
     * Adjust a product's stock (api/trader/inventory/adjust). {@code quantity} is
     * signed: positive adds, negative reduces. Returns status-only.
     */
    public void adjustInventory(int productId, int quantity, String reason, ApiCallback<Void> cb) {
        String body;
        try {
            body = new org.json.JSONObject()
                    .put("product_id", productId)
                    .put("quantity", quantity)
                    .put("reason", reason == null ? "" : reason)
                    .toString();
        } catch (Exception e) {
            body = "{}";
        }
        final String json = body;
        io.execute(() -> {
            Http.Result r = Http.postJson(BASE_URL, "api/trader/inventory/adjust", json, session.getToken());
            deliverStatusOnly(r, cb);
        });
    }

    /** One product's snapshot + full movement ledger (api/trader/inventory/history). */
    public void getInventoryHistory(int productId, ApiCallback<InventoryHistoryData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("product_id", String.valueOf(productId));
        getAuthed("api/trader/inventory/history", q, InventoryHistoryData.class, cb);
    }

    /** Inventory analytics: ageing buckets, gross profit, reorder suggestions. */
    public void getInventoryAnalytics(int days, ApiCallback<InventoryAnalyticsData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("days", String.valueOf(days));
        getAuthed("api/trader/inventory/analytics", q, InventoryAnalyticsData.class, cb);
    }

    // ---- Trader Goods Returns (GRN) -------------------------------------

    /** List the trader's Goods Returns + KPI summary (api/trader/returns). */
    public void getReturns(ApiCallback<ReturnsListData> cb) {
        getAuthed("api/trader/returns", null, ReturnsListData.class, cb);
    }

    /**
     * Submit a goods return (api/trader/returns). {@code jsonBody} is the raw JSON:
     * {@code {"items":[{"product_id":..,"quantity":..,"reason":".."}],"reason":"..",
     * "note":"..","source_grv_id":".."}}. Reduces stock immediately; the distributor
     * reviews and may issue a credit note. The {@code data} block isn't modelled —
     * only status/message decide success.
     */
    public void createReturn(String jsonBody, ApiCallback<Void> cb) {
        io.execute(() -> {
            Http.Result r = Http.postJson(BASE_URL, "api/trader/returns", jsonBody, session.getToken());
            deliverStatusOnly(r, cb);
        });
    }

    // ---- Trader POS (Pagamio) -------------------------------------------

    /** Sellable POS products (on-hand, VAT-inclusive price, image) — api/trader/pos/products. */
    public void getPosProducts(ApiCallback<PosProductsData> cb) {
        getAuthed("api/trader/pos/products", null, PosProductsData.class, cb);
    }

    /**
     * Complete a POS sale (api/trader/pos/sale, JSON). {@code itemsJson} is the
     * {@code items} array string ({@code [{"product_id":..,"quantity":..,"unit_price":..}]});
     * {@code paymentMethod} is one of cash|wallet|qr — card is taken on a terminal
     * instead, via {@link #sendPosPayment}. Reduces stock and returns the receipt;
     * VAT is extracted from the inclusive total server-side.
     */
    public void createPosSale(String itemsJson, String paymentMethod, double discount,
                              ApiCallback<PosSaleData> cb) {
        String body;
        try {
            body = "{\"items\":" + (itemsJson == null ? "[]" : itemsJson)
                    + ",\"payment_method\":\"" + (paymentMethod == null ? "cash" : paymentMethod) + "\""
                    + ",\"discount\":" + discount + "}";
        } catch (Exception e) {
            body = "{}";
        }
        final String json = body;
        io.execute(() -> {
            Http.Result r = Http.postJson(BASE_URL, "api/trader/pos/sale", json, session.getToken());
            deliver(r, PosSaleData.class, cb);
        });
    }

    /**
     * The trader's card machines (PayCloud terminals) — api/common/pos/terminals.
     * Read when a POS sale is being paid by card, so the cashier can pick which
     * machine the amount goes to.
     */
    public void getPosTerminals(ApiCallback<PosTerminalsData> cb) {
        getAuthed("api/common/pos/terminals", null, PosTerminalsData.class, cb);
    }

    /**
     * Push an amount to a card machine (api/common/pos/payments, JSON). The backend
     * relays it to the terminal, where the customer taps or inserts their card; the
     * sale itself is recorded server-side once the gateway reports the result, so
     * this call deliberately does <b>not</b> go through {@code api/trader/pos/sale}.
     *
     * <p>Only status/message are read back: a failure carries the gateway's own
     * wording (e.g. "[E07507]The device is not turned on…"), which is what the cashier
     * needs to see, and the success payload is not needed to move the till on.
     */
    public void sendPosPayment(double amount, int terminalId, String description,
                               String referenceType, ApiCallback<Void> cb) {
        String body;
        try {
            body = new org.json.JSONObject()
                    // Two decimals: the machine must be asked for exactly what the
                    // till showed, not a binary-floating-point neighbour of it.
                    .put("amount", Math.round(amount * 100d) / 100d)
                    .put("terminal_id", terminalId)
                    .put("description", description == null ? "" : description)
                    .put("reference_type", referenceType == null ? "" : referenceType)
                    .toString();
        } catch (Exception e) {
            cb.onError("Could not send the payment to the card machine.");
            return;
        }
        final String json = body;
        io.execute(() -> deliverStatusOnly(
                Http.postJson(BASE_URL, "api/common/pos/payments", json, session.getToken()), cb));
    }

    // ---- Card payment on this terminal (PayCloud same-terminal intent) ------

    /**
     * Step 1 of a card payment taken on the POS terminal the app is running on:
     * POST api/common/pos/intent/prepare (JSON). The server creates the order, fixes
     * the amount, and returns the WiseCashier Intent (action + extras) ready to launch
     * — see {@link com.anokix.traderapp.pos.WiseCashier#buildIntent}. The scenario is
     * always CARD here; {@code description} is optional.
     */
    public void preparePosIntent(double amount, String description,
                                 ApiCallback<PosIntentData.Prepare> cb) {
        String body;
        try {
            org.json.JSONObject o = new org.json.JSONObject()
                    // Two decimals, as for the card-machine push: the server turns this
                    // into cents, so hand it exactly what the till showed.
                    .put("amount", Math.round(amount * 100d) / 100d)
                    .put("payment_scenario", "CARD");
            if (description != null && !description.isEmpty()) {
                o.put("description", description);
            }
            body = o.toString();
        } catch (Exception e) {
            cb.onError("Could not start the card payment.");
            return;
        }
        final String json = body;
        io.execute(() -> deliver(
                Http.postJson(BASE_URL, "api/common/pos/intent/prepare", json, session.getToken()),
                PosIntentData.Prepare.class, cb));
    }

    /**
     * Step 2: POST api/common/pos/intent/result (JSON) — WiseCashier's answer, exactly
     * as {@code onActivityResult} delivered it ({@code transData} goes through as the
     * raw string). The backend classifies the code and checks approvals with PayCloud.
     * Sending the same result twice is safe, so a failed send can simply be retried.
     */
    public void reportPosIntentResult(String businessOrderNo, String result, String resultMsg,
                                      String transData, ApiCallback<PosIntentData.Result> cb) {
        String body;
        try {
            body = new org.json.JSONObject()
                    .put("business_order_no", businessOrderNo == null ? "" : businessOrderNo)
                    .put("result", result == null ? "" : result)
                    .put("result_msg", resultMsg == null ? "" : resultMsg)
                    .put("trans_data", transData == null ? "" : transData)
                    .toString();
        } catch (Exception e) {
            cb.onError("Could not record the card payment result.");
            return;
        }
        final String json = body;
        io.execute(() -> deliver(
                Http.postJson(BASE_URL, "api/common/pos/intent/result", json, session.getToken()),
                PosIntentData.Result.class, cb));
    }

    /**
     * Step 3, only when the answer was not final: GET api/common/pos/payments/status
     * for the order, polled until it is no longer open.
     */
    public void getPosPaymentStatus(String merchantOrderNo, ApiCallback<PosIntentData.Status> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("merchant_order_no", merchantOrderNo == null ? "" : merchantOrderNo);
        getAuthed("api/common/pos/payments/status", q, PosIntentData.Status.class, cb);
    }

    /** POS sales history + summary (api/trader/pos/sales). Filters are optional. */
    public void getPosSales(String dateFrom, String dateTo, int page, int perPage,
                            ApiCallback<PosSalesData> cb) {
        Map<String, String> q = new HashMap<>();
        if (dateFrom != null && !dateFrom.isEmpty()) q.put("date_from", dateFrom);
        if (dateTo != null && !dateTo.isEmpty()) q.put("date_to", dateTo);
        q.put("page", String.valueOf(page));
        q.put("per_page", String.valueOf(perPage));
        getAuthed("api/trader/pos/sales", q, PosSalesData.class, cb);
    }

    public void getStore(String id, ApiCallback<StoreDetailData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("id", id);
        getAuthed("api/distributor/stores", q, StoreDetailData.class, cb);
    }

    public void createStore(Map<String, String> form, ApiCallback<StoreDetailData> cb) {
        postAuthed("api/distributor/stores/create", form, StoreDetailData.class, cb);
    }

    public void updateStore(Map<String, String> form, ApiCallback<StoreDetailData> cb) {
        postAuthed("api/distributor/stores/update", form, StoreDetailData.class, cb);
    }

    // ---- Airtime & VAS (Limes, common) ----------------------------------

    /**
     * VAS dashboard: recent transactions, KPI summary (spend/count today, commission this
     * month, wallet balance) and pagination (api/common/vas/transactions).
     */
    public void getVasTransactions(int page, int perPage, ApiCallback<VasTransactionsData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("page", String.valueOf(page));
        q.put("per_page", String.valueOf(perPage));
        getAuthed("api/common/vas/transactions", q, VasTransactionsData.class, cb);
    }

    /**
     * The Limes VAS category tree (api/common/vas/categories). Flatten via {@code leaves()}.
     * Scoped to the customer being acted for — see {@link #selectVasCustomer}.
     */
    public void getVasCategories(String customerId, ApiCallback<VasCategoriesData> cb) {
        getAuthed("api/common/vas/categories", customerScope(customerId),
                VasCategoriesData.class, cb);
    }

    /** Purchasable products for one leaf category (api/common/vas/products). */
    public void getVasProducts(String category, int page, int limit, String customerId,
                               ApiCallback<VasProductsData> cb) {
        Map<String, String> q = customerScope(customerId);
        q.put("category", category == null ? "" : category);
        q.put("page", String.valueOf(page));
        q.put("limit", String.valueOf(limit));
        getAuthed("api/common/vas/products", q, VasProductsData.class, cb);
    }

    /**
     * Buy a VAS product (api/common/vas/purchase). Paid from the anokiX wallet; the
     * server debits and forwards to Limes. Only status/message decide success — the
     * failure path carries the network's reason (e.g. inactive subscriber).
     */
    public void purchaseVas(String productId, String msisdn, double amount, String sku,
                            String name, String category, String customerId, ApiCallback<Void> cb) {
        Map<String, String> form = customerScope(customerId);
        form.put("product_id", productId == null ? "" : productId);
        form.put("msisdn", msisdn == null ? "" : msisdn);
        form.put("amount", String.valueOf(amount));
        form.put("sku", sku == null ? "" : sku);
        form.put("name", name == null ? "" : name);
        form.put("category", category == null ? "" : category);
        // Postman collection sends earn_commission=1 so the trader accrues their VAS commission.
        form.put("earn_commission", "1");
        io.execute(() -> deliverStatusOnly(
                Http.postForm(BASE_URL, "api/common/vas/purchase", form, session.getToken()), cb));
    }

    /**
     * Register an end-customer in the Limes CRM (api/common/vas/onboard-customer). Step 1 of
     * signing someone up to a SIM subscription; upserts by ID number, so a retry reports
     * {@code already_exists} rather than creating a duplicate.
     */
    public void onboardVasCustomer(Map<String, String> form, ApiCallback<VasOnboardCustomerData> cb) {
        postAuthed("api/common/vas/onboard-customer", form, VasOnboardCustomerData.class, cb);
    }

    /**
     * Build — but do not send — the JSON body an onboard would POST to Limes
     * (api/common/vas/onboard-customer/preview). Runs no validation, so a half-filled form
     * still previews.
     */
    public void previewVasOnboard(Map<String, String> form, ApiCallback<VasOnboardPreviewData> cb) {
        postAuthed("api/common/vas/onboard-customer/preview", form, VasOnboardPreviewData.class, cb);
    }

    /**
     * End-customers this trader has onboarded (api/common/vas/customers). Pass
     * {@code activeOnly} to ask for the ACT customers only — that is the list the "Acting as"
     * picker offers, because Limes will not authenticate as a customer who is not active yet.
     */
    public void getVasCustomers(String search, int limit, boolean activeOnly,
                                ApiCallback<VasCustomersData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("search", search == null ? "" : search);
        q.put("limit", String.valueOf(limit));
        if (activeOnly) q.put("active", "1");
        getAuthed("api/common/vas/customers", q, VasCustomersData.class, cb);
    }

    /**
     * Authenticate as an onboarded customer (api/common/vas/select-customer). Limes issues its
     * token per customer, so the Subscription, Catalog &amp; orders and Dynamic services tabs
     * only work once this has been approved; every later call on those tabs carries the same
     * {@code customer_id}.
     */
    public void selectVasCustomer(String customerId, ApiCallback<VasSelectCustomerData> cb) {
        postAuthed("api/common/vas/select-customer", customerScope(customerId),
                VasSelectCustomerData.class, cb);
    }

    /** SIM subscription products offered by the "Assign a SIM" form (never hardcode these). */
    public void getVasSubscriptionProducts(String customerId,
                                           ApiCallback<VasSubscriptionProductsData> cb) {
        getAuthed("api/common/vas/subscription-products", customerScope(customerId),
                VasSubscriptionProductsData.class, cb);
    }

    /** SIMs already activated for the selected customer, with the MSISDN Limes assigned to each. */
    public void getVasSubscriptions(String customerId, ApiCallback<VasSubscriptionsData> cb) {
        getAuthed("api/common/vas/subscriptions", customerScope(customerId),
                VasSubscriptionsData.class, cb);
    }

    /**
     * Activate a SIM / create a subscription (api/common/vas/subscribe). An eSIM carries no
     * ICCID, so {@code e_sim=1} is sent instead of the serial; a physical "SIM in hand"
     * sends its ICCID and activates immediately.
     */
    public void subscribeVas(String productId, String iccid, boolean eSim, String customerId,
                             ApiCallback<Void> cb) {
        Map<String, String> form = customerScope(customerId);
        form.put("product_id", productId == null ? "" : productId);
        if (eSim) {
            form.put("e_sim", "1");
        } else {
            form.put("iccid", iccid == null ? "" : iccid);
        }
        io.execute(() -> deliverStatusOnly(
                Http.postForm(BASE_URL, "api/common/vas/subscribe", form, session.getToken()), cb));
    }

    /**
     * Provision custom/variable-value bundles straight onto a number
     * (api/common/vas/dynamic-services) — the path Limes recommends over order/create for
     * amounts the fixed catalog cannot express. Expects a JSON body, so the service lines go
     * through {@link Http#postJson}; the charge is the sum of {@code priceInCents}.
     *
     * Each entry of {@code services} carries {@code definitionCode}, {@code value},
     * {@code priceInCents} and an optional {@code expiryDate}; Gson serialises them so no
     * caller has to hand-build JSON.
     */
    public void provisionVasDynamicServices(String msisdn, List<Map<String, Object>> services,
                                            String customerId, ApiCallback<Void> cb) {
        Map<String, Object> body = new HashMap<>();
        body.put("msisdn", msisdn == null ? "" : msisdn);
        // Postman collection sends earn_commission=1 so the trader accrues their commission.
        body.put("earn_commission", 1);
        body.put("services", services == null ? new java.util.ArrayList<>() : services);
        if (customerId != null && !customerId.trim().isEmpty()) {
            body.put("customer_id", customerId.trim());
        }

        final String json = gson.toJson(body);
        io.execute(() -> deliverStatusOnly(
                Http.postJson(BASE_URL, "api/common/vas/dynamic-services", json,
                        session.getToken()), cb));
    }

    /**
     * A fresh parameter map already carrying {@code customer_id}. Limes runs these calls on the
     * customer's own token, so the id travels with every request the three customer-scoped tabs
     * make; a blank id is simply omitted rather than sent as an empty string.
     */
    private static Map<String, String> customerScope(String customerId) {
        Map<String, String> params = new HashMap<>();
        if (customerId != null && !customerId.trim().isEmpty()) {
            params.put("customer_id", customerId.trim());
        }
        return params;
    }

    // ---- Reports ---------------------------------------------------------

    /** Dashboard: catalogue of report types, already-generated reports and the KPI summary. */
    public void getReports(ApiCallback<ReportsData> cb) {
        getAuthed("api/trader/reports", null, ReportsData.class, cb);
    }

    /**
     * Build a report for a period. {@code reportKey} comes from
     * {@link ReportsData.Available#key}; both dates are "yyyy-MM-dd".
     * The envelope's message ("Till Sales ready — 2 rows.") is worth surfacing,
     * so callers get it via {@link ApiCallback#onSuccess(Object, String)}.
     */
    public void generateReport(String reportKey, String from, String to,
                               ApiCallback<ReportMutationData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("report_key", reportKey == null ? "" : reportKey);
        form.put("from", from == null ? "" : from);
        form.put("to", to == null ? "" : to);
        postAuthed("api/trader/reports/generate", form, ReportMutationData.class, cb);
    }

    /** Delete a generated report; the response carries the refreshed summary. */
    public void deleteReport(int id, ApiCallback<ReportMutationData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("id", String.valueOf(id));
        postAuthed("api/trader/reports/delete", form, ReportMutationData.class, cb);
    }

    /**
     * Fetch the report file itself. This is the one call that does not go through
     * {@link #BASE_URL}: the download service listens on its own port and takes the
     * token in the JSON body rather than an Authorization header. On failure it
     * answers with the usual JSON envelope instead of a file, so a JSON content-type
     * is treated as an error and its {@code message} is surfaced.
     */
    public void downloadReport(int id, ApiCallback<Http.BinaryResult> cb) {
        String json = "{\"id\":" + id + ",\"token\":\"" + escapeJson(session.getToken()) + "\"}";
        io.execute(() -> {
            Http.BinaryResult r = Http.postJsonForBytes(REPORT_DOWNLOAD_URL, json);
            main.post(() -> {
                if (r.ok && !r.isJson()) {
                    cb.onSuccess(r);
                } else {
                    cb.onError(downloadError(r));
                }
            });
        });
    }

    /** Pull the server's {@code message} out of a failed download, else a generic line. */
    private String downloadError(Http.BinaryResult r) {
        if (r.isJson() && r.bytes != null && r.bytes.length > 0) {
            try {
                ApiResponse<?> resp = gson.fromJson(new String(r.bytes, "UTF-8"), ApiResponse.class);
                if (resp != null && resp.message != null && !resp.message.isEmpty()) {
                    return resp.message;
                }
            } catch (Exception ignored) {
                // fall through to the generic message
            }
        }
        return "Could not download the report.";
    }

    private static String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ---- Staff -----------------------------------------------------------

    /** Everyone the trader has given till access to, plus what a staff login may reach. */
    public void getStaff(ApiCallback<StaffData> cb) {
        getAuthed("api/trader/staff", null, StaffData.class, cb);
    }

    /**
     * Add a staff member. They sign in with {@code email} + {@code password};
     * phone and job title are optional. The response is the created member.
     */
    public void addStaff(String firstName, String lastName, String email, String password,
                         String phone, String jobTitle, ApiCallback<StaffMemberData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("first_name", firstName == null ? "" : firstName);
        form.put("last_name", lastName == null ? "" : lastName);
        form.put("email", email == null ? "" : email);
        form.put("password", password == null ? "" : password);
        form.put("phone_number", phone == null ? "" : phone);
        form.put("job_title", jobTitle == null ? "" : jobTitle);
        postAuthed("api/trader/staff", form, StaffMemberData.class, cb);
    }

    /** Set a new sign-in password for a staff member (they are not emailed it). */
    public void setStaffPassword(int staffId, String password, ApiCallback<StaffMemberData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("staff_id", String.valueOf(staffId));
        form.put("password", password == null ? "" : password);
        postAuthed("api/trader/staff/password", form, StaffMemberData.class, cb);
    }

    /** Turn a staff member's sign-in on or off. {@code status} is "active" or "disabled". */
    public void updateStaffStatus(int staffId, String status, ApiCallback<StaffMemberData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("staff_id", String.valueOf(staffId));
        form.put("status", status == null ? "" : status);
        postAuthed("api/trader/staff/update", form, StaffMemberData.class, cb);
    }

    // ---- anokiX rewards (Limes) ------------------------------------------

    /**
     * The whole rewards screen in one round trip: balance, benefits, tier, the points
     * ledger (paged), the voucher catalog, promotions and the invite code.
     */
    public void getRewardsOverview(int page, int perPage, ApiCallback<RewardsOverviewData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("page", String.valueOf(Math.max(1, page)));
        q.put("per_page", String.valueOf(perPage <= 0 ? 20 : perPage));
        getAuthed("api/common/rewards/overview", q, RewardsOverviewData.class, cb);
    }

    /**
     * Spend points on a catalog voucher. Limes airtime/data fulfil live (points are
     * refunded server-side if the upstream call fails); everything else is recorded
     * pending. Fails 422 on an insufficient balance or an invalid voucher.
     *
     * {@code msisdn} is only required by vouchers whose {@code needs_msisdn} is set.
     */
    public void redeemReward(int voucherId, String msisdn,
                             ApiCallback<RewardsOverviewData.RedeemResult> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("voucher_id", String.valueOf(voucherId));
        if (msisdn != null && !msisdn.isEmpty()) form.put("msisdn", msisdn);
        postAuthed("api/common/rewards/redeem", form, RewardsOverviewData.RedeemResult.class, cb);
    }

    // ---- Support Centre --------------------------------------------------

    /** Landing screen: enabled channels, help-article taxonomy and the trader's tickets. */
    public void getSupportOverview(ApiCallback<SupportOverviewData> cb) {
        getAuthed("api/common/support/overview", null, SupportOverviewData.class, cb);
    }

    /** Free-text help-article search. */
    public void searchSupportArticles(String query, ApiCallback<SupportArticlesData> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("q", query == null ? "" : query);
        getAuthed("api/common/support/articles/search", q, SupportArticlesData.class, cb);
    }

    /**
     * Open a ticket. {@code channel} is "web" (a written request, with a priority) or
     * "callback" (a ring-me-back request, with a phone number); pass null for the field
     * that does not apply to the chosen channel.
     *
     * {@code distributorId} routes the ticket: null sends it to anokiX Support, a value
     * raises it with that distributor instead.
     */
    public void createSupportTicket(String subject, String message, String channel,
                                    String priority, String callbackPhone, String category,
                                    String distributorId, ApiCallback<SupportTicketData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("subject", subject == null ? "" : subject);
        form.put("message", message == null ? "" : message);
        form.put("channel", channel == null ? "web" : channel);
        if (priority != null && !priority.isEmpty()) form.put("priority", priority);
        if (callbackPhone != null && !callbackPhone.isEmpty()) form.put("callback_phone", callbackPhone);
        if (category != null && !category.isEmpty()) form.put("category", category);
        if (distributorId != null && !distributorId.isEmpty()) form.put("distributor_id", distributorId);
        postAuthed("api/common/support/tickets", form, SupportTicketData.class, cb);
    }

    /** One ticket with its full message thread. */
    public void getSupportTicket(int id, ApiCallback<SupportTicketData> cb) {
        getAuthed("api/common/support/tickets/" + id, null, SupportTicketData.class, cb);
    }

    /** Add a merchant reply; the response is the refreshed ticket. */
    public void replySupportTicket(int ticketId, String message, ApiCallback<SupportTicketData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("ticket_id", String.valueOf(ticketId));
        form.put("message", message == null ? "" : message);
        postAuthed("api/common/support/tickets/reply", form, SupportTicketData.class, cb);
    }

    /** Close a ticket; the response is the refreshed ticket. */
    public void closeSupportTicket(int ticketId, ApiCallback<SupportTicketData> cb) {
        Map<String, String> form = new HashMap<>();
        form.put("ticket_id", String.valueOf(ticketId));
        postAuthed("api/common/support/tickets/close", form, SupportTicketData.class, cb);
    }

    // ---- Internals -------------------------------------------------------

    private <T> void getAuthed(String path, Map<String, String> query, Class<T> type, ApiCallback<T> cb) {
        io.execute(() -> {
            Http.Result r = Http.get(BASE_URL, path, query, session.getToken());
            deliver(r, type, cb);
        });
    }

    private <T> void postAuthed(String path, Map<String, String> form, Class<T> type, ApiCallback<T> cb) {
        io.execute(() -> {
            Http.Result r = Http.postForm(BASE_URL, path, form, session.getToken());
            deliver(r, type, cb);
        });
    }

    /** Parse the envelope and post the result on the main thread. */
    private <T> void deliver(Http.Result r, Class<T> type, ApiCallback<T> cb) {
        T data = null;
        String error = null;
        String message = null;
        try {
            if (r.body == null || r.body.isEmpty()) {
                error = "Network error (HTTP " + r.code + ")";
            } else {
                Type envelope = TypeToken.getParameterized(ApiResponse.class, type).getType();
                ApiResponse<T> resp = gson.fromJson(r.body, envelope);
                if (resp != null && resp.status) {
                    data = resp.data;
                    message = resp.message;
                } else {
                    error = (resp != null && resp.message != null) ? resp.message : "Request failed";
                }
            }
        } catch (Exception e) {
            Log.e(Http.TAG, "parse failed: " + e, e);
            error = "Unexpected response from server";
        }

        final T result = data;
        final String errorMessage = error;
        final String successMessage = message;
        main.post(() -> {
            if (errorMessage == null) {
                cb.onSuccess(result, successMessage);
            } else {
                cb.onError(errorMessage);
            }
        });
    }

    /**
     * Check only the {@code status}/{@code message} of an envelope, ignoring the shape
     * of {@code data}. Used by endpoints (e.g. products/delete) whose {@code data} block
     * is not a typed object — parsing it into one would throw and surface a false error.
     */
    private <T> void deliverStatusOnly(Http.Result r, ApiCallback<T> cb) {
        String error = null;
        try {
            if (r.body == null || r.body.isEmpty()) {
                error = "Network error (HTTP " + r.code + ")";
            } else {
                ApiResponse<?> resp = gson.fromJson(r.body, ApiResponse.class);
                if (resp == null || !resp.status) {
                    error = (resp != null && resp.message != null) ? resp.message : "Request failed";
                }
            }
        } catch (Exception e) {
            Log.e(Http.TAG, "parse failed: " + e, e);
            error = "Unexpected response from server";
        }

        final String errorMessage = error;
        main.post(() -> {
            if (errorMessage == null) {
                cb.onSuccess(null);
            } else {
                cb.onError(errorMessage);
            }
        });
    }
}
