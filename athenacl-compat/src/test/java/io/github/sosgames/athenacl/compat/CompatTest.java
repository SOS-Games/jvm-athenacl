// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatTest {
    @Test
    void seed7MatchesCpythonRandom() {
        CompatRandom random = CompatRandom.seed(7);
        List<String> draws = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            draws.add(Py.repr(random.random()));
        }
        assertEquals(List.of(
                "0.32383276483316237",
                "0.15084917392450192",
                "0.6509344730398537",
                "0.07243628666754276",
                "0.5358820043066892"), draws);
    }

    @Test
    void seed7MatchesCpythonHelpers() {
        CompatRandom random = CompatRandom.seed(7);
        List<Integer> rolls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            rolls.add(random.randint(1, 6));
        }
        assertEquals(List.of(3, 2, 4, 6, 1, 1, 5, 1), rolls);

        random = CompatRandom.seed(7);
        List<String> letters = List.of("a", "b", "c", "d");
        List<String> picked = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            picked.add(random.choice(letters));
        }
        assertEquals(List.of("c", "b", "d", "a", "a", "a"), picked);

        random = CompatRandom.seed(7);
        List<Integer> items = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        random.shuffle(items);
        assertEquals(List.of(5, 1, 4, 2, 3), items);
    }

    @Test
    void seed7MatchesCpythonContinuous() {
        CompatRandom random = CompatRandom.seed(7);
        assertEquals(-0.2558802884476004, random.gauss(0, 1), 1e-15);
        assertEquals(0.511431512516514, random.gauss(0, 1), 1e-15);
        random = CompatRandom.seed(7);
        assertEquals(0.2608765628232029, random.expovariate(1.5), 1e-15);
        random = CompatRandom.seed(7);
        assertEquals(0.17522206514459931, random.betavariate(2, 5), 1e-15);
    }

    @Test
    void roundAndFloorMatchPython() {
        assertEquals(2, Py.round(2.5));
        assertEquals(4, Py.round(3.5));
        assertEquals(-2, Py.round(-1.5));
        assertEquals(1.2, Py.round(1.25, 1));
        assertEquals(-3, Py.floorDiv(-7, 3));
        assertEquals(2, Py.floorMod(-7, 3));
    }

    @Test
    void restringulatorMatchesTheDoctests() {
        assertEquals("'this','is','a','test'", PoArgs.restringulate("this, is, a , test"));
        assertEquals("'this',3,12.5,['test',['a',3,4]],'test'",
                PoArgs.restringulate("this, 3, 12.5, (test, (a, 3, 4)), test"));
        assertEquals("'pt',['c',2],['bg','oc',[7,5,2,1,1]],['c',1]",
                PoArgs.restringulate("pt,(c,2),(bg,oc,(7,5,2,1,1)),(c,1)"));
    }

    @Test
    void projectLiteralKeepsAOneElementTuple() {
        PyValue value = PyLiteral.parse("[(0,)]");
        assertEquals("[(0,)]", PyValue.repr(value));
    }

    @Test
    void parserFuzzRoundTrips() {
        CompatRandom random = CompatRandom.seed(7);
        for (int i = 0; i < 400; i++) {
            PyValue value = randomValue(random, 0);
            String text = PyValue.repr(value);
            assertEquals(text, PyValue.repr(PyLiteral.parse(text)));
        }
        for (int i = 0; i < 200; i++) {
            String source = randomPo(random, 0);
            String quoted = PoArgs.restringulate(source);
            String again = row(PoArgs.parse(source));
            assertEquals(again, row(PyLiteral.parseRow(quoted)));
        }
        assertThrows(IllegalArgumentException.class, () -> PyLiteral.parse("("));
        assertThrows(IllegalArgumentException.class, () -> PyLiteral.parse("'open"));
    }

    @Test
    void acronymIgnoresCase() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("oc", "orderedCyclic");
        names.put("rw", "randomWalk");
        assertEquals("orderedCyclic", Acronyms.expand("oc", names));
        assertEquals("orderedCyclic", Acronyms.expand("OC", names));
        assertEquals("randomWalk", Acronyms.expand("randomwalk", names));
        assertEquals("", Acronyms.expand("nope", names));
    }

    @Test
    void corpusHasTheThirtyOneDemos() throws Exception {
        List<Path> scripts = DemoCorpus.scripts(Path.of("conformance/demos"));
        assertEquals(31, scripts.size());
        for (Path script : scripts) {
            assertTrue(DemoCorpus.commands(script) > 0, script.toString());
        }
    }

    private static String row(List<PyValue> values) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(PyValue.repr(values.get(i)));
        }
        return text.toString();
    }

    private static PyValue randomValue(CompatRandom random, int depth) {
        int kind = random.nextInt(depth >= 3 ? 4 : 5);
        return switch (kind) {
            case 0 -> new PyValue.IntNum(random.nextInt(30) - 5);
            case 1 -> new PyValue.FloatNum((random.nextInt(20) - 5) / 4.0);
            case 2 -> new PyValue.Str(Character.toString((char) ('a' + random.nextInt(6))));
            case 3 -> random.nextInt(2) == 0 ? new PyValue.None() : new PyValue.Bool(random.nextInt(2) == 0);
            default -> {
                int n = random.nextInt(3);
                List<PyValue> items = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    items.add(randomValue(random, depth + 1));
                }
                yield new PyValue.Seq(items, random.nextInt(2) == 0);
            }
        };
    }

    private static String randomPo(CompatRandom random, int depth) {
        int n = 1 + random.nextInt(3);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                text.append(',');
            }
            int kind = random.nextInt(depth >= 2 ? 2 : 3);
            if (kind == 0) {
                text.append(random.nextInt(12));
            } else if (kind == 1) {
                text.append((char) ('a' + random.nextInt(6)));
            } else {
                text.append('(').append(randomPo(random, depth + 1)).append(')');
            }
        }
        return text.toString();
    }
}
