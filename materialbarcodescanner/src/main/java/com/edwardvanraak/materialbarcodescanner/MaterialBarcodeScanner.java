package com.edwardvanraak.materialbarcodescanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.view.View;

import com.google.android.gms.vision.barcode.Barcode;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class MaterialBarcodeScanner {

    /**
     * Request codes
     */
    public static final int RC_HANDLE_CAMERA_PERM = 2;

    /**
     * Scanner modes
     */
    static final int SCANNER_MODE_FREE = 1;
    static final int SCANNER_MODE_CENTER = 2;

    private final MaterialBarcodeScannerBuilder materialBarcodeScannerBuilder;

    private OnResultListener onResultListener;

    public MaterialBarcodeScanner(@NonNull MaterialBarcodeScannerBuilder materialBarcodeScannerBuilder) {
        this.materialBarcodeScannerBuilder = materialBarcodeScannerBuilder;
    }

    void setOnResultListener(OnResultListener onResultListener) {
        this.onResultListener = onResultListener;
    }

    MaterialBarcodeScannerBuilder getMaterialBarcodeScannerBuilder() {
        return materialBarcodeScannerBuilder;
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onBarcodeScannerResult(Barcode barcode) {
        onResultListener.onResult(barcode);
        EventBus.getDefault().removeStickyEvent(barcode);
        materialBarcodeScannerBuilder.clean();
    }

    /**
     * Start a scan for a barcode
     * <p>
     * This opens a new activity with the parameters provided by the MaterialBarcodeScannerBuilder
     */
    public void startScan() {
        EventBus.getDefault().register(this);
        if (materialBarcodeScannerBuilder.getActivity() == null) {
            throw new RuntimeException("Could not start scan: Activity reference lost (please rebuild the MaterialBarcodeScanner before calling startScan)");
        }
        int mCameraPermission = ActivityCompat.checkSelfPermission(materialBarcodeScannerBuilder.getActivity(),
                Manifest.permission.CAMERA);
        if (mCameraPermission != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        } else {
            EventBus.getDefault().postSticky(this);
            Intent intent = new Intent(materialBarcodeScannerBuilder.getActivity(),
                    MaterialBarcodeScannerActivity.class);
            materialBarcodeScannerBuilder.getActivity().startActivityForResult(intent,
                    MaterialBarcodeScannerActivity.REQUEST_CODE_SCANNER);
        }
    }

    private void requestCameraPermission() {
        final String[] permissions = new String[]{Manifest.permission.CAMERA};
        if (!ActivityCompat.shouldShowRequestPermissionRationale(materialBarcodeScannerBuilder.getActivity(),
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(materialBarcodeScannerBuilder.getActivity(),
                    permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(materialBarcodeScannerBuilder.getActivity(),
                        permissions, RC_HANDLE_CAMERA_PERM);
            }
        };
        Snackbar.make(materialBarcodeScannerBuilder.rootView, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(android.R.string.ok, listener)
                .show();
    }

    /**
     * Interface definition for a callback to be invoked when a view is clicked.
     */
    public interface OnResultListener {
        void onResult(Barcode barcode);
    }

}
