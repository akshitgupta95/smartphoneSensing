package com.tudelft.smartphonesensing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.util.Log;

import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static android.content.Context.SENSOR_SERVICE;

public class ParticleModel {
    public static class ConvexBox {
        final Coordinate[] points;
        final ConvexBox[] neighbours;
        final double volume;
        final Coordinate center;

        ConvexBox(Coordinate[] points) {
            if (points.length != 3) {
                //Currently only stuck on random point generation
                throw new RuntimeException("Only triangles are allowed");
            }

            double sumx = 0, sumy = 0;
            double vol = 0;
            for (int i = 0, j = points.length - 1; i < points.length; i++) {
                sumx += points[i].x;
                sumy += points[i].y;
                vol += points[i].x * points[j].y - points[i].y * points[j].x;
                j = i;
            }

            center = new Coordinate(sumx / points.length, sumy / points.length);
            //negative volume means clockwise contour, reverse this to guarantee ccw
            if (vol < 0) {
                for (int i = 0; i < points.length / 2; i++) {
                    int j = points.length - 1 - i;
                    Coordinate temp = points[j];
                    points[j] = points[i];
                    points[i] = temp;
                }
                vol = -vol;
            }
            this.points = points;
            this.neighbours = new ConvexBox[points.length];
            volume = vol / 2;
        }

        ConvexBox moveInside(double fromx, double fromy, double tox, double toy) {
            Coordinate prev = points[points.length - 1];
            int bestindex = -1;
            double furthestcrossing = 0;
            for (int i = 0; i < points.length; i++) {
                Coordinate current = points[i];
                double cross = crossProduct(prev.x, prev.y, current.x, current.y, tox, toy);
                //Log.i("CROSS", String.format("%s %s %s", prev, current, cross));
                if (cross > 0) {
                    //divide cross product by length of the edge to get the distance past the edge
                    double dist = cross / Math.sqrt(Math.pow(prev.x - current.x, 2) + Math.pow(prev.y - current.y, 2));
                    if (dist > furthestcrossing) {
                        furthestcrossing = dist;
                        //awkward indexing as this edge belongs to the previous index
                        bestindex = (i + points.length - 1) % points.length;
                    }
                }
                prev = current;
            }
            return bestindex == -1 ? this : neighbours[bestindex];
        }

        double[] randomPosition(Random rand) {
            //coordinate along first edge a and second edge b
            double a = rand.nextDouble();
            double b = rand.nextDouble();
            //if sum>1 the point lies outside the triangl, mirror the coordinate to bring it back inside
            //while remaining even distributed
            if (a + b > 1) {
                a = 1d - a;
                b = 1d - b;
            }
            double x = points[0].x + (points[1].x - points[0].x) * a + (points[2].x - points[0].x) * b;
            double y = points[0].y + (points[1].y - points[0].y) * a + (points[2].y - points[0].y) * b;
            return new double[]{x, y};
        }
    }

