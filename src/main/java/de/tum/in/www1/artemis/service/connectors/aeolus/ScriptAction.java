package de.tum.in.www1.artemis.service.connectors.aeolus;

/**
 * Represents a repository that can be used in a {@link Windfile}
 */
public class ScriptAction extends Action {

    private String script;

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public static ScriptAction deserialize(SerializedAction serializedAction) {
        ScriptAction scriptAction = new ScriptAction();
        scriptAction.setName(serializedAction.getName());
        scriptAction.setScript(serializedAction.getScript());
        scriptAction.setParameters(serializedAction.getParameters());
        scriptAction.setEnvironment(serializedAction.getEnvironment());
        scriptAction.setRunAlways(serializedAction.isRunAlways());
        return scriptAction;
    }
}
