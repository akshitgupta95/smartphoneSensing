package com.tudelft.smartphonesensing;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FloorplanView extends View {
    enum SelectionMode {VIEWING, EDITING, PARTICLES}

    SelectionMode selectionMode = SelectionMode.VIEWING;
    MotionTracker tracker;
    Floorplan floorplan;
    String floorplanName;
    ParticleModel particleModel;
    boolean simulating = false;
    float viewportWidthMeters = 5;
    PointF viewportCenter = new PointF(0, 0);
    Floorplan.FloorEditable selectedElement = null;
    Matrix floorTransform = new Matrix();
    Matrix floorTransformInverse = new Matrix();
    List<Runnable> buttonsListeners = new ArrayList<>();

    public FloorplanView(Context context) {
        super(context);
        init();
    }

    public FloorplanView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void addButtonListener(Runnable cb) {
        buttonsListeners.add(cb);
    }

    public void setSelectionMode(SelectionMode selectionMode) {
        this.selectionMode = selectionMode;
        if (selectionMode != SelectionMode.EDITING) {
            selectedElement = null;
        }
        if (selectionMode != SelectionMode.PARTICLES) {
            simulating = false;
        }
        buttonsChanged();
    }

    public SelectionMode getSelectionMode() {
        return selectionMode;
    }

    public Floorplan getFloorplan() {
        return this.floorplan;
    }

    public String getFloorplanName() {
        return this.floorplanName;
    }

    public ParticleModel getParticleModel() {
        return this.particleModel;
    }

    public boolean getSimulating() {
        return this.simulating;
    }

    public void setSimulating(boolean simulating) {
        this.simulating = simulating;
    }

    private void buttonsChanged() {
        buttonsListeners.forEach(Runnable::run);
        invalidate();
    }

    public void saveAs(String name) {
        if (!name.equals(floorplanName)) {
            setFloorplan(floorplan, name);
        }
        AppDatabase db = AppDatabase.getInstance(getContext());
        FloorplanDataDAO.FloorplanData floordata = new FloorplanDataDAO.FloorplanData();
        floordata.setFloorplan(floorplan, floorplanName);
        //TODO do something with id here, currently saving a seperate version every time
        //use name as primary key?
        db.floorplanDataDAO().InsertAll(floordata);
    }

    public List<Floorplan.ElementAction> getActions() {
        List<Floorplan.ElementAction> actions = new ArrayList<>();
        if (this.selectionMode == SelectionMode.PARTICLES) {
            actions.add(Floorplan.ElementAction.shortHand(() -> simulating ? "Stop" : "Simulate", () -> {
                simulating = !simulating;
                buttonsChanged();
            }));
        }
        if (this.selectionMode == SelectionMode.EDITING) {
            actions.add(Floorplan.ElementAction.shortHand(() -> "Save", () -> {
                Util.showTextDialog(getContext(), "Save floorplan as", floorplanName, (name) -> {
                    if (name != null) {
                        saveAs(name);
                    }
                });
            }));
            actions.add(Floorplan.ElementAction.shortHand(() -> "Add rect", this::addRectangleObstacle));
            actions.add(Floorplan.ElementAction.shortHand(() -> "Align", this::setNorth));


            if (selectedElement != null) {
                actions.add(Floorplan.ElementAction.shortHand(() -> "Remove", () -> {
                    floorplan.removeElement(selectedElement);
                    selectedElement = null;
                    buttonsChanged();
                }));

                actions.addAll(selectedElement.getActions());

                if (selectedElement instanceof Floorplan.FloorplanBayesCell) {
                    Floorplan.FloorplanBayesCell cellelement = (Floorplan.FloorplanBayesCell) selectedElement;
                    actions.add(Floorplan.ElementAction.shortHand(() -> "Cell Settings", () -> {
                        //TODO copied from CellsAdapter.java, is this correct, can we combine there calls?
                        Bundle bundle = new Bundle();
                        int cellid = cellelement.getCellLocation();
                        if (cellid == -1) {
                            LocationCell cell = new LocationCell();
                            cell.setName("New cell");
                            //TODO shit, the room lib uses longs for id's, change this to long everywhere?
                            cellid = (int)AppDatabase.getInstance(getContext()).locationCellDAO().insert(cell);
                        }
                        bundle.putInt("cellName", cellid);
                        Fragment fragment = new CellFragment();
                        fragment.setArguments(bundle);
                        //TODO: Bad practice, decouple fragment and activity using some other method, use viewPager
                        MainActivity activity = (MainActivity) getContext();
                        activity.getSupportFragmentManager().beginTransaction().hide(activity.getActiveFragment()).replace(R.id.main_container, fragment).addToBackStack(null).commit();
                        activity.setActiveFragment(fragment);
                        Log.i("CLICK", "RecyclerView Item Click Position");
                    }));
                }
            }
        }

        return actions;
    }

    public void setFloorplan(Floorplan floorplan, String name) {
        this.floorplan = floorplan;
        this.floorplanName = name;
        selectedElement = null;
        buttonsChanged();
    }

    public void setParticleModel(ParticleModel particleModel) {
        this.particleModel = particleModel;
    }

    void addRectangleObstacle() {
        Floorplan.RectangleObstacle el = new Floorplan.RectangleObstacle();
        el.setArea(new RectF(viewportCenter.x - 0.5f, viewportCenter.y - 0.5f, viewportCenter.x + 0.5f, viewportCenter.y + 0.5f));
        floorplan.addElement(el);
        selectedElement = el;
        buttonsChanged();
    }

    void viewChanged() {
        floorTransform.reset();
        float viewportHeightMeters = viewportWidthMeters * getHeight() / getWidth();
        floorTransform.postTranslate(-viewportCenter.x, -viewportCenter.y);//move to center
        floorTransform.postScale(1 / viewportWidthMeters, -1 / viewportHeightMeters);//normalize to viewport coords
        floorTransform.postTranslate(0.5f, 0.5f);//put origin in center of screen
        floorTransform.postScale(getWidth(), getHeight());//convert to pixel coords
        floorTransform.invert(floorTransformInverse);
        invalidate();
    }

    void setNorth() {
        double angle = tracker.get2dNorthAngle();
        floorplan.setNorthAngleOffset(angle);
        Toast.makeText(getContext(), "Alligned map to phone axis", Toast.LENGTH_LONG).show();
        invalidate();
    }

    void changeSelection(Floorplan.FloorEditable element) {
        selectedElement = element;
        buttonsChanged();
    }

    @SuppressLint("ClickableViewAccessibility")
    void init() {
        initPaints();
        FlingHandler flinger = new FlingHandler();

        //TODO move this somewhere not ui related
        //TODO actually free this when done
        tracker = new MotionTracker(getContext(), (dx, dy) -> {
            if (particleModel != null) {
                particleModel.move(dx, dy);
                invalidate();
            }
        });

        floorplan = new Floorplan();

        try {
            floorplan.deserialize(new JSONObject(getContext().getString(R.string.default_floorplan_json)));
        } catch (JSONException err) {
            //TODO toast message
        }

        setOnTouchListener(new OnTouchListener() {
            int downcount = 0;
            boolean didmoveaction = false;

            ScaleGestureDetector pinchDetect = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                public boolean onScale(ScaleGestureDetector detector) {
                    if (selectedElement != null) {
                        float[] points = new float[]{0, 0, detector.getCurrentSpanX() - detector.getPreviousSpanX(), detector.getCurrentSpanY() - detector.getPreviousSpanY()};
                        floorTransformInverse.mapPoints(points);
                        //had to hard-code flipped y here because of the logical vs screen coords y flip
                        //not sure how to make the matrix do this flip
                        selectedElement.editScale(points[2] - points[0], -(points[3] - points[1]));
                        invalidate();
                    } else {
                        viewportWidthMeters /= detector.getScaleFactor();
                        viewChanged();
                    }
                    didmoveaction = true;
                    return true;
                }
            });

            GestureDetector scrollDetect = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    dragmove(distanceX, distanceY, false);
                    didmoveaction = true;
                    return true;
                }

                @Override
                public boolean onDown(MotionEvent e) {
                    flinger.stop();
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (selectedElement == null) {
                        //not sure in what unit velocity is, we need total pixel traveled for our anim and this seems to work
                        float scale = 0.2f;
                        flinger.fling(new PointF(velocityX * scale, velocityY * scale));
                    }
                    return true;
                }
            });

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                pinchDetect.onTouchEvent(e);
                scrollDetect.onTouchEvent(e);

                //TODO add some conditions about time/touch movements
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downcount++;
                }
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    downcount--;
                    if (downcount == 0) {
                        if (selectionMode == SelectionMode.EDITING) {
                            if (didmoveaction) {
                                floorplan.elementsChanged();
                                invalidate();
                            } else {
                                onClick(e.getX(), e.getY());
                            }
                        }
                        didmoveaction = false;
                    }
                }
                return true;
            }
        });
    }

    void onClick(float x, float y) {
        float[] point = new float[]{x, y};
        floorTransformInverse.mapPoints(point);

        Floorplan.FloorEditable match = (Floorplan.FloorEditable) floorplan.getElements().stream()
                .filter(el -> (el instanceof Floorplan.FloorEditable && ((Floorplan.FloorEditable) el).hitTest(point[0], point[1])))
                .findFirst().orElse(null);

        if (match != null) {
            selectedElement = match;
        } else {
            selectedElement = null;
        }
        buttonsChanged();
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
            dragmove(-difdisplacement.x, -difdisplacement.y, true);

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

    void dragmove(float dxpx, float dypx, boolean isfling) {
        float[] pts = new float[]{0f, 0f, dxpx, dypx};
        floorTransformInverse.mapPoints(pts);
        float dx = pts[2] - pts[0];
        float dy = pts[3] - pts[1];
        if (selectionMode == SelectionMode.PARTICLES && simulating && particleModel != null) {
            particleModel.move(-dx, -dy);
            invalidate();
        } else if (selectedElement != null) {
            if (!isfling) {
                selectedElement.editMove(-dx, -dy);
                invalidate();
            }
        } else {
            viewportCenter.x += dx;
            viewportCenter.y += dy;
            viewChanged();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewChanged();
    }

    Floorplan.PaintPalette palette;

    void initPaints() {
        palette = new Floorplan.PaintPalette();

        palette.background = new Paint();
        palette.background.setARGB(255, 150, 150, 150);

        palette.floor = new Paint();
        palette.floor.setARGB(255, 255, 255, 255);

        palette.lines = new Paint();
        palette.lines.setStrokeWidth(0.05f);
        palette.lines.setARGB(255, 0, 0, 0);
        palette.lines.setStyle(Paint.Style.STROKE);

        palette.text = new Paint();
        palette.text.setTextSize(60f);
        palette.text.setARGB(255, 0, 0, 0);
        palette.text.setLinearText(true);
        palette.text.setSubpixelText(true);
        palette.text.setTextAlign(Paint.Align.CENTER);
        palette.text.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, 0, getWidth(), getHeight(), palette.background);

        //save old transforms to stack and transform to map space
        canvas.save();
        canvas.setMatrix(floorTransform);

        //can now do all rendering in floorplan coordinates (meters)
        floorplan.render(canvas, palette);
        if (selectedElement != null) {
            canvas.drawPath(selectedElement.getContour(), palette.lines);
            selectedElement.drawEditInfo(canvas, floorTransform, palette);
        }
        if (selectionMode == SelectionMode.PARTICLES && particleModel != null) {
            particleModel.render(canvas);
        }

        //restore old transforms
        canvas.restore();
    }
}
