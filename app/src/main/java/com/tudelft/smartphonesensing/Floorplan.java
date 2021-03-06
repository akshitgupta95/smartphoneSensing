package com.tudelft.smartphonesensing;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.locationtech.jts.awt.PointShapeFactory;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.triangulate.ConformingDelaunayTriangulationBuilder;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Observable;
import java.util.Stack;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import mxb.jts.triangulate.EarClipper;

public class Floorplan {
    private ArrayList<FloorElement> elements = new ArrayList<>();
    private double northAngleOffset = 0;
    private int id;
    private String name;

    static final String ELEMENT_POLYGON = "poly";
    static final String ELEMENT_RECTANGLE = "rectangle";
    static final String ELEMENT_FLOORPLAN = "floorplan";
    static final String ELEMENT_RECTANGLE_BLOCKING = "rectangle_blocking";

    static final int MERGEGROUP_FLOOR = 0;
    static final int MERGEGROUP_FURNITURE = 1;

    static Floorplan load(AppDatabase db, FloorplanDataDAO.FloorplanData data) throws JSONException {
        Floorplan floor = new Floorplan(data.getId(), data.getName());
        List<LocationCell> cells = db.locationCellDAO().getAllInFloorplan(data.getId());
        JSONObject obj = new JSONObject(data.getLayoutJson());
        floor.deserialize(obj, cells);
        return floor;
    }

