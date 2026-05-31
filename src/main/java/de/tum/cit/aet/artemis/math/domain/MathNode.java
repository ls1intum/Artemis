package de.tum.cit.aet.artemis.math.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An AST node for mathematical expressions.
 * Stored as a JSON column via {@link MathNodeConverter}.
 * <p>
 * Terminal nodes ({@code number}, {@code variable}, {@code wildcard}) have no slots.
 * Non-terminal nodes ({@code add}, {@code sub}, {@code mul}, etc.) carry named slots, each holding an ordered list of children.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MathNode {

    private String type;

    private String value;

    private Map<String, List<MathNode>> slots;

    public MathNode() {
    }

    public MathNode(String type, String value, Map<String, List<MathNode>> slots) {
        this.type = type;
        this.value = value;
        this.slots = slots;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Map<String, List<MathNode>> getSlots() {
        return slots;
    }

    public void setSlots(Map<String, List<MathNode>> slots) {
        this.slots = slots;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MathNode other)) {
            return false;
        }
        return Objects.equals(type, other.type) && Objects.equals(value, other.value) && Objects.equals(slots, other.slots);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value, slots);
    }

    @Override
    public String toString() {
        return "MathNode{type='" + type + "', value='" + value + "'}";
    }
}
