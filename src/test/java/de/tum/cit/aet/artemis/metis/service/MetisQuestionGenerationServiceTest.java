package de.tum.cit.aet.artemis.metis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.metis.config.MetisProperties;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.domain.MetisFailureReason;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseContextDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisGenerationTargetDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;
import de.tum.cit.aet.artemis.metis.service.MetisDiffService.FileChange;
import de.tum.cit.aet.artemis.metis.service.MetisDiffService.OwnCodeDiff;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService.Mode;
import de.tum.cit.aet.artemis.metis.service.MetisLlmService.Pipeline;
import de.tum.cit.aet.artemis.metis.service.MetisQuestionGenerationService.Outcome;
import de.tum.cit.aet.artemis.metis.service.MetisWindowService.EffectiveWindow;
import de.tum.cit.aet.artemis.metis.util.MetisLlmTestResponses;
import de.tum.cit.aet.artemis.metis.util.MetisTestProperties;

/**
 * Unit tests of the generation of a claimed check where the integration test cannot steer the input: the budget of the generation prompt for many changed
 * files and for a small configured budget, a database that refuses the generated questions, and the clean-up of the question texts. The diff, the
 * repositories and the transport are mocks; the prompt templates are the real ones.
 */
class MetisQuestionGenerationServiceTest {

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-03-02T10:00:00Z");

    /** The effective due date of the student whose check is generated. */
    private static final ZonedDateTime DUE = NOW.minusHours(2);

    private static final long COURSE_ID = 1L;

    private static final long EXERCISE_ID = 2L;

    private static final long STUDENT_ID = 3L;

    private static final long CHECK_ID = 4L;

    private static final long PARTICIPATION_ID = 5L;

    private static final String REPOSITORY_URI = "http://localhost/git/METIS/metis-student1.git";

    private static final String COMMIT_HASH = "c".repeat(40);

    /** The line of the user prompt that the list of changed files follows. */
    private static final String CHANGED_FILES_HEADING = "Files the student changed (path: lines added, lines removed):\n";

    private static final Pattern MORE_CHANGED_FILES = Pattern.compile("^\\.\\.\\. and (\\d+) more changed files$");

    private final MetisCheckRepository checkRepository = mock(MetisCheckRepository.class);

    private final MetisQuestionRepository questionRepository = mock(MetisQuestionRepository.class);

    private final MetisExerciseConfigRepository configRepository = mock(MetisExerciseConfigRepository.class);

    private final MetisWindowService windowService = mock(MetisWindowService.class);

    private final MetisAiConsentService consentService = mock(MetisAiConsentService.class);

    private final MetisDiffService diffService = mock(MetisDiffService.class);

    private final MetisLlmService llmService = mock(MetisLlmService.class);

    private final UserTestRepository userRepository = mock(UserTestRepository.class);

