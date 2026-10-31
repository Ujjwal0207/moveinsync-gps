package com.moveinsync.distance.model;

/**
 * Rules that determine how segment distance is calculated and audited.
 */
public enum SegmentRule {
    /**
     * Consecutive fixes less than 70s apart at plausible speed. Straight-line (Haversine) applied.
     */
    SHORT_GAP_AERIAL,

    /**
     * Long gap where average speed is under 5 km/h (cab was parked/idling). Straight-line applied.
     */
    PARKED,

    /**
     * Real gap with significant distance or time; driving distance queried from routing provider.
     */
    ROUTED,

    /**
     * Routing provider output exceeded 100 km, or implied speed exceeded limit while aerial * 1.4 also exceeded limit.
     */
    REJECTED_SPEED,

    /**
     * Routing provider unavailable / circuit tripped. Straight-line * 1.4 applied. Trip flagged DEGRADED.
     */
    FALLBACK_DEGRADED
}
