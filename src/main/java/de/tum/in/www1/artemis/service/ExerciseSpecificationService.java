package de.tum.in.www1.artemis.service;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.Exam_;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup_;

@Service
public class ExerciseSpecificationService {

    private final AuthorizationCheckService authCheckService;

    public ExerciseSpecificationService(AuthorizationCheckService authCheckService) {
        this.authCheckService = authCheckService;
    }

    public <T extends Exercise> Specification<T> getExerciseSearchSpecification(String searchTerm, boolean isCourseFilter, boolean isExamFilter, User user, Pageable pageable) {
        return (root, query, criteriaBuilder) -> {
            Join<T, Course> joinCourse = root.join(Exercise_.COURSE, JoinType.LEFT);
            Join<T, ExerciseGroup> joinExerciseGroup = root.join(Exercise_.EXERCISE_GROUP, JoinType.LEFT);
            Join<ExerciseGroup, Exam> joinExam = joinExerciseGroup.join(ExerciseGroup_.EXAM, JoinType.LEFT);
            Join<Exam, Course> joinExamCourse = joinExam.join(Exam_.COURSE, JoinType.LEFT);

            Predicate idMatchesSearch = criteriaBuilder.equal(criteriaBuilder.concat(root.get(Exercise_.ID), ""), searchTerm);
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

                return filter;
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

            query.orderBy(QueryUtils.toOrders(pageable.getSort(), root, criteriaBuilder));
            return filter;
        };
    }

    public Specification<ProgrammingExercise> getExerciseSearchSpecification(String searchTerm, boolean isCourseFilter, boolean isExamFilter, boolean isSCAFilter, User user,
            Pageable pageable) {
        Specification<ProgrammingExercise> specification = getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        if (isSCAFilter) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.isTrue(root.get(ProgrammingExercise_.STATIC_CODE_ANALYSIS_ENABLED)));
        }
        return specification;
    }
}
