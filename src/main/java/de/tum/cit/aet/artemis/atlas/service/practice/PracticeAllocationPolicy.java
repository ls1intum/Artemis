package de.tum.cit.aet.artemis.atlas.service.practice;

import java.util.List;
import java.util.Set;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;

/**
 * Interface defining the contract for quiz question allocation policies in adaptive practice.
 * Concrete policies map student competency estimates (or baseline conditions) to a set of allocated items.
 */
public interface PracticeAllocationPolicy {

    /**
     * Allocates questions for the adaptive portion of a practice session.
     *
     * @param user               the student requesting the quiz
     * @param competencies       the course competencies
     * @param count              the number of questions to allocate
     * @param availableQuestions the candidate pool of questions available for practice in the course
     * @return list of allocated quiz questions
     */
    List<QuizQuestion> allocateQuestions(User user, Set<CourseCompetency> competencies, int count, List<QuizQuestion> availableQuestions);
}
