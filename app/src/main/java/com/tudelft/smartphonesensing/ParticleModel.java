package com.tudelft.smartphonesensing;

import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.List;

public class ParticleModel {
    public static class ConvexBox {
        final Coordinate[] points;
        final ConvexBox[] neighbours;
        double volume = 0;
        Coordinate center;

        ConvexBox(Coordinate[] points) {
            this.points = points;
            this.neighbours = new ConvexBox[points.length];

            double sumx = 0, sumy = 0;
            double vol = 0;
            int j = points.length - 1;
            for (int i = 0; i < points.length; i++) {
                sumx += points[i].x;
                sumy += points[i].y;
                vol += points[i].y * points[j].x - points[i].x * points[j].y;
                j = i;
            }

            center = new Coordinate(sumx / points.length, sumy / points.length);
            volume = vol / -2;
        }
    }
}
