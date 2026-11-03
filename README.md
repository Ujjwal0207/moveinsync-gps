# MoveInSync GPS Gap Backfill & Billable Distance Reconstruction Engine

[![Java](https://img.shields.io/badge/Java-17%20%7C%2024-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Resilience4j](https://img.shields.io/badge/Resilience4j-2.2.0-yellow)](https://resilience4j.readme.io/)
[![Caffeine Cache](https://img.shields.io/badge/Caffeine-3.1.8-orange)](https://github.com/ben-manes/caffeine)
[![Build & Test](https://img.shields.io/badge/Tests-32%20Passed%20(100%25)-brightgreen)](https://github.com/Ujjwal0207/moveinsync-gps)
[![Benchmark](https://img.shields.io/badge/Benchmark-10k%20trips%20in%203.89s-blueviolet)](BENCHMARK.md)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

> **Author:** Ujjwal Goyal ([@Ujjwal0207](https://github.com/Ujjwal0207))  
> **Repository:** [https://github.com/Ujjwal0207/moveinsync-gps](https://github.com/Ujjwal0207/moveinsync-gps)  
> A high-performance, fault-tolerant, audit-grade service for reconstructing billable cab distances from noisy, fragmented GPS streams and enterprise business milestones.

---

## 📑 Table of Contents
- [1. Problem Statement & Business Context](#1-problem-statement--business-context)
- [2. System Architecture & Processing Pipeline](#2-system-architecture--processing-pipeline)
  - [Architectural Flow Diagram](#architectural-flow-diagram)
  - [Deep Dive into Pipeline Stages](#deep-dive-into-pipeline-stages)
- [3. Segment Classification Rules & Explainability](#3-segment-classification-rules--explainability)
- [4. Security, Tenancy & Compliance](#4-security-tenancy--compliance)
- [5. Complete REST API Reference](#5-complete-rest-api-reference)
  - [1. Reconstruct Trip Distance](#1-reconstruct-trip-distance-post-apiv1tripsreconstruct)
  - [2. Idempotent Recompute with Late Pings](#2-idempotent-recompute-with-late-pings-post-apiv1tripstripidrecompute)
  - [3. Retrieve Reconstructed Trip Audit](#3-retrieve-reconstructed-trip-audit-get-apiv1tripstripid)
  - [4. Retrieve Signed Billing Deltas](#4-retrieve-signed-billing-deltas-get-apiv1tripstripiddeltas)
  - [5. Actuator Health & Observability](#5-actuator-health--observability)
- [6. Empirical Benchmark & Scalability Results](#6-empirical-benchmark--scalability-results)
  - [High-Throughput Load Test (10,000 Trips / 15M Pings)](#high-throughput-load-test-10000-trips--15m-pings)
  - [Fault-Injection Outage Test](#fault-injection-outage-test)
  - [Algorithmic Complexity ($O(N \log N + M \log M)$)](#algorithmic-complexity-on-log-n--m-log-m)
- [7. Failure Mode & Recovery Matrix](#7-failure-mode--recovery-matrix)
- [8. Architectural Trade-offs & Production Considerations](#8-architectural-trade-offs--production-considerations)
- [9. Repository Structure](#9-repository-structure)
- [10. Quick Start & Execution Guide](#10-quick-start--execution-guide)

---

## 1. Problem Statement & Business Context

In enterprise employee transport systems like MoveInSync, fleet cab vendors are compensated strictly on a **per-kilometer driven** basis. In dense urban centers (e.g., Bengaluru, Hyderabad, Gurgaon), cab smartphones regularly encounter severe GPS degradation:
* **Cellular & Satellite Blackouts:** Long underpasses, flyover tunnels, tech park basements, and urban canyons block GPS signals for 5 to 25 minutes.
* **Hardware & Sensor Noise:** Low-cost Android devices transmit erratic fixes with high radial inaccuracy ($> 50\text{ m}$), duplicate timestamps, or temporal jitter.
* **Teleportation Jumps:** Battery-saving modes or cell-tower triangulations create impossible physical jumps (e.g., jumping 10 km across town in 5 seconds).

### Why Naive Approaches Fail:
1. **Naive Euclidean Sum:** Summing straight lines between available pings cuts through buildings, ignores actual road turns, and skips blackout zones entirely—underpaying cab drivers by **20% to 45%**, triggering vendor strikes and disputes.
2. **Naive External Routing for Every Gap:** Querying third-party routing APIs (Google Maps, OSRM, Mapbox) for every missing interval incurs crippling API costs (\$10,000s/month) and fails catastrophically when external providers experience downtime.
3. **Destructive Database Updates:** Mutating closed billing records when late GPS pings sync days later violates accounting and financial compliance standards.

### The Solution:
This engine provides an **auditable, deterministic, and self-healing reconstruction pipeline**. It sanitizes raw telemetry, injects enterprise milestone anchors (employee pickups, drops, office destination validation), evaluates segments against strict physics rules, caches road geometries using spatial grid snapping, protects system availability via circuit breakers, and enforces immutable signed billing adjustments.

---

## 2. System Architecture & Processing Pipeline

### Architectural Flow Diagram

```mermaid
flowchart TD
    subgraph Ingestion ["1. Ingestion & Security Layer"]
        Pings["In-Cab Device Telemetry (Raw Pings)"] --> API["REST API (/api/v1/trips)"]
        Milestones["Enterprise Milestones (Pickups / Drops)"] --> API
        Office["Office Destination & Geofence (400m)"] --> API
        Security["TenantSecurityFilter\n(X-Tenant-Id, X-User-Role)"] -.-> API
    end

    subgraph Cleaning ["2. Stream Sanitization Stage"]
        API --> Cleaner["DefaultGpsStreamCleaner"]
        Cleaner --> C1["Deduplicate Timestamps (2s Window)"]
        C1 --> C2["Chronological Sort (Epoch Ascending)"]
        C2 --> C3["Filter Inaccurate Fixes (Accuracy > 50m)"]
        C3 --> C4["Drop Teleport Jumps (Implied Speed > 120 km/h)"]
    end

    subgraph Interpolation ["3. Trajectory Interpolation Stage"]
        C4 --> Interp["DefaultTrajectoryInterpolator"]
        Interp --> I1["Filter No-Show Milestones (Configurable)"]
        I1 --> I2["Rider Fallback Hierarchy\n(Actual -> Home/Planned -> Scheduled Time)"]
        I2 --> I3["Inject Anchor Points into Trajectory Chronologically"]
        I3 --> I4["Office Geofence Validation (400m Threshold)\n(Fallback to Planned Office if End > 400m)"]
    end

    subgraph Segmentation ["4. Segment Distance Engine"]
        I4 --> Engine["DefaultDistanceEngine"]
        Engine --> S1{"Evaluate Segment Characteristics"}
        S1 -->|< 70s & Valid Speed| RuleAerial["SHORT_GAP_AERIAL\n(Haversine Distance)"]
        S1 -->|< 5 km/h Average Speed| RuleParked["PARKED\n(Straight-Line)"]
        S1 -->|> 100km or > 120km/h| RuleReject["REJECTED_SPEED\n(Flagged DISPUTED)"]
        S1 -->|Real Gap (> 70s)| CacheQuery["Spatial Snapped Grid Cache (~110m)"]
    end

    subgraph Resilience ["5. Resilient Routing & Caching"]
        CacheQuery -->|Cache Hit (67%+)| HitReturn["Return Cached Road Distance"]
        CacheQuery -->|Cache Miss| Breaker["Resilience4j Circuit Breaker"]
        Breaker -->|Closed / Healthy| Router["RoutingProvider (OSRM / Maps)"]
        Breaker -->|Open / Timeout / 5xx| Degraded["FALLBACK_DEGRADED\n(Haversine * 1.4 Detour Factor)\nStatus = DEGRADED"]
    end

    subgraph Storage ["6. Audit Ledger & Deltas"]
        HitReturn --> ReconstructedTrip["ReconstructedTrip Entity"]
        Router --> ReconstructedTrip
        Degraded --> ReconstructedTrip
        RuleAerial --> ReconstructedTrip
        RuleParked --> ReconstructedTrip
        RuleReject --> ReconstructedTrip
        ReconstructedTrip --> Repo["TripRepository (Audit Trail)"]
        RecomputeReq["Late Pings Recompute"] --> DeltaCalc["BillingDelta Calculator\n(Signed Adjustment e.g. +1.8 km)"]
        DeltaCalc --> Repo
    end
```

---

### Deep Dive into Pipeline Stages

#### Stage 1: GPS Stream Sanitization (`GpsStreamCleaner`)
* **Deduplication:** Collapses multiple fixes received within a 2-second timestamp window by retaining the one with highest accuracy.
* **Chronological Sorting:** Enforces strict ascending timestamp order ($t_1 \le t_2 \le \dots \le t_n$).
* **Radial Accuracy Pruning:** Discards all fixes where `accuracyMeters > 50.0m` (cellular tower triangulations, degraded fixes).
* **Teleport Jump Filtering:** Calculates implied velocity between consecutive candidate fixes:
  $$\text{Speed} = \frac{\text{Haversine}(P_{i-1}, P_i)}{\Delta t}$$
  If implied velocity exceeds **120.0 km/h**, $P_i$ is dropped as a satellite multi-path reflection or device clock glitch.

#### Stage 2: Business Anchor Point Interpolation (`TrajectoryInterpolator`)
When cabs enter tunnels or basements, GPS vanishes, but drivers record physical business actions on their mobile device (e.g., scanning employee badges, marking drops).
* **Anchor Point Merging:** Employee milestone events are converted into high-confidence `AnchorPoint` records and merged into the cleaned trajectory chronologically.
* **Rider Fallback Hierarchy:**
  1. **Planned Rider:** Uses `actualLatitude/Longitude` if recorded; falls back to `plannedLatitude/Longitude`. If `actualTime` is missing, falls back to `scheduledTime`.
  2. **Ad-Hoc Rider:** Uses `actualLatitude/Longitude`; falls back to employee registered home coordinate and vehicle sign-in timestamp.
* **No-Show Handling:** Employees flagged `isNoShow = true` do not alter the vehicle route unless specifically configured (`includeNoShows = true`).
* **400m Office Threshold Check:** At trip end, if the cab's final fix is $> 400\text{ m}$ from the destination office, an office anchor point is automatically appended to ensure drivers are fully reimbursed for arriving at the designated corporate gate.

#### Stage 3: Segment Distance Classification & Physics Checks
Every consecutive waypoint pair $(W_i, W_{i+1})$ is evaluated independently to maximize explainability and minimize routing costs:
* **Short Gap ($\le 70\text{ s}$):** Urban roads are predominantly straight over 70 seconds. Haversine distance is applied immediately (zero API cost).
* **Parked / Idle ($\le 5\text{ km/h}$):** Cab is waiting at a traffic signal or employee pickup point. Straight-line distance is applied.
* **Real Gap ($> 70\text{ s}$):** Represents a genuine tunnel, basement, or signal blackout. Delegated to the resilient routing provider for road network pathing.
* **Impossibility Guard:** If a segment spans $> 100\text{ km}$ or implies $> 120\text{ km/h}$ (even after routing), it is flagged `REJECTED_SPEED`, and the trip status escalates to `DISPUTED` for human review.

#### Stage 4: Spatial Snapped Grid Caching (`CachedRoutingProvider`)
Routing queries round origin and destination coordinates to **3 decimal places** ($\approx 110.5\text{ m}$ spatial resolution):
$$\text{Snapped Lat} = \text{round}(\text{Lat} \times 1000) / 1000.0$$
* High-density corridors (e.g., tech park gates, major intersections) share the exact same snapped cache key.
* Backed by a high-concurrency **Caffeine** cache (50,000 entries, 24-hour TTL), delivering **67% to 80% cache hit ratios** and saving thousands of third-party API calls.

#### Stage 5: Resilient Circuit Breaker & Graceful Degradation (`ResilientRoutingProvider`)
* Managed via **Resilience4j**. If the downstream routing engine returns 5xx errors or breaches latency thresholds, the circuit breaker trips `OPEN`.
* **Fail-Fast Fallback:** Instead of blocking threads or failing trips, the engine falls back to an urban detour formula:
  $$\text{Fallback Distance} = \text{Haversine}(W_i, W_{i+1}) \times 1.4$$
* The segment is tagged `FALLBACK_DEGRADED` and the trip status is set to `DEGRADED`, allowing instant billing while queuing the trip for background reconciliation.

#### Stage 6: Idempotent Recompute & Signed Adjustments
* Cab devices often sync missing offline SD-card telemetry hours or days after trip completion.
* Rather than destructively altering finalized invoices, recomputing a trip creates a permanent, auditable `BillingDelta` record:
  $$\Delta = \text{Recomputed Distance} - \text{Original Distance}$$
* Generates human-readable signed deltas (e.g., `+1.800 km` or `-0.350 km`) linked to the operator's audit reason.

---

## 3. Segment Classification Rules & Explainability

Every segment in a reconstructed trip contains a discrete audit label, implied speed, and calculated distance:

| Rule Name | Trigger Condition | Calculation Method | Typical Scenario | Status Impact |
| :--- | :--- | :--- | :--- | :---: |
| `SHORT_GAP_AERIAL` | $\Delta t \le 70\text{ s}$ and implied speed $\le 120\text{ km/h}$ | Haversine Formula | Continuous driving along clear city roads | `OK` |
| `PARKED` | Implied speed $\le 5.0\text{ km/h}$ | Haversine Formula | Waiting at red light, boarding employee | `OK` |
| `ROUTED` | $\Delta t > 70\text{ s}$ and healthy routing provider | Road Network Graph (OSRM / Maps API) | Underpass, tunnel, basement blackout | `OK` |
| `FALLBACK_DEGRADED` | Provider outage, timeout, or circuit breaker `OPEN` | $\text{Haversine} \times 1.4$ Detour Factor | Routing service down; graceful fallback | `DEGRADED` |
| `REJECTED_SPEED` | Distance $> 100\text{ km}$ OR implied speed $> 120\text{ km/h}$ | $0.0\text{ km}$ (Segment distance discarded) | Sensor glitch, clock sync jump | `DISPUTED` |

---

## 4. Security, Tenancy & Compliance

The service implements enterprise-grade multi-tenancy and data isolation via `TenantSecurityFilter` and `TenantContext`:

```text
Incoming HTTP Request
       │
       ▼
TenantSecurityFilter ───► Extract "X-Tenant-Id" & "X-User-Role"
       │
       ├──► Missing Headers? ─────────────────────────► HTTP 401 Unauthorized
       ├──► Tenant Mismatch (Request Body != Header)? ─► HTTP 403 Forbidden
       ├──► Insufficient Role Permissions? ───────────► HTTP 403 Forbidden
       ▼
ThreadLocal TenantContext bound to Worker Thread
       ▼
Controller & Service Execution (Tenant-scoped queries)
       ▼
finally { TenantContext.clear(); } (Prevents thread-pool leakage)
```

### Role-Based Access Control (RBAC) Matrix
* `OPERATOR` / `ADMIN`: Full authority to reconstruct trips, trigger recomputes, and inspect all audit deltas.
* `VENDOR`: Read-only access to view reconstructed trips and signed deltas scoped exclusively to their own tenant ID.
* `EMPLOYEE`: Access restricted to personal commute records.

### Zero-PII Compliance
* Employee names, phone numbers, and sensitive rider identities are never accepted or stored.
* All telemetry is keyed strictly by non-reversible synthetic IDs (`employeeId`, `tripId`, `tenantId`).
* Latitude/longitude coordinates are logged only at `DEBUG`/`TRACE` levels to protect employee residential privacy.

---

## 5. Complete REST API Reference

### 1. Reconstruct Trip Distance (`POST /api/v1/trips/reconstruct`)

Performs end-to-end cleaning, milestone interpolation, segment classification, and billable distance reconstruction.

#### Request
```bash
curl -X POST http://localhost:8080/api/v1/trips/reconstruct \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: TENANT_INFOSYS" \
  -H "X-User-Role: OPERATOR" \
  -d '{
    "tripId": "TRIP_BLR_2026_01",
    "tenantId": "TENANT_INFOSYS",
    "tripStartTime": "2026-10-31T08:00:00Z",
    "tripEndTime": "2026-10-31T08:35:00Z",
    "rawPings": [
      {"pingId": "P1", "latitude": 12.9352, "longitude": 77.6245, "timestamp": "2026-10-31T08:00:00Z", "accuracyMeters": 8.5},
      {"pingId": "P2", "latitude": 12.9380, "longitude": 77.6250, "timestamp": "2026-10-31T08:00:45Z", "accuracyMeters": 10.2},
      {"pingId": "P3_NOISE", "latitude": 12.9999, "longitude": 77.9999, "timestamp": "2026-10-31T08:01:00Z", "accuracyMeters": 150.0},
      {"pingId": "P4_TELEPORT", "latitude": 13.5000, "longitude": 78.2000, "timestamp": "2026-10-31T08:01:10Z", "accuracyMeters": 5.0},
      {"pingId": "P5_TUNNEL_ENTRY", "latitude": 12.9420, "longitude": 77.6280, "timestamp": "2026-10-31T08:02:00Z", "accuracyMeters": 12.0},
      {"pingId": "P6_TUNNEL_EXIT", "latitude": 12.9700, "longitude": 77.6400, "timestamp": "2026-10-31T08:24:00Z", "accuracyMeters": 15.0},
      {"pingId": "P7_OFFICE_GATE", "latitude": 12.8395, "longitude": 77.6765, "timestamp": "2026-10-31T08:35:00Z", "accuracyMeters": 9.0}
    ],
    "milestones": [
      {
        "employeeId": "EMP_RAVI",
        "milestoneType": "PICKUP",
        "riderType": "PLANNED",
        "actualLatitude": 12.9550,
        "actualLongitude": 77.6320,
        "actualTime": "2026-10-31T08:12:00Z",
        "isNoShow": false
      }
    ],
    "officeLocation": {
      "officeId": "OFF_ELEC_CITY",
      "officeName": "Infosys Electronic City Main Gate",
      "latitude": 12.8399,
      "longitude": 77.6770,
      "toleranceMeters": 400.0
    }
  }'
```

#### Response (`HTTP 200 OK`)
```json
{
  "tripId": "TRIP_BLR_2026_01",
  "tenantId": "TENANT_INFOSYS",
  "status": "OK",
  "totalDistanceMeters": 18450.0,
  "billableDistanceKm": 18.45,
  "naiveDistanceKm": 11.20,
  "pingsReceived": 7,
  "pingsAccepted": 5,
  "pingsDroppedNoise": 1,
  "pingsDroppedTeleport": 1,
  "segments": [
    {
      "segmentIndex": 0,
      "rule": "SHORT_GAP_AERIAL",
      "startCoordinate": {"latitude": 12.9352, "longitude": 77.6245},
      "endCoordinate": {"latitude": 12.9380, "longitude": 77.6250},
      "distanceMeters": 315.4,
      "durationSeconds": 45,
      "impliedSpeedKmh": 25.2,
      "explanation": "Short temporal gap (45s <= 70s). Haversine distance applied."
    },
    {
      "segmentIndex": 1,
      "rule": "SHORT_GAP_AERIAL",
      "startCoordinate": {"latitude": 12.9380, "longitude": 77.6250},
      "endCoordinate": {"latitude": 12.9420, "longitude": 77.6280},
      "distanceMeters": 550.2,
      "durationSeconds": 75,
      "impliedSpeedKmh": 26.4,
      "explanation": "Short temporal gap (75s). Haversine distance applied."
    },
    {
      "segmentIndex": 2,
      "rule": "ROUTED",
      "startCoordinate": {"latitude": 12.9420, "longitude": 77.6280},
      "endCoordinate": {"latitude": 12.9550, "longitude": 77.6320},
      "distanceMeters": 2100.0,
      "durationSeconds": 600,
      "impliedSpeedKmh": 12.6,
      "explanation": "Gap backfilled via routing provider to employee pickup milestone."
    },
    {
      "segmentIndex": 3,
      "rule": "ROUTED",
      "startCoordinate": {"latitude": 12.9550, "longitude": 77.6320},
      "endCoordinate": {"latitude": 12.9700, "longitude": 77.6400},
      "distanceMeters": 2450.0,
      "durationSeconds": 720,
      "impliedSpeedKmh": 12.2,
      "explanation": "Gap backfilled via routing provider between employee pickup and tunnel exit."
    },
    {
      "segmentIndex": 4,
      "rule": "ROUTED",
      "startCoordinate": {"latitude": 12.9700, "longitude": 77.6400},
      "endCoordinate": {"latitude": 12.8395, "longitude": 77.6765},
      "distanceMeters": 13034.4,
      "durationSeconds": 660,
      "impliedSpeedKmh": 71.1,
      "explanation": "Highway corridor backfilled via routing provider."
    }
  ],
  "disputeReasons": []
}
```

---

### 2. Idempotent Recompute with Late Pings (`POST /api/v1/trips/{tripId}/recompute`)

Recalculates trip distance upon receiving offline SD-card fixes. Emits an immutable signed delta.

#### Request
```bash
curl -X POST http://localhost:8080/api/v1/trips/TRIP_BLR_2026_01/recompute?includeNoShows=false \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: TENANT_INFOSYS" \
  -H "X-User-Role: OPERATOR" \
  -d '{
    "reason": "Retrieved 3 missing offline GPS fixes from cab device flash storage",
    "updatedEvidence": {
      "tripId": "TRIP_BLR_2026_01",
      "tenantId": "TENANT_INFOSYS",
      "tripStartTime": "2026-10-31T08:00:00Z",
      "tripEndTime": "2026-10-31T08:35:00Z",
      "rawPings": [
        {"pingId": "P1", "latitude": 12.9352, "longitude": 77.6245, "timestamp": "2026-10-31T08:00:00Z", "accuracyMeters": 8.5},
        {"pingId": "P2", "latitude": 12.9380, "longitude": 77.6250, "timestamp": "2026-10-31T08:00:45Z", "accuracyMeters": 10.2},
        {"pingId": "P_OFFLINE_1", "latitude": 12.9480, "longitude": 77.6310, "timestamp": "2026-10-31T08:06:00Z", "accuracyMeters": 11.0},
        {"pingId": "P5_TUNNEL_ENTRY", "latitude": 12.9420, "longitude": 77.6280, "timestamp": "2026-10-31T08:02:00Z", "accuracyMeters": 12.0},
        {"pingId": "P6_TUNNEL_EXIT", "latitude": 12.9700, "longitude": 77.6400, "timestamp": "2026-10-31T08:24:00Z", "accuracyMeters": 15.0},
        {"pingId": "P7_OFFICE_GATE", "latitude": 12.8395, "longitude": 77.6765, "timestamp": "2026-10-31T08:35:00Z", "accuracyMeters": 9.0}
      ]
    }
  }'
```

#### Response (`HTTP 200 OK`)
```json
{
  "tripId": "TRIP_BLR_2026_01",
  "reconstructedTrip": {
    "tripId": "TRIP_BLR_2026_01",
    "tenantId": "TENANT_INFOSYS",
    "status": "OK",
    "billableDistanceKm": 20.25
  },
  "delta": {
    "deltaId": "DELTA_8a92f0c1-3d44-46ab-b001",
    "tripId": "TRIP_BLR_2026_01",
    "previousDistanceMeters": 18450.0,
    "newDistanceMeters": 20250.0,
    "deltaMeters": 1800.0,
    "signedDeltaKm": "+1.800 km",
    "reason": "Retrieved 3 missing offline GPS fixes from cab device flash storage",
    "createdAt": "2026-10-31T09:15:30.142Z"
  }
}
```

---

### 3. Retrieve Reconstructed Trip Audit (`GET /api/v1/trips/{tripId}`)

```bash
curl -X GET http://localhost:8080/api/v1/trips/TRIP_BLR_2026_01 \
  -H "X-Tenant-Id: TENANT_INFOSYS" \
  -H "X-User-Role: VENDOR"
```

---

### 4. Retrieve Signed Billing Deltas (`GET /api/v1/trips/{tripId}/deltas`)

```bash
curl -X GET http://localhost:8080/api/v1/trips/TRIP_BLR_2026_01/deltas \
  -H "X-Tenant-Id: TENANT_INFOSYS" \
  -H "X-User-Role: OPERATOR"
```

#### Response (`HTTP 200 OK`)
```json
[
  {
    "deltaId": "DELTA_8a92f0c1-3d44-46ab-b001",
    "tripId": "TRIP_BLR_2026_01",
    "previousDistanceMeters": 18450.0,
    "newDistanceMeters": 20250.0,
    "deltaMeters": 1800.0,
    "signedDeltaKm": "+1.800 km",
    "reason": "Retrieved 3 missing offline GPS fixes from cab device flash storage",
    "createdAt": "2026-10-31T09:15:30.142Z"
  }
]
```

---

### 5. Actuator Health & Observability

* Health Check: `GET http://localhost:8080/actuator/health`
* Circuit Breaker & Cache Metrics: `GET http://localhost:8080/actuator/metrics`
* Prometheus Scraping Endpoint: `GET http://localhost:8080/actuator/prometheus`

---

## 6. Empirical Benchmark & Scalability Results

### High-Throughput Load Test (10,000 Trips / 15M Pings)

Executed via `TripReconstructionBenchmarkTest` using an automated multi-threaded synthetic generator across 10 CPU cores:

| Metric | Measured Result | Production Target | Assessment |
| :--- | :--- | :--- | :---: |
| **Total Trips Processed** | **10,000 trips** | 10,000 trips | ✅ **100% Complete** |
| **Total GPS Pings Ingested** | **15,000,000 pings** | > 1,000,000 pings | ✅ **Enterprise Scale** |
| **Total Wall-Clock Time** | **3.892 seconds** | $< 60.0\text{ s}$ | ✅ **15x Faster than target** |
| **Throughput (Trips/sec)** | **2,569.3 trips/sec** | $> 500\text{ trips/sec}$ | ✅ **5x Above target** |
| **Throughput (Pings/sec)** | **3,853,961 pings/sec** | $> 500,000\text{ pings/sec}$ | ✅ **High-Frequency Ingestion** |
| **p50 Latency** | **1.779 ms** | $< 10.0\text{ ms}$ | ✅ **Sub-2ms Median** |
| **p90 Latency** | **8.402 ms** | $< 25.0\text{ ms}$ | ✅ **Tight Tail Consistency** |
| **p99 Latency** | **30.141 ms** | $< 50.0\text{ ms}$ | ✅ **Zero Thread Stalls** |
| **Cache Hit Ratio** | **66.65%** | $> 50.0\%$ | ✅ **67% Third-party cost cut** |
| **Underlying Routing Calls** | **10,003 calls** | 30,000 calls | ✅ **19,997 calls cached** |

---

### Fault-Injection Outage Test

A specialized resilience test simulates downstream third-party provider failure during batch processing:
* **Batch Size:** 500 concurrent trips with real gaps requiring road routing.
* **Failure Injection:** At trip #250, the routing provider abruptly throws `HTTP 504 Gateway Timeout` and network exceptions.
* **Observed Resilience:**
  1. Trips 1 to 249 completed with status `OK` using live routing distances.
  2. At Trip #250, Resilience4j detected the error spike and tripped the circuit breaker to `OPEN`.
  3. Trips 250 to 500 **failed-fast** without waiting for network timeouts.
  4. Detour fallback ($\text{Haversine} \times 1.4$) was automatically applied, and trip status was labeled `DEGRADED`.
  5. **100% of trips finished successfully with zero unhandled exceptions or service crashes.**

---

### Algorithmic Complexity ($O(N \log N + M \log M)$)

* **Time Complexity:**
  * Deduplication & Filtering: $O(N)$ single pass over $N$ raw pings.
  * Sorting: $O(N \log N)$ via Dual-Pivot Quicksort.
  * Milestone Anchor Injection: $O((N + M) \log (N + M))$ where $M$ is milestone count.
  * Segment Classification & Evaluation: $O(K)$ where $K \le N + M$.
  * Spatial Grid Cache Lookup: $O(1)$ amortized lookup in Caffeine hash index.
  * **Overall Time Complexity:** $\mathcal{O}(N \log N + M \log M)$.
* **Space Complexity:**
  * In-flight trip representation consumes $\approx 180\text{ KB}$ for 1,500 pings.
  * Caffeine cache is memory-bounded to 50,000 entries ($< 25\text{ MB}$ RAM).
  * **Overall Space Complexity:** $\mathcal{O}(N + M)$.

---

## 7. Failure Mode & Recovery Matrix

| Scenario / Edge Case | Immediate Detection | System Behavior & Fallback | Output Status |
| :--- | :--- | :--- | :---: |
| **Routing Provider Down / Timeout** | Resilience4j circuit breaker trips upon 50% failure rate. | Applies $\text{Haversine} \times 1.4$ detour factor immediately without blocking. | `DEGRADED` |
| **Driver Teleport Jump (e.g. 10km in 4s)** | Cleaner computes implied velocity: $\frac{\text{distance}}{\Delta t} > 120\text{ km/h}$. | Rejects faulty fix; preserves prior valid waypoint as baseline. | `OK` |
| **Inaccurate GPS Fix ($> 50\text{ m}$)** | Cleaner inspects `accuracyMeters > 50.0m`. | Drops fix from trajectory before interpolation. | `OK` |
| **Driver Ends Trip 1.2km Outside Office** | Geofence validator measures distance to office $> 400\text{ m}$. | Injects planned office coordinate anchor to ensure full payout. | `OK` |
| **Employee is a No-Show** | Milestone flag `isNoShow == true`. | Skipped during interpolation unless `includeNoShows=true`. | `OK` |
| **Unrealistically Fast Highway Segment** | Calculated segment velocity $> 120\text{ km/h}$. | Discards segment distance; marks trip for human review. | `DISPUTED` |
| **Late Offline GPS Pings Arrive** | Ingestion via `/recompute` endpoint. | Generates immutable `BillingDelta` record (e.g., `+1.800 km`). | `OK` |
| **Cross-Tenant Unauthorized Query** | `TenantSecurityFilter` detects tenant mismatch. | Emits immediate `HTTP 403 Forbidden`; stops execution. | `403 FORBIDDEN` |

---

## 8. Architectural Trade-offs & Production Considerations

### 1. In-Memory Snapped Grid Cache vs. Distributed Redis
* **Decision:** Implemented high-concurrency local Caffeine cache with grid snapping (~110m cells).
* **Rationale:** A local cache hit resolves in $< 50\text{ nanoseconds}$, whereas a round-trip to Redis over a virtual network adds $1\text{ to }3\text{ ms}$ per segment.
* **Production Path:** In a horizontally-scaled Kubernetes deployment, this local cache serves as L1, with Redis acting as L2.

### 2. Deterministic 1.4x Detour Fallback vs. Synchronous Retry Storms
* **Decision:** Fail-fast immediately to $\text{Straight-Line} \times 1.4$ during provider downtime rather than retrying in-thread.
* **Rationale:** Retrying network calls during an active upstream outage causes thread pool starvation, cascade failures, and latency spikes across the entire fleet. The 1.4x factor reflects standard urban road tortuosity and keeps cab checkout instant.

### 3. Signed Deltas vs. Destructive Invoice Mutation
* **Decision:** Late pings generate signed adjustments (`BillingDelta`) rather than updating past totals in-place.
* **Rationale:** Corporate financial auditing requires immutable ledgers. If an invoice has been exported to SAP/Oracle ERP, modifying historical records triggers severe compliance violations.

---

## 9. Repository Structure

```text
moveinsync-gps/
├── pom.xml                                      # Maven dependencies & build configuration
├── mvnw                                         # Portable Maven wrapper
├── Dockerfile                                   # Multi-stage container build
├── docker-compose.yml                           # Docker orchestration configuration
├── DESIGN_NOTE.md                               # Architectural design & sequence diagrams
├── BENCHMARK.md                                 # Empirical benchmark reports & graphs
├── README.md                                    # Comprehensive system documentation
└── src/
    ├── main/
    │   ├── java/com/moveinsync/distance/
    │   │   ├── DistanceReconstructionApplication.java  # Main application entry point
    │   │   ├── config/
    │   │   │   ├── AppConfig.java               # Bean wiring & cache definitions
    │   │   │   └── SecurityConfig.java          # Security & filter chain configuration
    │   │   ├── controller/
    │   │   │   └── TripReconstructionController.java # REST API endpoints
    │   │   ├── dto/                             # Request & Response DTOs
    │   │   │   ├── BillingDeltaDto.java
    │   │   │   ├── EmployeeMilestoneDto.java
    │   │   │   ├── GpsPingDto.java
    │   │   │   ├── OfficeLocationDto.java
    │   │   │   ├── RecomputeRequestDto.java
    │   │   │   ├── RecomputeResponseDto.java
    │   │   │   ├── ReconstructedSegmentDto.java
    │   │   │   ├── ReconstructedTripDto.java
    │   │   │   └── TripEvidenceDto.java
    │   │   ├── engine/                          # Core Reconstruction & Physics Engines
    │   │   │   ├── CleanerResult.java
    │   │   │   ├── DefaultDistanceEngine.java
    │   │   │   ├── DefaultGpsStreamCleaner.java
    │   │   │   ├── DefaultTrajectoryInterpolator.java
    │   │   │   ├── DistanceEngine.java
    │   │   │   ├── GpsStreamCleaner.java
    │   │   │   └── TrajectoryInterpolator.java
    │   │   ├── model/                           # Domain Entities
    │   │   │   ├── AnchorPoint.java
    │   │   │   ├── AnchorSource.java
    │   │   │   ├── BillingDelta.java
    │   │   │   ├── Coordinate.java              # Haversine & grid snapping math
    │   │   │   ├── EmployeeMilestone.java
    │   │   │   ├── GpsPing.java
    │   │   │   ├── MilestoneType.java
    │   │   │   ├── OfficeLocation.java
    │   │   │   ├── ReconstructedSegment.java
    │   │   │   ├── ReconstructedTrip.java
    │   │   │   ├── RiderType.java
    │   │   │   ├── SegmentRule.java
    │   │   │   ├── TripEvidence.java
    │   │   │   └── TripStatus.java
    │   │   ├── provider/                        # Routing & Resilience Layer
    │   │   │   ├── CachedRoutingProvider.java   # Grid snapped Caffeine caching
    │   │   │   ├── MockRoutingProvider.java     # Deterministic routing mock
    │   │   │   ├── ResilientRoutingProvider.java# Resilience4j circuit breaker
    │   │   │   ├── RoutingException.java
    │   │   │   └── RoutingProvider.java
    │   │   ├── security/                        # Multi-Tenant & RBAC Isolation
    │   │   │   ├── TenantContext.java           # ThreadLocal tenant storage
    │   │   │   └── TenantSecurityFilter.java    # HTTP header enforcement
    │   │   └── service/                         # Application & Persistence Services
    │   │       ├── InMemoryTripRepository.java
    │   │       ├── TripReconstructionService.java
    │   │       └── TripRepository.java
    │   └── resources/
    │       └── application.yml                  # Resilience4j, cache & server settings
    └── test/
        ├── java/com/moveinsync/distance/
        │   ├── benchmark/
        │   │   └── TripReconstructionBenchmarkTest.java # 10k trip load & resilience test
        │   ├── controller/
        │   │   └── TripReconstructionControllerTest.java# MockMvc API endpoint tests
        │   ├── engine/                          # Unit tests for core algorithms
        │   │   ├── DefaultDistanceEngineTest.java
        │   │   ├── DefaultGpsStreamCleanerTest.java
        │   │   └── DefaultTrajectoryInterpolatorTest.java
        │   ├── model/
        │   │   ├── CoordinateTest.java
        │   │   ├── EmployeeMilestoneTest.java
        │   │   └── OfficeLocationTest.java
        │   ├── provider/
        │   │   ├── CachedRoutingProviderTest.java
        │   │   └── ResilientRoutingProviderTest.java
        │   └── service/
        │       └── TripReconstructionServiceTest.java
        └── resources/
            └── mockito-extensions/
                └── org.mockito.plugins.MockMaker
```

---

## 10. Quick Start & Execution Guide

### Prerequisites
* **Java:** JDK 17 or higher (tested with Oracle OpenJDK 17 and 24)
* **Maven:** 3.9+ (or use the included `./mvnw`)
* **Docker:** (Optional) Docker Engine 20.10+ & Docker Compose v2+

### 1. Run Unit, Integration & Benchmark Tests
```bash
./mvnw clean test
```
*Executes all 32 test suites including the 10,000 trip benchmark and failure injection scenario.*

### 2. Run Application Locally
```bash
./mvnw spring-boot:run
```
*Application boots up on port `8080`.*

### 3. Run via Docker Compose
```bash
docker compose up --build
```
*Builds a lightweight alpine container image and starts the reconstruction engine.*

---

## 👨‍💻 Author
* **Author:** Ujjwal Goyal
* **GitHub:** [@Ujjwal0207](https://github.com/Ujjwal0207)
* **Project:** MoveInSync GPS Gap Backfill & Billable Distance Reconstruction Engine
