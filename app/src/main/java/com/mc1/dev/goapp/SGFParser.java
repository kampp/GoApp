package com.mc1.dev.goapp;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.EmptyStackException;
import java.util.Stack;

// ----------------------------------------------------------------------
// class SGFParser
// This class provides functionality to either convert a .sgf file to
// the internal representation of a game as well as the other way around
// ----------------------------------------------------------------------
public class SGFParser {
    private static final String LOG_TAG = SGFParser.class.getSimpleName();
    private boolean isInsidePropertyVal = false;

    public SGFParser() {
    }

    // function parses the input file and returns a corresponding RunningGame object.
    // In case the file could not be opened or the read process fails an IOException
    // is thrown.
    public RunningGame parse(InputStream input) throws IOException, InvalidParameterException {
        Stack<ArrayList<Integer>> stack = new Stack<>();
        int position[] = new int[2];
        int noOfChildren;

        ArrayList<Integer> parentNode = new ArrayList<>();

        BufferedReader br = null;

        GameMetaInformation gmi = new GameMetaInformation();
        RunningGame rg = new RunningGame(gmi);

        try {
            br = new BufferedReader(new InputStreamReader(input));
            String line;

            StringBuilder propertyValue = new StringBuilder();
            StringBuilder propertyId = new StringBuilder();

            while ((line = br.readLine()) != null) {
                String lineSplit[] = line.split(";");

                for (String ls : lineSplit) {

                    android.support.v4.util.ArrayMap<String, String> nodeList = readProperties(ls, propertyValue, propertyId);

                    for (android.support.v4.util.ArrayMap.Entry<String, String> entry : nodeList.entrySet()) {
                        switch (entry.getKey()) {
                            case "B":
                                position[0] = (entry.getValue().charAt(0) - 'a');
                                position[1] = (entry.getValue().charAt(1) - 'a');
                                noOfChildren = rg.recordMove(GameMetaInformation.actionType.MOVE, position, parentNode);

                                parentNode.add(noOfChildren);

                                Log.i(LOG_TAG, "\tB[" + position[0] + " " + position[1] + "]\t" + parentNode.toString());
                                break;
                            case "W":
                                position[0] = (entry.getValue().charAt(0) - 'a');
                                position[1] = (entry.getValue().charAt(1) - 'a');
                                noOfChildren = rg.recordMove(GameMetaInformation.actionType.MOVE, position, parentNode);

                                parentNode.add(noOfChildren);

                                Log.i(LOG_TAG, "\tW[" + position[0] + " " + position[1] + "]\t" + parentNode.toString());
                                break;
                            case "BL":
                                try {
                                    float t = Float.parseFloat(entry.getValue());
                                    // convert the time from seconds to milliseconds
                                    rg.getSpecificNode(parentNode).setTime((long) (t * 1000.0f));
                                } catch (NumberFormatException e) {
                                    Log.w(LOG_TAG, "Could not parse time value" + e.getMessage());
                                }
                                //Log.i(LOG_TAG, "\t TimeLeft(b): " + rg.getSpecificNode(parentNode).getTime());
                                break;
                            case "WL":
                                try {
                                    float t = Float.parseFloat(entry.getValue());
                                    // convert the time from seconds to milliseconds
                                    rg.getSpecificNode(parentNode).setTime((long) (t * 1000.0f));
                                } catch (NumberFormatException e) {
                                    Log.w(LOG_TAG, "Could not parse time value" + e.getMessage());
                                }
                                //Log.i(LOG_TAG, "\t TimeLeft(w): " + rg.getSpecificNode(parentNode).getTime());
                                break;
                            case "OB":
                                try {
                                    byte ot = Byte.parseByte(entry.getValue());
                                    rg.getSpecificNode(parentNode).setOtPeriods(ot);
                                } catch (NumberFormatException e) {
                                    Log.w(LOG_TAG, "Could not parse over time period value" + e.getMessage());
                                }
                                //Log.i(LOG_TAG, "\t OT Periods(b): " + rg.getSpecificNode(parentNode).getOtPeriods());
                                break;
                            case "OW":
                                try {
                                    byte ot = Byte.parseByte(entry.getValue());
                                    rg.getSpecificNode(parentNode).setOtPeriods(ot);
                                } catch (NumberFormatException e) {
                                    Log.w(LOG_TAG, "Could not parse over time period value" + e.getMessage());
                                }
                                //Log.i(LOG_TAG, "\t OT Periods(w): " + rg.getSpecificNode(parentNode).getOtPeriods());
                                break;
                            case "GM":
                                if (!entry.getValue().equals("1"))
                                    throw new InvalidParameterException("Wrong Game Type!");
                                break;
                            case "SZ":
                                rg.getGameMetaInformation().setBoardSize(Integer.parseInt(entry.getValue()));
                                break;
                            case "KM":
                                rg.getGameMetaInformation().setKomi(Float.parseFloat(entry.getValue()));
                                break;
                            case "RU":
                                break;
                            case "TM":
                                break;
                            case "OT":
                                break;
                            case "PB":
                                rg.getGameMetaInformation().setBlackName(entry.getValue());
                                break;
                            case "PW":
                                rg.getGameMetaInformation().setWhiteName(entry.getValue());
                                break;
                            case "BR":
                                rg.getGameMetaInformation().setBlackRank(entry.getKey());
                                break;
                            case "WR":
                                rg.getGameMetaInformation().setWhiteRank(entry.getValue());
                                break;
                            case "DT":
                                try {
                                    Date dates[] = GameMetaInformation.convertStringToDates(entry.getValue());
                                    rg.getGameMetaInformation().setDates(dates);
                                    Log.i(LOG_TAG, "Date: " + rg.getGameMetaInformation().getDates()[0].toString());
                                } catch (Exception e) {
                                    Log.w(LOG_TAG, "Could not parse dates: " + e.getMessage());
                                }
                                break;
                            case "C":
                                rg.getSpecificNode(parentNode).setComment(entry.getValue());
                                Log.i(LOG_TAG, "C: " + rg.getSpecificNode(parentNode).getComment());
                                break;
                            case "RE":
                                rg.getGameMetaInformation().setResult(entry.getValue());
                                Log.i(LOG_TAG, "RES: " + rg.getGameMetaInformation().getResult());
                                break;
                        }
                    }

                    for (int j = 0; j < ls.length(); ++j) {
                        switch (ls.charAt(j)) {
                            case '(':
                                stack.push(new ArrayList<>(parentNode));
                                //Log.i(LOG_TAG, "Stack Push: " + stack.toString() + "\n");
                                break;
                            case ')':
                                try {
                                    parentNode = stack.pop();
                                    //Log.i(LOG_TAG, "Stack Pop: " + stack.toString() + "\n");
                                } catch (EmptyStackException e) {
                                    Log.e(LOG_TAG, "Something went wrong in the sgf File. ");
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }
                }
            }
            if (BuildConfig.DEBUG && !stack.isEmpty()) {
                throw new AssertionError();
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Could not open inputStram. " + e.getMessage());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Could not close buffered Reader. " + e.getMessage());
                }
            }
        }

        return rg;
    }

