package com.tudelft.smartphonesensing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;

public class FloorplanView extends View {
    Floorplan floorplan;
    float viewportWidthMeters = 5;
    PointF viewportCenter = new PointF(0, 0);
    float rotation = 0;

    public FloorplanView(Context context) {
        super(context);
        init();
    }

    public FloorplanView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    void init() {
        FlingHandler flinger = new FlingHandler();

        floorplan = new Floorplan();

        try {
            floorplan.deserialize(new JSONObject(getContext().getString(R.string.default_floorplan_json)));
        } catch (JSONException err) {
            //TODO toast message
        }

        ScaleGestureDetector pinchDetect = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            public boolean onScale(ScaleGestureDetector detector) {
                viewportWidthMeters /= detector.getScaleFactor();
                invalidate();
                return true;
            }
        });
        GestureDetector scrollDetect = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                float metersPerPixel = viewportWidthMeters / getWidth();
                viewportCenter.x += distanceX * metersPerPixel;
                viewportCenter.y += distanceY * metersPerPixel;
                invalidate();
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                flinger.stop();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                //not sure in what unit velocity is, we need total pixel traveled for our anim and this seems to work
                float scale = 0.2f;
                flinger.fling(new PointF(velocityX * scale, velocityY * scale));
                return true;
            }
        });

        setOnTouchListener((v, e) -> {
            pinchDetect.onTouchEvent(e);
            scrollDetect.onTouchEvent(e);
            return true;
        });
    }

    class FlingHandler implements Runnable {
        PointF velocity;
        long animstart;
        PointF previousDisplacement;
        float animduration;

        final int interval = 20;//every 20ms
        Handler handler = new Handler();

        @Override
        public void run() {
            if (animstart == 0) {
                return;
            }
            //time of anim 0-1
            float timeprogress = Math.min(1, (System.currentTimeMillis() - animstart) / animduration);
            //displacement of anim 0-1
            float posprogress = 1 - (timeprogress - 1) * (timeprogress - 1);
            //actual displacement caused by the animation
            PointF displacement = new PointF(velocity.x * posprogress, velocity.y * posprogress);
            PointF difdisplacement = new PointF(displacement.x - previousDisplacement.x, displacement.y - previousDisplacement.y);
            //convert from pixel space to floorplan space
            float scaling = viewportWidthMeters / getWidth();
            viewportCenter.x -= scaling * difdisplacement.x;
            viewportCenter.y -= scaling * difdisplacement.y;
            invalidate();

            previousDisplacement = displacement;
            if (timeprogress < 0.99) {
                //TODO does android have a "time until next frame paint" api? (window.requestAnimationFrame() in js)
                handler.postDelayed(this, 10);
            }
        }

        void fling(PointF velocity) {
            this.animstart = System.currentTimeMillis();
            this.velocity = velocity;
            previousDisplacement = new PointF(0, 0);
            //bit experimental
            this.animduration = (float) Math.sqrt(velocity.length() / 800f) * 1000;
            handler.post(this);
        }

        void stop() {
            animstart = 0;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Paint bg = new Paint();
        bg.setARGB(255, 255, 128, 128);
        canvas.drawRect(0, 0, getWidth(), getHeight(), bg);

        //save old transforms to stack
        canvas.save();
        
        Matrix view = new Matrix();
        float viewportHeightMeters = viewportWidthMeters * getHeight() / getWidth();
        view.postTranslate(-viewportCenter.x, -viewportCenter.y);//move to center
        view.postScale(1 / viewportWidthMeters, 1 / viewportHeightMeters);//normalize to viewport coords
        view.postTranslate(0.5f, 0.5f);//put origin in center of screen
        view.postScale(getWidth(), getHeight());//convert to pixel coords
        canvas.setMatrix(view);

        //can now do all rendering in floorplan coordinates (meters)
        floorplan.render(canvas);

        //restore old transforms
        canvas.restore();
    }
}
