package com.anokix.traderapp.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.anokix.traderapp.R;
import com.anokix.traderapp.network.MediaUrls;
import com.anokix.traderapp.network.dto.MarketplaceData;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Campaign detail sheet — the mobile counterpart of the Trader Portal drawer that opens
 * from a banner's "Shop Products" / "View Details", a promotion's "Shop Now" or a news
 * post. Shows the artwork, the distributor and campaign type, the run dates and every
 * product the campaign puts on offer (single products, combo deals and buy-one-get-one
 * pairs). An announcement carries no products and says so.
 *
 * <p>Built entirely from the campaign object already in the marketplace payload — there
 * is no extra request. Tapping a product hands it back through {@link Listener}.
 */
final class CampaignSheet {

    interface Listener {
        void onProductTapped(@NonNull MarketplaceData.CampaignProduct product, long campaignId);
    }

    private CampaignSheet() {
    }

    static void show(@NonNull Activity activity, @NonNull MarketplaceData.Promotion campaign,
                     @NonNull String currency, @NonNull Listener listener) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View root = LayoutInflater.from(activity).inflate(R.layout.sheet_campaign, null, false);
        dialog.setContentView(root);
        dialog.setOnShowListener(d -> {
            // Let the rounded sheet background show, and open fully — the content is short.
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        ((TextView) root.findViewById(R.id.campTitle)).setText(orEmpty(campaign.name));
        root.findViewById(R.id.campClose).setOnClickListener(v -> dialog.dismiss());
        root.findViewById(R.id.campDone).setOnClickListener(v -> dialog.dismiss());

        ImageView image = root.findViewById(R.id.campImage);
        String url = campaign.mediaUrl();
        if (url != null && !campaign.isVideo() && GlideGuard.canLoad(image)) {
            Glide.with(image).load(url).placeholder(R.drawable.bg_banner_placeholder)
                    .error(R.drawable.bg_banner_placeholder).fitCenter().into(image);
        } else {
            image.setImageResource(R.drawable.bg_banner_placeholder);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }

        TextView distributor = root.findViewById(R.id.campDistributor);
        String distName = campaign.distributorName();
        distributor.setText(distName);
        distributor.setVisibility(distName.isEmpty() ? View.GONE : View.VISIBLE);
        TextView type = root.findViewById(R.id.campType);
        String typeLabel = campaign.typeLabel();
        type.setText(typeLabel);
        type.setVisibility(typeLabel.isEmpty() ? View.GONE : View.VISIBLE);

        TextView description = root.findViewById(R.id.campDescription);
        description.setText(orEmpty(campaign.description));
        description.setVisibility(isEmpty(campaign.description) ? View.GONE : View.VISIBLE);

        String runs = runRange(campaign.start_date, campaign.end_date);
        root.findViewById(R.id.campRunsBox).setVisibility(runs.isEmpty() ? View.GONE : View.VISIBLE);
        ((TextView) root.findViewById(R.id.campRuns)).setText(runs);

        LinearLayout list = root.findViewById(R.id.campProducts);
        int count = bindProducts(activity, list, campaign, currency, product -> {
            dialog.dismiss();
            listener.onProductTapped(product, campaign.id);
        });
        ((TextView) root.findViewById(R.id.campProductsTitle)).setText(
                activity.getString(R.string.market_campaign_products) + "  " + count);
        list.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.campEmpty).setVisibility(count == 0 ? View.VISIBLE : View.GONE);

        dialog.show();
    }

