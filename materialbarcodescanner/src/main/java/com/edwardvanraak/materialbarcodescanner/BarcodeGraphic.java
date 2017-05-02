/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.edwardvanraak.materialbarcodescanner;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.google.android.gms.vision.barcode.Barcode;

/**
 * Graphic instance for rendering barcode position, size, and ID within an associated graphic
 * overlay view.
 */
class BarcodeGraphic extends GraphicOverlay.Graphic {

    private Paint rectPaint;
    private Paint textPaint;
    private volatile Barcode barcode;

    private int id;
    private int strokeWidth = 24;
    private int cornerWidth = 64;
    private int cornerPadding = strokeWidth / 2;

    BarcodeGraphic(GraphicOverlay overlay, final int trackerColor) {
        super(overlay);

        rectPaint = new Paint();
        rectPaint.setColor(trackerColor);
        rectPaint.setStyle(Paint.Style.STROKE);
        rectPaint.setStrokeWidth(strokeWidth);

        textPaint = new Paint();
        textPaint.setColor(trackerColor);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(46.0f);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Barcode getBarcode() {
        return barcode;
    }

    /**
     * Updates the barcode instance from the detection of the most recent frame.  Invalidates the
     * relevant portions of the overlay to trigger a redraw.
     */
    void updateItem(Barcode barcode) {
        this.barcode = barcode;
        postInvalidate();
    }

    /**
     * Draws the barcode annotations for position, size, and raw value on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        Barcode barcode = this.barcode;
        if (barcode == null) {
            return;
        }

        // Draws the bounding box around the barcode.
        RectF rect = new RectF(barcode.getBoundingBox());
        rect.left = translateX(rect.left);
        rect.top = translateY(rect.top);
        rect.right = translateX(rect.right);
        rect.bottom = translateY(rect.bottom);

        //canvas.drawRect(rect, rectPaint);

        /**
         * Draw the top left corner
         */
        canvas.drawLine(rect.left - cornerPadding, rect.top, rect.left + cornerWidth, rect.top, rectPaint);
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + cornerWidth, rectPaint);

        /**
         * Draw the bottom left corner
         */
        canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - cornerWidth, rectPaint);
        canvas.drawLine(rect.left - cornerPadding, rect.bottom, rect.left + cornerWidth, rect.bottom, rectPaint);

        /**
         * Draw the top right corner
         */
        canvas.drawLine(rect.right + cornerPadding, rect.top, rect.right - cornerWidth, rect.top, rectPaint);
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerWidth, rectPaint);

        /**
         * Draw the bottom right corner
         */
        canvas.drawLine(rect.right + cornerPadding, rect.bottom, rect.right - cornerWidth, rect.bottom, rectPaint);
        canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - cornerWidth, rectPaint);

        // Draws a label at the bottom of the barcode indicate the barcode value that was detected.
        canvas.drawText(barcode.displayValue, rect.left, rect.bottom + 100, textPaint);
    }

}
