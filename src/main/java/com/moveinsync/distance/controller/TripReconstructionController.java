package com.moveinsync.distance.controller;

import com.moveinsync.distance.dto.*;
import com.moveinsync.distance.model.BillingDelta;
import com.moveinsync.distance.model.ReconstructedTrip;
import com.moveinsync.distance.security.TenantContext;
import com.moveinsync.distance.service.TripReconstructionService;
import com.moveinsync.distance.service.TripRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trips")
public class TripReconstructionController {

    private final TripReconstructionService reconstructionService;
    private final TripRepository tripRepository;

    public TripReconstructionController(
            TripReconstructionService reconstructionService,
            TripRepository tripRepository
    ) {
        this.reconstructionService = reconstructionService;
        this.tripRepository = tripRepository;
    }

    @PostMapping("/reconstruct")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN', 'VENDOR')")
    public ResponseEntity<ReconstructedTripDto> reconstructTrip(
            @Valid @RequestBody TripEvidenceDto evidenceDto,
            @RequestParam(name = "includeNoShows", defaultValue = "false") boolean includeNoShows
    ) {
        validateTenantAccess(evidenceDto.tenantId());

        ReconstructedTrip trip = reconstructionService.reconstructTrip(evidenceDto.toModel(), includeNoShows);
        return ResponseEntity.ok(ReconstructedTripDto.fromModel(trip));
    }

    @PostMapping("/{tripId}/recompute")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<RecomputeResponseDto> recomputeTrip(
            @PathVariable(name = "tripId") String tripId,
            @Valid @RequestBody RecomputeRequestDto requestDto
    ) {
        validateTenantAccess(requestDto.updatedEvidence().tenantId());

        var result = reconstructionService.recomputeWithLateEvidence(
                tripId,
                requestDto.updatedEvidence().toModel(),
                requestDto.reason()
        );

        BillingDeltaDto deltaDto = result.delta().map(BillingDeltaDto::fromModel).orElse(null);
        return ResponseEntity.ok(new RecomputeResponseDto(
                ReconstructedTripDto.fromModel(result.reconstructedTrip()),
                deltaDto
        ));
    }

    @GetMapping("/{tripId}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN', 'VENDOR', 'EMPLOYEE')")
    public ResponseEntity<ReconstructedTripDto> getTrip(@PathVariable(name = "tripId") String tripId) {
        ReconstructedTrip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found: " + tripId));

        validateTenantAccess(trip.tenantId());
        return ResponseEntity.ok(ReconstructedTripDto.fromModel(trip));
    }

    @GetMapping("/{tripId}/deltas")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN', 'VENDOR')")
    public ResponseEntity<List<BillingDeltaDto>> getTripDeltas(@PathVariable(name = "tripId") String tripId) {
        ReconstructedTrip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found: " + tripId));

        validateTenantAccess(trip.tenantId());

        List<BillingDelta> deltas = tripRepository.findDeltasByTripId(tripId);
        List<BillingDeltaDto> dtos = deltas.stream().map(BillingDeltaDto::fromModel).toList();
        return ResponseEntity.ok(dtos);
    }

    private void validateTenantAccess(String entityTenantId) {
        String activeTenantId = TenantContext.getTenantId();
        if (activeTenantId != null && !activeTenantId.equalsIgnoreCase(entityTenantId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Tenant isolation violation: active tenant [" + activeTenantId + "] cannot access [" + entityTenantId + "]"
            );
        }
    }
}
