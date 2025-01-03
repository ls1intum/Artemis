package de.tum.cit.aet.artemis.programming.dto.aeolus;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a CI action that is intended to run only on a specific target, can be used in a {@link Windfile}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlatformAction(String name, Map<String, Object> parameters, Map<String, Object> environment, List<AeolusResult> results, String workdir, boolean runAlways,
        String platform, String kind, String type) implements Action {

}
