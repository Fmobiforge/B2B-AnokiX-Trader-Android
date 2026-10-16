package com.anokix.traderapp.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import com.anokix.traderapp.R;
import com.anokix.traderapp.model.OrderFormat;
import com.anokix.traderapp.network.ApiCallback;
import com.anokix.traderapp.network.ApiClient;
import com.anokix.traderapp.network.dto.GrvPdfData;
import com.anokix.traderapp.network.dto.OrderDetailData;
import com.anokix.traderapp.network.dto.OrdersData;
import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/**
 * Order detail / tracking (mirrors Orders2/Orders3.png): order header + a vertical
 * status timeline (Order Placed → Accepted → Picking → Packing → Out for Delivery →
 * Delivered), the delivery address, and the ordered items. Fetched by id from
 * {@code api/trader/orders?id=}.
 */
public class OrderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "id";

    private static final String[] STAGE_KEYS = {
            "pending", "accepted", "picking", "packing", "out_for_delivery", "delivered"
    };
    private static final String[] STAGE_LABELS = {
            "Order Placed", "Accepted by Distributor", "Picking", "Packing",
            "Out for Delivery", "Delivered"
    };

    private static final String[] LOGO_COLORS = {
            "#EC4899", "#7C3AED", "#2563EB", "#0891B2", "#16A34A", "#EA580C", "#D97706"
    };

    private final ApiClient api = ApiClient.get(this);

    private View scroll, progress;
    private String currency = "R";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        scroll = findViewById(R.id.detailScroll);
        progress = findViewById(R.id.detailProgress);
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        String id = getIntent().getStringExtra(EXTRA_ID);
        load(id);
    }

    private void load(String id) {
        progress.setVisibility(View.VISIBLE);
        scroll.setVisibility(View.GONE);
        api.getOrderById(id, new ApiCallback<OrderDetailData>() {
            @Override
            public void onSuccess(OrderDetailData data) {
                progress.setVisibility(View.GONE);
                if (data == null || data.order == null) {
                    Toast.makeText(OrderDetailActivity.this, R.string.order_load_failed,
                            Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                scroll.setVisibility(View.VISIBLE);
                bind(data.order);
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                Toast.makeText(OrderDetailActivity.this,
                        message != null ? message : getString(R.string.order_load_failed),
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void bind(OrdersData.Order o) {
        String name = o.distributorName();

        TextView logo = findViewById(R.id.detailLogo);
        logo.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.US));
        ViewCompat.setBackgroundTintList(logo,
                ColorStateList.valueOf(Color.parseColor(logoColor(o.distributor_id))));

        ((TextView) findViewById(R.id.detailOrderNumber)).setText(o.order_number);
        ((TextView) findViewById(R.id.detailDistributor)).setText(name);
        ((TextView) findViewById(R.id.detailAmount)).setText(OrderFormat.money(o.total_amount, currency));
        ((TextView) findViewById(R.id.detailItemsCount))
                .setText(getString(R.string.items_count, totalUnits(o)));

        findViewById(R.id.btnOrderPdf).setOnClickListener(v -> openPdf(o));

        TextView statusBadge = findViewById(R.id.detailStatus);
        int color = OrderFormat.statusColor(o.statusKey());
        statusBadge.setText(OrderFormat.humanize(o.statusKey()));
        statusBadge.setTextColor(color);
        ViewCompat.setBackgroundTintList(statusBadge,
                ColorStateList.valueOf(Color.argb(28, Color.red(color), Color.green(color), Color.blue(color))));

        bindAddress(o);
        buildTimeline(o);
        buildItems(o);
        bindNotes(o);
    }

    private void bindAddress(OrdersData.Order o) {
        TextView business = findViewById(R.id.detailBusiness);
        TextView address = findViewById(R.id.detailAddress);
        if (o.delivery_address != null) {
            business.setText(safe(o.delivery_address.business));
            address.setText(safe(o.delivery_address.address));
            business.setVisibility(isEmpty(o.delivery_address.business) ? View.GONE : View.VISIBLE);
        } else {
            business.setVisibility(View.GONE);
            address.setText("");
        }
    }

    private void bindNotes(OrdersData.Order o) {
        TextView notes = findViewById(R.id.detailNotes);
        if (isEmpty(o.notes)) {
            notes.setVisibility(View.GONE);
        } else {
            notes.setVisibility(View.VISIBLE);
            notes.setText(getString(R.string.order_note_prefix, o.notes));
        }
    }

    // ---- Timeline --------------------------------------------------------

    private void buildTimeline(OrdersData.Order o) {
        LinearLayout timeline = findViewById(R.id.timeline);
        timeline.removeAllViews();

        String statusKey = o.statusKey();
        boolean cancelled = "cancelled".equalsIgnoreCase(statusKey);
        int currentIndex = indexOf(statusKey);
        int density = (int) getResources().getDisplayMetrics().density;

        for (int i = 0; i < STAGE_LABELS.length; i++) {
            boolean done = !cancelled && i < currentIndex;
            boolean current = !cancelled && i == currentIndex;
            boolean terminalDone = current && "delivered".equals(statusKey);
            boolean last = i == STAGE_LABELS.length - 1;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            // Left column: circle + connector line.
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams leftLp =
                    new LinearLayout.LayoutParams(24 * density, LinearLayout.LayoutParams.WRAP_CONTENT);
            leftLp.setMarginEnd(14 * density);
            left.setLayoutParams(leftLp);

            View circle = buildCircle(done || terminalDone, current && !terminalDone, density);
            left.addView(circle);

            if (!last) {
                View line = new View(this);
                LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(2 * density, 30 * density);
                line.setLayoutParams(lineLp);
                line.setBackgroundColor(done ? 0xFF16A34A : 0xFFE2E8F0);
                left.addView(line);
            }

            // Right column: label + optional date.
            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setPadding(0, density, 0, 0);

            TextView label = new TextView(this);
            label.setText(STAGE_LABELS[i]);
            label.setTextSize(14);
            boolean reached = done || current;
            label.setTextColor(reached ? Color.parseColor("#1A237E") : Color.parseColor("#94A3B8"));
            if (current) {
                label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
            }
            right.addView(label);

            String sub = stageSubtitle(i, o);
            if (sub != null) {
                TextView date = new TextView(this);
                date.setText(sub);
                date.setTextSize(12);
                date.setTextColor(Color.parseColor("#5C6B7A"));
                date.setPadding(0, density, 0, 0);
                right.addView(date);
            }

            row.addView(left);
            row.addView(right);
            timeline.addView(row);
        }

        if (cancelled) {
            TextView cancel = new TextView(this);
            cancel.setText(R.string.order_cancelled_note);
            cancel.setTextColor(0xFFDC2626);
            cancel.setTextSize(13);
            cancel.setPadding(0, 8 * density, 0, 0);
            timeline.addView(cancel);
        }
    }

    /** Date/time text shown under a stage, or null if none. */
    private String stageSubtitle(int stageIndex, OrdersData.Order o) {
        switch (stageIndex) {
            case 0: // Order Placed
                return OrderFormat.createdAt(o.created_at);
            case 4: { // Out for Delivery — est. date + time window
                String date = OrderFormat.deliveryDate(o.delivery_date);
                if (date.isEmpty()) return null;
                String window = timeWindow(o);
                return "Est. " + date + (window.isEmpty() ? "" : ", " + window);
            }
            case 5: { // Delivered
                String date = OrderFormat.deliveryDate(o.delivery_date);
                return date.isEmpty() ? null : "Est. " + date;
            }
            default:
                return null;
        }
    }

    private String timeWindow(OrdersData.Order o) {
        String start = trimSeconds(o.delivery_start_time);
        String end = trimSeconds(o.delivery_end_time);
        if (start.isEmpty() && end.isEmpty()) return "";
        return start + "–" + end;
    }

    private static String trimSeconds(String t) {
        if (t == null) return "";
        // "08:00:00" -> "08:00"
        if (t.length() >= 5) return t.substring(0, 5);
        return t;
    }

    private View buildCircle(boolean done, boolean current, int density) {
        int size = 24 * density;
        if (done) {
            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(0xFF16A34A);
            iv.setBackground(bg);
            int pad = 5 * density;
            iv.setPadding(pad, pad, pad, pad);
            iv.setImageResource(R.drawable.ic_check_white);
            iv.setColorFilter(Color.WHITE);
            return iv;
        }
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.WHITE);
        ring.setStroke((current ? 3 : 2) * density, current ? 0xFF7C3AED : 0xFFCBD5E1);
        v.setBackground(ring);
        return v;
    }

    // ---- Items -----------------------------------------------------------

    private void buildItems(OrdersData.Order o) {
        LinearLayout container = findViewById(R.id.itemsContainer);
        container.removeAllViews();
        int count = o.items != null ? o.items.size() : 0;
        ((TextView) findViewById(R.id.itemsHeader)).setText(getString(R.string.items_header, count));
        if (o.items == null) return;

        LayoutInflater inflater = LayoutInflater.from(this);
        for (OrdersData.Item item : o.items) {
            View row = inflater.inflate(R.layout.item_order_detail_product, container, false);
            ImageView image = row.findViewById(R.id.itemImage);
            ((TextView) row.findViewById(R.id.itemQty)).setText("x" + item.quantity);
            ((TextView) row.findViewById(R.id.itemName))
                    .setText(item.product != null ? item.product.name : "");
            ((TextView) row.findViewById(R.id.itemPrice)).setText(
                    item.quantity + " × " + OrderFormat.money(item.unit_price, currency));
            ((TextView) row.findViewById(R.id.itemLineTotal))
                    .setText(OrderFormat.money(item.line_total, currency));
            if (item.product != null && item.product.image_url != null
                    && !item.product.image_url.isEmpty() && GlideGuard.canLoad(image)) {
                Glide.with(image).load(item.product.image_url).centerCrop().into(image);
            }
            container.addView(row);
        }
    }

    // ---- Purchase Order PDF ---------------------------------------------

    private void openPdf(OrdersData.Order o) {
        Toast.makeText(this, "Generating PDF…", Toast.LENGTH_SHORT).show();
        api.getOrderPdf(String.valueOf(o.id), new ApiCallback<GrvPdfData>() {
            @Override
            public void onSuccess(GrvPdfData pdf) {
                if (pdf == null || pdf.data == null || pdf.data.isEmpty()) {
                    Toast.makeText(OrderDetailActivity.this, "PDF unavailable.", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    byte[] bytes = Base64.decode(pdf.data, Base64.DEFAULT);
                    File dir = new File(getCacheDir(), "orders");
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                    String name = pdf.filename != null && !pdf.filename.isEmpty()
                            ? pdf.filename : o.order_number + ".pdf";
                    File file = new File(dir, name);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(bytes);
                    }
                    Intent intent = new Intent(OrderDetailActivity.this, PdfViewerActivity.class);
                    intent.putExtra(PdfViewerActivity.EXTRA_PATH, file.getAbsolutePath());
                    intent.putExtra(PdfViewerActivity.EXTRA_TITLE, o.order_number);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(OrderDetailActivity.this, "Couldn't open PDF.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(OrderDetailActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ---- Helpers ---------------------------------------------------------

    /**
     * Total ordered units shown in the header ("3 items"). The detail endpoint
     * carries no {@code item_count}, so it is summed from the items' quantities;
     * the list endpoint's {@code item_count} is the fallback before items load.
     */
    private static int totalUnits(OrdersData.Order o) {
        if (o.items != null && !o.items.isEmpty()) {
            int sum = 0;
            for (OrdersData.Item it : o.items) sum += it.quantity;
            return sum;
        }
        return o.item_count;
    }

    private int indexOf(String status) {
        for (int i = 0; i < STAGE_KEYS.length; i++) {
            if (STAGE_KEYS[i].equalsIgnoreCase(status)) return i;
        }
        return 0;
    }

    private static String logoColor(long distributorId) {
        return LOGO_COLORS[(int) (Math.abs(distributorId) % LOGO_COLORS.length)];
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
