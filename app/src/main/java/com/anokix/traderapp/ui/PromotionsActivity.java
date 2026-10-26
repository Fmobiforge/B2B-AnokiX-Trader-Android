package com.anokix.traderapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.MarketCart;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.CartData;
import com.anokix.traderapp.network.dto.MarketplaceData;
import com.anokix.traderapp.network.dto.PromotionCollectionData;

import java.util.ArrayList;
import java.util.List;

/**
 * The titled, paginated "Current Promotions" list (GET /api/trader/marketplace/promotions),
 * opened from the Marketplace "Current Promotions" View All. Vertical list of overlaid
 * promo cards (shared {@code item_promotion_card.xml} / {@link PromotionCardBinder}).
 */
public class PromotionsActivity extends AppCompatActivity {

    private static final int PER_PAGE = 10;

    private final ApiClient api = ApiClient.get(this);

    private int page = 1, totalPages = 1;
    private boolean loading = false;

    private TextView cartBadge, titleView, countView, emptyView;
    private ProgressBar progress, loadMore;
    private RecyclerView list;
    private PromoAdapter adapter;
    private final List<MarketplaceData.Promotion> promotions = new ArrayList<>();
    private String currency = "R";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_promotions);

        cartBadge = findViewById(R.id.cartBadge);
        titleView = findViewById(R.id.promotionsTitle);
        countView = findViewById(R.id.promotionsCount);
        emptyView = findViewById(R.id.emptyView);
        progress = findViewById(R.id.progress);
        loadMore = findViewById(R.id.loadMore);
        list = findViewById(R.id.promotionsList);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.cartButton).setOnClickListener(v ->
                startActivity(new Intent(this, CartActivity.class)));

        adapter = new PromoAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(this);
        list.setLayoutManager(lm);
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loading || page >= totalPages) return;
                if (lm.findLastVisibleItemPosition() >= lm.getItemCount() - 2) {
                    loadPage(page + 1, false);
                }
            }
        });

        loadPage(1, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCart();
    }

    /** "Shop Now": record the campaign open and show its products (portal drawer). */
    private void openCampaign(MarketplaceData.Promotion p) {
        api.recordCampaignClick(p.id);
        CampaignSheet.show(this, p, currency, (product, campaignId) -> {
            api.recordCampaignProductView(campaignId, product.product_id);
            startActivity(CampaignSheet.productIntent(this, product, currency, p.distributorName()));
        });
    }

    private void loadPage(int p, boolean reset) {
        if (loading) return;
        loading = true;
        (reset ? progress : loadMore).setVisibility(View.VISIBLE);
        if (reset) emptyView.setVisibility(View.GONE);
        api.getPromotions(p, PER_PAGE, new ApiCallback<PromotionCollectionData>() {
            @Override
            public void onSuccess(PromotionCollectionData result) {
                loading = false;
                progress.setVisibility(View.GONE);
                loadMore.setVisibility(View.GONE);
                if (result == null) return;
                currency = result.currencySymbol();
                if (result.title != null) titleView.setText(result.title);
                if (result.pagination != null) {
                    page = result.pagination.page;
                    totalPages = result.pagination.total_pages;
                    countView.setText(getString(R.string.promotions_count, result.pagination.total));
                }
                if (reset) promotions.clear();
                if (result.promotions != null) promotions.addAll(result.promotions);
                adapter.notifyDataSetChanged();
                emptyView.setVisibility(promotions.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                loading = false;
                progress.setVisibility(View.GONE);
                loadMore.setVisibility(View.GONE);
                if (message != null) Toast.makeText(PromotionsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
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

    // ---- Adapter ---------------------------------------------------------

    private class PromoAdapter extends RecyclerView.Adapter<PromoAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_promotion_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            MarketplaceData.Promotion p = promotions.get(position);
            PromotionCardBinder.bind(PromotionsActivity.this, h.itemView, p, () -> openCampaign(p));
            h.itemView.setOnClickListener(v -> openCampaign(p));
        }

        @Override
        public int getItemCount() {
            return promotions.size();
        }

        class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View v) {
                super(v);
            }
        }
    }
}
