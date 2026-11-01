package de.tum.cit.aet.artemis.localci.service;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * The parts of a legacy build plan configuration (the old Windfile format) that are read when it is converted into build phases.
 * <p>
 * The configuration is stored text that predates the typed build phases, so every part is read leniently: a value of an unexpected shape is treated as absent and never makes the
 * whole configuration unreadable. A {@code null} component therefore means that the configuration did not carry a usable value there.
 *
 * @param metadata the metadata of the configuration, absent when it is not an object
 * @param actions  the actions of the configuration, absent when {@code actions} is not an array, which is what tells a legacy Windfile from any other JSON
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
record LegacyBuildPlanConfiguration(@Nullable @JsonDeserialize(using = LegacyBuildPlanConfiguration.MetadataDeserializer.class) Metadata metadata,
        @Nullable @JsonDeserialize(using = LegacyBuildPlanConfiguration.ActionListDeserializer.class) List<Action> actions) {

    /**
     * The metadata of a legacy build plan configuration.
     *
     * @param docker the docker section, absent when it is not an object
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Metadata(@Nullable @JsonDeserialize(using = LegacyBuildPlanConfiguration.DockerDeserializer.class) Docker docker) {
    }

    /**
     * The docker section of the metadata.
     *
     * @param image the docker image, absent when it is not a string
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Docker(@Nullable @JsonDeserialize(using = LegacyBuildPlanConfiguration.TextDeserializer.class) String image) {
    }

    /**
     * One action of the legacy Windfile.
     *
     * @param script  the script of the action, absent when it is not a string
     * @param workdir the working directory of the script, absent when it is not a string
     * @param results the results of the action, absent when {@code results} is not an array
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Action(@Nullable @JsonDeserialize(using = LegacyBuildPlanConfiguration.TextDeserializer.class) String script,
            @Nullable @JsonDeserialize(using = LegacyBuildPlanConfiguration.TextDeserializer.class) String workdir,
            @Nullable @JsonDeserialize(using = LegacyBuildPlanConfiguration.ResultListDeserializer.class) List<Result> results) {
    }

    /**
     * One result of an action.
     *
     * @param path the path of the result files, absent when it is not a string
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(@Nullable @JsonDeserialize(using = LegacyBuildPlanConfiguration.TextDeserializer.class) String path) {
    }

    /** Reads a JSON string as it is and skips any other value, which is then absent. */
    static final class TextDeserializer extends ValueDeserializer<String> {

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() == JsonToken.VALUE_STRING) {
                return parser.getString();
            }
            parser.skipChildren();
            return null;
        }
    }

    /**
     * Reads a JSON object as the given type and skips any other value, which is then absent.
     *
     * @param <T> the type of the object
     */
    abstract static class ObjectDeserializer<T> extends ValueDeserializer<T> {

        private final Class<T> type;

        ObjectDeserializer(Class<T> type) {
            this.type = type;
        }

        @Override
        public T deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() == JsonToken.START_OBJECT) {
                return context.readValue(parser, type);
            }
            parser.skipChildren();
            return null;
        }
    }

    /** Reads the metadata section. */
    static final class MetadataDeserializer extends ObjectDeserializer<Metadata> {

        MetadataDeserializer() {
            super(Metadata.class);
        }
    }

    /** Reads the docker section. */
    static final class DockerDeserializer extends ObjectDeserializer<Docker> {

        DockerDeserializer() {
            super(Docker.class);
        }
    }

    /**
     * Reads a JSON array as a list of the given type, dropping the entries that are not objects, and skips any other value, which is then absent.
     *
     * @param <T> the type of the objects in the list
     */
    abstract static class ObjectListDeserializer<T> extends ValueDeserializer<List<T>> {

        private final Class<T> elementType;

        ObjectListDeserializer(Class<T> elementType) {
            this.elementType = elementType;
        }

        @Override
        public List<T> deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.START_ARRAY) {
                parser.skipChildren();
                return null;
            }
            List<T> elements = new ArrayList<>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    elements.add(context.readValue(parser, elementType));
                }
                else {
                    parser.skipChildren();
                }
            }
            return elements;
        }
    }

    /** Reads the actions of the configuration. */
    static final class ActionListDeserializer extends ObjectListDeserializer<Action> {

        ActionListDeserializer() {
            super(Action.class);
        }
    }

    /** Reads the results of an action. */
    static final class ResultListDeserializer extends ObjectListDeserializer<Result> {

        ResultListDeserializer() {
            super(Result.class);
        }
    }
}
