package com.tudelft.smartphonesensing;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ModelState {
    private ParticleModel particleModel = new ParticleModel();
    private Floorplan floorplan = null;
    private boolean running = false;
    private MotionTracker motionTracker;
    private Context context = null;
    private LocationCell particleConvergence = null;

    private boolean hasConvergence = false;

    public final Util.EventSource<Floorplan> floorplanChange = new Util.EventSource<>();
    public final Util.EventSource<Floorplan> particlemodelChange = new Util.EventSource<>();
    public final Util.EventSource<Void> predictionUpdate = new Util.EventSource<>();

    void setContext(Context context) {
        if (this.context != null) {
            throw new RuntimeException("context already set");
        }
        this.context = context;


        motionTracker = new MotionTracker(context, (dx, dy) -> {
            if (particleModel != null && running) {
                moveParticles(dx, dy);
            }
        });
    }

    public LocationCell getParticleConvergence() {
        return particleConvergence;
    }

    public void selectFloorMenu() {
        AppDatabase db = AppDatabase.getInstance(context);
        List<FloorplanDataDAO.FloorplanMeta> meta = db.floorplanDataDAO().getAllNames();

        List<String> names = meta.stream().map(e -> e.name == null ? "no name" : e.name).collect(Collectors.toList());
        names.add("New");
        Util.showDropdownSpinner(context, "Open floorplan", names.toArray(new String[0]), index -> {
            if (index < meta.size()) {
                FloorplanDataDAO.FloorplanMeta choice = meta.get(index);
                loadFloor(choice.id);
            } else {
                Util.showTextDialog(context, "Name for floor", "", this::loadNewDefaultFloor);
            }
        });
    }

    public void loadNewDefaultFloor(String name) {
        AppDatabase db = AppDatabase.getInstance(context);
        FloorplanDataDAO.FloorplanData floordata = new FloorplanDataDAO.FloorplanData();
        floordata.setName(name);
        floordata.setLayoutJson(context.getString(R.string.default_floorplan_json));
        int id = (int) db.floorplanDataDAO().insert(floordata);
        loadFloor(id);
    }

    public void loadFloor(int id) {
        AppDatabase db = AppDatabase.getInstance(context);
        try {
            floorplan = Floorplan.load(db, db.floorplanDataDAO().getById(id));
            floorplanChange.trigger(floorplan);
        } catch (JSONException e) {
            Toast.makeText(context, "Failed to load floorplan", Toast.LENGTH_SHORT).show();
        }
    }

    public void setRunning(boolean run) {
        running = run;

        if (running) {
            particleModel.setBoxes(floorplan.getWalkable());
            particleModel.setNorthAngleOffset(floorplan.getNorthAngleOffset());
            particleModel.spawnParticles(10000);
        }
    }

    public void moveParticles(double dx, double dy) {
        final double convergenceStd = 1.0;
        particleModel.move(dx, dy);
        ParticleModel.DistributionInfo2d distr = particleModel.getParticleDistribution();
        if (distr.std > convergenceStd * 2) {
            hasConvergence = false;
        }
        if (hasConvergence || distr.std < convergenceStd) {
            hasConvergence = true;
            Coordinate center = new Coordinate(distr.meanx, distr.meany);
            LocationCell best = null;
            double bestdist = Double.POSITIVE_INFINITY;
            for (Floorplan.FloorElement el : floorplan.getElements()) {
                if (el instanceof Floorplan.FloorplanBayesCell) {
                    Floorplan.FloorplanBayesCell bayescell = (Floorplan.FloorplanBayesCell) el;
                    if (bayescell.getCell() != null && !bayescell.getCell().getName().isEmpty()) {
                        if (el instanceof Floorplan.FloorEditable) {
                            //TODO using a ui api here for physical hit testing, detach these at some point
                            if (((Floorplan.FloorEditable) el).hitTest((float) distr.meanx, (float) distr.meany)) {
                                best = bayescell.getCell();
                                break;
                            }
                        }
                        double dist = el.getCenterPoint().distance(center);
                        if (dist < bestdist) {
                            bestdist = dist;
                            best = bayescell.getCell();
                        }
                    }
                }
            }
            particleConvergence = best;
        } else {
            particleConvergence = null;
        }
        predictionUpdate.trigger(null);
    }

    public void saveFloorplan() {
        AppDatabase db = AppDatabase.getInstance(context);
        try {
            FloorplanDataDAO.FloorplanData floordata = FloorplanDataDAO.FloorplanData.fromFloorplan(floorplan);
            db.floorplanDataDAO().update(floordata);
        } catch (JSONException e) {
            Toast.makeText(context, "Failed to save floorplan", Toast.LENGTH_SHORT).show();
        }
    }

    public Floorplan getFloorplan() {
        return floorplan;
    }

    public void AlignFloorAxis() {
        double angle = motionTracker.get2dNorthAngle();
        floorplan.setNorthAngleOffset(angle);
        Toast.makeText(context, "Aligned map to phone axis", Toast.LENGTH_LONG).show();
    }

    public ParticleModel getParticleModel() {
        return particleModel;
    }
}
