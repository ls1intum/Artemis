package de.tum.in.www1.artemis.service.connectors.aeolus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Represents a windfile, the definition file for an aeolus build plan that
 * can then be used to generate a Bamboo build plan or a Jenkinsfile.
 */
public class Windfile {

    private String api;

    private WindfileMetadata metadata;

    private List<Action> actions;

    private Map<String, AeolusRepository> repositories;

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

    private void checkMetadata() {
        if (this.metadata == null) {
            setMetadata(new WindfileMetadata());
        }
    }

    /**
     * Sets the id of the windfile. If no metadata is present, also sets the metadata.
     *
     * @param id the id of the windfile, which corresponds to the id of the build plan or Jenkinsfile
     *               that is generated from this windfile
     */
    public void setId(String id) {
        checkMetadata();
        this.metadata.setId(id);
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

    /**
     * Sets the credentials for the git repository that is used within the CI system.
     *
     * @param credentials the credentials for the git repository that is used within the CI system.
     */
    public void setGitCredentials(String credentials) {
        checkMetadata();
        this.metadata.setGitCredentials(credentials);
    }

    /**
     * Sets the name of the windfile.
     *
     * @param name the name of the windfile.
     */
    public void setName(String name) {
        checkMetadata();
        this.metadata.setName(name);
    }

    /**
     * Sets the description of the windfile.
     *
     * @param description the description of the windfile.
     */
    public void setDescription(String description) {
        checkMetadata();
        this.metadata.setDescription(description);
    }

    /**
     * Sets the result hook for the windfile.
     *
     * @param resultHook the result hook for the windfile.
     */
    public void setResultHook(String resultHook) {
        checkMetadata();
        this.metadata.setResultHook(resultHook);
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
     * @param id             the id of the windfile.
     * @param name           the name of the windfile.
     * @param gitCredentials the git credentials of the windfile.
     * @param resultHook     the result hook of the windfile.
     * @param description    the description of the windfile.
     * @param repositories   the repositories of the windfile.
     */
    public void setPreProcessingMetadata(String id, String name, String gitCredentials, String resultHook, String description, Map<String, AeolusRepository> repositories) {
        this.setId(id);
        this.setName(name);
        this.setGitCredentials(gitCredentials);
        this.setResultHook(resultHook);
        this.setDescription(description);
        this.setRepositories(repositories);
    }

    /**
     * Deserializes a windfile from a json string.
     *
     * @param json the json string to deserialize.
     * @return the deserialized windfile.
     * @throws JsonSyntaxException if the json string is not valid.
     */
    public static Windfile deserialize(String json) throws JsonSyntaxException {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Action.class, new ActionDeserializer());
        Gson gson = builder.create();
        return gson.fromJson(json, Windfile.class);
    }
}
