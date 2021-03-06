package com.mc1.dev.goapp;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;


public class ActivityRecordGame extends AppCompatActivity {
    private static final String LOG_TAG = ActivityMain.class.getSimpleName();
    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 730;

    // the main tree indices for the current game to be recorded
    private ArrayList<Integer> indices;
    // represents the current game state, when the user for example pressed the backwards button
    private ArrayList<Integer> currentGameState;
    private RunningGame game;
    private BoardView board;
    private AlertDialog.Builder dialogBuilder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Disable the default title as a customized one is used
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN );

        setContentView(R.layout.activity_record_game);

        Intent intent = getIntent();
        game = (RunningGame) intent.getSerializableExtra("game");
        // if the game is a new game the main tree indices are automatically stored
        // otherwise this is equivalent to assigning an empty ArrayList to indices.
        indices = game.getMainTreeIndices();

        currentGameState = new ArrayList<>();

        board = (BoardView) findViewById(R.id.recordBoardView);
        board.setBoardSize(game.getGameMetaInformation().getBoardSize());

        // Alert dialog in case of an attempted suicide move.
        dialogBuilder = new AlertDialog.Builder(this);
        dialogBuilder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
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
                    if (pointDistance(x, y, points[counter], points[counter + 1]) <= lineOffset / 2) {
                        int position[] = {i, j}; // the index-position for the stone to be set
                        switch (GameController.getInstance().checkAction(GameMetaInformation.actionType.MOVE, game, position, !game.getCurrentNode().isBlacksMove())) {
                            case OCCUPIED:
                                return super.onTouchEvent(event);
                            case SUICIDE:
                                dialogBuilder.setMessage(R.string.dialog_suicide_content).setTitle(R.string.dialog_suicide_title);
                                dialogBuilder.show();
                                return super.onTouchEvent(event);
                        }

                        // play the move with all attributes
                        indices.add(game.recordMove(GameMetaInformation.actionType.MOVE, position, indices));

                        // remove all prisoners from the board
                        // ! currentNode now has the color of the move played, e.g. a black stone was set, check if
                        // there are prisoners on white side
                        GameController.getInstance().calcPrisoners(game, game.getCurrentNode().isBlacksMove());
                        updatePrisonerViews();

                        board.refresh(indices, game);
                        return super.onTouchEvent(event);
                    } else {
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

        TextView blackPrisonerView = (TextView) findViewById(R.id.blackPrisonersView);
        TextView whitePrisonerView = (TextView) findViewById(R.id.whitePrisonersView);

        if (blackPrisonerView != null && whitePrisonerView != null) {
            String labelBlack = getResources().getString(R.string.label_prisoners_black);
            String labelWhite = getResources().getString(R.string.label_prisoners_white);
            String blackContent = labelBlack + "\r\n" + game.getGameMetaInformation().getBlackPrisoners();
            String whiteContent = labelWhite + "\r\n" + game.getGameMetaInformation().getWhitePrisoners();

            blackPrisonerView.setText(blackContent);
            whitePrisonerView.setText(whiteContent);
        }
    }

    // ----------------------------------------------------------------------
    // function save(View view)
    //
    // is called when the save-Button is clicked
    //
    // saves the current game status to a sgf file
    // ----------------------------------------------------------------------
    public void save(View view) {

        // before saving the permission to write to the external storage needs to be present.
        String perm = "android.permission.WRITE_EXTERNAL_STORAGE";
        int res = getApplicationContext().checkCallingOrSelfPermission(perm);
        if (res != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
            Log.i(LOG_TAG, "Permission for writing to external storage requested");
        } else {
            SGFParser sgfParser = new SGFParser();
            try {
                // files are always saved with the current date as file name
                // the parser's save function checks whether there has already been a file saved
                // on the current date and appends a postfix correspondingly.
                Calendar c = Calendar.getInstance();
                SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH);
                String todaysDate[] = new String[]{df.format(c.getTime())};
                game.getGameMetaInformation().setDates(todaysDate);
                String fName = sgfParser.save(game, game.getGameMetaInformation().getDates()[0]);

                // a popup dialog is shown in case of success.
                String saveDialog = getString(R.string.dialog_on_save_completed) + " " + fName;
                dialogBuilder.setMessage(saveDialog).setTitle(R.string.title_successful_save);
                dialogBuilder.show();
            } catch (IOException e) {
                dialogBuilder.setMessage(e.getMessage()).setTitle(R.string.title_error_message);
                dialogBuilder.show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // if the permission has been granted
                    save(this.getCurrentFocus());
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // creates a new move with stone positions outside the board and the
    // action type set to "pass"
    // ----------------------------------------------------------------------
    public void passMove(View view) {

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

    // ----------------------------------------------------------------------
    // skips to the next move in the main line of play if there is one
    // gets called on button click
    // ----------------------------------------------------------------------
    public void skipForward(View view) {
        if (indices.size() > currentGameState.size()) {
            currentGameState.add(0);
        }

        GameController.getInstance().calcPrisoners(game, game.getCurrentNode().isBlacksMove());
        updatePrisonerViews();

        board.refresh(currentGameState, game);
    }

    // ----------------------------------------------------------------------
    // skips to the last move in the main line of play if there is one
    // gets called on button click
    // ----------------------------------------------------------------------
    public void skipBackward(View view) {
        if (currentGameState.size() > 0) {
            currentGameState.remove(currentGameState.size() - 1);
        }

        GameController.getInstance().calcPrisoners(game, game.getCurrentNode().isBlacksMove());
        updatePrisonerViews();

        board.refresh(currentGameState, game);
    }

    private float pointDistance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt(Math.pow((x1 - x2), 2) + Math.pow((y1 - y2), 2));
    }
}
