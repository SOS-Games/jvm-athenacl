// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif;

import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;

import java.util.ArrayList;
import java.util.List;

/**
 * How often a motif comes back inside eight bars. A bar reuses a motif when another bar
 * in that window carries the same motif id. An idea played every bar is {@code 1.0}.
 */
public final class MotifReuse {
    private MotifReuse() {
    }

    public static Double per8bars(Score score) {
        int last = Math.min(8, Math.max(score.bars(), 1));
        List<String> ids = new ArrayList<>();
        for (int bar = 1; bar <= last; bar++) {
            String id = null;
            for (NoteEvent note : score.notes()) {
                if (note.bar() == bar && note.motif() != null && !note.motif().isBlank()) {
                    id = base(note.motif());
                    break;
                }
            }
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        int reused = 0;
        for (int i = 0; i < ids.size(); i++) {
            for (int j = 0; j < ids.size(); j++) {
                if (i != j && ids.get(i).equals(ids.get(j))) {
                    reused++;
                    break;
                }
            }
        }
        return reused / (double) ids.size();
    }

    public static String base(String motif) {
        int colon = motif.indexOf(':');
        return colon < 0 ? motif : motif.substring(0, colon);
    }
}
