package com.mc1.dev.goapp;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.Stack;

// ----------------------------------------------------------------------
// class SGFParser
// This class provides functionality to either convert a .sgf file to
// the internal representation of a game as well as the other way around
// ----------------------------------------------------------------------
public class SGFParser {
    private static final String LOG_TAG = SGFParser.class.getSimpleName();

    public SGFParser() {
    }

    // ----------------------------------------------------------------------
    // function parse(InputStream input)
    //
    // parses the input file and returns a corresponding RunningGame object.
    // In case the file could not be opened or the read process fails an
    // IOException is thrown.
    // ----------------------------------------------------------------------
    public RunningGame parse(InputStream input) throws IOException, InvalidParameterException {
        /*                  Variations:
        * A variation main line of play always starts with a '(' character. However the sgf format
        * is designed in the way, that the actual moves of the variation are stored at the
        * end of the file. Therefore the main variation continues after a '(' has been encountered
        * until a ')' is reached. From there the line of play jumps to the position of the last '('.
        * Therefore a stack has been used as a data structure to represent variations to the main
        * line of play, as the last element of the stack is always the parent node to return to.
        */
        Stack<ArrayList<Integer>> stack = new Stack<>();
        int noOfChildren;

        // the variable is used to handle multiline properties.
        boolean isInsidePropertyVal = false;

        // used to store the resign move at the very end of the main variation
        int sizeOfMainVariation = 0;

        // the list of indices characterising the respective parent of the currently regarded node
        ArrayList<Integer> parentNode = new ArrayList<>();

        // initialize the BufferedReader with null so that in case of an error it can be checked
        // whether the reader needs to be closed. See finally block.
        BufferedReader br = null;

        GameMetaInformation gmi = new GameMetaInformation();
        // TODO handle handicap
        gmi.setHandicap(0);
        RunningGame rg = new RunningGame(gmi);

        // get the char representation of a move just outside the size of the board
        char outOfBounds = (char) ((int) ('a') + rg.getGameMetaInformation().getBoardSize());

        try {
            br = new BufferedReader(new InputStreamReader(input));
            String line;

            // in case of multiline properties both propertyValue and propertyId need to be
            // accessible outside of the currently read line.
            StringBuilder propertyValue = new StringBuilder();
            StringBuilder propertyId = new StringBuilder();

            // the BufferedReader reads the input line by line.
            while ((line = br.readLine()) != null) {
                // split the current line in case there are multiple nodes in one line.
                // Theoretically the whole sgf file could be written into a single line (according
                // to the specification).
                String lineSplit[] = line.split(";");

                for (String ls : lineSplit) {

                    // isInsidePropertyVal is accessed and changed within the calling function
                    android.support.v4.util.ArrayMap<String, String> nodeList = readProperties(ls, propertyValue, propertyId, isInsidePropertyVal);

                    for (android.support.v4.util.ArrayMap.Entry<String, String> entry : nodeList.entrySet()) {
                        switch (entry.getKey()) {
                            // keyword representing a black move
                            case "B":
                                int position[] = new int[2];
                                // a move of the form B[] or B['boardSize+1', 'boardSize+1'] is
                                // considered a pass move
                                if (entry.getValue().length() != 0 && entry.getValue().charAt(0) != outOfBounds) {
                                    // convert letter describing the position of a stone to the more
                                    // intuitive integer
                                    position[0] = (entry.getValue().charAt(0) - 'a');
                                    position[1] = (entry.getValue().charAt(1) - 'a');

                                    // recordMove returns the index of the newly inserted MoveNode in
                                    // relation to its parent, which is also the number of children of
                                    // this node
                                    noOfChildren = rg.recordMove(GameMetaInformation.actionType.MOVE, position, parentNode);
                                } else {
                                    // the position of a pass move is just outside the board
                                    position[0] = rg.getGameMetaInformation().getBoardSize();
                                    position[1] = rg.getGameMetaInformation().getBoardSize();

                                    // recordMove returns the index of the newly inserted MoveNode in
                                    // relation to its parent, which is also the number of children of
                                    // this node
                                    noOfChildren = rg.recordMove(GameMetaInformation.actionType.PASS, position, parentNode);
                                }

                                // new parent node is the newly inserted MoveNode
                                parentNode.add(noOfChildren);

                                // only increment, when the node added is a first child
                                // --> main variation
                                if (noOfChildren == 0) sizeOfMainVariation++;

                                //Log.i(LOG_TAG, "\tB[" + position[0] + " " + position[1] + "]\t" + parentNode.toString());
                                break;
                            // keyword representing a white move
                            case "W":
                                position = new int[2];
                                if (entry.getValue().length() != 0 && entry.getValue().charAt(0) != outOfBounds) {
                                    position[0] = (entry.getValue().charAt(0) - 'a');
                                    position[1] = (entry.getValue().charAt(1) - 'a');
                                    noOfChildren = rg.recordMove(GameMetaInformation.actionType.MOVE, position, parentNode);
                                } else {
                                    position[0] = rg.getGameMetaInformation().getBoardSize();
                                    position[1] = rg.getGameMetaInformation().getBoardSize();
                                    noOfChildren = rg.recordMove(GameMetaInformation.actionType.PASS, position, parentNode);
                                }

                                parentNode.add(noOfChildren);

                                if (noOfChildren == 0) sizeOfMainVariation++;
                                //Log.i(LOG_TAG, "\tW[" + position[0] + " " + position[1] + "]\t" + parentNode.toString());
                                //Log.i(LOG_TAG, "\t\tWrg[" + rg.getSpecificNode(parentNode).getPosition()[0] + " " + rg.getSpecificNode(parentNode).getPosition()[1] + "]");
                                break;
                            // blacks time left
                            case "BL":
                                try {
                                    float t = Float.parseFloat(entry.getValue());
                                    // convert the time from seconds to milliseconds
                                    // the current node is in this case the defined by the parentNode
                                    // index Array, because the BL property is always evaluated after
                                    // the corresponding B property. From this it follows, that the
                                    // current node has already been set as the parent node.
                                    rg.getSpecificNode(parentNode).setTime((long) (t * 1000.0f));
                                } catch (NumberFormatException e) {
                                    Log.w(LOG_TAG, "Could not parse time value" + e.getMessage());
                                }
                                //Log.i(LOG_TAG, "\t TimeLeft(b): " + rg.getSpecificNode(parentNode).getTime());
                                break;
                            // whites time left
                            case "WL":
                                try {
                                    float t = Float.parseFloat(entry.getValue());
                                    rg.getSpecificNode(parentNode).setTime((long) (t * 1000.0f));
                                } catch (NumberFormatException e) {
                                    Log.w(LOG_TAG, "Could not parse time value" + e.getMessage());
                                }
                                //Log.i(LOG_TAG, "\t TimeLeft(w): " + rg.getSpecificNode(parentNode).getTime());
                                break;
                            // number of black overtimes left
                            case "OB":
                                try {
                                    byte ot = Byte.parseByte(entry.getValue());
                                    rg.getSpecificNode(parentNode).setOtPeriods(ot);
                                } catch (NumberFormatException e) {
                                    Log.w(LOG_TAG, "Could not parse over time period value" + e.getMessage());
                                }
                                //Log.i(LOG_TAG, "\t OT Periods(b): " + rg.getSpecificNode(parentNode).getOtPeriods());
                                break;
                            // number of white overtimes left
                            case "OW":
                                try {
                                    byte ot = Byte.parseByte(entry.getValue());
                                    rg.getSpecificNode(parentNode).setOtPeriods(ot);
                                } catch (NumberFormatException e) {
                                    Log.w(LOG_TAG, "Could not parse over time period value" + e.getMessage());
                                }
                                //Log.i(LOG_TAG, "\t OT Periods(w): " + rg.getSpecificNode(parentNode).getOtPeriods());
                                break;
                            // the game type contained in this file
                            case "GM":
                                // In the sgf specification a GM value of 1 has been specified for
                                // the game of go. If a different value is found in the file, it
                                // certainly does not contain the desired information.
                                if (!entry.getValue().equals("1"))
                                    throw new InvalidParameterException("Wrong Game Type!");
                                break;
                            // the board size
                            case "SZ":
                                try {
                                    rg.getGameMetaInformation().setBoardSize(Integer.parseInt(entry.getValue()));
                                } catch (NumberFormatException e) {
                                    Log.w(LOG_TAG, "Could not parse board size value" + e.getMessage());
                                }
                                break;
                            // komi
                            case "KM":
                                try {
                                    rg.getGameMetaInformation().setKomi(Float.parseFloat(entry.getValue()));
                                } catch (NumberFormatException e) {
                                    Log.w(LOG_TAG, "Could not parse komi value" + e.getMessage());
                                }
                                break;
                            case "RU":
                                break;
                            // TODO store time settings in the gmi?
                            case "TM":
                                break;
                            case "OT":
                                break;
                            // name of the black player
                            case "PB":
                                rg.getGameMetaInformation().setBlackName(entry.getValue());
                                break;
                            // name of the white player
                            case "PW":
                                rg.getGameMetaInformation().setWhiteName(entry.getValue());
                                break;
                            // rank of the black player
                            case "BR":
                                rg.getGameMetaInformation().setBlackRank(entry.getKey());
                                break;
                            // rank of the white player
                            case "WR":
                                rg.getGameMetaInformation().setWhiteRank(entry.getValue());
                                break;
                            // date of the game
                            case "DT":
                                try {
                                    String dates[] = GameMetaInformation.convertSgfStringToArray(entry.getValue());
                                    rg.getGameMetaInformation().setDates(dates);
                                    //Log.i(LOG_TAG, "Date: " + rg.getGameMetaInformation().getDates()[0].toString());
                                } catch (Exception e) {
                                    Log.w(LOG_TAG, "Could not parse dates: " + e.getMessage());
                                }
                                break;
                            // comment for the current node
                            case "C":
                                rg.getSpecificNode(parentNode).setComment(entry.getValue());
                                //Log.i(LOG_TAG, "C: " + rg.getSpecificNode(parentNode).getComment());
                                break;
                            // result for the current game
                            case "RE":
                                rg.getGameMetaInformation().setResult(entry.getValue());
                                //Log.i(LOG_TAG, "RES: " + rg.getGameMetaInformation().getResult());
                                break;
                        }
                    }

                    // the following block handles the branching out of variations
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

        // if the game was won by resignation a MoveNode with the actionType RESIGN needs to be
        // added to the very end of the tree.
        if (rg.getGameMetaInformation().getResult().matches("[BW]\\+(R)|(Resign)")) {
            ArrayList<Integer> lastMainNode = new ArrayList<>(Collections.nCopies(sizeOfMainVariation, 0));
            int invalidPosition[] = {rg.getGameMetaInformation().getBoardSize() + 1, rg.getGameMetaInformation().getBoardSize() + 1};
            rg.recordMove(GameMetaInformation.actionType.RESIGN, invalidPosition, lastMainNode);
        }

        return rg;
    }

    // ----------------------------------------------------------------------
    // function ArrayMap<String, String> readProperties(String linePart,
    //      StringBuilder propertyVal, StringBuilder propertyId)
    //
    // reads and separates all properties and their values from the given
    // string. Writes the results to an ArrayMap.
    // propertyVal and propertyId are modified in the process.
    // ----------------------------------------------------------------------
    private android.support.v4.util.ArrayMap<String, String> readProperties(String linePart, StringBuilder propertyVal, StringBuilder propertyId, boolean isInsidePropertyVal) {
        android.support.v4.util.ArrayMap<String, String> res = new android.support.v4.util.ArrayMap<>();

        // if the passed propertyVal is not empty the reason needs to be a newline character inside
        // a game comment. Thus preserving the newline character:
        if (propertyVal.length() != 0) {
            propertyVal.append('\n');
        }

        for (int strlen = 0; strlen < linePart.length(); ++strlen) {

            // if one or two capital letters directly followed by a [ are encountered this must be
            // a property identifier.
            // everything inside the following pair of [] must be the corresponding property value

            if (Character.isUpperCase(linePart.charAt(strlen)) && !isInsidePropertyVal) {
                propertyId.append(linePart.charAt(strlen));
            }

            switch (linePart.charAt(strlen)) {
                case '[':
                    // if the parser is already in a pair of [] every following [ needs to be
                    // conserved in the string.
                    if (isInsidePropertyVal) propertyVal.append(linePart.charAt(strlen));
                    isInsidePropertyVal = true;
                    break;

                case ']':
                    // the current property is only supposed to be written to res if the property
                    // is finished by a ] character not by an escaped (\]) character.
                    if (strlen - 1 >= 0 && linePart.charAt(strlen - 1) != '\\') {
                        res.put(propertyId.toString(), propertyVal.toString());
                        isInsidePropertyVal = false;
                        // reset both StringBuilder for use with the next property
                        propertyId.setLength(0);
                        propertyVal.setLength(0);
                    }
                    break;
            }
            // every character inside the brackets is taken as a property value. Concerning the [
            // character see above case '[' statement.
            if (isInsidePropertyVal && linePart.charAt(strlen) != '[') {
                propertyVal.append(linePart.charAt(strlen));
            }
        }

        return res;
    }

    // ----------------------------------------------------------------------
    // function boolean save(RunningGame rg, String fileNameNoExtension)
    //
    // saves the contents of the RunningGame object to the file with the name
    // fileNameNoExtension to the external storage directory into the subdirectory
    // "SGF_files".
    // ----------------------------------------------------------------------
    public String save(RunningGame rg, String fileNameNoExtension) throws IOException {
        // games should be stored on the external storage of the device, so as
        // to enable other apps to use the sgf files.
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            throw new IOException("External Storage not mounted");
        }

        File directory = new File(Environment.getExternalStorageDirectory() + "/SGF_files/");
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Log.i(LOG_TAG, "Directory is not created: " + directory.getAbsolutePath());
                throw new IOException("External storage subdirectory \"SGF_files\" could not be created");
            }
        }

        File file = new File(directory, fileNameNoExtension + ".sgf");

        // if the file with the desired name already exists a postfix is appended to the end of
        // the file to ensure uniqueness.
        if (file.exists()) {
            int postfixToBeAppended = 1;
            while (true) {
                file = new File(directory, fileNameNoExtension + "_" + postfixToBeAppended + ".sgf");
                if (!file.exists()) break;
                postfixToBeAppended++;
            }
        }

        //DEBUG
        /*
        if (file.canWrite()) {
            Log.i(LOG_TAG, "File is writable!");
            Log.i(LOG_TAG, file.getAbsolutePath());
        }
        */

        BufferedWriter bw = null;

        try {
            FileWriter fw = new FileWriter(file);
            bw = new BufferedWriter(fw);

            // the file always starts with an opening bracket and an empty node which represents
            // the root node.
            bw.write("(;");
            // the root node usually contains the meta information about the game.
            bw.write(rg.getGameMetaInformation().toString());

            MoveNode currentNode = rg.getRootNode();

            String tmp = descendThisChild(currentNode);
            Log.i(LOG_TAG, tmp);
            bw.write(tmp);

            bw.write(")");

            bw.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Could not open file to write " + e.getMessage());
            throw new IOException("Could not open selected file to write");
        } finally {
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Could not close buffered writer " + e.getMessage());
                }
            }
        }
        return file.getName();
    }

    // ----------------------------------------------------------------------
    // function String descendThisChild(MoveNode input)
    //
    // recursive function that descends all MoveNodes found as children of
    // the passed input node
    // ----------------------------------------------------------------------
    private String descendThisChild(MoveNode input) {
        String res = "";
        MoveNode parent = new MoveNode(input);
        if (parent.getParent() != null) res += getMoveValues(parent);
        if (parent.getChildren().size() != 1) {
            for (int children = 0; children < parent.getChildren().size(); children++) {
                res += "(";
                res += descendThisChild(parent.getChildren().get(children));
                res += ")";
            }
        } else {
            res += descendThisChild(parent.getChildren().get(0));
        }
        return res;
    }

    // ----------------------------------------------------------------------
    // function String getMoveValues(MoveNode currentNode)
    //
    // extracts the values of the corresponding black or white turn into a
    // String that adheres to the .sgf syntax
    // ----------------------------------------------------------------------
    private String getMoveValues(MoveNode currentNode) {
        String coordinate;
        String res = "";

        if (currentNode.getActionType().equals(GameMetaInformation.actionType.MOVE)) {
            char xCoordinate = (char) ((int) 'a' + currentNode.getPosition()[0]);
            char yCoordinate = (char) ((int) 'a' + currentNode.getPosition()[1]);
            coordinate = Character.toString(xCoordinate) + Character.toString(yCoordinate);
        } else {
            // a PASS move is stored as an empty move
            coordinate = "";
        }

        if (currentNode.getActionType() != GameMetaInformation.actionType.RESIGN) {
            if (currentNode.isBlacksMove()) {

                res += ";B[" + coordinate + "]";
                if (currentNode.getTime() != GameMetaInformation.INVALID_LONG) {
                    res += "BL[" + (float) currentNode.getTime() / 1000.0f + "]";
                }
                if (currentNode.getOtPeriods() != GameMetaInformation.INVALID_BYTE) {
                    res += "OB[" + currentNode.getOtPeriods() + "]";
                }
            } else {
                res += ";W[" + coordinate + "]";
                if (currentNode.getTime() != GameMetaInformation.INVALID_LONG) {
                    res += "WL[" + (float) currentNode.getTime() / 1000.0f + "]";
                }
                if (currentNode.getOtPeriods() != GameMetaInformation.INVALID_BYTE) {
                    res += "OW[" + currentNode.getOtPeriods() + "]";
                }
            }
            if (currentNode.getComment() != null) {
                res += "C[" + currentNode.getComment() + "]";
            }
        }
        // for readability purposes each node is terminated by a newline
        res += "\n";
        return res;
    }
}
