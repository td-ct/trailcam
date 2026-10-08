package de.tdct.trailcam;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class RangeSeekBar extends View {

    public interface OnRangeChangeListener {
        void onRangeChanged(float min, float max);
    }

    private static final float THUMB_RADIUS_DP = 12f;
    private static final float TRACK_HEIGHT_DP = 4f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    private float min = 0f;
    private float max = 1f;
    private float thumbRadius;
    private float trackHeight;
    private int activeThumb = -1;
    private boolean enabled = true;
    private OnRangeChangeListener listener;

    private float valueMin = 0f;
    private float valueMax = 1f;

    public RangeSeekBar(Context context) {
        super(context);
        init(context);
    }

    public RangeSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        thumbRadius = THUMB_RADIUS_DP * density;
        trackHeight = TRACK_HEIGHT_DP * density;
        trackPaint.setColor(0x66FFFFFF);
        activeTrackPaint.setColor(0xFF4CE05A);
        thumbPaint.setColor(0xFFFFFFFF);
        setMinimumHeight((int) (thumbRadius * 4));
    }

    public void setOnRangeChangeListener(OnRangeChangeListener l) {
        listener = l;
    }

    public void setRangeEnabled(boolean e) {
        enabled = e;
        setAlpha(e ? 1f : 0.4f);
        if (!e) activeThumb = -1;
        invalidate();
    }

    public void setValues(float minV, float maxV) {
        valueMin = clamp(minV);
        valueMax = clamp(maxV);
        if (valueMax < valueMin) valueMax = valueMin;
        invalidate();
    }

    public float getMinValue() {
        return valueMin;
    }

    public float getMaxValue() {
        return valueMax;
    }

    private float clamp(float v) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cy = getHeight() / 2f;
        float left = getPaddingLeft() + thumbRadius;
        float right = getWidth() - getPaddingRight() - thumbRadius;
        if (right - left <= 0) return;
        float y = cy - trackHeight / 2f;
        bounds.set(left, y, right, y + trackHeight);
        canvas.drawRoundRect(bounds, trackHeight / 2f, trackHeight / 2f, trackPaint);
        float xMin = left + valueMin * (right - left);
        float xMax = left + valueMax * (right - left);
        bounds.set(xMin, y, xMax, y + trackHeight);
        canvas.drawRoundRect(bounds, trackHeight / 2f, trackHeight / 2f, activeTrackPaint);
        canvas.drawCircle(xMin, cy, thumbRadius, thumbPaint);
        canvas.drawCircle(xMax, cy, thumbRadius, thumbPaint);
    }

    private float posToValue(float x) {
        float left = getPaddingLeft() + thumbRadius;
        float right = getWidth() - getPaddingRight() - thumbRadius;
        if (right - left <= 0) return 0f;
        return clamp((x - left) / (right - left));
    }

    private float valueToPos(float v) {
        float left = getPaddingLeft() + thumbRadius;
        float right = getWidth() - getPaddingRight() - thumbRadius;
        return left + v * (right - left);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!enabled) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                float x = event.getX();
                float xMin = valueToPos(valueMin);
                float xMax = valueToPos(valueMax);
                float dMin = Math.abs(x - xMin);
                float dMax = Math.abs(x - xMax);
                activeThumb = (dMin <= dMax) ? 0 : 1;
                updateActive(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (activeThumb >= 0) updateActive(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeThumb = -1;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void updateActive(float x) {
        float v = posToValue(x);
        if (activeThumb == 0) {
            valueMin = Math.min(v, valueMax);
        } else if (activeThumb == 1) {
            valueMax = Math.max(v, valueMin);
        }
        invalidate();
        if (listener != null) listener.onRangeChanged(valueMin, valueMax);
    }
}
