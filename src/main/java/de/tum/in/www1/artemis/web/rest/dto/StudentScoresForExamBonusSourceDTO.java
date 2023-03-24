package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;

/**
 * Represents the scores of a student in a course as returned from the server. Includes additional information for the exam bonus calculation.
 * Note: Contains some duplicated parameters from {@link StudentScoresDTO}. This way it can be a record which does not support subclassing.
 *
 * @param absoluteScore               the points achieved by the student in the course.
 * @param relativeScore               the points achieved by the student in the course divided by the max number of points achievable in the course (an exercise is added to
 *                                        these max points even if its assessment is not done yet).
 * @param currentRelativeScore        the points achieved by the student in the course divided by the max number of points reachable in the course (an exercise is not added to
 *                                        these max points if its assessment is not done yet).
 * @param presentationScore           the presentation scores achieved by the student in the course.
 * @param studentId                   the id of the student.
 * @param presentationScorePassed     whether the presentation score is sufficient for the bonus.
 * @param mostSeverePlagiarismVerdict the most severe plagiarism verdict of the student in the course.
 * @param hasParticipated             whether the student has participated in the course.
 */
public record StudentScoresForExamBonusSourceDTO(double absoluteScore, double relativeScore, double currentRelativeScore, int presentationScore, long studentId,
        boolean presentationScorePassed, PlagiarismVerdict mostSeverePlagiarismVerdict, boolean hasParticipated) {
}
