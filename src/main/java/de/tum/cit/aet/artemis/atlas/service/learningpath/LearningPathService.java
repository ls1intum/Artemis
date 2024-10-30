package de.tum.cit.aet.artemis.atlas.service.learningpath;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyGraphEdgeDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyGraphNodeDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathCompetencyGraphDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathHealthDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathInformationDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationOverviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.NgxLearningPathDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearningPathRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;

/**
 * Service Implementation for managing Learning Paths.
 * <p>
 * This includes
 * <ul>
 * <li>the generation of learning paths in courses,</li>
 * <li>performing pageable searches for learning paths,</li>
 * <li>performing health status checks,</li>
 * <li>and retrieval of ngx graph representations.</li>
 * </ul>
 */
@Profile(PROFILE_CORE)
@Service
public class LearningPathService {

    private static final Logger log = LoggerFactory.getLogger(LearningPathService.class);

    private final UserRepository userRepository;

    private final LearningPathRepository learningPathRepository;

    private final CompetencyProgressRepository competencyProgressRepository;

    private final LearningPathNavigationService learningPathNavigationService;

    private final CourseRepository courseRepository;

    private final CompetencyRepository competencyRepository;

    private final CompetencyRelationRepository competencyRelationRepository;

    private final LearningPathNgxService learningPathNgxService;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final CourseCompetencyRepository courseCompetencyRepository;

    public LearningPathService(UserRepository userRepository, LearningPathRepository learningPathRepository, CompetencyProgressRepository competencyProgressRepository,
            LearningPathNavigationService learningPathNavigationService, CourseRepository courseRepository, CompetencyRepository competencyRepository,
            CompetencyRelationRepository competencyRelationRepository, LearningPathNgxService learningPathNgxService,
            LectureUnitCompletionRepository lectureUnitCompletionRepository, StudentParticipationRepository studentParticipationRepository,
            CourseCompetencyRepository courseCompetencyRepository) {
        this.userRepository = userRepository;
        this.learningPathRepository = learningPathRepository;
        this.competencyProgressRepository = competencyProgressRepository;
        this.learningPathNavigationService = learningPathNavigationService;
        this.courseRepository = courseRepository;
        this.competencyRepository = competencyRepository;
        this.competencyRelationRepository = competencyRelationRepository;
        this.learningPathNgxService = learningPathNgxService;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
    }

    /**
     * Enable learning paths for the course and generate learning paths for all students enrolled in the course
     *
     * @param course course the learning paths are created for
     */
    public void enableLearningPathsForCourse(@NotNull Course course) {
        course.setLearningPathsEnabled(true);
        generateLearningPaths(course);
        courseRepository.save(course);
        log.debug("Enabled learning paths for course (id={})", course.getId());
    }

    /**
     * Generate learning paths for all students enrolled in the course
     *
     * @param course course the learning paths are created for
     */
    public void generateLearningPaths(@NotNull Course course) {
        var students = userRepository.getStudents(course);
        students.forEach(student -> generateLearningPathForUser(course, student));
        log.debug("Successfully created learning paths for all {} students in course (id={})", students.size(), course.getId());
    }

    /**
     * Generate learning path for the user in the course if the learning path is not present
     *
     * @param course course that defines the learning path
     * @param user   student for which the learning path is generated
     * @return the learning path of the user
     */
    public LearningPath generateLearningPathForUser(@NotNull Course course, @NotNull User user) {
        var existingLearningPath = learningPathRepository.findByCourseIdAndUserId(course.getId(), user.getId());
        // the learning path has not to be generated if it already exits
        if (existingLearningPath.isPresent()) {
            return existingLearningPath.get();
        }
        LearningPath lpToCreate = new LearningPath();
        lpToCreate.setUser(user);
        lpToCreate.setCourse(course);
        lpToCreate.getCompetencies().addAll(course.getCompetencies());
        lpToCreate.getCompetencies().addAll(course.getPrerequisites());
        var persistedLearningPath = learningPathRepository.save(lpToCreate);
        log.debug("Created LearningPath (id={}) for user (id={}) in course (id={})", persistedLearningPath.getId(), user.getId(), course.getId());
        updateLearningPathProgress(persistedLearningPath);
        return persistedLearningPath;
    }

