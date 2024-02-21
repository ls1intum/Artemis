package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.competency.CompetencyUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.repository.ParticipantScoreRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreScheduleService;
import de.tum.in.www1.artemis.team.TeamUtilService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.score.ScoreDTO;

class ParticipantScoreIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "participantscoreintegrationtest";

    private Long idOfExam;

    private Long courseId;

    private Long idOfIndividualTextExercise;

    private Long idOfTeamTextExercise;

    private Long idOfExerciseUnit;

    private TextExercise textExercise;

    private Exam exam;

    private User student1;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private UserUtilService userUtilService;

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

    @BeforeEach
    void setupTestScenario() {
        // Prevents the ParticipantScoreScheduleService from scheduling tasks related to prior results
        ReflectionTestUtils.setField(participantScoreScheduleService, "lastScheduledRun", Optional.of(Instant.now()));

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 200;
        participantScoreScheduleService.activate();
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        // creating the users student1, tutor1 and instructors1
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);

        Lecture lecture = lectureUtilService.createCourseWithLecture(true);
        Course course = lecture.getCourse();

        courseId = course.getId();

        textExercise = textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        ExerciseUnit exerciseUnit = lectureUtilService.createExerciseUnit(textExercise);
        lecture = lectureUtilService.addLectureUnitsToLecture(lecture, List.of(exerciseUnit));
        idOfExerciseUnit = lecture.getLectureUnits().get(0).getId();

        competencyUtilService.createCompetencyWithExercise(course, textExercise);

        idOfIndividualTextExercise = textExercise.getId();
        Exercise teamExercise = textExerciseUtilService.createTeamTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        idOfTeamTextExercise = teamExercise.getId();
        student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();

        User tutor1 = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
        Long idOfTeam1 = teamUtilService.createTeam(Set.of(student1), tutor1, teamExercise, TEST_PREFIX + "team1").getId();

        // Creating result for student1
        participationUtilService.createParticipationSubmissionAndResult(idOfIndividualTextExercise, student1, 10.0, 10.0, 50, true);
        // Creating result for team1
        Team team = teamRepository.findById(idOfTeam1).orElseThrow();
        participationUtilService.createParticipationSubmissionAndResult(idOfTeamTextExercise, team, 10.0, 10.0, 50, true);

        // setting up exam
        exam = examUtilService.addExamWithUser(course, student1, true, pastTimestamp, pastTimestamp, pastTimestamp);

        idOfExam = exam.getId();
        var examTextExercise = textExerciseUtilService.createTextExerciseForExam(exam.getExerciseGroups().get(0));
        long getIdOfIndividualTextExerciseOfExam = examTextExercise.getId();
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
        request.getList("/api/courses/" + courseId + "/course-scores", HttpStatus.FORBIDDEN, ScoreDTO.class);
        request.getList("/api/exams/" + idOfExam + "/exam-scores", HttpStatus.FORBIDDEN, ScoreDTO.class);
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
            request.delete("/api/participations/" + studentParticipation.getId(), HttpStatus.OK);
        }
        participations = studentParticipationRepository.findByExerciseIdAndStudentId(idOfIndividualTextExercise, student1.getId());
        assertThat(participations).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteExercise_asInstructorOfCourse_shouldDeleteExercise() throws Exception {
        request.delete("/api/text-exercises/" + idOfIndividualTextExercise, HttpStatus.OK);
        assertThat(exerciseRepository.existsById(idOfIndividualTextExercise)).isFalse();
        assertThat(lectureUnitRepository.existsById(idOfExerciseUnit)).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteCourse_asAdmin_shouldDeleteExercise() throws Exception {
        request.delete("/api/admin/courses/" + courseId, HttpStatus.OK);
        assertThat(courseRepository.existsById(courseId)).isFalse();
        assertThat(exerciseRepository.existsById(idOfIndividualTextExercise)).isFalse();
        assertThat(exerciseRepository.existsById(idOfTeamTextExercise)).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCourseScores_asInstructorOfCourse_shouldReturnCourseScores() throws Exception {
        List<ScoreDTO> courseScores = request.getList("/api/courses/" + courseId + "/course-scores", HttpStatus.OK, ScoreDTO.class);
        assertThat(courseScores).hasSize(3);
        ScoreDTO scoreOfStudent1 = courseScores.stream().filter(scoreDTO -> scoreDTO.studentId() == student1.getId()).findFirst().orElseThrow();
        assertThat(scoreOfStudent1.studentLogin()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(scoreOfStudent1.pointsAchieved()).isEqualTo(10.0);
        assertThat(scoreOfStudent1.scoreAchieved()).isEqualTo(50.0);
        assertThat(scoreOfStudent1.regularPointsAchievable()).isEqualTo(20.0);
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

        List<ScoreDTO> courseScores = request.getList("/api/courses/" + courseId + "/course-scores", HttpStatus.OK, ScoreDTO.class);
        assertThat(courseScores).hasSize(3);
        ScoreDTO scoreOfStudent1 = courseScores.stream().filter(scoreDTO -> scoreDTO.studentId() == student1.getId()).findFirst().orElseThrow();
        assertThat(scoreOfStudent1.studentLogin()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(scoreOfStudent1.pointsAchieved()).isEqualTo(15.0);
        assertThat(scoreOfStudent1.scoreAchieved()).isEqualTo(60.0);
        assertThat(scoreOfStudent1.regularPointsAchievable()).isEqualTo(25.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExamScores_asInstructorOfCourse_shouldReturnExamScores() throws Exception {
        List<ScoreDTO> courseScores = request.getList("/api/exams/" + idOfExam + "/exam-scores", HttpStatus.OK, ScoreDTO.class);
        assertThat(courseScores).hasSize(1);
        ScoreDTO scoreOfStudent1 = courseScores.stream().filter(scoreDTO -> scoreDTO.studentId() == student1.getId()).findFirst().orElseThrow();
        assertThat(scoreOfStudent1.studentLogin()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(scoreOfStudent1.pointsAchieved()).isEqualTo(5.0);
        assertThat(scoreOfStudent1.scoreAchieved()).isEqualTo(5.6);
        assertThat(scoreOfStudent1.regularPointsAchievable()).isEqualTo(90.0);
    }
}
