package com.moveinsync.distance.engine;

import com.moveinsync.distance.model.*;

import java.util.*;

/**
 * Standard implementation of TrajectoryInterpolator.
 * Injects business anchors into the timeline and performs deterministic sorting and waypoint deduplication.
 */
public class DefaultTrajectoryInterpolator implements TrajectoryInterpolator {

    @Override
    public List<AnchorPoint> interpolateTrajectory(
            List<GpsPing> cleanedPings,
            TripEvidence evidence,
            boolean includeNoShowAnchors
    ) {
        List<AnchorPoint> waypoints = new ArrayList<>();

        // 1. Add cleaned GPS pings
        if (cleanedPings != null) {
            for (GpsPing ping : cleanedPings) {
                if (ping != null) {
                    waypoints.add(new AnchorPoint(
                            ping.coordinate(),
                            ping.timestamp(),
                            AnchorSource.GPS_PING,
                            "GPS Fix: " + ping.pingId()
                    ));
                }
            }
        }

        // 2. Add employee milestones (pickups, drops, and conditionally no-shows)
        if (evidence.milestones() != null) {
            for (EmployeeMilestone milestone : evidence.milestones()) {
                if (milestone != null) {
                    Optional<AnchorPoint> anchorOpt = milestone.resolveAnchor(includeNoShowAnchors);
                    anchorOpt.ifPresent(waypoints::add);
                }
            }
        }

        // 3. Add office destination anchor
        if (evidence.officeLocation() != null && evidence.tripEndTime() != null) {
            AnchorPoint officeAnchor = evidence.officeLocation().resolveOfficeAnchor(
                    evidence.actualTripEndPoint(),
                    evidence.tripEndTime()
            );
            waypoints.add(officeAnchor);
        }

        if (waypoints.isEmpty()) {
            return List.of();
        }

        // 4. Sort chronologically. Ties broken by business importance: business anchors first over GPS pings
        waypoints.sort((a, b) -> {
            int cmp = a.timestamp().compareTo(b.timestamp());
            if (cmp != 0) return cmp;
            // Business anchors before GPS pings
            boolean aIsGps = (a.source() == AnchorSource.GPS_PING);
            boolean bIsGps = (b.source() == AnchorSource.GPS_PING);
            if (aIsGps != bIsGps) {
                return aIsGps ? 1 : -1;
            }
            return a.compareTo(b);
        });

        // 5. Deduplicate identical timestamps or zero-movement consecutive points
        List<AnchorPoint> consolidated = new ArrayList<>(waypoints.size());
        for (AnchorPoint wp : waypoints) {
            if (consolidated.isEmpty()) {
                consolidated.add(wp);
            } else {
                AnchorPoint last = consolidated.get(consolidated.size() - 1);
                if (last.timestamp().equals(wp.timestamp())) {
                    // Same timestamp: if the existing is a GPS ping and new is a business milestone, replace it
                    if (last.source() == AnchorSource.GPS_PING && wp.source() != AnchorSource.GPS_PING) {
                        consolidated.set(consolidated.size() - 1, wp);
                    }
                    // Otherwise discard duplicate timestamp
                } else if (last.coordinate().equals(wp.coordinate()) &&
                        last.coordinate().haversineDistanceMeters(wp.coordinate()) < 0.1) {
                    // Exact same location: if the previous is GPS ping and current is business anchor, replace it
                    if (last.source() == AnchorSource.GPS_PING && wp.source() != AnchorSource.GPS_PING) {
                        consolidated.set(consolidated.size() - 1, wp);
                    } else if (wp.source() == AnchorSource.GPS_PING) {
                        // Skip duplicate GPS ping at zero movement
                        continue;
                    } else {
                        consolidated.add(wp);
                    }
                } else {
                    consolidated.add(wp);
                }
            }
        }

        return Collections.unmodifiableList(consolidated);
    }
}
