package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyRepositoryApi;
import de.tum.cit.aet.artemis.atlas.api.PrerequisitesApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseLoadService {

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Optional<CompetencyRepositoryApi> competencyRepositoryApi;

    private final Optional<PrerequisitesApi> prerequisitesApi;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    public CourseLoadService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, Optional<LectureRepositoryApi> lectureRepositoryApi,
            Optional<CompetencyRepositoryApi> competencyRepositoryApi, Optional<de.tum.cit.aet.artemis.atlas.api.PrerequisitesApi> prerequisitesApi,
            Optional<ExamRepositoryApi> examRepositoryApi) {
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.competencyRepositoryApi = competencyRepositoryApi;
        this.prerequisitesApi = prerequisitesApi;
        this.examRepositoryApi = examRepositoryApi;
    }

    public Course loadCourseWithExercisesLecturesLectureUnitsCompetenciesPrerequisitesAndExams(long courseId) {
        ZonedDateTime now = ZonedDateTime.now();
        Course course = loadCourseWithExercisesLecturesLectureUnitsCompetenciesAndPrerequisites(courseId);
        Set<Exam> visibleExams = new HashSet<>();
        if (examRepositoryApi.isPresent()) {
            visibleExams = examRepositoryApi.orElseThrow().findAllVisibleByCourseId(courseId, now);
        }
        course.setExams(visibleExams);
        return course;
    }

    public Course loadCourseWithExercisesLecturesLectureUnitsCompetenciesAndPrerequisites(long courseId) {
        ZonedDateTime now = ZonedDateTime.now();
        Course course = courseRepository.findByIdElseThrow(courseId);
        Set<Exercise> releasedExercises = exerciseRepository.findAllReleasedExercisesByCourseId(courseId, now);
        Set<Lecture> visibleLectures = new HashSet<>();
        if (lectureRepositoryApi.isPresent()) {
            visibleLectures = lectureRepositoryApi.orElseThrow().findAllVisibleByCourseIdWithEagerLectureUnits(courseId, now);
        }
        Set<Competency> competencies = new HashSet<>();
        if (competencyRepositoryApi.isPresent()) {
            competencies = competencyRepositoryApi.orElseThrow().findAllByCourseId(courseId);
        }
        Set<Prerequisite> prerequisites = new HashSet<>();
        if (prerequisitesApi.isPresent()) {
            prerequisites = prerequisitesApi.orElseThrow().findAllByCourseId(courseId);
        }
        course.setExercises(releasedExercises);
        course.setLectures(visibleLectures);
        course.setCompetencies(competencies);
        course.setPrerequisites(prerequisites);
        return course;
    }

}
