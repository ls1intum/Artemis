package de.tum.in.www1.artemis.service;

import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

@Service
public class LearningPathService {

    private final Logger log = LoggerFactory.getLogger(LearningPathService.class);

    private final UserRepository userRepository;

    private final LearningPathRepository learningPathRepository;

    private final CompetencyProgressRepository competencyProgressRepository;

    private final CourseRepository courseRepository;

    public LearningPathService(UserRepository userRepository, LearningPathRepository learningPathRepository, CompetencyProgressRepository competencyProgressRepository,
            CourseRepository courseRepository) {
        this.userRepository = userRepository;
        this.learningPathRepository = learningPathRepository;
        this.competencyProgressRepository = competencyProgressRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Generate learning paths for all students enrolled in the course
     *
     * @param course course the learning paths are created for
     */
    public void generateLearningPaths(@NotNull Course course) {
        var students = userRepository.getStudents(course);
        students.forEach((student) -> generateLearningPathForUser(course, student));
        log.debug("Successfully created learning paths for all {} students in course (id={})", students.size(), course.getId());
    }

    /**
     * Generate learning path for the user in the course
     *
     * @param course course that defines the learning path
     * @param user   student for which the learning path is generated
     */
    public void generateLearningPathForUser(@NotNull Course course, @NotNull User user) {
        var existingLearningPath = learningPathRepository.findByCourseIdAndUserId(course.getId(), user.getId());
        if (existingLearningPath.isEmpty()) {
            LearningPath lpToCreate = new LearningPath();
            lpToCreate.setUser(user);
            lpToCreate.setCourse(course);
            lpToCreate.getCompetencies().addAll(course.getCompetencies());
            var persistedLearningPath = learningPathRepository.save(lpToCreate);
            log.debug("Created LearningPath (id={}) for user (id={}) in course (id={})", persistedLearningPath.getId(), user.getId(), course.getId());
            updateLearningPathProgress(persistedLearningPath);
        }
    }

    /**
     * Search for all learning paths fitting a {@link PageableSearchDTO search query}. The result is paged.
     *
     * @param search the search query defining the search term and the size of the returned page
     * @param course the course the learning paths are linked to
     * @return A wrapper object containing a list of all found learning paths and the total number of pages
     */
    public SearchResultPageDTO<LearningPath> getAllOfCourseOnPageWithSize(final PageableSearchDTO<String> search, final Course course) {
        final var pageable = PageUtil.createLearningPathPageRequest(search);
        final var searchTerm = search.getSearchTerm();
        final Page<LearningPath> learningPathPage = learningPathRepository.findByLoginInCourse(searchTerm, course.getId(), pageable);
        return new SearchResultPageDTO<>(learningPathPage.getContent(), learningPathPage.getTotalPages());
    }

    /**
     * Links given competency to all learning paths of the course.
     *
     * @param competency Competency that should be added to each learning path
     * @param courseId   course id that the learning paths belong to
     */
    public void linkCompetencyToLearningPathsOfCourse(Competency competency, long courseId) {
        var course = courseRepository.findWithEagerLearningPathsAndCompetenciesByIdElseThrow(courseId);
        var learningPaths = course.getLearningPaths();
        learningPaths.forEach(learningPath -> learningPath.addCompetency(competency));
        learningPathRepository.saveAll(learningPaths);
        log.debug("Linked competency (id={}) to learning paths", competency.getId());
    }

    /**
     * Remove linked competency from all learning paths of the course.
     *
     * @param competency Competency that should be removed from each learning path
     * @param courseId   course id that the learning paths belong to
     */
    public void removeLinkedCompetencyFromLearningPathsOfCourse(Competency competency, long courseId) {
        var course = courseRepository.findWithEagerLearningPathsAndCompetenciesByIdElseThrow(courseId);
        var learningPaths = course.getLearningPaths();
        learningPaths.forEach(learningPath -> learningPath.removeCompetency(competency));
        learningPathRepository.saveAll(learningPaths);
        log.debug("Removed linked competency (id={}) from learning paths", competency.getId());
    }

    public void updateLearningPathProgress(final long learningPathId) {
        final var learningPath = learningPathRepository.findWithEagerCompetenciesByIdElseThrow(learningPathId);
        this.updateLearningPathProgress(learningPath);
    }

    /**
     * Updates progress of the learning path specified by course and user id.
     *
     * @param courseId id of the course the learning path is linked to
     * @param userId   id of the user the learning path is linked to
     */
    public void updateLearningPathProgress(final long courseId, final long userId) {
        final var learningPath = learningPathRepository.findWithEagerCompetenciesByCourseIdAndUserId(courseId, userId);
        learningPath.ifPresent(this::updateLearningPathProgress);
    }

    /**
     * Updates progress of the given learning path. Competencies of the learning path must be loaded eagerly.
     *
     * @param learningPath learning path that is updated
     */
    private void updateLearningPathProgress(final LearningPath learningPath) {
        final var userId = learningPath.getUser().getId();
        final var competencyIds = learningPath.getCompetencies().stream().map(Competency::getId).collect(Collectors.toSet());
        final var competencyProgresses = competencyProgressRepository.findAllByCompetencyIdsAndUserId(competencyIds, userId);

        // TODO: consider optional competencies
        final var completed = (float) competencyProgresses.stream().filter(CompetencyProgressService::isMastered).count();
        final var numberOfCompetencies = learningPath.getCompetencies().size();
        learningPath.setProgress(numberOfCompetencies == 0 ? 0 : Math.round(completed * 100 / (float) numberOfCompetencies));
        learningPathRepository.save(learningPath);
        log.debug("Updated LearningPath (id={}) for user (id={})", learningPath.getId(), userId);
    }
}
