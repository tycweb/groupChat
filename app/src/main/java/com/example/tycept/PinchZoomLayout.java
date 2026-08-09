package com.example.tycept;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Wraps a single child (the full-screen photo ImageView, or the PlayerView) in
 * MediaViewerActivity and adds pinch-to-zoom, drag-to-pan while zoomed in, and
 * double-tap to zoom — the "zooming feature when opening videos/photos" the
 * chat bubbles themselves don't need but the full-screen viewer does.
 *
 * Zoom/pan are applied as scaleX/Y + translationX/Y on the child view rather
 * than a Matrix, so the exact same code works for both an ImageView and a
 * PlayerView without needing separate implementations.
 *
 * This layout owns all touch input on itself (it always intercepts) rather
 * than trying to selectively pass single taps through to the child — for a
 * video, MediaViewerActivity is told about taps via setOnSingleTapListener
 * and toggles the PlayerView's controller itself, so tapping to reveal
 * controls still works even though the PlayerView never sees the raw touch.
 */
public class PinchZoomLayout extends FrameLayout {

    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 4f;
    private static final float DOUBLE_TAP_SCALE = 2.5f;

    public interface OnSingleTapListener {
        void onSingleTap();
    }

    private float scale = 1f;
    private float posX = 0f;
    private float posY = 0f;
    private float lastTouchX, lastTouchY;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private OnSingleTapListener singleTapListener;

    public PinchZoomLayout(Context context) {
        super(context);
        setup(context);
    }

    public PinchZoomLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup(context);
    }

    private void setup(Context context) {
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void setOnSingleTapListener(OnSingleTapListener listener) {
        this.singleTapListener = listener;
    }

    /** Call when fresh media is loaded so zoom/pan start back at 1x, centered. */
    public void resetZoom() {
        scale = 1f;
        posX = 0f;
        posY = 0f;
        applyTransform();
    }

    private View child() {
        return getChildCount() > 0 ? getChildAt(0) : null;
    }

    private void applyTransform() {
        View c = child();
        if (c == null) return;
        c.setScaleX(scale);
        c.setScaleY(scale);
        c.setTranslationX(posX);
        c.setTranslationY(posY);
    }

    private void clampPosition() {
        View c = child();
        if (c == null) return;
        float extraW = Math.max(0f, c.getWidth() * (scale - 1f) / 2f);
        float extraH = Math.max(0f, c.getHeight() * (scale - 1f) / 2f);
        if (posX > extraW) posX = extraW;
        if (posX < -extraW) posX = -extraW;
        if (posY > extraH) posY = extraH;
        if (posY < -extraH) posY = -extraH;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We always handle touch ourselves (see class doc) so zoom/pan work
        // uniformly no matter what the child is.
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                activePointerId = event.getPointerId(0);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (scale > 1.01f && !scaleDetector.isInProgress()) {
                    int pointerIndex = event.findPointerIndex(activePointerId);
                    if (pointerIndex != -1) {
                        float x = event.getX(pointerIndex);
                        float y = event.getY(pointerIndex);
                        posX += (x - lastTouchX);
                        posY += (y - lastTouchY);
                        clampPosition();
                        applyTransform();
                        lastTouchX = x;
                        lastTouchY = y;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                // If the finger we were tracking lifted, fall back to whichever
                // pointer is left so panning doesn't jump on the next move.
                int pointerIndex = event.getActionIndex();
                int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == activePointerId) {
                    int newIndex = pointerIndex == 0 ? 1 : 0;
                    if (newIndex < event.getPointerCount()) {
                        lastTouchX = event.getX(newIndex);
                        lastTouchY = event.getY(newIndex);
                        activePointerId = event.getPointerId(newIndex);
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                break;
            }
        }
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scale *= detector.getScaleFactor();
            if (scale < MIN_SCALE) scale = MIN_SCALE;
            if (scale > MAX_SCALE) scale = MAX_SCALE;
            clampPosition();
            applyTransform();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (scale > 1.01f) {
                scale = 1f;
                posX = 0f;
                posY = 0f;
            } else {
                scale = DOUBLE_TAP_SCALE;
            }
            clampPosition();
            applyTransform();
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (singleTapListener != null) {
                singleTapListener.onSingleTap();
            }
            return true;
        }
    }
}
