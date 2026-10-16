package com.anokix.traderapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.MarketCart;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.CartData;
import com.bumptech.glide.Glide;

import java.util.Locale;

/**
 * My Cart (mirrors My_Cart.png). Renders the local {@link MarketCart}: per-line
 * quantity steppers and delete, the free-delivery banner, and the order summary.
 * Quantity / delete changes update the local cart and fire the matching
 * /api/trader/cart/* calls best-effort.
 */
public class CartActivity extends AppCompatActivity {

    private final ApiClient api = ApiClient.get(this);
    private final MarketCart cart = MarketCart.get();

    private LinearLayout linesContainer;
    private View emptyView, scroll;
    private TextView title, summarySubtotal, summaryTotal;
    private String currency = "R";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        linesContainer = findViewById(R.id.cartLinesContainer);
        emptyView = findViewById(R.id.emptyView);
        scroll = findViewById(R.id.cartScroll);
        title = findViewById(R.id.cartTitle);
        summarySubtotal = findViewById(R.id.summarySubtotal);
        summaryTotal = findViewById(R.id.summaryTotal);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnCheckout).setOnClickListener(v -> proceedToCheckout());

        render();
    }

    /** Launch Make Order with the selected cart lines (kept in the cart until ordered). */
    private void proceedToCheckout() {
        java.util.List<MarketCart.Line> selected = cart.selectedLines();
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.select_items_to_order, Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> ids = new ArrayList<>();
        String distributorId = null;
        for (MarketCart.Line l : selected) {
            if (l.cartItemId != null) ids.add(l.cartItemId);
            if (distributorId == null && l.distributorId != null) distributorId = l.distributorId;
        }
        if (ids.isEmpty()) {
            Toast.makeText(this, R.string.select_items_to_order, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent i = new Intent(this, MakeOrderActivity.class);
        i.putStringArrayListExtra(MakeOrderActivity.EXTRA_CART_ITEM_IDS, ids);
        i.putExtra(MakeOrderActivity.EXTRA_DISTRIBUTOR_ID, distributorId);
        i.putExtra(MakeOrderActivity.EXTRA_SUBTOTAL, cart.selectedSubtotal());
        i.putExtra(MakeOrderActivity.EXTRA_CURRENCY, currency);
        startActivityForResult(i, REQ_MAKE_ORDER);
    }

    private static final int REQ_MAKE_ORDER = 1001;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Order placed: the ordered lines are gone from the server cart — close the
        // cart so the user lands back on the marketplace.
        if (requestCode == REQ_MAKE_ORDER && resultCode == RESULT_OK) {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCart();
    }

    /** Pull the authoritative cart from the server and re-render. */
    private void loadCart() {
        api.getCart(new ApiCallback<CartData>() {
            @Override
            public void onSuccess(CartData result) {
                if (isFinishing() || isDestroyed()) return;
                if (result != null) {
                    cart.hydrate(result);
                    currency = result.currencySymbol();
                }
                render();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                render();
            }
        });
    }

    private void render() {
        boolean empty = cart.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        scroll.setVisibility(empty ? View.GONE : View.VISIBLE);

        title.setText(getString(R.string.my_cart_count,
                cart.distinctCount(), cart.distinctCount() == 1
                        ? getString(R.string.item) : getString(R.string.items)));

        linesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (MarketCart.Line line : new java.util.ArrayList<>(cart.lines())) {
            linesContainer.addView(buildLine(inflater, line));
        }
        updateSummary();
    }

    private View buildLine(LayoutInflater inflater, MarketCart.Line line) {
        View row = inflater.inflate(R.layout.item_market_cart_line, linesContainer, false);
        CheckBox select = row.findViewById(R.id.cartSelect);
        ImageView image = row.findViewById(R.id.cartImage);
        TextView name = row.findViewById(R.id.cartName);
        TextView unit = row.findViewById(R.id.cartUnitPrice);
        TextView qty = row.findViewById(R.id.cartQty);
        TextView lineTotal = row.findViewById(R.id.cartLineTotal);

        select.setChecked(line.selected);
        select.setOnCheckedChangeListener((b, checked) -> {
            line.selected = checked;
            updateSummary();
        });

        name.setText(line.product != null ? line.product.name : "");
        unit.setText(money(line.unitPrice));
        qty.setText(String.valueOf(line.quantity));
        lineTotal.setText(money(line.lineTotal()));
        if (line.product != null && line.product.imageUrl() != null && GlideGuard.canLoad(image)) {
            Glide.with(image).load(line.product.imageUrl()).centerCrop().into(image);
        }

        row.findViewById(R.id.cartMinus).setOnClickListener(v -> {
            int q = line.quantity - 1;
            cart.setQuantity(line, q);
            if (q > 0) {
                syncUpdate(line);
            } else {
                syncDelete(line);
            }
            render();
        });
        row.findViewById(R.id.cartPlus).setOnClickListener(v -> {
            cart.setQuantity(line, line.quantity + 1);
            syncUpdate(line);
            render();
        });
        row.findViewById(R.id.cartDelete).setOnClickListener(v -> {
            cart.remove(line);
            syncDelete(line);
            render();
        });
        return row;
    }

    private void syncUpdate(MarketCart.Line line) {
        if (line.cartItemId == null) return;
        api.updateCartItem(line.cartItemId, line.quantity, line.unitPrice, reconcile());
    }

    private void syncDelete(MarketCart.Line line) {
        if (line.cartItemId == null) return;
        api.deleteCartItem(line.cartItemId, reconcile());
    }

    private void updateSummary() {
        double subtotal = cart.selectedSubtotal();
        summarySubtotal.setText(money(subtotal));
        summaryTotal.setText(money(subtotal));
    }

    private String money(double v) {
        return currency + String.format(Locale.US, "%,.2f", v);
    }

    /** After a best-effort mutation, re-pull the server cart to reconcile ids/totals. */
    private ApiCallback<CartData> reconcile() {
        return new ApiCallback<CartData>() {
            @Override public void onSuccess(CartData result) { loadCart(); }
            @Override public void onError(String message) {}
        };
    }
}
