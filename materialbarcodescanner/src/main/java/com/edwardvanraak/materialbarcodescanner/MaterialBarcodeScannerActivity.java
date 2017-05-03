package com.edwardvanraak.materialbarcodescanner;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static junit.framework.Assert.assertNotNull;

public class MaterialBarcodeScannerActivity extends AppCompatActivity {

    private static final String TAG = "MaterialBarcodeScanner";

    public static final int REQUEST_CODE_SCANNER = 100;
    private static final int RC_HANDLE_GMS = 9001;

    private MaterialBarcodeScannerBuilder materialBarcodeScannerBuilder;
    private BarcodeDetector barcodeDetector;

    private CameraSourcePreview cameraSourcePreview;
    private GraphicOverlay<BarcodeGraphic> barcodeGraphicOverlay;
    private SoundPoolPlayer soundPoolPlayer;

    private TextView scannersQuantity;
    private Set<String> barcodes;

    private boolean flashOn = false;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (getWindow() != null) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            Log.e(TAG, getString(R.string.fullscreen_mode_error));
        }
        setContentView(R.layout.barcode_capture);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onMaterialBarcodeScanner(MaterialBarcodeScanner materialBarcodeScanner) {
        materialBarcodeScannerBuilder = materialBarcodeScanner.getMaterialBarcodeScannerBuilder();
        barcodeDetector = materialBarcodeScanner.getMaterialBarcodeScannerBuilder().getBarcodeDetector();
        startBarcodeCameraSource();
        setupLayout();
    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startBarcodeCameraSource() throws SecurityException {
        barcodes = new HashSet<>();
        // check that the device has play services available.
        soundPoolPlayer = new SoundPoolPlayer(this);
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dialog.show();
        }
        barcodeGraphicOverlay = (GraphicOverlay<BarcodeGraphic>) findViewById(R.id.graphicOverlay);
        BarcodeGraphicTracker.NewDetectionListener listener = new BarcodeGraphicTracker.NewDetectionListener() {
            @Override
            public void onNewDetection(Barcode barcode) {
                Log.d(TAG, "Barcode detected! - " + barcode.displayValue);
                if (barcodes != null) {
                    barcodes.add(barcode.displayValue);
                    setScannersQuantity(barcodes.size());
                    startOcrCameraDetector();
                }
                EventBus.getDefault().postSticky(barcode);
                updateCenterTrackerAfterDetectedState();
                if (materialBarcodeScannerBuilder.isBleepEnabled()) {
                    soundPoolPlayer.playShortResource(R.raw.bleep);
                }
            }
        };
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(barcodeGraphicOverlay, listener,
                materialBarcodeScannerBuilder.getTrackerColor());
        barcodeDetector.setProcessor(new MultiProcessor.Builder<>(barcodeFactory).build());
        CameraSource cameraSource = materialBarcodeScannerBuilder.getCameraSource();
        if (cameraSource != null) {
            try {
                cameraSourcePreview = (CameraSourcePreview) findViewById(R.id.preview);
                cameraSourcePreview.start(cameraSource, barcodeGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, getString(R.string.barcode_camera_source_error), e);
                cameraSource.release();
            }
        }
    }

    /**
     * Starts OCR detector on frame returned from camera.
     */
    @SuppressLint("InlinedApi")
    private void startOcrCameraDetector() {
        Context context = getApplicationContext();

        TextRecognizer textRecognizer = new TextRecognizer.Builder(context).build();
        SparseArray<TextBlock> sparseArray = textRecognizer.detect(materialBarcodeScannerBuilder
                .getCameraSource().getOutputFrame());

        //TODO: Make actions based on text values found
        if (sparseArray.size() > 0) {
            for (int i = 0; i < sparseArray.size(); i++) {
                Log.d(TAG, sparseArray.valueAt(i).getValue());
            }
        }

        if (!textRecognizer.isOperational()) {
            Log.w(TAG, getString(R.string.text_recognizer_dependencies_error));

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowStorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowStorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupLayout() {
        scannersQuantity = (TextView) findViewById(R.id.barcode_quantity);
        assertNotNull(scannersQuantity);
        setupButtons();
        setupCenterTracker();
        setScannersQuantity(0);
    }

    private void setupButtons() {
        final TextView doneIcon = (TextView) findViewById(R.id.doneIcon);
        final ImageView flashToggleIcon = (ImageView) findViewById(R.id.flashIcon);
        assertNotNull(doneIcon);
        assertNotNull(flashToggleIcon);
        doneIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                barcodeGraphicOverlay.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setResult(RESULT_OK);
                        finish();
                    }
                }, 50);
            }
        });
        flashToggleIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (flashOn) {
                    flashToggleIcon.setBackgroundResource(R.drawable.ic_flash_on_white_24dp);
                    disableTorch();
                } else {
                    flashToggleIcon.setBackgroundResource(R.drawable.ic_flash_off_white_24dp);
                    enableTorch();
                }
                flashOn ^= true;
            }
        });
        if (materialBarcodeScannerBuilder.isFlashEnabledByDefault()) {
            flashToggleIcon.setBackgroundResource(R.drawable.ic_flash_off_white_24dp);
        }
    }

    private void setupCenterTracker() {
        if (materialBarcodeScannerBuilder.getScannerMode() == MaterialBarcodeScanner.SCANNER_MODE_CENTER) {
            final ImageView centerTracker = (ImageView) findViewById(R.id.barcode_square);
            assertNotNull(centerTracker);
            centerTracker.setImageResource(materialBarcodeScannerBuilder.getTrackerResourceID());
            barcodeGraphicOverlay.setVisibility(View.INVISIBLE);
        }
    }

    private void setScannersQuantity(final int quantity) {
        scannersQuantity.post(new Runnable() {
            @Override
            public void run() {
                scannersQuantity.setText(getString(R.string.barcode_scanner_quantity, quantity));
            }
        });
    }

    private void updateCenterTrackerAfterDetectedState() {
        if (materialBarcodeScannerBuilder.getScannerMode() == MaterialBarcodeScanner.SCANNER_MODE_CENTER) {
            final ImageView centerTracker = (ImageView) findViewById(R.id.barcode_square);
            assertNotNull(centerTracker);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    new CountDownTimer(500, 100) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            centerTracker.setImageResource(materialBarcodeScannerBuilder.getTrackerDetectedResourceID());
                        }

                        @Override
                        public void onFinish() {
                            centerTracker.setImageResource(materialBarcodeScannerBuilder.getTrackerResourceID());
                        }
                    }.start();
                }
            });
        }
    }

    private void enableTorch() throws SecurityException {
        materialBarcodeScannerBuilder.getCameraSource().setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        try {
            materialBarcodeScannerBuilder.getCameraSource().start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void disableTorch() throws SecurityException {
        materialBarcodeScannerBuilder.getCameraSource().setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        try {
            materialBarcodeScannerBuilder.getCameraSource().start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void clean() {
        EventBus.getDefault().removeStickyEvent(MaterialBarcodeScanner.class);
        if (barcodes != null) {
            barcodes.clear();
        }
        if (cameraSourcePreview != null) {
            cameraSourcePreview.release();
            cameraSourcePreview = null;
        }
        if (soundPoolPlayer != null) {
            soundPoolPlayer.release();
            soundPoolPlayer = null;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (cameraSourcePreview != null) {
            cameraSourcePreview.stop();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detectors, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            clean();
        }
    }

}
