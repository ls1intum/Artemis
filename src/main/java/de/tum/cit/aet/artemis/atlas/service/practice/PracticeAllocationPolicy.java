package de.tum.cit.aet.artemis.atlas.service.practice;

import java.util.List;
import java.util.Set;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolCandidateDTO;

/**
 * Interface defining the contract for quiz question allocation policies in adaptive practice.
 * Concrete policies map student competency estimates (or baseline conditions) to a set of allocated candidate items from the Quiz Question Pool.
 */
public interface PracticeAllocationPolicy {

    /**
     * Allocates candidate questions for a practice session from lightweight candidate metadata.
     *
     * @param user         the student requesting the quiz
     * @param competencies the course competencies
     * @param count        the number of questions to allocate
     * @param candidates   the candidate metadata available for practice in the course
     * @return list of allocated candidate DTOs
     */
    List<QuizQuestionPoolCandidateDTO> allocateQuestions(User user, Set<CourseCompetency> competencies, int count, List<QuizQuestionPoolCandidateDTO> candidates);
}
