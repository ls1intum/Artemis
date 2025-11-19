package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HyperionCodeGenerationEventDTO(TypeDTO type, String jobId, long exerciseId, Integer iteration, RepositoryType repositoryType, String path, Boolean success,
        Integer attempts, String message) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TypeDTO(@JsonValue String value) {

        public static final TypeDTO STARTED = new TypeDTO("STARTED");

        public static final TypeDTO PROGRESS = new TypeDTO("PROGRESS");

        public static final TypeDTO FILE_UPDATED = new TypeDTO("FILE_UPDATED");

        public static final TypeDTO NEW_FILE = new TypeDTO("NEW_FILE");

        public static final TypeDTO DONE = new TypeDTO("DONE");

        public static final TypeDTO ERROR = new TypeDTO("ERROR");

        private static final Map<String, TypeDTO> KNOWN_TYPES = Map.ofEntries( //
                Map.entry(STARTED.value(), STARTED), //
                Map.entry(PROGRESS.value(), PROGRESS), //
                Map.entry(FILE_UPDATED.value(), FILE_UPDATED), //
                Map.entry(NEW_FILE.value(), NEW_FILE), //
                Map.entry(DONE.value(), DONE), //
                Map.entry(ERROR.value(), ERROR));

        public TypeDTO {
            Objects.requireNonNull(value, "value must not be null");
        }

        /**
         * Resolve an event type from its serialized value.
         *
         * @param value stored value or {@code null}
         * @return known {@link TypeDTO} or {@code null}
         */
        @JsonCreator
        public static TypeDTO fromValue(String value) {
            if (value == null) {
                return null;
            }
            return resolve(value);
        }

        /**
         * Preserve compatibility with enum style invocations.
         *
         * @param name canonical constant name such as {@code STARTED}
         * @return matching {@link TypeDTO}
         */
        public static TypeDTO valueOf(String name) {
            return resolve(name);
        }

        private static TypeDTO resolve(String name) {
            var type = KNOWN_TYPES.get(name);
            if (type == null) {
                throw new IllegalArgumentException("Unknown TypeDTO value: " + name);
            }
            return type;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
