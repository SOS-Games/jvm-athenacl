// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import io.github.sosgames.athenacl.compat.CompatRandom;
import io.github.sosgames.athenacl.compat.Py;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The 13 rhythm generators. Each one is sampled six times at 120 bpm with
 * both random streams seeded to 7. A pulse compared with nothing uses
 * identity. {@code iterateRhythmHold} treats a missing refresh as less than
 * the event count, the way Python 2 did. {@code gaRhythm} breeds its pulse
 * list for 40 generations before the first sample.
 */
public final class RhythmGenerators {
    public record Generator(String acronym, String name) {
    }

    public record Check(int pass, int fail, List<String> failed) {
    }

    private static final List<Generator> RHYTHM = List.of(
            new Generator("cs", "convertSecond"),
            new Generator("cst", "convertSecondTriple"),
            new Generator("pt", "pulseTriple"),
            new Generator("ba", "binaryAccent"),
            new Generator("gr", "gaRhythm"),
            new Generator("l", "loop"),
            new Generator("ps", "pulseSieve"),
            new Generator("rs", "rhythmSieve"),
            new Generator("mp", "markovPulse"),
            new Generator("mra", "markovRhythmAnalysis"),
            new Generator("irg", "iterateRhythmGroup"),
            new Generator("irw", "iterateRhythmWindow"),
            new Generator("irh", "iterateRhythmHold"));

    private RhythmGenerators() {
    }

    public static List<Generator> rhythmGroup() {
        return RHYTHM;
    }

    public static List<double[]> sample(String acronym) {
        CompatRandom parameter = CompatRandom.seed(7);
        CompatRandom texture = CompatRandom.seed(7);
        List<double[]> series = series(acronym, parameter, texture);
        if (series.size() < 6) {
            throw new IllegalStateException(acronym + " produced " + series.size());
        }
        return series.subList(0, 6);
    }

    public static Check check(Path golden) throws IOException {
        int pass = 0;
        List<String> failed = new ArrayList<>();
        for (String line : Files.readAllLines(golden, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] cells = line.split("\t");
            List<double[]> got = sample(cells[0]);
            boolean ok = true;
            for (int i = 0; i < 6; i++) {
                double[] expect = parseTriple(cells[i + 1]);
                if (!same(expect, got.get(i))) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                pass++;
            } else {
                failed.add(cells[0] + " got " + text(got));
            }
        }
        return new Check(pass, failed.size(), failed);
    }

    private static List<double[]> series(String acronym, CompatRandom parameter, CompatRandom texture) {
        return switch (acronym) {
            case "cs" -> repeat(new double[] {1, 0.999, 1});
            case "cst" -> repeat(new double[] {1, 0.5, 1});
            case "pt" -> repeat(sound(4, 1, 1, 0.75));
            case "ba" -> repeat(sound(3, 2, 1, 1.6));
            case "l" -> cycle(List.of(
                    sound(3, 1, 1, 0.94),
                    sound(3, 2, 1, 0.94),
                    sound(4, 1, 0, 0.94)));
            case "ps" -> accented(repeat(sound(2, 1, 1, 0.94)), binary("3|4", 12));
            case "rs" -> accented(cycle(List.of(sound(3, 1, 1, 0.94), sound(4, 1, 1, 0.94))), binary("3|4", 12));
            case "mp" -> markov(parameter, List.of(
                    sound(3, 1, 1, 1),
                    sound(2, 1, 1, 1),
                    sound(3, 2, 0, 1)), List.of(3.0, 4.0, 1.0));
            case "mra" -> markov(parameter, List.of(
                    sound(4, 1, 1, 1),
                    sound(2, 1, 1, 1),
                    sound(4, 1, 0, 1)), List.of(2.0, 2.0, 2.0));
            case "gr" -> cycle(GaRhythm.bundle(parameter));
            case "irg" -> iterateGroup();
            case "irw" -> iterateWindow();
            case "irh" -> iterateHold(texture);
            default -> throw new IllegalArgumentException(acronym);
        };
    }

