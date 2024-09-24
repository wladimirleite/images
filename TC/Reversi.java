import java.io.*;
import java.util.*;

public class Reversi {
    private static long timeout1;
    private static final long timeout2 = System.currentTimeMillis() + 9750;
    private static int n, nn, c, maxTurns;
    private static int[] zh, zs;
    private static byte[] target, grid;
    private static byte[][] saveGrid;
    private static short[] ccount;
    private static boolean[] corner;
    private static State initial, best;
    private static final int[] dx = {-1,-1,0,1,1,1,0,-1};
    private static final int[] dy = {0,1,1,1,0,-1,-1,-1};
    private static int[][][] paths;
    private static final double[] log = new double[1 << 16];
    private static int rnd = 19720909;

    public static void main(String[] args) throws Exception {
        init();
        solve();
        improve();
    }

    private static void solve() {
        best = initial;
        int m = 1;
        while (System.currentTimeMillis() < timeout2 && m <= 32) {
            int width = (int) (m * 10_000_000 / Math.pow(n, 3.7) / Math.pow(c, 0.6));
            Beam currStates = new Beam(width);
            Beam nextStates = new Beam(width);
            currStates.add(initial);
            long t0 = System.currentTimeMillis();
            int turn = 0;
            while (currStates.size() > 0 && System.currentTimeMillis() < timeout1) {
                for (int i = 0; i < currStates.size() && i < width; i++) {
                    State curr = currStates.get(i);
                    curr.updateGrid();
                    expand(curr, nextStates);
                }
                turn++;
                if (nextStates.size() == 0) break;
                long t1 = System.currentTimeMillis();
                long rem = (t1 - t0) * (maxTurns - turn);
                t0 = t1;
                if (t1 + rem / 1.1 > timeout1 && width > 1) {
                    width = Math.max(1, (int) (width / 1.05) - 1);
                    currStates.setWidth(width);
                    nextStates.setWidth(width);
                } else if (t1 + rem * 1.1 < timeout1 && width < 100000) {
                    width = Math.min(100000, (int) (width * 1.05) + 1);
                    currStates.setWidth(width);
                    nextStates.setWidth(width);
                }
                Beam aux = currStates;
                currStates = nextStates;
                nextStates = aux;
                nextStates.clear();
            }
            if (best.placed > nn/2) break;
            if (m < 16) m *= 2;
            else m++;
        }
    }

    private static void expand(State curr, Beam nextStates) {
        for (int pos = 0; pos < nn; pos++) {
            if (curr.grid[pos] != 0) continue;
            int[][] pp = paths[pos];
            if (pp.length == 0) continue;
            int tp = target[pos];
            for (int color = 1; color <= c; color++) {
                int gain = tp == color ? 1 : 0;
                boolean valid = false;
                int hash = curr.hash;
                System.arraycopy(curr.count, 0, ccount, 0, c);
                for (int[] pd : pp) {
                    byte old = curr.grid[pd[0]];
                    if (old <= 0 || old == color) continue;
                    for (int i = 1; i < pd.length; i++) {
                        old = curr.grid[pd[i]];
                        if (old <= 0) break;
                        if (old == color) {
                            valid = true;
                            for (int j = 0; j < i; j++) {
                                int p = pd[j];
                                hash ^= zs[swap(color, old = curr.grid[p], p)];
                                byte t = target[p];
                                if (t == color) gain++;
                                else if (t == old) gain--;
                                ccount[color - 1]++;
                                ccount[old - 1]--;
                            }
                            break;
                        }
                    }
                }
                if (valid) {
                    State next = new State();
                    next.bonus = curr.bonus;
                    if (corner[pos]) {
                        if (color == target[pos]) next.bonus++;
                        else next.bonus--;
                    }
                    next.count = ccount;
                    next.count[color - 1]++;
                    next.parent = curr;
                    next.matches = curr.matches + gain;
                    next.placed = curr.placed + 1;
                    next.move = move(color, pos);
                    next.hash = hash ^ zh[next.move];
                    if (nextStates.add(next)) ccount = new short[c];
                    if (next.score() > best.score()) {
                        best = next;
                        best.updateGrid();
                    }
                }
            }
        }
    }

