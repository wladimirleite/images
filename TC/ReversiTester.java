import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.awt.event.KeyEvent;

import com.topcoder.marathon.*;

public class ReversiTester extends MarathonAnimatedVis {
    // parameter ranges
    private static final int minN = 8, maxN = 30; // grid size range
    private static final int minC = 2, maxC = 6; // number of colours range
    private static final double minP = 0, maxP = 0.2; // wall ratio range

    // Inputs
    private int N; // grid size
    private int C; // number of colours
    private double P; // wall ratio

    // Constants
    private static final int genIters = 100;
    private static final int Wall = -1;
    private static final int Empty = 0;
    private static final int[] dr = {-1,-1,0,1,1,1,0,-1};
    private static final int[] dc = {0,1,1,1,0,-1,-1,-1};

    // Graphics
    private static final Color[] colors = {new Color(255, 0, 0),new Color(0, 180, 0),new Color(0, 0, 255),new Color(255, 255, 0),new Color(255, 0, 255),new Color(0, 255, 255)};
    private Color[] colorsLight;
    private static final Color wallColor = Color.darkGray;
    private int selectedColor = 1;

    // State Control
    private int[][] grid;
    private int[][] target;
    private List<List<Move>> history; // used for undo in manual mode
    private int numMoves = 0;
    private int numEmpty = 0;
    private int selectedR = -1;
    private int selectedC = -1;
    private int matches, numTargets;
    private double score;
    private int[] targetsPerColor, matchesPerColor;

    protected void generate() {
        N = randomInt(minN, maxN);
        C = randomInt(minC, maxC);
        P = randomDouble(minP, maxP);

        // Special cases
        if (seed == 1) {
            N = minN;
            C = 3;
        } else if (seed == 2) {
            N = maxN;
            C = maxC;
            P = maxP;
        }

        // User defined parameters
        if (parameters.isDefined("N")) N = randomInt(parameters.getIntRange("N"), minN, maxN);
        if (parameters.isDefined("C")) C = randomInt(parameters.getIntRange("C"), minC, maxC);
        if (parameters.isDefined("P")) P = randomDouble(parameters.getDoubleRange("P"), minP, maxP);

        history = new ArrayList<List<Move>>();
        grid = new int[N][N];
        numEmpty = N * N;

        // add starting tokens
        for (int i = 1; i <= C; i++) {
            if (C == 2 && i == 2) break;

            int r = -1, c = -1;
            while (true) {
                r = randomInt(0, N - 2);
                c = randomInt(0, N - 2);
                if (grid[r][c] == Empty && grid[r + 1][c] == Empty && grid[r][c + 1] == Empty && grid[r + 1][c + 1] == Empty) break;
            }

            grid[r][c] = i;
            grid[r + 1][c + 1] = i;
            int col = i + 1;
            if (col > C) col = 1;
            grid[r + 1][c] = col;
            grid[r][c + 1] = col;
            numEmpty -= 4;
        }

        // add walls
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++)
                if (grid[r][c] == Empty && randomDouble(0, 1) < P) {
                    grid[r][c] = Wall;
                    numEmpty--;
                }

        // generate target grid via simulation
        int[] ind = new int[N * N * C];
        for (int i = 0; i < ind.length; i++)
            ind[i] = i;

        target = new int[N][N];

        int bestFilled = 0;
        int bestUsed = 0;
        int[][] bestTarget = new int[N][N];

