# Instrument tags

`kit analyze` writes a profile. `kit describe` reads it. A tag is a measured label. A verdict is ok, caution, or bad.

| Tag | When it is set |
| --- | --- |
| bright | Spectral centroid sits at least twice the sounding pitch. |
| warm | Centroid sits at or under 1.4 times the sounding pitch. |
| pad | The attack takes 80 ms or more. |
| plucky | The attack is quick and the note falls away within 400 ms. |
| noisy | The spectrum is flat enough that a pitch estimate is not trusted. |

`bad` means a hard flag: clipping, a loop click, partials that are not a harmonic series, or a pitch error past 30 cents. A noisy spectrum does not take the pitch or inharmonicity flags. `caution` means a softer flag, such as a pitch error between 15 and 30 cents, or a velocity change under 3 LU. An `analysis:` block on the kit instrument replaces the measured verdict and is marked `source=override`.

Thresholds live in `instruments/analysis.yaml`. They are starting values. The CC0 bank in `samples/kits/analysis/` is what they are checked against.
