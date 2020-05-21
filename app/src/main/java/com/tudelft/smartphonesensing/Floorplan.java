package com.tudelft.smartphonesensing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Floorplan {
    List<FloorElement> elements = new ArrayList<>();

    static final String ELEMENT_POLYGON = "poly";
    static final String ELEMENT_RECTANGLE = "rectangle";
    static final String ELEMENT_FLOORPLAN = "floorplan";

    Floorplan() {

    }

    public void setElements(List<FloorElement> elements) {
        this.elements = elements;
    }

    public List<FloorElement> getElements() {
        return elements;
    }

    public JSONObject serialize() throws JSONException {
        JSONObject obj = new JSONObject();
        JSONArray els = new JSONArray();
        for (FloorElement el : elements) {
            els.put(el.serialize());
        }
        obj.put("children", els);
        obj.put("type", ELEMENT_FLOORPLAN);
        return obj;
    }

    public void deserialize(JSONObject obj) throws JSONException {
        List<FloorElement> els = new ArrayList<>();
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
                default:
                    throw new JSONException(String.format("Unknown obstacle type: %s", child.getString("type")));
            }
            newel.deserialize(child);
            els.add(newel);
        }
        this.elements = els;
    }

    void render(Canvas canvas) {
        for (Floorplan.FloorElement el : elements) {
            el.render(canvas);
        }
    }

    public interface FloorElement {
        /**
         * Checks if the obstacle collides with a line between p1 and p2
         *
         * @return true if there is a collision
         */
        boolean checkCollision(PointF p1, PointF p2);

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
        void deserialize(JSONObject props) throws JSONException;

        /**
         * Used to render a graphical representation of the obstacle
         * The canvas is preconfigured with the correct transforms to convert meters in floor space to pixels on screen
         * ex: c.drawRect(0,0,1,1,paint); to draw a 1x1 meter rectangle at the origin of floor space
         */
        void render(Canvas c);
    }

    public static class PolygonObstacle implements FloorElement {
        PointF[] vertices;

        PointF[] getVertices() {
            return vertices;
        }

        void setVertices(PointF[] vertices) {
            this.vertices = vertices;
        }

        @Override
        public boolean checkCollision(PointF p1, PointF p2) {
            if (vertices.length < 2) {
                return false;
            }
            PointF prevVertex = vertices[vertices.length - 1];
            for (PointF vertex : vertices) {
                if (Util.intersectLineFragments(p1.x, p1.y, p2.x, p2.y, vertex.x, vertex.y, prevVertex.x, prevVertex.y)) {
                    return true;
                }
                prevVertex = vertex;
            }
            return false;
        }

        @Override
        public JSONObject serialize() throws JSONException {
            JSONObject obj = new JSONObject();
            JSONArray verts = new JSONArray();
            for (PointF vertex : vertices) {
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
        public void deserialize(JSONObject props) throws JSONException {
            List<PointF> vertices = new ArrayList<>();
            JSONArray verts = props.getJSONArray("vertices");
            for (int i = 0; i < verts.length(); i++) {
                JSONObject jsonVertex = verts.getJSONObject(i);
                vertices.add(new PointF((float) jsonVertex.getDouble("x"), (float) jsonVertex.getDouble("y")));
            }
            this.vertices = (PointF[]) vertices.toArray();
        }

        @Override
        public void render(Canvas c) {
            Path p = new Path();
            p.moveTo(vertices[0].x, vertices[0].y);
            for (int i = 1; i < vertices.length; i++) {
                p.lineTo(vertices[i].x, vertices[i].y);
            }
            p.close();

            Paint color = new Paint();
            color.setARGB(255, 100, 100, 100);
            c.drawPath(p, color);
        }
    }

    public static class RectangleObstacle implements FloorElement {
        RectF area;

        public RectF getArea() {
            return area;
        }

        public void setArea(RectF area) {
            this.area = area;
        }

        @Override
        public boolean checkCollision(PointF p1, PointF p2) {
            return Util.intersectLineFragments(p1.x, p1.y, p2.x, p2.y, area.left, area.top, area.right, area.top)
                    || Util.intersectLineFragments(p1.x, p1.y, p1.x, p2.y, area.right, area.top, area.right, area.bottom)
                    || Util.intersectLineFragments(p1.x, p1.y, p2.x, p2.y, area.right, area.bottom, area.left, area.bottom)
                    || Util.intersectLineFragments(p1.x, p1.y, p2.x, p2.y, area.left, area.bottom, area.left, area.top);
        }

        @Override
        public JSONObject serialize() throws JSONException {
            JSONObject props = new JSONObject();
            props.put("left", area.left);
            props.put("top", area.top);
            props.put("right", area.right);
            props.put("bottom", area.bottom);
            props.put("type", ELEMENT_RECTANGLE);
            return props;
        }

        @Override
        public void deserialize(JSONObject props) throws JSONException {
            area.left = (float) props.getDouble("left");
            area.top = (float) props.getDouble("top");
            area.right = (float) props.getDouble("right");
            area.bottom = (float) props.getDouble("bottom");
        }

        @Override
        public void render(Canvas c) {
            Paint fill = new Paint();
            fill.setARGB(255, 0, 0, 0);
            c.drawRect(area, fill);
        }
    }


}