    static double crossProduct(double x0, double y0, double x1, double y1, double x2, double y2) {
        return (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
    }

    static class Particle {
        Coordinate pos;
        ConvexBox box;
        private double[] biasmatrix;
        private double biasAngle;
        private double biasScale;

        Particle(ConvexBox box, double x, double y) {
            this.box = box;
            this.pos = new Coordinate(x, y);
            this.setBias(1, 0);
        }

        void setBias(double angle, double scale) {
            biasmatrix = new double[]{
                    scale * Math.cos(angle), scale * Math.sin(angle),
                    -scale * Math.sin(angle), scale * Math.cos(angle)
            };
            biasAngle = angle;
            biasScale = scale;
        }

        double getBiasAngle() {
            return biasAngle;
        }

        double getBiasScale() {
            return biasScale;
        }

        boolean move(double dx, double dy) {
            ConvexBox prev = box;
            ConvexBox newbox = box;
            double mdx = dx * biasmatrix[0] + dy * biasmatrix[1];
            double mdy = dx * biasmatrix[2] + dy * biasmatrix[3];
            do {
                prev = newbox;
                newbox = prev.moveInside(pos.x, pos.y, pos.x + mdx, pos.y + mdy);
                if (newbox == null) {
                    return false;
                }
            } while (prev != newbox);
            pos.x += mdx;
            pos.y += mdy;
            box = newbox;
            return true;
        }
    }

    private final double biasAngleRange = Math.PI / 4;
    private final double biasScaleRange = 0.5;
    private final Random rand = new Random();
    private double totalarea;
    private List<ConvexBox> boxes;
    private List<Particle> particles = new ArrayList<>();

    void setBoxes(List<ConvexBox> boxes) {
        this.boxes = boxes;
        totalarea = boxes.stream().reduce(0d, (p, b) -> p + b.volume, Double::sum);

        int n = particles.size();
        particles.clear();
        //spawnParticles(n);
    }

    Particle createParticle(ConvexBox box, double x, double y, double biasangle, double biasscale) {
        Particle p = new Particle(box, x, y);
        double angle = biasAngleRange * (0.5 - rand.nextDouble());
        double scale = 1 + biasScaleRange * (0.5 - rand.nextDouble());
        p.setBias(biasangle / 2 + angle / 2, biasscale / 2 + scale / 2);
        return p;
    }

    public void spawnParticles(int n) {
        for (int i = 0; i < n; i++) {
            particles.add(randomParticle());
        }
    }

    private Particle randomParticle() {
        double p = rand.nextDouble() * totalarea;
        ConvexBox targetbox = boxes.get(0);
        for (ConvexBox box : boxes) {
            p -= box.volume;
            if (p <= 0) {
                targetbox = box;
                break;
            }
        }
        double[] pos = targetbox.randomPosition(rand);
        return createParticle(targetbox, pos[0], pos[1], 0, 1);
    }

    public void move(double dx, double dy) {
        double randscale = 0.5;
        List<Integer> validindexes = new ArrayList<>(particles.size());
        List<Integer> invalidindexes = new ArrayList<>();

        for (int i = 0; i < particles.size(); i++) {
            double xrand = 1 + randscale * (-0.5 + rand.nextDouble());
            double yrand = 1 + randscale * (-0.5 + rand.nextDouble());

            if (!particles.get(i).move(dx * xrand, dy * yrand)) {
                invalidindexes.add(i);
            } else {
                validindexes.add(i);
            }
        }

        for (int i = 0; i < invalidindexes.size(); i++) {
            if (validindexes.size() != 0) {
                int cloneindex = rand.nextInt(validindexes.size());
                Particle parent = particles.get(validindexes.get(cloneindex));
                particles.set(invalidindexes.get(i), createParticle(parent.box, parent.pos.x, parent.pos.y, parent.getBiasAngle(), parent.getBiasScale()));
            } else {
                particles.set(invalidindexes.get(i), randomParticle());
            }
        }

        //particles = particles.stream().filter(p -> p.move(dx, dy)).collect(Collectors.toList());
    }

    void render(Canvas canvas) {
        if (boxes == null) {
            return;
        }
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.02f);
        paint.setARGB(255, 255, 0, 0);
        /*
        for (ParticleModel.ConvexBox box : boxes) {
            Path p = new Path();
            p.moveTo((float) box.points[0].x, (float) box.points[0].y);
            for (int i = 1; i < box.points.length; i++) {
                p.lineTo((float) box.points[i].x, (float) box.points[i].y);
            }
            p.close();
            canvas.drawPath(p, paint);
        }
        */

        //draw only a portion of the particles if there are many
        final int MAXVISIBLEPARTICLES = 30000;
        int step = (particles.size() + MAXVISIBLEPARTICLES) / MAXVISIBLEPARTICLES;
        for (int i = 0; i < particles.size(); i += step) {
            Particle particle = particles.get(i);
            canvas.drawPoint((float) particle.pos.x, (float) particle.pos.y, paint);
        }
    }
}




