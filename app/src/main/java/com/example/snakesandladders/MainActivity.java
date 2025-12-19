package com.example.snakesandladders;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.drawable.DrawableCompat;

public class MainActivity extends AppCompatActivity {

    private TextView[] squares;
    private final int total = 60;
    private TextView[] playerTokens;
    private int[] playerPositions;
    private int lastMovedPlayer = 0;
    private ViewGroup boardContainer;
    private Button rollButton;
    private TextView diceResult;
    private final Map<Integer, Integer> snakes = new HashMap<>();
    private final Map<Integer, Integer> ladders = new HashMap<>();
    private LinesOverlay linesOverlay;
    private boolean isResetBlocked = false;
    private boolean isVsComputer = true;
    private int currentPlayerTurn = 1;
    private View menuOverlay;
    private View gameGroup;
    private final Handler gameHandler = new Handler(Looper.getMainLooper());

    // Called when the activity is first created.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        menuOverlay = findViewById(R.id.menuOverlay);
        gameGroup = findViewById(R.id.gameGroup);
        Button btnVsComputer = findViewById(R.id.btnVsComputer);
        Button btnPlayFriend = findViewById(R.id.btnPlayFriend);
        Button btnInfo = findViewById(R.id.btnInfo);

        btnVsComputer.setOnClickListener(v -> startGame(true));
        btnPlayFriend.setOnClickListener(v -> startGame(false));
        btnInfo.setOnClickListener(v -> showInfoDialog());

