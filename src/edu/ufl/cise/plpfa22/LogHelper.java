package edu.ufl.cise.plpfa22;

public class LogHelper {

    private static final boolean SHOW_OUTPUT = false;

    public static void printOutput(Object text) {
        if (SHOW_OUTPUT) {
            System.out.println(text);
        }
    }

}
