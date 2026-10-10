// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/** {@code L64X128MixRandom}, split by hashing the scope into a new seed. */
public final class DefaultRandom implements RandomSource {
    private final RandomGenerator random;
    private final String seedPath;

    private DefaultRandom(RandomGenerator random, String seedPath) {
        this.random = random;
        this.seedPath = seedPath;
    }

    public static DefaultRandom of(long seed) {
        return new DefaultRandom(newGenerator(seed), Long.toString(seed));
    }

    @Override
    public double nextDouble() {
        return random.nextDouble();
    }

    @Override
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    @Override
    public RandomSource fork(long... scope) {
        long mixed = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < seedPath.length(); i++) {
            mixed = mix(mixed, seedPath.charAt(i));
        }
        for (long part : scope) {
            mixed = mix(mixed, part);
        }
        StringBuilder path = new StringBuilder(seedPath);
        for (long part : scope) {
            path.append('/').append(part);
        }
        return new DefaultRandom(newGenerator(mixed), path.toString());
    }

    @Override
    public String seedPath() {
        return seedPath;
    }

    private static RandomGenerator newGenerator(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    private static long mix(long left, long right) {
        long value = left ^ (right + 0x9E3779B97F4A7C15L + (left << 6) + (left >>> 2));
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
