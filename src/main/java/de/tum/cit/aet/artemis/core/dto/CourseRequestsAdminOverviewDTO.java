package de.tum.cit.aet.artemis.core.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for the admin course requests overview containing pending and decided requests separately.
 *
 * @param pendingRequests   list of pending course requests with instructor course count
 * @param decidedRequests   list of decided (accepted/rejected) course requests
 * @param totalDecidedCount total number of decided requests for pagination
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseRequestsAdminOverviewDTO(List<CourseRequestDTO> pendingRequests, List<CourseRequestDTO> decidedRequests, long totalDecidedCount) {
}
