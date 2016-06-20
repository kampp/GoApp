package com.mc1.dev.goapp;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.EmptyStackException;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ----------------------------------------------------------------------
// class SGFParser
// This class provides functionality to either convert a .sgf file to
// the internal representation of a game as well as the other way around
// ----------------------------------------------------------------------
public class SGFParser {
    private static final String LOG_TAG = SGFParser.class.getSimpleName();

    private final Pattern blacksMove = Pattern.compile("B\\[([a-t]{0,2})\\]");
    private final Pattern whitesMove = Pattern.compile("W\\[([a-t]{0,2})\\]");
    private final Pattern blacksTimeLeft = Pattern.compile("BL\\[(\\d+(\\.\\d{1,3})?)\\]");
    private final Pattern whitesTimeLeft = Pattern.compile("WL\\[(\\d+(\\.\\d{1,3})?)\\]");
    private final Pattern blacksMovesLeft = Pattern.compile("OB\\[(\\d+)\\]");
    private final Pattern whitesMovesLeft = Pattern.compile("OW\\[(\\d+)\\]");
    private final Pattern gameIdentifier = Pattern.compile("GM\\[(\\d)\\]");
    private final Pattern boardSize = Pattern.compile("SZ\\[(\\d{1,2})\\]");
    private final Pattern komi = Pattern.compile("KM\\[((-)?\\d+(\\.[50]0)?)\\]");
    private final Pattern mainTime = Pattern.compile("TM\\[(\\d+)\\]");
    private final Pattern overTime = Pattern.compile("OT\\[(\\d+[/x]\\d+\\p{Blank}[a-zA-Z\\p{Punct}]+)\\]");
    private final Pattern playerBlack = Pattern.compile("PB\\[(\\p{Alnum}+)\\]");
    private final Pattern playerWhite = Pattern.compile("PW\\[(\\p{Alnum}+)\\]");
    private final Pattern blackRank = Pattern.compile("BR\\[(\\d{1,2}[kd])\\]");
    private final Pattern whiteRank = Pattern.compile("WR\\[(\\d{1,2}[kd])\\]");
    private final Pattern date = Pattern.compile("DT\\[(\\d{4}(-\\d{2}){2})\\]");
    // TODO date format needs to be corrected see specification of sgf
    // strings in the form "YYYY-MM-DD,DD,YYYY-MM,YYYY" are valid!!
    private final Pattern comment = Pattern.compile("C\\[(\\p{Alnum}+)\\]");
    private final Pattern result = Pattern.compile("RE\\[([WB]\\+\\p{Alnum}+)\\]");


    public SGFParser() {
    }

    public RunningGame parse(File sgfFile) {
        String content = readFile(sgfFile);

        GameMetaInformation gmi = new GameMetaInformation();

        RunningGame rg = new RunningGame(gmi);

        String[] allNodes = content.split(";");
        Stack<Integer> stack = new Stack<>();

        // TODO implement the support for variations
        for (int i = 0; i < allNodes.length; ++i) {
            if (allNodes[i].contains("(")) stack.push(i);
            if (allNodes[i].contains(")")) {
                try {
                    stack.pop();
                } catch (EmptyStackException e) {
                    Log.e(LOG_TAG, "Something is wrong with the SGF File");
                }
            }
            readProperties(allNodes[i], rg);
        }
        return rg;
    }

