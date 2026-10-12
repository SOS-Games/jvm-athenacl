// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.compat;

import io.github.sosgames.athenacl.events.RandomSource;

/**
 * CPython's MT19937, including {@code random.seed(int)}. {@code random()} is the
 * 53-bit mix of two draws. {@code nextInt} is {@code _randbelow}, the same call
 * {@code randint}, {@code choice}, and {@code shuffle} use. Choosing from one item
 * still draws bits until the result is zero. {@code fork} does not advance this generator.
 */
public final class CompatRandom implements RandomSource {
    private static final int N = 624;
    private static final int M = 397;
    private static final int MATRIX_A = 0x9908b0df;
    private static final int UPPER = 0x80000000;
    private static final int LOWER = 0x7fffffff;
    private static final double TWOPI = 2.0 * StrictMath.PI;
    private static final double LOG4 = StrictMath.log(4.0);
    private static final double SG_MAGICCONST = 1.0 + StrictMath.log(4.5);
    private static final double E = StrictMath.E;

    private final int[] mt = new int[N];
    private int index = N;
    private final String seedPath;
    private Double gaussNext;

    private CompatRandom(String seedPath) {
        this.seedPath = seedPath;
    }

    public static CompatRandom seed(long value) {
        CompatRandom random = new CompatRandom(Long.toString(value));
        random.initByArray(words(value));
        return random;
    }

    public double random() {
        long a = Integer.toUnsignedLong(genrand()) >>> 5;
        long b = Integer.toUnsignedLong(genrand()) >>> 6;
        return (a * 67108864.0 + b) * (1.0 / 9007199254740992.0);
    }

    public int randint(int low, int high) {
        if (high < low) {
            throw new IllegalArgumentException("randint high is below low");
        }
        long span = (long) high - (long) low + 1L;
        if (span > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("randint span is too wide");
        }
        return low + randbelow((int) span);
    }

