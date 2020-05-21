package com.tudelft.smartphonesensing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Floorplan {
    List<FloorElement> elements = new ArrayList<>();

    Floorplan() {
        Floorplan.RectangleObstacle el = new Floorplan.RectangleObstacle();
        el.setArea(new RectF(0, 0, 1, 1));
        elements.add(el);
    }

    public void setElements(List<FloorElement> elements) {
        this.elements = elements;
    }

    public List<FloorElement> getElements() {
        return elements;
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
            return false;
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
