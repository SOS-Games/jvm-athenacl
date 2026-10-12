// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.compat;

import java.util.List;

/**
 * One Python literal: a number, a string, {@code None}, a bool, or a list or tuple.
 * A one-element tuple keeps its trailing comma when it is printed back.
 */
public sealed interface PyValue permits PyValue.None, PyValue.Bool, PyValue.IntNum, PyValue.FloatNum,
        PyValue.Str, PyValue.Seq {

    record None() implements PyValue {
    }

    record Bool(boolean value) implements PyValue {
    }

    record IntNum(long value) implements PyValue {
    }

    record FloatNum(double value) implements PyValue {
    }

    record Str(String value) implements PyValue {
    }

    record Seq(List<PyValue> items, boolean tuple) implements PyValue {
        public Seq {
            items = List.copyOf(items);
        }
    }

    static String repr(PyValue value) {
        return switch (value) {
            case None ignored -> "None";
            case Bool bool -> bool.value() ? "True" : "False";
            case IntNum num -> Long.toString(num.value());
            case FloatNum num -> Double.toString(num.value());
            case Str str -> quote(str.value());
            case Seq seq -> seqText(seq);
        };
    }

    private static String seqText(Seq seq) {
        StringBuilder text = new StringBuilder();
        text.append(seq.tuple() ? '(' : '[');
        for (int i = 0; i < seq.items().size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(repr(seq.items().get(i)));
        }
        if (seq.tuple() && seq.items().size() == 1) {
            text.append(',');
        }
        text.append(seq.tuple() ? ')' : ']');
        return text.toString();
    }

    private static String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