    @BeforeEach
    void setUp() {
        TimeUtil.setClock(Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
        MetisExerciseConfig config = new MetisExerciseConfig();
        config.setExerciseId(EXERCISE_ID);
        config.setEnabled(true);
        var target = new MetisGenerationTargetDTO(CHECK_ID, MetisCheckStatus.GENERATING, null, PARTICIPATION_ID, STUDENT_ID, REPOSITORY_URI, DUE, EXERCISE_ID, COURSE_ID);
        when(checkRepository.findGenerationTarget(CHECK_ID)).thenReturn(Optional.of(target));
        when(configRepository.findById(EXERCISE_ID)).thenReturn(Optional.of(config));
        when(windowService.resolveForStudent(any(), any(), anyLong(), anyLong())).thenReturn(new EffectiveWindow(null, null, null));
        when(consentService.hasConsent(STUDENT_ID)).thenReturn(true);
        when(checkRepository.findLatestCommitHashesUpTo(eq(PARTICIPATION_ID), any(), any())).thenReturn(List.of(COMMIT_HASH));
        when(configRepository.findExerciseContext(EXERCISE_ID)).thenReturn(Optional.of(new MetisExerciseContextDTO(EXERCISE_ID, "Sorting", "Sort an array of numbers.", null)));
        when(checkRepository.markGenerated(eq(CHECK_ID), any(), any(), any(), any(), any())).thenReturn(1);
        when(llmService.complete(eq(Pipeline.QUESTION_GENERATION), eq(Mode.BATCH), anyString(), anyString(), any()))
                .thenReturn(MetisLlmTestResponses.completion(MetisLlmTestResponses.questionsJson(List.of("First?", "Second?", "Third?"), List.of(120, 120, 120))));
    }

    @AfterEach
    void tearDown() {
        TimeUtil.resetClock();
    }

    @Nested
    class PromptBudget {

        @Test
        void shouldListTheFocusFilesAndTheLargestChangesWhenTheChangedFilesExceedTheirShareOfTheBudget() {
            List<FileChange> focusFiles = List.of(file("src/A.java", 20, 400), file("src/B.java", 20, 400), file("src/C.java", 20, 400));
            List<FileChange> changedFiles = new ArrayList<>(focusFiles);
            for (int number = 1; number <= 5; number++) {
                changedFiles.add(file("src/big/Big" + number + ".java", 15, 400));
            }
            for (int number = 1; number <= 600; number++) {
                changedFiles.add(file(String.format(Locale.ROOT, "src/generated/File%03d.java", number), 1, 100));
            }
            stubDiff(changedFiles, focusFiles);

            assertThat(service(MetisTestProperties.defaults()).generate(CHECK_ID, false)).isEqualTo(Outcome.GENERATED);

            String prompt = capturedUserPrompt();
            String changedFilesSection = prompt.substring(prompt.indexOf(CHANGED_FILES_HEADING) + CHANGED_FILES_HEADING.length(), prompt.indexOf("\n\n### Question 1"));
            // A quarter of the default budget of 60000 characters
            assertThat(changedFilesSection.length()).isLessThanOrEqualTo(15000);
            List<String> lines = List.of(changedFilesSection.split("\n"));
            Matcher more = MORE_CHANGED_FILES.matcher(lines.getLast());
            assertThat(more.matches()).isTrue();
            List<String> listed = lines.subList(0, lines.size() - 1);
            assertThat(listed.size() + Integer.parseInt(more.group(1))).isEqualTo(changedFiles.size());
            assertThat(listed).contains("src/A.java: +20 -0", "src/B.java: +20 -0", "src/C.java: +20 -0", "src/big/Big1.java: +15 -0", "src/big/Big5.java: +15 -0");
            assertThat(listed).isSorted();
            // The diffs of the focus files are whole
            assertThat(prompt).contains(focusFiles.getFirst().unifiedDiff(), focusFiles.get(1).unifiedDiff(), focusFiles.get(2).unifiedDiff());
        }

        @Test
        void shouldCutTheDiffsByTheSameShareButNoneBelowTheMinimum() {
            FileChange small = file("src/Small.java", 30, 1500);
            FileChange first = file("src/First.java", 200, 12000);
            FileChange second = file("src/Second.java", 200, 12000);
            List<FileChange> focusFiles = List.of(small, first, second);
            stubDiff(focusFiles, focusFiles);

            Outcome outcome = service(MetisTestProperties.with(Map.of("generation.max-prompt-characters", "20000"))).generate(CHECK_ID, false);

            assertThat(outcome).isEqualTo(Outcome.GENERATED);
            String prompt = capturedUserPrompt();
            assertThat(prompt.length()).isBetween(20000 - 10, 20000);
            // The full contents went first, then the diffs were cut
            assertThat(prompt.split(Pattern.quote(MetisQuestionGenerationService.CONTENT_OMITTED), -1)).hasSize(4);
            assertThat(prompt).contains(small.unifiedDiff()).doesNotContain(first.unifiedDiff(), second.unifiedDiff());
            int firstKept = keptDiffLength(prompt, first);
            assertThat(firstKept).isGreaterThan(MetisQuestionGenerationService.MIN_FOCUS_DIFF_CHARACTERS).isEqualTo(keptDiffLength(prompt, second));
        }

        @Test
        void shouldFailTheCheckWithoutAnLlmCallWhenTheBudgetLeavesNoRoomForTheCode() {
            List<FileChange> focusFiles = List.of(file("src/First.java", 200, 12000), file("src/Second.java", 200, 12000), file("src/Third.java", 200, 12000));
            stubDiff(focusFiles, focusFiles);

            Outcome outcome = service(MetisTestProperties.with(Map.of("generation.max-prompt-characters", "6000"))).generate(CHECK_ID, false);

            assertThat(outcome).isEqualTo(Outcome.FAILED);
            verify(checkRepository).markUnavailable(CHECK_ID, MetisCheckStatus.FAILED, MetisFailureReason.INTERNAL_ERROR.name(), COMMIT_HASH);
            verify(llmService, never()).complete(any(), any(), anyString(), anyString(), any());
        }

        /**
         * The length of the part of a diff that a prompt kept: from its start to the end of its diff block.
         */
        private static int keptDiffLength(String prompt, FileChange focusFile) {
            int start = prompt.indexOf(focusFile.unifiedDiff().substring(0, 200));
            assertThat(start).isNotNegative();
            return prompt.indexOf("\n--- END DIFF", start) - start;
        }
    }

    @Nested
    class Storing {

        @BeforeEach
        void setUpDiff() {
            List<FileChange> focusFiles = List.of(file("src/A.java", 20, 400), file("src/B.java", 20, 400), file("src/C.java", 20, 400));
            stubDiff(focusFiles, focusFiles);
            doThrow(new DataIntegrityViolationException("refused")).when(questionRepository).saveAll(any());
        }

        @Test
        void shouldFailTheCheckWhenTheDatabaseRefusesTheQuestionsThemselves() {
            when(questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(CHECK_ID)).thenReturn(List.of());

            assertThat(service(MetisTestProperties.defaults()).generate(CHECK_ID, false)).isEqualTo(Outcome.FAILED);

            verify(checkRepository).markUnavailable(CHECK_ID, MetisCheckStatus.FAILED, MetisFailureReason.INTERNAL_ERROR.name(), null);
            verify(checkRepository, never()).markGenerated(anyLong(), any(), any(), any(), any(), any());
        }

        @Test
        void shouldLeaveTheCheckToTheRunThatStoredItsQuestionsFirst() {
            when(questionRepository.findAllByCheckIdOrderByQuestionOrderAsc(CHECK_ID)).thenReturn(List.of(new MetisQuestion()));

            assertThat(service(MetisTestProperties.defaults()).generate(CHECK_ID, false)).isEqualTo(Outcome.DEFERRED);

            verify(checkRepository, never()).markUnavailable(anyLong(), any(), any(), any());
            verify(checkRepository, never()).markGenerated(anyLong(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    class QuestionTexts {

        @Test
        void shouldRemoveControlCharactersButKeepLineBreaksAndTabs() {
            String text = " Why does a C string end with '" + Character.toString(0) + "'?" + Character.toString(7) + "\r\n\tExplain. ";

            assertThat(MetisQuestionGenerationService.questionText(text)).isEqualTo("Why does a C string end with ''?\r\n\tExplain.");
        }

        @Test
        void shouldTreatAQuestionOfControlCharactersOnlyAsUnusable() {
            String controlCharactersOnly = Character.toString(0) + Character.toString(27) + " ";
            String output = MetisLlmTestResponses.questionsJson(List.of("First?", controlCharactersOnly), List.of(120, 120));

            assertThat(service(MetisTestProperties.defaults()).parseQuestions(output, 2)).isEmpty();
        }
    }

    private MetisQuestionGenerationService service(MetisProperties properties) {
        return new MetisQuestionGenerationService(properties, checkRepository, questionRepository, configRepository, windowService, consentService, diffService,
                new MetisPromptTemplateService(), llmService, userRepository);
    }

    private void stubDiff(List<FileChange> changedFiles, List<FileChange> focusCandidates) {
        List<FileChange> sortedByPath = changedFiles.stream().sorted(Comparator.comparing(FileChange::path)).toList();
        when(diffService.computeOwnCodeDiff(REPOSITORY_URI, COMMIT_HASH)).thenReturn(new OwnCodeDiff("b".repeat(40), COMMIT_HASH, true, sortedByPath, focusCandidates));
    }

    private String capturedUserPrompt() {
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(llmService).complete(eq(Pipeline.QUESTION_GENERATION), eq(Mode.BATCH), anyString(), userPrompt.capture(), any());
        return userPrompt.getValue();
    }

    /**
     * A file the student added, with as many lines as it has changed lines and a diff of about the given length. Its content is about as long as its diff, so
     * that a prompt over the budget leaves the content out first.
     */
    private static FileChange file(String path, int addedLines, int diffCharacters) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        // Each line of the diff is one character longer than the line of the file, for its plus sign; no line ends with whitespace, which a prompt strips
        int lineLength = Math.max(1, diffCharacters / addedLines - 1);
        List<String> lines = new ArrayList<>(addedLines);
        for (int line = 1; line <= addedLines; line++) {
            String start = String.format(Locale.ROOT, "// %s line %d:", name, line);
            lines.add(start + "x".repeat(Math.max(0, lineLength - start.length())));
        }
        String content = String.join("\n", lines) + "\n";
        String diff = String.format(Locale.ROOT, "@@ -0,0 +1,%d @@\n", addedLines) + String.join("\n", lines.stream().map(line -> "+" + line).toList());
        return new FileChange(path, addedLines, 0, content, diff);
    }
}
