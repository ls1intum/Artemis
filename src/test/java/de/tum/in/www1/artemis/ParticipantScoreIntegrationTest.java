package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreAverageDTO;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreDTO;
import de.tum.in.www1.artemis.web.rest.dto.ScoreDTO;

public class ParticipantScoreIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    Long idOfExam;

    Long idOfCourse;

    Long idOfTeam1;

    Long idOfStudent1;

    Long idOfIndividualTextExercise;

    Long getIdOfIndividualTextExerciseOfExam;

    Long idOfTeamTextExercise;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ParticipationService participationService;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    ExamRepository examRepository;

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @BeforeEach
    public void setupTestScenario() {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        // creating the users student1-student5, tutor1-tutor10 and instructors1-instructor10
        this.database.addUsers(5, 10, 10);
        // creating course
        Course course = this.database.createCourse();
        idOfCourse = course.getId();
        createIndividualTextExercise(pastTimestamp, pastTimestamp, pastTimestamp);
        createTeamTextExerciseAndTeam(pastTimestamp, pastTimestamp, pastTimestamp);

        // Creating result for student1
        User student1 = userRepository.findOneByLogin("student1").get();
        idOfStudent1 = student1.getId();
        createParticipationSubmissionAndResult(idOfIndividualTextExercise, student1, 10.0, 10.0, 50, true);
        // Creating result for team1
        Team team = teamRepository.findById(idOfTeam1).get();
        createParticipationSubmissionAndResult(idOfTeamTextExercise, team, 10.0, 10.0, 50, true);

        // setting up exam
        Exam exam = ModelFactory.generateExam(course);
        ModelFactory.generateExerciseGroup(true, exam);
        exam.addRegisteredUser(student1);
        exam = examRepository.save(exam);
        idOfExam = exam.getId();
        createIndividualTextExerciseForExam();
        createParticipationSubmissionAndResult(getIdOfIndividualTextExerciseOfExam, student1, 10.0, 10.0, 50, true);
    }

    private void testAllPreAuthorize() throws Exception {
        request.getList("/api/courses/" + idOfCourse + "/participant-scores", HttpStatus.FORBIDDEN, ParticipantScoreDTO.class);
        request.getList("/api/courses/" + idOfCourse + "/participant-scores/average-participant", HttpStatus.FORBIDDEN, ParticipantScoreAverageDTO.class);
        request.get("/api/courses/" + idOfCourse + "/participant-scores/average", HttpStatus.FORBIDDEN, Long.class);
        request.getList("/api/exams/" + idOfExam + "/participant-scores", HttpStatus.FORBIDDEN, ParticipantScoreDTO.class);
        request.getList("/api/exams/" + idOfExam + "/participant-scores/average-participant", HttpStatus.FORBIDDEN, ParticipantScoreAverageDTO.class);
        request.get("/api/exams/" + idOfExam + "/participant-scores/", HttpStatus.FORBIDDEN, Long.class);
        request.getList("/api/courses/" + idOfCourse + "/course-scores", HttpStatus.FORBIDDEN, ScoreDTO.class);
        request.getList("/api/exams/" + idOfExam + "/exam-scores", HttpStatus.FORBIDDEN, ScoreDTO.class);
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getCourseScores_asInstructorOfCourse_shouldReturnCourseScores() throws Exception {
        List<ScoreDTO> courseScores = request.getList("/api/courses/" + idOfCourse + "/course-scores", HttpStatus.OK, ScoreDTO.class);
        assertThat(courseScores.size()).isEqualTo(25);
        ScoreDTO scoreOfStudent1 = courseScores.stream().filter(scoreDTO -> scoreDTO.studentId.equals(idOfStudent1)).findFirst().get();
        assertThat(scoreOfStudent1.studentLogin).isEqualTo("student1");
        assertThat(scoreOfStudent1.pointsAchieved).isEqualTo(10.0);
        assertThat(scoreOfStudent1.scoreAchieved).isEqualTo(50.0);
        assertThat(scoreOfStudent1.regularPointsAchievable).isEqualTo(20.0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getExamScores_asInstructorOfCourse_shouldReturnExamScores() throws Exception {
        List<ScoreDTO> courseScores = request.getList("/api/exams/" + idOfExam + "/exam-scores", HttpStatus.OK, ScoreDTO.class);
        assertThat(courseScores.size()).isEqualTo(1);
        ScoreDTO scoreOfStudent1 = courseScores.stream().filter(scoreDTO -> scoreDTO.studentId.equals(idOfStudent1)).findFirst().get();
        assertThat(scoreOfStudent1.studentLogin).isEqualTo("student1");
        assertThat(scoreOfStudent1.pointsAchieved).isEqualTo(5.0);
        assertThat(scoreOfStudent1.scoreAchieved).isEqualTo(5.6);
        assertThat(scoreOfStudent1.regularPointsAchievable).isEqualTo(90.0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getParticipantScoresOfCourse_asInstructorOfCourse_shouldReturnParticipantScores() throws Exception {
        List<ParticipantScoreDTO> participantScoresOfCourse = request.getList("/api/courses/" + idOfCourse + "/participant-scores", HttpStatus.OK, ParticipantScoreDTO.class);
        assertThat(participantScoresOfCourse.size()).isEqualTo(2);
        ParticipantScoreDTO student1Result = participantScoresOfCourse.stream().filter(participantScoreDTO -> participantScoreDTO.userId != null).findFirst().get();
        ParticipantScoreDTO team1Result = participantScoresOfCourse.stream().filter(participantScoreDTO -> participantScoreDTO.teamId != null).findFirst().get();
        assertParticipantScoreDTOStructure(student1Result, idOfStudent1, null, idOfIndividualTextExercise, 50L, 50L, 5.0, 5.0);
        assertParticipantScoreDTOStructure(team1Result, null, idOfTeam1, idOfTeamTextExercise, 50L, 50L, 5.0, 5.0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAverageScoreOfParticipantInCourse_asInstructorOfCourse_shouldReturnAverageParticipantScores() throws Exception {
        List<ParticipantScoreAverageDTO> participantScoreAverageDTOS = request.getList("/api/courses/" + idOfCourse + "/participant-scores/average-participant", HttpStatus.OK,
                ParticipantScoreAverageDTO.class);
        assertThat(participantScoreAverageDTOS.size()).isEqualTo(2);
        ParticipantScoreAverageDTO student1Result = participantScoreAverageDTOS.stream().filter(participantScoreAverageDTO -> participantScoreAverageDTO.userName != null)
                .findFirst().get();
        ParticipantScoreAverageDTO team1Result = participantScoreAverageDTOS.stream().filter(participantScoreAverageDTO -> participantScoreAverageDTO.teamName != null).findFirst()
                .get();
        assertAverageParticipantScoreDTOStructure(student1Result, "student1", null, 50.0, 50.0, 5.0, 5.0);
        assertAverageParticipantScoreDTOStructure(team1Result, null, "team1", 50.0, 50.0, 5.0, 5.0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAverageScoreOfCourses_asInstructorOfCourse_shouldReturnAverageScore() throws Exception {
        Long averageRated = request.get("/api/courses/" + idOfCourse + "/participant-scores/average?onlyConsiderRatedScores=true", HttpStatus.OK, Long.class);
        assertThat(averageRated).isEqualTo(50L);
        Long average = request.get("/api/courses/" + idOfCourse + "/participant-scores/average?onlyConsiderRatedScores=false", HttpStatus.OK, Long.class);
        assertThat(average).isEqualTo(50L);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getParticipantScoresOfExam_asInstructorOfCourse_shouldReturnParticipantScores() throws Exception {
        List<ParticipantScoreDTO> participantScoresOfExam = request.getList("/api/exams/" + idOfExam + "/participant-scores", HttpStatus.OK, ParticipantScoreDTO.class);
        assertThat(participantScoresOfExam.size()).isEqualTo(1);
        ParticipantScoreDTO student1Result = participantScoresOfExam.stream().filter(participantScoreDTO -> participantScoreDTO.userId != null).findFirst().get();
        assertParticipantScoreDTOStructure(student1Result, idOfStudent1, null, getIdOfIndividualTextExerciseOfExam, 50L, 50L, 5.0, 5.0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAverageScoreOfParticipantInExam_asInstructorOfCourse_shouldReturnAverageParticipantScores() throws Exception {
        List<ParticipantScoreAverageDTO> participantScoreAverageDTOS = request.getList("/api/exams/" + idOfExam + "/participant-scores/average-participant", HttpStatus.OK,
                ParticipantScoreAverageDTO.class);
        assertThat(participantScoreAverageDTOS.size()).isEqualTo(1);
        ParticipantScoreAverageDTO student1Result = participantScoreAverageDTOS.stream().filter(participantScoreAverageDTO -> participantScoreAverageDTO.userName != null)
                .findFirst().get();
        assertAverageParticipantScoreDTOStructure(student1Result, "student1", null, 50.0, 50.0, 5.0, 5.0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAverageScoreOfExam_asInstructorOfCourse_shouldReturnAverageScore() throws Exception {
        Long averageRated = request.get("/api/exams/" + idOfExam + "/participant-scores/average?onlyConsiderRatedScores=true", HttpStatus.OK, Long.class);
        assertThat(averageRated).isEqualTo(50L);
        Long average = request.get("/api/exams/" + idOfExam + "/participant-scores/average?onlyConsiderRatedScores=false", HttpStatus.OK, Long.class);
        assertThat(average).isEqualTo(50L);
    }

    private void createIndividualTextExercise(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise = exerciseRepository.save(textExercise);

        idOfIndividualTextExercise = textExercise.getId();
    }

    private void createIndividualTextExerciseForExam() {
        Exam exam;
        exam = examRepository.findWithExerciseGroupsAndExercisesById(idOfExam).get();
        var exerciseGroup0 = exam.getExerciseGroups().get(0);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup0);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise = exerciseRepository.save(textExercise);
        getIdOfIndividualTextExerciseOfExam = textExercise.getId();
    }

    private Result createParticipationSubmissionAndResult(Long idOfExercise, Participant participant, Double pointsOfExercise, Double bonusPointsOfExercise, long scoreAwarded,
            boolean rated) {
        Exercise exercise = exerciseRepository.findById(idOfExercise).get();

        if (!exercise.getMaxPoints().equals(pointsOfExercise)) {
            exercise.setMaxPoints(pointsOfExercise);
        }
        if (!exercise.getBonusPoints().equals(bonusPointsOfExercise)) {
            exercise.setBonusPoints(bonusPointsOfExercise);
        }
        exercise = exerciseRepository.saveAndFlush(exercise);

        StudentParticipation studentParticipation = participationService.startExercise(exercise, participant, false);

        return createSubmissionAndResult(studentParticipation, scoreAwarded, rated);
    }

    private Result createSubmissionAndResult(StudentParticipation studentParticipation, long scoreAwarded, boolean rated) {
        Exercise exercise = studentParticipation.getExercise();
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
        submission = submissionRepository.saveAndFlush(submission);

        Result result = ModelFactory.generateResult(rated, scoreAwarded);
        result.setParticipation(studentParticipation);
        result.setSubmission(submission);
        result.completionDate(ZonedDateTime.now());
        return resultRepository.saveAndFlush(result);
    }

    private void createTeamTextExerciseAndTeam(ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        Course course;
        // creating text exercise with Result
        course = courseRepository.findWithEagerExercisesById(idOfCourse);
        TextExercise teamTextExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        teamTextExercise.setMaxPoints(10.0);
        teamTextExercise.setBonusPoints(0.0);
        teamTextExercise.setMode(ExerciseMode.TEAM);
        teamTextExercise = exerciseRepository.save(teamTextExercise);

        User student1 = userRepository.findOneByLogin("student1").get();
        User tutor1 = userRepository.findOneByLogin("tutor1").get();
        Team team = new Team();
        team.addStudents(student1);
        team.setOwner(tutor1);
        team.setShortName("team1");
        team.setName("team1");
        team = teamRepository.saveAndFlush(team);

        idOfTeam1 = team.getId();
        idOfTeamTextExercise = teamTextExercise.getId();
    }

    private void assertParticipantScoreDTOStructure(ParticipantScoreDTO participantScoreDTO, Long expectedUserId, Long expectedTeamId, Long expectedExerciseId,
            Long expectedLastResultScore, Long expectedLastRatedResultScore, Double expectedLastPoints, Double exptectedLastRatedPoints) {
        assertThat(participantScoreDTO.userId).isEqualTo(expectedUserId);
        assertThat(participantScoreDTO.teamId).isEqualTo(expectedTeamId);
        assertThat(participantScoreDTO.exerciseId).isEqualTo(expectedExerciseId);
        assertThat(participantScoreDTO.lastResultScore).isEqualTo(expectedLastResultScore);
        assertThat(participantScoreDTO.lastRatedResultScore).isEqualTo(expectedLastRatedResultScore);
        assertThat(participantScoreDTO.lastPoints).isEqualTo(expectedLastPoints);
        assertThat(participantScoreDTO.lastRatedPoints).isEqualTo(exptectedLastRatedPoints);
    }

    private void assertAverageParticipantScoreDTOStructure(ParticipantScoreAverageDTO participantScoreAverageDTO, String expectedUserName, String expectedTeamName,
            Double expectedAverageScore, Double expectedAverageRatedScore, Double expectedAveragePoints, Double expectedAverageRatedPoints) {
        assertThat(participantScoreAverageDTO.userName).isEqualTo(expectedUserName);
        assertThat(participantScoreAverageDTO.teamName).isEqualTo(expectedTeamName);
        assertThat(participantScoreAverageDTO.averageScore).isEqualTo(expectedAverageScore);
        assertThat(participantScoreAverageDTO.averageRatedScore).isEqualTo(expectedAverageRatedScore);
        assertThat(participantScoreAverageDTO.averagePoints).isEqualTo(expectedAveragePoints);
        assertThat(participantScoreAverageDTO.averageRatedPoints).isEqualTo(expectedAverageRatedPoints);

    }
}
