package com.moveinsync.distance.engine;

import com.moveinsync.distance.model.AnchorPoint;
import com.moveinsync.distance.model.GpsPing;
import com.moveinsync.distance.model.TripEvidence;

import java.util.List;

/**
 * Merges cleaned GPS fixes with business anchor points (employee drops/pickups, no-shows, office destination).
 */
public interface TrajectoryInterpolator {

    /**
     * Ingests cleaned GPS pings and trip evidence, returning an ordered trajectory of waypoints.
     *
     * @param cleanedPings sanitized GPS stream
     * @param evidence trip milestones, office destination, and trip bounds
     * @param includeNoShowAnchors configuration flag to include or ignore no-shows
     * @return chronologically sorted, deduplicated list of trajectory anchor points
     */
    List<AnchorPoint> interpolateTrajectory(
            List<GpsPing> cleanedPings,
            TripEvidence evidence,
            boolean includeNoShowAnchors
    );
}
