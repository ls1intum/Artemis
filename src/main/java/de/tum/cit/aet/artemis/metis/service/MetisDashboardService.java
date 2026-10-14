package de.tum.cit.aet.artemis.metis.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.domain.MetisGroupWindow;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.dto.MetisCheckDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisDashboardDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisDashboardRowDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisDashboardSummaryDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseFactsDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisParticipationRowDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisQuestionDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisGroupWindowRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;

/**
 * The results of the comprehension checks of one exercise as staff see them: the dashboard with its summary and the export of every question and answer as
 * CSV.
 * <p>
 * The dashboard has one row per check, ordered by the student's login: the student's name and login, the tutorial group shown for the student (the group of
 * the student's window, else the alphabetically first group, chosen like the student's own window), the check with the snapshot of its generation, and its
 * questions in thread order. A participation without a check has no row; the poller creates the checks of all eligible participations shortly after their due
 * date. Questions are listed only for checks whose questions belong to the current generation (in review, ready, in progress or completed): a check that was
 * skipped, failed or put back into the queue may still hold the questions of an earlier run, which the next generation deletes.
 * <p>
 * The summary is computed from the rows with the rules of the client's review page, so that the tiles, the review page and the table never disagree. The
 * dashboard and the export read everything with the same six statements, however many students the exercise has.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Service
public class MetisDashboardService {

    /** The average score is rounded to one decimal, as the client rounds it. */
    private static final double ONE_DECIMAL = 10.0;

    /** The states of a check whose questions belong to its current generation; the questions of any other state are leftovers of an earlier run. */
    private static final Set<MetisCheckStatus> STATES_WITH_QUESTIONS = Set.of(MetisCheckStatus.IN_REVIEW, MetisCheckStatus.READY, MetisCheckStatus.IN_PROGRESS,
            MetisCheckStatus.COMPLETED);

    /** The states of a check its student has not opened yet. */
    private static final Set<MetisCheckStatus> NOT_STARTED_STATES = Set.of(MetisCheckStatus.PENDING, MetisCheckStatus.GENERATING, MetisCheckStatus.IN_REVIEW,
            MetisCheckStatus.READY);

    /** The states of a check that has no questions for its student and therefore nothing to review or rate. */
    private static final Set<MetisCheckStatus> SKIPPED_OR_FAILED_STATES = Set.of(MetisCheckStatus.SKIPPED, MetisCheckStatus.FAILED);

    /** The first columns of the export, which describe the check and repeat on every line of it. */
    private static final List<String> CHECK_COLUMNS = List.of("participation_id", "student_login", "student_name", "tutorial_group", "check_status", "failure_reason",
            "commit_hash", "llm_model", "prompt_version", "generated_at", "started_at", "completed_at");

    /** The last columns of the export, which describe the question of a line; a check without questions leaves them empty. */
    private static final List<String> QUESTION_COLUMNS = List.of("question_order", "question_kind", "parent_question_order", "focus_file_path", "focus_file_changed_lines",
            "question_text", "time_limit_seconds", "regeneration_count", "approved_at", "shown_at", "answered_at", "answer_seconds", "answer_text", "llm_score", "llm_rationale",
            "llm_rated_at");

    /** The header line of the export, the check columns followed by the question columns. */
    private static final String[] CSV_HEADER = Stream.concat(CHECK_COLUMNS.stream(), QUESTION_COLUMNS.stream()).toArray(String[]::new);

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder().setHeader(CSV_HEADER).get();

    /** Makes a spreadsheet such as Excel read the export as UTF-8. */
    private static final char BYTE_ORDER_MARK = '\uFEFF';

    /** The first characters with which a spreadsheet reads a cell as a formula. */
    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    /** The kind of a main question in the export. */
    private static final String MAIN = "MAIN";

    /** The kind of a follow-up question in the export. */
    private static final String FOLLOW_UP = "FOLLOW_UP";

    /** The characters a file name of the export must not contain: everything but letters, digits, dots, underscores and hyphens. */
    private static final Pattern UNSAFE_FILE_NAME_CHARACTERS = Pattern.compile("[^A-Za-z0-9._-]");

    private static final String FILE_NAME_SUFFIX = "-comprehension-check.csv";

    private final MetisExerciseConfigRepository configRepository;

    private final MetisGroupWindowRepository groupWindowRepository;

    private final MetisCheckRepository checkRepository;

    private final MetisQuestionRepository questionRepository;

    private final MetisWindowService windowService;

    public MetisDashboardService(MetisExerciseConfigRepository configRepository, MetisGroupWindowRepository groupWindowRepository, MetisCheckRepository checkRepository,
            MetisQuestionRepository questionRepository, MetisWindowService windowService) {
        this.configRepository = configRepository;
        this.groupWindowRepository = groupWindowRepository;
        this.checkRepository = checkRepository;
        this.questionRepository = questionRepository;
        this.windowService = windowService;
    }

    /**
     * The export of an exercise as a file.
     *
     * @param fileName the name of the file, made of letters, digits, dots, underscores and hyphens only
     * @param content  the CSV encoded as UTF-8, starting with a byte order mark
     */
    public record CsvExport(String fileName, byte[] content) {
    }

    /**
     * The dashboard of an exercise: one row per check with its student, tutorial group and questions, and the summary of all rows.
     *
     * @param exerciseId the exercise
     * @return the summary and the rows, ordered by the student's login; no rows while the exercise has no check
     */
    public MetisDashboardDTO getDashboard(long exerciseId) {
        DashboardData data = load(exerciseId);
        return new MetisDashboardDTO(summarize(data.rows(), data.windowEnd()), data.rows());
    }

    /**
     * Exports the dashboard of an exercise as CSV, one line per question in thread order, with the columns of the check repeated on every line. A check without
     * questions, for example a skipped one, has one line whose question columns are empty. Times are written in ISO-8601 in UTC, and the answer time is the whole
     * seconds between showing and answering the question.
     * <p>
     * The file starts with a byte order mark, so that a spreadsheet reads it as UTF-8. A text cell that a spreadsheet would read as a formula, because it starts
     * with {@code =}, {@code +}, {@code -}, {@code @}, a tab or a carriage return, gets a leading apostrophe, so that opening the export never evaluates what a
     * student or the model wrote.
     *
     * @param exerciseId the exercise
     * @return the file name, the short name of the exercise (or {@code exercise-} and its id without one) followed by {@code -comprehension-check.csv}, and the
     *         content
     */
    public CsvExport exportCsv(long exerciseId) {
        DashboardData data = load(exerciseId);
        StringBuilder csv = new StringBuilder().append(BYTE_ORDER_MARK);
        try (CSVPrinter printer = new CSVPrinter(csv, CSV_FORMAT)) {
            for (MetisDashboardRowDTO row : data.rows()) {
                printLines(printer, row);
            }
        }
        catch (IOException e) {
            // Appending to a string builder cannot fail; the printer declares the exception for writers that can
            throw new UncheckedIOException(e);
        }
        return new CsvExport(fileName(exerciseId, data.facts()), csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * What the dashboard and the export of an exercise are built from.
     *
     * @param facts     the facts about the exercise, or null when it is no programming exercise
     * @param rows      one row per check, ordered by the student's login
     * @param windowEnd the latest end of any window of the exercise, or null when no window has an end
     */
    private record DashboardData(@Nullable MetisExerciseFactsDTO facts, List<MetisDashboardRowDTO> rows, @Nullable ZonedDateTime windowEnd) {
    }

    /**
     * Reads everything the dashboard of an exercise shows with six statements, however many students it has: the configuration, the facts about the exercise,
     * the windows of the tutorial groups, the checks with their students, all questions of the exercise, and the registrations in the tutorial groups of the
     * course (left out for an exercise without a course).
     */
    private DashboardData load(long exerciseId) {
        MetisExerciseConfig config = configRepository.findById(exerciseId).orElse(null);
        MetisExerciseFactsDTO facts = configRepository.findExerciseFacts(exerciseId).orElse(null);
        List<MetisGroupWindow> groupWindows = groupWindowRepository.findAllByExerciseId(exerciseId);
        List<MetisParticipationRowDTO> checks = checkRepository.findDashboardRows(exerciseId);
        Map<Long, List<MetisQuestion>> questionsByCheck = questionRepository.findAllByExerciseId(exerciseId).stream().collect(Collectors.groupingBy(MetisQuestion::getCheckId));
        Long courseId = facts != null ? facts.courseId() : null;
        Map<Long, String> groupTitles = windowService.tutorialGroupTitlesByStudent(courseId, groupWindows);
        List<MetisDashboardRowDTO> rows = checks.stream().map(check -> toRow(check, questionsByCheck, groupTitles)).toList();
        return new DashboardData(facts, rows, MetisWindowService.latestWindowEnd(config, groupWindows));
    }

    /**
     * One row of the dashboard. The name of the student falls back to the login when the account has no name.
     */
    private static MetisDashboardRowDTO toRow(MetisParticipationRowDTO check, Map<Long, List<MetisQuestion>> questionsByCheck, Map<Long, String> groupTitles) {
        List<MetisQuestionDTO> questions = List.of();
        if (STATES_WITH_QUESTIONS.contains(check.status())) {
            List<MetisQuestion> storedQuestions = questionsByCheck.getOrDefault(check.checkId(), List.of());
            questions = MetisQuestionThreads.inThreadOrder(storedQuestions).stream().map(MetisQuestionDTO::ofStaff).toList();
        }
        String studentName = Objects.requireNonNullElse(User.displayName(check.firstName(), check.lastName(), check.studentLogin()), check.studentLogin());
        String groupTitle = groupTitles.get(check.studentId());
        return new MetisDashboardRowDTO(check.participationId(), studentName, check.studentLogin(), groupTitle, MetisCheckDTO.of(check), questions);
    }

    /**
     * The tiles of the dashboard, computed from its rows.
     */
    private static MetisDashboardSummaryDTO summarize(List<MetisDashboardRowDTO> rows, @Nullable ZonedDateTime windowEnd) {
        int completed = countWithStatus(rows, Set.of(MetisCheckStatus.COMPLETED));
        int inProgress = countWithStatus(rows, Set.of(MetisCheckStatus.IN_PROGRESS));
        int notStarted = countWithStatus(rows, NOT_STARTED_STATES);
        int skippedOrFailed = countWithStatus(rows, SKIPPED_OR_FAILED_STATES);
        int awaitingReview = (int) rows.stream().filter(MetisDashboardService::isAwaitingReview).count();
        int followUps = (int) rows.stream().flatMap(row -> row.questions().stream()).filter(question -> question.parentQuestionId() != null).count();
        return new MetisDashboardSummaryDTO(rows.size(), completed, inProgress, notStarted, skippedOrFailed, awaitingReview, averageScore(rows), followUps, windowEnd);
    }

    private static int countWithStatus(List<MetisDashboardRowDTO> rows, Set<MetisCheckStatus> statuses) {
        return (int) rows.stream().filter(row -> statuses.contains(row.check().status())).count();
    }

    /**
     * Whether a row waits for the review, by the rule of the review page: only a check in review does, until it has main questions and all of them are approved.
     * Without a review the questions are released at once, a check the student has started is past its review, and a check without questions yet, skipped or
     * failed has nothing to review. Follow-ups are never reviewed, so they do not count.
     */
    private static boolean isAwaitingReview(MetisDashboardRowDTO row) {
        if (row.check().status() != MetisCheckStatus.IN_REVIEW) {
            return false;
        }
        List<MetisQuestionDTO> mainQuestions = row.questions().stream().filter(question -> question.parentQuestionId() == null).toList();
        return mainQuestions.isEmpty() || mainQuestions.stream().anyMatch(question -> question.approvedAt() == null);
    }

    /**
     * The mean score of the main questions of the completed checks, the first answer to each question, rounded to one decimal. Follow-ups and the checks that
     * are still in progress do not count.
     *
     * @return the rounded mean, or null when no main question of a completed check has a score
     */
    @Nullable
    private static Double averageScore(List<MetisDashboardRowDTO> rows) {
        List<Integer> scores = new ArrayList<>();
        for (MetisDashboardRowDTO row : rows) {
            if (row.check().status() != MetisCheckStatus.COMPLETED) {
                continue;
            }
            for (MetisQuestionDTO question : row.questions()) {
                if (question.parentQuestionId() == null && question.llmScore() != null) {
                    scores.add(question.llmScore());
                }
            }
        }
        OptionalDouble mean = scores.stream().mapToInt(Integer::intValue).average();
        if (mean.isEmpty()) {
            return null;
        }
        return Math.round(mean.getAsDouble() * ONE_DECIMAL) / ONE_DECIMAL;
    }

    /**
     * Prints the lines of one row of the dashboard: one per question, or one with empty question columns for a check without questions.
     */
    private static void printLines(CSVPrinter printer, MetisDashboardRowDTO row) throws IOException {
        List<Object> checkCells = checkCells(row);
        if (row.questions().isEmpty()) {
            List<Object> cells = new ArrayList<>(checkCells);
            cells.addAll(Collections.nCopies(QUESTION_COLUMNS.size(), null));
            printLine(printer, cells);
            return;
        }
        Map<Long, Integer> orderById = row.questions().stream().collect(Collectors.toMap(MetisQuestionDTO::id, MetisQuestionDTO::questionOrder));
        for (MetisQuestionDTO question : row.questions()) {
            List<Object> cells = new ArrayList<>(checkCells);
            cells.addAll(questionCells(question, orderById));
            printLine(printer, cells);
        }
    }

    /**
     * Prints one line of the export. Every text cell passes through {@link #cell(Object)}, whichever column it belongs to, so that no column can be left out of
     * the protection against formulas.
     */
    private static void printLine(CSVPrinter printer, List<Object> cells) throws IOException {
        printer.printRecord(cells.stream().map(MetisDashboardService::cell).toList());
    }

    /**
     * The cells of the columns that describe the check of a row; a null cell is printed empty.
     */
    private static List<Object> checkCells(MetisDashboardRowDTO row) {
        MetisCheckDTO check = row.check();
        return Arrays.asList(row.participationId(), row.studentLogin(), row.studentName(), row.tutorialGroupTitle(), check.status().name(), check.failureReason(),
                check.commitHash(), check.llmModel(), check.promptVersion(), timestamp(check.generatedAt()), timestamp(check.startedAt()), timestamp(check.completedAt()));
    }

    /**
     * The cells of the columns that describe one question; a null cell is printed empty.
     *
     * @param question  the question of the line
     * @param orderById the order of every question of the same check, by id, for the order of the main question a follow-up belongs to
     */
    private static List<Object> questionCells(MetisQuestionDTO question, Map<Long, Integer> orderById) {
        Long parentId = question.parentQuestionId();
        String kind = parentId != null ? FOLLOW_UP : MAIN;
        Integer parentOrder = parentId != null ? orderById.get(parentId) : null;
        return Arrays.asList(question.questionOrder(), kind, parentOrder, question.focusFilePath(), question.focusFileChangedLines(), question.questionText(),
                question.timeLimitSeconds(), question.regenerationCount(), timestamp(question.approvedAt()), timestamp(question.shownAt()), timestamp(question.answeredAt()),
                answerSeconds(question), question.answerText(), question.llmScore(), question.llmRationale(), timestamp(question.llmRatedAt()));
    }

    /**
     * A cell as it is printed. Text that a spreadsheet would read as a formula gets a leading apostrophe, which the spreadsheet shows as text; numbers, other
     * text and null, which is printed empty, stay as they are.
     */
    @Nullable
    private static Object cell(@Nullable Object value) {
        if (value instanceof String text && !text.isEmpty() && FORMULA_TRIGGERS.indexOf(text.charAt(0)) >= 0) {
            return "'" + text;
        }
        return value;
    }

    /**
     * A time in ISO-8601 in UTC, for example {@code 2024-03-01T10:15:30Z}.
     */
    @Nullable
    private static String timestamp(@Nullable ZonedDateTime time) {
        return time != null ? time.toInstant().toString() : null;
    }

    /**
     * The whole seconds between showing a question and storing its answer, or null for a question that has not been answered.
     */
    @Nullable
    private static Long answerSeconds(MetisQuestionDTO question) {
        ZonedDateTime shownAt = question.shownAt();
        ZonedDateTime answeredAt = question.answeredAt();
        if (shownAt == null || answeredAt == null) {
            return null;
        }
        return Duration.between(shownAt, answeredAt).toSeconds();
    }

    /**
     * The file name of the export: the short name of the exercise, reduced to the characters a file name may safely contain, or {@code exercise-} followed by
     * the exercise id when no such character is left or the exercise has no short name.
     */
    private static String fileName(long exerciseId, @Nullable MetisExerciseFactsDTO facts) {
        String shortName = facts != null && facts.shortName() != null ? UNSAFE_FILE_NAME_CHARACTERS.matcher(facts.shortName()).replaceAll("") : "";
        String exercise = shortName.isEmpty() ? "exercise-" + exerciseId : shortName;
        return exercise + FILE_NAME_SUFFIX;
    }
}
