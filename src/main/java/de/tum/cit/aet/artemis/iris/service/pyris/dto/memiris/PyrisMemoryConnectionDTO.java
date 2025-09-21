package de.tum.cit.aet.artemis.iris.service.pyris.dto.memiris;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisMemoryConnectionDTO(String id, @JsonProperty("connectionType") String connectionType, List<PyrisMemoryDTO> memories, String description,
        Map<String, Object> context, double weight) {
}
