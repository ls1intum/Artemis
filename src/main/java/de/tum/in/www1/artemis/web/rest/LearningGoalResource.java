package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.LearningGoalProgressService;
import de.tum.in.www1.artemis.service.LearningGoalService;
import de.tum.in.www1.artemis.service.util.RoundingUtil;
import de.tum.in.www1.artemis.web.rest.dto.CourseLearningGoalProgressDTO;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class LearningGoalResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(LearningGoalResource.class);

    private static final String ENTITY_NAME = "learningGoal";

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final LearningGoalRepository learningGoalRepository;

    private final LearningGoalRelationRepository learningGoalRelationRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final LearningGoalService learningGoalService;

    private final LearningGoalProgressRepository learningGoalProgressRepository;

    private final ExerciseRepository exerciseRepository;

    private final LearningGoalProgressService learningGoalProgressService;

    public LearningGoalResource(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            LearningGoalRepository learningGoalRepository, LearningGoalRelationRepository learningGoalRelationRepository, LectureUnitRepository lectureUnitRepository,
            LearningGoalService learningGoalService, LearningGoalProgressRepository learningGoalProgressRepository, ExerciseRepository exerciseRepository,
            LearningGoalProgressService learningGoalProgressService) {
        this.courseRepository = courseRepository;
        this.learningGoalRelationRepository = learningGoalRelationRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.learningGoalRepository = learningGoalRepository;
        this.learningGoalService = learningGoalService;
        this.learningGoalProgressRepository = learningGoalProgressRepository;
        this.exerciseRepository = exerciseRepository;
        this.learningGoalProgressService = learningGoalProgressService;
    }

    /**
     * GET /learning-goals/:learningGoalId/title : Returns the title of the learning goal with the given id
     *
     * @param learningGoalId the id of the learning goal
     * @return the title of the learning goal wrapped in an ResponseEntity or 404 Not Found if no learning goal with that id exists
     */
    @GetMapping("/learning-goals/{learningGoalId}/title")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<String> getLearningGoalTitle(@PathVariable Long learningGoalId) {
        final var title = learningGoalRepository.getLearningGoalTitle(learningGoalId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * Search for all learning goals by title and course title. The result is pageable.
     *
     * @param search The pageable search containing the page size, page number and query string
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("learning-goals")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<SearchResultPageDTO<LearningGoal>> getAllLecturesOnPage(PageableSearchDTO<String> search) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(learningGoalService.getAllOnPageWithSize(search, user));
    }

    /**
     * GET /courses/:courseId/learning-goals : gets all the learning goals of a course
     *
     * @param courseId the id of the course for which the learning goals should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the found learning goals
     */
    @GetMapping("/courses/{courseId}/learning-goals")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<LearningGoal>> getLearningGoals(@PathVariable Long courseId) {
        log.debug("REST request to get learning goals for course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        Set<LearningGoal> learningGoals = learningGoalRepository.findAllForCourse(course.getId());
        return ResponseEntity.ok(new ArrayList<>(learningGoals));
    }

    /**
     * GET /courses/:courseId/learning-goals/:learningGoalId : gets the learning goal with the specified id
     *
     * @param learningGoalId the id of the learning goal to retrieve
     * @param courseId       the id of the course to which the learning goal belongs
     * @return the ResponseEntity with status 200 (OK) and with body the learning goal, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}/learning-goals/{learningGoalId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<LearningGoal> getLearningGoal(@PathVariable Long learningGoalId, @PathVariable Long courseId) {
        log.debug("REST request to get LearningGoal : {}", learningGoalId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var learningGoal = learningGoalRepository.findByIdWithExercisesAndLectureUnitsElseThrow(learningGoalId);
        checkAuthorizationForLearningGoal(Role.STUDENT, course, learningGoal);

        learningGoal.setUserProgress(learningGoalProgressRepository.findByLearningGoalIdAndUserId(learningGoalId, user.getId()).map(Set::of).orElse(Set.of()));
        // Set completion status and remove exercise units (redundant as we also return all exercises)
        learningGoal.setLectureUnits(learningGoal.getLectureUnits().stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit))
                .filter(lectureUnit -> authorizationCheckService.isAllowedToSeeLectureUnit(lectureUnit, user)).map(lectureUnit -> {
                    lectureUnit.setCompleted(lectureUnit.isCompletedFor(user));
                    return lectureUnit;
                }).collect(Collectors.toSet()));
        learningGoal.setExercises(
                learningGoal.getExercises().stream().filter(exercise -> authorizationCheckService.isAllowedToSeeExercise(exercise, user)).collect(Collectors.toSet()));
        return ResponseEntity.ok().body(learningGoal);
    }

    /**
     * PUT /courses/:courseId/learning-goals : Updates an existing learning goal.
     *
     * @param courseId     the id of the course to which the learning goals belongs
     * @param learningGoal the learningGoal to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated learningGoal
     */
    @PutMapping("/courses/{courseId}/learning-goals")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoal> updateLearningGoal(@PathVariable Long courseId, @RequestBody LearningGoal learningGoal) {
        log.debug("REST request to update LearningGoal : {}", learningGoal);
        if (learningGoal.getId() == null) {
            throw new BadRequestException();
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        var existingLearningGoal = this.learningGoalRepository.findByIdWithLectureUnitsElseThrow(learningGoal.getId());
        checkAuthorizationForLearningGoal(Role.INSTRUCTOR, course, existingLearningGoal);

        existingLearningGoal.setTitle(learningGoal.getTitle());
        existingLearningGoal.setDescription(learningGoal.getDescription());
        existingLearningGoal.setTaxonomy(learningGoal.getTaxonomy());
        existingLearningGoal.setMasteryThreshold(learningGoal.getMasteryThreshold());
        var persistedLearningGoal = learningGoalRepository.save(existingLearningGoal);

        linkLectureUnitsToLearningGoal(persistedLearningGoal, learningGoal.getLectureUnits(), existingLearningGoal.getLectureUnits());

        if (learningGoal.getLectureUnits().size() != existingLearningGoal.getLectureUnits().size()
                || !existingLearningGoal.getLectureUnits().containsAll(learningGoal.getLectureUnits())) {
            log.debug("Linked lecture units changed, updating student progress for learning goal...");
            learningGoalProgressService.updateProgressByLearningGoalAsync(persistedLearningGoal);
        }

        return ResponseEntity.ok(persistedLearningGoal);
    }

    /**
     * POST /courses/:courseId/learning-goals : creates a new learning goal.
     *
     * @param courseId     the id of the course to which the learning goal should be added
     * @param learningGoal the learning goal that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new learning goal
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses/{courseId}/learning-goals")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoal> createLearningGoal(@PathVariable Long courseId, @RequestBody LearningGoal learningGoal) throws URISyntaxException {
        log.debug("REST request to create LearningGoal : {}", learningGoal);
        if (learningGoal.getId() != null || learningGoal.getTitle() == null || learningGoal.getTitle().trim().isEmpty()) {
            throw new BadRequestException();
        }
        var course = courseRepository.findWithEagerLearningGoalsByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        LearningGoal learningGoalToCreate = new LearningGoal();
        learningGoalToCreate.setTitle(learningGoal.getTitle().trim());
        learningGoalToCreate.setDescription(learningGoal.getDescription());
        learningGoalToCreate.setTaxonomy(learningGoal.getTaxonomy());
        learningGoalToCreate.setMasteryThreshold(learningGoal.getMasteryThreshold());
        learningGoalToCreate.setCourse(course);

        var persistedLearningGoal = learningGoalRepository.save(learningGoalToCreate);

        linkLectureUnitsToLearningGoal(persistedLearningGoal, learningGoal.getLectureUnits(), Set.of());

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/learning-goals/" + persistedLearningGoal.getId())).body(persistedLearningGoal);
    }

    /**
     * DELETE /courses/:courseId/learning-goals/:learningGoalId
     *
     * @param courseId       the id of the course to which the learning goal belongs
     * @param learningGoalId the id of the learning goal to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/learning-goals/{learningGoalId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteLearningGoal(@PathVariable Long learningGoalId, @PathVariable Long courseId) {
        log.info("REST request to delete a LearningGoal : {}", learningGoalId);

        var course = courseRepository.findByIdElseThrow(courseId);
        var learningGoal = learningGoalRepository.findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(learningGoalId);
        checkAuthorizationForLearningGoal(Role.INSTRUCTOR, course, learningGoal);

        var relations = learningGoalRelationRepository.findAllByLearningGoalId(learningGoal.getId());
        if (!relations.isEmpty()) {
            throw new BadRequestException("Can not delete a learning goal that has active relations");
        }

        learningGoalProgressRepository.deleteAllByLearningGoalId(learningGoal.getId());

        learningGoal.getExercises().forEach(exercise -> {
            exercise.getLearningGoals().remove(learningGoal);
            exerciseRepository.save(exercise);
        });

        learningGoal.getLectureUnits().forEach(lectureUnit -> {
            lectureUnit.getLearningGoals().remove(learningGoal);
            lectureUnitRepository.save(lectureUnit);
        });

        learningGoalRepository.deleteById(learningGoal.getId());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, learningGoal.getTitle())).build();
    }

    /**
     * GET /courses/:courseId/learning-goals/:learningGoalId/student-progress gets the learning goal progress for a user
     *
     * @param courseId       the id of the course to which the learning goal belongs
     * @param learningGoalId the id of the learning goal for which to get the progress
     * @param refresh        whether to update the student progress or fetch it from the database (default)
     * @return the ResponseEntity with status 200 (OK) and with the learning goal course performance in the body
     */
    @GetMapping("/courses/{courseId}/learning-goals/{learningGoalId}/student-progress")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<LearningGoalProgress> getLearningGoalStudentProgress(@PathVariable Long courseId, @PathVariable Long learningGoalId,
            @RequestParam(defaultValue = "false") Boolean refresh) {
        log.debug("REST request to get student progress for learning goal: {}", learningGoalId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var learningGoal = learningGoalRepository.findByIdElseThrow(learningGoalId);
        checkAuthorizationForLearningGoal(Role.STUDENT, course, learningGoal);

        LearningGoalProgress studentProgress;
        if (refresh) {
            studentProgress = learningGoalProgressService.updateLearningGoalProgress(learningGoalId, user);
        }
        else {
            studentProgress = learningGoalProgressRepository.findEagerByLearningGoalIdAndUserId(learningGoalId, user.getId()).orElse(null);
        }

        return ResponseEntity.ok().body(studentProgress);
    }

    /**
     * GET /courses/:courseId/learning-goals/:learningGoalId/course-progress gets the learning goal progress for the whole course
     *
     * @param courseId       the id of the course to which the learning goal belongs
     * @param learningGoalId the id of the learning goal for which to get the progress
     * @return the ResponseEntity with status 200 (OK) and with the learning goal course performance in the body
     */
    @GetMapping("/courses/{courseId}/learning-goals/{learningGoalId}/course-progress")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<CourseLearningGoalProgressDTO> getLearningGoalCourseProgress(@PathVariable Long courseId, @PathVariable Long learningGoalId) {
        log.debug("REST request to get course progress for learning goal: {}", learningGoalId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var learningGoal = learningGoalRepository.findByIdWithLectureUnitsAndCompletionsElseThrow(learningGoalId);
        checkAuthorizationForLearningGoal(Role.INSTRUCTOR, course, learningGoal);

        var numberOfStudents = learningGoalProgressRepository.countByLearningGoal(learningGoal.getId());
        var numberOfMasteredStudents = learningGoalProgressRepository.countByLearningGoalAndProgressAndConfidenceGreaterThanEqual(learningGoal.getId(), 100.0,
                (double) learningGoal.getMasteryThreshold());
        var averageStudentScore = RoundingUtil.roundScoreSpecifiedByCourseSettings(learningGoalProgressRepository.findAverageConfidenceByLearningGoalId(learningGoalId).orElse(0.0),
                course);

        return ResponseEntity.ok().body(new CourseLearningGoalProgressDTO(learningGoal.getId(), numberOfStudents, numberOfMasteredStudents, averageStudentScore));
    }

    /**
     * GET /courses/:courseId/learning-goals/:learningGoalId/relations get the relations for the learning goal
     *
     * @param courseId       the id of the course to which the learning goal belongs
     * @param learningGoalId the id of the learning goal for which to fetch all relations
     * @return the ResponseEntity with status 200 (OK) and with a list of relations for the learning goal in the body
     */
    @GetMapping("/courses/{courseId}/learning-goals/{learningGoalId}/relations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<LearningGoalRelation>> getLearningGoalRelations(@PathVariable Long learningGoalId, @PathVariable Long courseId) {
        log.debug("REST request to get relations for LearningGoal : {}", learningGoalId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var learningGoal = learningGoalRepository.findByIdElseThrow(learningGoalId);
        checkAuthorizationForLearningGoal(Role.STUDENT, course, learningGoal);

        var relations = learningGoalRelationRepository.findAllByLearningGoalId(learningGoal.getId());
        return ResponseEntity.ok().body(relations);
    }

    /**
     * POST /courses/:courseId/learning-goals/:learningGoalId/relations
     *
     * @param courseId           the id of the course to which the learning goals belong
     * @param tailLearningGoalId the id of the learning goal at the tail of the relation
     * @param headLearningGoalId the id of the learning goal at the head of the relation
     * @param type               the type of the relation as request parameter
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("/courses/{courseId}/learning-goals/{tailLearningGoalId}/relations/{headLearningGoalId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoalRelation> createLearningGoalRelation(@PathVariable Long courseId, @PathVariable Long tailLearningGoalId,
            @PathVariable Long headLearningGoalId, @RequestParam(defaultValue = "") String type) {
        log.info("REST request to create a relation between learning goals {} and {}", tailLearningGoalId, headLearningGoalId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var tailLearningGoal = learningGoalRepository.findByIdElseThrow(tailLearningGoalId);
        checkAuthorizationForLearningGoal(Role.INSTRUCTOR, course, tailLearningGoal);
        var headLearningGoal = learningGoalRepository.findByIdElseThrow(headLearningGoalId);
        checkAuthorizationForLearningGoal(Role.INSTRUCTOR, course, headLearningGoal);

        try {
            var relationType = LearningGoalRelation.RelationType.valueOf(type);

            var relation = new LearningGoalRelation();
            relation.setTailLearningGoal(tailLearningGoal);
            relation.setHeadLearningGoal(headLearningGoal);
            relation.setType(relationType);

            var learningGoals = learningGoalRepository.findAllForCourse(course.getId());
            var learningGoalRelations = learningGoalRelationRepository.findAllByCourseId(course.getId());
            learningGoalRelations.add(relation);
            if (learningGoalService.doesCreateCircularRelation(learningGoals, learningGoalRelations)) {
                throw new BadRequestException("You can't define circular dependencies between competencies");
            }

            learningGoalRelationRepository.save(relation);

            return ResponseEntity.ok().body(relation);
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid value for relation type");
        }
    }

    /**
     * DELETE /courses/:courseId/learning-goals/:learningGoalId/relations/:learningGoalRelationId
     *
     * @param courseId               the id of the course
     * @param learningGoalId         the id of the learning goal to which the relation belongs
     * @param learningGoalRelationId the id of the learning goal relation
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/learning-goals/{learningGoalId}/relations/{learningGoalRelationId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removeLearningGoalRelation(@PathVariable Long learningGoalId, @PathVariable Long courseId, @PathVariable Long learningGoalRelationId) {
        log.info("REST request to remove a learning goal relation: {}", learningGoalId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var learningGoal = learningGoalRepository.findByIdElseThrow(learningGoalId);
        checkAuthorizationForLearningGoal(Role.INSTRUCTOR, course, learningGoal);

        var relation = learningGoalRelationRepository.findById(learningGoalRelationId).orElseThrow();
        if (!relation.getTailLearningGoal().getId().equals(learningGoal.getId())) {
            throw new BadRequestException("The relation does not belong to the specified learning goal");
        }

        learningGoalRelationRepository.delete(relation);

        return ResponseEntity.ok().build();
    }

    /**
     * GET /courses/:courseId/prerequisites
     *
     * @param courseId the id of the course for which the learning goals should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the found learning goals
     */
    @GetMapping("/courses/{courseId}/prerequisites")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<LearningGoal>> getPrerequisites(@PathVariable Long courseId) {
        log.debug("REST request to get prerequisites for course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        // Authorization check is skipped when course is open to self-registration
        if (!course.isRegistrationEnabled()) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        }

        Set<LearningGoal> prerequisites = learningGoalService.findAllPrerequisitesForCourse(course, user);

        return ResponseEntity.ok(new ArrayList<>(prerequisites));
    }

    /**
     * POST /courses/:courseId/prerequisites/:learningGoalId
     *
     * @param courseId       the id of the course for which the learning goal should be a prerequisite
     * @param learningGoalId the id of the prerequisite (learning goal) to add
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("/courses/{courseId}/prerequisites/{learningGoalId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoal> addPrerequisite(@PathVariable Long learningGoalId, @PathVariable Long courseId) {
        log.info("REST request to add a prerequisite: {}", learningGoalId);
        var course = courseRepository.findWithEagerLearningGoalsByIdElseThrow(courseId);
        var learningGoal = learningGoalRepository.findByIdWithConsecutiveCoursesElseThrow(learningGoalId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, learningGoal.getCourse(), null);

        if (learningGoal.getCourse().getId().equals(courseId)) {
            throw new ConflictException("The learning goal of a course can not be a prerequisite to the same course", "LearningGoal", "learningGoalCycle");
        }

        course.addPrerequisite(learningGoal);
        courseRepository.save(course);
        return ResponseEntity.ok().body(learningGoal);
    }

    /**
     * DELETE /courses/:courseId/prerequisites/:learningGoalId
     *
     * @param courseId       the id of the course for which the learning goal is a prerequisite
     * @param learningGoalId the id of the prerequisite (learning goal) to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/prerequisites/{learningGoalId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removePrerequisite(@PathVariable Long learningGoalId, @PathVariable Long courseId) {
        log.info("REST request to remove a prerequisite: {}", learningGoalId);
        var course = courseRepository.findWithEagerLearningGoalsByIdElseThrow(courseId);
        var learningGoal = learningGoalRepository.findByIdWithConsecutiveCoursesElseThrow(learningGoalId);
        if (!learningGoal.getConsecutiveCourses().stream().map(Course::getId).toList().contains(courseId)) {
            throw new ConflictException("The learning goal is not a prerequisite of the given course", "LearningGoal", "prerequisiteWrongCourse");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, learningGoal.getCourse(), null);

        course.removePrerequisite(learningGoal);
        courseRepository.save(course);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, learningGoal.getTitle())).build();
    }

    /**
     * Link the learning goal to a set of lecture units (and exercises if it includes exercise units)
     *
     * @param learningGoal         The learning goal to be linked
     * @param lectureUnitsToAdd    A set of lecture units to link to the specified learning goal
     * @param lectureUnitsToRemove A set of lecture units to unlink from the specified learning goal
     */
    private void linkLectureUnitsToLearningGoal(LearningGoal learningGoal, Set<LectureUnit> lectureUnitsToAdd, Set<LectureUnit> lectureUnitsToRemove) {
        // Remove the learning goal from the old lecture units
        var lectureUnitsToRemoveFromDb = lectureUnitRepository.findAllByIdWithLearningGoalsBidirectional(lectureUnitsToRemove.stream().map(LectureUnit::getId).toList());
        lectureUnitRepository.saveAll(lectureUnitsToRemoveFromDb.stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).map(lectureUnit -> {
            lectureUnit.getLearningGoals().remove(learningGoal);
            return lectureUnit;
        }).collect(Collectors.toSet()));
        exerciseRepository.saveAll(lectureUnitsToRemoveFromDb.stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit)
                .map(lectureUnit -> ((ExerciseUnit) lectureUnit).getExercise()).map(exercise -> {
                    exercise.getLearningGoals().remove(learningGoal);
                    return exercise;
                }).collect(Collectors.toSet()));

        // Add the learning goal to the new lecture units
        var lectureUnitsFromDb = lectureUnitRepository.findAllByIdWithLearningGoalsBidirectional(lectureUnitsToAdd.stream().map(LectureUnit::getId).toList());
        var lectureUnitsWithoutExercises = lectureUnitsFromDb.stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).collect(Collectors.toSet());
        var exercises = lectureUnitsFromDb.stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).map(lectureUnit -> ((ExerciseUnit) lectureUnit).getExercise())
                .collect(Collectors.toSet());
        lectureUnitsWithoutExercises.stream().map(LectureUnit::getLearningGoals).forEach(learningGoals -> learningGoals.add(learningGoal));
        exercises.stream().map(Exercise::getLearningGoals).forEach(learningGoals -> learningGoals.add(learningGoal));
        lectureUnitRepository.saveAll(lectureUnitsWithoutExercises);
        exerciseRepository.saveAll(exercises);
        learningGoal.setLectureUnits(lectureUnitsToAdd);
    }

    /**
     * Checks if the user has the necessary permissions and the learning goal matches the course.
     *
     * @param role         The minimal role the user must have in the course
     * @param course       The course for which to check the authorization role for
     * @param learningGoal The learning goal to be accessed by the user
     */
    private void checkAuthorizationForLearningGoal(Role role, @NotNull Course course, @NotNull LearningGoal learningGoal) {
        if (learningGoal.getCourse() == null) {
            throw new ConflictException("A learning goal must belong to a course", "LearningGoal", "learningGoalNoCourse");
        }
        if (!learningGoal.getCourse().getId().equals(course.getId())) {
            throw new ConflictException("The learning goal does not belong to the correct course", "LearningGoal", "learningGoalWrongCourse");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(role, course, null);
    }
}
