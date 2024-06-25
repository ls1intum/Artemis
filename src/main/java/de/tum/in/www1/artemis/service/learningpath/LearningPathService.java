package de.tum.in.www1.artemis.service.learningpath;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnitCompletion;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.CompetencyProgressRepository;
import de.tum.in.www1.artemis.repository.CompetencyRelationRepository;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LearningPathRepository;
import de.tum.in.www1.artemis.repository.LectureUnitCompletionRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.competency.CompetencyProgressService;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyProgressDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyRelationDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathCompetencyGraphDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathHealthDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.NgxLearningPathDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

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

    private final CourseRepository courseRepository;

    private final CompetencyRepository competencyRepository;

    private final CompetencyRelationRepository competencyRelationRepository;

    private final LearningPathNgxService learningPathNgxService;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public LearningPathService(UserRepository userRepository, LearningPathRepository learningPathRepository, CompetencyProgressRepository competencyProgressRepository,
            CourseRepository courseRepository, CompetencyRepository competencyRepository, CompetencyRelationRepository competencyRelationRepository,
            LearningPathNgxService learningPathNgxService, LectureUnitCompletionRepository lectureUnitCompletionRepository,
            StudentParticipationRepository studentParticipationRepository) {
        this.userRepository = userRepository;
        this.learningPathRepository = learningPathRepository;
        this.competencyProgressRepository = competencyProgressRepository;
        this.courseRepository = courseRepository;
        this.competencyRepository = competencyRepository;
        this.competencyRelationRepository = competencyRelationRepository;
        this.learningPathNgxService = learningPathNgxService;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
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
    public void linkCompetencyToLearningPathsOfCourse(@NotNull Competency competency, long courseId) {
        var course = courseRepository.findWithEagerLearningPathsAndCompetenciesByIdElseThrow(courseId);
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
    public void linkCompetenciesToLearningPathsOfCourse(@NotNull List<Competency> competencies, long courseId) {
        if (competencies.isEmpty()) {
            return;
        }
        var course = courseRepository.findWithEagerLearningPathsAndCompetenciesByIdElseThrow(courseId);
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
    public void removeLinkedCompetencyFromLearningPathsOfCourse(@NotNull Competency competency, long courseId) {
        var course = courseRepository.findWithEagerLearningPathsAndCompetenciesByIdElseThrow(courseId);
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
        final var competencyIds = learningPath.getCompetencies().stream().map(Competency::getId).collect(Collectors.toSet());
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
     * Gets the health status of learning paths for the given course.
     *
     * @param course the course for which the health status should be generated
     * @return dto containing the health status and additional information (missing learning paths) if needed
     */
    public LearningPathHealthDTO getHealthStatusForCourse(@NotNull Course course) {
        if (!course.getLearningPathsEnabled()) {
            return new LearningPathHealthDTO(Set.of(LearningPathHealthDTO.HealthStatus.DISABLED));
        }

        Set<LearningPathHealthDTO.HealthStatus> status = new HashSet<>();
        Long numberOfMissingLearningPaths = checkMissingLearningPaths(course, status);
        checkNoCompetencies(course, status);
        checkNoRelations(course, status);

        // if no issues where found, add OK status
        if (status.isEmpty()) {
            status.add(LearningPathHealthDTO.HealthStatus.OK);
        }

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
     * @return dto containing the competencies and relations of the learning path
     */
    public LearningPathCompetencyGraphDTO generateLearningPathCompetencyGraph(@NotNull LearningPath learningPath) {
        Set<CompetencyProgress> progresses = competencyProgressRepository.findAllByUserIdAndLearningPathId(learningPath.getUser().getId(), learningPath.getId());
        Set<CompetencyProgressDTO> progressDTOs = progresses.stream().map(CompetencyProgressDTO::of).collect(Collectors.toSet());

        Set<CompetencyRelation> relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(learningPath.getCourse().getId());
        Set<CompetencyRelationDTO> relationDTOs = relations.stream().map(CompetencyRelationDTO::of).collect(Collectors.toSet());
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
     * Finds a learning path by its id and eagerly fetches the competencies, linked lecture units and exercises, and the corresponding domain objects storing the progress of the
     * connected user.
     * <p>
     * As Spring Boot 3 doesn't support conditional JOIN FETCH statements, we have to retrieve the data manually.
     *
     * @param learningPathId the id of the learning path to fetch
     * @return the learning path with fetched data
     */
    public LearningPath findWithCompetenciesAndLearningObjectsAndCompletedUsersById(long learningPathId) {
        LearningPath learningPath = learningPathRepository.findWithCompetenciesAndLectureUnitsAndExercisesByIdElseThrow(learningPathId);
        // Remove exercise units, since they are already retrieved as exercises
        learningPath.getCompetencies().stream().forEach(competency -> competency
                .setLectureUnits(competency.getLectureUnits().stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).collect(Collectors.toSet())));
        if (learningPath.getUser() == null) {
            learningPath.getCompetencies().forEach(competency -> {
                competency.setUserProgress(Collections.emptySet());
                competency.getLectureUnits().forEach(lectureUnit -> lectureUnit.setCompletedUsers(Collections.emptySet()));
                competency.getExercises().forEach(exercise -> exercise.setStudentParticipations(Collections.emptySet()));
            });
            return learningPath;
        }
        Long userId = learningPath.getUser().getId();
        Set<Long> competencyIds = learningPath.getCompetencies().stream().map(Competency::getId).collect(Collectors.toSet());
        Map<Long, CompetencyProgress> competencyProgresses = competencyProgressRepository.findAllByCompetencyIdsAndUserId(competencyIds, userId).stream()
                .collect(Collectors.toMap(progress -> progress.getCompetency().getId(), cp -> cp));
        Set<LectureUnit> lectureUnits = learningPath.getCompetencies().stream().flatMap(competency -> competency.getLectureUnits().stream()).collect(Collectors.toSet());
        Map<Long, LectureUnitCompletion> completions = lectureUnitCompletionRepository.findByLectureUnitsAndUserId(lectureUnits, userId).stream()
                .collect(Collectors.toMap(completion -> completion.getLectureUnit().getId(), cp -> cp));
        Set<Long> exerciseIds = learningPath.getCompetencies().stream().flatMap(competency -> competency.getExercises().stream()).map(Exercise::getId).collect(Collectors.toSet());
        Map<Long, StudentParticipation> studentParticipations = studentParticipationRepository.findDistinctAllByExerciseIdInAndStudentId(exerciseIds, userId).stream()
                .collect(Collectors.toMap(participation -> participation.getExercise().getId(), sp -> sp));
        learningPath.getCompetencies().forEach(competency -> {
            if (competencyProgresses.containsKey(competency.getId())) {
                competency.setUserProgress(Set.of(competencyProgresses.get(competency.getId())));
            }
            else {
                competency.setUserProgress(Collections.emptySet());
            }
            competency.getLectureUnits().forEach(lectureUnit -> {
                if (completions.containsKey(lectureUnit.getId())) {
                    lectureUnit.setCompletedUsers(Set.of(completions.get(lectureUnit.getId())));
                }
                else {
                    lectureUnit.setCompletedUsers(Collections.emptySet());
                }
            });
            competency.getExercises().forEach(exercise -> {
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
