package de.tum.cit.aet.artemis.programming.service.aeolus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Represents a windfile, the definition file for an aeolus build plan that
 * can then be used to generate a Jenkinsfile.
 */
// TODO convert into Record
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Windfile {

    private static final ObjectMapper mapper = new ObjectMapper();

    private String api;

    private WindfileMetadata metadata;

    private List<Action> actions = new ArrayList<>();

    private Map<String, AeolusRepository> repositories = new HashMap<>();

    public String getApi() {
        return api;
    }

    public void setApi(String api) {
        this.api = api;
    }

    public WindfileMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(WindfileMetadata metadata) {
        this.metadata = metadata;
    }

    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    /**
     * Gets the script actions of a windfile.
     *
     * @return the script actions of a windfile.
     */
    public List<ScriptAction> getScriptActions() {
        List<ScriptAction> scriptActions = new ArrayList<>();
        for (Action action : actions) {
            if (action instanceof ScriptAction) {
                scriptActions.add((ScriptAction) action);
            }
        }
        return scriptActions;
    }

    public void setRepositories(Map<String, AeolusRepository> repositories) {
        this.repositories = repositories;
    }

    public Map<String, AeolusRepository> getRepositories() {
        return repositories;
    }

    /**
     * Sets the pre-processing metadata for the windfile.
     *
     * @param id                    the id of the windfile.
     * @param name                  the name of the windfile.
     * @param gitCredentials        the git credentials of the windfile.
     * @param resultHook            the result hook of the windfile.
     * @param description           the description of the windfile.
     * @param repositories          the repositories of the windfile.
     * @param resultHookCredentials the credentials for the result hook of the windfile.
     */
    public void setPreProcessingMetadata(String id, String name, String gitCredentials, String resultHook, String description, Map<String, AeolusRepository> repositories,
            String resultHookCredentials) {
        this.setMetadata(new WindfileMetadata(name, id, description, null, gitCredentials, null, resultHook, resultHookCredentials));
        this.setRepositories(repositories);
    }

    /**
     * Deserializes a windfile from a json string.
     *
     * @param json the json string to deserialize.
     * @return the deserialized windfile.
     * @throws JsonProcessingException if the json string is not valid.
     */
    public static Windfile deserialize(String json) throws JsonProcessingException {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Action.class, new ActionDeserializer());
        mapper.registerModule(module);
        return mapper.readValue(json, Windfile.class);
    }

    /**
     * Collects the results of all actions of a windfile.
     *
     * @return the results of all actions of this windfile
     */
    public List<AeolusResult> getResults() {
        List<AeolusResult> results = new ArrayList<>();
        for (Action action : actions.stream().filter(action -> action.getResults() != null && !action.getResults().isEmpty()).toList()) {
            results.addAll(action.getResults());
        }
        return results;
    }
}
