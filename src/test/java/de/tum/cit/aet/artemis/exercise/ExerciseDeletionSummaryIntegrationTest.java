package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.assessment.util.ComplaintUtilService;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ExerciseDeletionSummaryIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "exdelsuminteg";

    private static final ZonedDateTime TEST_DATE = ZonedDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));

    @Autowired
    private RequestUtilService request;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private BuildJobTestRepository buildJobRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private ResultTestRepository resultRepository;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private ComplaintUtilService complaintUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);

        // Add an instructor who is not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetDeletionSummary_programmingExercise() throws Exception {
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        final ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        var studentParticipation1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var studentParticipation2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");

        BuildJob buildJob1 = new BuildJob();
        buildJob1.setExerciseId(programmingExercise.getId());
        buildJob1.setParticipationId(studentParticipation1.getId());
        buildJob1.setCourseId(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        buildJobRepository.save(buildJob1);

        BuildJob buildJob2 = new BuildJob();
        buildJob2.setExerciseId(programmingExercise.getId());
        buildJob2.setParticipationId(studentParticipation2.getId());
        buildJob2.setCourseId(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        buildJobRepository.save(buildJob2);

        Channel channel = new Channel();
        channel.setExercise(programmingExercise);
        channel.setName("test-channel-exercise");
        channel.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        channelRepository.save(channel);

        Post post1 = new Post();
        post1.setConversation(channel);
        post1.setContent("Test Post 1");
        post1.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        postRepository.save(post1);

        Post post2 = new Post();
        post2.setConversation(channel);
        post2.setContent("Test Post 2");
        post2.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        postRepository.save(post2);

        Channel channel2 = new Channel();
        channel2.setName("test-channel-course");
        channel2.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        channel2.setIsCourseWide(true);
        channelRepository.save(channel2);

        Post post3 = new Post();
        post3.setConversation(channel2);
        post3.setContent("Test Post 3");
        post3.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        postRepository.save(post3);

        AnswerPost answerPost = new AnswerPost();
        answerPost.setPost(post1);
        answerPost.setContent("Test Answer");
        answerPost.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        answerPostRepository.save(answerPost);

        var summary = request.get("/api/exercise/exercises/" + programmingExercise.getId() + "/deletion-summary", HttpStatus.OK, ExerciseDeletionSummaryDTO.class);

        assertThat(summary.numberOfStudentParticipations()).isEqualTo(2);
        assertThat(summary.numberOfBuilds()).isEqualTo(2);
        assertThat(summary.numberOfAssessments()).isEqualTo(0);
        assertThat(summary.numberOfCommunicationPosts()).isEqualTo(2);
        assertThat(summary.numberOfAnswerPosts()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetDeletionSummary_textExercise() throws Exception {
        Course course = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        TextExercise textExercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);

        var submission1 = textExerciseUtilService.createTextSubmissionWithResultAndAssessor(textExercise, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        var result1 = submission1.getLatestResult();
        assertThat(result1).isNotNull();
        result1.setRated(true);
        resultRepository.save(result1);

        var textSubmission2 = new TextSubmission();
        textSubmission2.setSubmitted(true);
        textSubmission2.setSubmissionDate(TEST_DATE);
        var submission2 = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission2, TEST_PREFIX + "student2", TEST_PREFIX + "tutor1");
        var result2 = submission2.getLatestResult();
        assertThat(result2).isNotNull();
        result2.setRated(false);
        resultRepository.save(result2);

        var summary = request.get("/api/exercise/exercises/" + textExercise.getId() + "/deletion-summary", HttpStatus.OK, ExerciseDeletionSummaryDTO.class);

        assertThat(summary.numberOfStudentParticipations()).isEqualTo(2);
        assertThat(summary.numberOfBuilds()).isEqualTo(0);
        assertThat(summary.numberOfAssessments()).isEqualTo(1);
        assertThat(summary.numberOfCommunicationPosts()).isEqualTo(0);
        assertThat(summary.numberOfAnswerPosts()).isEqualTo(0);
        // everything else that the deletion removes along with the exercise is counted, too
        assertThat(summary.numberOfSubmissions()).isEqualTo(2);
        assertThat(summary.numberOfResults()).isEqualTo(2);
        assertThat(summary.numberOfTeams()).isZero();
        assertThat(summary.numberOfComplaints()).isZero();
        assertThat(summary.numberOfPlagiarismComparisons()).isZero();
        assertThat(summary.numberOfExampleSubmissions()).isZero();
        assertThat(summary.numberOfCompetencyLinks()).isZero();
        assertThat(summary.numberOfLectureUnitLinks()).isZero();
        assertThat(summary.numberOfStudentExams()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetDeletionSummary_modelingExercise() throws Exception {
        Course course = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("ClassDiagram", TEST_PREFIX);
        ModelingExercise modelingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ModelingExercise.class);

        var modelingSubmission = new ModelingSubmission();
        modelingSubmission.setSubmitted(true);
        modelingSubmission.setSubmissionDate(TEST_DATE);
        var submission1 = modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(modelingExercise, modelingSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        var result1 = submission1.getLatestResult();
        assertThat(result1).isNotNull();
        result1.setRated(true);
        result1.setCompletionDate(TEST_DATE);
        resultRepository.save(result1);

        modelingExerciseUtilService.addModelingSubmissionWithEmptyResult(modelingExercise, "", TEST_PREFIX + "student2");

        var summary = request.get("/api/exercise/exercises/" + modelingExercise.getId() + "/deletion-summary", HttpStatus.OK, ExerciseDeletionSummaryDTO.class);

        assertThat(summary.numberOfStudentParticipations()).isEqualTo(2);
        assertThat(summary.numberOfAssessments()).isEqualTo(1);
        assertThat(summary.numberOfBuilds()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetDeletionSummary_fileUploadExercise() throws Exception {
        Course course = fileUploadExerciseUtilService.addEnrolledCourseWithFileUploadExercise(TEST_PREFIX);
        FileUploadExercise fileUploadExercise = ExerciseUtilService.getFirstExerciseWithType(course, FileUploadExercise.class);

        var fileUploadSubmission = new FileUploadSubmission();
        fileUploadSubmission.setSubmitted(true);
        fileUploadSubmission.setSubmissionDate(TEST_DATE);
        var submission1 = fileUploadExerciseUtilService.saveFileUploadSubmissionWithResultAndAssessor(fileUploadExercise, fileUploadSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        var result1 = submission1.getLatestResult();
        assertThat(result1).isNotNull();
        result1.setRated(true);
        resultRepository.save(result1);

        var fileUploadSubmission2 = new FileUploadSubmission();
        fileUploadSubmission2.setSubmitted(true);
        fileUploadSubmission2.setSubmissionDate(TEST_DATE);
        fileUploadExerciseUtilService.saveFileUploadSubmission(fileUploadExercise, fileUploadSubmission2, TEST_PREFIX + "student2");

        var summary = request.get("/api/exercise/exercises/" + fileUploadExercise.getId() + "/deletion-summary", HttpStatus.OK, ExerciseDeletionSummaryDTO.class);

        assertThat(summary.numberOfStudentParticipations()).isEqualTo(2);
        assertThat(summary.numberOfAssessments()).isEqualTo(1);
        assertThat(summary.numberOfBuilds()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetDeletionSummary_quizExercise() throws Exception {
        Course course = quizExerciseUtilService.addEnrolledCourseWithOneQuizExercise("Title", TEST_PREFIX);
        QuizExercise quizExercise = ExerciseUtilService.getFirstExerciseWithType(course, QuizExercise.class);

        Submission submission = quizExerciseUtilService.addQuizExerciseToCourseWithParticipationAndSubmissionForUser(course, TEST_PREFIX + "student1", false);
        Exercise quizExercise2 = submission.getParticipation().getExercise();

        var summary = request.get("/api/exercise/exercises/" + quizExercise.getId() + "/deletion-summary", HttpStatus.OK, ExerciseDeletionSummaryDTO.class);
        assertThat(summary.numberOfStudentParticipations()).isEqualTo(0);

        summary = request.get("/api/exercise/exercises/" + quizExercise2.getId() + "/deletion-summary", HttpStatus.OK, ExerciseDeletionSummaryDTO.class);
        assertThat(summary.numberOfStudentParticipations()).isEqualTo(1);
        assertThat(summary.numberOfAssessments()).isEqualTo(0);
        assertThat(summary.numberOfBuilds()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetDeletionSummary_countsEverythingTheDeletionRemoves() throws Exception {
        Course course = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        TextExercise textExercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        // two teams
        teamUtilService.addTeamsForExercise(textExercise, 2, instructor);

        // two submissions with a result each, and a complaint about each of the results
        var submission1 = textExerciseUtilService.createTextSubmissionWithResultAndAssessor(textExercise, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        var submission2 = textExerciseUtilService.createTextSubmissionWithResultAndAssessor(textExercise, TEST_PREFIX + "student2", TEST_PREFIX + "tutor1");
        complaintUtilService.addComplaintToSubmission(submission1, TEST_PREFIX + "student1", ComplaintType.COMPLAINT);
        complaintUtilService.addComplaintToSubmission(submission2, TEST_PREFIX + "student2", ComplaintType.MORE_FEEDBACK);

        // one plagiarism comparison
        var plagiarismResult = textExerciseUtilService.createPlagiarismResultForExercise(textExercise);
        var comparison = new PlagiarismComparison();
        comparison.setPlagiarismResult(plagiarismResult);
        comparison.setStatus(PlagiarismStatus.NONE);
        var submissionA = new PlagiarismSubmission();
        submissionA.setStudentLogin(TEST_PREFIX + "student1");
        submissionA.setSubmissionId(submission1.getId());
        var submissionB = new PlagiarismSubmission();
        submissionB.setStudentLogin(TEST_PREFIX + "student2");
        submissionB.setSubmissionId(submission2.getId());
        comparison.setSubmissionA(submissionA);
        comparison.setSubmissionB(submissionB);
        plagiarismComparisonRepository.save(comparison);

        // two example submissions
        participationUtilService.addExampleSubmission(participationUtilService.generateExampleSubmission("first example", textExercise, true));
        participationUtilService.addExampleSubmission(participationUtilService.generateExampleSubmission("second example", textExercise, true));

        // one competency link and two lecture units that link to the exercise
        competencyUtilService.linkExerciseToCompetency(competencyUtilService.createCompetency(course), textExercise);
        var lecture = lectureUtilService.createLecture(course);
        lectureUtilService.createExerciseUnit(textExercise, lecture);
        lectureUtilService.createExerciseUnit(textExercise, lecture);

        var summary = request.get("/api/exercise/exercises/" + textExercise.getId() + "/deletion-summary", HttpStatus.OK, ExerciseDeletionSummaryDTO.class);

        assertThat(summary.numberOfStudentParticipations()).isEqualTo(2);
        assertThat(summary.numberOfSubmissions()).isEqualTo(2);
        assertThat(summary.numberOfResults()).isEqualTo(2);
        assertThat(summary.numberOfTeams()).isEqualTo(2);
        assertThat(summary.numberOfComplaints()).isEqualTo(2);
        assertThat(summary.numberOfPlagiarismComparisons()).isEqualTo(1);
        assertThat(summary.numberOfExampleSubmissions()).isEqualTo(2);
        assertThat(summary.numberOfCompetencyLinks()).isEqualTo(1);
        assertThat(summary.numberOfLectureUnitLinks()).isEqualTo(2);
        assertThat(summary.numberOfStudentExams()).as("a course exercise is in no student exam").isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetDeletionSummary_countsTheStudentExamsOfAnExamExercise() throws Exception {
        TextExercise examExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);
        Exam exam = examExercise.getExerciseGroup().getExam();
        for (String login : List.of(TEST_PREFIX + "student1", TEST_PREFIX + "student2")) {
            StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, login);
            studentExam.setExercises(List.of(examExercise));
            studentExamRepository.save(studentExam);
        }
        // a student exam that does not contain the exercise does not count
        examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + "instructor1");

        var summary = request.get("/api/exercise/exercises/" + examExercise.getId() + "/deletion-summary", HttpStatus.OK, ExerciseDeletionSummaryDTO.class);

        assertThat(summary.numberOfStudentExams()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetDeletionSummary_countsStudentExamsWithoutTestRunFlagAsRegularButNotTestRuns() throws Exception {
        TextExercise examExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);
        Exam exam = examExercise.getExerciseGroup().getExam();
        StudentExam withFlag = examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + "student1");
        withFlag.setExercises(List.of(examExercise));
        studentExamRepository.save(withFlag);
        // the column is nullable, a student exam without the flag is a regular one
        StudentExam withoutFlag = examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + "student2");
        ReflectionTestUtils.setField(withoutFlag, "testRun", null);
        withoutFlag.setExercises(List.of(examExercise));
        studentExamRepository.save(withoutFlag);
        // a test run of an instructor is no student exam of the real exam
        StudentExam testRun = examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + "instructor1");
        testRun.setTestRun(true);
        testRun.setExercises(List.of(examExercise));
        studentExamRepository.save(testRun);

        var summary = request.get("/api/exercise/exercises/" + examExercise.getId() + "/deletion-summary", HttpStatus.OK, ExerciseDeletionSummaryDTO.class);

        assertThat(summary.numberOfStudentExams()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetDeletionSummary_allZeroResults() throws Exception {
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        final ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        var summary = request.get("/api/exercise/exercises/" + programmingExercise.getId() + "/deletion-summary", HttpStatus.OK, ExerciseDeletionSummaryDTO.class);

        assertThat(summary.numberOfStudentParticipations()).isEqualTo(0);
        assertThat(summary.numberOfBuilds()).isEqualTo(0);
        assertThat(summary.numberOfAssessments()).isEqualTo(0);
        assertThat(summary.numberOfCommunicationPosts()).isEqualTo(0);
        assertThat(summary.numberOfAnswerPosts()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void testGetDeletionSummary_editorForbidden() throws Exception {
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        final ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        request.get("/api/exercise/exercises/" + programmingExercise.getId() + "/deletion-summary", HttpStatus.FORBIDDEN, ExerciseDeletionSummaryDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "USER")
    void testGetDeletionSummary_instructorNotInCourseForbidden() throws Exception {
        // Use a course without TEST_PREFIX enrollment so instructor2 is not enrolled (it should be FORBIDDEN)
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        final ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        request.get("/api/exercise/exercises/" + programmingExercise.getId() + "/deletion-summary", HttpStatus.FORBIDDEN, ExerciseDeletionSummaryDTO.class);
    }
}
