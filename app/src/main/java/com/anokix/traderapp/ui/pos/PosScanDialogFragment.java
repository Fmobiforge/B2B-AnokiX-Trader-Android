package com.anokix.traderapp.ui.pos;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Size;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.StringRes;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.anokix.traderapp.R;
import com.anokix.traderapp.data.Cart;
import com.anokix.traderapp.model.PosProduct;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * POS "Scan a barcode" / "Scan a QR code" sheet.
 *
 * <p>The portal shows this as a type-it-in modal because a browser cannot reach the
 * camera; a phone can, so the camera is the primary input here and manual entry is the
 * fallback (damaged labels, and USB/Bluetooth handheld scanners, which type the code and
 * press Enter). Either way the code is resolved against the loaded POS product list by
 * {@link ProductCodeMatcher} and the outcome is reported in place — added, out of stock,
 * or no match — leaving the sheet open so the cashier can keep scanning the basket.
 */
public class PosScanDialogFragment extends DialogFragment {

    /** Which sheet to show. Both match on barcode + SKU; they differ in symbologies + copy. */
    public enum Mode { BARCODE, QR }

    /** Supplied by the hosting POS screen so the sheet always reads the live product list. */
    public interface Host {
        /** The products currently loaded on the POS screen (may be empty while loading). */
        List<PosProduct> scanProducts();

        /** A unit was added to the sale — refresh the Current Sale bar. */
        void onScanCartChanged();
    }

    /**
     * Alternative host for screens that resolve codes themselves (the Marketplace looks
     * the code up through its search API). When the host implements this, the sheet hands
     * over the first accepted code and closes instead of matching against a POS list.
     */
    public interface CodeHost {
        void onCodeScanned(String code);
    }

    private static final String ARG_MODE = "mode";
    /** A rung-up code counts again only once it has been out of frame for this long. */
    private static final long SEPARATION_MS = 900L;
    /** Nothing at all is accepted for this long after an accept — one item per gesture. */
    private static final long DEAD_TIME_MS = 600L;

