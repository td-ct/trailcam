package de.tdct.trailcam;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class PickOverlayView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float x = -1f;
    private float y = -1f;

    public PickOverlayView(Context context) {
        super(context);
        init();
    }

    public PickOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(0xFF4CE05A);
        float density = getResources().getDisplayMetrics().density;
        ringPaint.setStrokeWidth(2f * density);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0x664CE05A);
        setClickable(false);
        setFocusable(false);
    }

    public void show(float px, float py) {
        x = px;
        y = py;
        invalidate();
    }

    public void hide() {
        x = -1f;
        y = -1f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (x < 0f || y < 0f) return;
        float density = getResources().getDisplayMetrics().density;
        canvas.drawCircle(x, y, 14f * density, ringPaint);
        float sampledRadius = Math.max(2.5f * density, 3f * density);
        canvas.drawCircle(x, y, sampledRadius, dotPaint);
        canvas.drawLine(x - 20f * density, y, x - 16f * density, y, ringPaint);
        canvas.drawLine(x + 16f * density, y, x + 20f * density, y, ringPaint);
        canvas.drawLine(x, y - 20f * density, x, y - 16f * density, ringPaint);
        canvas.drawLine(x, y + 16f * density, x, y + 20f * density, ringPaint);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return false;
    }
}
