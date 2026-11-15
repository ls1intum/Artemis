package de.tum.cit.aet.artemis.core.service.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.core.dto.agent.AgentFieldErrorDTO;
import de.tum.cit.aet.artemis.core.dto.agent.AgentProblemDTO;

/**
 * What a tool reports back to the agent: text, and whether it describes a failure. A failure is a result, not an exception, so that
 * the model can read what went wrong and correct itself.
 *
 * @param text    the text the agent reads
 * @param isError whether the tool failed
 */
public record AgentToolResult(String text, boolean isError) {

    /** The longest answer handed to an agent, so that one call cannot fill its context. */
    private static final int MAX_CHARACTERS = 20_000;

    public static AgentToolResult ok(String text) {
        return new AgentToolResult(shorten(text), false);
    }

    public static AgentToolResult error(String text) {
        return new AgentToolResult(text, true);
    }

    /**
     * Turns the answer of the REST API into a result, with advice for the failures an agent can act on.
     *
     * @param response the answer of the endpoint a tool called
     * @return the result
     */
    public static AgentToolResult of(AgentApiResponse response) {
        if (response.isSuccess()) {
            return ok(response.body() == null ? "Done (status " + response.status() + ")." : response.body());
        }
        return error("HTTP " + response.status() + ": " + messageOf(response) + " " + adviceFor(response.status()));
    }

    /**
     * Returns complete single-resource JSON so a read/edit/update round trip cannot lose data through truncation.
     *
     * @param response the answer of the single-resource read
     * @return the complete successful body, or the usual error advice
     */
    public static AgentToolResult complete(AgentApiResponse response) {
        return response.isSuccess() && response.body() != null ? new AgentToolResult(response.body(), false) : of(response);
    }

    /**
     * The explanation in a problem document. Artemis puts the sentence for people in the title and an error key in the message, so
     * both are reported, without the generic name of the status.
     */
    private static String messageOf(AgentApiResponse response) {
        AgentProblemDTO problem = response.problem();
        if (problem == null) {
            return response.body() == null ? "no details" : response.body();
        }
        HttpStatus httpStatus = HttpStatus.resolve(response.status());
        List<String> parts = new ArrayList<>();
        for (String value : Arrays.asList(problem.detail(), problem.title(), problem.message())) {
            if (value != null && !value.isBlank() && !parts.contains(value) && (httpStatus == null || !value.equals(httpStatus.getReasonPhrase()))) {
                parts.add(value);
            }
        }
        if (problem.fieldErrors() != null && !problem.fieldErrors().isEmpty()) {
            parts.add(problem.fieldErrors().stream().map(AgentToolResult::describe).collect(Collectors.joining("; ")));
        }
        return parts.isEmpty() ? (response.body() == null ? "no details" : response.body()) : String.join(" | ", parts);
    }

    private static String describe(AgentFieldErrorDTO fieldError) {
        return fieldError.field() + ": " + fieldError.message();
    }

    private static String adviceFor(int status) {
        return switch (status) {
            case 400 -> "Fix the request: the message names the problem. Do not send the same input again.";
            case 401 -> "The token is invalid, expired or revoked. Ask the instructor for a new one.";
            case 403 -> "Not allowed: the token lacks a scope, the instructor lacks the role in this course, or the content is already published. Tell the instructor.";
            case 404 -> "Not found, or not visible to the instructor. Check the ids.";
            default -> status >= 500 ? "The server failed. Creating is not idempotent: look at what exists before you try again." : "";
        };
    }

    private static String shorten(String text) {
        return text.length() <= MAX_CHARACTERS ? text : text.substring(0, MAX_CHARACTERS) + "... [truncated, " + text.length() + " characters in total]";
    }
}