    private void readProperties(String node, RunningGame rg) {

        MoveNode currentMoveNode = rg.getCurrentNode();
        MoveNode newMoveNode;

        String gameIdent = getPropertyValue(node, gameIdentifier);
        if (!gameIdent.equals("") && !gameIdent.equals("1")) { // Go has the GM value 1
            //TODO handle sgf files which specify the wrong game.
        }

        String boardSize = getPropertyValue(node, this.boardSize);
        if (!boardSize.equals("")) {
            try {
                rg.getGameMetaInformation().setBoardSize(Integer.parseInt(boardSize));
            } catch (NumberFormatException e) {
                Log.w(LOG_TAG, "The given board size could not be parsed.");
            }

        }

        String komi = getPropertyValue(node, this.komi);
        if (!komi.equals("")) {
            try {
                rg.getGameMetaInformation().setKomi(Float.parseFloat(komi));
            } catch (NumberFormatException e) {
                // TODO decide whether default value should be set in case of parsing failure.
                Log.w(LOG_TAG, "The given komi can not be converted to float.");//\nUsing default value instead");
                //rg.getGameMetaInformation().setKomi(6.5f);
            }
        }

        String pBlack = getPropertyValue(node, playerBlack);
        if (!pBlack.equals("")) {
            rg.getGameMetaInformation().setBlackName(pBlack);
        }

        String pWhite = getPropertyValue(node, playerWhite);
        if (!pWhite.equals("")) {
            rg.getGameMetaInformation().setWhiteName(pWhite);
        }

        String bRank = getPropertyValue(node, blackRank);
        if (!bRank.equals("")) {
            rg.getGameMetaInformation().setBlackRank(bRank);
        }

        String wRank = getPropertyValue(node, whiteRank);
        if (!wRank.equals("")) {
            rg.getGameMetaInformation().setWhiteRank(wRank);
        }

        String res = getPropertyValue(node, result);
        if (!res.equals("")) {
            // TODO check for correct result format
            rg.getGameMetaInformation().setResult(res);
        }

        String co = getPropertyValue(node, comment);
        if (co.equals("")) {
            co = null;
        }

        String bTurn = getPropertyValue(node, blacksMove);
        if (!bTurn.equals("")) {
            int[] position = {bTurn.charAt(0) - 'a' + 1, bTurn.charAt(1) - 'a' + 1};
            String bTime = getPropertyValue(node, blacksTimeLeft);
            if (!bTime.equals("")) {
                long timeLeft = Math.round(Float.valueOf(bTime));
                byte periodsLeft;
                String bTurnsLeft = getPropertyValue(node, blacksMovesLeft);
                if (!bTurnsLeft.equals("")) {
                    periodsLeft = Byte.parseByte(bTurnsLeft);
                } else {
                    // When a player is not in over time the ot periods = 1
                    periodsLeft = 1;
                }
                // When the position of the move exceeds the board size --> pass move
                if (position[0] > rg.getGameMetaInformation().getBoardSize()) {
                    newMoveNode = new MoveNode(1, true, position, currentMoveNode, timeLeft, periodsLeft, co);
                } else {
                    newMoveNode = new MoveNode(0, true, position, currentMoveNode, timeLeft, periodsLeft, co);
                }
            } else {
                // When the position of the move exceeds the board size --> pass move
                if (position[0] > rg.getGameMetaInformation().getBoardSize()) {
                    newMoveNode = new MoveNode(1, true, position, currentMoveNode, co);
                } else {
                    newMoveNode = new MoveNode(0, true, position, currentMoveNode, co);
                }
            }
        }
        else { // TODO is possible for a node to neither contain a black move nor a white move?
        //if (!wTurn.equals("")) { // TODO use this if upper comment is true
            String wTurn = getPropertyValue(node, whitesMove);
            int[] position = {wTurn.charAt(0) - 'a' + 1, wTurn.charAt(1) - 'a' + 1};
            String wTime = getPropertyValue(node, whitesTimeLeft);
            if (!wTime.equals("")) {
                long timeLeft = Math.round(Float.valueOf(wTime));
                byte periodsLeft;
                String wTurnsLeft = getPropertyValue(node, whitesMovesLeft);
                if (!wTurnsLeft.equals("")) {
                    periodsLeft = Byte.parseByte(wTurnsLeft);
                } else {
                    periodsLeft = 1;
                }
                // When the position of the move exceeds the board size --> pass move
                if (position[0] > rg.getGameMetaInformation().getBoardSize()) {
                    newMoveNode = new MoveNode(1, false, position, currentMoveNode, timeLeft, periodsLeft, co);
                } else {
                    newMoveNode = new MoveNode(0, false, position, currentMoveNode, timeLeft, periodsLeft, co);
                }
            } else {
                // When the position of the move exceeds the board size --> pass move
                if (position[0] > rg.getGameMetaInformation().getBoardSize()) {
                    newMoveNode = new MoveNode(1, false, position, currentMoveNode, co);
                } else {
                    newMoveNode = new MoveNode(0, false, position, currentMoveNode, co);
                }
            }
        }

        // TODO for action type resign: a MoveNode with this action type needs to be added to the end of the tree if the result contains a "+Res"

        rg.addIndexToMainTree(currentMoveNode.addChild(newMoveNode)); // append child to current node and add the index to the main tree structure
    }

    private String getPropertyValue(String node, Pattern pattern) {
        String retval = "";
        if (pattern.matcher(node).matches()) {
            Matcher matcher = pattern.matcher(node);
            matcher.find();
            retval = matcher.group(1);
        }
        return retval;
    }

    private String readFile(File sgfFile) {
        if (sgfFile.exists()) {
            // StringBuilder is not threadsafe!
            // TODO larger files will cause either fatal errors or strange behaviour, which will eventual lead to a fatal error while parsing
            StringBuilder content = new StringBuilder(2000); // TODO fixed, not configureable size, maybe change to buffer

            try {
                BufferedReader br = new BufferedReader(new FileReader(sgfFile));
                String line;

                while ((line = br.readLine()) != null) {
                    content.append(line);
                }
                return content.toString();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Could not open File");
            }
        } else {
            Log.w(LOG_TAG, "SGF file does not exist");
        }
        //TODO the null case needs to be caught upwards. Maybe use custom exception instead.
        return null;
    }

}
