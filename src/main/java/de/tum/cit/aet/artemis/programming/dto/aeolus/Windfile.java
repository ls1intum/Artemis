package de.tum.cit.aet.artemis.programming.dto.aeolus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import de.tum.cit.aet.artemis.programming.service.aeolus.ActionDeserializer;

/**
 * Represents a windfile, the definition file for an aeolus build plan that can then be used to generate a Jenkinsfile.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Windfile(String api, WindfileMetadata metadata, List<Action> actions, Map<String, AeolusRepository> repositories) {

    public Windfile {
        if (actions == null) {
            actions = new ArrayList<>();
        }
        if (repositories == null) {
            repositories = new HashMap<>();
        }
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Creates a new windfile based on an existing one with updated metadata.
     *
     * @param existingWindfile the existing windfile to base the new one on.
     * @param metadata         the metadata of the newly created windfile.
     */
    public Windfile(Windfile existingWindfile, WindfileMetadata metadata) {
        this(existingWindfile.api(), metadata, existingWindfile.actions(), existingWindfile.repositories());
    }

    /**
     * Creates a new windfile based on an existing one with updated metadata.
     *
     * @param existingWindfile the existing windfile to base the new one on.
     * @param metadata         the metadata of the newly created windfile.
     * @param repositories     the repositories of the newly created windfile.
     */
    public Windfile(Windfile existingWindfile, WindfileMetadata metadata, Map<String, AeolusRepository> repositories) {
        this(existingWindfile.api(), metadata, existingWindfile.actions(), repositories);
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
     * Gets the script actions of a windfile.
     *
     * @return the script actions of a windfile.
     */
    public List<ScriptAction> scriptActions() {
        return actions.stream().filter(ScriptAction.class::isInstance).map(ScriptAction.class::cast).toList();
    }

    /**
     * Collects the results of all actions of a windfile.
     *
     * @return the results of all actions of this windfile
     */
    public List<AeolusResult> results() {
        return actions.stream().filter(action -> action.results() != null && !action.results().isEmpty()).flatMap(action -> action.results().stream()).toList();
    }
}
