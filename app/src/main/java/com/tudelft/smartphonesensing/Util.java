package com.tudelft.smartphonesensing;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Util {

    static long macStringToLong(String mac) {
        return Long.parseLong(mac.replace(":", ""), 16);
    }

    static String macLongToString(long mac) {
        return String.format("%02x:%02x:%02x:%02x:%02x:%02x", (mac >> 40) & 0xff, (mac >> 32) & 0xff, (mac >> 24) & 0xff, (mac >> 16) & 0xff, (mac >> 8) & 0xff, (mac >> 0) & 0xff);
    }


    /**
     * Checks if two line fragments intersect
     *
     * @param fromx1 x start of first line fragment
     * @param fromy1 y start of first line fragment
     * @param tox1   end point of first line fragment
     * @param toy1   end point of first line fragment
     * @param fromx2 first point of second line fragment
     * @param fromy2 first point of second line fragment
     * @param tox2   end point of second line fragment
     * @param toy2   end point of second line fragment
     * @return true if the line fragments intersect, false otherwise
     */
    static boolean intersectLineFragments(double fromx1, double fromy1, double tox1, double toy1, double fromx2, double fromy2, double tox2, double toy2) {
        double[] ab = intersectLineFragmentsPosition(fromx1, fromy1, tox1, toy1, fromx2, fromy2, tox2, toy2);
        return ab != null && ab[0] >= 0 && ab[0] <= 1 && ab[1] >= 0 && ab[1] <= 1;
    }

    /**
     * Checks if two line fragments intersect
     *
     * @param fromx1 x start of first line fragment
     * @param fromy1 y start of first line fragment
     * @param tox1   end point of first line fragment
     * @param toy1   end point of first line fragment
     * @param fromx2 first point of second line fragment
     * @param fromy2 first point of second line fragment
     * @param tox2   end point of second line fragment
     * @param toy2   end point of second line fragment
     * @return [a, b] where a is the location of the crossing in the first line fragment and b is the
     * location on the second line fragment, if both are between 0 and 1 it means the vectors cross
     * between their end points. Returns null if the lines are parallel.
     */
    static double[] intersectLineFragmentsPosition(double fromx1, double fromy1, double tox1, double toy1, double fromx2, double fromy2, double tox2, double toy2) {
        //let v1 and v2 be the start points of the line fragments
        //let d1 and d2 be the directions of the line fragments (end point-start point)
        //v1+a*d1=v2+b*d2
        //=> v2-v1=[d1 d2]*[a;-b]
        // let [d1 d2] be D and let v2-v1 be diffv
        //=> diffv=D*[a;-b]
        //=> [a;-b]=D^-1*diffv
        //non-invertible [d1 d2] means the lines don't intersect

        //v2-v1
        double diffvx = fromx2 - fromx1;
        double diffvy = fromy2 - fromy1;

        //d1, d2
        double d1x = tox1 - fromx1;
        double d1y = toy1 - fromy1;
        double d2x = tox2 - fromx2;
        double d2y = toy2 - fromy2;

        //det([d1 d2])
        double det = d1x * d2y - d2x * d1y;
        if (det == 0) {
            return null;
        }

        //D=[d1x d2x; d1y d2y] => D^-1=1/det*[d2y -d2x; -d1y d1x]

        double a = 1 / det * d2y * diffvx + 1 / det * (-d1y) * diffvy;
        double negb = 1 / det * (-d2x) * diffvx + 1 / det * d1x * diffvy;
        return new double[]{a, -negb};
    }

    public static void showTextDialog(Context context, String title, String initialValue, Consumer<String> onclose) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(initialValue);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", (dialog, which) -> {
            onclose.accept(input.getText().toString());
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
        });
        builder.show();
    }

    public static void showDropdownSpinner(Context context, String title, String[] options, Consumer<Integer> onclose) {
        AlertDialog.Builder b = new AlertDialog.Builder(context);

        b.setTitle(title);
        b.setItems(options, (DialogInterface dialog, int which) -> {
            dialog.dismiss();
            onclose.accept(which);
        });
        b.show();
    }

    public static class EventSource<T> {
        List<Consumer<T>> listeners = new ArrayList<>();

        void listen(Consumer<T> listener) {
            listeners.add(listener);
        }

        void remove(Consumer<T> listener) {
            listeners.remove(listener);
        }

        void trigger(T value) {
            listeners.forEach(l -> l.accept(value));
        }
    }
}






