package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.LearningGoalService;
import de.tum.in.www1.artemis.web.rest.dto.CourseLearningGoalProgress;
import de.tum.in.www1.artemis.web.rest.dto.IndividualLearningGoalProgress;
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

    private final LectureUnitRepository lectureUnitRepository;

    private final LearningGoalService learningGoalService;

    public LearningGoalResource(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            LearningGoalRepository learningGoalRepository, LectureUnitRepository lectureUnitRepository, LearningGoalService learningGoalService) {
        this.courseRepository = courseRepository;
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
     * @return the ResponseEntity with status 200 (OK) and with the learning goal cours performance in the body
     */
    @GetMapping("/courses/{courseId}/goals/{learningGoalId}/course-progress")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<CourseLearningGoalProgress> getLearningGoalProgressOfCourse(@PathVariable Long learningGoalId, @PathVariable Long courseId,
            @RequestParam(defaultValue = "false", required = false) boolean useParticipantScoreTable) {
        log.debug("REST request to get course progress for LearningGoal : {}", learningGoalId);
        Optional<LearningGoal> optionalLearningGoal = learningGoalRepository.findByIdWithLectureUnitsBidirectional(learningGoalId);
        if (optionalLearningGoal.isEmpty()) {
            return notFound();
        }
        LearningGoal learningGoal = optionalLearningGoal.get();
        if (learningGoal.getCourse() == null) {
            return conflict();
        }
        if (!learningGoal.getCourse().getId().equals(courseId)) {
            return conflict();
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authorizationCheckService.isAtLeastInstructorInCourse(learningGoal.getCourse(), user)) {
            return forbidden();
        }

        CourseLearningGoalProgress courseLearningGoalProgress = learningGoalService.calculateLearningGoalCourseProgress(learningGoal, useParticipantScoreTable);
        return ResponseEntity.ok().body(courseLearningGoalProgress);
    }

    /**
     * GET /courses/:courseId/goals/:learningGoalId/progress  gets the learning goal progress for the logged in user
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
        Optional<LearningGoal> optionalLearningGoal = learningGoalRepository.findByIdWithLectureUnitsBidirectional(learningGoalId);
        if (optionalLearningGoal.isEmpty()) {
            return notFound();
        }
        LearningGoal learningGoal = optionalLearningGoal.get();
        if (learningGoal.getCourse() == null) {
            return conflict();
        }
        if (!learningGoal.getCourse().getId().equals(courseId)) {
            return conflict();
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authorizationCheckService.isAtLeastStudentInCourse(learningGoal.getCourse(), user)) {
            return forbidden();
        }

        IndividualLearningGoalProgress individualLearningGoalProgress = learningGoalService.calculateLearningGoalProgress(learningGoal, user, useParticipantScoreTable);
        return ResponseEntity.ok().body(individualLearningGoalProgress);
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
        log.info("REST request to remove a LearningGoal : {}", learningGoalId);
        Optional<LearningGoal> learningGoalOptional = this.learningGoalRepository.findByIdWithLectureUnitsBidirectional(learningGoalId);
        if (learningGoalOptional.isEmpty()) {
            return badRequest();
        }
        LearningGoal learningGoalFromDb = learningGoalOptional.get();
        if (learningGoalFromDb.getCourse() == null || !learningGoalFromDb.getCourse().getId().equals(courseId)) {
            return badRequest();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(learningGoalFromDb.getCourse(), null)) {
            return forbidden();
        }
        learningGoalRepository.deleteById(learningGoalFromDb.getId());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, learningGoalFromDb.getTitle())).build();
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

        if (!authorizationCheckService.isAtLeastStudentInCourse(course, user)) {
            return forbidden();
        }

        Set<LearningGoal> learningGoals = learningGoalRepository.findAllByCourseIdWithLectureUnitsUnidirectional(courseId);
        // if the user is a student the not yet released lecture units need to be filtered out
        if (authorizationCheckService.isOnlyStudentInCourse(course, user)) {
            for (LearningGoal learningGoal : learningGoals) {
                Set<LectureUnit> visibleLectureUnits = learningGoal.getLectureUnits().parallelStream().filter(LectureUnit::isVisibleToStudents).collect(Collectors.toSet());
                learningGoal.setLectureUnits(visibleLectureUnits);
            }
        }

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
        Optional<LearningGoal> optionalLearningGoal = learningGoalRepository.findByIdWithLectureUnitsBidirectional(learningGoalId);
        if (optionalLearningGoal.isEmpty()) {
            return notFound();
        }
        LearningGoal learningGoal = optionalLearningGoal.get();
        if (learningGoal.getCourse() == null) {
            return conflict();
        }
        if (!learningGoal.getCourse().getId().equals(courseId)) {
            return conflict();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(learningGoal.getCourse(), null)) {
            return forbidden();
        }
        return ResponseEntity.ok().body(learningGoal);
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
            return badRequest();
        }
        Optional<LearningGoal> learningGoalOptional = this.learningGoalRepository.findByIdWithLectureUnitsBidirectional(learningGoal.getId());
        if (learningGoalOptional.isEmpty()) {
            return badRequest();
        }
        LearningGoal learningGoalFromDb = learningGoalOptional.get();
        if (learningGoalFromDb.getCourse() == null || !learningGoalFromDb.getCourse().getId().equals(courseId)) {
            return badRequest();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(learningGoalFromDb.getCourse(), null)) {
            return forbidden();
        }
        learningGoalFromDb.setTitle(learningGoal.getTitle());
        learningGoalFromDb.setDescription(learningGoal.getDescription());
        // exchanging the lecture units send by the client to the corresponding entities from the database
        Set<LectureUnit> lectureUnitsToConnectWithLearningGoal;
        try {
            lectureUnitsToConnectWithLearningGoal = getLectureUnitsFromDatabase(learningGoal.getLectureUnits());
        }
        catch (IllegalArgumentException e) {
            return badRequest();
        }

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

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedLearningGoal.getId().toString()))
                .body(updatedLearningGoal);
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
            return badRequest();
        }
        Optional<Course> courseOptional = courseRepository.findWithEagerLearningGoalsById(courseId);
        if (courseOptional.isEmpty()) {
            return badRequest();
        }
        Course course = courseOptional.get();

        if (course.getLearningGoals().stream().map(LearningGoal::getTitle).anyMatch(title -> title.equals(learningGoalFromClient.getTitle()))) {
            return badRequest();
        }

        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            return forbidden();
        }

        Set<LectureUnit> lectureUnitsToConnectWithLearningGoal;
        try {
            lectureUnitsToConnectWithLearningGoal = getLectureUnitsFromDatabase(learningGoalFromClient.getLectureUnits());
        }
        catch (IllegalArgumentException e) {
            return badRequest();
        }

        LearningGoal learningGoalToCreate = new LearningGoal();
        learningGoalToCreate.setTitle(learningGoalFromClient.getTitle());
        learningGoalToCreate.setDescription(learningGoalFromClient.getDescription());
        learningGoalToCreate.setCourse(course);
        LearningGoal persistedLearningGoal = learningGoalRepository.save(learningGoalToCreate);
        persistedLearningGoal = this.learningGoalRepository.findByIdWithLectureUnitsBidirectional(persistedLearningGoal.getId()).get();

        for (LectureUnit lectureUnit : lectureUnitsToConnectWithLearningGoal) {
            persistedLearningGoal.addLectureUnit(lectureUnit);
        }
        persistedLearningGoal = learningGoalRepository.save(persistedLearningGoal);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/goals/" + persistedLearningGoal.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(persistedLearningGoal);

    }

    private Set<LectureUnit> getLectureUnitsFromDatabase(Set<LectureUnit> lectureUnitsFromClient) throws IllegalArgumentException {
        Set<LectureUnit> lectureUnitsFromDatabase = new HashSet<>();
        if (lectureUnitsFromClient != null && !lectureUnitsFromClient.isEmpty()) {
            for (LectureUnit lectureUnit : lectureUnitsFromClient) {
                if (lectureUnit.getId() == null) {
                    throw new IllegalArgumentException();
                }
                Optional<LectureUnit> lectureUnitFromDbOptional = lectureUnitRepository.findByIdWithLearningGoals(lectureUnit.getId());
                if (lectureUnitFromDbOptional.isEmpty()) {
                    throw new IllegalArgumentException();
                }
                lectureUnitsFromDatabase.add(lectureUnitFromDbOptional.get());
            }
        }
        return lectureUnitsFromDatabase;
    }

}
