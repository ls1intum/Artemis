package de.tum.cit.aet.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisFailureReason;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.dto.MetisCheckDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisDashboardDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisDashboardRowDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisDashboardSummaryDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisGenerateResultDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisQuestionDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;
import de.tum.cit.aet.artemis.metis.util.MetisUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupUtilService;

/**
 * The results of the comprehension checks of an exercise through their REST endpoints: the dashboard with its rows and summary, the number of statements it
 * takes, the CSV export and "generate now", each against the roles that may and may not use them.
 * <p>
 * The clock is frozen per method. Every configuration a method switched on is switched off again afterwards, so that the checks "generate now" puts into the
 * queue are never claimed by a dispatch elsewhere.
 */
class MetisDashboardIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "metisdashboard";

    /** The frozen time of every method. Every other time is an offset from it, in whole seconds, which the database keeps exactly. */
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2024-07-01T12:00:00Z");

    private static final ZonedDateTime DUE = NOW.minusDays(1);

    private static final ZonedDateTime WINDOW_START = NOW.minusHours(1);

    private static final ZonedDateTime WINDOW_END = NOW.plusHours(1);

    /** When the first question of a completed fixture check was shown; the rest of its thread follows within minutes. */
    private static final ZonedDateTime THREAD_START = NOW.minusMinutes(30);

    private static final ZonedDateTime APPROVED_AT = NOW.minusDays(1).plusHours(2);

    private static final String COMMIT_HASH = "a".repeat(40);

    /** The columns of the export, spelled out rather than taken from the code under test. */
    private static final List<String> EXPORT_COLUMNS = List.of("participation_id", "student_login", "student_name", "tutorial_group", "check_status", "failure_reason",
            "commit_hash", "llm_model", "prompt_version", "generated_at", "started_at", "completed_at", "question_order", "question_kind", "parent_question_order",
            "focus_file_path", "focus_file_changed_lines", "question_text", "time_limit_seconds", "regeneration_count", "approved_at", "shown_at", "answered_at", "answer_seconds",
            "answer_text", "llm_score", "llm_rationale", "llm_rated_at");

    /** The columns of the export that describe the question of a line. */
    private static final List<String> QUESTION_COLUMNS = EXPORT_COLUMNS.subList(EXPORT_COLUMNS.indexOf("question_order"), EXPORT_COLUMNS.size());

    /** The columns are datetime(3) without a zone, so what comes back is the same instant, not necessarily the same zone. */
    private static final BiPredicate<ZonedDateTime, ZonedDateTime> SAME_INSTANT = (left, right) -> left.toInstant().equals(right.toInstant());

    private static final RecursiveComparisonConfiguration SAME_INSTANTS = RecursiveComparisonConfiguration.builder().withEqualsForType(SAME_INSTANT, ZonedDateTime.class).build();

    @Autowired
    private MetisUtilService metisUtilService;

    @Autowired
    private MetisCheckRepository checkRepository;

    @Autowired
    private MetisQuestionRepository questionRepository;

    @Autowired
    private TutorialGroupUtilService tutorialGroupUtilService;

    private final List<Long> enabledExerciseIds = new ArrayList<>();

    private Course course;

    private ProgrammingExercise exercise;

    private long exerciseId;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 10, 1, 1, 1);
        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exerciseId = exercise.getId();
        Exercise stored = exerciseRepository.findByIdElseThrow(exerciseId);
        stored.setReleaseDate(DUE.minusDays(7));
        exerciseRepository.save(stored);
        exerciseUtilService.updateExerciseDueDate(exerciseId, DUE);
        // The fixture seeds cloud AI consent only for accounts without any decision, so a decision a method changed would otherwise reach the next one
        for (int student = 1; student <= 3; student++) {
            userUtilService.setAiSelectionDecision(user("student" + student), AiSelectionDecision.CLOUD_AI);
        }
        freezeClockAt(NOW);
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
        enabledExerciseIds.forEach(id -> metisUtilService.saveConfig(id, false, false, null, null));
        enabledExerciseIds.clear();
    }

    @Nested
    class Dashboard {

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldListOneRowPerCheckWithItsStudentGroupAndQuestionsInThreadOrder() throws Exception {
            // student1 is in a group with a window and in one without, whose title comes first; the group of the window is the one shown
            TutorialGroup tuesday = tutorialGroup("Metis Tuesday", Set.of(user("student1"), user("student2")));
            tutorialGroup("Metis Monday", Set.of(user("student1"), user("student3")));
            tutorialGroup("Metis Wednesday", Set.of(user("student3")));
            metisUtilService.saveGroupWindow(exerciseId, tuesday.getId(), WINDOW_START, WINDOW_END);
            rename("student3", " ", null);
            CompletedCheck completed = completedCheck("student1");
            MetisCheck skipped = unavailableCheck("student2", MetisCheckStatus.SKIPPED, MetisFailureReason.WINDOW_CLOSED);
            mainQuestion(skipped, 1);
            MetisCheck ready = check("student3", MetisCheckStatus.READY);
            MetisQuestion firstOfReady = mainQuestion(ready, 1);
            MetisQuestion secondOfReady = mainQuestion(ready, 2);
            MetisCheck pending = check("student4", MetisCheckStatus.PENDING);
            mainQuestion(pending, 1);
            participation("student5");

            List<MetisDashboardRowDTO> rows = getDashboard().rows();

            assertThat(rows).extracting(MetisDashboardRowDTO::studentLogin).containsExactly(login("student1"), login("student2"), login("student3"), login("student4"));
            assertThat(rows).extracting(MetisDashboardRowDTO::studentName).containsExactly(login("student1") + "First " + login("student1") + "Last",
                    login("student2") + "First " + login("student2") + "Last", login("student3"), login("student4") + "First " + login("student4") + "Last");
            assertThat(rows).extracting(MetisDashboardRowDTO::tutorialGroupTitle).containsExactly("Metis Tuesday", "Metis Tuesday", "Metis Monday", null);
            assertThat(rows).extracting(MetisDashboardRowDTO::participationId).containsExactly(completed.check().getParticipationId(), skipped.getParticipationId(),
                    ready.getParticipationId(), pending.getParticipationId());

            var expectedCompleted = new MetisCheckDTO(completed.check().getId(), completed.check().getParticipationId(), MetisCheckStatus.COMPLETED, COMMIT_HASH,
                    MetisUtilService.TEST_LLM_MODEL, MetisUtilService.TEST_PROMPT_VERSION, null, NOW, NOW, NOW);
            assertThat(rows.getFirst().check()).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(expectedCompleted);
            var expectedSkipped = new MetisCheckDTO(skipped.getId(), skipped.getParticipationId(), MetisCheckStatus.SKIPPED, null, null, null,
                    MetisFailureReason.WINDOW_CLOSED.name(), null, null, null);
            assertThat(rows.get(1).check()).isEqualTo(expectedSkipped);

            // The follow-up of the first question was asked after the second main question in the order of the check, but comes right after its main question
            assertThat(rows.getFirst().questions()).extracting(MetisQuestionDTO::id).containsExactly(completed.first().getId(), completed.followUp().getId(),
                    completed.second().getId());
            MetisQuestionDTO firstQuestion = rows.getFirst().questions().getFirst();
            assertThat(firstQuestion.questionText()).isEqualTo(completed.first().getQuestionText());
            assertThat(firstQuestion.approvedAt().toInstant()).isEqualTo(APPROVED_AT.toInstant());
            assertThat(firstQuestion.answerText()).isEqualTo("It copies the input.");
            assertThat(firstQuestion.llmScore()).isEqualTo(2);
            assertThat(firstQuestion.llmRationale()).isEqualTo("Vague about why the input is copied.");
            assertThat(rows.getFirst().questions().get(1).parentQuestionId()).isEqualTo(completed.first().getId());
            // Questions left over from an earlier run of a skipped or queued check are not shown
            assertThat(rows.get(1).questions()).isEmpty();
            assertThat(rows.get(2).questions()).extracting(MetisQuestionDTO::id).containsExactly(firstOfReady.getId(), secondOfReady.getId());
            assertThat(rows.get(3).questions()).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldSummarizeTheRowsWithTheRulesOfTheReviewPage() throws Exception {
            metisUtilService.saveConfig(exerciseId, false, false, WINDOW_START, WINDOW_END);
            TutorialGroup tuesday = tutorialGroup("Metis Tuesday", Set.of());
            metisUtilService.saveGroupWindow(exerciseId, tuesday.getId(), WINDOW_START, WINDOW_END.plusHours(2));
            // Completed and approved: main scores 2 and 5, the follow-up rated 4 does not count towards the average
            completedCheck("student1");
            // Completed without a review: main score 4 and an answer the backfill has not rated yet
            MetisCheck completedWithoutReview = check("student2", MetisCheckStatus.COMPLETED);
            rated(answered(mainQuestion(completedWithoutReview, 1), THREAD_START, THREAD_START.plusSeconds(60), "It sorts."), 4, THREAD_START.plusSeconds(65));
            answered(mainQuestion(completedWithoutReview, 2), THREAD_START.plusSeconds(65), THREAD_START.plusSeconds(120), "It swaps.");
            // In progress: its score does not count yet, its follow-up does
            MetisCheck inProgress = check("student3", MetisCheckStatus.IN_PROGRESS);
            MetisQuestion mainOfInProgress = approved(mainQuestion(inProgress, 1));
            rated(answered(mainOfInProgress, THREAD_START, THREAD_START.plusSeconds(60), "No idea."), 1, THREAD_START.plusSeconds(65));
            followUp(inProgress, 2, mainOfInProgress);
            MetisCheck inReview = check("student4", MetisCheckStatus.IN_REVIEW);
            approved(mainQuestion(inReview, 1));
            mainQuestion(inReview, 2);
            approved(mainQuestion(check("student5", MetisCheckStatus.READY), 1));
            mainQuestion(check("student6", MetisCheckStatus.READY), 1);
            check("student7", MetisCheckStatus.PENDING);
            unavailableCheck("student8", MetisCheckStatus.SKIPPED, MetisFailureReason.NO_OWN_CODE);
            unavailableCheck("student9", MetisCheckStatus.FAILED, MetisFailureReason.LLM_REJECTED);
            check("student10", MetisCheckStatus.GENERATING);

            MetisDashboardSummaryDTO summary = getDashboard().summary();

            // Not started: student4 to student7, and student10, whose questions are being generated. Awaiting review: the unapproved questions of student2,
            // student4 and student6, and student7 and student10, who have no questions yet. (2 + 5 + 4) / 3 rounds to 3.7
            var expectedSummary = new MetisDashboardSummaryDTO(10, 2, 1, 5, 2, 5, 3.7, 2, WINDOW_END.plusHours(2));
            assertThat(summary).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(expectedSummary);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldReturnAnEmptyDashboardWhenNoParticipationHasACheck() throws Exception {
            participation("student1");

            String json = request.get(dashboardPath(), HttpStatus.OK, String.class);
            MetisDashboardDTO dashboard = getDashboard();

            assertThat(dashboard).isEqualTo(new MetisDashboardDTO(new MetisDashboardSummaryDTO(0, 0, 0, 0, 0, 0, null, 0, null), List.of()));
            // The rows always reach the client, even when there are none; the average and the window end are left out when they are unknown
            assertThat(json).contains("\"rows\":[]").doesNotContain("averageScore").doesNotContain("windowEnd");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldReadTheDashboardWithTheSameStatementsForOneAndForThreeStudents() throws Exception {
            metisUtilService.saveConfig(exerciseId, false, false, WINDOW_START, WINDOW_END);
            TutorialGroup tuesday = tutorialGroup("Metis Tuesday", Set.of(user("student1"), user("student2"), user("student3")));
            metisUtilService.saveGroupWindow(exerciseId, tuesday.getId(), WINDOW_START, WINDOW_END);
            completedCheck("student1");
            // The first request of a user may load what later requests find in a cache
            getDashboard();
            queryInterceptor.startQueryCount();
            getDashboard();
            long statementsForOneStudent = queryInterceptor.getQueryCount();
            completedCheck("student2");
            completedCheck("student3");

            MetisDashboardDTO dashboard = assertThatDb(() -> getDashboard()).hasBeenCalledTimes(statementsForOneStudent);

            assertThat(dashboard.rows()).hasSize(3).allSatisfy(row -> assertThat(row.questions()).hasSize(3));
            // Of these, Metis reads its configuration, the group windows, the checks with their students and all questions, and the tutorial group registrations
            List<String> statements = queryInterceptor.getStatements().stream().map(sql -> sql.toLowerCase(Locale.ROOT)).toList();
            assertThat(statements).filteredOn(sql -> sql.contains("metis_")).hasSize(4);
            assertThat(statements).filteredOn(sql -> sql.contains("tutorial_group_registration")).hasSize(1);
        }
    }

    @Nested
    class Export {

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldExportOneLinePerQuestionInThreadOrderAndOneLineForACheckWithoutQuestions() throws Exception {
            TutorialGroup tuesday = tutorialGroup("Metis Tuesday", Set.of(user("student1")));
            metisUtilService.saveGroupWindow(exerciseId, tuesday.getId(), WINDOW_START, WINDOW_END);
            CompletedCheck completed = completedCheck("student1");
            MetisCheck skipped = unavailableCheck("student2", MetisCheckStatus.SKIPPED, MetisFailureReason.NO_SUBMISSION);

            MockHttpServletResponse response = export();

            byte[] content = response.getContentAsByteArray();
            assertThat(content).as("UTF-8 byte order mark").startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
            assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
            assertThat(fileNameOf(response)).isEqualTo(exercise.getShortName() + "-comprehension-check.csv");
            ExportedLines lines = lines(response);
            assertThat(lines.header()).isEqualTo(EXPORT_COLUMNS);
            assertThat(lines.records()).hasSize(4);

            CSVRecord first = lines.records().getFirst();
            assertThat(first.get("participation_id")).isEqualTo(String.valueOf(completed.check().getParticipationId()));
            assertThat(first.get("student_login")).isEqualTo(login("student1"));
            assertThat(first.get("student_name")).isEqualTo(login("student1") + "First " + login("student1") + "Last");
            assertThat(first.get("tutorial_group")).isEqualTo("Metis Tuesday");
            assertThat(first.get("check_status")).isEqualTo("COMPLETED");
            assertThat(first.get("failure_reason")).isEmpty();
            assertThat(first.get("commit_hash")).isEqualTo(COMMIT_HASH);
            assertThat(first.get("llm_model")).isEqualTo(MetisUtilService.TEST_LLM_MODEL);
            assertThat(first.get("prompt_version")).isEqualTo(MetisUtilService.TEST_PROMPT_VERSION);
            assertThat(first.get("generated_at")).isEqualTo("2024-07-01T12:00:00Z");
            assertThat(first.get("started_at")).isEqualTo("2024-07-01T12:00:00Z");
            assertThat(first.get("completed_at")).isEqualTo("2024-07-01T12:00:00Z");
            assertThat(first.get("question_order")).isEqualTo("1");
            assertThat(first.get("question_kind")).isEqualTo("MAIN");
            assertThat(first.get("parent_question_order")).isEmpty();
            assertThat(first.get("focus_file_path")).isEqualTo("src/Sorter1.java");
            assertThat(first.get("focus_file_changed_lines")).isEqualTo("11");
            assertThat(first.get("question_text")).isEqualTo(completed.first().getQuestionText());
            assertThat(first.get("time_limit_seconds")).isEqualTo("120");
            assertThat(first.get("regeneration_count")).isEqualTo("0");
            assertThat(first.get("approved_at")).isEqualTo("2024-06-30T14:00:00Z");
            assertThat(first.get("shown_at")).isEqualTo("2024-07-01T11:30:00Z");
            assertThat(first.get("answered_at")).isEqualTo("2024-07-01T11:31:15Z");
            assertThat(first.get("answer_seconds")).isEqualTo("75");
            assertThat(first.get("answer_text")).isEqualTo("It copies the input.");
            assertThat(first.get("llm_score")).isEqualTo("2");
            assertThat(first.get("llm_rationale")).isEqualTo("Vague about why the input is copied.");
            assertThat(first.get("llm_rated_at")).isEqualTo("2024-07-01T11:31:20Z");

            CSVRecord followUp = lines.records().get(1);
            assertThat(followUp.get("participation_id")).isEqualTo(String.valueOf(completed.check().getParticipationId()));
            assertThat(followUp.get("check_status")).isEqualTo("COMPLETED");
            assertThat(followUp.get("question_order")).isEqualTo("3");
            assertThat(followUp.get("question_kind")).isEqualTo("FOLLOW_UP");
            assertThat(followUp.get("parent_question_order")).isEqualTo("1");
            assertThat(followUp.get("focus_file_path")).isEmpty();
            assertThat(followUp.get("focus_file_changed_lines")).isEmpty();
            assertThat(followUp.get("approved_at")).isEmpty();
            assertThat(followUp.get("answer_seconds")).isEqualTo("40");
            assertThat(followUp.get("llm_score")).isEqualTo("4");

            CSVRecord second = lines.records().get(2);
            assertThat(second.get("question_order")).isEqualTo("2");
            assertThat(second.get("question_kind")).isEqualTo("MAIN");
            assertThat(second.get("answer_seconds")).isEqualTo("90");

            CSVRecord skippedLine = lines.records().get(3);
            assertThat(skippedLine.get("participation_id")).isEqualTo(String.valueOf(skipped.getParticipationId()));
            assertThat(skippedLine.get("student_login")).isEqualTo(login("student2"));
            assertThat(skippedLine.get("tutorial_group")).isEmpty();
            assertThat(skippedLine.get("check_status")).isEqualTo("SKIPPED");
            assertThat(skippedLine.get("failure_reason")).isEqualTo(MetisFailureReason.NO_SUBMISSION.name());
            assertThat(skippedLine.get("commit_hash")).isEmpty();
            assertThat(QUESTION_COLUMNS).allSatisfy(column -> assertThat(skippedLine.get(column)).as(column).isEmpty());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldEscapeTextCellsThatASpreadsheetWouldReadAsAFormula() throws Exception {
            rename("student1", "=HYPERLINK(\"http://example.com\")", "Doe");
            tutorialGroup("@Metis group", Set.of(user("student1")));
            MetisCheck check = check("student1", MetisCheckStatus.COMPLETED);
            // Only the server writes the model, but every text column is protected alike
            check.setLlmModel("-model");
            check = checkRepository.save(check);
            MetisQuestion first = update(mainQuestion(check, 1), question -> {
                question.setQuestionText("+ What does the loop add?");
                // A student may name a file like this
                question.setFocusFilePath("=1+1.java");
            });
            rated(answered(first, THREAD_START, THREAD_START.plusSeconds(30), "-1 when the list is empty"), 3, THREAD_START.plusSeconds(35));
            update(first, question -> question.setLlmRationale("\tIndented rationale"));
            MetisQuestion second = update(mainQuestion(check, 2), question -> question.setQuestionText("\rWhy is the copy needed?"));
            answered(second, THREAD_START.plusSeconds(35), THREAD_START.plusSeconds(70), "@SUM(A1:A2)");
            MetisQuestion third = mainQuestion(check, 3);
            answered(third, THREAD_START.plusSeconds(70), THREAD_START.plusSeconds(90), "a = b, because + binds weaker");

            List<CSVRecord> records = lines(export()).records();

            assertThat(records.getFirst().get("student_name")).isEqualTo("'=HYPERLINK(\"http://example.com\") Doe");
            assertThat(records.getFirst().get("tutorial_group")).isEqualTo("'@Metis group");
            assertThat(records.getFirst().get("llm_model")).isEqualTo("'-model");
            assertThat(records.getFirst().get("focus_file_path")).isEqualTo("'=1+1.java");
            assertThat(records.getFirst().get("question_text")).isEqualTo("'+ What does the loop add?");
            assertThat(records.getFirst().get("answer_text")).isEqualTo("'-1 when the list is empty");
            assertThat(records.getFirst().get("llm_rationale")).isEqualTo("'\tIndented rationale");
            assertThat(records.get(1).get("question_text")).isEqualTo("'\rWhy is the copy needed?");
            assertThat(records.get(1).get("answer_text")).isEqualTo("'@SUM(A1:A2)");
            // Only the first character counts
            assertThat(records.get(2).get("answer_text")).isEqualTo("a = b, because + binds weaker");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldNameTheFileAfterTheShortNameReducedToTheCharactersOfAFileName() throws Exception {
            setShortName("Sort ü/1.v2");
            assertThat(fileNameOf(export())).isEqualTo("Sort1.v2-comprehension-check.csv");

            // Nothing of this short name is left, so the exercise id stands in for it
            setShortName("ü ü");
            assertThat(fileNameOf(export())).isEqualTo("exercise-" + exerciseId + "-comprehension-check.csv");
        }
    }

    @Nested
    class GenerateNow {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldCreateTheMissingChecksAndPutTheFailedOnesBackIntoTheQueue() throws Exception {
            enable();
            ProgrammingExerciseStudentParticipation withoutCheck = participation("student1");
            MetisCheck failed = unavailableCheck("student2", MetisCheckStatus.FAILED, MetisFailureReason.LLM_UNAVAILABLE);
            MetisCheck withoutSubmission = unavailableCheck("student3", MetisCheckStatus.SKIPPED, MetisFailureReason.NO_SUBMISSION);

            MetisGenerateResultDTO result = request.postWithResponseBody(generatePath(), null, MetisGenerateResultDTO.class, HttpStatus.OK);

            assertThat(result).isEqualTo(new MetisGenerateResultDTO(1, 1));
            assertThat(checkRepository.findByParticipationId(withoutCheck.getId())).hasValueSatisfying(created -> {
                assertThat(created.getStatus()).isEqualTo(MetisCheckStatus.PENDING);
                assertThat(created.getFailureReason()).isNull();
            });
            MetisCheck requeued = reload(failed);
            assertThat(requeued.getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(requeued.getFailureReason()).isNull();
            // Neither the submissions before the due date nor the code can change after it, so this check stays skipped
            MetisCheck stillSkipped = reload(withoutSubmission);
            assertThat(stillSkipped.getStatus()).isEqualTo(MetisCheckStatus.SKIPPED);
            assertThat(stillSkipped.getFailureReason()).isEqualTo(MetisFailureReason.NO_SUBMISSION.name());
            // The request node only creates and queues checks; the scheduling node generates them
            verifyNoInteractions(metisLlmService);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldKeepANewCheckOfAStudentWithoutConsentSkippedAndCountItOnce() throws Exception {
            enable();
            ProgrammingExerciseStudentParticipation consenting = participation("student1");
            // Skipped for missing consent before; the student has agreed since
            MetisCheck skippedBefore = unavailableCheck("student2", MetisCheckStatus.SKIPPED, MetisFailureReason.NO_AI_CONSENT);
            userUtilService.clearAiSelectionDecision(user("student3"));
            ProgrammingExerciseStudentParticipation withoutConsent = participation("student3");

            MetisGenerateResultDTO result = request.postWithResponseBody(generatePath(), null, MetisGenerateResultDTO.class, HttpStatus.OK);

            // Created: the checks of student1 and student3. Queued again: only the check that existed before the request
            assertThat(result).isEqualTo(new MetisGenerateResultDTO(2, 1));
            assertThat(checkRepository.findByParticipationId(consenting.getId())).hasValueSatisfying(created -> {
                assertThat(created.getStatus()).isEqualTo(MetisCheckStatus.PENDING);
                assertThat(created.getFailureReason()).isNull();
            });
            assertThat(checkRepository.findByParticipationId(withoutConsent.getId())).hasValueSatisfying(created -> {
                assertThat(created.getStatus()).isEqualTo(MetisCheckStatus.SKIPPED);
                assertThat(created.getFailureReason()).isEqualTo(MetisFailureReason.NO_AI_CONSENT.name());
            });
            MetisCheck requeued = reload(skippedBefore);
            assertThat(requeued.getStatus()).isEqualTo(MetisCheckStatus.PENDING);
            assertThat(requeued.getFailureReason()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldRefuseToGenerateWhileTheCheckIsNotEnabled() throws Exception {
            ProgrammingExerciseStudentParticipation withoutCheck = participation("student1");

            request.postAndExpectError(generatePath(), null, HttpStatus.CONFLICT, "metis.notEnabled");

            assertThat(checkRepository.findByParticipationId(withoutCheck.getId())).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldForbidAnEditorToGenerate() throws Exception {
            enable();
            ProgrammingExerciseStudentParticipation withoutCheck = participation("student1");

            request.postWithResponseBody(generatePath(), null, MetisGenerateResultDTO.class, HttpStatus.FORBIDDEN);

            assertThat(checkRepository.findByParticipationId(withoutCheck.getId())).isEmpty();
        }
    }

    @Nested
    class Authorization {

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldLetATutorReadTheResultsButNotGenerate() throws Exception {
            enable();
            check("student1", MetisCheckStatus.READY);
            ProgrammingExerciseStudentParticipation withoutCheck = participation("student2");

            assertThat(getDashboard().rows()).hasSize(1);
            assertThat(lines(export()).records()).hasSize(1);
            request.postWithResponseBody(generatePath(), null, MetisGenerateResultDTO.class, HttpStatus.FORBIDDEN);

            assertThat(checkRepository.findByParticipationId(withoutCheck.getId())).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldForbidAStudentEveryEndpoint() throws Exception {
            enable();
            check("student1", MetisCheckStatus.READY);
            ProgrammingExerciseStudentParticipation withoutCheck = participation("student2");

            request.get(dashboardPath(), HttpStatus.FORBIDDEN, MetisDashboardDTO.class);
            request.get(exportPath(), HttpStatus.FORBIDDEN, byte[].class);
            request.postWithResponseBody(generatePath(), null, MetisGenerateResultDTO.class, HttpStatus.FORBIDDEN);

            assertThat(checkRepository.findByParticipationId(withoutCheck.getId())).isEmpty();
        }
    }

    /**
     * A completed check, its main questions and the follow-up of its first main question, which was asked after the first answer and therefore comes after the
     * second main question in the order of the check.
     */
    private record CompletedCheck(MetisCheck check, MetisQuestion first, MetisQuestion followUp, MetisQuestion second) {
    }

    /**
     * The header and the lines of an export.
     */
    private record ExportedLines(List<String> header, List<CSVRecord> records) {
    }

    /**
     * A completed check of the student whose questions were all approved, shown, answered and rated: the first main question with 2, its follow-up with 4 and
     * the second main question with 5.
     */
    private CompletedCheck completedCheck(String userSuffix) {
        MetisCheck check = check(userSuffix, MetisCheckStatus.COMPLETED);
        MetisQuestion first = approved(mainQuestion(check, 1));
        MetisQuestion second = approved(mainQuestion(check, 2));
        first = rated(answered(first, THREAD_START, THREAD_START.plusSeconds(75), "It copies the input."), 2, THREAD_START.plusSeconds(80));
        MetisQuestion followUp = followUp(check, 3, first);
        followUp = rated(answered(followUp, THREAD_START.plusSeconds(80), THREAD_START.plusSeconds(120), "Sorting must not change the caller's array."), 4,
                THREAD_START.plusSeconds(125));
        second = rated(answered(second, THREAD_START.plusSeconds(125), THREAD_START.plusSeconds(215), "It swaps neighbours that are out of order."), 5,
                THREAD_START.plusSeconds(220));
        return new CompletedCheck(check, first, followUp, second);
    }

    private void enable() {
        metisUtilService.saveConfig(exerciseId, true, false, null, null);
        enabledExerciseIds.add(exerciseId);
    }

    private ProgrammingExerciseStudentParticipation participation(String userSuffix) {
        var participation = ParticipationFactory.generateIndividualProgrammingExerciseStudentParticipation(exercise, user(userSuffix));
        String repositoryName = (exercise.getProjectKey() + "-" + TEST_PREFIX + userSuffix).toLowerCase(Locale.ROOT);
        participation.setRepositoryUri("http://localhost/git/" + exercise.getProjectKey() + "/" + repositoryName + ".git");
        return programmingExerciseStudentParticipationRepository.save(participation);
    }

    private MetisCheck check(String userSuffix, MetisCheckStatus status) {
        return metisUtilService.saveGeneratedCheck(participation(userSuffix).getId(), status, COMMIT_HASH);
    }

    private MetisCheck unavailableCheck(String userSuffix, MetisCheckStatus status, MetisFailureReason reason) {
        MetisCheck check = metisUtilService.saveCheck(participation(userSuffix).getId(), status);
        check.setFailureReason(reason.name());
        return checkRepository.save(check);
    }

    private MetisQuestion mainQuestion(MetisCheck check, int order) {
        return metisUtilService.saveMainQuestion(check.getId(), order, "src/Sorter" + order + ".java", 10 + order, "What does part " + order + " of the sorter do?", 120);
    }

    private MetisQuestion followUp(MetisCheck check, int order, MetisQuestion mainQuestion) {
        return metisUtilService.saveFollowUp(check.getId(), order, mainQuestion.getId(), "Why is part " + mainQuestion.getQuestionOrder() + " needed?", 60);
    }

    private MetisQuestion approved(MetisQuestion question) {
        return update(question, stored -> stored.setApprovedAt(APPROVED_AT));
    }

    private MetisQuestion answered(MetisQuestion question, ZonedDateTime shownAt, ZonedDateTime answeredAt, String answer) {
        return update(question, stored -> {
            stored.setShownAt(shownAt);
            stored.setAnsweredAt(answeredAt);
            stored.setAnswerText(answer);
        });
    }

    private MetisQuestion rated(MetisQuestion question, int score, ZonedDateTime ratedAt) {
        String rationale = switch (score) {
            case 2 -> "Vague about why the input is copied.";
            case 4 -> "Names the aliasing problem.";
            case 5 -> "Correct and specific.";
            default -> "Rated " + score + ".";
        };
        return update(question, stored -> {
            stored.setLlmScore(score);
            stored.setLlmRationale(rationale);
            stored.setLlmRatedAt(ratedAt);
        });
    }

    private MetisQuestion update(MetisQuestion question, Consumer<MetisQuestion> change) {
        MetisQuestion stored = questionRepository.findByIdElseThrow(question.getId());
        change.accept(stored);
        return questionRepository.save(stored);
    }

    private MetisCheck reload(MetisCheck check) {
        return checkRepository.findByIdElseThrow(check.getId());
    }

    private TutorialGroup tutorialGroup(String title, Set<User> students) {
        return tutorialGroupUtilService.createAndSaveTutorialGroup(course.getId(), title, "", 10, false, "Garching", "English", user("tutor1"), students);
    }

    private void rename(String userSuffix, @Nullable String firstName, @Nullable String lastName) {
        User student = user(userSuffix);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        userTestRepository.save(student);
    }

    private void setShortName(String shortName) {
        Exercise stored = exerciseRepository.findByIdElseThrow(exerciseId);
        stored.setShortName(shortName);
        exerciseRepository.save(stored);
    }

    private User user(String suffix) {
        return userUtilService.getUserByLogin(login(suffix));
    }

    private static String login(String suffix) {
        return TEST_PREFIX + suffix;
    }

    private MetisDashboardDTO getDashboard() throws Exception {
        return request.get(dashboardPath(), HttpStatus.OK, MetisDashboardDTO.class);
    }

    private MockHttpServletResponse export() throws Exception {
        MockHttpServletResponse response = request.performMvcRequest(MockMvcRequestBuilders.get(new URI(exportPath()))).andExpect(status().isOk()).andReturn().getResponse();
        request.restoreSecurityContext();
        return response;
    }

    /**
     * The header and the lines of an export, read with the byte order mark removed.
     */
    private static ExportedLines lines(MockHttpServletResponse response) throws IOException {
        String csv = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(new StringReader(csv.substring(1)))) {
            return new ExportedLines(parser.getHeaderNames(), parser.getRecords());
        }
    }

    private static String fileNameOf(MockHttpServletResponse response) {
        return ContentDisposition.parse(response.getHeader(HttpHeaders.CONTENT_DISPOSITION)).getFilename();
    }

    private static void freezeClockAt(ZonedDateTime time) {
        TimeUtil.setClock(Clock.fixed(time.toInstant(), ZoneOffset.UTC));
    }

    private String exercisePath() {
        return "/api/metis/exercises/" + exerciseId;
    }

    private String dashboardPath() {
        return exercisePath() + "/dashboard";
    }

    private String exportPath() {
        return exercisePath() + "/export";
    }

    private String generatePath() {
        return exercisePath() + "/checks/generate";
    }
}
