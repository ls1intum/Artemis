package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.TextAssessmentKnowledgeService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreAverageDTO;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreDTO;
import de.tum.in.www1.artemis.web.rest.dto.ScoreDTO;

class ParticipantScoreIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
    private LectureRepository lectureRepository;

    @Autowired
    private LearningGoalRepository learningGoalRepository;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private TextAssessmentKnowledgeService textAssessmentKnowledgeService;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @BeforeEach
    void setupTestScenario() {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        // creating the users student1-student5, tutor1-tutor10 and instructors1-instructor10
        this.database.addUsers(5, 10, 0, 10);
        // creating course
        Course course = this.database.createCourse();
        Lecture lecture = new Lecture();
        lecture.setTitle("ExampleLecture");
        lecture.setCourse(course);
        lecture = lectureRepository.saveAndFlush(lecture);
        courseId = course.getId();
        TextExercise textExercise = database.createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        ExerciseUnit exerciseUnit = database.createExerciseUnit(textExercise);
        database.addLectureUnitsToLecture(lecture, Set.of(exerciseUnit));
        lecture = lectureRepository.findByIdWithLectureUnitsAndLearningGoalsElseThrow(lecture.getId());
        exerciseUnit = (ExerciseUnit) lecture.getLectureUnits().get(0);
        idOfExerciseUnit = exerciseUnit.getId();
        LearningGoal learningGoal = new LearningGoal();
        learningGoal.setTitle("ExampleLearningGoal");
        learningGoal.setCourse(course);
        learningGoal.addExercise(textExercise);
        learningGoalRepository.saveAndFlush(learningGoal);
        idOfIndividualTextExercise = textExercise.getId();
        Exercise teamExercise = database.createTeamTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        idOfTeamTextExercise = teamExercise.getId();
        User student1 = userRepository.findOneByLogin("student1").get();
        idOfStudent1 = student1.getId();
        User tutor1 = userRepository.findOneByLogin("tutor1").get();
        idOfTeam1 = database.createTeam(Set.of(student1), tutor1, teamExercise, "team1").getId();

        // Creating result for student1
        database.createParticipationSubmissionAndResult(idOfIndividualTextExercise, student1, 10.0, 10.0, 50, true);
        // Creating result for team1
        Team team = teamRepository.findById(idOfTeam1).get();
        database.createParticipationSubmissionAndResult(idOfTeamTextExercise, team, 10.0, 10.0, 50, true);

        // setting up exam
        Exam exam = ModelFactory.generateExam(course);
        ModelFactory.generateExerciseGroup(true, exam);
        exam.addRegisteredUser(student1);
        exam = examRepository.save(exam);
        idOfExam = exam.getId();
        createIndividualTextExerciseForExam();
        database.createParticipationSubmissionAndResult(getIdOfIndividualTextExerciseOfExam, student1, 10.0, 10.0, 50, true);

        await().until(() -> participantScoreRepository.findAll().size() == 3);
    }

    private void testAllPreAuthorize() throws Exception {
        request.getList("/api/courses/" + courseId + "/participant-scores", HttpStatus.FORBIDDEN, ParticipantScoreDTO.class);
        request.getList("/api/courses/" + courseId + "/participant-scores/average-participant", HttpStatus.FORBIDDEN, ParticipantScoreAverageDTO.class);
        request.get("/api/courses/" + courseId + "/participant-scores/average", HttpStatus.FORBIDDEN, Long.class);
        request.getList("/api/exams/" + idOfExam + "/participant-scores", HttpStatus.FORBIDDEN, ParticipantScoreDTO.class);
        request.getList("/api/exams/" + idOfExam + "/participant-scores/average-participant", HttpStatus.FORBIDDEN, ParticipantScoreAverageDTO.class);
        request.get("/api/exams/" + idOfExam + "/participant-scores/", HttpStatus.FORBIDDEN, Long.class);
        request.getList("/api/courses/" + courseId + "/course-scores", HttpStatus.FORBIDDEN, ScoreDTO.class);
        request.getList("/api/exams/" + idOfExam + "/exam-scores", HttpStatus.FORBIDDEN, ScoreDTO.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteParticipation_asInstructorOfCourse_shouldDeleteParticipation() throws Exception {
        List<StudentParticipation> participations = studentParticipationRepository.findByExerciseIdAndStudentId(idOfIndividualTextExercise, idOfStudent1);
        assertThat(participations).isNotEmpty();
        for (StudentParticipation studentParticipation : participations) {
            database.createSubmissionAndResult(studentParticipation, 30, false);
        }
        participations = studentParticipationRepository.findByExerciseIdAndStudentId(idOfIndividualTextExercise, idOfStudent1);
        assertThat(participations).isNotEmpty();
        for (StudentParticipation studentParticipation : participations) {
            request.delete("/api/participations/" + studentParticipation.getId(), HttpStatus.OK);
        }
        participations = studentParticipationRepository.findByExerciseIdAndStudentId(idOfIndividualTextExercise, idOfStudent1);
        assertThat(participations).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteExercise_asInstructorOfCourse_shouldDeleteExercise() throws Exception {
        request.delete("/api/text-exercises/" + idOfIndividualTextExercise, HttpStatus.OK);
        assertThat(exerciseRepository.existsById(idOfIndividualTextExercise)).isFalse();
        assertThat(lectureUnitRepository.existsById(idOfExerciseUnit)).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void deleteCourse_asInstructorOfCourse_shouldDeleteExercise() throws Exception {
        request.delete("/api/courses/" + courseId, HttpStatus.OK);
        assertThat(courseRepository.existsById(courseId)).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getCourseScores_asInstructorOfCourse_shouldReturnCourseScores() throws Exception {
        List<ScoreDTO> courseScores = request.getList("/api/courses/" + courseId + "/course-scores", HttpStatus.OK, ScoreDTO.class);
        assertThat(courseScores).hasSize(25);
        ScoreDTO scoreOfStudent1 = courseScores.stream().filter(scoreDTO -> scoreDTO.studentId.equals(idOfStudent1)).findFirst().get();
        assertThat(scoreOfStudent1.studentLogin).isEqualTo("student1");
        assertThat(scoreOfStudent1.pointsAchieved).isEqualTo(10.0);
        assertThat(scoreOfStudent1.scoreAchieved).isEqualTo(50.0);
        assertThat(scoreOfStudent1.regularPointsAchievable).isEqualTo(20.0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getExamScores_asInstructorOfCourse_shouldReturnExamScores() throws Exception {
        List<ScoreDTO> courseScores = request.getList("/api/exams/" + idOfExam + "/exam-scores", HttpStatus.OK, ScoreDTO.class);
        assertThat(courseScores).hasSize(1);
        ScoreDTO scoreOfStudent1 = courseScores.stream().filter(scoreDTO -> scoreDTO.studentId.equals(idOfStudent1)).findFirst().get();
        assertThat(scoreOfStudent1.studentLogin).isEqualTo("student1");
        assertThat(scoreOfStudent1.pointsAchieved).isEqualTo(5.0);
        assertThat(scoreOfStudent1.scoreAchieved).isEqualTo(5.6);
        assertThat(scoreOfStudent1.regularPointsAchievable).isEqualTo(90.0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getParticipantScoresOfCourse_asInstructorOfCourse_shouldReturnParticipantScores() throws Exception {
        List<ParticipantScoreDTO> participantScoresOfCourse = request.getList("/api/courses/" + courseId + "/participant-scores", HttpStatus.OK, ParticipantScoreDTO.class);
        assertThat(participantScoresOfCourse).hasSize(2);
        ParticipantScoreDTO student1Result = participantScoresOfCourse.stream().filter(participantScoreDTO -> participantScoreDTO.userId() != null).findFirst().get();
        ParticipantScoreDTO team1Result = participantScoresOfCourse.stream().filter(participantScoreDTO -> participantScoreDTO.teamId() != null).findFirst().get();
        assertParticipantScoreDTOStructure(student1Result, idOfStudent1, null, idOfIndividualTextExercise, 50D, 50D, 5.0, 5.0);
        assertParticipantScoreDTOStructure(team1Result, null, idOfTeam1, idOfTeamTextExercise, 50D, 50D, 5.0, 5.0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getAverageScoreOfParticipantInCourse_asInstructorOfCourse_shouldReturnAverageParticipantScores() throws Exception {
        var scoreAverageDTOS = request.getList("/api/courses/" + courseId + "/participant-scores/average-participant", HttpStatus.OK, ParticipantScoreAverageDTO.class);
        assertThat(scoreAverageDTOS).hasSize(2);
        var student1Result = scoreAverageDTOS.get(0);
        var team1Result = scoreAverageDTOS.get(1);
        assertAverageParticipantScoreDTOStructure(student1Result, "student1", 50.0, 50.0, 5.0, 5.0);
        assertAverageParticipantScoreDTOStructure(team1Result, "team1", 50.0, 50.0, 5.0, 5.0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getAverageScoreOfCourses_asInstructorOfCourse_shouldReturnAverageScore() throws Exception {
        Double averageRated = request.get("/api/courses/" + courseId + "/participant-scores/average?onlyConsiderRatedScores=true", HttpStatus.OK, Double.class);
        assertThat(averageRated).isEqualTo(50D);
        Double average = request.get("/api/courses/" + courseId + "/participant-scores/average?onlyConsiderRatedScores=false", HttpStatus.OK, Double.class);
        assertThat(average).isEqualTo(50D);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getParticipantScoresOfExam_asInstructorOfCourse_shouldReturnParticipantScores() throws Exception {
        List<ParticipantScoreDTO> participantScoresOfExam = request.getList("/api/exams/" + idOfExam + "/participant-scores", HttpStatus.OK, ParticipantScoreDTO.class);
        assertThat(participantScoresOfExam).hasSize(1);
        ParticipantScoreDTO student1Result = participantScoresOfExam.stream().filter(participantScoreDTO -> participantScoreDTO.userId() != null).findFirst().get();
        assertParticipantScoreDTOStructure(student1Result, idOfStudent1, null, getIdOfIndividualTextExerciseOfExam, 50D, 50D, 5.0, 5.0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getAverageScoreOfParticipantInExam_asInstructorOfCourse_shouldReturnAverageParticipantScores() throws Exception {
        List<ParticipantScoreAverageDTO> participantScoreAverageDTOS = request.getList("/api/exams/" + idOfExam + "/participant-scores/average-participant", HttpStatus.OK,
                ParticipantScoreAverageDTO.class);
        assertThat(participantScoreAverageDTOS).hasSize(1);
        ParticipantScoreAverageDTO student1Result = participantScoreAverageDTOS.stream().filter(participantScoreAverageDTO -> participantScoreAverageDTO.name() != null).findFirst()
                .get();
        assertAverageParticipantScoreDTOStructure(student1Result, "student1", 50.0, 50.0, 5.0, 5.0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getAverageScoreOfExam_asInstructorOfCourse_shouldReturnAverageScore() throws Exception {
        Double averageRated = request.get("/api/exams/" + idOfExam + "/participant-scores/average?onlyConsiderRatedScores=true", HttpStatus.OK, Double.class);
        assertThat(averageRated).isEqualTo(50D);
        Double average = request.get("/api/exams/" + idOfExam + "/participant-scores/average?onlyConsiderRatedScores=false", HttpStatus.OK, Double.class);
        assertThat(average).isEqualTo(50D);
    }

    private void createIndividualTextExerciseForExam() {
        Exam exam;
        exam = examRepository.findWithExerciseGroupsAndExercisesById(idOfExam).get();
        var exerciseGroup0 = exam.getExerciseGroups().get(0);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup0);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise.setKnowledge(textAssessmentKnowledgeService.createNewKnowledge());
        textExercise = exerciseRepository.save(textExercise);
        getIdOfIndividualTextExerciseOfExam = textExercise.getId();
    }

    private void assertParticipantScoreDTOStructure(ParticipantScoreDTO participantScoreDTO, Long expectedUserId, Long expectedTeamId, Long expectedExerciseId,
            Double expectedLastResultScore, Double expectedLastRatedResultScore, Double expectedLastPoints, Double expectedLastRatedPoints) {
        assertThat(participantScoreDTO.userId()).isEqualTo(expectedUserId);
        assertThat(participantScoreDTO.teamId()).isEqualTo(expectedTeamId);
        assertThat(participantScoreDTO.exerciseId()).isEqualTo(expectedExerciseId);
        assertThat(participantScoreDTO.lastResultScore()).isEqualTo(expectedLastResultScore);
        assertThat(participantScoreDTO.lastRatedResultScore()).isEqualTo(expectedLastRatedResultScore);
        assertThat(participantScoreDTO.lastPoints()).isEqualTo(expectedLastPoints);
        assertThat(participantScoreDTO.lastRatedPoints()).isEqualTo(expectedLastRatedPoints);
    }

    private void assertAverageParticipantScoreDTOStructure(ParticipantScoreAverageDTO participantScoreAverageDTO, String expectedName, Double expectedAverageScore,
            Double expectedAverageRatedScore, Double expectedAveragePoints, Double expectedAverageRatedPoints) {
        assertThat(participantScoreAverageDTO.name()).isEqualTo(expectedName);
        assertThat(participantScoreAverageDTO.averageScore()).isEqualTo(expectedAverageScore);
        assertThat(participantScoreAverageDTO.averageRatedScore()).isEqualTo(expectedAverageRatedScore);
        assertThat(participantScoreAverageDTO.averagePoints()).isEqualTo(expectedAveragePoints);
        assertThat(participantScoreAverageDTO.averageRatedPoints()).isEqualTo(expectedAverageRatedPoints);

    }
}