    private Floorplan(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public double getNorthAngleOffset() {
        return northAngleOffset;
    }

    public void setNorthAngleOffset(double angle) {
        northAngleOffset = angle;
    }

    public List<ParticleModel.ConvexBox> getWalkable() {
        Geometry combined = new MultiPolygon(new Polygon[0], new GeometryFactory());
        List<FloorObstacle> ordered = elements.stream()
                .filter(a -> a instanceof FloorObstacle)
                .sorted((a, b) -> a.getMergeGroup() - b.getMergeGroup())
                .map(a -> (FloorObstacle) a)
                .collect(Collectors.toList());
        for (FloorObstacle el : ordered) {
            if (el.isWalkable()) {
                combined = combined.union(el.getGeometry());
            } else {
                combined = combined.difference(el.getGeometry());
            }
        }

        class Border {
            private ParticleModel.ConvexBox box = null;
            private int index = -1;

            private Border(ParticleModel.ConvexBox box, int index) {
                this.box = box;
                this.index = index;
            }

            private Border() {
            }
        }

        List<ParticleModel.ConvexBox> triangles = new ArrayList<>();

        final boolean useEarcutter = true;
        if (useEarcutter) {
            //Using the earcurrent method, i can't find any maven package that does this, only one
            //github repo that i copied over to mxb.jts.triangulate

            List<Polygon> polys = new ArrayList<>();
            if (combined instanceof Polygon) {
                polys.add((Polygon) combined);
            }
            if (combined instanceof MultiPolygon) {
                MultiPolygon multipoly = (MultiPolygon) combined;
                for (int i = 0; i < multipoly.getNumGeometries(); i++) {
                    polys.add((Polygon) multipoly.getGeometryN(i));
                }
            }


            List<Polygon> subPolygons = new ArrayList<>();
            for (Polygon poly : polys) {
                EarClipper clipper = new EarClipper(poly);
                GeometryCollection res = (GeometryCollection) clipper.getResult();
                for (int i = 0; i < res.getNumGeometries(); i++) {
                    subPolygons.add((Polygon) res.getGeometryN(i));
                }
            }

            HashMap<Coordinate, List<ParticleModel.ConvexBox>> vertices = new HashMap<>();
            for (Polygon poly : subPolygons) {
                Coordinate[] coords = poly.getCoordinates();
                coords = Arrays.copyOf(coords, coords.length - 1);
                ParticleModel.ConvexBox box = new ParticleModel.ConvexBox(coords);
                for (Coordinate coord : coords) {
                    List<ParticleModel.ConvexBox> boxes = vertices.getOrDefault(coord, null);
                    if (boxes == null) {
                        boxes = new ArrayList<>();
                        vertices.put(coord, boxes);
                    }
                    boxes.add(box);
                }
                triangles.add(box);
            }
            for (ParticleModel.ConvexBox box : triangles) {
                List<ParticleModel.ConvexBox> current = vertices.get(box.points[0]);
                for (int i = 0; i < box.points.length; i++) {
                    int nextindex = (i + 1) % box.points.length;
                    List<ParticleModel.ConvexBox> next = vertices.get(box.points[nextindex]);
                    for (ParticleModel.ConvexBox otherbox : current) {
                        if (otherbox == box) {
                            continue;
                        }
                        if (next.contains(otherbox)) {
                            box.neighbours[i] = otherbox;
                            int otherindex = Arrays.asList(otherbox.points).indexOf(box.points[nextindex]);
                            otherbox.neighbours[otherindex] = box;
                        }
                    }
                    current = next;
                }
            }
        } else {
            //Alternative method using "Conforming Delaunay Triangulation" This method apparently
            //has some good mathematical properties but creates way more polygons than necessary
            ConformingDelaunayTriangulationBuilder b = new ConformingDelaunayTriangulationBuilder();
            b.setSites(combined);
            b.setConstraints(combined);
            QuadEdgeSubdivision div = b.getSubdivision();

            HashMap<QuadEdge, Border> visited = new HashMap<>();
            Stack<QuadEdge> unchecked = new Stack<>();
            unchecked.add((QuadEdge) div.getEdges().iterator().next());
            while (!unchecked.empty()) {
                QuadEdge edge = unchecked.pop();
                if (visited.containsKey(edge)) {
                    continue;
                }

                QuadEdge start = edge;
                List<QuadEdge> edges = new ArrayList<>();
                List<Coordinate> points = new ArrayList<>();
                do {
                    edges.add(edge);
                    points.add(edge.orig().getCoordinate());
                    edge = edge.dNext().sym();
                }
                while (edge != start);


                ParticleModel.ConvexBox triangle = new ParticleModel.ConvexBox(points.toArray(new Coordinate[0]));
                Point center = new Point(new CoordinateArraySequence(new Coordinate[]{triangle.center}), new GeometryFactory());
                boolean valid = triangle.volume > 0 && combined.contains(center);

                for (int i = 0; i < edges.size(); i++) {
                    QuadEdge subedge = edges.get(i);
                    visited.put(subedge, valid ? new Border(triangle, i) : new Border());
                    Border other = visited.getOrDefault(subedge.sym(), null);
                    if (other == null) {
                        unchecked.add(subedge.sym());
                    } else if (valid && other.box != null) {
                        other.box.neighbours[other.index] = triangle;
                        triangle.neighbours[i] = other.box;
                    }
                }

                if (valid) {
                    triangles.add(triangle);
                }
            }
        }
        return triangles;
    }

    public void setElements(ArrayList<FloorElement> elements) {
        this.elements = elements;
        elementsChanged();
    }

    public void elementsChanged() {
        //TODO implement observer pattern?
    }

    public void removeElement(FloorElement el) {
        elements.remove(el);
        elementsChanged();
    }

    public List<FloorElement> getElements() {
        return elements;
    }

    public void addElement(FloorElement el) {
        elements.add(el);
        elementsChanged();
    }

    public JSONObject serialize() throws JSONException {
        JSONObject obj = new JSONObject();
        JSONArray els = new JSONArray();
        for (FloorElement el : elements) {
            els.put(el.serialize());
        }
        obj.put("northangle", northAngleOffset);
        obj.put("children", els);
        obj.put("type", ELEMENT_FLOORPLAN);
        return obj;
    }

    public void deserialize(JSONObject obj, List<LocationCell> cells) throws JSONException {
        ArrayList<FloorElement> els = new ArrayList<>();
        JSONArray children = obj.getJSONArray("children");
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.getJSONObject(i);
            FloorElement newel;
            switch (child.getString("type")) {
                //TODO allow recursion?
                //case ELEMENT_FLOORPLAN:
                //    newel=new Floorplan()
                case ELEMENT_POLYGON:
                    newel = new PolygonObstacle();
                    break;
                case ELEMENT_RECTANGLE:
                    newel = new RectangleObstacle();
                    break;
                case ELEMENT_RECTANGLE_BLOCKING:
                    newel = new RectangleBlocking();
                    break;
                default:
                    throw new JSONException(String.format("Unknown obstacle type: %s", child.getString("type")));
            }
            newel.deserialize(child, cells);
            els.add(newel);
        }
        try {
            northAngleOffset = obj.getDouble("northangle");
        } catch (JSONException e) {
            northAngleOffset = 0;
        }
        setElements(els);
    }

    public static class RenderOpts {
        public FloorElement selected;
        public Matrix transform;
        public PaintPalette palette;
        public boolean drawboxes;
    }

    void render(Canvas canvas, RenderOpts opts) {
        for (Floorplan.FloorElement el : elements) {
            el.renderBackground(canvas, opts);
        }
        for (Floorplan.FloorElement el : elements) {
            el.renderForeground(canvas, opts);
        }
    }

    public interface FloorplanBayesCell {
        LocationCell getCell();

        void setCell(LocationCell loc);
    }

    public interface FloorEditable extends FloorElement {
        /**
         * @return true if the element occupies floor space at x,y
         */
        boolean hitTest(float x, float y);

