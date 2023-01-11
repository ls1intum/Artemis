package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

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
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.LearningGoalProgressService;
import de.tum.in.www1.artemis.service.LearningGoalService;
import de.tum.in.www1.artemis.service.util.RoundingUtil;
import de.tum.in.www1.artemis.web.rest.dto.CourseLearningGoalProgressDTO;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
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

    private final ExerciseService exerciseService;

    public LearningGoalResource(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            LearningGoalRepository learningGoalRepository, LearningGoalRelationRepository learningGoalRelationRepository, LectureUnitRepository lectureUnitRepository,
            LearningGoalService learningGoalService, LearningGoalProgressRepository learningGoalProgressRepository, ExerciseRepository exerciseRepository,
            LearningGoalProgressService learningGoalProgressService, ExerciseService exerciseService) {
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
        this.exerciseService = exerciseService;
    }

    /**
     * GET /courses/:courseId/goals/:learningGoalId/individual-progress  gets the learning goal progress for the whole course
     *
     * @param courseId                 the id of the course to which the learning goal belongs
     * @param learningGoalId           the id of the learning goal for which to get the progress
     * @return the ResponseEntity with status 200 (OK) and with the learning goal course performance in the body
     */
    @GetMapping("/courses/{courseId}/goals/{learningGoalId}/student-progress")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<LearningGoalProgress> getLearningGoalStudentProgress(@PathVariable Long courseId, @PathVariable Long learningGoalId,
            @RequestParam(defaultValue = "false") Boolean refresh) {
        log.debug("REST request to get student progress for learning goal: {}", learningGoalId);
        // var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        if (refresh) {
            return ResponseEntity.ok().body(learningGoalProgressService.updateLearningGoalProgress(learningGoalId, user));
        }
        else {
            return ResponseEntity.ok().body(learningGoalProgressRepository.findEagerByLearningGoalIdAndUserId(learningGoalId, user.getId()).orElse(null));
        }
    }

    /**
     * GET /courses/:courseId/goals/:learningGoalId/course-progress  gets the learning goal progress for the whole course
     *
     * @param courseId                 the id of the course to which the learning goal belongs
     * @param learningGoalId           the id of the learning goal for which to get the progress
     * @return the ResponseEntity with status 200 (OK) and with the learning goal course performance in the body
     */
    @GetMapping("/courses/{courseId}/goals/{learningGoalId}/course-progress")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<CourseLearningGoalProgressDTO> getLearningGoalCourseProgress(@PathVariable Long courseId, @PathVariable Long learningGoalId) {
        log.debug("REST request to get course progress for learning goal: {}", learningGoalId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var learningGoal = findLearningGoal(Role.INSTRUCTOR, learningGoalId, course.getId(), true, true);

        var numberOfStudents = learningGoalProgressRepository.countByLearningGoal(learningGoal.getId());
        var numberOfMasteredStudents = learningGoalProgressRepository.countByLearningGoalAndProgressAndConfidenceGreaterThanEqual(learningGoal.getId(), 100.0,
                learningGoal.getMasteryThreshold().doubleValue());
        var averageStudentScore = RoundingUtil.roundScoreSpecifiedByCourseSettings(learningGoalProgressRepository.findAverageConfidenceByLearningGoalId(learningGoalId).orElse(0.0),
                course);

        return ResponseEntity.ok().body(new CourseLearningGoalProgressDTO(learningGoal.getId(), numberOfStudents, numberOfMasteredStudents, averageStudentScore));
    }

    /**
     * GET /courses/:courseId/goals/:learningGoalId/relations  get the relations for the learning goal
     *
     * @param courseId                 the id of the course to which the learning goal belongs
     * @param learningGoalId           the id of the learning goal for which to fetch all relations
     * @return the ResponseEntity with status 200 (OK) and with a list of relations for the learning goal in the body
     */
    @GetMapping("/courses/{courseId}/goals/{learningGoalId}/relations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<LearningGoalRelation>> getLearningGoalRelations(@PathVariable Long learningGoalId, @PathVariable Long courseId) {
        log.debug("REST request to get relations for LearningGoal : {}", learningGoalId);
        var learningGoal = findLearningGoal(Role.STUDENT, learningGoalId, courseId, false, false);
        var relations = learningGoalRelationRepository.findAllByLearningGoalId(learningGoal.getId());
        return ResponseEntity.ok().body(relations);
    }

    /**
     * DELETE /courses/:courseId/goals/:learningGoalId
     * @param courseId the id of the course to which the learning goal belongs
     * @param learningGoalId the id of the learning goal to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/goals/{learningGoalId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteLearningGoal(@PathVariable Long learningGoalId, @PathVariable Long courseId) {
        log.info("REST request to delete a LearningGoal : {}", learningGoalId);

        var learningGoal = findLearningGoal(Role.INSTRUCTOR, learningGoalId, courseId, false, false);

        var relations = learningGoalRelationRepository.findAllByLearningGoalId(learningGoal.getId());

        if (!relations.isEmpty()) {
            throw new BadRequestException("Can not delete a learning goal that has active relations");
        }

        learningGoalProgressRepository.deleteAllByLearningGoalId(learningGoal.getId());

        learningGoalRepository.deleteById(learningGoal.getId());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, learningGoal.getTitle())).build();
    }

    /**
     * GET /courses/:courseId/goals : gets all the learning goals of a course
     * @param courseId the id of the course for which the learning goals should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the found learning goals
     */
    @GetMapping("/courses/{courseId}/goals")
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
     * GET /courses/:courseId/goals/:learningGoalId : gets the learning goal with the specified id
     *
     * @param learningGoalId the id of the learning goal to retrieve
     * @param courseId the id of the course to which the learning goal belongs
     * @return the ResponseEntity with status 200 (OK) and with body the learning goal, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}/goals/{learningGoalId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoal> getLearningGoal(@PathVariable Long learningGoalId, @PathVariable Long courseId) {
        log.debug("REST request to get LearningGoal : {}", learningGoalId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        var learningGoal = learningGoalRepository.findByIdWithExercisesAndLectureUnitsAndProgressForUserElseThrow(learningGoalId, courseId);

        if (!learningGoal.getCourse().getId().equals(courseId)) {
            throw new BadRequestException("The learning goal does not belong to the specified course");
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        // Set completion stations and remove exercise units (redundant as we also return all exercises)
        learningGoal.setLectureUnits(learningGoal.getLectureUnits().stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).map(lectureUnit -> {
            lectureUnit.setCompleted(lectureUnit.isCompletedFor(user));
            return lectureUnit;
        }).collect(Collectors.toSet()));
        return ResponseEntity.ok().body(learningGoal);
    }

    private LearningGoal findLearningGoal(Role role, Long learningGoalId, Long courseId, boolean withCompletions, boolean withLectureUnits) {
        LearningGoal learningGoal;
        if (withCompletions && withLectureUnits) {
            learningGoal = learningGoalRepository.findByIdWithLectureUnitsAndCompletionsElseThrow(learningGoalId);
        }
        else if (withLectureUnits) {
            learningGoal = learningGoalRepository.findByIdWithLectureUnitsElseThrow(learningGoalId);
        }
        else {
            learningGoal = learningGoalRepository.findByIdElseThrow(learningGoalId);
        }

        if (learningGoal.getCourse() == null) {
            throw new ConflictException("A learning goal must belong to a course", "LearningGoal", "learningGoalNoCourse");
        }
        if (!learningGoal.getCourse().getId().equals(courseId)) {
            throw new ConflictException("The learning goal does not belong to the correct course", "LearningGoal", "learningGoalWrongCourse");
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(role, learningGoal.getCourse(), null);
        return learningGoal;
    }

    private LearningGoal findPrerequisite(Role role, Long learningGoalId, Long courseId) {
        var learningGoal = learningGoalRepository.findByIdWithConsecutiveCoursesElseThrow(learningGoalId);
        if (!learningGoal.getConsecutiveCourses().stream().map(Course::getId).toList().contains(courseId)) {
            throw new ConflictException("The learning goal is not a prerequisite of the given course", "LearningGoal", "prerequisiteWrongCourse");
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(role, learningGoal.getCourse(), null);
        return learningGoal;
    }

    /**
     * PUT /courses/:courseId/goals : Updates an existing learning goal.
     *
     * @param courseId  the id of the course to which the learning goals belongs
     * @param learningGoal the learningGoal to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated learningGoal
     */
    @PutMapping("/courses/{courseId}/goals")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoal> updateLearningGoal(@PathVariable Long courseId, @RequestBody LearningGoal learningGoal) {
        log.debug("REST request to update LearningGoal : {}", learningGoal);
        if (learningGoal.getId() == null) {
            throw new BadRequestException();
        }
        var existingLearningGoal = this.learningGoalRepository.findByIdWithLectureUnitsElseThrow(learningGoal.getId());
        if (existingLearningGoal.getCourse() == null || !existingLearningGoal.getCourse().getId().equals(courseId)) {
            throw new BadRequestException();
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, existingLearningGoal.getCourse(), null);

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
     * POST /courses/:courseId/goals : creates a new learning goal.
     *
     * @param courseId      the id of the course to which the learning goal should be added
     * @param learningGoalFromClient the learning goal that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new learning goal
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses/{courseId}/goals")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoal> createLearningGoal(@PathVariable Long courseId, @RequestBody LearningGoal learningGoalFromClient) throws URISyntaxException {
        log.debug("REST request to create LearningGoal : {}", learningGoalFromClient);

        if (learningGoalFromClient.getId() != null || learningGoalFromClient.getTitle() == null) {
            throw new BadRequestException();
        }
        Course course = courseRepository.findWithEagerLearningGoalsByIdElseThrow(courseId);
        if (course.getLearningGoals().stream().map(LearningGoal::getTitle).anyMatch(title -> title.equals(learningGoalFromClient.getTitle()))) {
            throw new BadRequestException();
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        LearningGoal learningGoalToCreate = new LearningGoal();
        learningGoalToCreate.setTitle(learningGoalFromClient.getTitle());
        learningGoalToCreate.setDescription(learningGoalFromClient.getDescription());
        learningGoalToCreate.setTaxonomy(learningGoalFromClient.getTaxonomy());
        learningGoalToCreate.setMasteryThreshold(learningGoalFromClient.getMasteryThreshold());
        learningGoalToCreate.setCourse(course);

        var persistedLearningGoal = learningGoalRepository.save(learningGoalToCreate);

        linkLectureUnitsToLearningGoal(persistedLearningGoal, learningGoalFromClient.getLectureUnits(), Set.of());

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/goals/" + persistedLearningGoal.getId())).body(persistedLearningGoal);
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
     * POST /courses/:courseId/goals/:learningGoalId/relations
     * @param courseId  the id of the course to which the learning goals belong
     * @param tailLearningGoalId the id of the learning goal at the tail of the relation
     * @param headLearningGoalId the id of the learning goal at the head of the relation
     * @param type the type of the relation as request parameter
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("/courses/{courseId}/goals/{tailLearningGoalId}/relations/{headLearningGoalId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoalRelation> createLearningGoalRelation(@PathVariable Long courseId, @PathVariable Long tailLearningGoalId,
            @PathVariable Long headLearningGoalId, @RequestParam(defaultValue = "") String type) {
        log.info("REST request to create a relation between learning goals {} and {}", tailLearningGoalId, headLearningGoalId);
        var tailLearningGoal = findLearningGoal(Role.INSTRUCTOR, tailLearningGoalId, courseId, false, false);
        var headLearningGoal = findLearningGoal(Role.INSTRUCTOR, headLearningGoalId, courseId, false, false);

        try {
            var relationType = LearningGoalRelation.RelationType.valueOf(type);

            var relation = new LearningGoalRelation();
            relation.setTailLearningGoal(tailLearningGoal);
            relation.setHeadLearningGoal(headLearningGoal);
            relation.setType(relationType);
            learningGoalRelationRepository.save(relation);

            return ResponseEntity.ok().body(relation);
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid value for relation type");
        }
    }

    /**
     * DELETE /courses/:courseId/goals/:learningGoalId/relations/:learningGoalRelationId
     * @param courseId the id of the course
     * @param learningGoalId the id of the learning goal to which the relation belongs
     * @param learningGoalRelationId the id of the learning goal relation
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/goals/{learningGoalId}/relations/{learningGoalRelationId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removeLearningGoalRelation(@PathVariable Long learningGoalId, @PathVariable Long courseId, @PathVariable Long learningGoalRelationId) {
        log.info("REST request to remove a learning goal relation: {}", learningGoalId);
        var learningGoal = findLearningGoal(Role.INSTRUCTOR, learningGoalId, courseId, false, false);

        var relation = learningGoalRelationRepository.findById(learningGoalRelationId).orElseThrow();

        if (!relation.getTailLearningGoal().getId().equals(learningGoal.getId())) {
            throw new BadRequestException("The relation does not belong to the specified learning goal");
        }

        learningGoalRelationRepository.delete(relation);

        return ResponseEntity.ok().build();
    }

    /**
     * GET /courses/:courseId/prerequisites
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
     * @param courseId the id of the course for which the learning goal should be a prerequisite
     * @param learningGoalId the id of the prerequisite (learning goal) to add
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("/courses/{courseId}/prerequisites/{learningGoalId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoal> addPrerequisite(@PathVariable Long learningGoalId, @PathVariable Long courseId) {
        log.info("REST request to add a prerequisite: {}", learningGoalId);
        var course = courseRepository.findWithEagerLearningGoalsByIdElseThrow(courseId);
        var learningGoal = learningGoalRepository.findByIdWithConsecutiveCoursesElseThrow(learningGoalId);

        if (learningGoal.getCourse().getId().equals(courseId)) {
            throw new ConflictException("The learning goal of a course can not be a prerequisite to the same course", "LearningGoal", "learningGoalCycle");
        }

        course.addPrerequisite(learningGoal);
        courseRepository.save(course);
        return ResponseEntity.ok().body(learningGoal);
    }

    /**
     * DELETE /courses/:courseId/prerequisites/:learningGoalId
     * @param courseId the id of the course for which the learning goal is a prerequisite
     * @param learningGoalId the id of the prerequisite (learning goal) to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/prerequisites/{learningGoalId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removePrerequisite(@PathVariable Long learningGoalId, @PathVariable Long courseId) {
        log.info("REST request to remove a prerequisite: {}", learningGoalId);
        var course = courseRepository.findWithEagerLearningGoalsByIdElseThrow(courseId);
        var learningGoal = findPrerequisite(Role.INSTRUCTOR, learningGoalId, courseId);
        course.removePrerequisite(learningGoal);
        courseRepository.save(course);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, learningGoal.getTitle())).build();
    }

    private Set<LectureUnit> getLectureUnitsFromDatabase(Set<LectureUnit> lectureUnitsFromClient) {
        Set<LectureUnit> lectureUnitsFromDatabase = new HashSet<>();
        if (lectureUnitsFromClient != null && !lectureUnitsFromClient.isEmpty()) {
            for (LectureUnit lectureUnit : lectureUnitsFromClient) {
                if (lectureUnit.getId() == null) {
                    throw new BadRequestAlertException("The lecture unit does not have an ID", "LectureUnit", "noId");
                }
                var lectureUnitFromDb = lectureUnitRepository.findByIdWithLearningGoalsElseThrow(lectureUnit.getId());
                lectureUnitsFromDatabase.add(lectureUnitFromDb);
            }
        }
        return lectureUnitsFromDatabase;
    }

    /**
     * Link the learning goal to a set of lecture units (and exercises if it includes exercise units)
     * @param learningGoal The learning goal to be linked
     * @param lectureUnitsToAdd A set of lecture units to link to the specified learning goal
     * @param lectureUnitsToRemove A set of lecture units to unlink from the specified learning goal
     */
    private void linkLectureUnitsToLearningGoal(LearningGoal learningGoal, Set<LectureUnit> lectureUnitsToAdd, Set<LectureUnit> lectureUnitsToRemove) {
        // Remove the learning goal from the old lecture units
        var lectureUnitsToRemoveFromDb = getLectureUnitsFromDatabase(lectureUnitsToRemove);
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
        var lectureUnitsFromDb = getLectureUnitsFromDatabase(lectureUnitsToAdd);
        var lectureUnitsWithoutExercises = lectureUnitsFromDb.stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).collect(Collectors.toSet());
        var exercises = lectureUnitsFromDb.stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).map(lectureUnit -> ((ExerciseUnit) lectureUnit).getExercise())
                .collect(Collectors.toSet());
        lectureUnitsWithoutExercises.stream().map(LectureUnit::getLearningGoals).forEach(learningGoals -> learningGoals.add(learningGoal));
        exercises.stream().map(Exercise::getLearningGoals).forEach(learningGoals -> learningGoals.add(learningGoal));
        lectureUnitRepository.saveAll(lectureUnitsWithoutExercises);
        exerciseRepository.saveAll(exercises);
    }

}
