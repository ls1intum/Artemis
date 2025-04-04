package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A result produced by an analysis tool.
 *
 * @param ruleId    The stable, unique identifier of the rule, if any, to which this result is relevant.
 * @param ruleIndex The index within the tool component rules array of the rule object associated with this result.
 * @param rule      Information about how to locate a relevant reporting descriptor.
 * @param kind      A value that categorizes results by evaluation state.
 * @param level     A value specifying the severity level of the result.
 * @param message   A message that describes the result. The first sentence of the message only will be displayed when visible space is limited.
 * @param locations The set of locations where the result was detected. Specify only one location unless the problem indicated by the result can only be corrected by making a
 *                      change at every specified location.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Result(String ruleId, Integer ruleIndex, ReportingDescriptorReference rule,
        de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif.Result.Kind kind, Level level, Message message, List<Location> locations) {

    public Result(String ruleId, Integer ruleIndex, ReportingDescriptorReference rule, Kind kind, Level level, Message message, List<Location> locations) {
        this.ruleId = ruleId;
        this.ruleIndex = Objects.requireNonNullElse(ruleIndex, -1);
        this.rule = rule;
        this.kind = Objects.requireNonNullElse(kind, Kind.FAIL);
        this.level = level;
        this.message = message;
        this.locations = locations;
    }

    /**
     * The stable, unique identifier of the rule, if any, to which this result is relevant.
     */
    public Optional<String> getOptionalRuleId() {
        return Optional.ofNullable(ruleId);
    }

    /**
     * The index within the tool component rules array of the rule object associated with this result.
     */
    public Optional<Integer> getOptionalRuleIndex() {
        return Optional.ofNullable(ruleIndex);
    }

    /**
     * Information about how to locate a relevant reporting descriptor.
     */
    public Optional<ReportingDescriptorReference> getOptionalRule() {
        return Optional.ofNullable(rule);
    }

    /**
     * A value that categorizes results by evaluation state.
     */
    public Optional<Kind> getOptionalKind() {
        return Optional.ofNullable(kind);
    }

    /**
     * A value specifying the severity level of the result.
     */
    public Optional<Level> getOptionalLevel() {
        return Optional.ofNullable(level);
    }

    /**
     * The set of locations where the result was detected. Specify only one location unless the problem indicated by the result can only be corrected by making a change at every
     * specified location.
     */
    public Optional<List<Location>> getOptionalLocations() {
        return Optional.ofNullable(locations);
    }

    /**
     * A value that categorizes results by evaluation state.
     */
    public enum Kind {

        NOT_APPLICABLE("notApplicable"), PASS("pass"), FAIL("fail"), REVIEW("review"), OPEN("open"), INFORMATIONAL("informational");

        private final String value;

        private static final Map<String, Kind> CONSTANTS = new HashMap<>();

        static {
            for (Kind c : values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        Kind(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        /**
         * Creates a {@link Kind} instance from a given string value.
         * <p>
         *
         * @param value the string representation of the {@link Kind}
         * @return the matching {@link Kind} instance
         * @throws IllegalArgumentException if the provided value does not correspond to any defined {@link Kind}
         */
        @JsonCreator
        public static Kind fromValue(String value) {
            Kind constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            }
            else {
                return constant;
            }
        }
    }
}