    public <T> T choice(java.util.List<T> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("choice needs an item");
        }
        return items.get(randbelow(items.size()));
    }

    public void shuffle(java.util.List<?> items) {
        for (int i = items.size() - 1; i > 0; i--) {
            int j = randbelow(i + 1);
            java.util.Collections.swap(items, i, j);
        }
    }

    public double gauss(double mu, double sigma) {
        Double cached = gaussNext;
        gaussNext = null;
        double z;
        if (cached == null) {
            double x2pi = random() * TWOPI;
            double g2rad = StrictMath.sqrt(-2.0 * StrictMath.log(1.0 - random()));
            z = StrictMath.cos(x2pi) * g2rad;
            gaussNext = StrictMath.sin(x2pi) * g2rad;
        } else {
            z = cached;
        }
        return mu + z * sigma;
    }

    public double expovariate(double lambda) {
        return -StrictMath.log(1.0 - random()) / lambda;
    }

    public double betavariate(double alpha, double beta) {
        double y = gammavariate(alpha, 1.0);
        if (y == 0.0) {
            return 0.0;
        }
        return y / (y + gammavariate(beta, 1.0));
    }

    @Override
    public double nextDouble() {
        return random();
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        return randbelow(bound);
    }

    @Override
    public RandomSource fork(long... scope) {
        StringBuilder path = new StringBuilder(seedPath);
        int[] key = new int[scope.length == 0 ? 1 : scope.length * 2];
        int n = 0;
        if (scope.length == 0) {
            key[n++] = 0;
        }
        for (long part : scope) {
            path.append('/').append(part);
            key[n++] = (int) part;
            key[n++] = (int) (part >>> 32);
        }
        CompatRandom child = new CompatRandom(path.toString());
        child.initByArray(java.util.Arrays.copyOf(key, n));
        return child;
    }

    @Override
    public String seedPath() {
        return seedPath;
    }

    private int randbelow(int bound) {
        int bits = 32 - Integer.numberOfLeadingZeros(bound);
        while (true) {
            int draw = getrandbits(bits);
            if (draw < bound) {
                return draw;
            }
        }
    }

    private int getrandbits(int bits) {
        int draw = genrand();
        if (bits < 32) {
            draw >>>= (32 - bits);
        }
        return draw;
    }

    private double gammavariate(double alpha, double beta) {
        if (alpha <= 0.0 || beta <= 0.0) {
            throw new IllegalArgumentException("gamma alpha and beta must be positive");
        }
        if (alpha > 1.0) {
            double ainv = StrictMath.sqrt(2.0 * alpha - 1.0);
            double bbb = alpha - LOG4;
            double ccc = alpha + ainv;
            while (true) {
                double u1 = random();
                if (!(1e-7 < u1 && u1 < 0.9999999)) {
                    continue;
                }
                double u2 = 1.0 - random();
                double v = StrictMath.log(u1 / (1.0 - u1)) / ainv;
                double x = alpha * StrictMath.exp(v);
                double z = u1 * u1 * u2;
                double r = bbb + ccc * v - x;
                if (r + SG_MAGICCONST - 4.5 * z >= 0.0 || r >= StrictMath.log(z)) {
                    return x * beta;
                }
            }
        }
        if (alpha == 1.0) {
            return -StrictMath.log(1.0 - random()) * beta;
        }
        while (true) {
            double u = random();
            double b = (E + alpha) / E;
            double p = b * u;
            double x;
            if (p <= 1.0) {
                x = StrictMath.pow(p, 1.0 / alpha);
            } else {
                x = -StrictMath.log((b - p) / alpha);
            }
            double u1 = random();
            if (p > 1.0) {
                if (u1 <= StrictMath.pow(x, alpha - 1.0)) {
                    return x * beta;
                }
            } else if (u1 <= StrictMath.exp(-x)) {
                return x * beta;
            }
        }
    }

    private void initByArray(int[] key) {
        initGenrand(19650218);
        int i = 1;
        int j = 0;
        int k = Math.max(N, key.length);
        for (; k > 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1664525)) + key[j] + j;
            i++;
            j++;
            if (i >= N) {
                mt[0] = mt[N - 1];
                i = 1;
            }
            if (j >= key.length) {
                j = 0;
            }
        }
        for (k = N - 1; k > 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1566083941)) - i;
            i++;
            if (i >= N) {
                mt[0] = mt[N - 1];
                i = 1;
            }
        }
        mt[0] = UPPER;
    }

    private void initGenrand(int seed) {
        mt[0] = seed;
        for (int i = 1; i < N; i++) {
            mt[i] = 1812433253 * (mt[i - 1] ^ (mt[i - 1] >>> 30)) + i;
        }
        index = N;
    }

    private int genrand() {
        if (index >= N) {
            twist();
        }
        int y = mt[index++];
        y ^= (y >>> 11);
        y ^= (y << 7) & 0x9d2c5680;
        y ^= (y << 15) & 0xefc60000;
        y ^= (y >>> 18);
        return y;
    }

    private void twist() {
        for (int k = 0; k < N - M; k++) {
            int y = (mt[k] & UPPER) | (mt[k + 1] & LOWER);
            mt[k] = mt[k + M] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
        }
        for (int k = N - M; k < N - 1; k++) {
            int y = (mt[k] & UPPER) | (mt[k + 1] & LOWER);
            mt[k] = mt[k + (M - N)] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
        }
        int y = (mt[N - 1] & UPPER) | (mt[0] & LOWER);
        mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
        index = 0;
    }

    private static int[] words(long value) {
        long abs = value == Long.MIN_VALUE ? Long.MIN_VALUE : Math.abs(value);
        if (Long.compareUnsigned(abs, 0xFFFFFFFFL) <= 0) {
            return new int[] {(int) abs};
        }
        return new int[] {(int) abs, (int) (abs >>> 32)};
    }
}
