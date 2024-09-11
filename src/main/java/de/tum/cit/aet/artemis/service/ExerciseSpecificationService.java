package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.Course_;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.Exercise_;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise_;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.cit.aet.artemis.domain.exam.Exam;
import de.tum.cit.aet.artemis.domain.exam.Exam_;
import de.tum.cit.aet.artemis.domain.exam.ExerciseGroup;
import de.tum.cit.aet.artemis.domain.exam.ExerciseGroup_;
import de.tum.cit.aet.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;

@Profile(PROFILE_CORE)
@Service
public class ExerciseSpecificationService {

    private final AuthorizationCheckService authCheckService;

    public ExerciseSpecificationService(AuthorizationCheckService authCheckService) {
        this.authCheckService = authCheckService;
    }

    /**
     * Creates a {@link Specification} to find exercises with the matching searchTerm.
     * Allows to filter course for only course or exam exercises.
     * Results are paged to reduce database load.
     *
     * @param searchTerm     Searches for an exercise id, exercise name or course name matching this input
     * @param isCourseFilter Whether to search in courses for exercises
     * @param isExamFilter   Whether to search in exams for exercises
     * @param user           The user for whom to fetch all available exercises
     * @param pageable       Defining how to sort the result and on which
     * @param <T>            The generic exercise type
     * @return A specification that can get passed to the exercise repository.
     * @see org.springframework.data.jpa.repository.JpaSpecificationExecutor#findAll(Specification, Pageable)
     */
    public <T extends Exercise> Specification<T> getExerciseSearchSpecification(String searchTerm, boolean isCourseFilter, boolean isExamFilter, User user, Pageable pageable) {
        return (root, query, criteriaBuilder) -> {
            Join<T, Course> joinCourse = root.join(Exercise_.COURSE, JoinType.LEFT);
            Join<T, ExerciseGroup> joinExerciseGroup = root.join(Exercise_.EXERCISE_GROUP, JoinType.LEFT);
            Join<ExerciseGroup, Exam> joinExam = joinExerciseGroup.join(ExerciseGroup_.EXAM, JoinType.LEFT);
            Join<Exam, Course> joinExamCourse = joinExam.join(Exam_.COURSE, JoinType.LEFT);

            Predicate idMatchesSearch = criteriaBuilder.equal(root.get(Exercise_.ID).as(String.class), searchTerm);
            Predicate exerciseTitleMatches = criteriaBuilder.like(root.get(Exercise_.TITLE), "%" + searchTerm + "%");
            Predicate courseTitleMatches = criteriaBuilder.like(joinCourse.get(Course_.TITLE), "%" + searchTerm + "%");
            Predicate examCourseTitleMatches = criteriaBuilder.like(joinExamCourse.get(Course_.TITLE), "%" + searchTerm + "%");

            Predicate matchingCourseExercise = criteriaBuilder.or(idMatchesSearch, exerciseTitleMatches, courseTitleMatches);
            Predicate matchingExamExercise = criteriaBuilder.or(idMatchesSearch, exerciseTitleMatches, examCourseTitleMatches);

            Predicate filter;

            if (!authCheckService.isAdmin(user)) {
                var groups = user.getGroups();
                Predicate atLeastEditorInCourse = criteriaBuilder.or(joinCourse.get(Course_.instructorGroupName).in(groups), joinCourse.get(Course_.editorGroupName).in(groups));
                Predicate atLeastEditorInExam = criteriaBuilder.or(joinExamCourse.get(Course_.instructorGroupName).in(groups),
                        joinExamCourse.get(Course_.editorGroupName).in(groups));

                Predicate availableCourseExercise = criteriaBuilder.and(matchingCourseExercise, atLeastEditorInCourse);
                Predicate availableExamExercise = criteriaBuilder.and(matchingExamExercise, atLeastEditorInExam);

                if (isCourseFilter && isExamFilter) {
                    filter = criteriaBuilder.or(availableCourseExercise, availableExamExercise);
                }
                else if (isCourseFilter) {
                    filter = availableCourseExercise;
                }
                else {
                    filter = availableExamExercise;
                }
            }
            else {
                Predicate isCourseExercise = joinCourse.isNotNull();
                Predicate isExamExercise = joinExerciseGroup.isNotNull();
                if (isCourseFilter && isExamFilter) {
                    filter = criteriaBuilder.or(matchingCourseExercise, matchingExamExercise);
                }
                else if (isCourseFilter) {
                    filter = criteriaBuilder.and(matchingCourseExercise, isCourseExercise);
                }
                else {
                    filter = criteriaBuilder.and(matchingExamExercise, isExamExercise);
                }
            }
            return filter;
        };
    }

    /**
     * Creates a {@link Specification} to filter for programming exercises with the given programming language and SCA enabled.
     *
     * @param programmingLanguage the language to filter for
     * @return a Specification that can get passed to the @{@link de.tum.cit.aet.artemis.repository.ProgrammingExerciseRepository}
     * @see de.tum.cit.aet.artemis.service.programming.ProgrammingExerciseService#getAllWithSCAOnPageWithSize(SearchTermPageableSearchDTO, boolean, boolean, ProgrammingLanguage,
     *      User)
     */
    public Specification<ProgrammingExercise> createSCAFilter(ProgrammingLanguage programmingLanguage) {
        return (root, query, criteriaBuilder) -> {
            Predicate scaActive = criteriaBuilder.isTrue(root.get(ProgrammingExercise_.STATIC_CODE_ANALYSIS_ENABLED));
            Predicate sameLanguage = criteriaBuilder.equal(root.get(ProgrammingExercise_.PROGRAMMING_LANGUAGE), programmingLanguage);
            return criteriaBuilder.and(scaActive, sameLanguage);
        };
    }
}
