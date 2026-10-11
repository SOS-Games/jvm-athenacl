// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif.critic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Logistic fit on winner-minus-loser feature gaps. Iteration count, example order,
 * and the pull back toward the default weights are fixed, so two runs print the same
 * hash. The header date is the constant {@code 1970-01-01}. A wall-clock date would
 * change every run, and the hash is of the weight lines only.
 */
public final class WeightFit {
    private static final int ITERATIONS = 60;
    private static final double LEARNING = 0.2;
    private static final double L2 = 0.02;

    private WeightFit() {
    }

    public record Model(double[] weights, String[] order, String hash, double accuracy, int n, String yaml) {
    }

    public static Model fit(ListView examples, double[] defaults) {
        double[] start = defaults == null ? MelodyCritic.defaultWeights() : defaults.clone();
        double[] weights = train(examples, start, -1, examples.n());
        double[] rounded = round(weights);
        double accuracy = accuracy(examples, start);
        String hash = hash(rounded);
        String yaml = yaml(rounded, hash, examples.n());
        return new Model(rounded, order(rounded), hash, accuracy, examples.n(), yaml);
    }

    /** Leave-one-out. Each held-out row is scored by a fit that never saw it. */
    private static double accuracy(ListView examples, double[] defaults) {
        if (examples.n() == 0) {
            return 0;
        }
        int hits = 0;
        for (int held = 0; held < examples.n(); held++) {
            double[] weights = train(examples, defaults, held, examples.n());
            double dot = 0;
            for (int feature = 0; feature < weights.length; feature++) {
                dot += weights[feature] * examples.diff(held, feature);
            }
            if (dot > 0) {
                hits++;
            }
        }
        return hits / (double) examples.n();
    }

    private static double[] train(ListView examples, double[] defaults, int skip, int limit) {
        double[] weights = defaults.clone();
        int features = weights.length;
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            for (int row = 0; row < examples.n() && row < limit; row++) {
                if (skip >= 0 && row == skip) {
                    continue;
                }
                double dot = 0;
                for (int feature = 0; feature < features; feature++) {
                    dot += weights[feature] * examples.diff(row, feature);
                }
                dot = Math.max(-20, Math.min(20, dot));
                double prediction = 1.0 / (1.0 + Math.exp(-dot));
                double error = 1.0 - prediction;
                for (int feature = 0; feature < features; feature++) {
                    double gradient = error * examples.diff(row, feature) - L2 * (weights[feature] - defaults[feature]);
                    weights[feature] = Math.max(0, weights[feature] + LEARNING * gradient);
                }
            }
        }
        return weights;
    }

    private static double[] round(double[] weights) {
        double[] rounded = new double[weights.length];
        for (int i = 0; i < weights.length; i++) {
            rounded[i] = Math.round(weights[i] * 1000.0) / 1000.0;
        }
        return rounded;
    }

    private static String[] order(double[] weights) {
        Integer[] index = new Integer[weights.length];
        for (int i = 0; i < index.length; i++) {
            index[i] = i;
        }
        Arrays.sort(index, (left, right) -> {
            int byWeight = Double.compare(weights[right], weights[left]);
            return byWeight != 0 ? byWeight : Integer.compare(left, right);
        });
        String[] names = new String[index.length];
        for (int i = 0; i < index.length; i++) {
            names[i] = MelodyCritic.FEATURES[index[i]];
        }
        return names;
    }

    private static String hash(double[] weights) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < weights.length; i++) {
            text.append(MelodyCritic.FEATURES[i]).append('=')
                    .append(String.format(Locale.ROOT, "%.3f", weights[i])).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte piece : digest) {
                hex.append(String.format("%02x", piece));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String yaml(double[] weights, String hash, int n) {
        StringBuilder text = new StringBuilder();
        text.append("# hook critic weights\n");
        text.append("# n: ").append(n).append('\n');
        text.append("# date: 1970-01-01\n");
        text.append("# weightsHash: ").append(hash).append('\n');
        for (int i = 0; i < weights.length; i++) {
            text.append(MelodyCritic.FEATURES[i]).append(": ")
                    .append(String.format(Locale.ROOT, "%.3f", weights[i])).append('\n');
        }
        return text.toString();
    }

    /** Rows of winner-minus-loser, one column per feature, in {@link MelodyCritic#FEATURES} order. */
    public interface ListView {
        int n();

        double diff(int row, int feature);
    }
}
