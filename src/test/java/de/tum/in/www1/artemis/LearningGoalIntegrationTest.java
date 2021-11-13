package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

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

public class LearningGoalIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private TextAssessmentKnowledgeService textAssessmentKnowledgeService;

    @Autowired
    private ModelAssessmentKnowledgeService modelAssessmentKnowledgeService;

    private Long idOfCourse;

    private Long idOfLearningGoal;

    private Long idOfLectureOne;

    private Long idOfTextUnitOfLectureOne;

    private Long idOfExerciseUnitTextOfLectureOne;

    private Long idOfExerciseUnitModelingOfLectureOne;

    private Long idOfLectureTwo;

    private Long idOfTextUnitOfLectureTwo;

    private Long idOfExerciseUnitTextOfLectureTwo;

    private Long idOfExerciseUnitModelingOfLectureTwo;

    private Long idOfTextExercise;

    private Long idOfModelingExercise;

    private Long idOfTeamTextExercise;

    private List<Team> teams;

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @BeforeEach
    public void setupTestScenario() {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        // creating the users student1-student5, tutor1-tutor10 and instructors1-instructor10
        this.database.addUsers(5, 10, 0, 10);

        // Add users that are not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor42"));

        // creating course
        Course course = this.database.createCourse();
        idOfCourse = course.getId();

        User student1 = userRepository.findOneByLogin("student1").get();

        createLectureOne(course);
        createLectureTwo(course);
        createTextExercise(pastTimestamp, pastTimestamp, pastTimestamp);
        createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 0.0, 50, true);
        createModelingExercise(pastTimestamp, pastTimestamp, pastTimestamp);
        createParticipationSubmissionAndResult(idOfModelingExercise, student1, 10.0, 0.0, 50, true);
        createTeamTextExercise(pastTimestamp, pastTimestamp, pastTimestamp);
        User tutor = userRepository.findOneByLogin("tutor1").get();
        Exercise teamTextExercise = exerciseRepository.findById(idOfTeamTextExercise).get();
        // will also create users
        teams = database.addTeamsForExerciseFixedTeamSize(teamTextExercise, 5, tutor, 3);

        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(0), 10.0, 0.0, 50, true);

        creatingLectureUnitsOfLectureOne();
        creatingLectureUnitsOfLectureTwo();
        createLearningGoal();
    }

    private void createLearningGoal() {
        Course course;
        course = courseRepository.findWithEagerLearningGoalsById(idOfCourse).get();
        LearningGoal learningGoal = new LearningGoal();
        learningGoal.setTitle("LearningGoalOne");
        learningGoal.setDescription("This is an example learning goal");
        learningGoal.setCourse(course);
        List<LectureUnit> allLectureUnits = lectureUnitRepository.findAll();
        Set<LectureUnit> connectedLectureUnits = new HashSet<>(allLectureUnits);
        learningGoal.setLectureUnits(connectedLectureUnits);
        learningGoal = learningGoalRepository.save(learningGoal);
        idOfLearningGoal = learningGoal.getId();
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
        idOfExerciseUnitTextOfLectureOne = textExerciseUnit.getId();

        Exercise modelingExercise = exerciseRepository.findById(idOfModelingExercise).get();
        ExerciseUnit modelingExerciseUnit = new ExerciseUnit();
        modelingExerciseUnit.setExercise(modelingExercise);
        modelingExerciseUnit = exerciseUnitRepository.save(modelingExerciseUnit);
        idOfExerciseUnitModelingOfLectureOne = modelingExerciseUnit.getId();

        Exercise textTeamExercise = exerciseRepository.findById(idOfTeamTextExercise).get();
        ExerciseUnit teamTextExerciseUnit = new ExerciseUnit();
        teamTextExerciseUnit.setExercise(textTeamExercise);
        teamTextExerciseUnit = exerciseUnitRepository.save(teamTextExerciseUnit);

        List<LectureUnit> lectureUnitsOfLectureOne = List.of(textUnit, textExerciseUnit, modelingExerciseUnit, teamTextExerciseUnit);
        Lecture lectureOne = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoals(idOfLectureOne).get();
        for (LectureUnit lectureUnit : lectureUnitsOfLectureOne) {
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
        idOfExerciseUnitTextOfLectureTwo = textExerciseUnit.getId();

        Exercise modelingExercise = exerciseRepository.findById(idOfModelingExercise).get();
        ExerciseUnit modelingExerciseUnit = new ExerciseUnit();
        modelingExerciseUnit.setExercise(modelingExercise);
        modelingExerciseUnit = exerciseUnitRepository.save(modelingExerciseUnit);
        idOfExerciseUnitModelingOfLectureTwo = modelingExerciseUnit.getId();

        List<LectureUnit> lectureUnitsOfLectureTwo = List.of(textUnit, textExerciseUnit, modelingExerciseUnit);
        Lecture lectureTwo = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoals(idOfLectureTwo).get();
        for (LectureUnit lectureUnit : lectureUnitsOfLectureTwo) {
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

    private void createTextExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise.setKnowledge(textAssessmentKnowledgeService.createNewKnowledge());
        textExercise = exerciseRepository.save(textExercise);
        idOfTextExercise = textExercise.getId();
    }

    private void createModelingExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        modelingExercise.setMaxPoints(10.0);
        modelingExercise.setBonusPoints(0.0);
        modelingExercise.setKnowledge(modelAssessmentKnowledgeService.createNewKnowledge());
        modelingExercise = exerciseRepository.save(modelingExercise);
        idOfModelingExercise = modelingExercise.getId();
    }

    private void createTeamTextExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
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
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testAll_asEditor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLearningGoal_asInstructor_shouldReturnLearningGoal() throws Exception {
        LearningGoal learningGoal = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.OK, LearningGoal.class);
        assertThat(learningGoal.getId()).isEqualTo(idOfLearningGoal);
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    public void getLearningGoal_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.FORBIDDEN, LearningGoal.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLearningGoalsOfCourse_asStudent1_shouldReturnLearningGoals() throws Exception {
        List<LearningGoal> learningGoalsOfCourse = request.getList("/api/courses/" + idOfCourse + "/goals", HttpStatus.OK, LearningGoal.class);
        assertThat(learningGoalsOfCourse.size()).isEqualTo(1);
        assertThat(learningGoalsOfCourse.get(0).getId()).isEqualTo(idOfLearningGoal);
    }

    @Test
    @WithMockUser(username = "student42", roles = "USER")
    public void getLearningGoalsOfCourse_asStudentNotInCourse_shouldReturnForbidden() throws Exception {
        request.getList("/api/courses/" + idOfCourse + "/goals", HttpStatus.FORBIDDEN, LearningGoal.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLearningGoal_asInstructor_shouldDeleteLearningGoal() throws Exception {
        request.delete("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.OK);
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.NOT_FOUND, LearningGoal.class);
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    public void deleteLearningGoal_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.delete("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteCourse_asAdmin_shouldAlsoDeleteLearningGoal() throws Exception {
        request.delete("/api/courses/" + idOfCourse, HttpStatus.OK);
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.NOT_FOUND, LearningGoal.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLecture_asInstructor_shouldUpdateLearningGoal() throws Exception {
        request.delete("/api/lectures/" + idOfLectureTwo, HttpStatus.OK);
        LearningGoal learningGoal = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.OK, LearningGoal.class);
        assertThat(learningGoal.getLectureUnits().stream().map(DomainObject::getId))
                .containsAll(Set.of(idOfTextUnitOfLectureOne, idOfExerciseUnitTextOfLectureOne, idOfExerciseUnitModelingOfLectureOne));
        assertThat(learningGoal.getLectureUnits().stream().map(DomainObject::getId))
                .doesNotContainAnyElementsOf(Set.of(idOfTextUnitOfLectureTwo, idOfExerciseUnitTextOfLectureTwo, idOfExerciseUnitModelingOfLectureTwo));

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLectureUnit_asInstructor_shouldUpdateLearningGoal() throws Exception {
        request.delete("/api/lectures/" + idOfLectureTwo + "/lecture-units/" + idOfTextUnitOfLectureTwo, HttpStatus.OK);
        LearningGoal learningGoal = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal, HttpStatus.OK, LearningGoal.class);
        assertThat(learningGoal.getLectureUnits().stream().map(DomainObject::getId)).containsAll(Set.of(idOfTextUnitOfLectureOne, idOfExerciseUnitTextOfLectureOne,
                idOfExerciseUnitModelingOfLectureOne, idOfExerciseUnitTextOfLectureTwo, idOfExerciseUnitModelingOfLectureTwo));
        assertThat(learningGoal.getLectureUnits().stream().map(DomainObject::getId)).doesNotContainAnyElementsOf(Set.of(idOfTextUnitOfLectureTwo));

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLearningGoalProgress_asStudent1_shouldReturnProgressTenOutOfTwenty() throws Exception {
        IndividualLearningGoalProgress individualLearningGoalProgress = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/individual-progress",
                HttpStatus.OK, IndividualLearningGoalProgress.class);
        assertThat(individualLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal).isEqualTo(30.0);
        assertThat(individualLearningGoalProgress.pointsAchievedByStudentInLearningGoal).isEqualTo(10.0);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLearningGoalProgress_asStudent1_usingParticipantScores_shouldReturnProgressTenOutOfTwenty() throws Exception {
        IndividualLearningGoalProgress individualLearningGoalProgress = request.get(
                "/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/individual-progress?useParticipantScoreTable=true", HttpStatus.OK,
                IndividualLearningGoalProgress.class);
        assertThat(individualLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal).isEqualTo(30.0);
        assertThat(individualLearningGoalProgress.pointsAchievedByStudentInLearningGoal).isEqualTo(10.0);
    }

    @Test
    @WithMockUser(username = "team1student1", roles = "USER")
    public void getLearningGoalProgress_asTeam1Student1_shouldReturnProgressTenOutOfThirty() throws Exception {
        IndividualLearningGoalProgress individualLearningGoalProgress = request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/individual-progress",
                HttpStatus.OK, IndividualLearningGoalProgress.class);
        assertThat(individualLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal).isEqualTo(30.0);
        assertThat(individualLearningGoalProgress.pointsAchievedByStudentInLearningGoal).isEqualTo(5.0);
    }

    @Test
    @WithMockUser(username = "team1student1", roles = "USER")
    public void getLearningGoalProgress_asTeam1Student1_usingParticipantScores_shouldReturnProgressTenOutOfThirty() throws Exception {
        IndividualLearningGoalProgress individualLearningGoalProgress = request.get(
                "/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/individual-progress?useParticipantScoreTable=true", HttpStatus.OK,
                IndividualLearningGoalProgress.class);
        assertThat(individualLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal).isEqualTo(30.0);
        assertThat(individualLearningGoalProgress.pointsAchievedByStudentInLearningGoal).isEqualTo(5.0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLearningGoalCourseProgressTeamsTest_asInstructorOne() throws Exception {
        cleanUpInitialParticipations();

        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(0), 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from team
        createParticipationSubmissionAndResult(idOfTeamTextExercise, teams.get(0), 10.0, 0.0, 50, false);

        // will be ignored in favor of last submission from team
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLearningGoalCourseProgressIndividualTest_asInstructorOne() throws Exception {
        cleanUpInitialParticipations();
        User student1 = userRepository.findOneByLogin("student1").get();
        User student2 = userRepository.findOneByLogin("student2").get();
        User student3 = userRepository.findOneByLogin("student3").get();
        User student4 = userRepository.findOneByLogin("student4").get();
        User instructor1 = userRepository.findOneByLogin("instructor1").get();

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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLearningGoalCourseProgressIndividualTest_asInstructorOne_usingParticipantScoreTable() throws Exception {
        cleanUpInitialParticipations();
        User student1 = userRepository.findOneByLogin("student1").get();
        User student2 = userRepository.findOneByLogin("student2").get();
        User student3 = userRepository.findOneByLogin("student3").get();
        User student4 = userRepository.findOneByLogin("student4").get();
        User instructor1 = userRepository.findOneByLogin("instructor1").get();

        createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from team
        createParticipationSubmissionAndResult(idOfTextExercise, student1, 10.0, 0.0, 50, false);

        createParticipationSubmissionAndResult(idOfTextExercise, student2, 10.0, 0.0, 100, true);  // will be ignored in favor of last submission from student
        createParticipationSubmissionAndResult(idOfTextExercise, student2, 10.0, 0.0, 10, false);

        createParticipationSubmissionAndResult(idOfTextExercise, student3, 10.0, 0.0, 10, true);
        createParticipationSubmissionAndResult(idOfTextExercise, student4, 10.0, 0.0, 50, true);

        createParticipationSubmissionAndResult(idOfTextExercise, instructor1, 10.0, 0.0, 100, true); // will be ignored as not a student

        CourseLearningGoalProgress courseLearningGoalProgress = request.get(
                "/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/course-progress?useParticipantScoreTable=true", HttpStatus.OK, CourseLearningGoalProgress.class);

        assertThat(courseLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal).isEqualTo(30.0);
        assertThat(courseLearningGoalProgress.averagePointsAchievedByStudentInLearningGoal).isEqualTo(3.0);

        assertThatSpecificCourseLectureUnitProgressExists(courseLearningGoalProgress, 20.0, 4, 30.0);
    }

    public void assertThatSpecificCourseLectureUnitProgressExists(CourseLearningGoalProgress courseLearningGoalProgress, double expectedParticipationRate,
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
    }

    @Test
    @WithMockUser(username = "student42", roles = "USER")
    public void getLearningGoalProgress_asStudentNotInCourse_shouldReturnProgressTenOutOfTwenty() throws Exception {
        request.get("/api/courses/" + idOfCourse + "/goals/" + idOfLearningGoal + "/individual-progress", HttpStatus.FORBIDDEN, IndividualLearningGoalProgress.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateLearningGoal_asInstructor_shouldUpdateLearningGoal() throws Exception {
        LearningGoal existingLearningGoal = learningGoalRepository.findByIdWithLectureUnitsBidirectional(idOfLearningGoal).get();
        LectureUnit textLectureUnit = lectureUnitRepository.findByIdWithLearningGoalsBidirectional(idOfTextUnitOfLectureOne).get();
        existingLearningGoal.setTitle("Updated");
        existingLearningGoal.removeLectureUnit(textLectureUnit);
        existingLearningGoal.setDescription("Updated Description");

        LearningGoal updatedLearningGoal = request.putWithResponseBody("/api/courses/" + idOfCourse + "/goals", existingLearningGoal, LearningGoal.class, HttpStatus.OK);

        assertThat(updatedLearningGoal.getTitle()).isEqualTo("Updated");
        assertThat(updatedLearningGoal.getDescription()).isEqualTo("Updated Description");
        assertThat(updatedLearningGoal.getLectureUnits().stream().map(DomainObject::getId).collect(Collectors.toSet()).contains(textLectureUnit.getId())).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createLearningGoal_asInstructor_shouldCreateLearningGoal() throws Exception {
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
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    public void createLearningGoal_instructorNotInCourse_shouldReturnForbidden() throws Exception {
        LearningGoal learningGoal = new LearningGoal();
        learningGoal.setTitle("Example Title");
        request.postWithResponseBody("/api/courses/" + idOfCourse + "/goals", learningGoal, LearningGoal.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createLearningGoal_goalWithNameAlreadyExists_shouldReturnBadRequest() throws Exception {
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

}