        int iter = 0;
        for (; iter < genIters; iter++) {
            int numFilled = 0;

            for (int r = 0; r < N; r++)
                for (int c = 0; c < N; c++)
                    target[r][c] = grid[r][c];

            while (true) {
                shuffle(ind);
                boolean moved = false;
                for (int i = 0; i < ind.length; i++) {
                    int r = ind[i] / (N * C);
                    int c = (ind[i] - r * N * C) / C;
                    int col = (ind[i] % C) + 1;

                    if (canMove(target, r, c, col)) {
                        moved = true;
                        makeMove(target, r, c, col, false);
                        numFilled++;

                        // System.out.println("target after "+col+" at "+r+" "+c);
                        // printGrid(target);
                    }
                }
                if (!moved) break;
            }

            Set<Integer> used = new HashSet<Integer>();
            for (int r = 0; r < N; r++)
                for (int c = 0; c < N; c++)
                    if (target[r][c] != Wall && target[r][c] != Empty) used.add(target[r][c]);

            // save best target
            if (numFilled > bestFilled || (numFilled == bestFilled && used.size() > bestUsed)) {
                bestFilled = numFilled;
                bestUsed = used.size();
                if (debug) System.out.println("iter " + iter + " empty " + (numEmpty - numFilled) + " used " + used.size());

                for (int r = 0; r < N; r++)
                    for (int c = 0; c < N; c++)
                        bestTarget[r][c] = target[r][c];

                if (numEmpty == numFilled && used.size() == C) break; // found optimal
            }
        }
        if (debug) System.out.println("iters " + iter);