        /**
         * resize the element by multiplying it's size by scalex, scaley
         *
         * @return true if the component was changed, false otherwise
         */
        boolean editScale(float scalex, float scaley);

        /**
         * move the element by dx, dy in meters
         *
         * @return true if the component was changed, false otherwise
         */
        boolean editMove(float dx, float dy);

        /**
         * @return a path that defines a highlight contour around the element
         */
        Path getContour();

        /**
         * @return Returns a list of actions that this element has
         */
        List<ElementAction> getActions();
    }

    public interface ElementAction {
        String getName();

        void click();

        static ElementAction shortHand(Supplier<String> getName, Runnable onClick) {
            return new ElementAction() {
                @Override
                public String getName() {
                    return getName.get();
                }

                @Override
                public void click() {
                    onClick.run();
                }
            };
        }
    }

    public interface FloorObstacle {
        Polygon getGeometry();

        boolean isWalkable();
    }

    public interface FloorElement {

        /**
         * Used to store the obstacle
         *
         * @return A JSON compatible map of properties needed to rebuild this obstacle
         */
        JSONObject serialize() throws JSONException;

        /**
         * Load properties from the JSON map returned by serialize()
         * The object should be able to survive a round trip through serialization
         *
         * @param props the serialized properties returned by serialize
         */
        void deserialize(JSONObject props, List<LocationCell> cells) throws JSONException;

        /**
         * Used to render a graphical representation of the obstacle
         * The canvas is preconfigured with the correct transforms to convert meters in floor space to pixels on screen
         * ex: c.drawRect(0,0,1,1,paint); to draw a 1x1 meter rectangle at the origin of floor space
         */
        void renderBackground(Canvas c, RenderOpts opts);

        void renderForeground(Canvas c, RenderOpts opts);

        Coordinate getCenterPoint();

        int getMergeGroup();
    }

    public static class PolygonObstacle implements FloorElement, FloorObstacle {
        Coordinate[] vertices;

        Coordinate[] getVertices() {
            return vertices;
        }

        void setVertices(Coordinate[] vertices) {
            this.vertices = vertices;
        }

        @Override
        public boolean isWalkable() {
            return true;
        }

        @Override
        public int getMergeGroup() {
            return MERGEGROUP_FLOOR;
        }

        public Polygon getGeometry() {
            GeometryFactory fact = new GeometryFactory();
            Coordinate[] closed = Arrays.copyOf(vertices, vertices.length + 1);
            closed[closed.length - 1] = vertices[0];
            LinearRing ring = fact.createLinearRing(closed);
            return new Polygon(ring, null, fact);
        }

        @Override
        public Coordinate getCenterPoint() {
            double sumx = 0;
            double sumy = 0;
            for (Coordinate c : vertices) {
                sumx += c.x;
                sumy += c.y;
            }
            return new Coordinate(sumx / vertices.length, sumy / vertices.length);
        }

        @Override
        public JSONObject serialize() throws JSONException {
            JSONObject obj = new JSONObject();
            JSONArray verts = new JSONArray();
            for (Coordinate vertex : vertices) {
                JSONObject jsonvertex = new JSONObject();
                jsonvertex.put("x", vertex.x);
                jsonvertex.put("y", vertex.y);
                verts.put(jsonvertex);
            }
            obj.put("vertices", verts);
            obj.put("type", ELEMENT_POLYGON);
            return obj;
        }

        @Override
        public void deserialize(JSONObject props, List<LocationCell> cells) throws JSONException {
            List<Coordinate> vertices = new ArrayList<>();
            JSONArray verts = props.getJSONArray("vertices");
            for (int i = 0; i < verts.length(); i++) {
                JSONObject jsonVertex = verts.getJSONObject(i);
                vertices.add(new Coordinate(jsonVertex.getDouble("x"), jsonVertex.getDouble("y")));
            }
            this.vertices = vertices.toArray(new Coordinate[0]);
        }

        @Override
        public void renderBackground(Canvas c, RenderOpts opts) {
            Path p = new Path();
            p.moveTo((float) vertices[0].getX(), (float) vertices[0].getY());
            for (int i = 1; i < vertices.length; i++) {
                p.lineTo((float) vertices[i].getX(), (float) vertices[i].getY());
            }
            p.close();

            c.drawPath(p, opts.palette.floor);
        }

        @Override
        public void renderForeground(Canvas c, RenderOpts opts) {
        }
    }

    public static class RectangleBlocking extends RectangleElement {
        @Override
        public JSONObject serialize() throws JSONException {
            JSONObject ret = super.serialize();
            ret.put("type", ELEMENT_RECTANGLE_BLOCKING);
            return ret;
        }

        @Override
        public void renderBackground(Canvas c, RenderOpts opts) {
            c.drawRect(area, opts.palette.furniture);
        }

