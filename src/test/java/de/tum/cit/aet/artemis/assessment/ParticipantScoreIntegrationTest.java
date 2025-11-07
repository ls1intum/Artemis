package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.score.ScoreDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.assessment.util.GradingScaleFactory;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeInformationDTO;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ParticipantScoreIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "participantscoreintegrationtest";

    private Long idOfExam;

    private Long courseId;

    private Long idOfIndividualTextExercise;

    private Long idOfTeamTextExercise;

    private Long idOfExerciseUnit;

    private TextExercise textExercise;

    private ProgrammingExercise programmingExercise;

    private Exam exam;

    private User student1;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private Course course;

    private ModelingExercise modelingExercise;

    private StudentParticipation studentTextParticipation;

    private StudentParticipation teamTextParticipation;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @BeforeEach
    void setupTestScenario() throws Exception {
        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 200;
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        // creating the users student1, tutor1 and instructors1
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);

        Lecture lecture = lectureUtilService.createCourseWithLecture(true);
        course = lecture.getCourse();

        courseId = course.getId();

        textExercise = textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        ExerciseUnit exerciseUnit = lectureUtilService.createExerciseUnit(textExercise);
        lecture = lectureUtilService.addLectureUnitsToLecture(lecture, List.of(exerciseUnit));
        idOfExerciseUnit = lecture.getLectureUnits().getFirst().getId();

        competencyUtilService.createCompetencyWithExercise(course, textExercise);

        idOfIndividualTextExercise = textExercise.getId();
        Exercise teamExercise = textExerciseUtilService.createTeamTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        idOfTeamTextExercise = teamExercise.getId();
        student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        modelingExercise = modelingExerciseUtilService.addModelingExerciseToCourse(course);

        User tutor1 = userTestRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
        Long idOfTeam1 = teamUtilService.createTeam(Set.of(student1), tutor1, teamExercise, TEST_PREFIX + "team1").getId();

        // Creating result for student1
        studentTextParticipation = (StudentParticipation) participationUtilService
                .createParticipationSubmissionAndResult(idOfIndividualTextExercise, student1, 10.0, 10.0, 50, true).getSubmission().getParticipation();
        // Creating result for team1
        Team team = teamRepository.findById(idOfTeam1).orElseThrow();
        teamTextParticipation = (StudentParticipation) participationUtilService.createParticipationSubmissionAndResult(idOfTeamTextExercise, team, 10.0, 10.0, 50, true)
                .getSubmission().getParticipation();

        // setting up exam
        exam = examUtilService.addExamWithUser(course, student1, true, pastTimestamp, pastTimestamp, pastTimestamp);

        idOfExam = exam.getId();
        var examTextExercise = textExerciseUtilService.createTextExerciseForExam(exam.getExerciseGroups().getFirst());
        long getIdOfIndividualTextExerciseOfExam = examTextExercise.getId();

        programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), course);
        programmingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig()));
        programmingExercise = exerciseRepository.save(programmingExercise);
        course.addExercises(programmingExercise);
        courseRepository.save(course);
        participationUtilService.createParticipationSubmissionAndResult(getIdOfIndividualTextExerciseOfExam, student1, 10.0, 10.0, 50, true);

        participantScoreScheduleService.executeScheduledTasks();
        await().until(participantScoreScheduleService::isIdle);
        await().until(() -> participantScoreRepository.findAllByExercise(textExercise).size() == 1);
        await().until(() -> participantScoreRepository.findAllByExercise(teamExercise).size() == 1);
        await().until(() -> participantScoreRepository.findAllByExercise(examTextExercise).size() == 1);
    }

    @AfterEach
    void tearDown() {
        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 500;
    }

    private void testAllPreAuthorize() throws Exception {
        request.getList("/api/assessment/courses/" + courseId + "/course-scores", HttpStatus.FORBIDDEN, ScoreDTO.class);
        request.getList("/api/assessment/exams/" + idOfExam + "/exam-scores", HttpStatus.FORBIDDEN, ScoreDTO.class);
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteParticipation_asInstructorOfCourse_shouldDeleteParticipation() throws Exception {
        // The exercise must have a short name because otherwise the corresponding audit event is too long for the database
        textExerciseUtilService.renameTextExercise(textExercise, "1");

        List<StudentParticipation> participations = studentParticipationRepository.findByExerciseIdAndStudentId(idOfIndividualTextExercise, student1.getId());
        assertThat(participations).isNotEmpty();

        for (StudentParticipation studentParticipation : participations) {
            participationUtilService.createSubmissionAndResult(studentParticipation, 30, false);
        }
        participations = studentParticipationRepository.findByExerciseIdAndStudentId(idOfIndividualTextExercise, student1.getId());
        assertThat(participations).isNotEmpty();

        await().until(() -> participantScoreScheduleService.isIdle());

        for (StudentParticipation studentParticipation : participations) {
            request.delete("/api/exercise/participations/" + studentParticipation.getId(), HttpStatus.OK);
        }
        participations = studentParticipationRepository.findByExerciseIdAndStudentId(idOfIndividualTextExercise, student1.getId());
        assertThat(participations).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteExercise_asInstructorOfCourse_shouldDeleteExercise() throws Exception {
        request.delete("/api/text/text-exercises/" + idOfIndividualTextExercise, HttpStatus.OK);
        assertThat(exerciseRepository.existsById(idOfIndividualTextExercise)).isFalse();
        assertThat(lectureUnitRepository.existsById(idOfExerciseUnit)).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteCourse_asAdmin_shouldDeleteExercise() throws Exception {
        request.delete("/api/core/admin/courses/" + courseId, HttpStatus.OK);
        assertThat(courseRepository.existsById(courseId)).isFalse();
        assertThat(exerciseRepository.existsById(idOfIndividualTextExercise)).isFalse();
        assertThat(exerciseRepository.existsById(idOfTeamTextExercise)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCourseScores_asInstructorOfCourse_shouldReturnCourseScores() throws Exception {
        List<ScoreDTO> courseScores = request.getList("/api/assessment/courses/" + courseId + "/course-scores", HttpStatus.OK, ScoreDTO.class);
        assertThat(courseScores).hasSize(4);
        ScoreDTO scoreOfStudent1 = courseScores.stream().filter(scoreDTO -> scoreDTO.studentId() == student1.getId()).findFirst().orElseThrow();
        assertThat(scoreOfStudent1.studentLogin()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(scoreOfStudent1.pointsAchieved()).isEqualTo(10.0);
        assertThat(scoreOfStudent1.scoreAchieved()).isEqualTo(33.3);
        assertThat(scoreOfStudent1.regularPointsAchievable()).isEqualTo(30.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCourseScores_asInstructorOfCourseWithGradedPresentations_shouldReturnCourseScores() throws Exception {
        GradingScale gradingScale = GradingScaleFactory.generateGradingScaleForCourse(exam.getCourse(), 2, 20.0);
        gradingScaleRepository.save(gradingScale);

        Set<Exercise> exercises = exerciseRepository.findAllExercisesByCourseId(exam.getCourse().getId());
        studentParticipationRepository.getAllParticipationsOfUserInExercises(student1, exercises, false).forEach(participation -> {
            participation.setPresentationScore(100.0);
            studentParticipationRepository.save(participation);
        });

        List<ScoreDTO> courseScores = request.getList("/api/assessment/courses/" + courseId + "/course-scores", HttpStatus.OK, ScoreDTO.class);
        assertThat(courseScores).hasSize(4);
        ScoreDTO scoreOfStudent1 = courseScores.stream().filter(scoreDTO -> scoreDTO.studentId() == student1.getId()).findFirst().orElseThrow();
        assertThat(scoreOfStudent1.studentLogin()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(scoreOfStudent1.pointsAchieved()).isEqualTo(17.5);
        assertThat(scoreOfStudent1.scoreAchieved()).isEqualTo(46.7);
        assertThat(scoreOfStudent1.regularPointsAchievable()).isEqualTo(37.5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExamScores_asInstructorOfCourse_shouldReturnExamScores() throws Exception {
        List<ScoreDTO> courseScores = request.getList("/api/assessment/exams/" + idOfExam + "/exam-scores", HttpStatus.OK, ScoreDTO.class);
        assertThat(courseScores).hasSize(1);
        ScoreDTO scoreOfStudent1 = courseScores.stream().filter(scoreDTO -> scoreDTO.studentId() == student1.getId()).findFirst().orElseThrow();
        assertThat(scoreOfStudent1.studentLogin()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(scoreOfStudent1.pointsAchieved()).isEqualTo(5.0);
        assertThat(scoreOfStudent1.scoreAchieved()).isEqualTo(5.6);
        assertThat(scoreOfStudent1.regularPointsAchievable()).isEqualTo(90.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getGradeScoresForCourse() throws Exception {
        // we only consider participations that have no due date or where the due date has passed
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExercise = exerciseRepository.save(programmingExercise);
        textExercise.setDueDate(null);
        textExercise = exerciseRepository.save(textExercise);

        StudentParticipation programmingParticipation = participationUtilService.createAndSaveParticipationForExercise(programmingExercise, TEST_PREFIX + "student1");
        Submission programmingSubmission = new ProgrammingSubmission();
        // submission date is after the due date, but before the individual due date --> included
        programmingSubmission.setSubmissionDate(programmingExercise.getDueDate().plusMinutes(2));
        programmingParticipation.setIndividualDueDate(programmingExercise.getDueDate().plusMinutes(5));
        programmingSubmission = participationUtilService.addSubmission(programmingParticipation, programmingSubmission);
        Result programmingResult = participationUtilService.addResultToSubmission(programmingParticipation, programmingSubmission);
        programmingResult.setCompletionDate(programmingExercise.getDueDate().minusMinutes(2));
        resultRepository.save(programmingResult);

        StudentParticipation textParticipation = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student2");
        Submission textSubmission = new TextSubmission();
        textSubmission.setSubmissionDate(ZonedDateTime.now().minusMinutes(2));
        textSubmission = participationUtilService.addSubmission(textParticipation, textSubmission);
        Result textResult = participationUtilService.addResultToSubmission(textParticipation, textSubmission);
        textResult.setCompletionDate(ZonedDateTime.now().minusHours(2));
        resultRepository.save(textResult);

        StudentParticipation modelingParticipation = participationUtilService.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "student1");
        Submission modelingSubmission = participationUtilService.addSubmission(modelingParticipation, new ModelingSubmission());
        participationUtilService.addResultToSubmission(modelingParticipation, modelingSubmission);

        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().minusHours(1), QuizMode.SYNCHRONIZED, course);
        quizExercise = exerciseRepository.save(quizExercise);
        StudentParticipation quizParticipation = participationUtilService.createAndSaveParticipationForExercise(quizExercise, TEST_PREFIX + "student2");
        Submission quizSubmission = new QuizSubmission();
        quizSubmission.setSubmissionDate(ZonedDateTime.now().minusHours(3));
        quizSubmission = participationUtilService.addSubmission(quizParticipation, quizSubmission);
        Result quizResult = participationUtilService.addResultToSubmission(quizParticipation, quizSubmission);
        assertThat(quizExercise.getDueDate()).isNotNull();
        quizResult.setCompletionDate(quizExercise.getDueDate().minusMinutes(2));
        resultRepository.save(quizResult);
        CourseGradeInformationDTO courseGradeInformationDTO = request.get("/api/assessment/courses/" + course.getId() + "/grade-scores", HttpStatus.OK,
                CourseGradeInformationDTO.class);
        assertThat(courseGradeInformationDTO).isNotNull();
        Collection<CourseGradeScoreDTO> courseGradeScoreDTOS = courseGradeInformationDTO.gradeScores();
        // 3 x text, 1 x quiz and 1 x programming should be included. Modeling should be excluded because it has a due date in the future
        assertThat(courseGradeScoreDTOS).hasSize(5);

        Map<Long, IdsMapValue> expectedValuesMap = new HashMap<>();
        expectedValuesMap.put(programmingParticipation.getId(), new IdsMapValue(programmingExercise.getId(), programmingParticipation.getParticipant().getId(), 100.00));
        expectedValuesMap.put(textParticipation.getId(), new IdsMapValue(textExercise.getId(), textParticipation.getParticipant().getId(), 100.00));
        Team expectedTeam = teamRepository.findWithStudentsById(teamTextParticipation.getParticipant().getId()).orElseThrow();
        for (User student : expectedTeam.getStudents()) {
            expectedValuesMap.put(teamTextParticipation.getId(), new IdsMapValue(teamTextParticipation.getExercise().getId(), student.getId(), 50));
        }
        expectedValuesMap.put(studentTextParticipation.getId(), new IdsMapValue(textExercise.getId(), studentTextParticipation.getParticipant().getId(), 50));
        expectedValuesMap.put(quizParticipation.getId(), new IdsMapValue(quizExercise.getId(), quizParticipation.getParticipant().getId(), 100.00));

        courseGradeScoreDTOS.forEach(gradeScoreDTO -> {
            long participationId = gradeScoreDTO.participationId();
            long exerciseId = gradeScoreDTO.exerciseId();
            long userId = gradeScoreDTO.userId();
            double score = gradeScoreDTO.score();
            assertThat(expectedValuesMap).containsKey(participationId);
            var expectedValuesForParticipation = expectedValuesMap.get(participationId);
            assertThat(exerciseId).isEqualTo(expectedValuesForParticipation.exerciseId());
            assertThat(userId).isEqualTo(expectedValuesForParticipation.studentId());
            assertThat(score).isEqualTo(expectedValuesForParticipation.score());
        });
    }

    private record IdsMapValue(long exerciseId, long studentId, double score) {
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void getAllParticipationsForCourse_noInstructorInCourse() throws Exception {
        request.get("/api/assessment/courses/" + course.getId() + "/grade-scores", HttpStatus.FORBIDDEN, CourseGradeInformationDTO.class);
    }
}
