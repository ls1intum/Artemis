package de.tum.cit.aet.artemis.programming.dto.aeolus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AeolusResult(String name, String path, String ignore, String type, boolean before) {
}