    private static void output(int[] moves) {
        StringBuilder sb = new StringBuilder();
        sb.append(moves.length).append("\n");
        for (int m : moves) {
            int pos = pos(m);
            sb.append(y(pos)).append(' ').append(x(pos)).append(' ').append(color(m)).append('\n');
        }
        System.out.print(sb);
        System.out.flush();
    }

    private static void improve() {
        List<Integer> lm = new ArrayList<Integer>();
        State s = best;
        while (s.parent != null) {
            lm.add(s.move);
            s = s.parent;
        }
        Collections.reverse(lm);
        int[] moves = new int[lm.size()];
        for (int i = 0; i < moves.length; i++) {
            moves[i] = lm.get(i);
        }
        int[] order = new int[c + 1];
        for (int i = 1; i <= c; i++) {
            order[i] = i;
        }
        int curr = eval(moves, order, 0, nn + 1, true);
        int[] ret = moves.clone();
        int[] changed = moves.clone();
        int max = curr;
        double t0 = n < 15 ? 0.2 : 0.4;
        double temp = t0;
        double duration = timeout2 - System.currentTimeMillis();
        int loop = 0;
        while (true) {
            if ((++loop & 255) == 0) {
                long t = System.currentTimeMillis();
                if (t >= timeout2) break;
                temp = (timeout2 - t) * t0 / duration;
            }
            int o0 = nextInt(c) + 1;
            int o1 = nextInt(c) + 1;
            if (o0 != o1) {
                int aux = order[o0];
                order[o0] = order[o1];
                order[o1] = aux;
            }
            int start = 0;
            int end = nn + 1;
            if ((loop & 7) == 0) {
                int p2 = nextInt(moves.length);
                int m2 = moves[p2];
                int nc = nextInt(c) + 1;
                if (nc == color(m2)) continue;
                System.arraycopy(moves, 0, changed, 0, moves.length);
                changed[p2] = move(nc, pos(m2));
                start = p2;
                end = p2;
            } else {
                int p0 = nextInt(moves.length);
                int p1 = nextInt(moves.length);
                if (n > 16) {
                    int p2 = nextInt(moves.length);
                    if (p2 != p0 && Math.abs(p0 - p2) < Math.abs(p0 - p1)) p1 = p2;
                }
                if (p0 == p1) continue;
                if (p0 > p1) {
                    System.arraycopy(moves, 0, changed, 0, p1);
                    System.arraycopy(moves, p1, changed, p1 + 1, p0 - p1);
                    System.arraycopy(moves, p0 + 1, changed, p0 + 1, moves.length - p0 - 1);
                    start = p1;
                    end = p0;
                } else {
                    System.arraycopy(moves, 0, changed, 0, p0);
                    System.arraycopy(moves, p0 + 1, changed, p0, p1 - p0);
                    System.arraycopy(moves, p1 + 1, changed, p1 + 1, moves.length - p1 - 1);
                    start = p0;
                    end = p1;
                }
                changed[p1] = moves[p0];
            }
            int next = eval(changed, order, start, end, false);
            if (next == -10000) next = curr;
            int gain = next - curr;
            if (gain >= 0 || gain > log[nextInt(log.length)] * temp) {
                curr = next;
                eval(changed, order, start, end, true);
                System.arraycopy(changed, 0, moves, 0, moves.length);
                if (curr > max) {
                    max = curr;
                    System.arraycopy(changed, 0, ret, 0, moves.length);
                }
            }
        }
        output(ret);
    }

