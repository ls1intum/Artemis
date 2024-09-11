package de.tum.cit.aet.artemis.web.rest.dto.score;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the scores of a student in a course as returned from the server.
 *
 * @param absoluteScore        the points achieved by the student in the course.
 * @param relativeScore        the points achieved by the student in the course divided by the max number of points achievable in the course (an exercise is added to these max
 *                                 points even if its assessment is not done yet).
 * @param currentRelativeScore the points achieved by the student in the course divided by the max number of points reachable in the course (an exercise is not added to these
 *                                 max points if its assessment is not done yet).
 * @param presentationScore    the presentation scores achieved by the student in the course.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentScoresDTO(double absoluteScore, double relativeScore, double currentRelativeScore, double presentationScore) {
}
