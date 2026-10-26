package com.anokix.traderapp.network.dto;

import com.anokix.traderapp.network.MediaUrls;

import java.util.List;
import java.util.Locale;

/**
 * data block of GET /api/trader/marketplace.
 *
 * Mirrors the Trader Portal marketplace page: the active distributor, promo
 * banners, business-insight summary cards, recommended / best-seller products,
 * exclusive promotions, and the trader's own distributor list (for the
 * "Change Distributor" picker).
 */
public class MarketplaceData {

    public Meta meta;
    public Distributor selected_distributor;
    public List<Banner> banners;
    public List<Product> recommended;
    public List<Product> best_sellers;
    public List<Promotion> promotions;
    public BusinessInsights business_insights;
    public List<Distributor> my_distributors;

    // Trader Portal marketplace sections (Best/Exclusive Deals, News, Stories, Top
    // Suppliers, Best Sellers, Recent Orders). Each may be missing or empty.
    /** Products on a "best deal" campaign; each carries its {@code campaign_id}. */
    public List<Product> best_deal_products;
    /** Products only this distributor offers; each carries its {@code campaign_id}. */
    public List<Product> exclusive_deal_products;
    /** campaign_type=post — the "News from {distributor}" feed. */
    public List<Promotion> posts;
    /** campaign_type=story — the round "Stories" thumbnails. */
    public List<Promotion> stories;
    /** Brands with the most products in the catalogue. */
    public List<Supplier> top_suppliers;
    /** Distributors ranked by catalogue size ("Best Sellers" on the portal). */
    public List<Seller> top_sellers;
    public List<RecentOrder> recent_orders;

    /** Currency symbol for the page (defaults to the SA Rand "R"). */
    public String currencySymbol() {
        if (meta != null && meta.currency != null && meta.currency.symbol != null
                && !meta.currency.symbol.trim().isEmpty()) {
            return meta.currency.symbol;
        }
        return "R";
    }

    public static class Meta {
        public Currency currency;
    }

    public static class Currency {
        public String code;
        public String symbol;
    }

    public static class Distributor {
        public long id;
        public String distributor_code;
        public String company_legal_name;
        public String trading_name;
        public String display_name;
        public String company_email;
        public String company_phone_number;
        public String address;
        public String status;
        public String logo_url;
        public String preferred_delivery_days;
        public String contact_name;
        public String contact_email;
        public String contact_phone;
        public int products_available;
        /** May be null or numeric; kept as String so either decodes. */
        public String minimum_order;

        public String displayName() {
            // Prefer the registered legal name, then backend display, then trading.
            if (notEmpty(company_legal_name)) return company_legal_name;
            if (notEmpty(display_name)) return display_name;
            if (notEmpty(trading_name)) return trading_name;
            return distributor_code != null ? distributor_code : "Distributor";
        }
    }

    public static class Banner {
        public long id;
        public long campaign_id;
        public long distributor_id;
        public String campaign_type;
        public String name;
        public String description;
        public Media banner_file;
        public Media video_file;
        public String start_date;
        public String end_date;
        public CampaignProducts selected_products;
        public Distributor distributor;

        public String mediaUrl() {
            if (video_file != null && notEmpty(video_file.file_url)) {
                return MediaUrls.resolve(video_file.file_url);
            }
            if (banner_file != null && notEmpty(banner_file.file_url)) {
                return MediaUrls.resolve(banner_file.file_url);
            }
            return null;
        }

        public boolean isVideo() {
            return video_file != null && notEmpty(video_file.file_url);
        }

        /** The campaign behind this slide, in the shape the campaign sheet renders. */
        public Promotion asCampaign() {
            Promotion p = new Promotion();
            p.id = campaign_id > 0 ? campaign_id : id;
            p.distributor_id = distributor_id;
            p.campaign_type = campaign_type;
            p.name = name;
            p.description = description;
            p.start_date = start_date;
            p.end_date = end_date;
            p.banner_file = banner_file;
            p.video_file = video_file;
            p.selected_products = selected_products;
            p.distributor = distributor;
            return p;
        }
    }

    public static class Promotion {
        public long id;
        public long distributor_id;
        public String campaign_type;
        public String name;
        public String description;
        public String start_date;
        public String end_date;
        public Media banner_file;
        public Media video_file;
        public CampaignProducts selected_products;
        public Distributor distributor;

        /** Announcements and news posts promote no products — the CTA reads "View Details". */
        public boolean isAnnouncement() {
            String t = campaign_type == null ? "" : campaign_type.toLowerCase(Locale.US);
            return t.equals("post") || t.equals("story") || t.contains("announcement");
        }

        /** Badge for the slide/sheet, e.g. "cant_miss_deal" → "CAN'T MISS DEAL". */
        public String typeLabel() {
            String t = campaign_type == null ? "" : campaign_type.toLowerCase(Locale.US);
            switch (t) {
                case "post": return "ANNOUNCEMENT";
                case "story": return "STORY";
                case "cant_miss_deal": return "CAN'T MISS DEAL";
                case "exclusive_deal": return "EXCLUSIVE DEAL";
                case "best_deal": return "BEST DEAL";
                case "promotion": return "PROMOTION";
                case "featured_store": return "FEATURED STORE";
                default: return t.replace('_', ' ').toUpperCase(Locale.US);
            }
        }