    private static int eval(int[] moves, int[] order, int start, int end, boolean save) {
        if (start == 0) System.arraycopy(initial.grid, 0, grid, 0, nn);
        else System.arraycopy(saveGrid[start >>> 2], 0, grid, 0, nn);
        boolean checked = false;
        for (int i = start & ~3; i < moves.length; i++) {
            if (!checked && i > end && (i & 3) == 0) {
                if (Arrays.equals(grid, saveGrid[i >>> 2])) return -10000;
                checked = true;
            }
            if (save && i > start && (i & 3) == 0) System.arraycopy(grid, 0, saveGrid[i >>> 2], 0, nn);
            int m = moves[i];
            int pos = pos(m);
            int[][] pp = paths[pos];
            for (int nc : order) {
                byte color = (byte) (nc == 0 ? color(m) : nc);
                boolean valid = false;
                for (int[] pd : pp) {
                    byte old = grid[pd[0]];
                    if (old <= 0 || old == color) continue;
                    for (int j = 1; j < pd.length; j++) {
                        old = grid[pd[j]];
                        if (old <= 0) break;
                        if (old == color) {
                            for (int k = 0; k < j; k++) {
                                grid[pd[k]] = color;
                            }
                            valid = true;
                            break;
                        }
                    }
                }
                if (!valid) {
                    if (nc == c) return -1;
                    continue;
                }
                grid[pos] = color;
                if (nc != 0) moves[i] = move(nc, pos);
                break;
            }
        }
        int matches = 0;
        for (int i = 0; i < nn; i++) {
            byte t = target[i];
            if (t >= 1 && grid[i] == t) matches++;
        }
        return matches;
    }

    private static int pos(int x, int y) {
        return x + y * n;
    }

    private static int move(int color, int pos) {
        return color * nn + pos;
    }

    private static int swap(int c1, int c2, int pos) {
        return (c1 + c2 * (c + 1)) * nn + pos;
    }

    private static int color(int move) {
        return move / nn;
    }

    private static int pos(int move) {
        return move % nn;
    }

    private static int x(int pos) {
        return pos % n;
    }

    private static int y(int pos) {
        return pos / n;
    }

