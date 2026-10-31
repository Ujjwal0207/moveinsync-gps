package com.moveinsync.distance.engine;

import com.moveinsync.distance.model.AnchorPoint;
import com.moveinsync.distance.model.ReconstructedSegment;
import com.moveinsync.distance.provider.RoutingProvider;

import java.util.List;

/**
 * Reconstructs billable distance for each trajectory segment according to MoveInSync gap rules.
 */
public interface DistanceEngine {

    /**
     * Segments the trajectory and computes explainable billable distance for every hop.
     *
     * @param trajectory ordered waypoints
     * @param routingProvider routing provider instance
     * @return list of reconstructed, fully annotated segments
     */
    List<ReconstructedSegment> evaluateSegments(
            List<AnchorPoint> trajectory,
            RoutingProvider routingProvider
    );
}
