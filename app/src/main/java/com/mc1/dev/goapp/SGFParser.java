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
        RunningGame rg = new RunningGame(gmi);

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
                // Theoretically the whole sgf file could be written into a single line.
                String lineSplit[] = line.split(";");

                for (String ls : lineSplit) {

                    android.support.v4.util.ArrayMap<String, String> nodeList = readProperties(ls, propertyValue, propertyId, isInsidePropertyVal);

                    for (android.support.v4.util.ArrayMap.Entry<String, String> entry : nodeList.entrySet()) {
                        switch (entry.getKey()) {
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

                                Log.i(LOG_TAG, "\tB[" + position[0] + " " + position[1] + "]\t" + parentNode.toString());
                                break;
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
                                Log.i(LOG_TAG, "\tW[" + position[0] + " " + position[1] + "]\t" + parentNode.toString());
                                //Log.i(LOG_TAG, "\t\tWrg[" + rg.getSpecificNode(parentNode).getPosition()[0] + " " + rg.getSpecificNode(parentNode).getPosition()[1] + "]");
                                break;
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
                            case "WL":
                                try {
                                    float t = Float.parseFloat(entry.getValue());
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
                                // In the sgf specification a GM value of 1 has been specified for
                                // the game of go. If a different value is found in the file, it
                                // certainly does not contain the desired information.
                                if (!entry.getValue().equals("1"))
                                    throw new InvalidParameterException("Wrong Game Type!");
                                break;
                            case "SZ":
                                try {
                                    rg.getGameMetaInformation().setBoardSize(Integer.parseInt(entry.getValue()));
                                } catch (NumberFormatException e) {
                                    Log.w(LOG_TAG, "Could not parse board size value" + e.getMessage());
                                }
                                break;
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
                                    //Log.i(LOG_TAG, "Date: " + rg.getGameMetaInformation().getDates()[0].toString());
                                } catch (Exception e) {
                                    Log.w(LOG_TAG, "Could not parse dates: " + e.getMessage());
                                }
                                break;
                            case "C":
                                rg.getSpecificNode(parentNode).setComment(entry.getValue());
                                //Log.i(LOG_TAG, "C: " + rg.getSpecificNode(parentNode).getComment());
                                break;
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
    // function boolean save(RunningGame rg, String fileName)
    //
    // saves the contents of the RunningGame object to the file with the name
    // fileName to the Downloads external storage directory.
    // ----------------------------------------------------------------------
    public void save(RunningGame rg, String fileName) throws IOException {
        // games should be stored on the external storage of the device, so as
        // to enable other apps to use the sgf files.
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            throw new IOException("External Storage not mounted");
        }

        //File directoryFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File directory = new File(Environment.getExternalStorageDirectory() + "/SGF_files/");
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Log.i(LOG_TAG, "Directory is not created: " + directory.getAbsolutePath());
            }
        }


        File file = new File(directory, fileName);

        if (file.canWrite()) {
            Log.i(LOG_TAG, "File is writable!");
            Log.i(LOG_TAG, file.getAbsolutePath());
        }

        BufferedWriter bw = null;

        try {
            FileWriter fw = new FileWriter(file);

            bw = new BufferedWriter(fw);

            bw.write("(;");

            bw.write(rg.getGameMetaInformation().toString());

            ArrayList<Integer> iterator = new ArrayList<>();
            MoveNode currentNode = rg.getSpecificNode(iterator);

            Stack<ArrayList<Integer>> stack = new Stack<>();

            //descentVariations(currentNode, bw, iterator, stack, rg);
            bw.write(descendThisChild(currentNode));

            while (currentNode.getChildren().size() != 0) {
                Log.i(LOG_TAG, "\t" + getMoveValues(currentNode));
                currentNode = currentNode.getChildren().get(0);
            }


            bw.write(")");

            bw.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Could not open file to write " + e.getMessage());
        } finally {
            if (bw != null) {
                try {
                    bw.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Could not close buffered writer " + e.getMessage());
                }
            }
        }
    }


    private void writeMoves(BufferedWriter bw, RunningGame rg) throws IOException {
        ArrayList<Integer> nodeIndex = new ArrayList<>();

        MoveNode currentNode = rg.getSpecificNode(nodeIndex);

        Stack<ArrayList<Integer>> stack = new Stack<>();


        while (true) {
            if (currentNode.hasChildren()) nodeIndex.add(0);
            for (int i = currentNode.getChildren().size() - 1; i >= 0; i--) {
                nodeIndex.set(nodeIndex.size() - 1, i);
                stack.push(new ArrayList<>(nodeIndex));
            }
            if (stack.isEmpty() && !currentNode.hasChildren()) break;

            bw.write(getMoveValues(currentNode));

            if (currentNode.getChildren().size() > 1) {
                bw.write("(");
            }
            if (!currentNode.hasChildren()) {
                bw.write(")");
            }


            try {
                currentNode = rg.getSpecificNode(stack.pop());
            } catch (EmptyStackException e) {
                e.printStackTrace();
            }

        }

    }

    private String descendThisChild(MoveNode input) {
        String res = "";
        MoveNode parent = new MoveNode(input);
        if (parent.getChildren().size() > 1) {
            res += getMoveValues(parent) + "(";
            for (int children = 0; children < parent.getChildren().size(); children++) {
                res += descendThisChild(parent.getChildren().get(children));
            }
            res += ")";
        } else if (parent.getChildren().size() == 1) {
            res += getMoveValues(parent) + descendThisChild(parent.getChildren().get(0));
        } else {
            res += getMoveValues(parent) + ")";
        }
        return res;
    }

    private String getMoveValues(MoveNode currentNode) {
        String coordinate;
        String res = "";

        if (currentNode.getActionType().equals(GameMetaInformation.actionType.MOVE)) {
            char xCoordinate = (char) ((int) 'a' + currentNode.getPosition()[0]);
            char yCoordinate = (char) ((int) 'a' + currentNode.getPosition()[1]);
            //Log.i(LOG_TAG, Integer.toString(currentNode.getPosition()[0]) + " " + Integer.toString(currentNode.getPosition()[1]));
            coordinate = Character.toString(xCoordinate) + Character.toString(yCoordinate);
        } else {
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
        return res;
    }


    private void descentVariations(MoveNode currentNode, BufferedWriter bw, ArrayList<Integer> iterator,
                                   Stack<ArrayList<Integer>> stack, RunningGame rg) throws IOException {
        String coords = "";

        while (!currentNode.getChildren().isEmpty()) {

            if (currentNode.getActionType() != GameMetaInformation.actionType.RESIGN) {
                if (currentNode.getActionType().equals(GameMetaInformation.actionType.MOVE)) {
                    char xCoord = (char) ((int) 'a' + currentNode.getPosition()[0]);
                    char yCoord = (char) ((int) 'a' + currentNode.getPosition()[1]);
                    Log.i(LOG_TAG, Integer.toString(currentNode.getPosition()[0]) + " " + Integer.toString(currentNode.getPosition()[1]));
                    coords = Character.toString(xCoord) + Character.toString(yCoord);
                }

                if (currentNode.isBlacksMove()) {
                    bw.write(";B[" + coords + "]");
                    if (currentNode.getTime() != GameMetaInformation.INVALID_LONG) {
                        bw.write("BL[" + (float) currentNode.getTime() / 1000.0f + "]");
                    }
                    if (currentNode.getOtPeriods() != GameMetaInformation.INVALID_BYTE) {
                        bw.write("OB[" + currentNode.getOtPeriods() + "]");
                    }
                } else {
                    bw.write(";W[" + coords + "]");
                    if (currentNode.getTime() != GameMetaInformation.INVALID_LONG) {
                        bw.write("WL[" + (float) currentNode.getTime() / 1000.0f + "]");
                    }
                    if (currentNode.getOtPeriods() != GameMetaInformation.INVALID_BYTE) {
                        bw.write("OW[" + currentNode.getOtPeriods() + "]");
                    }
                }

                if (currentNode.getComment() != null) {
                    bw.write("C[" + currentNode.getComment() + "]");
                }

                // due to the exit condition of the while loop it is safe to assume, that at
                // least one child is present
                iterator.add(0);

                for (int i = currentNode.getChildren().size() - 1; i >= 0; i--) {
                    iterator.set(iterator.size() - 1, i);
                    stack.push(new ArrayList<>(iterator));
                    Log.i(LOG_TAG, stack.toString());
                }

                if (currentNode.getChildren().size() > 1) {
                    bw.write("(");
                }
                try {
                    currentNode = rg.getSpecificNode(stack.pop());
                } catch (EmptyStackException e) {
                    Log.e(LOG_TAG, e.getMessage());
                }
            }
        }
        if (currentNode.getChildren().isEmpty() && !stack.isEmpty()) {
            try {
                currentNode = rg.getSpecificNode(stack.pop());
                bw.write(")");
                descentVariations(currentNode, bw, iterator, stack, rg);
            } catch (EmptyStackException e) {
                Log.e(LOG_TAG, e.getMessage());
            }
        }
    }
}
