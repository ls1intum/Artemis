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

    private static final String ENTITY_NAME = "competency";

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final LearningGoalRepository competencyRepository;

    private final LearningGoalRelationRepository competencyRelationRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final LearningGoalService competencyService;

    private final LearningGoalProgressRepository competencyProgressRepository;

    private final ExerciseRepository exerciseRepository;

    private final LearningGoalProgressService competencyProgressService;

    public LearningGoalResource(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            LearningGoalRepository competencyRepository, LearningGoalRelationRepository competencyRelationRepository, LectureUnitRepository lectureUnitRepository,
            LearningGoalService competencyService, LearningGoalProgressRepository competencyProgressRepository, ExerciseRepository exerciseRepository,
            LearningGoalProgressService competencyProgressService) {
        this.courseRepository = courseRepository;
        this.competencyRelationRepository = competencyRelationRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.competencyRepository = competencyRepository;
        this.competencyService = competencyService;
        this.competencyProgressRepository = competencyProgressRepository;
        this.exerciseRepository = exerciseRepository;
        this.competencyProgressService = competencyProgressService;
    }

    /**
     * GET /competencies/:competencyId/title : Returns the title of the competency with the given id
     *
     * @param competencyId the id of the competency
     * @return the title of the competency wrapped in an ResponseEntity or 404 Not Found if no competency with that id exists
     */
    @GetMapping("/competencies/{competencyId}/title")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<String> getCompetencyTitle(@PathVariable Long competencyId) {
        final var title = competencyRepository.getLearningGoalTitle(competencyId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * Search for all competencies by title and course title. The result is pageable.
     *
     * @param search The pageable search containing the page size, page number and query string
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("competencies")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<SearchResultPageDTO<LearningGoal>> getAllCompetenciesOnPage(PageableSearchDTO<String> search) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(competencyService.getAllOnPageWithSize(search, user));
    }

    /**
     * GET /courses/:courseId/competencies : gets all the competencies of a course
     *
     * @param courseId the id of the course for which the competencies should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the found competencies
     */
    @GetMapping("/courses/{courseId}/competencies")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<LearningGoal>> getCompetencies(@PathVariable Long courseId) {
        log.debug("REST request to get competencies for course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        Set<LearningGoal> competencies = competencyRepository.findAllForCourse(course.getId());
        return ResponseEntity.ok(new ArrayList<>(competencies));
    }

    /**
     * GET /courses/:courseId/competencies/:competencyId : gets the competency with the specified id
     *
     * @param competencyId the id of the competency to retrieve
     * @param courseId     the id of the course to which the competency belongs
     * @return the ResponseEntity with status 200 (OK) and with body the competency, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}/competencies/{competencyId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<LearningGoal> getCompetency(@PathVariable Long competencyId, @PathVariable Long courseId) {
        log.debug("REST request to get Competency : {}", competencyId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdWithExercisesAndLectureUnitsElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.STUDENT, course, competency);

        competency.setUserProgress(competencyProgressRepository.findByLearningGoalIdAndUserId(competencyId, user.getId()).map(Set::of).orElse(Set.of()));
        // Set completion status and remove exercise units (redundant as we also return all exercises)
        competency.setLectureUnits(competency.getLectureUnits().stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit))
                .filter(lectureUnit -> authorizationCheckService.isAllowedToSeeLectureUnit(lectureUnit, user)).map(lectureUnit -> {
                    lectureUnit.setCompleted(lectureUnit.isCompletedFor(user));
                    return lectureUnit;
                }).collect(Collectors.toSet()));
        competency
                .setExercises(competency.getExercises().stream().filter(exercise -> authorizationCheckService.isAllowedToSeeExercise(exercise, user)).collect(Collectors.toSet()));
        return ResponseEntity.ok().body(competency);
    }

    /**
     * PUT /courses/:courseId/competencies : Updates an existing competency.
     *
     * @param courseId   the id of the course to which the competencies belongs
     * @param competency the competency to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated competency
     */
    @PutMapping("/courses/{courseId}/competencies")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoal> updateCompetency(@PathVariable Long courseId, @RequestBody LearningGoal competency) {
        log.debug("REST request to update Competency : {}", competency);
        if (competency.getId() == null) {
            throw new BadRequestException();
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        var existingCompetency = this.competencyRepository.findByIdWithLectureUnitsElseThrow(competency.getId());
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, existingCompetency);

        existingCompetency.setTitle(competency.getTitle());
        existingCompetency.setDescription(competency.getDescription());
        existingCompetency.setTaxonomy(competency.getTaxonomy());
        existingCompetency.setMasteryThreshold(competency.getMasteryThreshold());
        var persistedCompetency = competencyRepository.save(existingCompetency);

        linkLectureUnitsToCompetency(persistedCompetency, competency.getLectureUnits(), existingCompetency.getLectureUnits());

        if (competency.getLectureUnits().size() != existingCompetency.getLectureUnits().size() || !existingCompetency.getLectureUnits().containsAll(competency.getLectureUnits())) {
            log.debug("Linked lecture units changed, updating student progress for competency...");
            competencyProgressService.updateProgressByLearningGoalAsync(persistedCompetency);
        }

        return ResponseEntity.ok(persistedCompetency);
    }

    /**
     * POST /courses/:courseId/competencies : creates a new competency.
     *
     * @param courseId   the id of the course to which the competency should be added
     * @param competency the competency that should be created
     * @return the ResponseEntity with status 201 (Created) and with body the new competency
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses/{courseId}/competencies")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoal> createCompetency(@PathVariable Long courseId, @RequestBody LearningGoal competency) throws URISyntaxException {
        log.debug("REST request to create Competency : {}", competency);
        if (competency.getId() != null || competency.getTitle() == null || competency.getTitle().trim().isEmpty()) {
            throw new BadRequestException();
        }
        var course = courseRepository.findWithEagerLearningGoalsByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        LearningGoal competencyToCreate = new LearningGoal();
        competencyToCreate.setTitle(competency.getTitle().trim());
        competencyToCreate.setDescription(competency.getDescription());
        competencyToCreate.setTaxonomy(competency.getTaxonomy());
        competencyToCreate.setMasteryThreshold(competency.getMasteryThreshold());
        competencyToCreate.setCourse(course);

        var persistedCompetency = competencyRepository.save(competencyToCreate);

        linkLectureUnitsToCompetency(persistedCompetency, competency.getLectureUnits(), Set.of());

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/" + persistedCompetency.getId())).body(persistedCompetency);
    }

    /**
     * POST /courses/:courseId/competencies/import : imports a new competency.
     *
     * @param courseId           the id of the course to which the competency should be imported to
     * @param competencyToImport the competency that should be imported
     * @return the ResponseEntity with status 201 (Created) and with body containing the imported competency
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/courses/{courseId}/competencies/import")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoal> importCompetency(@PathVariable long courseId, @RequestBody LearningGoal competencyToImport) throws URISyntaxException {
        log.info("REST request to import a Competency: {}", competencyToImport.getId());

        var course = courseRepository.findWithEagerLearningGoalsByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, competencyToImport.getCourse(), null);

        if (competencyToImport.getCourse().getId().equals(courseId)) {
            throw new ConflictException("The competency is already added to this course", "Competency", "competencyCycle");
        }

        competencyToImport.setCourse(course);
        competencyToImport.setId(null);
        competencyToImport = competencyRepository.save(competencyToImport);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/competencies/" + competencyToImport.getId())).body(competencyToImport);
    }

    /**
     * DELETE /courses/:courseId/competencies/:competencyId
     *
     * @param courseId     the id of the course to which the competency belongs
     * @param competencyId the id of the competency to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/competencies/{competencyId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteCompetency(@PathVariable Long competencyId, @PathVariable Long courseId) {
        log.info("REST request to delete a Competency : {}", competencyId);

        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, competency);

        var relations = competencyRelationRepository.findAllByLearningGoalId(competency.getId());
        if (!relations.isEmpty()) {
            throw new BadRequestException("Can not delete a competency that has active relations");
        }

        competencyProgressRepository.deleteAllByLearningGoalId(competency.getId());

        competency.getExercises().forEach(exercise -> {
            exercise.getLearningGoals().remove(competency);
            exerciseRepository.save(exercise);
        });

        competency.getLectureUnits().forEach(lectureUnit -> {
            lectureUnit.getLearningGoals().remove(competency);
            lectureUnitRepository.save(lectureUnit);
        });

        competencyRepository.deleteById(competency.getId());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, competency.getTitle())).build();
    }

    /**
     * GET /courses/:courseId/competencies/:competencyId/student-progress gets the competency progress for a user
     *
     * @param courseId     the id of the course to which the competency belongs
     * @param competencyId the id of the competency for which to get the progress
     * @param refresh      whether to update the student progress or fetch it from the database (default)
     * @return the ResponseEntity with status 200 (OK) and with the competency course performance in the body
     */
    @GetMapping("/courses/{courseId}/competencies/{competencyId}/student-progress")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<LearningGoalProgress> getCompetencyStudentProgress(@PathVariable Long courseId, @PathVariable Long competencyId,
            @RequestParam(defaultValue = "false") Boolean refresh) {
        log.debug("REST request to get student progress for competency: {}", competencyId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.STUDENT, course, competency);

        LearningGoalProgress studentProgress;
        if (refresh) {
            studentProgress = competencyProgressService.updateLearningGoalProgress(competencyId, user);
        }
        else {
            studentProgress = competencyProgressRepository.findEagerByLearningGoalIdAndUserId(competencyId, user.getId()).orElse(null);
        }

        return ResponseEntity.ok().body(studentProgress);
    }

    /**
     * GET /courses/:courseId/competencies/:competencyId/course-progress gets the competency progress for the whole course
     *
     * @param courseId     the id of the course to which the competency belongs
     * @param competencyId the id of the competency for which to get the progress
     * @return the ResponseEntity with status 200 (OK) and with the competency course performance in the body
     */
    @GetMapping("/courses/{courseId}/competencies/{competencyId}/course-progress")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<CourseLearningGoalProgressDTO> getCompetencyCourseProgress(@PathVariable Long courseId, @PathVariable Long competencyId) {
        log.debug("REST request to get course progress for competency: {}", competencyId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdWithLectureUnitsAndCompletionsElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, competency);

        var numberOfStudents = competencyProgressRepository.countByLearningGoal(competency.getId());
        var numberOfMasteredStudents = competencyProgressRepository.countByLearningGoalAndProgressAndConfidenceGreaterThanEqual(competency.getId(), 100.0,
                (double) competency.getMasteryThreshold());
        var averageStudentScore = RoundingUtil.roundScoreSpecifiedByCourseSettings(competencyProgressRepository.findAverageConfidenceByLearningGoalId(competencyId).orElse(0.0),
                course);

        return ResponseEntity.ok().body(new CourseLearningGoalProgressDTO(competency.getId(), numberOfStudents, numberOfMasteredStudents, averageStudentScore));
    }

    /**
     * GET /courses/:courseId/competencies/:competencyId/relations get the relations for the competency
     *
     * @param courseId     the id of the course to which the competency belongs
     * @param competencyId the id of the competency for which to fetch all relations
     * @return the ResponseEntity with status 200 (OK) and with a list of relations for the competency in the body
     */
    @GetMapping("/courses/{courseId}/competencies/{competencyId}/relations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<LearningGoalRelation>> getCompetencyRelations(@PathVariable Long competencyId, @PathVariable Long courseId) {
        log.debug("REST request to get relations for Competency : {}", competencyId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.STUDENT, course, competency);

        var relations = competencyRelationRepository.findAllByLearningGoalId(competency.getId());
        return ResponseEntity.ok().body(relations);
    }

    /**
     * POST /courses/:courseId/competencies/:tailCompetencyId/relations/headCompetencyId
     *
     * @param courseId         the id of the course to which the competencies belong
     * @param tailCompetencyId the id of the competency at the tail of the relation
     * @param headCompetencyId the id of the competency at the head of the relation
     * @param type             the type of the relation as request parameter
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("/courses/{courseId}/competencies/{tailCompetencyId}/relations/{headCompetencyId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoalRelation> createCompetencyRelation(@PathVariable Long courseId, @PathVariable Long tailCompetencyId, @PathVariable Long headCompetencyId,
            @RequestParam(defaultValue = "") String type) {
        log.info("REST request to create a relation between competencies {} and {}", tailCompetencyId, headCompetencyId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var tailCompetency = competencyRepository.findByIdElseThrow(tailCompetencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, tailCompetency);
        var headCompetency = competencyRepository.findByIdElseThrow(headCompetencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, headCompetency);

        try {
            var relationType = LearningGoalRelation.RelationType.valueOf(type);

            var relation = new LearningGoalRelation();
            relation.setTailLearningGoal(tailCompetency);
            relation.setHeadLearningGoal(headCompetency);
            relation.setType(relationType);
            competencyRelationRepository.save(relation);

            return ResponseEntity.ok().body(relation);
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid value for relation type");
        }
    }

    /**
     * DELETE /courses/:courseId/competencies/:competencyId/relations/:competencyRelationId
     *
     * @param courseId             the id of the course
     * @param competencyId         the id of the competency to which the relation belongs
     * @param competencyRelationId the id of the competency relation
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/competencies/{competencyId}/relations/{competencyRelationId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removeCompetencyRelation(@PathVariable Long competencyId, @PathVariable Long courseId, @PathVariable Long competencyRelationId) {
        log.info("REST request to remove a competency relation: {}", competencyId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, competency);

        var relation = competencyRelationRepository.findById(competencyRelationId).orElseThrow();
        if (!relation.getTailLearningGoal().getId().equals(competency.getId())) {
            throw new BadRequestException("The relation does not belong to the specified competency");
        }

        competencyRelationRepository.delete(relation);

        return ResponseEntity.ok().build();
    }

    /**
     * GET /courses/:courseId/prerequisites
     *
     * @param courseId the id of the course for which the competencies should be fetched
     * @return the ResponseEntity with status 200 (OK) and with body the found competencies
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

        Set<LearningGoal> prerequisites = competencyService.findAllPrerequisitesForCourse(course, user);

        return ResponseEntity.ok(new ArrayList<>(prerequisites));
    }

    /**
     * POST /courses/:courseId/prerequisites/:competencyId
     *
     * @param courseId     the id of the course for which the competency should be a prerequisite
     * @param competencyId the id of the prerequisite (competency) to add
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping("/courses/{courseId}/prerequisites/{competencyId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoal> addPrerequisite(@PathVariable Long competencyId, @PathVariable Long courseId) {
        log.info("REST request to add a prerequisite: {}", competencyId);
        var course = courseRepository.findWithEagerLearningGoalsByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdWithConsecutiveCoursesElseThrow(competencyId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, competency.getCourse(), null);

        if (competency.getCourse().getId().equals(courseId)) {
            throw new ConflictException("The competency of a course can not be a prerequisite to the same course", "Competency", "competencyCycle");
        }

        course.addPrerequisite(competency);
        courseRepository.save(course);
        return ResponseEntity.ok().body(competency);
    }

    /**
     * DELETE /courses/:courseId/prerequisites/:competencyId
     *
     * @param courseId     the id of the course for which the competency is a prerequisite
     * @param competencyId the id of the prerequisite (competency) to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/prerequisites/{competencyId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removePrerequisite(@PathVariable Long competencyId, @PathVariable Long courseId) {
        log.info("REST request to remove a prerequisite: {}", competencyId);
        var course = courseRepository.findWithEagerLearningGoalsByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdWithConsecutiveCoursesElseThrow(competencyId);
        if (!competency.getConsecutiveCourses().stream().map(Course::getId).toList().contains(courseId)) {
            throw new ConflictException("The competency is not a prerequisite of the given course", "Competency", "prerequisiteWrongCourse");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, competency.getCourse(), null);

        course.removePrerequisite(competency);
        courseRepository.save(course);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, competency.getTitle())).build();
    }

    /**
     * Link the competency to a set of lecture units (and exercises if it includes exercise units)
     *
     * @param competency           The competency to be linked
     * @param lectureUnitsToAdd    A set of lecture units to link to the specified competency
     * @param lectureUnitsToRemove A set of lecture units to unlink from the specified competency
     */
    private void linkLectureUnitsToCompetency(LearningGoal competency, Set<LectureUnit> lectureUnitsToAdd, Set<LectureUnit> lectureUnitsToRemove) {
        // Remove the competency from the old lecture units
        var lectureUnitsToRemoveFromDb = lectureUnitRepository.findAllByIdWithLearningGoalsBidirectional(lectureUnitsToRemove.stream().map(LectureUnit::getId).toList());
        lectureUnitRepository.saveAll(lectureUnitsToRemoveFromDb.stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).map(lectureUnit -> {
            lectureUnit.getLearningGoals().remove(competency);
            return lectureUnit;
        }).collect(Collectors.toSet()));
        exerciseRepository.saveAll(lectureUnitsToRemoveFromDb.stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit)
                .map(lectureUnit -> ((ExerciseUnit) lectureUnit).getExercise()).map(exercise -> {
                    exercise.getLearningGoals().remove(competency);
                    return exercise;
                }).collect(Collectors.toSet()));

        // Add the competency to the new lecture units
        var lectureUnitsFromDb = lectureUnitRepository.findAllByIdWithLearningGoalsBidirectional(lectureUnitsToAdd.stream().map(LectureUnit::getId).toList());
        var lectureUnitsWithoutExercises = lectureUnitsFromDb.stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).collect(Collectors.toSet());
        var exercises = lectureUnitsFromDb.stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).map(lectureUnit -> ((ExerciseUnit) lectureUnit).getExercise())
                .collect(Collectors.toSet());
        lectureUnitsWithoutExercises.stream().map(LectureUnit::getLearningGoals).forEach(competencies -> competencies.add(competency));
        exercises.stream().map(Exercise::getLearningGoals).forEach(competencies -> competencies.add(competency));
        lectureUnitRepository.saveAll(lectureUnitsWithoutExercises);
        exerciseRepository.saveAll(exercises);
        competency.setLectureUnits(lectureUnitsToAdd);
    }

    /**
     * Checks if the user has the necessary permissions and the competency matches the course.
     *
     * @param role       The minimal role the user must have in the course
     * @param course     The course for which to check the authorization role for
     * @param competency The competency to be accessed by the user
     */
    private void checkAuthorizationForCompetency(Role role, @NotNull Course course, @NotNull LearningGoal competency) {
        if (competency.getCourse() == null) {
            throw new ConflictException("A competency must belong to a course", "Competency", "competencyNoCourse");
        }
        if (!competency.getCourse().getId().equals(course.getId())) {
            throw new ConflictException("The competency does not belong to the correct course", "Competency", "competencyWrongCourse");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(role, course, null);
    }

}
