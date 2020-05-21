package com.tudelft.smartphonesensing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

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
        floorplan = new Floorplan();
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


        for (Floorplan.FloorElement el : floorplan.getElements()) {
            el.render(canvas);
        }

        //restore old transforms
        canvas.restore();
    }
}
