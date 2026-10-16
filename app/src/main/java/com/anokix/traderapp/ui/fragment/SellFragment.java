package com.anokix.traderapp.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anokix.traderapp.R;
import com.anokix.traderapp.data.Cart;
import com.anokix.traderapp.model.PosProduct;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.PosProductsData;
import com.anokix.traderapp.ui.CheckoutActivity;
import com.anokix.traderapp.ui.GlideGuard;
import com.anokix.traderapp.ui.MainActivity;
import com.anokix.traderapp.ui.NotificationsActivity;
import com.anokix.traderapp.ui.SalesActivity;
import com.anokix.traderapp.ui.pos.PosScanDialogFragment;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * POS (Pagamio) — the till screen: a live product grid from {@code api/trader/pos/products}
 * with search + category filters. Products reach the sale three ways — tapping "+", or the
 * Scan / QR sheets ({@link PosScanDialogFragment}), which resolve a scanned or typed code
 * against this same grid. The bottom bar opens Complete Sale ({@link CheckoutActivity}),
 * which applies the discount, extracts VAT and commits the sale via
 * {@code api/trader/pos/sale}. The receipt icon opens Sales History
 * ({@link SalesActivity}, {@code api/trader/pos/sales}).
 *
 * <p>POS-only by design: Airtime & VAS lives on its own screen
 * ({@link com.anokix.traderapp.ui.AirtimeActivity}), not here.
 */
public class SellFragment extends Fragment implements PosScanDialogFragment.Host {

    private static final String ALL = "All";

    private TextView cartTotalView;
    private TextView cartCountView;
    private ProgressBar progress;
    private TextView emptyView;
    private ChipGroup chipGroup;

