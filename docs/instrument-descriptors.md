# Instrument descriptors

A descriptor is data about an instrument. It is not a measurement. Ensemble selection, lint, and `kit describe` read descriptors. They do not render probe notes.

## Fields

| Field | Meaning |
| --- | --- |
| roles | `lead`, `pad`, `bass`, `arp`, `perc`, `drone`, `fx` |
| tags | Words from `presets/tags.yaml` |
| register | `low`, `high`, `sweetLow`, `sweetHigh` as MIDI note numbers |
| class | `pitched-sustained`, `pitched-percussive`, `unpitched-perc`, `tuned-perc`, `ensemble`, `synth` |
| loudnessTrimDb | Gain hint. Default 0 |
| avoid | When true, selection skips the instrument unless a part pins it |
| blendWith, clashWith | Instrument ids, or `tag:` plus a tag |
| genres | Optional affinity list |
| notes | Free text for a person or a later describer |

A kit may set any subset:

```yaml
instruments:
  soft_flute:
    preset: sf:generaluser/0/73
    roles: [lead]
    tags: [soft, airy, sustained, orchestral]
    register: {low: 60, high: 96, sweetLow: 67, sweetHigh: 88}
    clashWith: [tag:harsh]
    notes: "breathy, good for peaceful melodies"
```

`kit tag soft_flute avoid=true --out build/l7k/kit.yaml` writes the same kind of fields. Unknown keys and unknown tags exit 3 and name a close match. Add a tag by editing `presets/tags.yaml`. No Java change is required.

## Precedence

Each field keeps the first source that sets it:

1. Game override (`descriptors.<instrument>.<field>` on the theme)
2. Kit YAML (`source=declared`)
3. External profile, only when `descriptors.external.enabled` is true
4. `presets/gm-defaults.yaml` by program number (`source=gm-default`)

A preset with no declared fields is usable as soon as `kit scan` lists it. Drum kits use the `drums` block (bank 128).

## Ensemble

A genre `ensemble.roles` block can set `count`, `requireTags`, `preferTags` (a weight per tag), `excludeTags`, `register`, `class`, `maxPerFamily`, `pin`, and `fallback`. A state can narrow a role. A game pin names the instrument.

Selection, for each role in the order bass, pad, lead, arp, perc, then any other role:

1. Keep candidates whose roles match, that are not marked avoid, that have the required tags and class, and whose register covers the target.
2. Score preferTags, genre affinity, and how the sweet range sits in the target. Subtract declared clashes and sweet-range overlap with a lead already chosen.
3. Sort by score, then id. Draw among the top K (default 3) with the seeded generator at `seed/ensemble/<role>`.
4. If nobody is left, drop preferTags, then the register limit, then requireTags. The explain line shows `relaxed=`. If that is still empty, use `fallback` or the theme's instrument for that role. Otherwise the command exits 3 and names the role.

`ensemble select --genre fantasy --state sneaking --seed 1 --explain` prints the picks and the candidates. `theme resolve --seed` includes `source=ensemble:auto`.

## External profile

A future describer can drop a JSON file without an engine change. The loader reads it only when `descriptors.external.enabled` is true. The path is `descriptors.external.path`, or `instruments/external-profiles.json`.

```json
{
  "version": 1,
  "profiles": [
    {
      "soundfont": "<sha256 of the sf2>",
      "bank": 0,
      "program": 73,
      "source": "describer",
      "confidence": 0.8,
      "roles": ["lead"],
      "tags": ["soft", "airy"],
      "class": "pitched-sustained",
      "register": {"low": 60, "high": 96, "sweetLow": 67, "sweetHigh": 88},
      "notes": "breathy"
    }
  ]
}
```

The key is the soundfont hash, the bank, and the program. `source` and `confidence` are for the describer. The descriptor fields are the same ones a kit YAML may set. They sit between the kit declaration and the General MIDI table.

## Measured analysis

`analysis.enabled` defaults to false. `kit analyze` then exits 2: `analysis disabled; set analysis.enabled=true (experimental)`. The probe, the metrics, and the verdicts live in `io.github.sosgames.athenacl.instruments.analysis.experimental`. `gradlew analysisCheck` runs those tests with the setting turned on. Layer check does not.
