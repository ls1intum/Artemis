package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a Bitbucket change activity response
 * Not all possible values are included. If you want to extend this DTO for new functionality, consult the Bitbucket documentation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BitbucketChangeActivitiesDTO(Long size, Long limit, Long start, Boolean isLastPage, List<ValuesDTO> values) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ValuesDTO(Long id, Long createdDate, RefChangeDTO refChange, String trigger) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record RefChangeDTO(String fromHash, String toHash, String refId) {
    }
}