        boardContainer = findViewById(R.id.main);
        linesOverlay = new LinesOverlay(this);
        linesOverlay.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5f, getResources().getDisplayMetrics()));
        boardContainer.addView(linesOverlay, 1, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rollButton = findViewById(R.id.rollButton);
        rollButton.setOnClickListener(v -> rollDice());

        diceResult = findViewById(R.id.diceResult);

        Button resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(v -> {
            if (isResetBlocked) return;

            gameHandler.removeCallbacksAndMessages(null);
            if (playerTokens != null) {
                if (playerTokens[1] != null) playerTokens[1].animate().cancel();
                if (playerTokens[2] != null) playerTokens[2].animate().cancel();
            }

            menuOverlay.setVisibility(View.VISIBLE);
            gameGroup.setVisibility(View.INVISIBLE);

            isResetBlocked = true;
            resetButton.setEnabled(false);
            gameHandler.postDelayed(() -> {
                isResetBlocked = false;
                resetButton.setEnabled(true);
            }, 3000);
        });
        
        if (squares == null) {
            setupBoard();
        }
    }

    // Shows an information dialog with game rules.
    private void showInfoDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.game_information_title)
            .setMessage(R.string.game_information_message)
            .setPositiveButton(R.string.ok, null)
            .show();
    }

    // Configures and starts a new game session.
    private void startGame(boolean vsComputer) {
        this.isVsComputer = vsComputer;
        this.currentPlayerTurn = 1;

        menuOverlay.setVisibility(View.GONE);
        gameGroup.setVisibility(View.VISIBLE);

        placePlayer(1, 1);
        placePlayer(2, 1);
        lastMovedPlayer = 0;
        rollButton.setEnabled(true);
        diceResult.setText("");
        generateBoardFeatures(); 
    }

    // Creates the grid of squares for the game board.
    private void setupBoard() {
        GridLayout grid = findViewById(R.id.gameGrid);
        grid.setColumnCount(6);
        grid.setRowCount(10);
        grid.removeAllViews();

        squares = new TextView[total + 1];

        final int cellDp = 58;
        final int marginDp = 3;
        int cellPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, cellDp, getResources().getDisplayMetrics());
        int marginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, marginDp, getResources().getDisplayMetrics());

        for (int i = 0; i < total; i++) {
            int number = i + 1;
            int rowIndex = 9 - (i / 6);
            int colIndex = i % 6;

            TextView tv = new TextView(this);
            tv.setText(String.valueOf(number));
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            tv.setTextColor(Color.BLACK);

            GradientDrawable cellBg = new GradientDrawable();
            if (number == total) {
                cellBg.setColor(Color.parseColor("#FFD700"));
            } else {
                cellBg.setColor(Color.WHITE);
            }
            int strokePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, getResources().getDisplayMetrics());
            cellBg.setStroke(strokePx, Color.BLACK);
            cellBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, getResources().getDisplayMetrics()));
            tv.setBackground(cellBg);
            tv.setElevation(0f); 

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(rowIndex), GridLayout.spec(colIndex));
            lp.width = cellPx;
            lp.height = cellPx;
            lp.setMargins(marginPx, marginPx, marginPx, marginPx);
            tv.setLayoutParams(lp);

            grid.addView(tv);
            squares[number] = tv;
        }
        createPlayers(cellPx);
    }

    // Randomly generates snakes and ladders on the board.
    private void generateBoardFeatures() {
        for (int i = 1; i <= total; i++) {
            TextView square = getSquare(i);
            if (square != null) {
                GradientDrawable bg = (GradientDrawable) square.getBackground();
                if (i == total) {
                    bg.setColor(Color.parseColor("#FFD700"));
                } else {
                    bg.setColor(Color.WHITE);
                }
            }
        }

        snakes.clear();
        ladders.clear();

        Random rand = new Random();
        Set<Integer> usedSquares = new HashSet<>();

        final int lightGreen = Color.parseColor("#BFBBF2C6");
        final int lightBlue = Color.parseColor("#6600008B");
        final int lightRed = Color.parseColor("#BFF8C6CF");

        int targetLadders = 2 + rand.nextInt(4);
        for (int i = 0; i < targetLadders; i++) {
            int start, end;
            do {
                start = rand.nextInt(total - 10) + 2;
                end = start + rand.nextInt(13) + 6;
            } while (end >= total || usedSquares.contains(start) || usedSquares.contains(end));
            ladders.put(start, end);
            usedSquares.add(start);
            usedSquares.add(end);
            highlightSquare(start, lightGreen);
            highlightSquare(end, lightBlue);
        }

        int targetSnakes = 2 + rand.nextInt(4);
        for (int i = 0; i < targetSnakes; i++) {
            int start, end;
            do {
                start = rand.nextInt(total - 10) + 10;
                end = start - (rand.nextInt(13) + 6);
            } while (end <= 1 || usedSquares.contains(start) || usedSquares.contains(end));
            snakes.put(start, end);
            usedSquares.add(start);
            usedSquares.add(end);
            highlightSquare(start, lightRed);
            highlightSquare(end, lightBlue);
        }

        List<LinesOverlay.BoardItem> items = new ArrayList<>();
        int[] ladderResIds = {R.drawable.ladder_brown, R.drawable.ladder_red, R.drawable.ladder_green, R.drawable.ladder_yellow};
        for (Map.Entry<Integer, Integer> e : ladders.entrySet()) {
            items.add(new LinesOverlay.BoardItem(getSquare(e.getKey()), getSquare(e.getValue()), ladderResIds[rand.nextInt(ladderResIds.length)]));
        }

        int[] snakeResIds = {R.drawable.snake_green, R.drawable.snake_pink, R.drawable.snake_yellow, R.drawable.snake_purple};
        for (Map.Entry<Integer, Integer> e : snakes.entrySet()) {
            items.add(new LinesOverlay.BoardItem(getSquare(e.getKey()), getSquare(e.getValue()), snakeResIds[rand.nextInt(snakeResIds.length)]));
        }

        linesOverlay.setItems(items);
        linesOverlay.invalidate();
    }

    // Sets the background color of a specific square.
    private void highlightSquare(int number, int color) {
        TextView square = getSquare(number);
        if (square != null) {
            GradientDrawable bg = (GradientDrawable) square.getBackground();
            bg.setColor(color);
        }
    }

    // Returns the TextView for a given square number.
    private TextView getSquare(int number) {
        if (number < 1 || number > total) return null;
        return squares[number];
    }

    // Creates the player tokens and adds them to the board.
    private void createPlayers(int cellPx) {
        playerTokens = new TextView[3];
        playerPositions = new int[3];

        final int tokenSize = (int) (cellPx * 0.45f);

        playerTokens[1] = createTokenView(Color.BLUE, tokenSize);
        playerTokens[1].setTag("player1");
        boardContainer.addView(playerTokens[1], new ViewGroup.LayoutParams(tokenSize, tokenSize));

        playerTokens[2] = createTokenView(Color.parseColor("#FF4081"), tokenSize);
        playerTokens[2].setTag("player2");
        boardContainer.addView(playerTokens[2], new ViewGroup.LayoutParams(tokenSize, tokenSize));

        playerPositions[1] = 0;
        playerPositions[2] = 0;
    }

    // Creates a single player token view with a specific color.
    private TextView createTokenView(int color, int sizePx) {
        TextView token = new TextView(this);
        token.setWidth(sizePx);
        token.setHeight(sizePx);

        Drawable d = ContextCompat.getDrawable(this, R.drawable.ic_stick_figure);
        d = DrawableCompat.wrap(d.mutate());
        DrawableCompat.setTint(d, color);
        token.setBackground(d);
        token.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, getResources().getDisplayMetrics()));
        return token;
    }

    // Calculates the screen coordinates for a token on a given square.
    @Nullable
    private float[] getTokenCoordinates(int playerId, int squareNumber) {
        TextView square = getSquare(squareNumber);
        if (square == null) return null;

        int[] parentLoc = new int[2];
        boardContainer.getLocationOnScreen(parentLoc);
        int[] sqLoc = new int[2];
        square.getLocationOnScreen(sqLoc);

        int squareX = sqLoc[0] - parentLoc[0];
        int squareY = sqLoc[1] - parentLoc[1];
        int squareW = square.getWidth();
        int squareH = square.getHeight();

        View token = playerTokens[playerId];
        int tokenW = token.getWidth();
        int tokenH = token.getHeight();
        if (tokenW == 0) tokenW = (int) (squareW * 0.45f);
        if (tokenH == 0) tokenH = tokenW;

        float centerX = squareX + (squareW - tokenW) / 2f;
        float centerY = squareY + (squareH - tokenH) / 2f;
        float separation = tokenW * 0.18f;

        float tx = (playerId == 1) ? centerX - separation : centerX + separation;
        float ty = (playerId == 1) ? centerY - separation : centerY + separation;
        
        return new float[]{tx, ty};
    }

    // Instantly places a player token on a specific square.
    private void placePlayer(int playerId, int squareNumber) {
        if (playerId < 1 || playerId > 2) return;
        if (squareNumber < 1 || squareNumber > total) return;

        float[] coords = getTokenCoordinates(playerId, squareNumber);
        if (coords == null) return;

        View token = playerTokens[playerId];
        token.setX(coords[0]);
        token.setY(coords[1]);

        playerPositions[playerId] = squareNumber;
        token.bringToFront();
    }

    // Animates a player token sliding to a target square.
    private void animateSlide(int playerId, int targetSquare, Runnable onComplete) {
        float[] coords = getTokenCoordinates(playerId, targetSquare);
        if (coords == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        View token = playerTokens[playerId];
        token.animate()
             .x(coords[0])
             .y(coords[1])
             .setDuration(1200)
             .setInterpolator(new AccelerateDecelerateInterpolator())
             .withEndAction(() -> {
                placePlayer(playerId, targetSquare);
                if (onComplete != null) onComplete.run();
             })
             .start();
    }

    // Moves a player and checks for snakes or ladders.
    private void movePlayerAndCheck(int playerId, int steps, Runnable onComplete) {
        animateSteps(playerId, steps, () -> {
            int currentPos = getPlayerPosition(playerId);
            Integer ladderDest = ladders.get(currentPos);
            Integer snakeDest = snakes.get(currentPos);
            
            Runnable slideAction = null;
            if (ladderDest != null) {
                slideAction = () -> animateSlide(playerId, ladderDest, onComplete);
            } else if (snakeDest != null) {
                slideAction = () -> animateSlide(playerId, snakeDest, onComplete);
            }

            if (slideAction != null) {
                gameHandler.postDelayed(slideAction, 500);
            } else {
                lastMovedPlayer = playerId;
                if (onComplete != null) onComplete.run();
            }
        });
    }

    // Animates a player moving one square at a time.
    private void animateSteps(int playerId, int stepsRemaining, Runnable onFinished) {
        if (stepsRemaining <= 0) {
            if (onFinished != null) onFinished.run();
            return;
        }

        int current = getPlayerPosition(playerId);
        if (current >= total) {
             if (onFinished != null) onFinished.run();
             return;
        }

        placePlayer(playerId, current + 1);

        gameHandler.postDelayed(() -> animateSteps(playerId, stepsRemaining - 1, onFinished), 400);
    }

    // Returns the current square number of a player.
    private int getPlayerPosition(int playerId) {
        if (playerId < 1 || playerId > 2) return 0;
        return playerPositions[playerId];
    }

    // Handles the dice roll logic for the current player.
    public void rollDice() {
        rollButton.setEnabled(false);

        Random rand = new Random();
        int steps = rand.nextInt(6) + 1;

        if (diceResult != null) {
            String rollText = isVsComputer ? 
                getString(R.string.player_rolled, steps) :
                (currentPlayerTurn == 1 ? getString(R.string.player_1_rolled, steps) : getString(R.string.player_2_rolled, steps));
            diceResult.setText(rollText);
        }

        gameHandler.postDelayed(() -> movePlayerAndCheck(currentPlayerTurn, steps, () -> {
            if (checkIfWin()) return;
            
            if (isVsComputer) {
                startComputerTurn();
            } else {
                currentPlayerTurn = (currentPlayerTurn == 1) ? 2 : 1;
                gameHandler.postDelayed(() -> rollButton.setEnabled(true), 1000);
            }
        }), 300);
    }

    // Manages the computer's turn.
    private void startComputerTurn() {
        gameHandler.postDelayed(() -> {
            int steps = new Random().nextInt(6) + 1;
            diceResult.setText(getString(R.string.computer_rolled, steps));
            movePlayerAndCheck(2, steps, () -> {
                if (!checkIfWin()) {
                    rollButton.setEnabled(true);
                }
            });
        }, 1500);
    }

    // Checks if the last moved player has won the game.
    public boolean checkIfWin() {
        if (lastMovedPlayer == 0) return false;
        if (getPlayerPosition(lastMovedPlayer) == total) {
            String msg = isVsComputer ? 
                (lastMovedPlayer == 1 ? getString(R.string.player_won) : getString(R.string.computer_won)) :
                (lastMovedPlayer == 1 ? getString(R.string.player_1_won) : getString(R.string.player_2_won));
            
            Drawable winnerIcon = ContextCompat.getDrawable(this, R.drawable.ic_winner);
            
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.winner_title)
                .setMessage(msg)
                .setIcon(winnerIcon)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    menuOverlay.setVisibility(View.VISIBLE);
                    gameGroup.setVisibility(View.INVISIBLE);
                })
                .show();
            return true;
        }
        return false;
    }

    // Custom view to draw snakes and ladders.
    private static class LinesOverlay extends View {
        static class BoardItem {
            final float sx, sy, ex, ey;
            final int resId;
            BoardItem(View startV, View endV, int resId) {
                int[] parentLoc = new int[2];
                ((View)startV.getParent().getParent()).getLocationOnScreen(parentLoc);

                int[] sLoc = new int[2];
                startV.getLocationOnScreen(sLoc);
                int[] eLoc = new int[2];
                endV.getLocationOnScreen(eLoc);

                this.sx = (sLoc[0] - parentLoc[0]) + startV.getWidth() / 2f;
                this.sy = (sLoc[1] - parentLoc[1]) + startV.getHeight() / 2f;
                this.ex = (eLoc[0] - parentLoc[0]) + endV.getWidth() / 2f;
                this.ey = (eLoc[1] - parentLoc[1]) + endV.getHeight() / 2f;
                this.resId = resId;
            }
        }

        private List<BoardItem> items = new ArrayList<>();

        public LinesOverlay(MainActivity ctx) {
            super(ctx);
        }

        public void setItems(List<BoardItem> items) {
            this.items = items != null ? items : new ArrayList<>();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            for (BoardItem item : items) {
                Drawable d = ContextCompat.getDrawable(getContext(), item.resId);
                if (d == null) continue;

                float dx = item.ex - item.sx;
                float dy = item.ey - item.sy;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float angle = (float) Math.toDegrees(Math.atan2(dy, dx)) - 90;

                int w = d.getIntrinsicWidth();
                
                canvas.save();
                canvas.translate(item.sx, item.sy);
                canvas.rotate(angle);
                d.setBounds(-w / 2, 0, w / 2, (int) distance);
                d.draw(canvas);
                canvas.restore();
            }
        }
    }
}
