package de.tum.cit.aet.artemis.programming.dto.aeolus;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents an action that is intended to be executed on a single target, used in {@link Windfile} to enable platform
 * independent actions but also to run actions on a single target. (e.q. the parsing of the test results that needs to
 * run on Jenkins but not in LocalCI)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScriptAction(String name, Map<String, Object> parameters, Map<String, Object> environment, List<AeolusResult> results, String workdir, boolean runAlways,
        String platform, String script) implements Action {

}
