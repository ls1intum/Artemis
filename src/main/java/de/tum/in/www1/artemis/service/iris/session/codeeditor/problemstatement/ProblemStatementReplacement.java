package de.tum.in.www1.artemis.service.iris.session.codeeditor.problemstatement;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.service.iris.session.codeeditor.IrisChangeException;

/**
 * A change that replaces a range of text in the problem statement with the updated string.
 *
 * @param from    The start of the range to replace, inclusive
 * @param to      The end of the range to replace, exclusive
 * @param updated The updated string to replace the range with
 */
public record ProblemStatementReplacement(String from, String to, String updated) implements ProblemStatementChange {

    @Override
    public String apply(String problemStatement) throws IrisChangeException {
        String before;
        int startIndex;
        if (from.equals("!start!")) {
            before = "";
            startIndex = 0;
        }
        else {
            // Search for the start string in the problem statement
            startIndex = problemStatement.indexOf(from);
            if (startIndex == -1) {
                throw new IrisChangeException("Could not locate range start '" + from + "'");
            }
            before = problemStatement.substring(0, startIndex);
        }

        String after;
        if (to.equals("!end!")) {
            after = "";
        }
        else {
            // Search for the end string in the remaining string
            int endIndex = problemStatement.substring(startIndex).indexOf(to);
            if (endIndex == -1) {
                throw new IrisChangeException("Could not find range end '" + to + "' after range start '" + from + "'");
            }
            endIndex += startIndex; // Add the start index to get the index in the original problem statement
            after = problemStatement.substring(endIndex);
        }

        // Replace the range with the updated string
        return before + updated + after;
    }

    public static ProblemStatementChange parse(JsonNode node) throws IllegalArgumentException {
        String from = node.required("from").asText();
        String to = node.required("to").asText();
        String updated = node.required("updated").asText();
        return new ProblemStatementReplacement(from, to, updated);
    }
}
