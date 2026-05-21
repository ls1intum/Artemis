package de.tum.cit.aet.artemis.core.dto.osv;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing the response from the OSV batch API.
 * Contains results for each query in the same order as the request.
 *
 * @param results the list of vulnerability results, one per query in the original request
 * @see <a href="https://osv.dev/docs/#tag/api/operation/OSV_QueryAffectedBatch">OSV Batch API Documentation</a>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record OsvBatchResponseDTO(List<OsvVulnerabilityResultDTO> results) {

}
