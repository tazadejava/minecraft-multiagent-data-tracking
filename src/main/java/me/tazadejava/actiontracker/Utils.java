package me.tazadejava.actiontracker;

//miscellaneous utility functions
public class Utils {

    public static boolean isInteger(String val) {
        try {
            Integer.parseInt(val);
            return true;
        } catch(NumberFormatException ex) {
            return false;
        }
    }
}
