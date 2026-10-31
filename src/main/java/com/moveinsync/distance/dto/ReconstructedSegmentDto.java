package com.moveinsync.distance.dto;

import com.moveinsync.distance.model.ReconstructedSegment;
import com.moveinsync.distance.model.SegmentRule;

import java.time.Instant;

public record ReconstructedSegmentDto(
        int segmentIndex,
        double startLatitude,
        double startLongitude,
        double endLatitude,
        double endLongitude,
        Instant startTime,
        Instant endTime,
        long elapsedSeconds,
        double distanceKm,
        double impliedSpeedKmh,
        SegmentRule rule,
        String explanation
) {
    public static ReconstructedSegmentDto fromModel(ReconstructedSegment model) {
        return new ReconstructedSegmentDto(
                model.segmentIndex(),
                model.startCoordinate().latitude(),
                model.startCoordinate().longitude(),
                model.endCoordinate().latitude(),
                model.endCoordinate().longitude(),
                model.startTime(),
                model.endTime(),
                model.elapsedSeconds(),
                model.distanceKm(),
                model.impliedSpeedKmh(),
                model.rule(),
                model.explanation()
        );
    }
}
