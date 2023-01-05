package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ModelAssessmentKnowledgeService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.TextAssessmentKnowledgeService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.CourseLearningGoalProgress;
import de.tum.in.www1.artemis.web.rest.dto.IndividualLearningGoalProgress;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

class LearningGoalIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "learninggoalintegrationtest";

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private TextUnitRepository textUnitRepository;

    @Autowired
    private ExerciseUnitRepository exerciseUnitRepository;

    @Autowired
    private LearningGoalRepository learningGoalRepository;

    @Autowired
    private LearningGoalRelationRepository learningGoalRelationRepository;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private TextAssessmentKnowledgeService textAssessmentKnowledgeService;

    @Autowired
    private ModelAssessmentKnowledgeService modelAssessmentKnowledgeService;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @Autowired
    private TeamRepository teamRepository;

    private Long idOfCourse;

    private Long idOfCourseTwo;

    private Long idOfLearningGoal;

    private Long idOfLectureOne;

    private Long idOfTextUnitOfLectureOne;

    private Long idOfLectureTwo;

    private Long idOfTextUnitOfLectureTwo;

    private Long idOfTextExercise;

    private Exercise modelingExercise;

    private Long idOfModelingExercise;

    private Exercise teamTextExercise;

    private Exercise textExercise;

    private Long idOfTeamTextExercise;

    private List<Team> teams;

    @BeforeEach
    void setupTestScenario() {
        participantScoreSchedulerService.activate();
        // We can not remove the teams due to the existing participations etc., but we can remove all students from them so that they are no longer accessed
        // The students are reused between tests
        // TODO: Check if this can also be solved by changing ExerciseRepo#calculateStatisticsForTeamCourseExercises to check for the exerciseId
        var existingTeams = teamRepository.findAll().stream().filter(t -> t.getShortName().contains(TEST_PREFIX)).toList();
        existingTeams.forEach(t -> t.setStudents(Set.of()));
        teamRepository.saveAll(existingTeams);

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        this.database.addUsers(TEST_PREFIX, 5, 1, 0, 1);

        // Add users that are not in the course
        database.createAndSaveUser(TEST_PREFIX + "student42");
        database.createAndSaveUser(TEST_PREFIX + "tutor42");
        database.createAndSaveUser(TEST_PREFIX + "instructor42");

        // creating course
        Course course = this.database.createCourse();
        idOfCourse = course.getId();

        Course courseTwo = this.database.createCourse();
        idOfCourseTwo = courseTwo.getId();

        User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();

        createLectureOne(course);
        createLectureTwo(course);
        textExercise = createTextExercise(pastTimestamp, pastTimestamp, pastTimestamp);
        createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 0.0, 50, true);
        modelingExercise = createModelingExercise(pastTimestamp, pastTimestamp, pastTimestamp);
        createParticipationSubmissionAndResult(idOfModelingExercise, student1, 10.0, 0.0, 50, true);
        teamTextExercise = createTeamTextExercise(pastTimestamp, pastTimestamp, pastTimestamp);
        User tutor = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").get();
        // will also create users
        teams = database.addTeamsForExerciseFixedTeamSize(TEST_PREFIX, "lgi", teamTextExercise, 5, tutor, 3);

        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(0), 10.0, 0.0, 50, true);

        await().until(() -> participantScoreRepository.findAllByExercise(textExercise).size() == 1);
        await().until(() -> participantScoreRepository.findAllByExercise(teamTextExercise).size() == 1);
        await().until(() -> participantScoreRepository.findAllByExercise(modelingExercise).size() == 1);

        creatingLectureUnitsOfLectureOne();
        creatingLectureUnitsOfLectureTwo();
        createLearningGoal();
        createPrerequisite();
    }

    private void createLearningGoal() {
        Course course = courseRepository.findWithEagerLearningGoalsById(idOfCourse).get();
        LearningGoal learningGoal = new LearningGoal();
        learningGoal.setTitle("LearningGoalOne");
        learningGoal.setDescription("This is an example learning goal");
        learningGoal.setTaxonomy(LearningGoalTaxonomy.UNDERSTAND);
        learningGoal.setCourse(course);

        LearningGoal otherLearningGoal = new LearningGoal();
        otherLearningGoal.setTitle("Detailed sub learning goal");
        otherLearningGoal.setDescription("A communi observantia non est recedendum.");
        otherLearningGoal.setCourse(course);
        learningGoalRepository.save(otherLearningGoal);

        var lecture1 = lectureRepository.findByIdWithLectureUnits(idOfLectureOne).get();
        var lecture2 = lectureRepository.findByIdWithLectureUnits(idOfLectureTwo).get();
        Set<LectureUnit> connectedLectureUnits = new HashSet<>(lecture1.getLectureUnits());
        connectedLectureUnits.addAll(lecture2.getLectureUnits());

        learningGoal.setLectureUnits(connectedLectureUnits.stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).collect(Collectors.toSet()));
        learningGoal.setExercises(connectedLectureUnits.stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit)
                .map(lectureUnit -> ((ExerciseUnit) lectureUnit).getExercise()).collect(Collectors.toSet()));

        learningGoal = learningGoalRepository.save(learningGoal);
        idOfLearningGoal = learningGoal.getId();
    }

    private void createPrerequisite() {
        // Add the first learning goal as a prerequisite to the other course
        LearningGoal learningGoal = learningGoalRepository.findByIdWithConsecutiveCourses(idOfLearningGoal).get();
        Course course2 = courseRepository.findWithEagerLearningGoalsById(idOfCourseTwo).get();
        course2.addPrerequisite(learningGoal);
        courseRepository.save(course2);
    }

    private void creatingLectureUnitsOfLectureOne() {
        // creating lecture units for lecture one
        TextUnit textUnit = new TextUnit();
        textUnit.setName("TextUnitOfLectureOne");
        textUnit = textUnitRepository.save(textUnit);
        idOfTextUnitOfLectureOne = textUnit.getId();

        Exercise textExercise = exerciseRepository.findById(idOfTextExercise).get();
        ExerciseUnit textExerciseUnit = new ExerciseUnit();
        textExerciseUnit.setExercise(textExercise);
        textExerciseUnit = exerciseUnitRepository.save(textExerciseUnit);

        Exercise modelingExercise = exerciseRepository.findById(idOfModelingExercise).get();
        ExerciseUnit modelingExerciseUnit = new ExerciseUnit();
        modelingExerciseUnit.setExercise(modelingExercise);
        modelingExerciseUnit = exerciseUnitRepository.save(modelingExerciseUnit);

        Exercise textTeamExercise = exerciseRepository.findById(idOfTeamTextExercise).get();
        ExerciseUnit teamTextExerciseUnit = new ExerciseUnit();
        teamTextExerciseUnit.setExercise(textTeamExercise);
        teamTextExerciseUnit = exerciseUnitRepository.save(teamTextExerciseUnit);

        Lecture lectureOne = lectureRepository.findByIdWithLectureUnits(idOfLectureOne).get();
        for (LectureUnit lectureUnit : List.of(textUnit, textExerciseUnit, modelingExerciseUnit, teamTextExerciseUnit)) {
            lectureOne.addLectureUnit(lectureUnit);
        }
        lectureRepository.save(lectureOne);
    }

    private void creatingLectureUnitsOfLectureTwo() {
        // creating lecture units for lecture one
        TextUnit textUnit = new TextUnit();
        textUnit.setName("TextUnitOfLectureTwo");
        textUnit = textUnitRepository.save(textUnit);
        idOfTextUnitOfLectureTwo = textUnit.getId();

        Exercise textExercise = exerciseRepository.findById(idOfTextExercise).get();
        ExerciseUnit textExerciseUnit = new ExerciseUnit();
        textExerciseUnit.setExercise(textExercise);
        textExerciseUnit = exerciseUnitRepository.save(textExerciseUnit);

        Exercise modelingExercise = exerciseRepository.findById(idOfModelingExercise).get();
        ExerciseUnit modelingExerciseUnit = new ExerciseUnit();
        modelingExerciseUnit.setExercise(modelingExercise);
        modelingExerciseUnit = exerciseUnitRepository.save(modelingExerciseUnit);

        Lecture lectureTwo = lectureRepository.findByIdWithLectureUnits(idOfLectureTwo).get();
        for (LectureUnit lectureUnit : List.of(textUnit, textExerciseUnit, modelingExerciseUnit)) {
            lectureTwo.addLectureUnit(lectureUnit);
        }
        lectureRepository.save(lectureTwo);
    }

    private void createLectureOne(Course course) {
        Lecture lectureOne = new Lecture();
        lectureOne.setTitle("LectureOne");
        lectureOne.setCourse(course);
        lectureOne = lectureRepository.save(lectureOne);
        idOfLectureOne = lectureOne.getId();
    }

    private void createLectureTwo(Course course) {
        Lecture lectureTwo = new Lecture();
        lectureTwo.setTitle("LectureTwo");
        lectureTwo.setCourse(course);
        lectureTwo = lectureRepository.save(lectureTwo);
        idOfLectureTwo = lectureTwo.getId();
    }

    private TextExercise createTextExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise.setKnowledge(textAssessmentKnowledgeService.createNewKnowledge());
        textExercise = exerciseRepository.save(textExercise);
        idOfTextExercise = textExercise.getId();

        return textExercise;
    }

    private ModelingExercise createModelingExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        modelingExercise.setMaxPoints(10.0);
        modelingExercise.setBonusPoints(0.0);
        modelingExercise.setKnowledge(modelAssessmentKnowledgeService.createNewKnowledge());
        modelingExercise = exerciseRepository.save(modelingExercise);
        idOfModelingExercise = modelingExercise.getId();

        return modelingExercise;
    }

    private TextExercise createTeamTextExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setMode(ExerciseMode.TEAM);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise.setKnowledge(textAssessmentKnowledgeService.createNewKnowledge());
        textExercise = exerciseRepository.save(textExercise);
        idOfTeamTextExercise = textExercise.getId();

        return textExercise;
    }

    private void createParticipationSubmissionAndResult(Long idOfExercise, Participant participant, Double pointsOfExercise, Double bonusPointsOfExercise, long scoreAwarded,
            boolean rated) {
        Exercise exercise = exerciseRepository.findById(idOfExercise).get();

        if (!exercise.getMaxPoints().equals(pointsOfExercise)) {
            exercise.setMaxPoints(pointsOfExercise);
        }
        if (!exercise.getBonusPoints().equals(bonusPointsOfExercise)) {
            exercise.setBonusPoints(bonusPointsOfExercise);
        }
        exercise = exerciseRepository.save(exercise);

        StudentParticipation studentParticipation = participationService.startExercise(exercise, participant, false);

        Submission submission;
        if (exercise instanceof ProgrammingExercise) {
            submission = new ProgrammingSubmission();
        }
        else if (exercise instanceof ModelingExercise) {
            submission = new ModelingSubmission();
        }
        else if (exercise instanceof TextExercise) {
            submission = new TextSubmission();
        }
        else if (exercise instanceof FileUploadExercise) {
            submission = new FileUploadSubmission();
        }
        else if (exercise instanceof QuizExercise) {
            submission = new QuizSubmission();
        }
        else {
            throw new RuntimeException("Unsupported exercise type: " + exercise);
        }

        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        submission = submissionRepository.save(submission);

        // result
        Result result = ModelFactory.generateResult(rated, scoreAwarded);
        result.setParticipation(studentParticipation);
        result.setCompletionDate(ZonedDateTime.now());
        result = resultRepository.save(result);

        submission.addResult(result);
        result.setSubmission(submission);
        submissionRepository.save(submission);
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/courses/" + idOfCourse + "/goals", new LearningGoal(), HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + idOfCourse + "/goals", new LearningGoal(), HttpStatus.FORBIDDEN);
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.FORBIDDEN, LearningGoal.class);
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/course-progress", HttpStatus.FORBIDDEN, CourseLearningGoalProgress.class);
        request.delete("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAll_asEditor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLearningGoal_asInstructor_shouldReturnLearningGoal() throws Exception {
        LearningGoal learningGoal = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.OK, LearningGoal.class);
        assertThat(learningGoal.getId()).isEqualTo(idOfLearningGoal);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void getLearningGoal_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.FORBIDDEN, LearningGoal.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLearningGoalsOfCourse_asStudent1_shouldReturnLearningGoals() throws Exception {
        var lecture1 = lectureRepository.findByIdWithLectureUnits(idOfLectureOne).get();
        TextUnit unreleasedLectureUnit = new TextUnit();
        unreleasedLectureUnit.setName("TextUnitOfLectureOne");
        unreleasedLectureUnit.setReleaseDate(ZonedDateTime.now().plus(5, ChronoUnit.DAYS));
        unreleasedLectureUnit = textUnitRepository.save(unreleasedLectureUnit);
        lecture1.addLectureUnit(unreleasedLectureUnit);
        lectureRepository.save(lecture1);

        Course course = courseRepository.findByIdElseThrow(idOfCourse);
        LearningGoal newLearningGoal = new LearningGoal();
        newLearningGoal.setTitle("Title");
        newLearningGoal.setDescription("Description");
        newLearningGoal.setCourse(course);
        newLearningGoal.setLectureUnits(new HashSet<>(List.of(unreleasedLectureUnit)));
        learningGoalRepository.save(newLearningGoal);

        List<LearningGoal> learningGoalsOfCourse = request.getList("/api/courses/" + idOfCourse + "/goals", HttpStatus.OK, LearningGoal.class);

        assertThat(learningGoalsOfCourse.stream().filter(l -> l.getId().equals(idOfLearningGoal)).findFirst()).isPresent();
        assertThat(learningGoalsOfCourse.stream().filter(l -> l.getId().equals(newLearningGoal.getId())).findFirst().get().getLectureUnits()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void getLearningGoalsOfCourse_asStudentNotInCourse_shouldReturnForbidden() throws Exception {
        request.getList("/api/courses/" + idOfCourse + "/goals", HttpStatus.FORBIDDEN, LearningGoal.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLearningGoal_asInstructor_shouldDeleteLearningGoal() throws Exception {
        request.delete("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.OK);
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.NOT_FOUND, LearningGoal.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLearningGoal_witRelatedGoals_shouldReturnBadRequest() throws Exception {
        LearningGoal learningGoal = learningGoalRepository.findByIdElseThrow(idOfLearningGoal);
        Course course = courseRepository.findByIdElseThrow(idOfCourse);
        LearningGoal learningGoal1 = database.createLearningGoal(course);

        var relation = new LearningGoalRelation();
        relation.setTailLearningGoal(learningGoal);
        relation.setHeadLearningGoal(learningGoal1);
        relation.setType(LearningGoalRelation.RelationType.EXTENDS);
        learningGoalRelationRepository.save(relation);

        // Should return bad request, as the learning goal still has relations
        request.delete("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void deleteLearningGoal_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.delete("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteCourse_asAdmin_shouldAlsoDeleteLearningGoal() throws Exception {
        request.delete("/api/admin/courses/" + idOfCourse, HttpStatus.OK);
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.NOT_FOUND, LearningGoal.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createLearningGoalRelation() throws Exception {
        Course course = courseRepository.findByIdElseThrow(idOfCourse);
        Long idOfOtherLearningGoal = database.createLearningGoal(course).getId();

        request.postWithoutResponseBody(
                "/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/relations/" + idOfOtherLearningGoal + "?type=" + LearningGoalRelation.RelationType.EXTENDS.name(),
                HttpStatus.OK, new LinkedMultiValueMap<>());

        var relations = learningGoalRelationRepository.findAllByLearningGoalId(idOfLearningGoal);
        assertThat(relations).hasSize(1);
        assertThat(relations.stream().findFirst().get().getType()).isEqualTo(LearningGoalRelation.RelationType.EXTENDS);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void createLearningGoalRelation_shouldReturnForbidden() throws Exception {
        Course course = courseRepository.findByIdElseThrow(idOfCourse);
        Long idOfOtherLearningGoal = database.createLearningGoal(course).getId();

        request.post(
                "/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/relations/" + idOfOtherLearningGoal + "?type=" + LearningGoalRelation.RelationType.EXTENDS.name(),
                null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLearningGoalRelations() throws Exception {
        LearningGoal learningGoal = learningGoalRepository.findByIdElseThrow(idOfLearningGoal);
        Course course = courseRepository.findByIdElseThrow(idOfCourse);
        LearningGoal otherLearningGoal = database.createLearningGoal(course);

        var relation = new LearningGoalRelation();
        relation.setTailLearningGoal(learningGoal);
        relation.setHeadLearningGoal(otherLearningGoal);
        relation.setType(LearningGoalRelation.RelationType.EXTENDS);
        relation = learningGoalRelationRepository.save(relation);

        var relations = request.getList("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/relations", HttpStatus.OK, LearningGoalRelation.class);

        assertThat(relations).hasSize(1);
        assertThat(relations.get(0)).isEqualTo(relation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLearningGoalRelation() throws Exception {
        LearningGoal learningGoal = learningGoalRepository.findByIdElseThrow(idOfLearningGoal);
        Course course = courseRepository.findByIdElseThrow(idOfCourse);
        LearningGoal otherLearningGoal = database.createLearningGoal(course);

        var relation = new LearningGoalRelation();
        relation.setTailLearningGoal(learningGoal);
        relation.setHeadLearningGoal(otherLearningGoal);
        relation.setType(LearningGoalRelation.RelationType.EXTENDS);
        relation = learningGoalRelationRepository.save(relation);

        request.delete("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/relations/" + relation.getId(), HttpStatus.OK);

        var relations = learningGoalRelationRepository.findAllByLearningGoalId(idOfLearningGoal);
        assertThat(relations).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLecture_asInstructor_shouldUpdateLearningGoal() throws Exception {
        request.delete("/api/lectures/" + idOfLectureTwo, HttpStatus.OK);
        LearningGoal learningGoal = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.OK, LearningGoal.class);
        assertThat(learningGoal.getLectureUnits().stream().map(DomainObject::getId)).containsAll(Set.of(idOfTextUnitOfLectureOne));
        assertThat(learningGoal.getLectureUnits().stream().map(DomainObject::getId)).doesNotContainAnyElementsOf(Set.of(idOfTextUnitOfLectureTwo));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnit_asInstructor_shouldUpdateLearningGoal() throws Exception {
        request.delete("/api/lectures/" + idOfLectureTwo + "/lecture-units/" + idOfTextUnitOfLectureTwo, HttpStatus.OK);
        LearningGoal learningGoal = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.OK, LearningGoal.class);
        assertThat(learningGoal.getLectureUnits().stream().map(DomainObject::getId)).containsAll(Set.of(idOfTextUnitOfLectureOne));
        assertThat(learningGoal.getLectureUnits().stream().map(DomainObject::getId)).doesNotContainAnyElementsOf(Set.of(idOfTextUnitOfLectureTwo));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLearningGoalProgress_asStudent1_shouldReturnProgressTenOutOfTwenty() throws Exception {
        IndividualLearningGoalProgress individualLearningGoalProgress = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/individual-progress",
                HttpStatus.OK, IndividualLearningGoalProgress.class);
        assertThat(individualLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal).isEqualTo(30.0);
        assertThat(individualLearningGoalProgress.pointsAchievedByStudentInLearningGoal).isEqualTo(10.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLearningGoalProgress_asStudent1_usingParticipantScores_shouldReturnProgressTenOutOfTwenty() throws Exception {
        IndividualLearningGoalProgress individualLearningGoalProgress = request.get(
                "/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/individual-progress?useParticipantScoreTable=true", HttpStatus.OK,
                IndividualLearningGoalProgress.class);
        assertThat(individualLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal).isEqualTo(30.0);
        assertThat(individualLearningGoalProgress.pointsAchievedByStudentInLearningGoal).isEqualTo(10.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "team1student1", roles = "USER")
    void getLearningGoalProgress_asTeam1Student1_shouldReturnProgressTenOutOfThirty() throws Exception {
        IndividualLearningGoalProgress individualLearningGoalProgress = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/individual-progress",
                HttpStatus.OK, IndividualLearningGoalProgress.class);
        assertThat(individualLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal).isEqualTo(30.0);
        assertThat(individualLearningGoalProgress.pointsAchievedByStudentInLearningGoal).isEqualTo(5.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "team1student1", roles = "USER")
    void getLearningGoalProgress_asTeam1Student1_usingParticipantScores_shouldReturnProgressTenOutOfThirty() throws Exception {
        IndividualLearningGoalProgress individualLearningGoalProgress = request.get(
                "/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/individual-progress?useParticipantScoreTable=true", HttpStatus.OK,
                IndividualLearningGoalProgress.class);
        assertThat(individualLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal).isEqualTo(30.0);
        assertThat(individualLearningGoalProgress.pointsAchievedByStudentInLearningGoal).isEqualTo(5.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLearningGoalCourseProgressTeamsTest_asInstructorOne() throws Exception {
        // adjustToCustomGroup("asInstructorOne");

        cleanUpInitialParticipations();

        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(0), 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from team
        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(0), 10.0, 0.0, 50, false);

        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(1), 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from team
        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(1), 10.0, 0.0, 10, false);

        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(2), 10.0, 0.0, 10, true);
        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(3), 10.0, 0.0, 50, true);

        CourseLearningGoalProgress courseLearningGoalProgress = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/course-progress", HttpStatus.OK,
                CourseLearningGoalProgress.class);

        assertThat(courseLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal).isEqualTo(30.0);
        assertThat(courseLearningGoalProgress.averagePointsAchievedByStudentInLearningGoal).isEqualTo(3.0);

        assertThatSpecificCourseLectureUnitProgressExists(courseLearningGoalProgress, 80.0, 4, 30);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLearningGoalCourseProgressIndividualTest_asInstructorOne() throws Exception {
        var course = courseRepository.findById(idOfCourse).get();
        course.setStudentGroupName(TEST_PREFIX + "student" + "individualTest");
        courseRepository.save(course);
        adjustStudentGroupsToCustomGroups("individualTest");

        cleanUpInitialParticipations();
        User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        User student2 = userRepository.findOneByLogin(TEST_PREFIX + "student2").get();
        User student3 = userRepository.findOneByLogin(TEST_PREFIX + "student3").get();
        User student4 = userRepository.findOneByLogin(TEST_PREFIX + "student4").get();
        User instructor1 = userRepository.findOneByLogin(TEST_PREFIX + "instructor1").get();

        createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from team
        createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 0.0, 50, false);

        createParticipationSubmissionAndResult(idOfTextExercise, student2, 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from student
        createParticipationSubmissionAndResult(idOfTextExercise, student2, 10.0, 0.0, 10, false);

        createParticipationSubmissionAndResult(idOfTextExercise, student3, 10.0, 0.0, 10, true);
        createParticipationSubmissionAndResult(idOfTextExercise, student4, 10.0, 0.0, 50, true);

        createParticipationSubmissionAndResult(idOfTextExercise, instructor1, 10.0, 0.0, 100, true); // will be ignored as not a student

        CourseLearningGoalProgress courseLearningGoalProgress = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/course-progress", HttpStatus.OK,
                CourseLearningGoalProgress.class);

        assertThat(courseLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal).isEqualTo(30.0);
        assertThat(courseLearningGoalProgress.averagePointsAchievedByStudentInLearningGoal).isEqualTo(3.0);

        assertThatSpecificCourseLectureUnitProgressExists(courseLearningGoalProgress, 20.0, 4, 30.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLearningGoalCourseProgressIndividualTest_asInstructorOne_usingParticipantScoreTable() throws Exception {
        var course = courseRepository.findById(idOfCourse).get();
        course.setStudentGroupName(TEST_PREFIX + "student" + "individualTestTable");
        courseRepository.save(course);
        adjustStudentGroupsToCustomGroups("individualTestTable");

        cleanUpInitialParticipations();
        User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        User student2 = userRepository.findOneByLogin(TEST_PREFIX + "student2").get();
        User student3 = userRepository.findOneByLogin(TEST_PREFIX + "student3").get();
        User student4 = userRepository.findOneByLogin(TEST_PREFIX + "student4").get();
        User instructor1 = userRepository.findOneByLogin(TEST_PREFIX + "instructor1").get();

        createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from team
        createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 0.0, 50, false);

        createParticipationSubmissionAndResult(idOfTextExercise, student2, 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from student
        createParticipationSubmissionAndResult(idOfTextExercise, student2, 10.0, 0.0, 10, false);

        createParticipationSubmissionAndResult(idOfTextExercise, student3, 10.0, 0.0, 10, true);
        createParticipationSubmissionAndResult(idOfTextExercise, student4, 10.0, 0.0, 50, true);

        createParticipationSubmissionAndResult(idOfTextExercise, instructor1, 10.0, 0.0, 100, true); // will be ignored as not a student

        await().until(() -> participantScoreRepository.findAllByExercise(textExercise).size() == 5);

        CourseLearningGoalProgress courseLearningGoalProgress = request.get(
                "/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/course-progress?useParticipantScoreTable=true", HttpStatus.OK, CourseLearningGoalProgress.class);

        assertThat(courseLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal).isEqualTo(30.0);
        assertThat(courseLearningGoalProgress.averagePointsAchievedByStudentInLearningGoal).isEqualTo(3.0);

        assertThatSpecificCourseLectureUnitProgressExists(courseLearningGoalProgress, 20.0, 4, 30.0);
    }

    private void assertThatSpecificCourseLectureUnitProgressExists(CourseLearningGoalProgress courseLearningGoalProgress, double expectedParticipationRate,
            int expectedNoOfParticipants, double expectedAverageScore) {
        boolean foundProgressWithCorrectNumbers = false;
        for (CourseLearningGoalProgress.CourseLectureUnitProgress courseLectureUnitProgress : courseLearningGoalProgress.progressInLectureUnits) {
            if (courseLectureUnitProgress.participationRate.equals(expectedParticipationRate) && courseLectureUnitProgress.noOfParticipants.equals(expectedNoOfParticipants)
                    && courseLectureUnitProgress.averageScoreAchievedByStudentInLectureUnit.equals(expectedAverageScore)) {
                foundProgressWithCorrectNumbers = true;
                break;
            }
        }

        assertThat(foundProgressWithCorrectNumbers).isTrue();
    }

    private void cleanUpInitialParticipations() {
        participationService.deleteAllByExerciseId(idOfTextExercise, true, true);
        participationService.deleteAllByExerciseId(idOfModelingExercise, true, true);
        participationService.deleteAllByExerciseId(idOfTeamTextExercise, true, true);
        await().until(() -> participantScoreRepository.findAllByExercise(textExercise).isEmpty());
        await().until(() -> participantScoreRepository.findAllByExercise(modelingExercise).isEmpty());
        await().until(() -> participantScoreRepository.findAllByExercise(teamTextExercise).isEmpty());
        await().until(() -> participantScoreSchedulerService.isIdle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void getLearningGoalProgress_asStudentNotInCourse_shouldReturnProgressTenOutOfTwenty() throws Exception {
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/individual-progress", HttpStatus.FORBIDDEN, IndividualLearningGoalProgress.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLearningGoal_asInstructor_shouldUpdateLearningGoal() throws Exception {
        LearningGoal existingLearningGoal = learningGoalRepository.findByIdWithLectureUnitsAndCompletions(idOfLearningGoal).orElseThrow();
        LectureUnit textLectureUnit = lectureUnitRepository.findByIdWithLearningGoalsBidirectionalElseThrow(idOfTextUnitOfLectureOne);
        existingLearningGoal.setTitle("Updated");
        existingLearningGoal.removeLectureUnit(textLectureUnit);
        existingLearningGoal.setDescription("Updated Description");

        LearningGoal updatedLearningGoal = request.putWithResponseBody("/api/courses/" + idOfCourse + "/goals", existingLearningGoal, LearningGoal.class, HttpStatus.OK);

        assertThat(updatedLearningGoal.getTitle()).isEqualTo("Updated");
        assertThat(updatedLearningGoal.getDescription()).isEqualTo("Updated Description");
        assertThat(updatedLearningGoal.getLectureUnits().stream().map(DomainObject::getId).collect(Collectors.toSet())).doesNotContain(textLectureUnit.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createLearningGoal_asInstructor_shouldCreateLearningGoal() throws Exception {
        Course course = courseRepository.findWithEagerLearningGoalsById(idOfCourse).get();
        LearningGoal learningGoal = new LearningGoal();
        learningGoal.setTitle("FreshlyCreatedLearningGoal");
        learningGoal.setDescription("This is an example of a freshly created learning goal");
        learningGoal.setCourse(course);
        List<LectureUnit> allLectureUnits = lectureUnitRepository.findAll();
        Set<LectureUnit> connectedLectureUnits = new HashSet<>(allLectureUnits);
        learningGoal.setLectureUnits(connectedLectureUnits);

        var persistedLearningGoal = request.postWithResponseBody("/api/courses/" + idOfCourse + "/goals", learningGoal, LearningGoal.class, HttpStatus.CREATED);
        assertThat(persistedLearningGoal.getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void createLearningGoal_instructorNotInCourse_shouldReturnForbidden() throws Exception {
        LearningGoal learningGoal = new LearningGoal();
        learningGoal.setTitle("Example Title");
        request.postWithResponseBody("/api/courses/" + idOfCourse + "/goals", learningGoal, LearningGoal.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createLearningGoal_goalWithNameAlreadyExists_shouldReturnBadRequest() throws Exception {
        Course course = courseRepository.findWithEagerLearningGoalsById(idOfCourse).get();
        LearningGoal existingLearningGoal = learningGoalRepository.findById(idOfLearningGoal).get();
        LearningGoal learningGoal = new LearningGoal();
        learningGoal.setTitle(existingLearningGoal.getTitle());
        learningGoal.setDescription("This is an example of a freshly created learning goal");
        learningGoal.setCourse(course);
        List<LectureUnit> allLectureUnits = lectureUnitRepository.findAll();
        Set<LectureUnit> connectedLectureUnits = new HashSet<>(allLectureUnits);
        learningGoal.setLectureUnits(connectedLectureUnits);
        request.postWithResponseBody("/api/courses/" + idOfCourse + "/goals", learningGoal, LearningGoal.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void testInstructorGetsOnlyResultsFromOwningCourses() throws Exception {
        final var search = database.configureSearch("");
        final var result = request.get("/api/learning-goals/", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetsResultsFromOwningCoursesNotEmpty() throws Exception {
        LearningGoal learningGoal = learningGoalRepository.findById(idOfLearningGoal).get();
        final var search = database.configureSearch(learningGoal.getTitle());
        final var result = request.get("/api/learning-goals/", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(10);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getPrerequisites() throws Exception {
        LearningGoal learningGoal = learningGoalRepository.findById(idOfLearningGoal).get();
        List<LearningGoal> prerequisites = request.getList("/api/courses/" + idOfCourseTwo + "/prerequisites", HttpStatus.OK, LearningGoal.class);
        assertThat(prerequisites).containsExactly(learningGoal);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void addPrerequisite() throws Exception {
        Course courseTwo = courseRepository.findById(idOfCourseTwo).get();
        LearningGoal learningGoal = new LearningGoal();
        learningGoal.setTitle("LearningGoalTwo");
        learningGoal.setDescription("This is an example learning goal");
        learningGoal.setCourse(courseTwo);
        learningGoal = learningGoalRepository.save(learningGoal);

        LearningGoal prerequisite = request.postWithResponseBody("/api/courses/" + idOfCourse + "/prerequisites/" + learningGoal.getId(), learningGoal, LearningGoal.class,
                HttpStatus.OK);

        assertThat(prerequisite).isNotNull();
        Course course = courseRepository.findById(idOfCourse).get();
        assertThat(prerequisite.getConsecutiveCourses()).contains(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void addPrerequisite_unauthorized() throws Exception {
        request.postWithResponseBody("/api/courses/" + idOfCourse + "/prerequisites/99", null, LearningGoal.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void removePrerequisite() throws Exception {
        LearningGoal learningGoal = learningGoalRepository.findById(idOfLearningGoal).get();
        request.delete("/api/courses/" + idOfCourseTwo + "/prerequisites/" + idOfLearningGoal, HttpStatus.OK);

        Course course = courseRepository.findWithEagerLearningGoalsById(idOfCourseTwo).orElseThrow();
        assertThat(course.getPrerequisites()).doesNotContain(learningGoal);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void removePrerequisite_unauthorized() throws Exception {
        request.delete("/api/courses/" + idOfCourseTwo + "/prerequisites/" + idOfLearningGoal, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void addPrerequisite_doNotAllowCycle() throws Exception {
        // Test that a learning goal of a course can not be a prerequisite to the same course
        LearningGoal learningGoal = learningGoalRepository.findById(idOfLearningGoal).get();
        request.postWithResponseBody("/api/courses/" + idOfCourse + "/prerequisites/" + idOfLearningGoal, learningGoal, LearningGoal.class, HttpStatus.CONFLICT);
    }

    private void adjustStudentGroupsToCustomGroups(String suffix) {
        var testUsers = userRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX);
        // Ignore student 42 because not part of course
        var testStudents = testUsers.stream().filter(user -> user.getLogin().contains("student") && !user.getLogin().contains("42")).toList();
        testStudents.forEach(student -> student.setGroups(Set.of(TEST_PREFIX + "student" + suffix)));
        userRepository.saveAll(testStudents);
    }

}
