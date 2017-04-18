package com.edwardvanraak.materialbarcodescanner;

import android.app.Dialog;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static junit.framework.Assert.assertNotNull;

public class MaterialBarcodeScannerActivity extends AppCompatActivity {

    private static final int RC_HANDLE_GMS = 9001;
    private static final String TAG = "MaterialBarcodeScanner";

    private MaterialBarcodeScanner mMaterialBarcodeScanner;
    private MaterialBarcodeScannerBuilder mMaterialBarcodeScannerBuilder;

    private BarcodeDetector barcodeDetector;

    private CameraSourcePreview mCameraSourcePreview;

    private GraphicOverlay<BarcodeGraphic> mGraphicOverlay;

    private SoundPoolPlayer mSoundPoolPlayer;

    private TextView scannersQuantity;

    private Set<String> barcodes;

    private boolean mFlashOn = false;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (getWindow() != null) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            Log.e(TAG, "Barcode scanner could not go into fullscreen mode!");
        }
        setContentView(R.layout.barcode_capture);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onMaterialBarcodeScanner(MaterialBarcodeScanner materialBarcodeScanner) {
        this.mMaterialBarcodeScanner = materialBarcodeScanner;
        mMaterialBarcodeScannerBuilder = mMaterialBarcodeScanner.getMaterialBarcodeScannerBuilder();
        barcodeDetector = mMaterialBarcodeScanner.getMaterialBarcodeScannerBuilder().getBarcodeDetector();
        startCameraSource();
        setupLayout();
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
                mGraphicOverlay.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                }, 50);
            }
        });
        flashToggleIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFlashOn) {
                    flashToggleIcon.setBackgroundResource(R.drawable.ic_flash_on_white_24dp);
                    disableTorch();
                } else {
                    flashToggleIcon.setBackgroundResource(R.drawable.ic_flash_off_white_24dp);
                    enableTorch();
                }
                mFlashOn ^= true;
            }
        });
        if (mMaterialBarcodeScannerBuilder.isFlashEnabledByDefault()) {
            flashToggleIcon.setBackgroundResource(R.drawable.ic_flash_off_white_24dp);
        }
    }

    private void setupCenterTracker() {
        if (mMaterialBarcodeScannerBuilder.getScannerMode() == MaterialBarcodeScanner.SCANNER_MODE_CENTER) {
            final ImageView centerTracker = (ImageView) findViewById(R.id.barcode_square);
            assertNotNull(centerTracker);
            centerTracker.setImageResource(mMaterialBarcodeScannerBuilder.getTrackerResourceID());
            mGraphicOverlay.setVisibility(View.INVISIBLE);
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

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() throws SecurityException {
        barcodes = new HashSet<>();

        // check that the device has play services available.
        mSoundPoolPlayer = new SoundPoolPlayer(this);
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dialog.show();
        }
        mGraphicOverlay = (GraphicOverlay<BarcodeGraphic>) findViewById(R.id.graphicOverlay);
        BarcodeGraphicTracker.NewDetectionListener listener = new BarcodeGraphicTracker.NewDetectionListener() {
            @Override
            public void onNewDetection(Barcode barcode) {
                Log.d(TAG, "Barcode detected! - " + barcode.displayValue);
                if (barcodes != null) {
                    barcodes.add(barcode.displayValue);
                    setScannersQuantity(barcodes.size());
                }
                EventBus.getDefault().postSticky(barcode);
                updateCenterTrackerAfterDetectedState();
                if (mMaterialBarcodeScannerBuilder.isBleepEnabled()) {
                    mSoundPoolPlayer.playShortResource(R.raw.bleep);
                }
            }
        };
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(mGraphicOverlay, listener,
                mMaterialBarcodeScannerBuilder.getTrackerColor());
        barcodeDetector.setProcessor(new MultiProcessor.Builder<>(barcodeFactory).build());
        CameraSource mCameraSource = mMaterialBarcodeScannerBuilder.getCameraSource();
        if (mCameraSource != null) {
            try {
                mCameraSourcePreview = (CameraSourcePreview) findViewById(R.id.preview);
                mCameraSourcePreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
            }
        }
    }

    private void updateCenterTrackerAfterDetectedState() {
        if (mMaterialBarcodeScannerBuilder.getScannerMode() == MaterialBarcodeScanner.SCANNER_MODE_CENTER) {
            final ImageView centerTracker = (ImageView) findViewById(R.id.barcode_square);
            assertNotNull(centerTracker);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    new CountDownTimer(500, 500) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            centerTracker.setImageResource(mMaterialBarcodeScannerBuilder.getTrackerDetectedResourceID());
                        }

                        @Override
                        public void onFinish() {
                            centerTracker.setImageResource(mMaterialBarcodeScannerBuilder.getTrackerResourceID());
                        }
                    }.start();
                }
            });
        }
    }

    private void enableTorch() throws SecurityException {
        mMaterialBarcodeScannerBuilder.getCameraSource().setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        try {
            mMaterialBarcodeScannerBuilder.getCameraSource().start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void disableTorch() throws SecurityException {
        mMaterialBarcodeScannerBuilder.getCameraSource().setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        try {
            mMaterialBarcodeScannerBuilder.getCameraSource().start();
        } catch (IOException e) {
            e.printStackTrace();
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
        if (mCameraSourcePreview != null) {
            mCameraSourcePreview.stop();
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

    private void clean() {
        EventBus.getDefault().removeStickyEvent(MaterialBarcodeScanner.class);
        if (barcodes != null) {
            barcodes.clear();
        }
        if (mCameraSourcePreview != null) {
            mCameraSourcePreview.release();
            mCameraSourcePreview = null;
        }
        if (mSoundPoolPlayer != null) {
            mSoundPoolPlayer.release();
            mSoundPoolPlayer = null;
        }
    }
}