        @Override
        public boolean isWalkable() {
            return false;
        }

        @Override
        public int getMergeGroup() {
            return MERGEGROUP_FURNITURE;
        }
    }

    public static class RectangleObstacle extends RectangleElement implements FloorplanBayesCell {
        LocationCell cell = null;

        @Override
        public JSONObject serialize() throws JSONException {
            JSONObject props = super.serialize();
            props.put("type", ELEMENT_RECTANGLE);
            props.put("cellid", (cell == null ? -1 : cell.getId()));
            return props;
        }

        @Override
        public void deserialize(JSONObject props, List<LocationCell> cells) throws JSONException {
            super.deserialize(props, cells);
            int celid = props.getInt("cellid");
            cell = cells.stream().filter(c -> c.getId() == celid).findFirst().orElse(null);
        }

        @Override
        public LocationCell getCell() {
            return cell;
        }

        @Override
        public void setCell(LocationCell loc) {
            cell = loc;
        }

        @Override
        public void renderBackground(Canvas c, RenderOpts opts) {
            c.drawRect(area, opts.palette.floor);
        }

        @Override
        public void renderForeground(Canvas c, RenderOpts opts) {
            if (cell != null) {
                String text = cell.getName();
                renderForegroundText(c, opts, text);
            } else {
                super.renderForeground(c, opts);
            }
        }

        @Override
        public boolean isWalkable() {
            return true;
        }

        @Override
        public int getMergeGroup() {
            return MERGEGROUP_FLOOR;
        }
    }

    public static abstract class RectangleElement implements FloorElement, FloorObstacle, FloorEditable {
        RectF area = new RectF();

        public RectF getArea() {
            return area;
        }

        public void setArea(RectF area) {
            this.area = area;
        }

        @Override
        public Coordinate getCenterPoint() {
            return new Coordinate(area.centerX(), area.centerY());
        }

        @Override
        public List<ElementAction> getActions() {
            return new ArrayList<>();
        }

        @Override
        public Polygon getGeometry() {
            return new GeometryFactory().createPolygon(new Coordinate[]{
                    new Coordinate(area.left, area.top),
                    new Coordinate(area.right, area.top),
                    new Coordinate(area.right, area.bottom),
                    new Coordinate(area.left, area.bottom),
                    new Coordinate(area.left, area.top)
            });
        }

        @Override
        public JSONObject serialize() throws JSONException {
            JSONObject props = new JSONObject();
            props.put("left", area.left);
            props.put("top", area.top);
            props.put("right", area.right);
            props.put("bottom", area.bottom);
            return props;
        }

        @Override
        public void deserialize(JSONObject props, List<LocationCell> cells) throws JSONException {
            area.left = (float) props.getDouble("left");
            area.top = (float) props.getDouble("top");
            area.right = (float) props.getDouble("right");
            area.bottom = (float) props.getDouble("bottom");
        }

        @Override
        public void renderBackground(Canvas c, RenderOpts opts) {
            c.drawRect(area, opts.palette.floor);
        }

        void renderForegroundText(Canvas c, RenderOpts opts, String text) {
            float[] center = new float[]{area.centerX(), area.centerY()};
            opts.transform.mapPoints(center);
            c.save();
            c.setMatrix(new Matrix());
            c.drawText(text, center[0], center[1], opts.palette.text);
            c.restore();
        }

        @Override
        public void renderForeground(Canvas c, RenderOpts opts) {
            if (opts.selected == this) {
                String text = String.format(Locale.US, "%.1fx%.1f m", area.width(), area.height());
                renderForegroundText(c, opts, text);
            }
        }

        @Override
        public boolean hitTest(float x, float y) {
            return x >= area.left && x <= area.right && y >= area.top && y <= area.bottom;
        }

        @Override
        public boolean editScale(float dx, float dy) {
            area.left -= dx / 2f;
            area.right += dx / 2f;
            area.top -= dy / 2f;
            area.bottom += dy / 2f;
            return true;
        }

        @Override
        public boolean editMove(float dx, float dy) {
            area.left += dx;
            area.right += dx;
            area.top += dy;
            area.bottom += dy;
            return true;
        }

        @Override
        public Path getContour() {
            Path p = new Path();
            p.moveTo(area.left, area.top);
            p.lineTo(area.right, area.top);
            p.lineTo(area.right, area.bottom);
            p.lineTo(area.left, area.bottom);
            p.close();
            //p.addRect(area, Path.Direction.CW);
            return p;
        }

    }

    public static class PaintPalette {
        public Paint background;
        public Paint floor;
        public Paint furniture;
        public Paint lines;
        public Paint text;
        public Paint particle;
    }


}
