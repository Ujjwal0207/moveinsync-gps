package com.moveinsync.distance.engine;

import com.moveinsync.distance.model.GpsPing;

import java.util.List;

/**
 * Interface for ingesting and cleaning raw GPS device pings.
 */
public interface GpsStreamCleaner {

    CleanerResult clean(List<GpsPing> rawPings);
}
