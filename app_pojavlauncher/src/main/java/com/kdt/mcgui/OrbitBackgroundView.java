package com.kdt.mcgui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import net.kdt.pojavlaunch.R;

/**
 * Orbit's launcher background, the same look as Orbit on PC: Minecraft's title-screen world (the four side
 * faces of the panorama, side by side) turning slowly behind a dark glass shade that is heaviest on the left and
 * at the bottom, where the buttons sit. Redraws at the display rate while visible.
 */
public class OrbitBackgroundView extends View {
    private static final float SECONDS_PER_TURN = 160f;   // same speed as the PC launcher's spinning world
    private final Paint mImg = new Paint(Paint.FILTER_BITMAP_FLAG), mShadeX = new Paint(), mShadeY = new Paint(), mTop = new Paint();
    private final Rect mSrc = new Rect();
    private final RectF mDst = new RectF();
    private final long mStart = SystemClock.uptimeMillis();
    private Bitmap mStrip;
    private boolean mRunning;

    public OrbitBackgroundView(Context c) { this(c, null); }

    public OrbitBackgroundView(Context c, AttributeSet a) {
        super(c, a);
        try { mStrip = BitmapFactory.decodeResource(getResources(), R.drawable.orbit_panorama); } catch (Throwable ignored) { }
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); mRunning = true; postInvalidateOnAnimation(); }
    @Override protected void onDetachedFromWindow() { mRunning = false; super.onDetachedFromWindow(); }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        mShadeX.setShader(new LinearGradient(0, 0, w, 0, new int[]{0xD1030508, 0x73030508, 0x0D030508}, new float[]{0f, 0.42f, 0.7f}, Shader.TileMode.CLAMP));
        mShadeY.setShader(new LinearGradient(0, h, 0, h * 0.62f, 0xD9030508, 0x00030508, Shader.TileMode.CLAMP));
        mTop.setShader(new LinearGradient(0, 0, 0, h * 0.22f, 0xB3030508, 0x00030508, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(0xFF05070A);
        if (mStrip != null) {
            float t = (SystemClock.uptimeMillis() - mStart) / 1000f;
            int sw = mStrip.getWidth(), sh = mStrip.getHeight();
            float scale = h * 1.12f / sh;                       // a touch taller than the screen so the bob never shows an edge
            float drawW = sw * scale, top = -h * 0.06f + (float) Math.sin(t * 0.21) * h * 0.02f;
            float x = -((t / SECONDS_PER_TURN) % 1f) * drawW;
            for (float sx = x; sx < w; sx += drawW) {             // wrap around: the strip is a full turn
                mSrc.set(0, 0, sw, sh);
                mDst.set(sx, top, sx + drawW, top + sh * scale);
                c.drawBitmap(mStrip, mSrc, mDst, mImg);
            }
        }
        c.drawRect(0, 0, w, h, mShadeX);
        c.drawRect(0, 0, w, h, mShadeY);
        c.drawRect(0, 0, w, h, mTop);
        if (mRunning) postInvalidateOnAnimation();
    }
}
