package com.mc1.dev.goapp;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;


public class ActivityPlay extends AppCompatActivity {

    private boolean blackIsTurned; // if true, this signals that the black player is playing on the top (turned) side of the field
    private RunningGame game;
    private BoardView board;
    private AlertDialog.Builder dialogBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN );

        setContentView(R.layout.activity_play);

        final Intent intent = getIntent();
        game = (RunningGame) intent.getSerializableExtra("game");

        board = (BoardView) findViewById(R.id.mainBoardView);
        board.setBoardSize(game.getGameMetaInformation().getBoardSize());

        TextView turnedTimeView = (TextView) findViewById(R.id.playTimeViewTurned);
        TextView timeView = (TextView) findViewById(R.id.playTimeView);

        dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setCancelable(false);
        dialogBuilder.setPositiveButton("Play again", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Intent intentPlay = new Intent(getApplicationContext(), ActivityPlay.class);
                intentPlay.putExtra("game", new RunningGame(game.getGameMetaInformation()));
                startActivity(intentPlay);
            }
        });
        dialogBuilder.setNeutralButton("Review", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intentReview = new Intent(getApplicationContext(), ActivityRecordGame.class);
                intentReview.putExtra("game", game);
                startActivity(intentReview);
            }
        });
        dialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intentCancel = new Intent(getApplicationContext(), ActivityMain.class);
                startActivity(intentCancel);
            }
        });

        // TODO intialise name strings
        // TODO initialise blackIsTurned

        if (turnedTimeView != null && timeView != null) {
            // per default the time settings of each game equate to the following:
            // 10 seconds main time + 4 * 5 Seconds (Japanese overtime) or
            // 10 seconds main time + 4 Stones in 5 Seconds (Canadian overtime)
            byte sth = 4;
            TimeController.getInstance().configure(game.getGameMetaInformation().getTimeMode(), 10000, 5000, sth, 100, timeView, turnedTimeView, getResources().getString(R.string.label_time));
        }

    }

    public void onResume() {
        super.onResume();
    }

    public void onPause() {
        super.onPause();
    }

    public void onStop() {
        super.onStop();
    }

    public void onRestart() {
        super.onRestart();
    }

    // ----------------------------------------------------------------------
    // function resign()
    //
    // is called when the resign-Button is pressed
    // ----------------------------------------------------------------------
    public void resign(View view) {
        String content = "";
        String title = "";

        if (view.getId() == R.id.resignButtonTurned && blackIsTurned || view.getId() == R.id.resignButton && !blackIsTurned) { // if black won
            content = getString(R.string.end_black_1) + " " + game.getGameMetaInformation().getBlackPrisoners() + " " + getString(R.string.end_part_2);
            if (!game.getGameMetaInformation().getBlackName().equals("")) {
                title = game.getGameMetaInformation().getBlackName() + " " + getString(R.string.end_title);
            }
            else {
                title = getString(R.string.end_title_black);
            }

        }
        else {
            content = getString(R.string.end_white_1) + " " + game.getGameMetaInformation().getWhitePrisoners() + " " + getString(R.string.end_part_2);
            if (!game.getGameMetaInformation().getWhiteName().equals("")) {
                title = game.getGameMetaInformation().getWhiteName() + " " + getString(R.string.end_title);
            }
            else {
                title = getString(R.string.end_title_white);
            }
        }

        dialogBuilder.setMessage(content).setTitle(title);
        dialogBuilder.show();
    }

    // ----------------------------------------------------------------------
    // function passMove()
    //
    // is called when the pass-Button is pressed
    //
    // creates a new move with stone positions outside the board and the
    // action type set to "pass"
    // ----------------------------------------------------------------------
    public void passMove(View view) {

        // if the last played move is not the same as the player on the top (turned) side
            if (blackIsTurned != game.getCurrentNode().isBlacksMove()) {
                if (view.getId() != R.id.passButtonTurned) {
                    return; // eg: color is on top, color has to do the move, but the bottom button is pressed
                }
            // else go on and play the pass move
        }
        else { // if the last played move is the same as the player on the top (turned) side
            if (view.getId() == R.id.passButtonTurned) {
                return; // eg. color is on top, !color has to do the move, but the top button is pressed
            }
            // else go on an play the pass move
        }

        int[] position = {board.getBoardSize(), board.getBoardSize()};
        byte perLeft;

        if (game.getCurrentNode().isBlacksMove()) {
            perLeft = TimeController.getInstance().getBlackPeriodsLeft();
        } else {
            perLeft = TimeController.getInstance().getWhitePeriodsLeft();
        }


        // play the move with all attributes
        game.playMove(GameMetaInformation.actionType.PASS, position, /*TimeController.getInstance().swapTimePeriods(game.getCurrentNode().isBlacksMove()) */ 1, perLeft);

        board.refresh(game.getMainTreeIndices(), game);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int counter = 0;
            float x = event.getX();
            float y = event.getY();
            float lineOffset = board.getLineOffset();
            float points[] = board.getPoints();

            LinearLayout turnedActionBar = (LinearLayout) findViewById(R.id.actionBarTurned);
            if (turnedActionBar != null) {
                y = y - turnedActionBar.getHeight();
            }

            for (int i = 0; i < board.getBoardSize(); i++) {
                for (int j = 0; j < board.getBoardSize(); j++) {
                    if (pointDistance(x,y, points[counter], points[counter+1]) <= lineOffset/2) {
                        int position[] = {i,j}; // the index-position for the stone to be set
                        switch (GameController.getInstance().checkAction(GameMetaInformation.actionType.MOVE, game, position, !game.getCurrentNode().isBlacksMove() )) {
                            case OCCUPIED   :
                                return super.onTouchEvent(event);
                            case SUICIDE    :
                                dialogBuilder.setMessage(R.string.dialog_suicide_content).setTitle(R.string.dialog_suicide_title);
                                dialogBuilder.show();
                                return super.onTouchEvent(event);
                            case END:
                                endGame();
                                return super.onTouchEvent(event);
                        }


                        // time
                        byte perLeft;
                        if (game.getCurrentNode().isBlacksMove()) {
                            perLeft = TimeController.getInstance().getBlackPeriodsLeft();
                        } else {
                            perLeft = TimeController.getInstance().getWhitePeriodsLeft();
                        }


                        // play the move with all attributes
                        game.playMove(GameMetaInformation.actionType.MOVE, position, TimeController.getInstance().swapTimePeriods(game.getCurrentNode().isBlacksMove()), perLeft);

                        // remove all prisoners from the board
                        // call twice to check for white and black stones, if they are prisoner
                        GameController.getInstance().calcPrisoners(game, game.getCurrentNode().isBlacksMove());
                        GameController.getInstance().calcPrisoners(game, !game.getCurrentNode().isBlacksMove());
                        updatePrisonerViews();

                        board.refresh(game.getMainTreeIndices(), game);
                        return super.onTouchEvent(event);
                    }
                    else {
                        counter = counter + 2;
                    }
                }
            }
            return super.onTouchEvent(event);
        }
        // TODO if zoom
        return super.onTouchEvent(event);
    }

    private void updatePrisonerViews() {

        TextView prisonerViewTurned = (TextView) findViewById(R.id.playPrisonersViewTurned);
        TextView prisonerView = (TextView) findViewById(R.id.playPrisonersView);

        if (prisonerViewTurned != null && prisonerView != null) {
            String label = getResources().getString(R.string.label_prisoners);
            String blackContent = label + "\r\n" + game.getGameMetaInformation().getBlackPrisoners();
            String whiteContent = label + "\r\n" + game.getGameMetaInformation().getWhitePrisoners();

            if (blackIsTurned) {
                prisonerViewTurned.setText(blackContent);
                prisonerView.setText(whiteContent);
            }
            else {
                prisonerViewTurned.setText(whiteContent);
                prisonerView.setText(blackContent);
            }
        }
    }

    private void endGame() {

        // int[] points = GameController.calculateGameEnding();
        boolean blackWon = true;

        String content = "";
        String title = "";
        if (blackWon) {
            content = getString(R.string.end_black_1) + " " + game.getGameMetaInformation().getBlackPrisoners() + " " + getString(R.string.end_part_2);
            if (!game.getGameMetaInformation().getBlackName().equals("")) {
                title = game.getGameMetaInformation().getBlackName() + " " + getString(R.string.end_title);
            } else {
                title = getString(R.string.end_title_black);
            }

        } else {
            content = getString(R.string.end_white_1) + " " + game.getGameMetaInformation().getWhitePrisoners() + " " + getString(R.string.end_part_2);
            if (!game.getGameMetaInformation().getWhiteName().equals("")) {
                title = game.getGameMetaInformation().getWhiteName() + " " + getString(R.string.end_title);
            } else {
                title = getString(R.string.end_title_white);
            }
        }

        dialogBuilder.setMessage(content).setTitle(title);
        dialogBuilder.show();
    }

    private float pointDistance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt(Math.pow((x1 - x2),2) + Math.pow((y1 - y2),2) );
    }
}
