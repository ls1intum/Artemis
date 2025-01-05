package de.tum.cit.aet.artemis.programming.dto.aeolus;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Base for the actions that can be defined in a {@link Windfile}
 * NOTE: you must create a record that implements this interface to specify actions
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public interface Action {

    Map<String, Object> parameters();

    Map<String, Object> environment();

    boolean runAlways();

    String name();

    List<AeolusResult> results();

    String workdir();

    String platform();
}