    private static List<double[]> iterateGroup() {
        List<double[]> loop = List.of(sound(4, 1, 1, 0.94), sound(2, 1, 0, 0.94));
        int[] control = {1, -1, 2};
        int loopAt = 0;
        int controlAt = 0;
        List<double[]> buffer = new ArrayList<>();
        List<double[]> out = new ArrayList<>();
        for (int n = 0; n < 6; n++) {
            int guard = 0;
            while (buffer.isEmpty()) {
                int q = control[controlAt % control.length];
                controlAt++;
                if (q < 0) {
                    for (int skip = 0; skip < -q; skip++) {
                        loopAt++;
                    }
                } else if (q > 0) {
                    double[] pulse = loop.get(loopAt % loop.size());
                    loopAt++;
                    for (int copy = 0; copy < q; copy++) {
                        buffer.add(pulse);
                    }
                }
                if (++guard > 16 && buffer.isEmpty()) {
                    buffer.add(loop.get(loopAt % loop.size()));
                    loopAt++;
                }
            }
            out.add(buffer.remove(0));
        }
        return out;
    }

    private static List<double[]> iterateWindow() {
        List<double[]> loops = List.of(sound(4, 1, 1, 0.94), sound(2, 1, 1, 0.94));
        int[] count = {2, 1};
        int pos = 0;
        int countAt = 0;
        List<double[]> buffer = new ArrayList<>();
        List<double[]> out = new ArrayList<>();
        for (int n = 0; n < 6; n++) {
            if (buffer.isEmpty()) {
                int which = pos % loops.size();
                pos++;
                int q = count[countAt % count.length];
                countAt++;
                if (q > 0) {
                    for (int i = 0; i < q; i++) {
                        buffer.add(loops.get(which));
                    }
                }
            }
            out.add(buffer.remove(0));
        }
        return out;
    }

