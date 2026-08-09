package com.example.localshare;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws expanding, fading concentric rings from the center of the view,
 * similar to the "device discoverable" radar animation used by nearby-share
 * style apps. Call start()/stop() to control the animation loop.
 */
public class PulseView extends View {

    private static final int RING_COUNT = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<ValueAnimator> animators = new ArrayList<>();
    private final float[] radiusFraction = new float[RING_COUNT];
    private final float[] alphaFraction = new float[RING_COUNT];
    private boolean running = false;

    public PulseView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(Color.parseColor("#7c93ff"));
    }

    public void start() {
        if (running) return;
        running = true;
        for (int i = 0; i < RING_COUNT; i++) {
            final int idx = i;
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(2400);
            animator.setStartDelay(i * 800L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                radiusFraction[idx] = f;
                alphaFraction[idx] = 1f - f;
                invalidate();
            });
            animator.start();
            animators.add(animator);
        }
    }

    public void stop() {
        running = false;
        for (ValueAnimator a : animators) a.cancel();
        animators.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!running) return;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxRadius = Math.min(getWidth(), getHeight()) / 2f;
        for (int i = 0; i < RING_COUNT; i++) {
            float radius = radiusFraction[i] * maxRadius;
            int alpha = (int) (alphaFraction[i] * 180);
            paint.setAlpha(Math.max(0, alpha));
            canvas.drawCircle(cx, cy, radius, paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }
}