    /** Product page for a product embedded in a campaign (it refreshes itself by id). */
    static Intent productIntent(@NonNull Context context, @NonNull MarketplaceData.CampaignProduct p,
                                @NonNull String currency, @NonNull String distributorName) {
        Intent i = new Intent(context, ProductDetailActivity.class);
        i.putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, p.product_id);
        i.putExtra(ProductDetailActivity.EXTRA_NAME, p.name);
        i.putExtra(ProductDetailActivity.EXTRA_IMAGE, MediaUrls.resolve(p.imageUrl()));
        i.putExtra(ProductDetailActivity.EXTRA_PRICE, p.price());
        i.putExtra(ProductDetailActivity.EXTRA_SKU, p.sku);
        i.putExtra(ProductDetailActivity.EXTRA_BARCODE, p.barcode);
        i.putExtra(ProductDetailActivity.EXTRA_STOCK, p.available_stock);
        if (p.brand != null) i.putExtra(ProductDetailActivity.EXTRA_BRAND, p.brand.name);
        if (p.category != null) i.putExtra(ProductDetailActivity.EXTRA_CATEGORY, p.category.title);
        i.putExtra(ProductDetailActivity.EXTRA_DISTRIBUTOR, distributorName);
        i.putExtra(ProductDetailActivity.EXTRA_CURRENCY, currency);
        return i;
    }

    private interface RowTap {
        void onTap(MarketplaceData.CampaignProduct product);
    }

    /** Adds one row per offered product and returns how many were added. */
    private static int bindProducts(Activity activity, LinearLayout list,
                                    MarketplaceData.Promotion campaign, String currency, RowTap tap) {
        list.removeAllViews();
        MarketplaceData.CampaignProducts sp = campaign.selected_products;
        if (sp == null) return 0;
        int count = 0;
        if (sp.single_products != null) {
            for (MarketplaceData.CampaignProduct p : sp.single_products) {
                addRow(activity, list, p, null, currency, false, tap);
                count++;
            }
        }
        if (sp.combo_deals != null) {
            for (MarketplaceData.ComboDeal combo : sp.combo_deals) {
                if (combo == null || combo.products == null) continue;
                for (MarketplaceData.CampaignProduct p : combo.products) {
                    addRow(activity, list, p, activity.getString(R.string.market_combo_deal),
                            currency, false, tap);
                    count++;
                }
            }
        }
        if (sp.buy1_get1_deals != null) {
            for (MarketplaceData.BuyGetDeal deal : sp.buy1_get1_deals) {
                if (deal == null) continue;
                count += addAll(activity, list, deal.buy_products,
                        activity.getString(R.string.market_buy_item), currency, false, tap);
                count += addAll(activity, list, deal.free_products,
                        activity.getString(R.string.market_free_item), currency, true, tap);
            }
        }
        return count;
    }

    private static int addAll(Activity activity, LinearLayout list,
                              List<MarketplaceData.CampaignProduct> products, String badge,
                              String currency, boolean free, RowTap tap) {
        if (products == null) return 0;
        for (MarketplaceData.CampaignProduct p : products) {
            addRow(activity, list, p, badge, currency, free, tap);
        }
        return products.size();
    }

    private static void addRow(Activity activity, LinearLayout list,
                               MarketplaceData.CampaignProduct p, String badge, String currency,
                               boolean free, RowTap tap) {
        if (p == null) return;
        if (list.getChildCount() > 0) {
            View divider = new View(activity);
            divider.setBackgroundColor(ContextCompat.getColor(activity, R.color.divider));
            list.addView(divider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
        View row = LayoutInflater.from(activity).inflate(R.layout.item_campaign_product, list, false);
        ((TextView) row.findViewById(R.id.cpName)).setText(orEmpty(p.name));
        ((TextView) row.findViewById(R.id.cpSub)).setText(activity.getString(
                R.string.market_stock_left, MarketFormat.count(p.available_stock)));

        TextView price = row.findViewById(R.id.cpPrice);
        if (free) {
            price.setText(R.string.market_free_item);
            price.setTextColor(ContextCompat.getColor(activity, R.color.success_strong));
        } else {
            price.setText(MarketFormat.money(currency, p.price()));
        }

        TextView badgeView = row.findViewById(R.id.cpBadge);
        if (badge != null) {
            badgeView.setText(badge.toUpperCase(Locale.US));
            badgeView.setVisibility(View.VISIBLE);
        }

        ImageView image = row.findViewById(R.id.cpImage);
        String url = MediaUrls.resolve(p.imageUrl());
        if (url != null && GlideGuard.canLoad(image)) {
            Glide.with(image).load(url).centerCrop().into(image);
        }
        row.setOnClickListener(v -> tap.onTap(p));
        list.addView(row);
    }

    /** "2026-09-28" + "2026-12-31" → "28 Sep 2026 → 31 Dec 2026". */
    static String runRange(String start, String end) {
        String s = prettyDate(start);
        String e = prettyDate(end);
        if (s.isEmpty() && e.isEmpty()) return "";
        if (s.isEmpty()) return e;
        if (e.isEmpty()) return s;
        return s + " → " + e;
    }

    private static String prettyDate(String raw) {
        if (isEmpty(raw)) return "";
        String day = raw.trim();
        if (day.length() > 10) day = day.substring(0, 10);
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            in.setLenient(false);
            return new SimpleDateFormat("d MMM yyyy", Locale.US).format(in.parse(day));
        } catch (ParseException e) {
            return raw.trim();
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
