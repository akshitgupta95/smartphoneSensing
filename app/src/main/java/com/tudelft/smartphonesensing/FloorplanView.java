package com.tudelft.smartphonesensing;

import android.annotation.SuppressLint;
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
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class FloorplanView extends View {
    enum SelectionMode {VIEWING, EDITING, PARTICLES}

    SelectionMode selectionMode = SelectionMode.VIEWING;
    ModelState model;
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
            model.saveFloorplan();
        }
        if (selectionMode != SelectionMode.PARTICLES) {
            simulating = false;
        }
        buttonsChanged();
    }

    public SelectionMode getSelectionMode() {
        return selectionMode;
    }

    private void buttonsChanged() {
        buttonsListeners.forEach(Runnable::run);
        invalidate();
    }

    public List<Floorplan.ElementAction> getActions() {
        List<Floorplan.ElementAction> actions = new ArrayList<>();
        if (this.selectionMode == SelectionMode.PARTICLES) {
            actions.add(Floorplan.ElementAction.shortHand(() -> simulating ? "Stop" : "Simulate", () -> {
                simulating = !simulating;
                buttonsChanged();
            }));
            actions.add(Floorplan.ElementAction.shortHand(() -> "triangles", () -> {
                renderOpts.drawboxes = !renderOpts.drawboxes;
                buttonsChanged();
            }));
        }
        if (this.selectionMode == SelectionMode.EDITING) {
            if (selectedElement == null) {
                actions.add(Floorplan.ElementAction.shortHand(() -> "Align", model::AlignFloorAxis));
                actions.add(Floorplan.ElementAction.shortHand(() -> "Add rect", this::addRectangleObstacle));
                actions.add(Floorplan.ElementAction.shortHand(() -> "Furniture", this::addRectangleBlocking));
            } else {
                actions.add(Floorplan.ElementAction.shortHand(() -> "Remove", () -> {
                    model.getFloorplan().removeElement(selectedElement);
                    selectedElement = null;
                    buttonsChanged();
                }));

                actions.addAll(selectedElement.getActions());

                if (selectedElement instanceof Floorplan.FloorplanBayesCell) {
                    Floorplan.FloorplanBayesCell cellelement = (Floorplan.FloorplanBayesCell) selectedElement;
                    actions.add(Floorplan.ElementAction.shortHand(() -> "Cell Settings", () -> {
                        LocationCell cell = cellelement.getCell();
                        if (cell == null) {
                            cell = new LocationCell();
                            cell.setName("New cell");
                            int cellid = (int) AppDatabase.getInstance(getContext()).locationCellDAO().insert(cell);
                            cell.setId(cellid);
                            cell.setFloorplanId(model.getFloorplan().getId());
                            cellelement.setCell(cell);
                        }

                        MainActivity activity = (MainActivity) getContext();
                        activity.cellFragment.setCell(cell);
                        activity.setActiveFragment(activity.cellFragment, true);
                    }));
                }
            }
        }

        return actions;
    }

    //TODO give this vies its own life cycle instead of depending on parents
    public void floorplanChanged() {
        selectedElement = null;
        buttonsChanged();
    }

    void addRectangleObstacle() {
        Floorplan.RectangleObstacle el = new Floorplan.RectangleObstacle();
        el.setArea(new RectF(viewportCenter.x - 0.5f, viewportCenter.y - 0.5f, viewportCenter.x + 0.5f, viewportCenter.y + 0.5f));
        model.getFloorplan().addElement(el);
        selectedElement = el;
        buttonsChanged();
    }

    void addRectangleBlocking() {
        Floorplan.RectangleBlocking el = new Floorplan.RectangleBlocking();
        el.setArea(new RectF(viewportCenter.x - 0.5f, viewportCenter.y - 0.5f, viewportCenter.x + 0.5f, viewportCenter.y + 0.5f));
        model.getFloorplan().addElement(el);
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

    void changeSelection(Floorplan.FloorEditable element) {
        selectedElement = element;
        buttonsChanged();
    }

    @SuppressLint("ClickableViewAccessibility")
    void init() {
        initPaints();
        //TODO do some argument passing to get this value instead
        model = MainActivity.modelState;

        FlingHandler flinger = new FlingHandler();

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
                                model.getFloorplan().elementsChanged();
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

        Floorplan.FloorEditable match = model.getFloorplan().getElements().stream()
                .filter(el -> (el instanceof Floorplan.FloorEditable))
                .map(el -> (Floorplan.FloorEditable) el)
                .sorted((a, b) -> b.getMergeGroup() - a.getMergeGroup())
                .filter(el -> el.hitTest(point[0], point[1]))
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
        if (selectionMode == SelectionMode.PARTICLES && simulating && model.getParticleModel() != null) {
            model.moveParticles(-dx, -dy);
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

    Floorplan.RenderOpts renderOpts = new Floorplan.RenderOpts();

    void initPaints() {
        Floorplan.PaintPalette palette = new Floorplan.PaintPalette();

        palette.background = new Paint();
        palette.background.setARGB(255, 150, 150, 150);

        palette.floor = new Paint();
        palette.floor.setARGB(255, 255, 255, 255);

        palette.furniture = new Paint();
        palette.furniture.setARGB(255, 220, 220, 220);

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

        palette.particle = new Paint();
        palette.particle.setStyle(Paint.Style.STROKE);
        palette.particle.setStrokeWidth(0.02f);
        palette.particle.setARGB(255, 255, 0, 0);

        renderOpts.palette = palette;
        renderOpts.transform = floorTransform;
        renderOpts.drawboxes = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        renderOpts.selected = selectedElement;
        canvas.drawRect(0, 0, getWidth(), getHeight(), renderOpts.palette.background);

        //save old transforms to stack and transform to map space
        canvas.save();
        canvas.setMatrix(floorTransform);

        //can now do all rendering in floorplan coordinates (meters)
        model.getFloorplan().render(canvas, renderOpts);
        if (selectedElement != null) {
            canvas.drawPath(selectedElement.getContour(), renderOpts.palette.lines);
        }
        if (selectionMode == SelectionMode.PARTICLES && model.getParticleModel() != null) {
            model.getParticleModel().render(canvas, renderOpts);
        }

        //restore old transforms
        canvas.restore();
    }
}
