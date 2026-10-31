package com.moveinsync.distance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.distance.dto.*;
import com.moveinsync.distance.model.MilestoneType;
import com.moveinsync.distance.model.RiderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TripReconstructionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final Instant baseTime = Instant.parse("2026-10-31T08:00:00Z");

    @Test
    @DisplayName("Should successfully reconstruct trip via REST API with valid tenant credentials")
    void shouldReconstructTripViaRestApi() throws Exception {
        GpsPingDto p1 = new GpsPingDto("P1", 12.9352, 77.6245, baseTime, 10.0);
        GpsPingDto p2 = new GpsPingDto("P2", 12.9380, 77.6250, baseTime.plusSeconds(30), 10.0);

        TripEvidenceDto dto = new TripEvidenceDto(
                "API_TRIP_01",
                "CLIENT_INFOSYS",
                baseTime,
                baseTime.plusSeconds(30),
                List.of(p1, p2),
                List.of(),
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/v1/trips/reconstruct")
                        .header("X-Tenant-Id", "CLIENT_INFOSYS")
                        .header("X-User-Role", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value("API_TRIP_01"))
                .andExpect(jsonPath("$.tenantId").value("CLIENT_INFOSYS"))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.segments").isArray());
    }

    @Test
    @DisplayName("Should reject request with 403 Forbidden when client tries to access another tenant's trip")
    void shouldEnforceTenantIsolation() throws Exception {
        GpsPingDto p1 = new GpsPingDto("P1", 12.9352, 77.6245, baseTime, 10.0);
        GpsPingDto p2 = new GpsPingDto("P2", 12.9380, 77.6250, baseTime.plusSeconds(30), 10.0);

        // Evidence belongs to CLIENT_WIPRO, but caller header is CLIENT_INFOSYS
        TripEvidenceDto dto = new TripEvidenceDto(
                "API_TRIP_WIPRO_01",
                "CLIENT_WIPRO",
                baseTime,
                baseTime.plusSeconds(30),
                List.of(p1, p2),
                List.of(),
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/v1/trips/reconstruct")
                        .header("X-Tenant-Id", "CLIENT_INFOSYS")
                        .header("X-User-Role", "OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should reject request with 401 Unauthorized when X-Tenant-Id header is missing")
    void shouldRejectWhenTenantHeaderMissing() throws Exception {
        TripEvidenceDto dto = new TripEvidenceDto(
                "API_TRIP_02",
                "CLIENT_INFOSYS",
                baseTime,
                baseTime.plusSeconds(30),
                List.of(),
                List.of(),
                null,
                null,
                null
        );

        mockMvc.perform(post("/api/v1/trips/reconstruct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }
}