    public static PosScanDialogFragment newInstance(Mode mode) {
        PosScanDialogFragment f = new PosScanDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MODE, mode.name());
        f.setArguments(args);
        return f;
    }

    private Mode mode = Mode.BARCODE;

    private PreviewView previewView;
    private View reticle, cameraOff;
    private TextView cameraOffText, hintText, feedbackText, cartSummary;
    private MaterialButton cameraOffAction;
    private ImageButton torchButton;
    private View feedback;
    private ImageView feedbackIcon;
    private EditText input;

    private ExecutorService analysisExecutor;
    private BarcodeScanner scanner;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private boolean torchOn;
    private boolean cameraStarted;

    /** Codes already rung up and still in front of the lens: code → frame it was last seen in. */
    private final Map<String, Long> heldCodes = new LinkedHashMap<>();
    private long lastAcceptedAt;
    private boolean showingHeldState;

    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_AnokiX_Dialog_Scan);
        if (getArguments() != null) {
            try {
                mode = Mode.valueOf(getArguments().getString(ARG_MODE, Mode.BARCODE.name()));
            } catch (IllegalArgumentException ignored) {
                mode = Mode.BARCODE;
            }
        }
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        startCamera();
                    } else {
                        // Denied. If the OS will no longer show the prompt, the only route
                        // left is app settings — so point there instead of a dead button.
                        boolean canAskAgain = shouldShowRequestPermissionRationale(
                                Manifest.permission.CAMERA);
                        showCameraOff(R.string.scan_camera_denied_body,
                                canAskAgain ? R.string.scan_allow_camera : R.string.scan_open_settings,
                                canAskAgain ? this::requestCameraPermission : this::openAppSettings);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_pos_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        previewView = view.findViewById(R.id.scanPreview);
        reticle = view.findViewById(R.id.scanReticle);
        cameraOff = view.findViewById(R.id.scanCameraOff);
        cameraOffText = view.findViewById(R.id.scanCameraOffText);
        cameraOffAction = view.findViewById(R.id.scanCameraOffAction);
        torchButton = view.findViewById(R.id.scanTorch);
        hintText = view.findViewById(R.id.scanHint);
        feedback = view.findViewById(R.id.scanFeedback);
        feedbackIcon = view.findViewById(R.id.scanFeedbackIcon);
        feedbackText = view.findViewById(R.id.scanFeedbackText);
        cartSummary = view.findViewById(R.id.scanCartSummary);
        input = view.findViewById(R.id.scanInput);

        boolean qr = mode == Mode.QR;
        ((TextView) view.findViewById(R.id.scanTitle))
                .setText(qr ? R.string.scan_qr_title : R.string.scan_barcode_title);
        input.setHint(qr ? R.string.scan_qr_hint : R.string.scan_barcode_hint);
        hintText.setText(qr ? R.string.scan_point_camera_qr : R.string.scan_point_camera);

        view.findViewById(R.id.scanClose).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.scanDone).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.scanAdd).setOnClickListener(v -> submitTypedCode());

        // A handheld scanner types the code then sends Enter — same path as tapping Add.
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean isDone = actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_NULL;
            if (!isDone) {
                return false;
            }
            // A hardware Enter (IME_NULL) reports both down and up — adding on each would
            // ring the item up twice, which is exactly the scanner case this path exists for.
            if (event != null && event.getAction() != KeyEvent.ACTION_DOWN) {
                return true;
            }
            submitTypedCode();
            return true;
        });

        torchButton.setOnClickListener(v -> toggleTorch());

        analysisExecutor = Executors.newSingleThreadExecutor();
        scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(primaryFormat(), otherFormats())
                .build());

        refreshCartSummary();
        if (codeHost() != null) {
            // Lookup mode: there is no POS sale to summarise.
            cartSummary.setVisibility(View.INVISIBLE);
            ((TextView) view.findViewById(R.id.scanAdd)).setText(R.string.market_scan_find);
        }
        ensureCamera();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            // Keep the sheet above the keyboard when the cashier types a code in.
            getDialog().getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    @Override
    public void onDestroyView() {
        // Release the camera before the views go: the analyzer holds this fragment's executor.
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        camera = null;
        cameraStarted = false;
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
            analysisExecutor = null;
        }
        if (scanner != null) {
            scanner.close();
            scanner = null;
        }
        super.onDestroyView();
    }

    // ---- Camera ---------------------------------------------------------

    /** Barcode mode leads with 1D retail symbologies; QR mode leads with QR. */
    private int primaryFormat() {
        return mode == Mode.QR ? Barcode.FORMAT_QR_CODE : Barcode.FORMAT_EAN_13;
    }

    private int[] otherFormats() {
        if (mode == Mode.QR) {
            return new int[]{Barcode.FORMAT_AZTEC, Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_PDF417};
        }
        return new int[]{
                Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_ITF, Barcode.FORMAT_CODABAR};
    }

    private void ensureCamera() {
        if (!requireContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            showCameraOff(R.string.scan_camera_unavailable_body, 0, null);
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            showCameraOff(R.string.scan_camera_permission_body, R.string.scan_allow_camera,
                    this::requestCameraPermission);
        }
    }

    private void requestCameraPermission() {
        permissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", requireContext().getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            // No settings activity to route to — manual entry still works.
        }
    }

    /**
     * Shows the placeholder over the preview.
     *
     * @param actionRes 0 (with a null action) hides the button — nothing the cashier can
     *                  do about it, so manual entry below is the only route
     */
    private void showCameraOff(@StringRes int bodyRes, @StringRes int actionRes,
                               @Nullable Runnable action) {
        if (!isAdded()) return;
        cameraOff.setVisibility(View.VISIBLE);
        previewView.setVisibility(View.GONE);
        reticle.setVisibility(View.GONE);
        hintText.setVisibility(View.GONE);
        torchButton.setVisibility(View.GONE);
        cameraOffText.setText(bodyRes);
        if (actionRes == 0 || action == null) {
            cameraOffAction.setVisibility(View.GONE);
            return;
        }
        cameraOffAction.setVisibility(View.VISIBLE);
        cameraOffAction.setText(actionRes);
        cameraOffAction.setOnClickListener(v -> action.run());
    }

    private void startCamera() {
        if (cameraStarted || !isAdded()) return;
        cameraStarted = true;
        final ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(requireContext());
        future.addListener(() -> {
            if (!isAdded()) return;
            try {
                bindCamera(future.get());
            } catch (Exception e) {
                cameraStarted = false;
                showCameraOff(R.string.scan_camera_failed_body, 0, null);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    // Consumes (rather than propagates) the ExperimentalGetImage opt-in that `analyze`
    // needs — the method reference below is what pulls it in here.
    @OptIn(markerClass = ExperimentalGetImage.class)
    private void bindCamera(ProcessCameraProvider provider) {
        if (getView() == null) return;   // sheet closed while the provider was warming up
        cameraProvider = provider;
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                // 720p is plenty to decode a shelf label and keeps the analyzer ahead of
                // the frame rate on the low-end handsets these tills run on.
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(analysisExecutor, this::analyze);

        provider.unbindAll();
        camera = provider.bindToLifecycle(getViewLifecycleOwner(),
                CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);

        previewView.setVisibility(View.VISIBLE);
        reticle.setVisibility(View.VISIBLE);
        hintText.setVisibility(View.VISIBLE);
        cameraOff.setVisibility(View.GONE);
        torchButton.setVisibility(
                camera.getCameraInfo().hasFlashUnit() ? View.VISIBLE : View.GONE);
    }

    /** ImageProxy.getImage() is the documented way to hand a CameraX frame to ML Kit. */
    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyze(@NonNull ImageProxy proxy) {
        android.media.Image image = proxy.getImage();
        BarcodeScanner active = scanner;
        if (image == null || active == null) {
            proxy.close();
            return;
        }
        InputImage frame = InputImage.fromMediaImage(
                image, proxy.getImageInfo().getRotationDegrees());
        active.process(frame)
                .addOnSuccessListener(this::onFrameDecoded)
                .addOnCompleteListener(task -> proxy.close());
    }

    /**
     * Scan separation, run on the main thread for every decoded frame.
     *
     * <p>A label sits in front of the lens for dozens of frames, so a code is rung up once
     * and then <em>held</em>: it cannot be rung up again until it has been out of frame for
     * {@link #SEPARATION_MS}. That is the physical gesture a cashier already makes — scan,
     * move the item aside, scan the next — and it means presenting the same item a second
     * time still deliberately adds a second unit. A short dead time after every accept
     * stops a second label in the same shot being swept up with the first.
     *
     * <p>Codes are held whatever the outcome, so an unknown or out-of-stock label reports
     * once instead of hammering the banner at frame rate.
     */
    private void onFrameDecoded(@NonNull List<Barcode> barcodes) {
        if (!isAdded()) return;
        long now = SystemClock.elapsedRealtime();

        Set<String> visible = new LinkedHashSet<>();
        for (Barcode barcode : barcodes) {
            String value = barcode.getRawValue();
            if (value != null && !value.trim().isEmpty()) {
                visible.add(value.trim());
            }
        }

        // Refresh the hold on anything still in view; release what has cleared the frame.
        Iterator<Map.Entry<String, Long>> held = heldCodes.entrySet().iterator();
        while (held.hasNext()) {
            Map.Entry<String, Long> entry = held.next();
            if (visible.contains(entry.getKey())) {
                entry.setValue(now);
            } else if (now - entry.getValue() >= SEPARATION_MS) {
                held.remove();
            }
        }
        updateScanState();

        if (now - lastAcceptedAt < DEAD_TIME_MS) {
            return;
        }
        for (String code : visible) {
            if (heldCodes.containsKey(code)) {
                continue;
            }
            heldCodes.put(code, now);
            lastAcceptedAt = now;
            updateScanState();
            buzz();
            resolve(code);
            return;
        }
    }

    /** Tells the cashier whether the lens is clear and ready, or still holding a scan. */
    private void updateScanState() {
        boolean holding = !heldCodes.isEmpty();
        if (holding == showingHeldState) {
            return;
        }
        showingHeldState = holding;
        if (holding) {
            hintText.setText(R.string.scan_separation_hint);
            reticle.setBackgroundResource(R.drawable.bg_scan_reticle_held);
        } else {
            hintText.setText(mode == Mode.QR
                    ? R.string.scan_point_camera_qr : R.string.scan_point_camera);
            reticle.setBackgroundResource(R.drawable.bg_scan_reticle);
        }
    }

    private void toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) return;
        torchOn = !torchOn;
        camera.getCameraControl().enableTorch(torchOn);
        torchButton.setImageResource(torchOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
    }

    /** Short confirmation tick so the cashier can keep their eyes on the goods. */
    private void buzz() {
        Vibrator vibrator = ContextCompat.getSystemService(requireContext(), Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(35);
        }
    }

    // ---- Resolve + report -----------------------------------------------

    private void submitTypedCode() {
        String code = input.getText().toString().trim();
        if (code.isEmpty()) {
            showFeedback(getString(R.string.scan_enter_code), Tone.WARNING);
            return;
        }
        // A typed code is an explicit request, so it skips the hold entirely. The dead time
        // still applies, so the camera cannot pile a scan on top of what was just typed.
        lastAcceptedAt = SystemClock.elapsedRealtime();
        resolve(code);
    }

    /** Match the code against the POS list, then add / explain. */
    private void resolve(String code) {
        CodeHost codeHost = codeHost();
        if (codeHost != null) {
            codeHost.onCodeScanned(code);
            dismissAllowingStateLoss();
            return;
        }
        Host host = host();
        List<PosProduct> products = host == null ? null : host.scanProducts();
        if (products == null || products.isEmpty()) {
            showFeedback(getString(R.string.scan_products_not_loaded), Tone.WARNING);
            return;
        }

        PosProduct product = ProductCodeMatcher.find(products, code);
        if (product == null) {
            showFeedback(getString(R.string.scan_not_found, code), Tone.ERROR);
            return;
        }
        if (!product.sellable()) {
            showFeedback(getString(R.string.scan_out_of_stock, product.name), Tone.WARNING);
            return;
        }

        Cart cart = Cart.get();
        if (cart.qtyOf(product.id) >= product.units) {
            // Cart.add() clamps at on-hand stock; say so rather than no-op silently.
            showFeedback(getString(R.string.scan_stock_limit, product.name, product.units),
                    Tone.WARNING);
            return;
        }
        cart.add(product);
        input.setText("");
        showFeedback(getString(R.string.scan_added, product.name, money(product.price)),
                Tone.SUCCESS);
        refreshCartSummary();
        if (host != null) {
            host.onScanCartChanged();
        }
    }

    private enum Tone { SUCCESS, WARNING, ERROR }

    private void showFeedback(String message, Tone tone) {
        feedback.setVisibility(View.VISIBLE);
        feedbackText.setText(message);
        int background, icon, tint;
        switch (tone) {
            case WARNING:
                background = R.drawable.bg_scan_banner_warning;
                icon = R.drawable.ic_alert_circle;
                tint = R.color.warning;
                break;
            case ERROR:
                background = R.drawable.bg_scan_banner_error;
                icon = R.drawable.ic_x_circle;
                tint = R.color.danger;
                break;
            case SUCCESS:
            default:
                background = R.drawable.bg_scan_banner_success;
                icon = R.drawable.ic_check_circle;
                tint = R.color.success;
                break;
        }
        feedback.setBackgroundResource(background);
        feedbackIcon.setImageResource(icon);
        feedbackIcon.setImageTintList(
                ContextCompat.getColorStateList(requireContext(), tint));
    }

    private void refreshCartSummary() {
        Cart cart = Cart.get();
        int count = cart.itemCount();
        cartSummary.setText(count == 0
                ? getString(R.string.scan_cart_empty)
                : getResources().getQuantityString(R.plurals.scan_cart_summary, count, count,
                        money(cart.subtotal())));
    }

    @Nullable
    private CodeHost codeHost() {
        if (getParentFragment() instanceof CodeHost) {
            return (CodeHost) getParentFragment();
        }
        Context context = getContext();
        return context instanceof CodeHost ? (CodeHost) context : null;
    }

    @Nullable
    private Host host() {
        if (getParentFragment() instanceof Host) {
            return (Host) getParentFragment();
        }
        Context context = getContext();
        return context instanceof Host ? (Host) context : null;
    }

    private String money(double value) {
        return String.format(Locale.US, "R%,.2f", value);
    }
}
