package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
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
import de.tum.in.www1.artemis.service.LectureUnitService;
import de.tum.in.www1.artemis.service.ModelAssessmentKnowledgeService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.TextAssessmentKnowledgeService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.CourseCompetencyProgressDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

class CompetencyIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "competencyintegrationtest";

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
    private CompetencyRepository competencyRepository;

    @Autowired
    private CompetencyRelationRepository competencyRelationRepository;

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

    @Autowired
    private LectureUnitService lectureUnitService;

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
        participantScoreScheduleService.activate();
        // We can not remove the teams due to the existing participations etc., but we can remove all students from them so that they are no longer accessed
        // The students are reused between tests
        // TODO: Check if this can also be solved by changing ExerciseRepo#calculateStatisticsForTeamCourseExercises to check for the exerciseId
        var existingTeams = teamRepository.findAll().stream().filter(t -> t.getShortName().contains(TEST_PREFIX)).toList();
        existingTeams.forEach(t -> t.setStudents(Set.of()));
        teamRepository.saveAll(existingTeams);

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        database.addUsers(TEST_PREFIX, 4, 1, 0, 1);

        // Add users that are not in the course
        database.createAndSaveUser(TEST_PREFIX + "student42");
        database.createAndSaveUser(TEST_PREFIX + "tutor42");
        database.createAndSaveUser(TEST_PREFIX + "instructor42");

        // creating course
        Course course = this.database.createCourse();
        idOfCourse = course.getId();

        Course courseTwo = this.database.createCourse();
        idOfCourseTwo = courseTwo.getId();

        User student1 = database.getUserByLogin(TEST_PREFIX + "student1");

        createLearningGoal();
        createPrerequisite();
        createLectureOne(course);
        createLectureTwo(course);
        var learningGoal = competencyRepository.findByIdElseThrow(idOfLearningGoal);
        textExercise = createTextExercise(pastTimestamp, pastTimestamp, pastTimestamp, Set.of(learningGoal));
        createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 0.0, 50, true);
        modelingExercise = createModelingExercise(pastTimestamp, pastTimestamp, pastTimestamp, Set.of(learningGoal));
        createParticipationSubmissionAndResult(idOfModelingExercise, student1, 10.0, 0.0, 50, true);
        teamTextExercise = createTeamTextExercise(pastTimestamp, pastTimestamp, pastTimestamp, Set.of(learningGoal));
        User tutor = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").get();
        // will also create users
        teams = database.addTeamsForExerciseFixedTeamSize(TEST_PREFIX, "lgi", teamTextExercise, 5, tutor, 3);

        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(0), 10.0, 0.0, 50, true);

        creatingLectureUnitsOfLectureOne();
        creatingLectureUnitsOfLectureTwo();

        await().atMost(Duration.ofSeconds(30)).until(() -> participantScoreRepository.findAllByExercise(textExercise).size() == 1);
        await().atMost(Duration.ofSeconds(30)).until(() -> participantScoreRepository.findAllByExercise(teamTextExercise).size() == 1);
        await().atMost(Duration.ofSeconds(30)).until(() -> participantScoreRepository.findAllByExercise(modelingExercise).size() == 1);
    }

    private void createLearningGoal() {
        Course course = courseRepository.findWithEagerCompetenciesById(idOfCourse).get();
        Competency competency = new Competency();
        competency.setTitle("LearningGoal" + new Random().nextInt());
        competency.setDescription("This is an example learning goal");
        competency.setTaxonomy(CompetencyTaxonomy.UNDERSTAND);
        competency.setCourse(course);
        competency = competencyRepository.save(competency);
        idOfLearningGoal = competency.getId();

        Competency otherCompetency = new Competency();
        otherCompetency.setTitle("Detailed sub learning goal");
        otherCompetency.setDescription("A communi observantia non est recedendum.");
        otherCompetency.setCourse(course);
        competencyRepository.save(otherCompetency);
    }

    private void createPrerequisite() {
        // Add the first competency as a prerequisite to the other course
        Competency competency = competencyRepository.findByIdWithConsecutiveCourses(idOfLearningGoal).get();
        Course course2 = courseRepository.findWithEagerCompetenciesById(idOfCourseTwo).get();
        course2.addPrerequisite(competency);
        courseRepository.save(course2);
    }

    private void creatingLectureUnitsOfLectureOne() {
        // creating lecture units for lecture one
        var learningGoal = competencyRepository.findById(idOfLearningGoal).get();

        TextUnit textUnit = new TextUnit();
        textUnit.setName("TextUnitOfLectureOne");
        textUnit.setLearningGoals(Set.of(learningGoal));
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

    private TextExercise createTextExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp, Set<Competency> competencies) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise.setKnowledge(textAssessmentKnowledgeService.createNewKnowledge());
        textExercise.setLearningGoals(competencies);
        textExercise = exerciseRepository.save(textExercise);
        idOfTextExercise = textExercise.getId();

        return textExercise;
    }

    private ModelingExercise createModelingExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp, Set<Competency> competencies) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        modelingExercise.setMaxPoints(10.0);
        modelingExercise.setBonusPoints(0.0);
        modelingExercise.setKnowledge(modelAssessmentKnowledgeService.createNewKnowledge());
        modelingExercise.setLearningGoals(competencies);
        modelingExercise = exerciseRepository.save(modelingExercise);
        idOfModelingExercise = modelingExercise.getId();

        return modelingExercise;
    }

    private TextExercise createTeamTextExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp, Set<Competency> competencies) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setMode(ExerciseMode.TEAM);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise.setKnowledge(textAssessmentKnowledgeService.createNewKnowledge());
        textExercise.setLearningGoals(competencies);
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
        request.put("/api/courses/" + idOfCourse + "/competencies", new Competency(), HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + idOfCourse + "/competencies", new Competency(), HttpStatus.FORBIDDEN);
        request.get("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal + "/course-progress", HttpStatus.FORBIDDEN, CourseCompetencyProgressDTO.class);
        request.delete("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal, HttpStatus.FORBIDDEN);
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
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getTitle_ForLearningGoal() throws Exception {
        var learningGoal = competencyRepository.findById(idOfLearningGoal).get();
        String title = request.get("/api/competencies/" + idOfLearningGoal + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(learningGoal.getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getTitle_ForNonExistingLearningGoal() throws Exception {
        request.get("/api/competencies/12312321321/title", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLearningGoal_asStudent_shouldReturnLearningGoal() throws Exception {
        Competency competency = request.get("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal, HttpStatus.OK, Competency.class);
        assertThat(competency.getId()).isEqualTo(idOfLearningGoal);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void getLearningGoal_asUserNotInCourse_shouldReturnForbidden() throws Exception {
        request.get("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal, HttpStatus.FORBIDDEN, Competency.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLearningGoal_asStudent_wrongCourse() throws Exception {
        request.get("/api/courses/" + idOfCourseTwo + "/competencies/" + idOfLearningGoal, HttpStatus.CONFLICT, Competency.class);
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
        Competency newCompetency = new Competency();
        newCompetency.setTitle("Title");
        newCompetency.setDescription("Description");
        newCompetency.setCourse(course);
        newCompetency.setLectureUnits(new HashSet<>(List.of(unreleasedLectureUnit)));
        competencyRepository.save(newCompetency);

        List<Competency> learningGoalsOfCourse = request.getList("/api/courses/" + idOfCourse + "/competencies", HttpStatus.OK, Competency.class);

        assertThat(learningGoalsOfCourse.stream().filter(l -> l.getId().equals(idOfLearningGoal)).findFirst()).isPresent();
        assertThat(learningGoalsOfCourse.stream().filter(l -> l.getId().equals(newCompetency.getId())).findFirst().get().getLectureUnits()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void getLearningGoalsOfCourse_asStudentNotInCourse_shouldReturnForbidden() throws Exception {
        request.getList("/api/courses/" + idOfCourse + "/competencies", HttpStatus.FORBIDDEN, Competency.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLearningGoal_asInstructor_shouldDeleteLearningGoal() throws Exception {
        request.delete("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal, HttpStatus.OK);
        request.get("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal, HttpStatus.NOT_FOUND, Competency.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLearningGoal_witRelatedGoals_shouldReturnBadRequest() throws Exception {
        Competency competency = competencyRepository.findByIdElseThrow(idOfLearningGoal);
        Course course = courseRepository.findByIdElseThrow(idOfCourse);
        Competency competency1 = database.createLearningGoal(course);

        var relation = new CompetencyRelation();
        relation.setTailCompetency(competency);
        relation.setHeadCompetency(competency1);
        relation.setType(CompetencyRelation.RelationType.EXTENDS);
        competencyRelationRepository.save(relation);

        // Should return bad request, as the competency still has relations
        request.delete("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void deleteLearningGoal_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.delete("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteCourse_asAdmin_shouldAlsoDeleteLearningGoal() throws Exception {
        request.delete("/api/admin/courses/" + idOfCourse, HttpStatus.OK);
        request.get("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal, HttpStatus.NOT_FOUND, Competency.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createLearningGoalRelation() throws Exception {
        Course course = courseRepository.findByIdElseThrow(idOfCourse);
        Long idOfOtherLearningGoal = database.createLearningGoal(course).getId();

        request.postWithoutResponseBody("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal + "/relations/" + idOfOtherLearningGoal + "?type="
                + CompetencyRelation.RelationType.EXTENDS.name(), HttpStatus.OK, new LinkedMultiValueMap<>());

        var relations = competencyRelationRepository.findAllByCompetencyId(idOfLearningGoal);
        assertThat(relations).hasSize(1);
        assertThat(relations.stream().findFirst().get().getType()).isEqualTo(CompetencyRelation.RelationType.EXTENDS);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createLearningGoalRelation_shouldReturnBadRequest() throws Exception {
        Course course = courseRepository.findByIdElseThrow(idOfCourse);
        Long idOfOtherLearningGoal = database.createLearningGoal(course).getId();

        request.post("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal + "/relations/" + idOfOtherLearningGoal + "?type=" + "abc123xyz", null,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createLearningGoalRelation_shouldReturnBadRequest_ForCircularRelations() throws Exception {
        Competency competency = competencyRepository.findByIdElseThrow(idOfLearningGoal);
        Course course = courseRepository.findByIdElseThrow(idOfCourse);
        Long idOfOtherLearningGoal1 = database.createLearningGoal(course).getId();
        Competency otherCompetency1 = competencyRepository.findByIdElseThrow(idOfOtherLearningGoal1);
        Long idOfOtherLearningGoal2 = database.createLearningGoal(course).getId();
        Competency otherCompetency2 = competencyRepository.findByIdElseThrow(idOfOtherLearningGoal1);

        var relation1 = new CompetencyRelation();
        relation1.setTailCompetency(competency);
        relation1.setHeadCompetency(otherCompetency1);
        relation1.setType(CompetencyRelation.RelationType.EXTENDS);
        competencyRelationRepository.save(relation1);

        var relation2 = new CompetencyRelation();
        relation2.setTailCompetency(otherCompetency1);
        relation2.setHeadCompetency(otherCompetency2);
        relation2.setType(CompetencyRelation.RelationType.MATCHES);
        competencyRelationRepository.save(relation2);

        request.post("/api/courses/" + idOfCourse + "/competencies/" + idOfOtherLearningGoal2 + "/relations/" + idOfLearningGoal + "?type="
                + CompetencyRelation.RelationType.ASSUMES.name(), null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void createLearningGoalRelation_shouldReturnForbidden() throws Exception {
        Course course = courseRepository.findByIdElseThrow(idOfCourse);
        Long idOfOtherLearningGoal = database.createLearningGoal(course).getId();

        request.post("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal + "/relations/" + idOfOtherLearningGoal + "?type="
                + CompetencyRelation.RelationType.EXTENDS.name(), null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLearningGoalRelations() throws Exception {
        Competency competency = competencyRepository.findByIdElseThrow(idOfLearningGoal);
        Course course = courseRepository.findByIdElseThrow(idOfCourse);
        Competency otherCompetency = database.createLearningGoal(course);

        var relation = new CompetencyRelation();
        relation.setTailCompetency(competency);
        relation.setHeadCompetency(otherCompetency);
        relation.setType(CompetencyRelation.RelationType.EXTENDS);
        relation = competencyRelationRepository.save(relation);

        var relations = request.getList("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal + "/relations", HttpStatus.OK, CompetencyRelation.class);

        assertThat(relations).hasSize(1);
        assertThat(relations.get(0)).isEqualTo(relation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLearningGoalRelation() throws Exception {
        Competency competency = competencyRepository.findByIdElseThrow(idOfLearningGoal);
        Course course = courseRepository.findByIdElseThrow(idOfCourse);
        Competency otherCompetency = database.createLearningGoal(course);

        var relation = new CompetencyRelation();
        relation.setTailCompetency(competency);
        relation.setHeadCompetency(otherCompetency);
        relation.setType(CompetencyRelation.RelationType.EXTENDS);
        relation = competencyRelationRepository.save(relation);

        request.delete("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal + "/relations/" + relation.getId(), HttpStatus.OK);

        var relations = competencyRelationRepository.findAllByCompetencyId(idOfLearningGoal);
        assertThat(relations).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLearningGoalRelation_shouldReturnBadRequest() throws Exception {
        Competency competency = competencyRepository.findByIdElseThrow(idOfLearningGoal);
        Course course = courseRepository.findByIdElseThrow(idOfCourse);
        Competency otherCompetency = database.createLearningGoal(course);

        var relation = new CompetencyRelation();
        relation.setTailCompetency(otherCompetency); // invalid
        relation.setHeadCompetency(competency);
        relation.setType(CompetencyRelation.RelationType.EXTENDS);
        relation = competencyRelationRepository.save(relation);

        request.delete("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal + "/relations/" + relation.getId(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLecture_asInstructor_shouldUpdateLearningGoal() throws Exception {
        request.delete("/api/lectures/" + idOfLectureTwo, HttpStatus.OK);
        Competency competency = request.get("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal, HttpStatus.OK, Competency.class);
        assertThat(competency.getLectureUnits().stream().map(DomainObject::getId)).containsAll(Set.of(idOfTextUnitOfLectureOne));
        assertThat(competency.getLectureUnits().stream().map(DomainObject::getId)).doesNotContainAnyElementsOf(Set.of(idOfTextUnitOfLectureTwo));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnit_asInstructor_shouldUpdateLearningGoal() throws Exception {
        request.delete("/api/lectures/" + idOfLectureTwo + "/lecture-units/" + idOfTextUnitOfLectureTwo, HttpStatus.OK);
        Competency competency = request.get("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal, HttpStatus.OK, Competency.class);
        assertThat(competency.getLectureUnits().stream().map(DomainObject::getId)).containsAll(Set.of(idOfTextUnitOfLectureOne));
        assertThat(competency.getLectureUnits().stream().map(DomainObject::getId)).doesNotContainAnyElementsOf(Set.of(idOfTextUnitOfLectureTwo));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLearningGoalCourseProgressTeamsTest_asInstructorOne() throws Exception {
        cleanUpInitialParticipations();

        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(0), 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from team
        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(0), 10.0, 0.0, 50, false);

        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(1), 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from team
        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(1), 10.0, 0.0, 10, false);

        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(2), 10.0, 0.0, 10, true);
        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(3), 10.0, 0.0, 50, true);

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(15)).until(() -> participantScoreScheduleService.isIdle());

        CourseCompetencyProgressDTO courseLearningGoalProgress = request.get("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal + "/course-progress", HttpStatus.OK,
                CourseCompetencyProgressDTO.class);

        assertThat(courseLearningGoalProgress.averageStudentScore()).isEqualTo(31.5);
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

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(15)).until(() -> participantScoreScheduleService.isIdle());

        CourseCompetencyProgressDTO courseLearningGoalProgress = request.get("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal + "/course-progress", HttpStatus.OK,
                CourseCompetencyProgressDTO.class);

        assertThat(courseLearningGoalProgress.averageStudentScore()).isEqualTo(46.3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLearningGoalStudentProgressTest() throws Exception {
        var course = courseRepository.findById(idOfCourse).get();
        course.setStudentGroupName(TEST_PREFIX + "student" + "studentTest");
        courseRepository.save(course);
        adjustStudentGroupsToCustomGroups("studentTest");

        cleanUpInitialParticipations();
        User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();

        lectureUnitService.setLectureUnitCompletion(textUnitRepository.findById(idOfTextUnitOfLectureOne).get(), student1, true);

        createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 0.0, 90, true);  // will be ignored in favor of last submission from team
        createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 0.0, 85, false);

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(15)).until(() -> participantScoreScheduleService.isIdle());

        CompetencyProgress studentCompetencyProgress1 = request.get("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal + "/student-progress?refresh=true",
                HttpStatus.OK, CompetencyProgress.class);

        assertThat(studentCompetencyProgress1.getProgress()).isEqualTo(50.0);
        assertThat(studentCompetencyProgress1.getConfidence()).isEqualTo(85.0);

        CompetencyProgress studentCompetencyProgress2 = request.get("/api/courses/" + idOfCourse + "/competencies/" + idOfLearningGoal + "/student-progress?refresh=false",
                HttpStatus.OK, CompetencyProgress.class);

        assertThat(studentCompetencyProgress2.getProgress()).isEqualTo(50.0);
        assertThat(studentCompetencyProgress2.getConfidence()).isEqualTo(85.0);
    }

    private void cleanUpInitialParticipations() {
        participationService.deleteAllByExerciseId(idOfTextExercise, true, true);
        participationService.deleteAllByExerciseId(idOfModelingExercise, true, true);
        participationService.deleteAllByExerciseId(idOfTeamTextExercise, true, true);
        await().until(() -> participantScoreScheduleService.isIdle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLearningGoal_asInstructor_shouldUpdateLearningGoal() throws Exception {
        Competency existingCompetency = competencyRepository.findByIdWithLectureUnitsAndCompletionsElseThrow(idOfLearningGoal);
        LectureUnit textLectureUnit = lectureUnitRepository.findByIdWithCompetenciesBidirectionalElseThrow(idOfTextUnitOfLectureOne);
        existingCompetency.setTitle("Updated");
        existingCompetency.removeLectureUnit(textLectureUnit);
        existingCompetency.setDescription("Updated Description");

        Competency updatedCompetency = request.putWithResponseBody("/api/courses/" + idOfCourse + "/competencies", existingCompetency, Competency.class, HttpStatus.OK);

        assertThat(updatedCompetency.getTitle()).isEqualTo("Updated");
        assertThat(updatedCompetency.getDescription()).isEqualTo("Updated Description");
        assertThat(updatedCompetency.getLectureUnits().stream().map(DomainObject::getId).collect(Collectors.toSet())).doesNotContain(textLectureUnit.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLearningGoal_asInstructor_badRequest() throws Exception {
        Competency existingCompetency = competencyRepository.findByIdElseThrow(idOfLearningGoal);
        existingCompetency.setId(null);
        request.putWithResponseBody("/api/courses/" + idOfCourse + "/competencies", existingCompetency, Competency.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createLearningGoal_asInstructor_shouldCreateLearningGoal() throws Exception {
        Course course = courseRepository.findWithEagerCompetenciesById(idOfCourse).get();
        Competency competency = new Competency();
        competency.setTitle("FreshlyCreatedLearningGoal");
        competency.setDescription("This is an example of a freshly created learning goal");
        competency.setCourse(course);
        List<LectureUnit> allLectureUnits = lectureUnitRepository.findAll();
        Set<LectureUnit> connectedLectureUnits = new HashSet<>(allLectureUnits);
        competency.setLectureUnits(connectedLectureUnits);

        var persistedLearningGoal = request.postWithResponseBody("/api/courses/" + idOfCourse + "/competencies", competency, Competency.class, HttpStatus.CREATED);
        assertThat(persistedLearningGoal.getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createLearningGoal_asInstructor_badRequest() throws Exception {
        Competency competency = new Competency(); // no title
        request.postWithResponseBody("/api/courses/" + idOfCourse + "/competencies", competency, Competency.class, HttpStatus.BAD_REQUEST);
        competency.setTitle(" "); // empty title
        request.postWithResponseBody("/api/courses/" + idOfCourse + "/competencies", competency, Competency.class, HttpStatus.BAD_REQUEST);
        competency.setTitle("Hello");
        competency.setId(5L); // id is set
        request.postWithResponseBody("/api/courses/" + idOfCourse + "/competencies", competency, Competency.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void createLearningGoal_instructorNotInCourse_shouldReturnForbidden() throws Exception {
        Competency competency = new Competency();
        competency.setTitle("Example Title");
        request.postWithResponseBody("/api/courses/" + idOfCourse + "/competencies", competency, Competency.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importLearningGoal_asInstructor_shouldImportLearningGoal() throws Exception {
        var existingLearningGoal = competencyRepository.findByIdElseThrow(idOfLearningGoal);
        var importedLearningGoal = request.postWithResponseBody("/api/courses/" + idOfCourseTwo + "/competencies/import", existingLearningGoal, Competency.class,
                HttpStatus.CREATED);

        assertThat(competencyRepository.findById(importedLearningGoal.getId())).isNotEmpty();
        assertThat(importedLearningGoal.getTitle()).isEqualTo(existingLearningGoal.getTitle());
        assertThat(importedLearningGoal.getDescription()).isEqualTo(existingLearningGoal.getDescription());
        assertThat(importedLearningGoal.getMasteryThreshold()).isEqualTo(existingLearningGoal.getMasteryThreshold());
        assertThat(importedLearningGoal.getTaxonomy()).isEqualTo(existingLearningGoal.getTaxonomy());
        assertThat(importedLearningGoal.getExercises()).isEmpty();
        assertThat(importedLearningGoal.getLectureUnits()).isEmpty();
        assertThat(importedLearningGoal.getConsecutiveCourses()).isEmpty();
        assertThat(importedLearningGoal.getUserProgress().isEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void importLearningGoal_instructorNotInCourse_shouldReturnForbidden() throws Exception {
        Competency competency = new Competency();
        competency.setTitle("Example Title");
        request.postWithResponseBody("/api/courses/" + idOfCourseTwo + "/competencies/import", competency, Competency.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void testInstructorGetsOnlyResultsFromOwningCourses() throws Exception {
        final var search = database.configureSearch("");
        final var result = request.get("/api/competencies/", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetsResultsFromOwningCoursesNotEmpty() throws Exception {
        Competency competency = competencyRepository.findById(idOfLearningGoal).get();
        final var search = database.configureSearch(competency.getTitle());
        final var result = request.get("/api/competencies/", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminGetsResultsFromAllCourses() throws Exception {
        Competency competency = competencyRepository.findById(idOfLearningGoal).get();
        final var search = database.configureSearch(competency.getTitle());
        final var result = request.get("/api/competencies/", HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getPrerequisites() throws Exception {
        Competency competency = competencyRepository.findById(idOfLearningGoal).get();
        List<Competency> prerequisites = request.getList("/api/courses/" + idOfCourseTwo + "/prerequisites", HttpStatus.OK, Competency.class);
        assertThat(prerequisites).containsExactly(competency);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void addPrerequisite() throws Exception {
        Course courseTwo = courseRepository.findById(idOfCourseTwo).get();
        Competency competency = new Competency();
        competency.setTitle("LearningGoalTwo");
        competency.setDescription("This is an example learning goal");
        competency.setCourse(courseTwo);
        competency = competencyRepository.save(competency);

        Competency prerequisite = request.postWithResponseBody("/api/courses/" + idOfCourse + "/prerequisites/" + competency.getId(), competency, Competency.class, HttpStatus.OK);

        assertThat(prerequisite).isNotNull();
        Course course = courseRepository.findById(idOfCourse).get();
        assertThat(prerequisite.getConsecutiveCourses()).contains(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void addPrerequisite_unauthorized() throws Exception {
        request.postWithResponseBody("/api/courses/" + idOfCourse + "/prerequisites/99", null, Competency.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void removePrerequisite() throws Exception {
        Competency competency = competencyRepository.findById(idOfLearningGoal).get();
        request.delete("/api/courses/" + idOfCourseTwo + "/prerequisites/" + idOfLearningGoal, HttpStatus.OK);

        Course course = courseRepository.findWithEagerCompetenciesById(idOfCourseTwo).orElseThrow();
        assertThat(course.getPrerequisites()).doesNotContain(competency);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void removePrerequisite_conflict() throws Exception {
        request.delete("/api/courses/" + idOfCourse + "/prerequisites/" + idOfLearningGoal, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void removePrerequisite_unauthorized() throws Exception {
        request.delete("/api/courses/" + idOfCourseTwo + "/prerequisites/" + idOfLearningGoal, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void addPrerequisite_doNotAllowCycle() throws Exception {
        // Test that a competency of a course can not be a prerequisite to the same course
        Competency competency = competencyRepository.findById(idOfLearningGoal).get();
        request.postWithResponseBody("/api/courses/" + idOfCourse + "/prerequisites/" + idOfLearningGoal, competency, Competency.class, HttpStatus.CONFLICT);
    }

    private void adjustStudentGroupsToCustomGroups(String suffix) {
        var testUsers = userRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX);
        // Ignore student 42 because not part of course
        var testStudents = testUsers.stream().filter(user -> user.getLogin().contains("student") && !user.getLogin().contains("42")).toList();
        testStudents.forEach(student -> student.setGroups(Set.of(TEST_PREFIX + "student" + suffix)));
        userRepository.saveAll(testStudents);
    }

}
