package com.example.snakesandladders;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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

    // Manageable board state
    private TextView[] squares;
    private final int total = 60;

    // New: player tokens
    private TextView[] playerTokens;    // 1-based indexing: playerTokens[1], playerTokens[2]
    private int[] playerPositions;      // current square number for each player (1..total)

    // Turn tracker: remove nextPlayerId; use lastMovedPlayer to know who moved last
    // Last moved player id (0 = none, 1 or 2)
    private int lastMovedPlayer = 0;

    // parent container (ConstraintLayout) used to position tokens with absolute X/Y
    private ViewGroup boardContainer;

    // Button to roll dice
    private Button rollButton;
    // TextView to show dice result
    private TextView diceResult;

    // Snakes and Ladders maps: startSquare -> endSquare
    private final Map<Integer, Integer> snakes = new HashMap<>();
    private final Map<Integer, Integer> ladders = new HashMap<>();

    // Overlay for drawing snake/ladder lines
    private LinesOverlay linesOverlay;

    // To prevent rapid resets
    private boolean isResetBlocked = false;

    // Game Mode
    private boolean isVsComputer = true;
    private int currentPlayerTurn = 1; // 1 or 2

    // Menu Views
    private View menuOverlay;

    // Global handler for game loop to allow cancellation
    private final Handler gameHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Apply window insets padding to the root view (root id "main" in XML)
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Menu
        menuOverlay = findViewById(R.id.menuOverlay);
        Button btnVsComputer = findViewById(R.id.btnVsComputer);
        Button btnPlayFriend = findViewById(R.id.btnPlayFriend);

        btnVsComputer.setOnClickListener(v -> startGame(true));
        btnPlayFriend.setOnClickListener(v -> startGame(false));

        int cols = 6;
        int rows = 10;

        // Programmatically populate the GridLayout with 60 equally sized squares
        GridLayout grid = findViewById(R.id.gameGrid);
        grid.setColumnCount(cols); // Update column count in code
        grid.setRowCount(rows);    // Update row count in code
        grid.removeAllViews();

        // store parent container (ConstraintLayout) to position tokens with setX/setY
        boardContainer = (ViewGroup) grid.getParent();

        // Add overlay to boardContainer so it is above the grid (thus lines draw over squares)
        // Insert it immediately after the grid child, so buttons (defined after grid) remain on top
        linesOverlay = new LinesOverlay(this);
        int gridIndex = boardContainer.indexOfChild(grid);
        int insertIndex = Math.max(0, gridIndex + 1);
        boardContainer.addView(linesOverlay, insertIndex, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        linesOverlay.setWillNotDraw(false);
        linesOverlay.setClickable(false);
        linesOverlay.setFocusable(false);
        // Ensure lines draw above squares (elevation > 2dp)
        linesOverlay.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, getResources().getDisplayMetrics()));

        squares = new TextView[total + 1]; // 1-based indexing for convenience

        // Update cell size to fit 6 columns - slightly smaller than before
        final int cellDp = 58;
        final int marginDp = 3;
        int cellPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, cellDp, getResources().getDisplayMetrics());
        int marginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, marginDp, getResources().getDisplayMetrics());

        for (int i = 0; i < total; i++) {
            int number = i + 1;
            // Compute row index so numbering goes bottom-to-top
            int rowIndex = rows - 1 - (i / cols); // 0 = top row, rows-1 = bottom row
            int colIndex = i % cols;

            TextView tv = new TextView(this);
            tv.setText(String.valueOf(number));
            tv.setGravity(Gravity.CENTER);
            // make numbers slightly smaller to fit
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            tv.setTextColor(Color.BLACK);

            // CHANGED: Use a thicker black stroke for better visibility
            GradientDrawable cellBg = new GradientDrawable();
            cellBg.setColor(Color.WHITE);
            // Increase stroke width to 3dp and make it BLACK
            int strokePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, getResources().getDisplayMetrics());
            cellBg.setStroke(strokePx, Color.BLACK);
            cellBg.setCornerRadius(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, getResources().getDisplayMetrics()));
            tv.setBackground(cellBg);
            tv.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, getResources().getDisplayMetrics()));

            tv.setId(View.generateViewId());
            tv.setTag(number); // store number as tag for convenience

            // layout params for GridLayout cell
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(rowIndex), GridLayout.spec(colIndex));
            lp.width = cellPx;
            lp.height = cellPx;
            lp.setMargins(marginPx, marginPx, marginPx, marginPx);
            tv.setLayoutParams(lp);

            grid.addView(tv);

            // store reference so squares can be managed later
            squares[number] = tv;
        }

        // Initialize players (tokens will be added to parent container)
        createPlayers(cellPx);

        diceResult = findViewById(R.id.diceResult);
        rollButton = findViewById(R.id.rollButton);
        rollButton.setOnClickListener(v -> rollDice());

        // Generate snakes and ladders and place players initially after layout so we can compute coordinates
        grid.post(() -> {
            generateBoardFeatures();
            placePlayer(1, 1); // player 1 -> square 1 (blue)
            placePlayer(2, 1); // player 2 -> square 1 (pink)
        });

        // wire the reset button
        Button resetButton = findViewById(R.id.resetButton);
        resetButton.setOnClickListener(v -> {
            if (isResetBlocked) return; // Prevent clicking if blocked

            // Stop all pending game events and animations
            gameHandler.removeCallbacksAndMessages(null);
            if (playerTokens != null) {
                if (playerTokens[1] != null) playerTokens[1].animate().cancel();
                if (playerTokens[2] != null) playerTokens[2].animate().cancel();
            }

            // Reset logic: show menu again
            menuOverlay.setVisibility(View.VISIBLE);

            // Block reset button
            isResetBlocked = true;
            resetButton.setEnabled(false); // Visually disable it
            gameHandler.postDelayed(() -> {
                isResetBlocked = false;
                resetButton.setEnabled(true);
            }, 3000);
        });
    }

    private void startGame(boolean vsComputer) {
        this.isVsComputer = vsComputer;
        this.currentPlayerTurn = 1;

        // Hide menu
        menuOverlay.setVisibility(View.GONE);

        // Reset board
        placePlayer(1, 1);
        placePlayer(2, 1);
        lastMovedPlayer = 0;
        if (rollButton != null) rollButton.setEnabled(true);
        if (diceResult != null) diceResult.setText("");
        generateBoardFeatures();
    }

    // Helper: Generate snakes and ladders randomly and set overlay lines
    private void generateBoardFeatures() {
        // Clear existing highlights and maps
        for (int i = 1; i <= total; i++) {
            clearHighlight(i);
        }
        snakes.clear();
        ladders.clear();

        Random rand = new Random();
        Set<Integer> usedSquares = new HashSet<>();

        // color: dark blue with ~25% alpha
        final int darkBlue25 = Color.parseColor("#4000008B"); // AARRGGBB -> 0x40 alpha, 0x00008B darkblue

        // CHANGED: choose random count between 2 and 5 inclusive
        int targetLadders = 2 + rand.nextInt(4); // 2..5
        int laddersCount = 0;
        int attempts = 0;
        while (laddersCount < targetLadders && attempts < 1000) {
            attempts++;
            int start = rand.nextInt(total - 10) + 2;
            int jump = rand.nextInt(13) + 6; // 6..18
            int end = start + jump;

            if (end < total && !usedSquares.contains(start) && !usedSquares.contains(end)) {
                ladders.put(start, end);
                usedSquares.add(start);
                usedSquares.add(end);

                highlightSquare(start, Color.parseColor("#BBF2C6")); // light green
                // UPDATED: highlight destination square with dark blue 25% opacity
                highlightSquare(end, darkBlue25);
                laddersCount++;
            }
        }

        // CHANGED: choose random count between 2 and 5 inclusive
        int targetSnakes = 2 + rand.nextInt(4); // 2..5
        int snakesCount = 0;
        attempts = 0;
        while (snakesCount < targetSnakes && attempts < 1000) {
            attempts++;
            int start = rand.nextInt(total - 10) + 10;
            int drop = rand.nextInt(13) + 6; // 6..18
            int end = start - drop;

            if (end > 1 && !usedSquares.contains(start) && !usedSquares.contains(end)) {
                snakes.put(start, end);
                usedSquares.add(start);
                usedSquares.add(end);

                highlightSquare(start, Color.parseColor("#F8C6CF")); // light red/pink
                // UPDATED: highlight destination square with dark blue 25% opacity
                highlightSquare(end, darkBlue25);
                snakesCount++;
            }
        }

        // Build line representations using view coordinates
        List<LinesOverlay.BoardItem> items = new ArrayList<>();
        int[] parentLoc = new int[2];
        boardContainer.getLocationOnScreen(parentLoc);

        // Randomly pick ladder images
        int[] ladderResIds = {R.drawable.ladder_brown, R.drawable.ladder_red, R.drawable.ladder_green, R.drawable.ladder_yellow};

        for (Map.Entry<Integer, Integer> e : ladders.entrySet()) {
            TextView startV = getSquare(e.getKey());
            TextView endV = getSquare(e.getValue());
            if (startV == null || endV == null) continue;
            int[] sLoc = new int[2];
            int[] tLoc = new int[2];
            startV.getLocationOnScreen(sLoc);
            endV.getLocationOnScreen(tLoc);

            float sLeft = sLoc[0] - parentLoc[0];
            float sTop = sLoc[1] - parentLoc[1];
            float eLeft = tLoc[0] - parentLoc[0];
            float eTop = tLoc[1] - parentLoc[1];

            float sx = sLeft + startV.getWidth() / 2f;
            float sy = sTop + startV.getHeight() / 2f;
            float ex = eLeft + endV.getWidth() / 2f;
            float ey = eTop + endV.getHeight() / 2f;

            int resId = ladderResIds[rand.nextInt(ladderResIds.length)];
            items.add(new LinesOverlay.BoardItem(sx, sy, ex, ey, resId));
        }

        // Randomly pick snake images
        int[] snakeResIds = {R.drawable.snake_green, R.drawable.snake_pink, R.drawable.snake_yellow, R.drawable.snake_purple};

        for (Map.Entry<Integer, Integer> e : snakes.entrySet()) {
            TextView startV = getSquare(e.getKey());
            TextView endV = getSquare(e.getValue());
            if (startV == null || endV == null) continue;
            int[] sLoc = new int[2];
            int[] tLoc = new int[2];
            startV.getLocationOnScreen(sLoc);
            endV.getLocationOnScreen(tLoc);

            float sLeft = sLoc[0] - parentLoc[0];
            float sTop = sLoc[1] - parentLoc[1];
            float eLeft = tLoc[0] - parentLoc[0];
            float eTop = tLoc[1] - parentLoc[1];

            float sx = sLeft + startV.getWidth() / 2f;
            float sy = sTop + startV.getHeight() / 2f;
            float ex = eLeft + endV.getWidth() / 2f;
            float ey = eTop + endV.getHeight() / 2f;

            int resId = snakeResIds[rand.nextInt(snakeResIds.length)];
            items.add(new LinesOverlay.BoardItem(sx, sy, ex, ey, resId));
        }

        // Update overlay
        if (linesOverlay != null) {
            linesOverlay.setItems(items);
            linesOverlay.invalidate();
        }
    }

    // Helper: get a square TextView by its board number
    private TextView getSquare(int number) {
        if (number < 1 || number > total) return null;
        return squares[number];
    }

    // Helper: highlight a square with a color (sets background)
    public void highlightSquare(int number, int color) {
        TextView tv = getSquare(number);
        if (tv == null) return;
        GradientDrawable bg = (GradientDrawable) tv.getBackground();
        bg.setColor(color);
        // Ensure stroke remains black even when color changes
        int strokePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, getResources().getDisplayMetrics());
        bg.setStroke(strokePx, Color.BLACK);
    }

    // Helper: clear highlight (reset to default background color)
    public void clearHighlight(int number) {
        TextView tv = getSquare(number);
        if (tv == null) return;
        GradientDrawable bg = (GradientDrawable) tv.getBackground();
        bg.setColor(Color.WHITE);
        // Ensure stroke remains black
        int strokePx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, getResources().getDisplayMetrics());
        bg.setStroke(strokePx, Color.BLACK);
    }

    // --- Player token management ---

    // Create two player token Views and add them to the parent container (they will be positioned later)
    private void createPlayers(int cellPx) {
        playerTokens = new TextView[3];    // index 1 and 2 used
        playerPositions = new int[3];

        // Make tokens smaller (45% of cell)
        final int tokenSize = (int) (cellPx * 0.45f);

        // Player 1 (blue)
        playerTokens[1] = createTokenView(Color.BLUE, tokenSize);
        playerTokens[1].setTag("player1");
        boardContainer.addView(playerTokens[1], new ViewGroup.LayoutParams(tokenSize, tokenSize));

        // Player 2 (pink)
        playerTokens[2] = createTokenView(Color.parseColor("#FF4081"), tokenSize); // pink-ish
        playerTokens[2].setTag("player2");
        boardContainer.addView(playerTokens[2], new ViewGroup.LayoutParams(tokenSize, tokenSize));

        // initialize positions to 0 (not on board yet)
        playerPositions[1] = 0;
        playerPositions[2] = 0;
    }

    // Helper to create a circular token TextView with given color
    private TextView createTokenView(int color, int sizePx) {
        TextView token = new TextView(this);
        token.setText(""); // no text by default
        token.setGravity(Gravity.CENTER);
        token.setWidth(sizePx);
        token.setHeight(sizePx);

        // Load the stick figure drawable
        Drawable d = ContextCompat.getDrawable(this, R.drawable.ic_stick_figure);
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
            DrawableCompat.setTint(d, color);
            token.setBackground(d);
        }

        // Ensure tokens draw above lines and squares
        token.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, getResources().getDisplayMetrics()));
        return token;
    }

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

    // Place a player token on a given square number (1..total)
    public void placePlayer(int playerId, int squareNumber) {
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

    // Animate a player token sliding from its current spot to a target square
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
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        placePlayer(playerId, targetSquare); // Snap to final position
                        if (onComplete != null) onComplete.run();
                    }
                })
                .start();
    }


    // Updated: movePlayerAndCheck now accepts a completion Runnable and delays the snake/ladder jump by 1 second.
    private void movePlayerAndCheck(int playerId, int steps, Runnable onComplete) {
        // Animate step by step
        animateSteps(playerId, steps, () -> {
            // Movement finished, now check logic
            int currentPos = getPlayerPosition(playerId);
            Integer ladderDest = ladders.get(currentPos);
            Integer snakeDest = snakes.get(currentPos);

            if (ladderDest != null) {
                // Wait a moment, then animate the slide up the ladder
                gameHandler.postDelayed(() -> {
                    animateSlide(playerId, ladderDest, () -> {
                        lastMovedPlayer = playerId;
                        if (onComplete != null) onComplete.run();
                    });
                }, 500);
            } else if (snakeDest != null) {
                // Wait a moment, then animate the slide down the snake
                gameHandler.postDelayed(() -> {
                    animateSlide(playerId, snakeDest, () -> {
                        lastMovedPlayer = playerId;
                        if (onComplete != null) onComplete.run();
                    });
                }, 500);
            } else {
                lastMovedPlayer = playerId;
                if (onComplete != null) onComplete.run();
            }
        });
    }

    // Recursive function to animate steps one by one
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

        // Move 1 step
        placePlayer(playerId, current + 1);

        // Delay next step to create "jumping" or "walking" effect
        gameHandler.postDelayed(() -> {
            animateSteps(playerId, stepsRemaining - 1, onFinished);
        }, 400); // 400ms delay per step
    }

    // Get player's current square (0 if not placed)
    public int getPlayerPosition(int playerId) {
        if (playerId < 1 || playerId > 2) return 0;
        return playerPositions[playerId];
    }

    // Roll dice logic
    public void rollDice() {
        if (rollButton != null) {
            rollButton.setEnabled(false);
        }

        Random rand = new Random();
        int steps = rand.nextInt(6) + 1;

        // Show roll
        if (diceResult != null) {
            if (isVsComputer) {
                diceResult.setText(getString(R.string.player_rolled, steps));
            } else {
                if (currentPlayerTurn == 1) {
                    diceResult.setText(getString(R.string.player_1_rolled, steps));
                } else {
                    diceResult.setText(getString(R.string.player_2_rolled, steps));
                }
            }
        }

        // Delay before moving
        gameHandler.postDelayed(() -> movePlayerAndCheck(currentPlayerTurn, steps, () -> {
            if (checkIfWin()) {
                return;
            }

            // Turn finished
            if (isVsComputer) {
                startComputerTurn();
            } else {
                // Play with Friend
                // Switch turns
                currentPlayerTurn = (currentPlayerTurn == 1) ? 2 : 1;

                // Add a small delay then re-enable button for the next player
                gameHandler.postDelayed(() -> {
                    if (rollButton != null) rollButton.setEnabled(true);
                }, 1000);
            }
        }), 300);
    }

    // Computer plays after a delay
    private void startComputerTurn() {
        Random rand = new Random();
        int delay = 1500; // Fixed 1.5 seconds delay

        gameHandler.postDelayed(() -> {
            int steps = rand.nextInt(6) + 1;

            // Show computer's roll with label
            if (diceResult != null) {
                diceResult.setText(getString(R.string.computer_rolled, steps));
            }

            movePlayerAndCheck(2, steps, () -> {
                if (!checkIfWin()) {
                    // If nobody won, enable button for human to play again
                    if (rollButton != null) {
                        rollButton.setEnabled(true);
                    }
                }
            });
        }, delay);
    }

    public boolean checkIfWin() {
        if (lastMovedPlayer == 0) return false; // no one moved yet
        int pos = getPlayerPosition(lastMovedPlayer);
        if (pos == total) {
            String msg;
            if (isVsComputer) {
                msg = (lastMovedPlayer == 1) ? getString(R.string.player_won) : getString(R.string.computer_won);
            } else {
                msg = (lastMovedPlayer == 1) ? getString(R.string.player_1_won) : getString(R.string.player_2_won);
            }

            // Add winner icon to dialog
            Drawable winnerIcon = ContextCompat.getDrawable(this, R.drawable.ic_winner);

            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.winner_title)
                    .setMessage(msg)
                    .setIcon(winnerIcon)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        // Show menu again after game over
                        menuOverlay.setVisibility(View.VISIBLE);
                    })
                    .show();
            return true;
        }
        return false;
    }

    // Inner overlay View that draws images between squares
    private static class LinesOverlay extends View {
        static class BoardItem {
            final float sx, sy, ex, ey;
            final int resId;
            BoardItem(float sx, float sy, float ex, float ey, int resId) {
                this.sx = sx; this.sy = sy; this.ex = ex; this.ey = ey; this.resId = resId;
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

                // Calculate angle and distance
                float dx = item.ex - item.sx;
                float dy = item.ey - item.sy;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float angle = (float) Math.toDegrees(Math.atan2(dy, dx)) - 90; // -90 because drawables are vertical

                int w = d.getIntrinsicWidth();

                // Save canvas state
                canvas.save();

                // Translate to start point
                canvas.translate(item.sx, item.sy);
                // Rotate
                canvas.rotate(angle);

                int halfW = w / 2;
                d.setBounds(-halfW, 0, halfW, (int) distance);

                d.draw(canvas);

                canvas.restore();
            }
        }
    }
}