    /**
     * Search for all learning paths fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged.
     *
     * @param search   the search query defining the search term and the size of the returned page
     * @param courseId the id of the course the learning paths are linked to
     * @return A wrapper object containing a list of all found learning paths and the total number of pages
     */
    public SearchResultPageDTO<LearningPathInformationDTO> getAllOfCourseOnPageWithSize(@NotNull SearchTermPageableSearchDTO<String> search, long courseId) {
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.LEARNING_PATH);
        final var searchTerm = search.getSearchTerm();
        final Page<LearningPath> learningPathPage = learningPathRepository.findByLoginOrNameInCourse(searchTerm, courseId, pageable);
        final List<LearningPathInformationDTO> contentDTOs = learningPathPage.getContent().stream().map(LearningPathInformationDTO::of).toList();
        return new SearchResultPageDTO<>(contentDTOs, learningPathPage.getTotalPages());
    }

    /**
     * Links given competency to all learning paths of the course.
     *
     * @param competency Competency that should be added to each learning path
     * @param courseId   course id that the learning paths belong to
     */
    public void linkCompetencyToLearningPathsOfCourse(@NotNull CourseCompetency competency, long courseId) {
        var course = courseRepository.findWithEagerLearningPathsAndLearningPathCompetenciesByIdElseThrow(courseId);
        var learningPaths = course.getLearningPaths();
        learningPaths.forEach(learningPath -> learningPath.addCompetency(competency));
        learningPathRepository.saveAll(learningPaths);
        log.debug("Linked competency (id={}) to learning paths", competency.getId());
    }

    /**
     * Links a list of competencies to all learning paths of the course.
     *
     * @param competencies The list of competencies that should be added
     * @param courseId     course id that the learning paths belong to
     */
    public void linkCompetenciesToLearningPathsOfCourse(@NotNull List<? extends CourseCompetency> competencies, long courseId) {
        if (competencies.isEmpty()) {
            return;
        }
        var course = courseRepository.findWithEagerLearningPathsAndLearningPathCompetenciesByIdElseThrow(courseId);
        var learningPaths = course.getLearningPaths();
        learningPaths.forEach(learningPath -> learningPath.addCompetencies(new HashSet<>(competencies)));
        learningPathRepository.saveAll(learningPaths);
        log.debug("Linked {} competencies to learning paths", competencies.size());
    }

    /**
     * Remove linked competency from all learning paths of the course.
     *
     * @param competency Competency that should be removed from each learning path
     * @param courseId   course id that the learning paths belong to
     */
    public void removeLinkedCompetencyFromLearningPathsOfCourse(@NotNull CourseCompetency competency, long courseId) {
        var course = courseRepository.findWithEagerLearningPathsAndLearningPathCompetenciesByIdElseThrow(courseId);
        var learningPaths = course.getLearningPaths();
        learningPaths.forEach(learningPath -> learningPath.removeCompetency(competency));
        learningPathRepository.saveAll(learningPaths);
        log.debug("Removed linked competency (id={}) from learning paths", competency.getId());
    }

    /**
     * Updates progress of the learning path specified by course and user id.
     *
     * @param courseId id of the course the learning path is linked to
     * @param userId   id of the user the learning path is linked to
     */
    public void updateLearningPathProgress(long courseId, long userId) {
        final var learningPath = learningPathRepository.findWithEagerCompetenciesByCourseIdAndUserId(courseId, userId);
        learningPath.ifPresent(this::updateLearningPathProgress);
    }

    /**
     * Updates progress of the given learning path. Competencies of the learning path must be loaded eagerly.
     *
     * @param learningPath learning path that is updated
     */
    private void updateLearningPathProgress(@NotNull LearningPath learningPath) {
        final var userId = learningPath.getUser().getId();
        final var competencyIds = learningPath.getCompetencies().stream().map(CourseCompetency::getId).collect(Collectors.toSet());
        final var competencyProgresses = competencyProgressRepository.findAllByCompetencyIdsAndUserId(competencyIds, userId);

        final float completed = competencyProgresses.stream().filter(CompetencyProgressService::isMastered).count();
        final var numberOfCompetencies = learningPath.getCompetencies().size();
        if (numberOfCompetencies == 0) {
            learningPath.setProgress(0);
        }
        else {
            learningPath.setProgress(Math.round(completed * 100 / numberOfCompetencies));
        }
        learningPathRepository.save(learningPath);
        log.debug("Updated LearningPath (id={}) for user (id={})", learningPath.getId(), userId);
    }

    /**
     * Get the learning path for the current user in the given course.
     *
     * @param courseId the id of the course
     * @return the learning path of the current user
     */
    public LearningPathDTO getLearningPathForCurrentUser(long courseId) {
        final var currentUser = userRepository.getUser();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(courseId, currentUser.getId());
        return LearningPathDTO.of(learningPath);
    }

    /**
     * Generate a learning path for the current user in the given course.
     *
     * @param courseId the id of the course
     * @return the generated learning path
     */
    public LearningPathDTO generateLearningPathForCurrentUser(long courseId) {
        final var currentUser = userRepository.getUser();
        final var course = courseRepository.findWithEagerCompetenciesAndPrerequisitesByIdElseThrow(courseId);
        if (learningPathRepository.findByCourseIdAndUserId(courseId, currentUser.getId()).isPresent()) {
            throw new ConflictException("Learning path already exists.", "LearningPath", "learningPathAlreadyExists");
        }
        final var learningPath = generateLearningPathForUser(course, currentUser);
        return LearningPathDTO.of(learningPath);
    }

    /**
     * Start the learning path for the current user
     *
     * @param learningPathId the id of the learning path
     */
    public void startLearningPathForCurrentUser(long learningPathId) {
        final var learningPath = learningPathRepository.findByIdElseThrow(learningPathId);
        final var currentUser = userRepository.getUser();
        if (!learningPath.getUser().equals(currentUser)) {
            throw new AccessForbiddenException("You are not allowed to start this learning path.");
        }
        else if (learningPath.isStartedByStudent()) {
            throw new ConflictException("Learning path already started.", "LearningPath", "learningPathAlreadyStarted");
        }
        learningPath.setStartedByStudent(true);
        learningPathRepository.save(learningPath);
    }

    /**
     * Gets the health status of learning paths for the given course.
     *
     * @param course the course for which the health status should be generated
     * @return dto containing the health status and additional information (missing learning paths) if needed
     */
    public LearningPathHealthDTO getHealthStatusForCourse(@NotNull Course course) {
        Set<LearningPathHealthDTO.HealthStatus> status = new HashSet<>();
        Long numberOfMissingLearningPaths = checkMissingLearningPaths(course, status);
        checkNoCompetencies(course, status);
        checkNoRelations(course, status);

        return new LearningPathHealthDTO(status, numberOfMissingLearningPaths);
    }

    private Long checkMissingLearningPaths(@NotNull Course course, @NotNull Set<LearningPathHealthDTO.HealthStatus> status) {
        long numberOfStudents = userRepository.countUserInGroup(course.getStudentGroupName());
        long numberOfLearningPaths = learningPathRepository.countLearningPathsOfEnrolledStudentsInCourse(course.getId());
        Long numberOfMissingLearningPaths = numberOfStudents - numberOfLearningPaths;

        if (numberOfMissingLearningPaths != 0) {
            status.add(LearningPathHealthDTO.HealthStatus.MISSING);
        }
        else {
            numberOfMissingLearningPaths = null;
        }

        return numberOfMissingLearningPaths;
    }

    private void checkNoCompetencies(@NotNull Course course, @NotNull Set<LearningPathHealthDTO.HealthStatus> status) {
        if (competencyRepository.countByCourse(course) == 0) {
            status.add(LearningPathHealthDTO.HealthStatus.NO_COMPETENCIES);
        }
    }

    private void checkNoRelations(@NotNull Course course, @NotNull Set<LearningPathHealthDTO.HealthStatus> status) {
        if (competencyRelationRepository.countByCourseId(course.getId()) == 0) {
            status.add(LearningPathHealthDTO.HealthStatus.NO_RELATIONS);
        }
    }

    /**
     * Generates the graph of competencies with the student's progress for the given learning path.
     *
     * @param learningPath the learning path for which the graph should be generated
     * @param user         the user for which the progress should be loaded
     * @return dto containing the competencies and relations of the learning path
     */
    public LearningPathCompetencyGraphDTO generateLearningPathCompetencyGraph(@NotNull LearningPath learningPath, @NotNull User user) {
        Set<CourseCompetency> competencies = learningPath.getCompetencies();
        Set<Long> competencyIds = competencies.stream().map(CourseCompetency::getId).collect(Collectors.toSet());
        Map<Long, CompetencyProgress> competencyProgresses = competencyProgressRepository.findAllByCompetencyIdsAndUserId(competencyIds, user.getId()).stream()
                .collect(Collectors.toMap(progress -> progress.getCompetency().getId(), cp -> cp));

        Set<CompetencyGraphNodeDTO> progressDTOs = competencies.stream().map(competency -> {
            var competencyProgressOptional = Optional.ofNullable(competencyProgresses.get(competency.getId()));
            var masteryProgress = competencyProgressOptional.map(CompetencyProgressService::getMasteryProgress).orElse(0.0);
            return CompetencyGraphNodeDTO.of(competency, Math.floor(masteryProgress * 100), CompetencyGraphNodeDTO.CompetencyNodeValueType.MASTERY_PROGRESS);
        }).collect(Collectors.toSet());

        Set<CompetencyRelation> relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(learningPath.getCourse().getId());
        Set<CompetencyGraphEdgeDTO> relationDTOs = relations.stream().map(CompetencyGraphEdgeDTO::of).collect(Collectors.toSet());
        return new LearningPathCompetencyGraphDTO(progressDTOs, relationDTOs);
    }

    /**
     * Generates the graph of competencies with the student's progress for the given learning path.
     *
     * @param courseId the id of the course for which the graph should be generated
     * @return dto containing the competencies and relations of the learning path
     */
    public LearningPathCompetencyGraphDTO generateLearningPathCompetencyInstructorGraph(long courseId) {
        List<CourseCompetency> competencies = courseCompetencyRepository.findByCourseIdOrderById(courseId);
        Set<CompetencyGraphNodeDTO> progressDTOs = competencies.stream().map(competency -> {
            double averageMasteryProgress = competencyProgressRepository.findAverageOfAllNonZeroStudentProgressByCompetencyId(competency.getId());
            return CompetencyGraphNodeDTO.of(competency, averageMasteryProgress, CompetencyGraphNodeDTO.CompetencyNodeValueType.AVERAGE_MASTERY_PROGRESS);
        }).collect(Collectors.toSet());

        Set<CompetencyRelation> relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(courseId);
        Set<CompetencyGraphEdgeDTO> relationDTOs = relations.stream().map(CompetencyGraphEdgeDTO::of).collect(Collectors.toSet());

        return new LearningPathCompetencyGraphDTO(progressDTOs, relationDTOs);
    }

    /**
     * Generates Ngx graph representation of the learning path graph.
     *
     * @param learningPath the learning path for which the Ngx representation should be created
     * @return Ngx graph representation of the learning path
     * @see NgxLearningPathDTO
     */
    public NgxLearningPathDTO generateNgxGraphRepresentation(@NotNull LearningPath learningPath) {
        return this.learningPathNgxService.generateNgxGraphRepresentation(learningPath);
    }

    /**
     * Generates Ngx path representation of the learning path.
     *
     * @param learningPath the learning path for which the Ngx representation should be created
     * @return Ngx path representation of the learning path
     * @see NgxLearningPathDTO
     */
    public NgxLearningPathDTO generateNgxPathRepresentation(@NotNull LearningPath learningPath) {
        return this.learningPathNgxService.generateNgxPathRepresentation(learningPath);
    }

    /**
     * Get the navigation overview for a given learning path.
     *
     * @param learningPathId the id of the learning path
     * @return the navigation overview
     */
    public LearningPathNavigationOverviewDTO getLearningPathNavigationOverview(long learningPathId) {
        var learningPath = findWithCompetenciesAndReleasedLearningObjectsAndCompletedUsersById(learningPathId);
        if (!userRepository.getUser().equals(learningPath.getUser())) {
            throw new AccessForbiddenException("You are not allowed to access this learning path");
        }
        return learningPathNavigationService.getNavigationOverview(learningPath);
    }

    /**
     * Finds a learning path by its id and eagerly fetches the competencies, linked and released lecture units and exercises, and the corresponding domain objects storing the
     * progress of the connected user.
     * <p>
     * As Spring Boot 3 doesn't support conditional JOIN FETCH statements, we have to retrieve the data manually.
     *
     * @param learningPathId the id of the learning path to fetch
     * @return the learning path with fetched data
     */
    public LearningPath findWithCompetenciesAndReleasedLearningObjectsAndCompletedUsersById(long learningPathId) {
        LearningPath learningPath = learningPathRepository.findWithCompetenciesAndLectureUnitsAndExercisesByIdElseThrow(learningPathId);

        // Remove exercises that are not visible to students
        learningPath.getCompetencies().forEach(competency -> competency
                .setExerciseLinks(competency.getExerciseLinks().stream().filter(exerciseLink -> exerciseLink.getExercise().isVisibleToStudents()).collect(Collectors.toSet())));
        // Remove unreleased lecture units as well as exercise units, since they are already retrieved as exercises
        learningPath.getCompetencies()
                .forEach(competency -> competency.setLectureUnitLinks(competency.getLectureUnitLinks().stream()
                        .filter(lectureUnitLink -> !(lectureUnitLink.getLectureUnit() instanceof ExerciseUnit) && lectureUnitLink.getLectureUnit().isVisibleToStudents())
                        .collect(Collectors.toSet())));

        if (learningPath.getUser() == null) {
            learningPath.getCompetencies().forEach(competency -> {
                competency.setUserProgress(Collections.emptySet());
                competency.getLectureUnitLinks().forEach(lectureUnitLink -> lectureUnitLink.getLectureUnit().setCompletedUsers(Collections.emptySet()));
                competency.getExerciseLinks().forEach(exerciseLink -> exerciseLink.getExercise().setStudentParticipations(Collections.emptySet()));
            });
            return learningPath;
        }
        Long userId = learningPath.getUser().getId();
        Set<Long> competencyIds = learningPath.getCompetencies().stream().map(CourseCompetency::getId).collect(Collectors.toSet());
        Map<Long, CompetencyProgress> competencyProgresses = competencyProgressRepository.findAllByCompetencyIdsAndUserId(competencyIds, userId).stream()
                .collect(Collectors.toMap(progress -> progress.getCompetency().getId(), cp -> cp));
        Set<LectureUnit> lectureUnits = learningPath.getCompetencies().stream()
                .flatMap(competency -> competency.getLectureUnitLinks().stream().map(CompetencyLectureUnitLink::getLectureUnit)).collect(Collectors.toSet());
        Map<Long, LectureUnitCompletion> completions = lectureUnitCompletionRepository.findByLectureUnitsAndUserId(lectureUnits, userId).stream()
                .collect(Collectors.toMap(completion -> completion.getLectureUnit().getId(), cp -> cp));
        Set<Long> exerciseIds = learningPath.getCompetencies().stream().flatMap(competency -> competency.getExerciseLinks().stream())
                .map(exerciseLink -> exerciseLink.getExercise().getId()).collect(Collectors.toSet());
        Map<Long, StudentParticipation> studentParticipations = studentParticipationRepository.findDistinctAllByExerciseIdInAndStudentId(exerciseIds, userId).stream()
                .collect(Collectors.toMap(participation -> participation.getExercise().getId(), sp -> sp));
        learningPath.getCompetencies().forEach(competency -> {
            if (competencyProgresses.containsKey(competency.getId())) {
                competency.setUserProgress(Set.of(competencyProgresses.get(competency.getId())));
            }
            else {
                competency.setUserProgress(Collections.emptySet());
            }
            competency.getLectureUnitLinks().stream().map(CompetencyLectureUnitLink::getLectureUnit).forEach(lectureUnit -> {
                if (completions.containsKey(lectureUnit.getId())) {
                    lectureUnit.setCompletedUsers(Set.of(completions.get(lectureUnit.getId())));
                }
                else {
                    lectureUnit.setCompletedUsers(Collections.emptySet());
                }
            });
            competency.getExerciseLinks().stream().map(CompetencyExerciseLink::getExercise).forEach(exercise -> {
                if (studentParticipations.containsKey(exercise.getId())) {
                    exercise.setStudentParticipations(Set.of(studentParticipations.get(exercise.getId())));
                }
                else {
                    exercise.setStudentParticipations(Collections.emptySet());
                }
            });
        });

        return learningPath;
    }
}
