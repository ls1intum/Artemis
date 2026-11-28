package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.dto.ExamImportResultDTO;
import de.tum.cit.aet.artemis.exam.service.ExamSessionService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Pins the exact JSON the exam DTOs send and accept: the detailed exam response, both deletion summaries and the request body of the exam import.
 * Ids and dates are masked, every other value is compared as sent.
 */
class ExamDtoWireContractIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "examdtowirepins";

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T.*");

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private ExamSessionService examSessionService;

    private Course course;

    private Exam exam;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 1);
        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        exam = examUtilService.addExam(course);
        // every optional field set, so that each one is pinned
        var now = ZonedDateTime.now();
        exam.setVisibleDate(now.minusHours(4));
        exam.setStartDate(now.minusHours(3));
        exam.setEndDate(now.minusHours(2));
        exam.setPublishResultsDate(now.minusHours(1));
        exam.setExamStudentReviewStart(now.minusMinutes(50));
        exam.setExamStudentReviewEnd(now.minusMinutes(40));
        exam.setExampleSolutionPublicationDate(now.minusMinutes(30));
        exam.setExamSummaryPublicationDate(now.minusMinutes(20));
        exam.setExaminer("Prof. Wire");
        exam.setModuleNumber("IN0042");
        exam.setCourseName("Wire course");
        exam.setExamWithAttendanceCheck(true);
        exam.setRandomizeExerciseOrder(true);
        exam.setExamArchivePath("exam-archive.zip");
        exam = examRepository.save(exam);
        // a channel name that must not leak into the detailed response
        var channel = examUtilService.addExamChannel(exam, "wire channel");
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var post = examUtilService.createPost(channel, "Instructor Post", instructor);
        examUtilService.createAnswerPost(post, "Answer 1", userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        examUtilService.createAnswerPost(post, "Answer 2", instructor);
        examUtilService.createAnswerPost(post, "Answer 3", instructor);
        exam = examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, 3);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, false, false);
        // student1 did not start, student2 started, student3 started and submitted
        for (int i = 1; i <= 3; i++) {
            var studentExam = examUtilService.addStudentExamWithUser(exam, userUtilService.getUserByLogin(TEST_PREFIX + "student" + i));
            examSessionService.startExamSession(studentExam, null, null, null, null);
            if (i >= 2) {
                studentExam.setStartedAndStartDate(ZonedDateTime.now());
            }
            if (i == 3) {
                studentExam.setSubmissionDate(ZonedDateTime.now());
                studentExam.setSubmitted(true);
            }
            studentExamRepository.save(studentExam);
        }
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
    }

    private String masked(JsonNode node) {
        return mask(node).toString();
    }

    private JsonNode mask(JsonNode node) {
        if (node.isObject()) {
            ObjectNode copy = request.getObjectMapper().createObjectNode();
            // keys sorted, so the pinned text does not depend on the property order
            node.properties().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                if (entry.getKey().equals("id") && entry.getValue().isNumber()) {
                    copy.put("id", "<id>");
                }
                else if ((entry.getKey().equals("title") || entry.getKey().equals("shortName")) && entry.getValue().isString()) {
                    // generated at random by the test factories
                    copy.put(entry.getKey(), "<text>");
                }
                else {
                    copy.set(entry.getKey(), mask(entry.getValue()));
                }
            });
            return copy;
        }
        if (node.isArray()) {
            // elements sorted, so the pinned text does not depend on the order of unordered collections
            var copy = request.getObjectMapper().createArrayNode();
            StreamSupport.stream(node.spliterator(), false).map(this::mask).sorted(Comparator.comparing(JsonNode::toString)).forEach(copy::add);
            return copy;
        }
        if (node.isString() && DATE.matcher(node.asString()).matches()) {
            return request.getObjectMapper().getNodeFactory().stringNode("<date>");
        }
        return node;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void detailedExamWire() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withExerciseGroups", "true");
        JsonNode wire = request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, JsonNode.class, params);
        assertThat(wire.has("channelName")).as("the detailed response never sends the channel name").isFalse();
        assertThat(masked(wire)).isEqualTo(DETAILED_EXAM_JSON);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void examDeletionSummaryWire() throws Exception {
        JsonNode wire = request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/deletion-summary", HttpStatus.OK, JsonNode.class);
        assertThat(masked(wire)).isEqualTo(EXAM_DELETION_SUMMARY_JSON);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1 })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void exerciseGroupDeletionSummaryWire(int groupIndex) throws Exception {
        ExerciseGroup group = exam.getExerciseGroups().get(groupIndex);
        JsonNode wire = request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/exercise-groups/" + group.getId() + "/deletion-summary", HttpStatus.OK,
                JsonNode.class);
        assertThat(masked(wire)).isEqualTo(EXERCISE_GROUP_DELETION_SUMMARY_JSON.get(groupIndex));
    }

    private ObjectNode importBody() throws Exception {
        String start = ZonedDateTime.now().plusDays(10).withNano(0).toString();
        return (ObjectNode) request.getObjectMapper().readTree("""
                {"title":"Imported wire exam","examMode":"REAL","examWithAttendanceCheck":false,
                 "visibleDate":"%s","startDate":"%s","endDate":"%s","workingTime":3600,"examMaxPoints":10,
                 "numberOfCorrectionRoundsInExam":1,"randomizeExerciseOrder":true,"numberOfExercisesInExam":2,"gracePeriod":180,
                 "startText":"start","endText":"end","confirmationStartText":"cs","confirmationEndText":"ce",
                 "examiner":"Prof","moduleNumber":"IN0001","courseName":"Course","channelName":"imported-wire-exam",
                 "courseId":%d,"exerciseGroups":[{"title":"Group","isMandatory":true,"exercises":[]}]}
                """.formatted(ZonedDateTime.now().plusDays(9).withNano(0), start, ZonedDateTime.now().plusDays(10).plusHours(1).withNano(0), course.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void importAcceptsTheRawBodyAndIgnoresIdAndUnknownFields() throws Exception {
        ObjectNode body = importBody();
        body.put("id", 987654321L);
        body.put("somethingUnknown", "x");
        ExamImportResultDTO result = request.postWithResponseBody("/api/exam/courses/" + course.getId() + "/exam-import", body, ExamImportResultDTO.class, HttpStatus.CREATED);
        assertThat(result.exam().id()).isNotEqualTo(987654321L);
        Exam imported = examRepository.findByIdWithExerciseGroupsElseThrow(result.exam().id());
        assertThat(imported.getTitle()).isEqualTo("Imported wire exam");
        assertThat(imported.getExamMaxPoints()).isEqualTo(10);
        assertThat(imported.getWorkingTime()).isEqualTo(3600);
        assertThat(imported.getGracePeriod()).isEqualTo(180);
        assertThat(imported.getNumberOfExercisesInExam()).isEqualTo(2);
        assertThat(imported.getNumberOfCorrectionRoundsInExam()).isEqualTo(1);
        assertThat(imported.getRandomizeExerciseOrder()).isTrue();
        assertThat(imported.getExaminer()).isEqualTo("Prof");
        assertThat(imported.getModuleNumber()).isEqualTo("IN0001");
        assertThat(imported.getCourseName()).isEqualTo("Course");
        assertThat(imported.getStartText()).isEqualTo("start");
        assertThat(imported.getEndText()).isEqualTo("end");
        assertThat(imported.getConfirmationStartText()).isEqualTo("cs");
        assertThat(imported.getConfirmationEndText()).isEqualTo("ce");
    }

    private static Stream<Arguments> invalidImportBodies() {
        return Stream.of(Arguments.of("title", null), Arguments.of("examMode", null), Arguments.of("visibleDate", null), Arguments.of("startDate", null),
                Arguments.of("endDate", null), Arguments.of("courseId", null), Arguments.of("examMaxPoints", "10.5"), Arguments.of("examMaxPoints", "\"10\""),
                Arguments.of("workingTime", "\"abc\""), Arguments.of("visibleDate", "\"not a date\""), Arguments.of("examMode", "\"NOT_A_MODE\""),
                Arguments.of("examWithAttendanceCheck", "\"maybe\""), Arguments.of("exerciseGroups", "[null]"),
                Arguments.of("exerciseGroups", "[{\"title\":\"g\",\"exercises\":[null]}]"));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("invalidImportBodies")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void importRejectsInvalidBodies(String field, String value) throws Exception {
        ObjectNode body = importBody();
        if (value == null) {
            body.remove(field);
        }
        else {
            body.set(field, request.getObjectMapper().readTree(value));
        }
        int examsBefore = examRepository.findByCourseId(course.getId()).size();
        request.postWithoutLocation("/api/exam/courses/" + course.getId() + "/exam-import", body, HttpStatus.BAD_REQUEST, null);
        assertThat(examRepository.findByCourseId(course.getId())).hasSize(examsBefore);
    }

    private static final String DETAILED_EXAM_JSON = "{\"confirmationEndText\":\"Confirmation End Text\",\"confirmationStartText\":\"Confirmation Start Text\",\"course\":{\"id\":\"<id>\",\"testCourse\":false,\"title\":\"<text>\"},\"courseName\":\"Wire course\",\"endDate\":\"<date>\",\"endText\":\"End Text\",\"examArchivePath\":\"exam-archive.zip\",\"examMaxPoints\":24,\"examMode\":\"REAL\",\"examStudentReviewEnd\":\"<date>\",\"examStudentReviewStart\":\"<date>\",\"examSummaryPublicationDate\":\"<date>\",\"examWithAttendanceCheck\":true,\"examiner\":\"Prof. Wire\",\"exampleSolutionPublicationDate\":\"<date>\",\"exerciseGroups\":[{\"exercises\":[{\"allowedNumberOfAttempts\":1,\"bonusPoints\":0.0,\"difficulty\":\"MEDIUM\",\"duration\":10,\"id\":\"<id>\",\"includedInOverallScore\":\"INCLUDED_COMPLETELY\",\"maxPoints\":9.0,\"numberOfParticipations\":0,\"quizMode\":\"SYNCHRONIZED\",\"quizQuestions\":[{\"id\":\"<id>\",\"type\":\"drag-and-drop\"},{\"id\":\"<id>\",\"type\":\"multiple-choice\"},{\"id\":\"<id>\",\"type\":\"short-answer\"}],\"randomizeQuestionOrder\":true,\"shortName\":\"<text>\",\"teamMode\":false,\"testRunParticipationsExist\":true,\"title\":\"<text>\",\"type\":\"quiz\"},{\"allowedNumberOfAttempts\":1,\"bonusPoints\":0.0,\"difficulty\":\"MEDIUM\",\"duration\":10,\"id\":\"<id>\",\"includedInOverallScore\":\"INCLUDED_COMPLETELY\",\"maxPoints\":9.0,\"numberOfParticipations\":0,\"quizMode\":\"SYNCHRONIZED\",\"quizQuestions\":[{\"id\":\"<id>\",\"type\":\"drag-and-drop\"},{\"id\":\"<id>\",\"type\":\"multiple-choice\"},{\"id\":\"<id>\",\"type\":\"short-answer\"}],\"randomizeQuestionOrder\":true,\"shortName\":\"<text>\",\"teamMode\":false,\"title\":\"<text>\",\"type\":\"quiz\"}],\"id\":\"<id>\",\"isMandatory\":true,\"title\":\"<text>\"},{\"exercises\":[{\"bonusPoints\":0.0,\"diagramType\":\"ClassDiagram\",\"difficulty\":\"MEDIUM\",\"id\":\"<id>\",\"includedInOverallScore\":\"INCLUDED_COMPLETELY\",\"maxPoints\":5.0,\"numberOfParticipations\":0,\"shortName\":\"<text>\",\"teamMode\":false,\"title\":\"<text>\",\"type\":\"modeling\"},{\"bonusPoints\":0.0,\"diagramType\":\"ClassDiagram\",\"difficulty\":\"MEDIUM\",\"id\":\"<id>\",\"includedInOverallScore\":\"INCLUDED_COMPLETELY\",\"maxPoints\":5.0,\"numberOfParticipations\":0,\"shortName\":\"<text>\",\"teamMode\":false,\"title\":\"<text>\",\"type\":\"modeling\"}],\"id\":\"<id>\",\"isMandatory\":true,\"title\":\"<text>\"},{\"exercises\":[{\"bonusPoints\":0.0,\"difficulty\":\"MEDIUM\",\"filePattern\":\"pdf\",\"id\":\"<id>\",\"includedInOverallScore\":\"INCLUDED_COMPLETELY\",\"maxPoints\":5.0,\"numberOfParticipations\":0,\"shortName\":\"<text>\",\"teamMode\":false,\"title\":\"<text>\",\"type\":\"file-upload\"},{\"bonusPoints\":0.0,\"difficulty\":\"MEDIUM\",\"filePattern\":\"pdf\",\"id\":\"<id>\",\"includedInOverallScore\":\"INCLUDED_COMPLETELY\",\"maxPoints\":5.0,\"numberOfParticipations\":0,\"shortName\":\"<text>\",\"teamMode\":false,\"title\":\"<text>\",\"type\":\"file-upload\"}],\"id\":\"<id>\",\"isMandatory\":true,\"title\":\"<text>\"},{\"exercises\":[{\"bonusPoints\":0.0,\"difficulty\":\"MEDIUM\",\"id\":\"<id>\",\"includedInOverallScore\":\"INCLUDED_AS_BONUS\",\"maxPoints\":5.0,\"numberOfParticipations\":0,\"shortName\":\"<text>\",\"teamMode\":false,\"title\":\"<text>\",\"type\":\"text\"}],\"id\":\"<id>\",\"isMandatory\":true,\"title\":\"<text>\"},{\"exercises\":[{\"bonusPoints\":0.0,\"difficulty\":\"MEDIUM\",\"id\":\"<id>\",\"includedInOverallScore\":\"INCLUDED_COMPLETELY\",\"maxPoints\":5.0,\"numberOfParticipations\":0,\"shortName\":\"<text>\",\"teamMode\":false,\"title\":\"<text>\",\"type\":\"text\"},{\"bonusPoints\":0.0,\"difficulty\":\"MEDIUM\",\"id\":\"<id>\",\"includedInOverallScore\":\"INCLUDED_COMPLETELY\",\"maxPoints\":5.0,\"numberOfParticipations\":0,\"shortName\":\"<text>\",\"teamMode\":false,\"title\":\"<text>\",\"type\":\"text\"}],\"id\":\"<id>\",\"isMandatory\":true,\"title\":\"<text>\"},{\"exercises\":[{\"bonusPoints\":0.0,\"difficulty\":\"MEDIUM\",\"id\":\"<id>\",\"includedInOverallScore\":\"NOT_INCLUDED\",\"maxPoints\":5.0,\"numberOfParticipations\":0,\"shortName\":\"<text>\",\"teamMode\":false,\"title\":\"<text>\",\"type\":\"text\"}],\"id\":\"<id>\",\"isMandatory\":true,\"title\":\"<text>\"}],\"gracePeriod\":180,\"id\":\"<id>\",\"moduleNumber\":\"IN0042\",\"numberOfCorrectionRoundsInExam\":1,\"numberOfExamUsers\":3,\"numberOfExercisesInExam\":6,\"publishResultsDate\":\"<date>\",\"randomizeExerciseOrder\":true,\"startDate\":\"<date>\",\"startText\":\"Start Text\",\"started\":true,\"title\":\"<text>\",\"visibleDate\":\"<date>\",\"workingTime\":3000}";

    private static final String EXAM_DELETION_SUMMARY_JSON = "{\"numberNotStartedExams\":1,\"numberOfAnswerPosts\":3,\"numberOfAssessments\":0,\"numberOfBuilds\":0,\"numberOfCommunicationPosts\":1,\"numberOfComplaints\":0,\"numberOfExamLiveEvents\":0,\"numberOfExamSessions\":3,\"numberOfExerciseGroups\":6,\"numberOfFileUploadExercises\":2,\"numberOfModelingExercises\":2,\"numberOfPlagiarismComparisons\":0,\"numberOfProgrammingExercises\":0,\"numberOfQuizExercises\":2,\"numberOfResults\":0,\"numberOfStudentParticipations\":6,\"numberOfSubmissions\":6,\"numberOfTestRuns\":1,\"numberOfTextExercises\":4,\"numberRegisteredStudents\":3,\"numberStartedExams\":2,\"numberSubmittedExams\":1}";

    private static final List<String> EXERCISE_GROUP_DELETION_SUMMARY_JSON = List.of(
            "{\"numberOfAssessments\":0,\"numberOfBuilds\":0,\"numberOfComplaints\":0,\"numberOfFileUploadExercises\":0,\"numberOfModelingExercises\":0,\"numberOfPlagiarismComparisons\":0,\"numberOfProgrammingExercises\":0,\"numberOfQuizExercises\":0,\"numberOfResults\":0,\"numberOfStudentExams\":0,\"numberOfStudentParticipations\":1,\"numberOfSubmissions\":1,\"numberOfTextExercises\":2}",
            "{\"numberOfAssessments\":0,\"numberOfBuilds\":0,\"numberOfComplaints\":0,\"numberOfFileUploadExercises\":0,\"numberOfModelingExercises\":0,\"numberOfPlagiarismComparisons\":0,\"numberOfProgrammingExercises\":0,\"numberOfQuizExercises\":2,\"numberOfResults\":0,\"numberOfStudentExams\":0,\"numberOfStudentParticipations\":1,\"numberOfSubmissions\":1,\"numberOfTextExercises\":0}");
}
