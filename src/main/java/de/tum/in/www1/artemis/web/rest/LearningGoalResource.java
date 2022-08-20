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

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.domain.LearningGoalRelation;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.LearningGoalService;
import de.tum.in.www1.artemis.web.rest.dto.CourseLearningGoalProgress;
import de.tum.in.www1.artemis.web.rest.dto.IndividualLearningGoalProgress;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
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

    public LearningGoalResource(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            LearningGoalRepository learningGoalRepository, LearningGoalRelationRepository learningGoalRelationRepository, LectureUnitRepository lectureUnitRepository,
            LearningGoalService learningGoalService) {
        this.courseRepository = courseRepository;
        this.learningGoalRelationRepository = learningGoalRelationRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.learningGoalRepository = learningGoalRepository;
        this.learningGoalService = learningGoalService;
    }

    /**
     * GET /courses/:courseId/goals/:learningGoalId/course-progress  gets the learning goal progress for the whole course
     *
     * @param courseId                 the id of the course to which the learning goal belongs
     * @param learningGoalId           the id of the learning goal for which to get the progress
     * @param useParticipantScoreTable use the participant score table instead of going through participation -> submission -> result
     * @return the ResponseEntity with status 200 (OK) and with the learning goal course performance in the body
     */
    @GetMapping("/courses/{courseId}/goals/{learningGoalId}/course-progress")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<CourseLearningGoalProgress> getLearningGoalProgressOfCourse(@PathVariable Long learningGoalId, @PathVariable Long courseId,
            @RequestParam(defaultValue = "false", required = false) boolean useParticipantScoreTable) {
        log.debug("REST request to get course progress for LearningGoal : {}", learningGoalId);
        var learningGoal = findLearningGoal(Role.INSTRUCTOR, learningGoalId, courseId, true, true);
        CourseLearningGoalProgress courseLearningGoalProgress = learningGoalService.calculateLearningGoalCourseProgress(learningGoal, useParticipantScoreTable);
        return ResponseEntity.ok().body(courseLearningGoalProgress);
    }

    /**
     * GET /courses/:courseId/goals/:learningGoalId/progress  gets the learning goal progress for the logged-in user
     *
     * @param courseId                 the id of the course to which the learning goal belongs
     * @param learningGoalId           the id of the learning goal for which to get the progress
     * @param useParticipantScoreTable use the participant score table instead of going through participation -> submission -> result
     * @return the ResponseEntity with status 200 (OK) and with the learning goal performance in the body
     */
    @GetMapping("/courses/{courseId}/goals/{learningGoalId}/individual-progress")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<IndividualLearningGoalProgress> getLearningGoalProgress(@PathVariable Long learningGoalId, @PathVariable Long courseId,
            @RequestParam(defaultValue = "false", required = false) boolean useParticipantScoreTable) {
        log.debug("REST request to get performance for LearningGoal : {}", learningGoalId);
        var learningGoal = findLearningGoal(Role.STUDENT, learningGoalId, courseId, true, true);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var individualLearningGoalProgress = learningGoalService.calculateLearningGoalProgress(learningGoal, user, useParticipantScoreTable);
        return ResponseEntity.ok().body(individualLearningGoalProgress);
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
    public ResponseEntity<Void> deleteLectureUnit(@PathVariable Long learningGoalId, @PathVariable Long courseId) {
        log.info("REST request to delete a LearningGoal : {}", learningGoalId);

        var learningGoal = findLearningGoal(Role.INSTRUCTOR, learningGoalId, courseId, false, false);

        var relations = learningGoalRelationRepository.findAllByLearningGoalId(learningGoal.getId());

        if (!relations.isEmpty()) {
            throw new BadRequestException("Can not delete a learning goal that has active relations");
        }

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

        Set<LearningGoal> learningGoals = learningGoalService.findAllForCourse(course, user);

        return ResponseEntity.ok(new ArrayList<>(learningGoals));
    }

    /**
     * GET /courses/:courseId/goals/:learningGoalId : gets the learning goal with the specified id
     *
     * @param learningGoalId the id of the textUnit to retrieve
     * @param courseId the id of the course to which the learning goal belongs
     * @return the ResponseEntity with status 200 (OK) and with body the learning goal, or with status 404 (Not Found)
     */
    @GetMapping("/courses/{courseId}/goals/{learningGoalId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<LearningGoal> getLearningGoal(@PathVariable Long learningGoalId, @PathVariable Long courseId) {
        log.debug("REST request to get LearningGoal : {}", learningGoalId);
        var learningGoal = findLearningGoal(Role.INSTRUCTOR, learningGoalId, courseId, false, true);
        return ResponseEntity.ok().body(learningGoal);
    }

    private LearningGoal findLearningGoal(Role role, Long learningGoalId, Long courseId, boolean withCompletions, boolean withLectureUnits) {
        LearningGoal learningGoal;
        if (withCompletions && withLectureUnits) {
            learningGoal = learningGoalRepository.findByIdWithLectureUnitsAndCompletionsElseThrow(learningGoalId);
        }
        else if (withLectureUnits) {
            learningGoal = learningGoalRepository.findByIdWithLectureUnitsBidirectionalElseThrow(learningGoalId);
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
        var learningGoalFromDb = this.learningGoalRepository.findByIdWithLectureUnitsBidirectionalElseThrow(learningGoal.getId());
        if (learningGoalFromDb.getCourse() == null || !learningGoalFromDb.getCourse().getId().equals(courseId)) {
            throw new BadRequestException();
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, learningGoalFromDb.getCourse(), null);
        learningGoalFromDb.setTitle(learningGoal.getTitle());
        learningGoalFromDb.setDescription(learningGoal.getDescription());
        learningGoalFromDb.setTaxonomy(learningGoal.getTaxonomy());

        // exchanging the lecture units send by the client to the corresponding entities from the database
        Set<LectureUnit> lectureUnitsToConnectWithLearningGoal = getLectureUnitsFromDatabase(learningGoal.getLectureUnits());
        // remove lecture units no longer associated with learning goal
        Set<LectureUnit> lectureUnitsToRemove = learningGoalFromDb.getLectureUnits().stream().filter(lectureUnit -> !lectureUnitsToConnectWithLearningGoal.contains(lectureUnit))
                .collect(Collectors.toSet());
        // add lecture units newly associated with learning goal
        Set<LectureUnit> lectureUnitsToAdd = lectureUnitsToConnectWithLearningGoal.stream().filter(lectureUnit -> !learningGoalFromDb.getLectureUnits().contains(lectureUnit))
                .collect(Collectors.toSet());
        for (LectureUnit lectureUnit : lectureUnitsToRemove) {
            learningGoalFromDb.removeLectureUnit(lectureUnit);
        }
        for (LectureUnit lectureUnit : lectureUnitsToAdd) {
            learningGoalFromDb.addLectureUnit(lectureUnit);
        }

        LearningGoal updatedLearningGoal = learningGoalRepository.save(learningGoalFromDb);

        return ResponseEntity.ok(updatedLearningGoal);
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
        Course course = courseRepository.findWithEagerLearningGoalsById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
        if (course.getLearningGoals().stream().map(LearningGoal::getTitle).anyMatch(title -> title.equals(learningGoalFromClient.getTitle()))) {
            throw new BadRequestException();
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        Set<LectureUnit> lectureUnitsToConnectWithLearningGoal = getLectureUnitsFromDatabase(learningGoalFromClient.getLectureUnits());
        learningGoalFromClient.setLectureUnits(lectureUnitsToConnectWithLearningGoal);
        learningGoalFromClient.setCourse(course);
        LearningGoal persistedLearningGoal = learningGoalRepository.save(learningGoalFromClient);

        persistedLearningGoal = this.learningGoalRepository.findByIdWithLectureUnitsBidirectionalElseThrow(persistedLearningGoal.getId());

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
     * @param courseId  the id of the course to which the learning goals belongs
     * @param tailLearningGoalId the id of the prerequisite (learning goal) to add
     * @param headLearningGoalId the id of the prerequisite (learning goal) to add
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
     * @param courseId the id of the course for which the learning goal is a prerequisite
     * @param learningGoalId the id of the prerequisite (learning goal) to remove
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/courses/{courseId}/goals/{learningGoalId}/relations/{learningGoalRelationId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> removeLearningGoalRelation(@PathVariable Long learningGoalId, @PathVariable Long courseId, @PathVariable Long learningGoalRelationId) {
        log.info("REST request to remove a learning goal relation: {}", learningGoalId);
        var learningGoal = findLearningGoal(Role.INSTRUCTOR, learningGoalId, courseId, false, false);

        var relation = learningGoalRelationRepository.findById(learningGoalRelationId).orElseThrow();
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
        var course = courseRepository.findWithEagerLearningGoalsById(courseId).orElseThrow();
        var learningGoal = learningGoalRepository.findByIdWithConsecutiveCourses(learningGoalId).orElseThrow();

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
        var course = courseRepository.findWithEagerLearningGoalsById(courseId).orElseThrow();
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
                var lectureUnitFromDb = lectureUnitRepository.findByIdWithLearningGoals(lectureUnit.getId())
                        .orElseThrow(() -> new EntityNotFoundException("LectureUnit", lectureUnit.getId()));
                lectureUnitsFromDatabase.add(lectureUnitFromDb);
            }
        }
        return lectureUnitsFromDatabase;
    }

}