    private static void init() throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        n = Integer.parseInt(in.readLine());
        c = Integer.parseInt(in.readLine());
        nn = n * n;
        SplittableRandom rnd = new SplittableRandom(nn + c);
        zh = new int[nn * (c + 1)];
        zs = new int[nn * (c + 1) * (c + 1)];
        for (int color = 1; color <= c; color++) {
            for (int pos = 0; pos < nn; pos++) {
                zh[move(color, pos)] = rnd.nextInt();
            }
        }
        for (int c1 = 1; c1 <= c; c1++) {
            for (int c2 = c1 + 1; c2 <= c; c2++) {
                for (int pos = 0; pos < nn; pos++) {
                    zs[swap(c1, c2, pos)] = zs[swap(c2, c1, pos)] = zh[move(c1, pos)] ^ zh[move(c2, pos)];
                }
            }
        }
        initial = new State();
        initial.grid = new byte[nn];
        initial.count = new short[c];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int pos = pos(x, y);
                byte color = initial.grid[pos] = (byte) Integer.parseInt(in.readLine());
                if (color > 0) {
                    initial.hash ^= zh[move(color, pos)];
                    initial.count[color - 1]++;
                }
                if (color == 0) maxTurns++;
            }
        }
        boolean[] seen = new boolean[c];
        target = new byte[nn];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = pos(x, y);
                byte color = target[p] = (byte) Integer.parseInt(in.readLine());
                if (color > 0 && color == initial.grid[p]) initial.matches++;
                if (color > 0) seen[color - 1] = true;
            }
        }
        for (int i = 0; i < c; i++) {
            if (!seen[i]) initial.count[i] -= nn;
        }
        in.close();
        corner = new boolean[nn];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int cnt = 0;
                for (int dir = 0; dir < 4; dir++) {
                    int nx = x + dx[dir];
                    if (nx < 0 || nx >= n) continue;
                    int ny = y + dy[dir];
                    if (ny < 0 || ny >= n) continue;
                    if (initial.grid[pos(nx, ny)] == -1) continue;
                    nx = x + dx[dir + 4];
                    if (nx < 0 || nx >= n) continue;
                    ny = y + dy[dir + 4];
                    if (ny < 0 || ny >= n) continue;
                    if (initial.grid[pos(nx, ny)] == -1) continue;
                    cnt++;
                }
                if (cnt == 0) corner[pos(x, y)] = true;
            }
        }
        paths = new int[nn][][];
        int[] update = new int[n];
        for (int pos = 0; pos < nn; pos++) {
            if (initial.grid[pos] != 0) continue;
            int x = x(pos);
            int y = y(pos);
            List<int[]> l = new ArrayList<int[]>();
            for (int dir = 0; dir < dx.length; dir++) {
                int tot = 0;
                int nx = x;
                int ny = y;
                int ax = dx[dir];
                int ay = dy[dir];
                tot = 0;
                for (int d = 0; d < n; d++) {
                    if ((nx += ax) < 0 || nx >= n) break;
                    if ((ny += ay) < 0 || ny >= n) break;
                    int np = pos(nx, ny);
                    if (initial.grid[np] == -1) break;
                    update[tot++] = np;
                }
                if (tot > 1) l.add(Arrays.copyOf(update, tot));
            }
            paths[pos] = l.toArray(new int[0][0]);
        }
        ccount = new short[c];
        for (int i = 0; i < log.length; i++) {
            log[i] = Math.log((i + 0.5) / log.length);
        }
        grid = new byte[nn];
        saveGrid = new byte[(nn + 3) >>> 2][nn];
        if (n <= 10) timeout1 = timeout2 - 7700;
        else if (n <= 21) timeout1 = timeout2 - 7000;
        else if (n <= 26) timeout1 = timeout2 - 6000;
        else timeout1 = timeout2 - 5000;
    }

    private static int nextInt(int v) {
        rnd ^= rnd << 13;
        rnd ^= rnd >>> 17;
        rnd ^= rnd << 15;
        return ((rnd >>> 15) * v) >>> 17;
    }

    static class Beam {
        private int beamWidth;
        private State[] items;
        private int size;

        Beam(int beamWidth) {
            this.beamWidth = beamWidth;
            items = new State[beamWidth * 4];
        }

        boolean add(State item) {
            if (size >= beamWidth && (items[beamWidth - 1]).compareTo(item) <= 0) return false;
            int pos = Arrays.binarySearch(items, 0, size, item);
            if (pos < 0) pos = -pos - 1;
            else return false;
            if (pos >= beamWidth) return false;
            if (size < beamWidth) size++;
            for (int i = size - 1; i > pos; i--) {
                items[i] = items[i - 1];
            }
            items[pos] = item;
            return true;
        }

        State get(int idx) {
            return items[idx];
        }

        void remove(int idx) {
            size--;
            for (int i = idx; i < size; i++) {
                items[i] = items[i + 1];
            }
        }

        int size() {
            return size;
        }

        void clear() {
            size = 0;
        }

        void setWidth(int width) {
            beamWidth = width;
            if (size > width) size = width;
            if (items.length < width) items = Arrays.copyOf(items, width);
        }
    }

    static class State implements Comparable<State> {
        int hash;
        State parent;
        byte[] grid;
        int matches, move, placed, val = Integer.MIN_VALUE, bonus;
        short[] count;

        int score() {
            return matches + placed * 8;
        }

        int eval() {
            if (val == Integer.MIN_VALUE) {
                val = matches + bonus;
                for (int v : count) {
                    if (v == 0) val -= nn;
                    else if (v == 1) val -= n;
                }
            }
            return val;
        }

        public int compareTo(State o) {
            int cmp = Integer.compare(o.eval(), eval());
            if (cmp != 0) return cmp;
            return Integer.compare(hash, o.hash);
        }

        void updateGrid() {
            if (grid != null) return;
            grid = parent.grid.clone();
            int pos = pos(move);
            byte color = grid[pos] = (byte) color(move);
            int[][] pp = paths[pos];
            for (int[] pd : pp) {
                byte old = grid[pd[0]];
                if (old <= 0 || old == color) continue;
                for (int i = 1; i < pd.length; i++) {
                    old = grid[pd[i]];
                    if (old <= 0) break;
                    if (old == color) {
                        for (int j = 0; j < i; j++) {
                            grid[pd[j]] = color;
                        }
                        break;
                    }
                }
            }
        }
    }
}
