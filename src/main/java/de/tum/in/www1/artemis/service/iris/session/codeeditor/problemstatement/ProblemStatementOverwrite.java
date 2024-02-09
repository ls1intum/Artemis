package de.tum.in.www1.artemis.service.iris.session.codeeditor.problemstatement;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A change that overwrites the entire problem statement of an exercise.
 *
 * @param updated The new problem statement
 */
public record ProblemStatementOverwrite(String updated) implements ProblemStatementChange {

    @Override
    public String apply(String problemStatement) {
        return updated;
    }

    public static ProblemStatementChange parse(JsonNode node) throws IllegalArgumentException {
        String updated = node.required("updated").asText();
        return new ProblemStatementOverwrite(updated);
    }
}