        // load best target
        targetsPerColor = new int[C];
        matchesPerColor = new int[C];
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                int color = target[r][c] = bestTarget[r][c];
                if (color >= 1) {
                    targetsPerColor[color - 1]++;
                    numTargets++;
                }
            }
        }

        if (debug) {
            System.out.println("Grid size, N = " + N);
            System.out.println("Number of colours, C = " + C);
            System.out.println("Wall ratio, P = " + P);
            System.out.println("Start grid:");
            printGrid(grid);
            System.out.println("\nTarget grid:");
            printGrid(target);
        }
    }

    protected void printGrid(int[][] g) {
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                String a = "";
                if (g[r][c] == Empty) a = ".";
                else if (g[r][c] == Wall) a = "#";
                else a = "" + g[r][c];
                System.out.print(a);
            }
            System.out.println();
        }
    }

    // shuffle the array randomly
    protected void shuffle(int[] a) {
        for (int i = 0; i < a.length; i++) {
            int k = randomInt(i, a.length - 1);
            int temp = a[i];
            a[i] = a[k];
            a[k] = temp;
        }
    }

    protected boolean canMove(int[][] g, int r, int c, int col) {
        if (g[r][c] != Empty) return false;

        for (int m = 0; m < dr.length; m++) {
            int r2 = r + dr[m];
            int c2 = c + dc[m];
            boolean found = false;
            int steps = 1;
            while (true) {
                if (!inGrid(r2, c2) || g[r2][c2] == Empty || g[r2][c2] == Wall) break;
                if (g[r2][c2] == col) {
                    found = true;
                    break;
                }
                r2 += dr[m];
                c2 += dc[m];
                steps++;
            }
            if (found && steps > 1) return true;
        }
        return false;
    }

    protected void makeMove(int[][] g, int r, int c, int col, boolean record) {
        List<Move> hist = new ArrayList<Move>();
        if (record) hist.add(new Move(r, c, Empty));

        g[r][c] = col;

        for (int m = 0; m < dr.length; m++) {
            int r2 = r + dr[m];
            int c2 = c + dc[m];
            boolean found = false;
            int steps = 1;
            while (true) {
                if (!inGrid(r2, c2) || g[r2][c2] == Empty || g[r2][c2] == Wall) break;
                if (g[r2][c2] == col) {
                    found = true;
                    break;
                }
                r2 += dr[m];
                c2 += dc[m];
                steps++;
            }

            if (found && steps > 1) {
                r2 = r + dr[m];
                c2 = c + dc[m];
                while (true) {
                    if (!inGrid(r2, c2) || g[r2][c2] == Empty || g[r2][c2] == col || g[r2][c2] == Wall) break;

                    if (record) hist.add(new Move(r2, c2, g[r2][c2]));
                    g[r2][c2] = col;
                    r2 += dr[m];
                    c2 += dc[m];
                }
            }
        }

        if (record) history.add(hist);
    }

    protected boolean isMaximize() {
        return true;
    }

    protected double run() throws Exception {
        init();

        if (parameters.isDefined("manual")) {
            setDefaultDelay(0);
            updateState();
            return 0;
        } else return runAuto();
    }

    protected double runAuto() throws Exception {
        double score = callSolution();
        if (score < 0) {
            if (!isReadActive()) return getErrorScore();
            return fatalError();
        }
        return score;
    }

    protected void timeout() {
        addInfo("Time", getRunTime());
        update();
    }

    private double callSolution() throws Exception {
        writeLine("" + N);
        writeLine("" + C);
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++)
                writeLine("" + grid[r][c]);
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++)
                writeLine("" + target[r][c]);

        flush();
        if (!isReadActive()) return -1;

        updateState();

        try {
            // read solution output
            startTime();
            int n = Integer.parseInt(readLine());
            if (n < 0 || n > numEmpty) return fatalError("Number of moves must between 0 and " + numEmpty + " inclusive");

            String[] moves = new String[n];
            for (int i = 0; i < n; i++)
                moves[i] = readLine();
            stopTime();

            for (numMoves = 1; numMoves <= n; numMoves++) {
                String[] temp = moves[numMoves - 1].split(" ");
                if (temp.length != 3) return fatalError("Each move must contain exactly 3 numbers separated by a space");

                int r = Integer.parseInt(temp[0]);
                int c = Integer.parseInt(temp[1]);
                int col = Integer.parseInt(temp[2]);

                if (!inGrid(r, c)) return fatalError("Location (" + r + "," + c + ") is not in the grid");
                if (grid[r][c] != Empty) return fatalError("Location (" + r + "," + c + ") is not empty");
                if (col < 1 || col > C) return fatalError("Colour must be between 1 and " + C + ", inclusive");
                if (!canMove(grid, r, c, col)) return fatalError("Placing colour " + col + " at location (" + r + "," + c + ") is not legal");

                // make move
                selectedR = r;
                selectedC = c;
                grid[r][c] = col;
                if (!parameters.isDefined("noanimate")) updateState();

                makeMove(grid, r, c, col, false);
                computeScore();
                if (!parameters.isDefined("noanimate")) updateState();
            }
            numMoves--;
            if (parameters.isDefined("noanimate")) updateState();
        } catch (Exception e) {
            if (debug) System.out.println(e.toString());
            return fatalError("Cannot parse your output");
        }

        return score;
    }

    protected boolean inGrid(int r, int c) {
        return r >= 0 && r < N && c >= 0 && c < N;
    }

    protected void computeScore() {
        matches = 0;
        Arrays.fill(matchesPerColor, 0);
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                int t = target[r][c];
                if (t != Wall && t != Empty && grid[r][c] == t) {
                    matches++;
                    matchesPerColor[t - 1]++;
                }
            }
        }

        score = numMoves + matches * matches;
    }

    protected void updateState() {
        if (hasVis()) {
            synchronized (updateLock) {
                addInfo("Moves", numMoves);
                addInfo("Time", getRunTime());
                computeScore();
                for (int i = 0; i < C; i++) {
                    addInfo(colors[i], matchesPerColor[i] + " / " + targetsPerColor[i]);
                }
                addInfo("Matches", matches + " / " + numTargets);
                addInfo("Score", (long) score);
                if (parameters.isDefined("manual")) addInfo("Selected", selectedColor);
            }
            updateDelay();
        }
    }

    protected void undo() {
        if (history.size() == 0) return;

        List<Move> hist = history.remove(history.size() - 1);

        for (Move m : hist)
            grid[m.r][m.c] = m.col;

        numEmpty++;
        numMoves--;
        selectedR = -1;
        selectedC = -1;
        updateState();
    }

    protected void contentClicked(double x, double y, int mouseButton, int clickCount) {
        if (!parameters.isDefined("manual")) return;

        // undo with right click
        if (mouseButton == java.awt.event.MouseEvent.BUTTON3) {
            undo();
            return;
        }

        int r = (int) Math.floor(y);
        int c = (int) Math.floor(x);

        if (!inGrid(r, c)) return; // outside of the grid

        // try to make move
        if (canMove(grid, r, c, selectedColor)) {
            selectedR = r;
            selectedC = c;
            makeMove(grid, r, c, selectedColor, true);
            numMoves++;
            numEmpty--;
            updateState();
        }
    }

    protected void handleKeyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();

        int[] codes = {KeyEvent.VK_1,KeyEvent.VK_2,KeyEvent.VK_3,KeyEvent.VK_4,KeyEvent.VK_5,KeyEvent.VK_6};
        for (int i = 0; i < C; i++)
            if (keyCode == codes[i]) {
                selectedColor = i + 1;
                new Thread() {
                    public void run() {
                        updateState();
                    }
                }.start();
            }
    }

    protected void paintContent(Graphics2D g) {
        g.setColor(Color.white);
        g.fillRect(0, 0, N, N);

        // Fill cells using target color and draw tokens as circles  
        g.setStroke(new BasicStroke(0.025f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (grid[r][c] == Wall) {
                    g.setColor(wallColor);
                    g.fillRect(c, r, 1, 1);
                } else {
                    g.setColor(colorsLight[target[r][c]]);
                    g.fillRect(c, r, 1, 1);
                    if (grid[r][c] != Empty) {
                        g.setColor(colors[grid[r][c] - 1]);
                        Ellipse2D t = new Ellipse2D.Double(c + 0.05, r + 0.05, 0.9, 0.9);
                        g.fill(t);
                        g.setColor(Color.black);
                        g.draw(t);
                    }
                }
            }
        }

        // draw grid lines
        g.setColor(Color.black);
        g.setStroke(new BasicStroke(0.005f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i <= N; i++) {
            g.drawLine(i, 0, i, N);
            g.drawLine(0, i, N, i);
        }

        // draw selected cells
        if (selectedR != -1) {
            g.setColor(Color.black);
            g.setStroke(new BasicStroke(0.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawRect(selectedC, selectedR, 1, 1);
        }

        // draw selected colour
        if (parameters.isDefined("manual")) {
            g.setColor(colors[selectedColor - 1]);
            g.fillRect(0, N + 1, N, 2);
            g.setColor(Color.black);
            adjustFont(g, Font.SANS_SERIF, Font.PLAIN, "Selected", new Rectangle2D.Double(0, 0, N / 2, 1));
            drawString(g, "Selected", new Rectangle2D.Double(N / 2.0, N + 2, 0, 0));
        }
    }

    private void init() {
        if (hasVis()) {
            setDefaultDelay(10);

            colorsLight = new Color[colors.length + 1];
            colorsLight[0] = Color.white;
            for (int i = 0; i < colors.length; i++) {
                Color c = colors[i];
                colorsLight[i + 1] = new Color(c.getRed(), c.getGreen(), c.getBlue(), 80);
            }

            if (parameters.isDefined("manual")) setContentRect(0, 0, N, N + 3);
            else setContentRect(0, 0, N, N);
            setInfoMaxDimension(20, 10 + C);

            addInfo("Seed", seed);
            addInfo("N", N);
            addInfo("C", C);
            addInfo("P", String.format("%.3f", P));

            addInfoBreak();
            addInfo("Time", "-");
            addInfo("Moves", "-");
            addInfo("Matches", matches + " / " + numTargets);
            addInfo("Score", (long) score);

            addInfoBreak();
            for (int i = 0; i < C; i++) {
                addInfo(colors[i], matchesPerColor[i] + " / " + targetsPerColor[i]);
            }
            if (parameters.isDefined("manual")) addInfo("Selected", selectedColor);

            update();
        }
    }

    public static void main(String[] args) {
        new MarathonController().run(args);
    }

    public class Move {
        int r;
        int c;
        int col;

        public Move(int r2, int c2, int col2) {
            r = r2;
            c = c2;
            col = col2;
        }
    }
}
