// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import java.util.List;

/**
 * The fourteen texture modules, including LineGroove. Each line is the short
 * name and the static parameters that module declares. The plan's count of
 * fourteen already matches this public list.
 */
public final class TextureModules {
    public record Module(String acronym, String name, String parameters) {
    }

    private static final List<Module> MODULES = List.of(
            new Module("da", "DroneArticulate",
                    "maxTimeOffset,levelFieldMonophonic,levelOctaveMonophonic"),
            new Module("ds", "DroneSustain",
                    "maxTimeOffset,levelFieldPolyphonic,levelOctavePolyphonic"),
            new Module("ie", "IntervalExpansion",
                    "loopWithinSet,ornamentLibrarySelect,ornamentMaxDensity,levelFieldMonophonic,levelOctaveMonophonic"),
            new Module("lc", "LineCluster",
                    "parallelMotionList,pitchSelectorControl,levelFieldPolyphonic,levelOctavePolyphonic"),
            new Module("lg", "LineGroove",
                    "parallelMotionList,pitchSelectorControl,levelFieldMonophonic,levelOctaveMonophonic"),
            new Module("lh", "LiteralHorizontal",
                    "loopWithinSet,levelFieldMonophonic,levelOctaveMonophonic"),
            new Module("lv", "LiteralVertical",
                    "loopWithinSet,maxTimeOffset,levelFieldPolyphonic,levelOctavePolyphonic,pathDurationFraction"),
            new Module("mo", "MonophonicOrnament",
                    "loopWithinSet,ornamentLibrarySelect,ornamentMaxDensity,levelFieldMonophonic,levelOctaveMonophonic"),
            new Module("tf", "TimeFill",
                    "pitchSelectorControl,levelFieldMonophonic,levelOctaveMonophonic,totalEventCount,levelEventPartition,eventDensityPartition"),
            new Module("ts", "TimeSegment",
                    "pitchSelectorControl,levelFieldMonophonic,levelOctaveMonophonic,levelEventCount,totalSegmentCount"),
            new Module("hs", "HarmonicShuffle",
                    "multisetSelectorControl,pitchSelectorControl,maxTimeOffset,levelFieldPolyphonic,levelOctavePolyphonic"),
            new Module("ha", "HarmonicAssembly",
                    "maxTimeOffset,levelFieldPolyphonic,levelOctavePolyphonic"),
            new Module("il", "InterpolateLine",
                    "pitchSelectorControl,levelFieldMonophonic,levelOctaveMonophonic,interpolationMethodControl,levelFrameDuration,parameterInterpolationControl,snapSustainTime,snapEventTime"),
            new Module("if", "InterpolateFill",
                    "pitchSelectorControl,levelFieldMonophonic,levelOctaveMonophonic,totalEventCount,levelEventPartition,eventDensityPartition,interpolationMethodControl,levelFrameDuration,parameterInterpolationControl,snapSustainTime"));

    private TextureModules() {
    }

    public static List<Module> modules() {
        return MODULES;
    }
}
