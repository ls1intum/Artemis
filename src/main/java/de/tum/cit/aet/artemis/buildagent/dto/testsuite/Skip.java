package de.tum.cit.aet.artemis.buildagent.dto.testsuite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Intentionally empty record to represent the skipped tag (<skipped/>)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Skip() {
}
