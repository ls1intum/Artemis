package de.tum.in.www1.artemis.service.connectors.aeolus;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Represents an action that is intended to be executed on a single target, used in {@link Windfile} to enable platform
 * independent actions but also to run actions on a single target. (e.q. the parsing of the test results that needs to
 * run on Bamboo but not in LocalCI)
 */
@JsonDeserialize()
public class ScriptAction extends Action {

    private String script;

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }
}
