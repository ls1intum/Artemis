package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exam.ExamFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreScheduleService;
import de.tum.in.www1.artemis.team.TeamUtilService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.ScoreDTO;

class ParticipantScoreIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "participantscoreintegrationtest";

    private Long idOfExam;

    private Long courseId;

    private Long idOfTeam1;

    private Long idOfStudent1;

    private Long idOfIndividualTextExercise;

    private Long getIdOfIndividualTextExerciseOfExam;

    private Long idOfTeamTextExercise;

    private Long idOfExerciseUnit;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private CompetencyRepository competencyRepository;

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
    private CourseUtilService courseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @BeforeEach
    void setupTestScenario() {
        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 50;
        participantScoreScheduleService.activate();
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        // creating the users student1, tutor1 and instructors1
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        // Instructors should only be part of "participantscoreinstructor"
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        instructor.setGroups(Set.of("participantscoreinstructor"));
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        tutor.setGroups(Set.of("participantscoretutor"));
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student.setGroups(Set.of("participantscorestudent"));
        userRepository.saveAll(List.of(instructor, tutor, student));

        // creating course
        Course course = courseUtilService.createCourse();
        course.setInstructorGroupName("participantscoreinstructor");
        course.setTeachingAssistantGroupName("participantscoretutor");
        course.setStudentGroupName("participantscorestudent");
        courseRepository.save(course);

        Lecture lecture = new Lecture();
        lecture.setTitle("ExampleLecture");
        lecture.setCourse(course);
        lecture = lectureRepository.saveAndFlush(lecture);
        courseId = course.getId();
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        ExerciseUnit exerciseUnit = lectureUtilService.createExerciseUnit(textExercise);
        lectureUtilService.addLectureUnitsToLecture(lecture, List.of(exerciseUnit));
        lecture = lectureRepository.findByIdWithLectureUnitsAndCompetenciesElseThrow(lecture.getId());
        exerciseUnit = (ExerciseUnit) lecture.getLectureUnits().get(0);
        idOfExerciseUnit = exerciseUnit.getId();
        Competency competency = new Competency();
        competency.setTitle("ExampleCompetency");
        competency.setCourse(course);
        competency.addExercise(textExercise);
        competencyRepository.saveAndFlush(competency);
        idOfIndividualTextExercise = textExercise.getId();
        Exercise teamExercise = textExerciseUtilService.createTeamTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        idOfTeamTextExercise = teamExercise.getId();
        User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        idOfStudent1 = student1.getId();
        User tutor1 = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").get();
        idOfTeam1 = teamUtilService.createTeam(Set.of(student1), tutor1, teamExercise, TEST_PREFIX + "team1").getId();

        // Creating result for student1
        participationUtilService.createParticipationSubmissionAndResult(idOfIndividualTextExercise, student1, 10.0, 10.0, 50, true);
        // Creating result for team1
        Team team = teamRepository.findById(idOfTeam1).get();
        participationUtilService.createParticipationSubmissionAndResult(idOfTeamTextExercise, team, 10.0, 10.0, 50, true);

        // setting up exam
        Exam exam = ExamFactory.generateExam(course);
        ExamFactory.generateExerciseGroup(true, exam);
        exam = examRepository.save(exam);
        var examUser = new ExamUser();
        examUser.setExam(exam);
        examUser.setUser(student1);
        examUserRepository.save(examUser);
        exam.setExamUsers(Set.of(examUser));
        exam = examRepository.save(exam);
        idOfExam = exam.getId();
        var examTextExercise = createIndividualTextExerciseForExam();
        participationUtilService.createParticipationSubmissionAndResult(getIdOfIndividualTextExerciseOfExam, student1, 10.0, 10.0, 50, true);

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
        var exercise = exerciseRepository.findById(idOfIndividualTextExercise).get();
        exercise.setTitle("1"); // The exercise must have a short name because otherwise the corresponding audit event is too long for the database
        exerciseRepository.save(exercise);
        List<StudentParticipation> participations = studentParticipationRepository.findByExerciseIdAndStudentId(idOfIndividualTextExercise, idOfStudent1);
        assertThat(participations).isNotEmpty();
        for (StudentParticipation studentParticipation : participations) {
            participationUtilService.createSubmissionAndResult(studentParticipation, 30, false);
        }
        participations = studentParticipationRepository.findByExerciseIdAndStudentId(idOfIndividualTextExercise, idOfStudent1);
        assertThat(participations).isNotEmpty();

        await().pollInterval(10, TimeUnit.MILLISECONDS).until(() -> participantScoreScheduleService.isIdle());

        for (StudentParticipation studentParticipation : participations) {
            request.delete("/api/participations/" + studentParticipation.getId(), HttpStatus.OK);
        }
        participations = studentParticipationRepository.findByExerciseIdAndStudentId(idOfIndividualTextExercise, idOfStudent1);
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
        ScoreDTO scoreOfStudent1 = courseScores.stream().filter(scoreDTO -> scoreDTO.studentId.equals(idOfStudent1)).findFirst().get();
        assertThat(scoreOfStudent1.studentLogin).isEqualTo(TEST_PREFIX + "student1");
        assertThat(scoreOfStudent1.pointsAchieved).isEqualTo(10.0);
        assertThat(scoreOfStudent1.scoreAchieved).isEqualTo(50.0);
        assertThat(scoreOfStudent1.regularPointsAchievable).isEqualTo(20.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCourseScores_asInstructorOfCourseWithGradedPresentations_shouldReturnCourseScores() throws Exception {
        Exam exam = examRepository.findWithExerciseGroupsAndExercisesById(idOfExam).get();

        GradingScale gradingScale = GradingScaleFactory.generateGradingScaleForCourse(exam.getCourse(), 2, 20.0);
        gradingScaleRepository.save(gradingScale);

        User student = userRepository.findById(idOfStudent1).get();

        Set<Exercise> exercises = exerciseRepository.findAllExercisesByCourseId(exam.getCourse().getId());
        studentParticipationRepository.getAllParticipationsOfUserInExercises(student, exercises, false).forEach(participation -> {
            participation.setPresentationScore(100.0);
            studentParticipationRepository.save(participation);
        });

        List<ScoreDTO> courseScores = request.getList("/api/courses/" + courseId + "/course-scores", HttpStatus.OK, ScoreDTO.class);
        assertThat(courseScores).hasSize(3);
        ScoreDTO scoreOfStudent1 = courseScores.stream().filter(scoreDTO -> scoreDTO.studentId.equals(idOfStudent1)).findFirst().get();
        assertThat(scoreOfStudent1.studentLogin).isEqualTo(TEST_PREFIX + "student1");
        assertThat(scoreOfStudent1.pointsAchieved).isEqualTo(15.0);
        assertThat(scoreOfStudent1.scoreAchieved).isEqualTo(60.0);
        assertThat(scoreOfStudent1.regularPointsAchievable).isEqualTo(25.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExamScores_asInstructorOfCourse_shouldReturnExamScores() throws Exception {
        List<ScoreDTO> courseScores = request.getList("/api/exams/" + idOfExam + "/exam-scores", HttpStatus.OK, ScoreDTO.class);
        assertThat(courseScores).hasSize(1);
        ScoreDTO scoreOfStudent1 = courseScores.stream().filter(scoreDTO -> scoreDTO.studentId.equals(idOfStudent1)).findFirst().get();
        assertThat(scoreOfStudent1.studentLogin).isEqualTo(TEST_PREFIX + "student1");
        assertThat(scoreOfStudent1.pointsAchieved).isEqualTo(5.0);
        assertThat(scoreOfStudent1.scoreAchieved).isEqualTo(5.6);
        assertThat(scoreOfStudent1.regularPointsAchievable).isEqualTo(90.0);
    }

    private TextExercise createIndividualTextExerciseForExam() {
        Exam exam;
        exam = examRepository.findWithExerciseGroupsAndExercisesById(idOfExam).get();
        var exerciseGroup0 = exam.getExerciseGroups().get(0);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup0);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise = exerciseRepository.save(textExercise);
        getIdOfIndividualTextExerciseOfExam = textExercise.getId();
        return textExercise;
    }
}
