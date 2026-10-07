package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One (student exam, exercise) pair whose participation the exam preparation has to set up.
 * <p>
 * Setting an exercise up needs nothing from the student exam but its id, and nothing from the student but the id and
 * the login their repository is named after, so this projection is read instead of the student exam graph. That graph
 * repeats the exam row and the full exercise row once per student exam: for 2000 students and four exercises it
 * transfers megabytes to convey a few kilobytes of distinct data, and it grows with the length of every problem
 * statement. The login is still repeated once per exercise here, but a login is orders of magnitude smaller than an
 * exercise row.
 *
 * @param studentExamId the id of the student exam
 * @param userId        the id of the student the student exam belongs to
 * @param userLogin     the login of that student, which their exercise repository is named after
 * @param exerciseId    the id of one exercise of that student exam
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StudentExamExerciseStartDTO(long studentExamId, long userId, String userLogin, long exerciseId) {
}
