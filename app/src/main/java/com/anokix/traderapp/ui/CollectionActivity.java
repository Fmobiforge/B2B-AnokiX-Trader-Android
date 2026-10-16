package com.anokix.traderapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.MarketCart;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.CartData;
import com.anokix.traderapp.network.dto.CollectionData;
import com.anokix.traderapp.network.dto.MarketplaceData;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * A titled, paginated product collection (GET /api/trader/marketplace/collection),
 * opened from a Marketplace section's "View All" (e.g. Recommended for You /
 * Best Sellers). Simple 2-column product grid with infinite-scroll pagination.
 */
public class CollectionActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_TITLE = "title";

    private static final int PER_PAGE = 20;

    private final ApiClient api = ApiClient.get(this);

    private String type, title;
    private String currency = "R";
    private int page = 1, totalPages = 1;
    private boolean loading = false;

    private TextView cartBadge, titleView, countView, emptyView;
    private ProgressBar progress, loadMore;
    private RecyclerView grid;
    private ProductAdapter adapter;
    private final List<MarketplaceData.Product> products = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collection);

        type = getIntent().getStringExtra(EXTRA_TYPE);
        title = getIntent().getStringExtra(EXTRA_TITLE);

        cartBadge = findViewById(R.id.cartBadge);
        titleView = findViewById(R.id.collectionTitle);
        countView = findViewById(R.id.collectionCount);
        emptyView = findViewById(R.id.emptyView);
        progress = findViewById(R.id.progress);
        loadMore = findViewById(R.id.loadMore);
        grid = findViewById(R.id.collectionGrid);

        if (title != null) titleView.setText(title);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.cartButton).setOnClickListener(v ->
                startActivity(new Intent(this, CartActivity.class)));

        adapter = new ProductAdapter();
        GridLayoutManager lm = new GridLayoutManager(this, 2);
        grid.setLayoutManager(lm);
        grid.setAdapter(adapter);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || page >= totalPages) return;
                int total = lm.getItemCount();
                int lastVisible = lm.findLastVisibleItemPosition();
                if (lastVisible >= total - 4) loadPage(page + 1, false);
            }
        });

        loadPage(1, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCart();
    }

    // ---- Data ------------------------------------------------------------

    private void loadPage(int p, boolean reset) {
        if (loading) return;
        loading = true;
        (reset ? progress : loadMore).setVisibility(View.VISIBLE);
        if (reset) emptyView.setVisibility(View.GONE);
        api.getCollection(type, p, PER_PAGE, new ApiCallback<CollectionData>() {
            @Override
            public void onSuccess(CollectionData result) {
                loading = false;
                progress.setVisibility(View.GONE);
                loadMore.setVisibility(View.GONE);
                if (result == null) return;
                currency = result.currencySymbol();
                if (title == null && result.title != null) {
                    title = result.title;
                    titleView.setText(title);
                }
                if (result.pagination != null) {
                    page = result.pagination.page;
                    totalPages = result.pagination.total_pages;
                    countView.setText(getString(R.string.collection_products, result.pagination.total));
                }
                if (reset) products.clear();
                if (result.products != null) products.addAll(result.products);
                adapter.notifyDataSetChanged();
                emptyView.setVisibility(products.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                loading = false;
                progress.setVisibility(View.GONE);
                loadMore.setVisibility(View.GONE);
                toast(message);
            }
        });
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
            public void onError(String message) {
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

    private void toast(String msg) {
        if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
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
            } else {
                h.stockLine.setText(R.string.out_of_stock);
            }
            h.stockLine.setTextColor(ContextCompat.getColor(CollectionActivity.this,
                    R.color.text_secondary));
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
