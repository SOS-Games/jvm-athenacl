# Instrument tags

`kit analyze` writes a profile. `kit describe` reads it. A tag is a measured label. A verdict is ok, caution, or bad.

| Tag | When it is set |
| --- | --- |
| bright | Spectral centroid sits at least twice the sounding pitch. |
| warm | Centroid sits at or under 1.4 times the sounding pitch. |
| pad | The attack takes 80 ms or more. |
| plucky | The attack is quick and the note falls away within 400 ms. |
| noisy | The spectrum is flat enough that a pitch estimate is not trusted. |

Each preset is classified first: pitched-sustained, pitched-percussive, unpitched-percussion, tuned-percussion, ensemble, or synth-raw. A kit line `class:` overrides that. `bad` means a hard flag for that class. Clipping is a true peak past -0.3 dBTP, not a hot level. A hot level is the tag `loud` and a gain trim. A loop click is a spike at a loop boundary after the attack. Unpitched percussion skips pitch, inharmonicity, and loop clicks. Tuned percussion skips inharmonicity and uses a wider pitch tolerance. An ensemble is rough only when the beating is far past a chorus. Pitch is compared as a pitch class; `octave_offset` is recorded and is not a failure. A noisy spectrum does not take the pitch or inharmonicity flags. `caution` means a softer flag, such as a pitch error between 15 and 30 cents, or a velocity change under 3 LU. The reason names the class rule, such as `inharmonicity@class=pitched-sustained`. An `analysis:` block on the kit instrument replaces the measured verdict and is marked `source=override`.

Thresholds live in `instruments/analysis.yaml`. They are starting values. The CC0 bank in `samples/kits/analysis/` is what they are checked against.
