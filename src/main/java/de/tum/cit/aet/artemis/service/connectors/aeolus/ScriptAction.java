package de.tum.cit.aet.artemis.service.connectors.aeolus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents an action that is intended to be executed on a single target, used in {@link Windfile} to enable platform
 * independent actions but also to run actions on a single target. (e.q. the parsing of the test results that needs to
 * run on Jenkins but not in LocalCI)
 */
// TODO: convert into Record
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ScriptAction extends Action {

    private String script;

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }
}
