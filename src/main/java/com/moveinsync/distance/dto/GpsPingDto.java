package com.moveinsync.distance.dto;

import com.moveinsync.distance.model.Coordinate;
import com.moveinsync.distance.model.GpsPing;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record GpsPingDto(
        @NotNull String pingId,
        double latitude,
        double longitude,
        @NotNull Instant timestamp,
        double accuracyMeters
) {
    public GpsPing toModel() {
        return new GpsPing(pingId, new Coordinate(latitude, longitude), timestamp, accuracyMeters);
    }

    public static GpsPingDto fromModel(GpsPing model) {
        return new GpsPingDto(
                model.pingId(),
                model.coordinate().latitude(),
                model.coordinate().longitude(),
                model.timestamp(),
                model.accuracyMeters()
        );
    }
}