    private ApiClient api;
    private final List<PosProduct> allProducts = new ArrayList<>();
    private ProductAdapter adapter;
    private String selectedCategory = ALL;
    private String searchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sell, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        api = ApiClient.get(requireContext());

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).bindDrawerButton(view.findViewById(R.id.hamburgerButton));
        }
        view.findViewById(R.id.notificationsButton).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationsActivity.class)));
        view.findViewById(R.id.posHistoryButton).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SalesActivity.class)));

        cartTotalView = view.findViewById(R.id.cartTotal);
        cartCountView = view.findViewById(R.id.cartCount);
        progress = view.findViewById(R.id.posProgress);
        emptyView = view.findViewById(R.id.posEmpty);
        chipGroup = view.findViewById(R.id.posCategoryChips);

        view.findViewById(R.id.scanButton).setOnClickListener(v ->
                openScanner(PosScanDialogFragment.Mode.BARCODE));
        view.findViewById(R.id.qrButton).setOnClickListener(v ->
                openScanner(PosScanDialogFragment.Mode.QR));

        view.findViewById(R.id.checkoutButton).setOnClickListener(v -> {
            if (Cart.get().isEmpty()) {
                Toast.makeText(requireContext(), R.string.empty_cart, Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(requireContext(), CheckoutActivity.class));
            }
        });

        EditText search = view.findViewById(R.id.posSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase(Locale.US);
                applyFilters();
            }
        });

        RecyclerView list = view.findViewById(R.id.posProducts);
        list.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new ProductAdapter();
        list.setAdapter(adapter);
    }

    /**
     * The grid is loaded here rather than in {@code onViewCreated} so it also refreshes on
     * the way back from Complete Sale — a committed sale reduces on-hand stock.
     */
    @Override
    public void onResume() {
        super.onResume();
        refreshCartBar();
        loadProducts();
    }

    private void loadProducts() {
        progress.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        api.getPosProducts(new ApiCallback<PosProductsData>() {
            @Override
            public void onSuccess(PosProductsData data) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                allProducts.clear();
                if (data != null && data.products != null) {
                    allProducts.addAll(data.products);
                }
                buildCategoryChips();
                applyFilters();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                applyFilters();
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void buildCategoryChips() {
        chipGroup.removeAllViews();
        Set<String> categories = new LinkedHashSet<>();
        categories.add(ALL);
        for (PosProduct p : allProducts) {
            if (p.category != null && !p.category.isEmpty()) {
                categories.add(p.category);
            }
        }
        // Drop a stale selection (the category may have disappeared after a reload).
        if (!categories.contains(selectedCategory)) {
            selectedCategory = ALL;
        }
        for (String label : categories) {
            Chip chip = new Chip(requireContext());
            chip.setText(label);
            chip.setCheckable(true);
            chip.setChecked(label.equals(selectedCategory));
            chip.setChipBackgroundColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_bg_selector));
            chip.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector));
            chip.setOnClickListener(v -> {
                selectedCategory = label;
                applyFilters();
            });
            chipGroup.addView(chip);
        }
    }

    private void applyFilters() {
        List<PosProduct> filtered = new ArrayList<>();
        for (PosProduct p : allProducts) {
            boolean catOk = ALL.equals(selectedCategory)
                    || selectedCategory.equalsIgnoreCase(p.category);
            boolean searchOk = searchQuery.isEmpty()
                    || (p.name != null && p.name.toLowerCase(Locale.US).contains(searchQuery))
                    || (p.sku != null && p.sku.toLowerCase(Locale.US).contains(searchQuery))
                    || (p.barcode != null && p.barcode.toLowerCase(Locale.US).contains(searchQuery));
            if (catOk && searchOk) {
                filtered.add(p);
            }
        }
        adapter.setItems(filtered);
        boolean empty = filtered.isEmpty() && progress.getVisibility() != View.VISIBLE;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    // ---- Scan / QR -------------------------------------------------------

    private static final String TAG_SCANNER = "pos-scanner";

    private void openScanner(PosScanDialogFragment.Mode mode) {
        if (getChildFragmentManager().findFragmentByTag(TAG_SCANNER) != null) {
            return; // already open — a double tap must not stack two cameras
        }
        PosScanDialogFragment.newInstance(mode)
                .show(getChildFragmentManager(), TAG_SCANNER);
    }

    /** The scanner reads the grid this fragment already holds — no second products call. */
    @Override
    public List<PosProduct> scanProducts() {
        return allProducts;
    }

    @Override
    public void onScanCartChanged() {
        refreshCartBar();
    }

    private void addToCart(PosProduct item) {
        Cart.get().add(item);
        refreshCartBar();
        Toast.makeText(requireContext(), getString(R.string.added_to_cart, item.name), Toast.LENGTH_SHORT).show();
    }

    private void refreshCartBar() {
        Cart cart = Cart.get();
        cartTotalView.setText(money(cart.subtotal()));
        int count = cart.itemCount();
        if (count > 0) {
            cartCountView.setVisibility(View.VISIBLE);
            cartCountView.setText(String.valueOf(count));
        } else {
            cartCountView.setVisibility(View.GONE);
        }
    }

    private String money(double value) {
        return String.format(Locale.US, "R%,.2f", value);
    }

    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
        private List<PosProduct> items = new ArrayList<>();

        void setItems(List<PosProduct> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pos_product, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            PosProduct item = items.get(position);
            h.name.setText(item.name);
            h.sku.setText(item.sku == null || item.sku.isEmpty() ? "" : "SKU: " + item.sku);
            h.price.setText(money(item.price));

            boolean sellable = item.sellable();
            h.stock.setText(sellable
                    ? item.units + " in stock"
                    : getString(R.string.out_of_stock));
            h.stock.setTextColor(ContextCompat.getColor(requireContext(),
                    sellable ? R.color.success : R.color.danger));

            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                if (GlideGuard.canLoad(h.image)) Glide.with(h.image.getContext()).load(item.imageUrl).centerCrop().into(h.image);
            } else {
                h.image.setImageDrawable(null);
            }

            h.add.setEnabled(sellable);
            h.add.setAlpha(sellable ? 1f : 0.4f);
            h.add.setOnClickListener(sellable ? v -> addToCart(item) : null);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final android.widget.ImageView image;
            final TextView name, sku, price, stock;
            final MaterialButton add;

            VH(@NonNull View v) {
                super(v);
                image = v.findViewById(R.id.posProductImage);
                name = v.findViewById(R.id.posProductName);
                sku = v.findViewById(R.id.posProductSku);
                price = v.findViewById(R.id.posProductPrice);
                stock = v.findViewById(R.id.posProductStock);
                add = v.findViewById(R.id.posProductAdd);
            }
        }
    }
}
