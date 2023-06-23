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
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CompetencyProgressService;
import de.tum.in.www1.artemis.service.CompetencyService;
import de.tum.in.www1.artemis.service.util.RoundingUtil;
import de.tum.in.www1.artemis.web.rest.dto.CourseCompetencyProgressDTO;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class CompetencyResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final Logger log = LoggerFactory.getLogger(CompetencyResource.class);

    private static final String ENTITY_NAME = "competency";

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final CompetencyRepository competencyRepository;

    private final CompetencyRelationRepository competencyRelationRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final CompetencyService competencyService;

    private final CompetencyProgressRepository competencyProgressRepository;

    private final ExerciseRepository exerciseRepository;

    private final CompetencyProgressService competencyProgressService;

    public CompetencyResource(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            CompetencyRepository competencyRepository, CompetencyRelationRepository competencyRelationRepository, LectureUnitRepository lectureUnitRepository,
            CompetencyService competencyService, CompetencyProgressRepository competencyProgressRepository, ExerciseRepository exerciseRepository,
            CompetencyProgressService competencyProgressService) {
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
    @EnforceAtLeastStudent
    public ResponseEntity<String> getCompetencyTitle(@PathVariable Long competencyId) {
        final var title = competencyRepository.getCompetencyTitle(competencyId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * Search for all competencies by title and course title. The result is pageable.
     *
     * @param search The pageable search containing the page size, page number and query string
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("competencies")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<Competency>> getAllCompetenciesOnPage(PageableSearchDTO<String> search) {
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
    @EnforceAtLeastStudent
    public ResponseEntity<List<Competency>> getCompetencies(@PathVariable Long courseId) {
        log.debug("REST request to get competencies for course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        Set<Competency> competencies = competencyRepository.findAllForCourse(course.getId());
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
    @EnforceAtLeastStudent
    public ResponseEntity<Competency> getCompetency(@PathVariable Long competencyId, @PathVariable Long courseId) {
        log.debug("REST request to get Competency : {}", competencyId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdWithExercisesAndLectureUnitsElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.STUDENT, course, competency);

        competency.setUserProgress(competencyProgressRepository.findByCompetencyIdAndUserId(competencyId, user.getId()).map(Set::of).orElse(Set.of()));
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
     * @param courseId   the id of the course to which the competencies belong
     * @param competency the competency to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated competency
     */
    @PutMapping("/courses/{courseId}/competencies")
    @EnforceAtLeastInstructor
    public ResponseEntity<Competency> updateCompetency(@PathVariable Long courseId, @RequestBody Competency competency) {
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
            competencyProgressService.updateProgressByCompetencyAsync(persistedCompetency);
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
    @EnforceAtLeastInstructor
    public ResponseEntity<Competency> createCompetency(@PathVariable Long courseId, @RequestBody Competency competency) throws URISyntaxException {
        log.debug("REST request to create Competency : {}", competency);
        if (competency.getId() != null || competency.getTitle() == null || competency.getTitle().trim().isEmpty()) {
            throw new BadRequestException();
        }
        var course = courseRepository.findWithEagerCompetenciesByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        Competency competencyToCreate = new Competency();
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
    @EnforceAtLeastInstructor
    public ResponseEntity<Competency> importCompetency(@PathVariable long courseId, @RequestBody Competency competencyToImport) throws URISyntaxException {
        log.info("REST request to import a competency: {}", competencyToImport.getId());

        var course = courseRepository.findWithEagerCompetenciesByIdElseThrow(courseId);
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
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteCompetency(@PathVariable Long competencyId, @PathVariable Long courseId) {
        log.info("REST request to delete a Competency : {}", competencyId);

        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, competency);

        var relations = competencyRelationRepository.findAllByCompetencyId(competency.getId());
        if (!relations.isEmpty()) {
            throw new BadRequestException("Can not delete a competency that has active relations");
        }

        competencyProgressRepository.deleteAllByCompetencyId(competency.getId());

        competency.getExercises().forEach(exercise -> {
            exercise.getCompetencies().remove(competency);
            exerciseRepository.save(exercise);
        });

        competency.getLectureUnits().forEach(lectureUnit -> {
            lectureUnit.getCompetencies().remove(competency);
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
    @EnforceAtLeastStudent
    public ResponseEntity<CompetencyProgress> getCompetencyStudentProgress(@PathVariable Long courseId, @PathVariable Long competencyId,
            @RequestParam(defaultValue = "false") Boolean refresh) {
        log.debug("REST request to get student progress for competency: {}", competencyId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.STUDENT, course, competency);

        CompetencyProgress studentProgress;
        if (refresh) {
            studentProgress = competencyProgressService.updateCompetencyProgress(competencyId, user);
        }
        else {
            studentProgress = competencyProgressRepository.findEagerByCompetencyIdAndUserId(competencyId, user.getId()).orElse(null);
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
    @EnforceAtLeastInstructor
    public ResponseEntity<CourseCompetencyProgressDTO> getCompetencyCourseProgress(@PathVariable Long courseId, @PathVariable Long competencyId) {
        log.debug("REST request to get course progress for competency: {}", competencyId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdWithLectureUnitsAndCompletionsElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, competency);

        var numberOfStudents = competencyProgressRepository.countByCompetency(competency.getId());
        var numberOfMasteredStudents = competencyProgressRepository.countByCompetencyAndProgressAndConfidenceGreaterThanEqual(competency.getId(), 100.0,
                (double) competency.getMasteryThreshold());
        var averageStudentScore = RoundingUtil.roundScoreSpecifiedByCourseSettings(competencyProgressRepository.findAverageConfidenceByCompetencyId(competencyId).orElse(0.0),
                course);

        return ResponseEntity.ok().body(new CourseCompetencyProgressDTO(competency.getId(), numberOfStudents, numberOfMasteredStudents, averageStudentScore));
    }

    /**
     * GET /courses/:courseId/competencies/:competencyId/relations get the relations for the competency
     *
     * @param courseId     the id of the course to which the competency belongs
     * @param competencyId the id of the competency for which to fetch all relations
     * @return the ResponseEntity with status 200 (OK) and with a list of relations for the competency in the body
     */
    @GetMapping("/courses/{courseId}/competencies/{competencyId}/relations")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<CompetencyRelation>> getCompetencyRelations(@PathVariable Long competencyId, @PathVariable Long courseId) {
        log.debug("REST request to get relations for Competency : {}", competencyId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.STUDENT, course, competency);

        var relations = competencyRelationRepository.findAllByCompetencyId(competency.getId());
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
    @EnforceAtLeastInstructor
    public ResponseEntity<CompetencyRelation> createCompetencyRelation(@PathVariable Long courseId, @PathVariable Long tailCompetencyId, @PathVariable Long headCompetencyId,
            @RequestParam(defaultValue = "") String type) {
        log.info("REST request to create a relation between competencies {} and {}", tailCompetencyId, headCompetencyId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var tailCompetency = competencyRepository.findByIdElseThrow(tailCompetencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, tailCompetency);
        var headCompetency = competencyRepository.findByIdElseThrow(headCompetencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, headCompetency);

        try {
            var relationType = CompetencyRelation.RelationType.valueOf(type);

            var relation = new CompetencyRelation();
            relation.setTailCompetency(tailCompetency);
            relation.setHeadCompetency(headCompetency);
            relation.setType(relationType);

            var competencies = competencyRepository.findAllForCourse(course.getId());
            var competencyRelations = competencyRelationRepository.findAllByCourseId(course.getId());
            competencyRelations.add(relation);
            if (competencyService.doesCreateCircularRelation(competencies, competencyRelations)) {
                throw new BadRequestException("You can't define circular dependencies between competencies");
            }

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
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removeCompetencyRelation(@PathVariable Long competencyId, @PathVariable Long courseId, @PathVariable Long competencyRelationId) {
        log.info("REST request to remove a competency relation: {}", competencyId);
        var course = courseRepository.findByIdElseThrow(courseId);
        var competency = competencyRepository.findByIdElseThrow(competencyId);
        checkAuthorizationForCompetency(Role.INSTRUCTOR, course, competency);

        var relation = competencyRelationRepository.findById(competencyRelationId).orElseThrow();
        if (!relation.getTailCompetency().getId().equals(competency.getId())) {
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
    @EnforceAtLeastStudent
    public ResponseEntity<List<Competency>> getPrerequisites(@PathVariable Long courseId) {
        log.debug("REST request to get prerequisites for course with id: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        // Authorization check is skipped when course is open to self-enrollment
        if (!course.isEnrollmentEnabled()) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        }

        Set<Competency> prerequisites = competencyService.findAllPrerequisitesForCourse(course, user);

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
    @EnforceAtLeastInstructor
    public ResponseEntity<Competency> addPrerequisite(@PathVariable Long competencyId, @PathVariable Long courseId) {
        log.info("REST request to add a prerequisite: {}", competencyId);
        var course = courseRepository.findWithEagerCompetenciesByIdElseThrow(courseId);
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
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> removePrerequisite(@PathVariable Long competencyId, @PathVariable Long courseId) {
        log.info("REST request to remove a prerequisite: {}", competencyId);
        var course = courseRepository.findWithEagerCompetenciesByIdElseThrow(courseId);
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
    private void linkLectureUnitsToCompetency(Competency competency, Set<LectureUnit> lectureUnitsToAdd, Set<LectureUnit> lectureUnitsToRemove) {
        // Remove the competency from the old lecture units
        var lectureUnitsToRemoveFromDb = lectureUnitRepository.findAllByIdWithCompetenciesBidirectional(lectureUnitsToRemove.stream().map(LectureUnit::getId).toList());
        lectureUnitRepository.saveAll(lectureUnitsToRemoveFromDb.stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).map(lectureUnit -> {
            lectureUnit.getCompetencies().remove(competency);
            return lectureUnit;
        }).collect(Collectors.toSet()));
        exerciseRepository.saveAll(lectureUnitsToRemoveFromDb.stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit)
                .map(lectureUnit -> ((ExerciseUnit) lectureUnit).getExercise()).map(exercise -> {
                    exercise.getCompetencies().remove(competency);
                    return exercise;
                }).collect(Collectors.toSet()));

        // Add the competency to the new lecture units
        var lectureUnitsFromDb = lectureUnitRepository.findAllByIdWithCompetenciesBidirectional(lectureUnitsToAdd.stream().map(LectureUnit::getId).toList());
        var lectureUnitsWithoutExercises = lectureUnitsFromDb.stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).collect(Collectors.toSet());
        var exercises = lectureUnitsFromDb.stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).map(lectureUnit -> ((ExerciseUnit) lectureUnit).getExercise())
                .collect(Collectors.toSet());
        lectureUnitsWithoutExercises.stream().map(LectureUnit::getCompetencies).forEach(competencies -> competencies.add(competency));
        exercises.stream().map(Exercise::getCompetencies).forEach(competencies -> competencies.add(competency));
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
    private void checkAuthorizationForCompetency(Role role, @NotNull Course course, @NotNull Competency competency) {
        if (competency.getCourse() == null) {
            throw new ConflictException("A competency must belong to a course", "Competency", "competencyNoCourse");
        }
        if (!competency.getCourse().getId().equals(course.getId())) {
            throw new ConflictException("The competency does not belong to the correct course", "Competency", "competencyWrongCourse");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(role, course, null);
    }
}