        public String distributorName() {
            return distributor != null ? distributor.displayName() : "";
        }

        public String mediaUrl() {
            if (banner_file != null && notEmpty(banner_file.file_url)) {
                return MediaUrls.resolve(banner_file.file_url);
            }
            if (video_file != null && notEmpty(video_file.file_url)) {
                return MediaUrls.resolve(video_file.file_url);
            }
            return null;
        }

        public boolean isVideo() {
            return (banner_file == null || !notEmpty(banner_file.file_url))
                    && video_file != null && notEmpty(video_file.file_url);
        }
    }

    public static class Media {
        public long id;
        public String media_type;
        public String file_path;
        public String file_url;
        public String original_name;
    }

    public static class BusinessInsights {
        public boolean is_static_data;
        public List<SummaryCard> summary_cards;
    }

    public static class SummaryCard {
        public String key;
        public String label;
        public String formatted_value;
        public boolean is_money;
        public String detail;
        public String action_label;
        public Trend trend;

        public String subtitle() {
            if (trend != null && notEmpty(trend.label)) return trend.label;
            if (notEmpty(detail)) return detail;
            if (notEmpty(action_label)) return action_label;
            return null;
        }

        public boolean trendUp() {
            return trend != null && "up".equalsIgnoreCase(trend.direction);
        }
    }

    public static class Trend {
        public String direction;
        public Integer percentage;
        public Integer count;
        public String label;
    }

    /** Product card shared by recommended / best_sellers and the product-detail call. */
    public static class Product {
        public String id;
        public long distributor_id;
        public String name;
        public String sku;
        public String barcode;
        public String description;
        public String brand_name;
        public String unit_name;
        public String category_title;
        public String parent_category_title;
        public String selling_price;
        public String cost_price;
        public String promotion_price;
        public int stock;
        public String stock_quantity;
        public String available_for_ordering;
        public String primary_product_image_url;
        public String image_url;
        public java.util.List<AdditionalImage> additional_images;
        /** Set on best/exclusive deal products — the campaign a view is attributed to. */
        public long campaign_id;

        public double priceValue() {
            double v = parse(selling_price);
            return v > 0 ? v : parse(promotion_price);
        }

        public String formattedPrice(String symbol) {
            return symbol + String.format(Locale.US, "%,.2f", priceValue());
        }

        public String imageUrl() {
            if (notEmpty(image_url)) return image_url;
            return primary_product_image_url;
        }

        /** Ordered, de-duplicated gallery: primary image first, then additional_images. */
        public java.util.List<String> galleryUrls() {
            java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();
            String primary = imageUrl();
            if (notEmpty(primary)) urls.add(primary);
            if (additional_images != null) {
                java.util.List<AdditionalImage> sorted = new java.util.ArrayList<>(additional_images);
                java.util.Collections.sort(sorted, (a, b) ->
                        Integer.compare(a != null ? a.sort_order : 0, b != null ? b.sort_order : 0));
                for (AdditionalImage img : sorted) {
                    if (img != null && notEmpty(img.file_url)) urls.add(img.file_url);
                }
            }
            return new java.util.ArrayList<>(urls);
        }

        public static class AdditionalImage {
            public String file_url;
            public int sort_order;
        }

        public boolean inStock() {
            if (stock > 0) return true;
            return parse(stock_quantity) > 0;
        }

        public int stockCount() {
            if (stock > 0) return stock;
            return (int) parse(stock_quantity);
        }

        private static double parse(String s) {
            if (s == null) return 0;
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    // ---- Campaign products (selected_products on banners / promotions) ----

    /** What a campaign puts on offer. All three lists may be empty (announcements). */
    public static class CampaignProducts {
        public List<CampaignProduct> single_products;
        public List<ComboDeal> combo_deals;
        public List<BuyGetDeal> buy1_get1_deals;
    }

    /** A product as embedded in a campaign — slimmer than {@link Product}. */
    public static class CampaignProduct {
        public String product_id;
        public String name;
        public String sku;
        public String barcode;
        public int available_stock;
        public double original_price;
        public double campaign_selling_price;
        public String image_url;
        public String primary_product_image_url;
        public NamedRef brand;
        public NamedRef category;

        public String imageUrl() {
            if (notEmpty(image_url)) return image_url;
            return primary_product_image_url;
        }

        public double price() {
            return campaign_selling_price > 0 ? campaign_selling_price : original_price;
        }
    }

    public static class ComboDeal {
        public double campaign_selling_price;
        public List<CampaignProduct> products;
    }

    public static class BuyGetDeal {
        public double campaign_selling_price;
        public List<CampaignProduct> buy_products;
        public List<CampaignProduct> free_products;
    }

    public static class NamedRef {
        public long id;
        public String name;
        public String title;
    }

    // ---- Side-rail sections ---------------------------------------------

    public static class Supplier {
        public long id;
        public String name;
        public String logo_url;
        public int product_count;
    }

    public static class Seller {
        public long id;
        public String name;
        public String logo_url;
        public int products_available;
        /** True for the distributor the trader already buys from ("YOURS"). */
        public boolean is_mine;
    }

    public static class RecentOrder {
        public long id;
        public String order_number;
        public String status;
        public String status_key;
        public double total_amount;
        public String created_at;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