    private static List<double[]> iterateHold(CompatRandom texture) {
        List<double[]> buffer = new ArrayList<>();
        int selector = 0;
        int eventCount = 0;
        int eventTotal = 0;
        Integer refresh = null;
        List<double[]> out = new ArrayList<>();
        for (int n = 0; n < 6; n++) {
            boolean due = refresh != null && eventCount >= refresh && refresh != 0;
            if (eventTotal == 0 || due) {
                buffer = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    double sustain = ValueMath.denorm(texture.random(), 0.5, 1.5);
                    buffer.add(sound(4, 1, 1, sustain));
                }
                selector = 0;
                eventCount = 0;
            }
            if (eventCount == 0 || eventTotal == 0) {
                refresh = 2;
            }
            eventCount++;
            eventTotal++;
            out.add(buffer.get(selector));
            selector++;
            if (selector == buffer.size()) {
                selector = 0;
            }
        }
        return out;
    }

    private static List<int[]> binary(String logic, int length) {
        List<Integer> hits = ValueMath.sieve(logic, 0, length - 1);
        List<int[]> bits = new ArrayList<>();
        for (int z = 0; z < length; z++) {
            bits.add(new int[] {hits.contains(z) ? 1 : 0});
        }
        return bits;
    }

    private static List<double[]> accented(List<double[]> pulses, List<int[]> bits) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double[] pulse = pulses.get(i).clone();
            pulse[2] = bits.get(i)[0];
            out.add(pulse);
        }
        return out;
    }

    private static List<double[]> markov(CompatRandom parameter, List<double[]> pulses, List<Double> weights) {
        List<ValueMath.Bound> bounds = ValueMath.unitBoundaryProportion(weights);
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int pick = ValueMath.unitBoundaryPos(parameter.random(), bounds);
            out.add(pulses.get(pick));
        }
        return out;
    }

    private static double[] sound(double div, double mult, double acc, double sus) {
        double dur = (0.5 / div) * mult;
        return new double[] {dur, dur * sus, acc};
    }

    private static List<double[]> repeat(double[] pulse) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            out.add(pulse.clone());
        }
        return out;
    }

    private static List<double[]> cycle(List<double[]> pulses) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            out.add(pulses.get(i % pulses.size()).clone());
        }
        return out;
    }

    private static boolean same(double[] expect, double[] got) {
        return OmdeGenerators.near(expect[0], got[0])
                && OmdeGenerators.near(expect[1], got[1])
                && OmdeGenerators.near(expect[2], got[2]);
    }

    private static double[] parseTriple(String text) {
        String body = text.substring(1, text.length() - 1);
        String[] parts = body.split(",");
        return new double[] {
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim())
        };
    }

    private static String text(List<double[]> pulses) {
        StringBuilder out = new StringBuilder();
        for (double[] pulse : pulses) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append('(').append(pulse[0]).append(", ").append(pulse[1]).append(", ").append(pulse[2]).append(')');
        }
        return out.toString();
    }

    /**
     * Breeds a small population of rhythms. Low fitness is a closer match.
     * The random draws are the parameter stream, the same module {@code random}
     * the rhythm generator reads while it is built.
     */
    private static final class GaRhythm {
        private GaRhythm() {
        }

        static List<double[]> bundle(CompatRandom random) {
            List<Gene> fit = List.of(new Gene(3, 1, 1), new Gene(3, 1, 1), new Gene(6, 1, 1));
            int pop = 4;
            double beat = 0.5;
            double mutation = 0.06;
            double crossover = 0.70;
            Chromosome[] population = new Chromosome[pop];
            Chromosome[] next = new Chromosome[pop];
            for (int i = 0; i < pop; i++) {
                population[i] = Chromosome.random(fit, mutation, beat, true, random);
                next[i] = Chromosome.random(fit, mutation, beat, true, random);
            }
            int best = sort(population);
            List<Chromosome> found = new ArrayList<>();
            for (int generation = 0; generation < 40; generation++) {
                best = step(population, next, fit, beat, mutation, crossover, 0.01, random);
                Chromosome winner = population[best];
                if (!contains(found, winner.bits)) {
                    found.add(winner.copy());
                }
            }
            found.sort(Comparator.comparingDouble(item -> item.fitness));
            List<Chromosome> unique = new ArrayList<>();
            for (Chromosome candidate : found) {
                boolean fresh = true;
                for (Chromosome kept : unique) {
                    if (matches(candidate.bits, kept.bits)) {
                        fresh = false;
                        break;
                    }
                }
                if (fresh) {
                    unique.add(candidate);
                }
            }
            unique.sort(Comparator.comparingDouble(item -> item.fitness));
            List<double[]> pulses = new ArrayList<>();
            for (Chromosome rhythm : unique) {
                for (Gene gene : rhythm.bits) {
                    pulses.add(sound(gene.div, gene.mult, gene.stat, 0.94));
                }
            }
            return pulses;
        }

        private static int step(Chromosome[] population, Chromosome[] next, List<Gene> fit, double beat,
                double mutation, double crossover, double elite, CompatRandom random) {
            int cutoff = (int) Py.round(elite * population.length);
            int i = 0;
            int eliteAt = 0;
            while (i < population.length) {
                if (i < cutoff) {
                    next[i] = population[sortIndex(population, eliteAt)].copy();
                    eliteAt++;
                    i++;
                    continue;
                }
                Chromosome mom = parent(population, random);
                Chromosome dad = parent(population, random);
                if (sameBits(mom.bits, dad.bits)) {
                    int tries = 0;
                    while (sameBits(mom.bits, dad.bits) && tries <= 20) {
                        dad = parent(population, random);
                        tries++;
                    }
                    if (sameBits(mom.bits, dad.bits)) {
                        dad = population[random.randint(0, population.length - 1)];
                    }
                }
                if (i < population.length - 1) {
                    Chromosome son = Chromosome.random(fit, mutation, beat, false, random);
                    Chromosome daughter = Chromosome.random(fit, mutation, beat, false, random);
                    mate(dad, mom, son, daughter, crossover, random);
                    son.mutate(false, random);
                    son.score(beat, fit);
                    daughter.mutate(false, random);
                    daughter.score(beat, fit);
                    next[i] = son;
                    next[i + 1] = daughter;
                    i += 2;
                } else {
                    Chromosome son = Chromosome.random(fit, mutation, beat, false, random);
                    mate(dad, mom, son, null, crossover, random);
                    son.mutate(false, random);
                    son.score(beat, fit);
                    next[i] = son;
                    i++;
                }
            }
            for (int n = 0; n < population.length; n++) {
                population[n] = next[n].copy();
            }
            return sort(population);
        }

        private static void mate(Chromosome dad, Chromosome mom, Chromosome first, Chromosome second,
                double crossover, CompatRandom random) {
            if (crosses(crossover, random)) {
                int[] loci = loci(dad.bits.size(), random);
                List<Gene> da = slice(dad.bits, 0, loci[0]);
                List<Gene> db = slice(dad.bits, loci[0], loci[1]);
                List<Gene> dc = slice(dad.bits, loci[1], dad.bits.size());
                List<Gene> ma = slice(mom.bits, 0, loci[0]);
                List<Gene> mb = slice(mom.bits, loci[0], loci[1]);
                List<Gene> mc = slice(mom.bits, loci[1], mom.bits.size());
                if (second != null) {
                    first.bits = join(da, mb, dc);
                    second.bits = join(ma, db, mc);
                } else if (random.randint(0, 1) == 1) {
                    first.bits = join(da, mb, dc);
                } else {
                    first.bits = join(ma, db, mc);
                }
            } else if (second != null) {
                first.bits = copyBits(dad.bits);
                second.bits = copyBits(mom.bits);
            } else {
                first.bits = copyBits(dad.bits);
            }
        }

        private static boolean crosses(double rate, CompatRandom random) {
            return random.randint(0, 9999) / 10000.0 < rate;
        }

        private static int[] loci(int length, CompatRandom random) {
            if (length <= 1) {
                return new int[] {0, 0};
            }
            if (length == 2) {
                return new int[] {0, 1};
            }
            int left = random.randint(0, length - 1);
            int right;
            do {
                right = random.randint(0, length - 1);
            } while (right == left);
            return left < right ? new int[] {left, right} : new int[] {right, left};
        }

        private static Chromosome parent(Chromosome[] population, CompatRandom random) {
            double scale = 10;
            double worst = 0;
            for (Chromosome item : population) {
                if (item.fitness >= worst) {
                    worst = item.fitness;
                }
            }
            worst *= 1.5;
            int sum = 0;
            for (Chromosome item : population) {
                sum += (int) Py.round((worst - item.fitness) * scale);
            }
            int stop = random.randint(0, sum);
            int cursor = 0;
            List<Integer> keys = new ArrayList<>();
            for (int i = 0; i < population.length; i++) {
                keys.add(i);
            }
            int last = 0;
            while (!keys.isEmpty()) {
                int key = random.choice(keys);
                keys.remove(Integer.valueOf(key));
                int width = (int) Py.round((worst - population[key].fitness) * scale);
                int localStop = cursor + width;
                if (stop >= cursor && stop <= localStop) {
                    return population[key];
                }
                cursor = localStop;
                last = key;
            }
            return population[last];
        }

        private static int sort(Chromosome[] population) {
            List<int[]> order = new ArrayList<>();
            for (int i = 0; i < population.length; i++) {
                order.add(new int[] {i});
            }
            order.sort(Comparator
                    .comparingDouble((int[] item) -> population[item[0]].fitness)
                    .thenComparingInt(item -> item[0]));
            return order.get(0)[0];
        }

        private static int sortIndex(Chromosome[] population, int rank) {
            List<int[]> order = new ArrayList<>();
            for (int i = 0; i < population.length; i++) {
                order.add(new int[] {i});
            }
            order.sort(Comparator
                    .comparingDouble((int[] item) -> population[item[0]].fitness)
                    .thenComparingInt(item -> item[0]));
            return order.get(rank)[0];
        }

        private static boolean contains(List<Chromosome> found, List<Gene> bits) {
            for (Chromosome item : found) {
                if (sameBits(item.bits, bits)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean sameBits(List<Gene> left, List<Gene> right) {
            if (left.size() != right.size()) {
                return false;
            }
            for (int i = 0; i < left.size(); i++) {
                if (!left.get(i).same(right.get(i))) {
                    return false;
                }
            }
            return true;
        }

        private static boolean matches(List<Gene> left, List<Gene> right) {
            if (left.size() != right.size()) {
                return false;
            }
            int hits = 0;
            for (int i = 0; i < left.size(); i++) {
                double gap = Math.abs(left.get(i).mult / left.get(i).div - right.get(i).mult / right.get(i).div);
                if (gap < 0.00001 && left.get(i).stat == right.get(i).stat) {
                    hits++;
                }
            }
            return hits == left.size();
        }

        private static List<Gene> slice(List<Gene> bits, int from, int to) {
            List<Gene> out = new ArrayList<>();
            for (int i = from; i < to; i++) {
                out.add(bits.get(i).copy());
            }
            return out;
        }

        private static List<Gene> copyBits(List<Gene> bits) {
            return slice(bits, 0, bits.size());
        }

        private static List<Gene> join(List<Gene> a, List<Gene> b, List<Gene> c) {
            List<Gene> out = new ArrayList<>();
            out.addAll(a);
            out.addAll(b);
            out.addAll(c);
            return out;
        }
    }

    private static final class Chromosome {
        List<Gene> bits = new ArrayList<>();
        double fitness;
        double mutation;
        double duration;

        static Chromosome random(List<Gene> fit, double mutation, double beat, boolean initCheck, CompatRandom random) {
            Chromosome made = new Chromosome();
            made.mutation = mutation;
            List<Gene> pool = new ArrayList<>();
            for (Gene gene : fit) {
                boolean seen = false;
                for (Gene have : pool) {
                    if (have.same(gene)) {
                        seen = true;
                        break;
                    }
                }
                if (!seen) {
                    pool.add(gene);
                }
            }
            for (int i = 0; i < fit.size(); i++) {
                made.bits.add(random.choice(pool).copy());
            }
            made.score(beat, fit);
            if (initCheck && made.fitness <= 0.1) {
                made.mutate(true, random);
                made.score(beat, fit);
            }
            return made;
        }

        void mutate(boolean force, CompatRandom random) {
            double cutoff = force ? 1.0 : mutation;
            if (random.random() > cutoff) {
                return;
            }
            String kind = random.choice(List.of(
                    "ratioEq", "divisorMutate", "multiplierMutate", "flipNoteRest", "inversion"));
            switch (kind) {
                case "inversion" -> invert(random);
                case "ratioEq" -> scale(random);
                case "multiplierMutate" -> shiftMult(random);
                case "divisorMutate" -> shiftDiv(random);
                case "flipNoteRest" -> flip(random);
                default -> throw new IllegalStateException(kind);
            }
        }

        void score(double beat, List<Gene> fit) {
            duration = seconds(beat, bits);
            double[] own = notes(beat, bits);
            double[] goal = notes(beat, fit);
            double fitDuration = seconds(beat, fit);
            double restValue = gap(own[1], goal[1]);
            double noteValue = gap(own[0], goal[0]);
            double durValue = gap(duration, fitDuration);
            int matchGene = 0;
            int matchValue = 0;
            for (int i = 0; i < bits.size(); i++) {
                double durDif = Math.abs(one(beat, bits.get(i)) - one(beat, fit.get(i)));
                if (durDif < 0.00001 && bits.get(i).stat == fit.get(i).stat) {
                    matchGene++;
                }
                if (durDif < 0.00001) {
                    matchValue++;
                }
            }
            double geneScore = (bits.size() - matchGene) * (duration / bits.size());
            double valueScore = (bits.size() - matchValue) * (duration / bits.size());
            fitness = durValue * 2.33 + restValue * 1.5 + noteValue * 1.5 + geneScore + valueScore * 0.666;
        }

        Chromosome copy() {
            Chromosome made = new Chromosome();
            made.mutation = mutation;
            made.fitness = fitness;
            made.duration = duration;
            made.bits = GaRhythm.copyBits(bits);
            return made;
        }

        private void invert(CompatRandom random) {
            int[] span = GaRhythm.loci(bits.size(), random);
            List<Gene> segment = GaRhythm.slice(bits, span[0], span[1] + 1);
            java.util.Collections.reverse(segment);
            for (int i = 0; i < segment.size(); i++) {
                bits.set(span[0] + i, segment.get(i));
            }
        }

        private void scale(CompatRandom random) {
            int locus = random.randint(0, bits.size() - 1);
            Gene gene = bits.get(locus).copy();
            if (gene.div % 2 == 0 && gene.mult % 2 == 0) {
                double factor = random.randint(0, 1) == 1 ? 0.5 : 2;
                gene.div *= factor;
                gene.mult *= factor;
            } else if (gene.div % 3 == 0 && gene.mult % 3 == 0) {
                double factor = random.randint(0, 1) == 1 ? (1.0 / 3.0) : 3;
                gene.div *= factor;
                gene.mult *= factor;
            } else if (random.randint(0, 1) == 1) {
                gene.div *= 2;
                gene.mult *= 2;
            } else {
                gene.div *= 3;
                gene.mult *= 3;
            }
            gene.listed = true;
            bits.set(locus, gene);
        }

        private void shiftMult(CompatRandom random) {
            int locus = random.randint(0, bits.size() - 1);
            Gene gene = bits.get(locus).copy();
            if (gene.mult == 1) {
                gene.mult += pick(gene.div, random);
            } else if (random.randint(0, 1) != 0) {
                gene.mult += gene.div == 1 ? 1 : pick(gene.div, random);
            } else {
                gene.mult -= gene.mult == 1 ? 1 : pick(gene.mult, random);
            }
            gene.listed = true;
            bits.set(locus, gene);
        }

        private void shiftDiv(CompatRandom random) {
            int locus = random.randint(0, bits.size() - 1);
            Gene gene = bits.get(locus).copy();
            if (gene.div == 1) {
                gene.div += 1;
            } else if (random.randint(0, 1) != 0) {
                gene.div += 1;
            } else {
                gene.div -= 1;
            }
            gene.listed = true;
            bits.set(locus, gene);
        }

        private void flip(CompatRandom random) {
            int locus = random.randint(0, bits.size() - 1);
            Gene gene = bits.get(locus).copy();
            gene.stat = gene.stat == 1 ? 0 : 1;
            gene.listed = true;
            bits.set(locus, gene);
        }

        private static int pick(double bound, CompatRandom random) {
            int limit = (int) bound;
            if (limit <= 1) {
                return 1;
            }
            List<Integer> span = new ArrayList<>();
            for (int value = 1; value < limit; value++) {
                span.add(value);
            }
            return random.choice(span);
        }

        private static double gap(double left, double right) {
            double difference = Math.abs(left - right);
            return difference < 0.00001 ? 0 : difference;
        }

        private static double seconds(double beat, List<Gene> genes) {
            double sum = 0;
            for (Gene gene : genes) {
                sum += one(beat, gene);
            }
            return sum;
        }

        private static double[] notes(double beat, List<Gene> genes) {
            double notes = 0;
            double rests = 0;
            for (Gene gene : genes) {
                double dur = one(beat, gene);
                if (gene.stat == 1) {
                    notes += dur;
                } else {
                    rests += dur;
                }
            }
            return new double[] {notes, rests};
        }

        private static double one(double beat, Gene gene) {
            return (beat / gene.div) * gene.mult;
        }
    }

    private static final class Gene {
        double div;
        double mult;
        int stat;
        boolean listed;

        Gene(double div, double mult, int stat) {
            this.div = div;
            this.mult = mult;
            this.stat = stat;
        }

        Gene copy() {
            Gene made = new Gene(div, mult, stat);
            made.listed = listed;
            return made;
        }

        boolean same(Gene other) {
            return listed == other.listed && div == other.div && mult == other.mult && stat == other.stat;
        }
    }
}