    private android.support.v4.util.ArrayMap<String, String> readProperties(String linePart, StringBuilder propertyVal, StringBuilder propertyId) {
        android.support.v4.util.ArrayMap<String, String> res = new android.support.v4.util.ArrayMap<>();

        // if you encounter one or two capital letters directly followed by a [ this must be a
        // property identifier
        // everything inside the following pair of [] must be the corresponding property value

        if (propertyVal.length() != 0) {
            propertyVal.append('\n');
        }

        for (int strlen = 0; strlen < linePart.length(); ++strlen) {

            if (Character.isUpperCase(linePart.charAt(strlen)) && !isInsidePropertyVal) {
                propertyId.append(linePart.charAt(strlen));
            }

            switch (linePart.charAt(strlen)) {
                case '[':
                    if (isInsidePropertyVal) propertyVal.append(linePart.charAt(strlen));
                    isInsidePropertyVal = true;
                    break;

                case ']':
                    if (strlen - 1 >= 0 && linePart.charAt(strlen - 1) != '\\') {
                        res.put(propertyId.toString(), propertyVal.toString());
                        isInsidePropertyVal = false;
                        propertyId.setLength(0);
                        propertyVal.setLength(0);
                    }
                    break;
            }
            if (isInsidePropertyVal && linePart.charAt(strlen) != '[') {
                propertyVal.append(linePart.charAt(strlen));
            }
        }

        return res;
    }
}
