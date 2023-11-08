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

    /**
     * Converts a serialized action to a platform action. Gson can't know the type of the action during deserialization,
     * so we have to do it manually.
     *
     * @param serializedAction the serialized action
     * @return the script action
     */
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
