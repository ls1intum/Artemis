package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalMap;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettings;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizAnswerOptionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolCellCountDTO;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolItemRepository;
import de.tum.cit.aet.artemis.hyperion.service.HyperionQuizQuestionPoolBuildService.Cell;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

class HyperionQuizQuestionPoolBuildServiceTest {

    @Mock
    private HyperionQuizQuestionPoolMaterialService materialService;

    @Mock
    private CourseCompetencyApi courseCompetencyApi;

    @Mock
    private HyperionQuizQuestionFilterService filterService;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    @Mock
    private QuizQuestionPoolItemRepository itemRepository;

    @Mock
    private HyperionCourseSettingsService settingsService;

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private DistributedDataProvider distributedDataProvider;

    @Mock
    private ChatModel chatModel;

    private LocalMap<Long, String> leases;

    private LocalMap<Long, HyperionCourseSettings> buildSettings;

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        leases = new LocalMap<>();
        buildSettings = new LocalMap<>();
        lenient().when(distributedDataProvider.<Long, String>getMap(HyperionQuizQuestionPoolBuildService.BUILD_LEASES)).thenReturn(leases);
        lenient().when(distributedDataProvider.<Long, HyperionCourseSettings>getMap(HyperionQuizQuestionPoolBuildService.BUILD_SETTINGS)).thenReturn(buildSettings);
        lenient().when(distributedDataProvider.<Long, Boolean>getMap(HyperionQuizQuestionPoolBuildService.BUILD_CANCELLATIONS)).thenReturn(new LocalMap<>());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void planCells_splitsEachTypeOverTheDifficultiesInAFixedOrder() {
        HyperionCourseSettings settings = new HyperionCourseSettings(false, 21, true, 5, 1, 4, 20, 50, 30);

        Map<Cell, Integer> targets = HyperionQuizQuestionPoolBuildService.planCells(Map.of(7L, List.of(2L, 1L), 3L, List.of(5L)), QuizQuestionGenerationLanguage.DE, settings);

        List<Cell> cells = List.copyOf(targets.keySet());
        assertThat(cells).hasSize(18);
        assertThat(cells.getFirst()).isEqualTo(new Cell(3L, 5L, QuizQuestionGenerationLanguage.DE, QuizQuestionGenerationType.SINGLE_CHOICE, QuizQuestionDifficulty.EASY));
        assertThat(cells.get(3)).isEqualTo(new Cell(3L, 5L, QuizQuestionGenerationLanguage.DE, QuizQuestionGenerationType.MULTIPLE_CHOICE, QuizQuestionDifficulty.EASY));
        assertThat(cells.get(6)).isEqualTo(new Cell(7L, 1L, QuizQuestionGenerationLanguage.DE, QuizQuestionGenerationType.SINGLE_CHOICE, QuizQuestionDifficulty.EASY));
        assertThat(cells.getLast()).isEqualTo(new Cell(7L, 2L, QuizQuestionGenerationLanguage.DE, QuizQuestionGenerationType.MULTIPLE_CHOICE, QuizQuestionDifficulty.HARD));
        assertThat(List.copyOf(targets.values()).subList(0, 6)).containsExactly(4, 11, 6, 1, 3, 1);
        assertThat(targets.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(3 * 26);
    }

    @Test
    void planCells_leavesOutDisabledMultipleChoiceAndDifficultiesWithoutAQuestion() {
        HyperionCourseSettings settings = new HyperionCourseSettings(false, 1, false, 12, 1, 4, 20, 50, 30);

        Map<Cell, Integer> targets = HyperionQuizQuestionPoolBuildService.planCells(Map.of(7L, List.of(1L)), QuizQuestionGenerationLanguage.EN, settings);

        assertThat(targets).containsExactly(entry(new Cell(7L, 1L, QuizQuestionGenerationLanguage.EN, QuizQuestionGenerationType.SINGLE_CHOICE, QuizQuestionDifficulty.MEDIUM), 1));
    }

    @Test
    void planCells_isEmptyWithoutLectureUnits() {
        assertThat(HyperionQuizQuestionPoolBuildService.planCells(Map.of(), QuizQuestionGenerationLanguage.EN, HyperionCourseSettings.defaultSettings())).isEmpty();
    }

    @Test
    void splitByPercent_givesTheRemainderToTheLargestFractionsEarlierOnesFirst() {
        assertThat(HyperionQuizQuestionPoolBuildService.splitByPercent(21, new int[] { 20, 50, 30 })).containsExactly(4, 11, 6);
        assertThat(HyperionQuizQuestionPoolBuildService.splitByPercent(25, new int[] { 20, 50, 30 })).containsExactly(5, 13, 7);
        assertThat(HyperionQuizQuestionPoolBuildService.splitByPercent(12, new int[] { 20, 50, 30 })).containsExactly(2, 6, 4);
        assertThat(HyperionQuizQuestionPoolBuildService.splitByPercent(1, new int[] { 20, 50, 30 })).containsExactly(0, 1, 0);
        assertThat(HyperionQuizQuestionPoolBuildService.splitByPercent(7, new int[] { 0, 100, 0 })).containsExactly(0, 7, 0);
    }

    @Test
    void enqueue_topsEveryCellUpToItsTargetFromOneCountQuery() {
        HyperionQuizQuestionPoolBuildService service = service();
        Course course = new Course();
        course.setId(5L);
        Competency competency = new Competency();
        competency.setId(9L);
        TextUnit unit = new TextUnit();
        unit.setId(4L);
        when(courseCompetencyApi.findAllForCourse(5L)).thenReturn(Set.<CourseCompetency>of(competency));
        when(materialService.findGroundableUnits(List.of(9L))).thenReturn(Map.of(9L, List.<LectureUnit>of(unit)));
        when(settingsService.getSettings(5L)).thenReturn(new HyperionCourseSettings(false, 12, false, 12, 1, 4, 0, 100, 0));
        when(itemRepository.countByCellForCourse(5L)).thenReturn(
                List.of(new QuizQuestionPoolCellCountDTO(9L, 4L, QuizQuestionGenerationLanguage.EN, QuizQuestionGenerationType.SINGLE_CHOICE, QuizQuestionDifficulty.MEDIUM, 10)));

        int created = service.enqueue(course);

        ArgumentCaptor<List<QuizQuestionPoolItem>> saved = ArgumentCaptor.captor();
        verify(itemRepository).saveAll(saved.capture());
        assertThat(created).isEqualTo(2);
        assertThat(saved.getValue()).extracting(QuizQuestionPoolItem::getItemIndex).containsExactly(10, 11);
    }

    @Test
    void enqueue_keepsTheSettingsOfTheBuildUntilReleased() {
        HyperionQuizQuestionPoolBuildService service = service();
        Course course = new Course();
        course.setId(5L);
        HyperionCourseSettings settings = new HyperionCourseSettings(false, 3, true, 2, 1, 4, 20, 50, 30);
        when(courseCompetencyApi.findAllForCourse(5L)).thenReturn(Set.of());
        when(materialService.findGroundableUnits(List.of())).thenReturn(Map.of());
        when(settingsService.getSettings(5L)).thenReturn(settings);
        String leaseToken = service.tryStart(5L);

        service.enqueue(course);

        assertThat(service.settingsOfRunningBuild(5L)).isEqualTo(settings);
        assertThat(service.settingsOfRunningBuild(6L)).isNull();
        service.release(5L, leaseToken);
        assertThat(service.settingsOfRunningBuild(5L)).isNull();
    }

    @Test
    void correctOptionCountFor_rotatesOverTheCourseRangeForMultipleChoiceItemsAndIsOneOtherwise() {
        HyperionCourseSettings defaults = HyperionCourseSettings.defaultSettings();
        HyperionCourseSettings narrow = new HyperionCourseSettings(false, 12, true, 12, 2, 3, 20, 50, 30);

        assertThat(List.of(1L, 2L, 3L, 4L, 5L).stream()
                .map(id -> HyperionQuizQuestionPoolBuildService.correctOptionCountFor(item(id, QuizQuestionGenerationType.MULTIPLE_CHOICE), defaults)).toList())
                .containsExactly(2, 3, 4, 1, 2);
        assertThat(List.of(1L, 2L, 3L, 4L).stream()
                .map(id -> HyperionQuizQuestionPoolBuildService.correctOptionCountFor(item(id, QuizQuestionGenerationType.MULTIPLE_CHOICE), narrow)).toList())
                .containsExactly(3, 2, 3, 2);
        assertThat(HyperionQuizQuestionPoolBuildService.correctOptionCountFor(item(2L, QuizQuestionGenerationType.SINGLE_CHOICE), defaults)).isEqualTo(1);
        assertThat(HyperionQuizQuestionPoolBuildService.correctOptionCountFor(item(2L, QuizQuestionGenerationType.TRUE_FALSE), defaults)).isEqualTo(1);
    }

    @Test
    void requestCancel_isRefusedWithoutARunningBuild() {
        HyperionQuizQuestionPoolBuildService service = service();

        assertThat(service.requestCancel(5L)).isFalse();
        service.tryStart(5L);
        assertThat(service.requestCancel(5L)).isTrue();
    }

    @Test
    void build_stopsBeforeItsNextUnitOfWorkAndDropsThePendingItemsWhenCancelled() {
        HyperionQuizQuestionPoolBuildService service = serviceWithChatClient();
        Course course = new Course();
        course.setId(5L);
        when(courseRepository.findByIdElseThrow(5L)).thenReturn(course);
        when(courseCompetencyApi.findAllForCourse(5L)).thenReturn(Set.of());
        when(settingsService.getSettings(5L)).thenReturn(HyperionCourseSettings.defaultSettings());
        when(itemRepository.deleteAllByCourseIdAndState(5L, QuizQuestionPoolItemState.PENDING)).thenReturn(4);
        String leaseToken = service.tryStart(5L);
        assertThat(service.requestCancel(5L)).isTrue();

        assertThat(service.build(5L, 7L, leaseToken)).isZero();

        verify(itemRepository).deleteAllByCourseIdAndState(5L, QuizQuestionPoolItemState.PENDING);
        verify(itemRepository, never()).findAllByCourseIdAndState(anyLong(), any(), any());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void release_clearsTheCancelRequestForTheNextBuild() {
        HyperionQuizQuestionPoolBuildService service = serviceWithChatClient();
        Course course = new Course();
        course.setId(5L);
        when(courseRepository.findByIdElseThrow(5L)).thenReturn(course);
        when(courseCompetencyApi.findAllForCourse(5L)).thenReturn(Set.of());
        when(settingsService.getSettings(5L)).thenReturn(HyperionCourseSettings.defaultSettings());
        String first = service.tryStart(5L);
        service.requestCancel(5L);
        service.release(5L, first);

        String second = service.tryStart(5L);
        service.build(5L, 7L, second);

        verify(itemRepository, never()).deleteAllByCourseIdAndState(anyLong(), any());
        verify(itemRepository).findAllByCourseIdAndState(5L, QuizQuestionPoolItemState.PENDING, PageRequest.of(0, 1));
    }

    @Test
    void build_assignsEveryQuestionToItsPositionAndRequiresTheAskedNumberOfCorrectOptions() {
        HyperionQuizQuestionPoolBuildService service = serviceWithChatClient();
        Course course = new Course();
        course.setId(5L);
        when(courseRepository.findByIdElseThrow(5L)).thenReturn(course);
        Competency competency = new Competency();
        competency.setId(9L);
        competency.setTitle("Recursion");
        when(courseCompetencyApi.findAllForCourse(5L)).thenReturn(Set.<CourseCompetency>of(competency));
        TextUnit unit = new TextUnit();
        unit.setId(4L);
        unit.setName("Base cases");
        when(materialService.findUnits(List.of(4L))).thenReturn(Map.of(4L, unit));
        when(materialService.extractText(unit)).thenReturn("Every recursion ends at a base case.");
        when(settingsService.getSettings(5L)).thenReturn(HyperionCourseSettings.defaultSettings());
        QuizQuestionPoolItem first = pendingItem(1L, course);
        QuizQuestionPoolItem second = pendingItem(2L, course);
        when(itemRepository.findAllByCourseIdAndState(5L, QuizQuestionPoolItemState.PENDING, PageRequest.of(0, 1))).thenReturn(List.of(first), List.of());
        when(itemRepository.findAllInCellWithState(5L, 9L, 4L, QuizQuestionGenerationLanguage.EN, QuizQuestionGenerationType.MULTIPLE_CHOICE, QuizQuestionDifficulty.MEDIUM,
                QuizQuestionPoolItemState.PENDING, PageRequest.of(0, 8))).thenReturn(List.of(first, second));
        when(itemRepository.claim(anyLong(), any(), any(), any(), anyLong())).thenReturn(1);
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        AtomicReference<Prompt> prompt = new AtomicReference<>();
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            prompt.set(invocation.getArgument(0));
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{ \"questions\": [" + question("Alpha", 1) + "," + question("Beta", 3) + "] }"))));
        });
        String leaseToken = service.tryStart(5L);

        service.build(5L, 7L, leaseToken);

        assertThat(prompt.get().getUserMessage().getText()).contains("Correct answer options per question: question 1: exactly 2; question 2: exactly 3");
        assertThat(first.getState()).isEqualTo(QuizQuestionPoolItemState.PENDING);
        assertThat(first.getFailure()).isEqualTo(HyperionQuizQuestionPoolBuildService.FAILURE_NO_VALID_QUESTION);
        assertThat(first.getQuestion()).isNull();
        assertThat(second.getState()).isEqualTo(QuizQuestionPoolItemState.GENERATED);
        assertThat(second.getQuestion().title()).isEqualTo("Beta");
        assertThat(second.getQuestion().options()).filteredOn(GeneratedQuizAnswerOptionDTO::correct).hasSize(3);
    }

    @Test
    void tryStart_holdsTheLeaseUntilReleased() {
        HyperionQuizQuestionPoolBuildService service = service();

        String leaseToken = service.tryStart(5L);

        assertThat(leaseToken).isNotNull();
        assertThat(service.isRunning(5L)).isTrue();
        assertThat(service.tryStart(5L)).isNull();
        assertThat(service.tryStart(6L)).isNotNull();

        service.release(5L, leaseToken);

        assertThat(service.isRunning(5L)).isFalse();
        assertThat(service.tryStart(5L)).isNotNull();
    }

    @Test
    void tryStart_takesOverAnExpiredLease() {
        HyperionQuizQuestionPoolBuildService service = service();
        leases.put(5L, "other-node/dead|" + (System.currentTimeMillis() - Duration.ofMinutes(11).toMillis()));

        assertThat(service.isRunning(5L)).isFalse();
        String leaseToken = service.tryStart(5L);

        assertThat(leaseToken).isNotNull();
        assertThat(leases.get(5L)).startsWith(leaseToken + "|");
        assertThat(service.isRunning(5L)).isTrue();
    }

    @Test
    void renewLease_failsOnceAnotherBuildTookTheCourse() {
        HyperionQuizQuestionPoolBuildService service = service();
        String leaseToken = service.tryStart(5L);
        leases.put(5L, "other-node/replacement|" + System.currentTimeMillis());

        assertThat(service.renewLease(5L, leaseToken)).isFalse();
        assertThat(leases.get(5L)).startsWith("other-node/replacement|");
    }

    @Test
    void renewLease_extendsAnOverdueLeaseNobodyElseTook() {
        HyperionQuizQuestionPoolBuildService service = service();
        String leaseToken = service.tryStart(5L);
        leases.put(5L, leaseToken + "|" + (System.currentTimeMillis() - Duration.ofMinutes(11).toMillis()));

        assertThat(service.renewLease(5L, leaseToken)).isTrue();
        assertThat(service.isRunning(5L)).isTrue();
    }

    @Test
    void release_leavesTheLeaseOfAnotherBuildAlone() {
        HyperionQuizQuestionPoolBuildService service = service();
        String expired = service.tryStart(5L);
        leases.remove(5L);
        String replacement = service.tryStart(5L);

        service.release(5L, expired);

        assertThat(leases.get(5L)).startsWith(replacement + "|");
    }

    @Test
    void buildAndRelease_dropsAQueuedBuildWhenAnotherBuildTookTheCourse() {
        HyperionQuizQuestionPoolBuildService service = serviceWithChatClient();
        String expired = service.tryStart(5L);
        leases.remove(5L);
        String replacement = service.tryStart(5L);

        service.buildAndRelease(5L, 7L, expired);

        assertThat(leases.get(5L)).startsWith(replacement + "|");
        verify(courseRepository, never()).findByIdElseThrow(anyLong());
    }

    @Test
    void buildAndRelease_takesTheLeaseAgainWhenItExpiredAndTheCourseIsFree() {
        HyperionQuizQuestionPoolBuildService service = serviceWithChatClient();
        when(courseRepository.findByIdElseThrow(5L)).thenThrow(new IllegalStateException("stop here"));
        String expired = service.tryStart(5L);
        leases.remove(5L);

        service.buildAndRelease(5L, 7L, expired);

        verify(courseRepository).findByIdElseThrow(5L);
        assertThat(service.isRunning(5L)).isFalse();
    }

    @Test
    void toQuestion_fallsBackToTheRequestedTypeWhenTheModelOmitsIt() {
        HyperionQuizQuestionPoolBuildService.PoolQuestionOutput generated = new HyperionQuizQuestionPoolBuildService.PoolQuestionOutput(null, "Loop count",
                "How often does the body run?",
                List.of(new HyperionQuizQuestionPoolBuildService.PoolOptionOutput("5 times", true), new HyperionQuizQuestionPoolBuildService.PoolOptionOutput("4 times", false)),
                "The condition is checked before every iteration.");

        Optional<GeneratedQuizQuestionDTO> question = HyperionQuizQuestionPoolBuildService.toQuestion(generated, QuizQuestionGenerationType.SINGLE_CHOICE);

        assertThat(question).hasValueSatisfying(mapped -> {
            assertThat(mapped.type()).isEqualTo(QuizQuestionGenerationType.SINGLE_CHOICE);
            assertThat(mapped.options()).hasSize(2);
            assertThat(mapped.explanation()).isEqualTo("The condition is checked before every iteration.");
        });
    }

    @Test
    void toQuestion_matchesTypeNamesIgnoringCaseAndSeparators() {
        List<HyperionQuizQuestionPoolBuildService.PoolOptionOutput> options = List.of(new HyperionQuizQuestionPoolBuildService.PoolOptionOutput("A", true));

        assertThat(HyperionQuizQuestionPoolBuildService.toQuestion(new HyperionQuizQuestionPoolBuildService.PoolQuestionOutput("single_choice", "T", "Q", options, null),
                QuizQuestionGenerationType.SINGLE_CHOICE)).hasValueSatisfying(mapped -> assertThat(mapped.type()).isEqualTo(QuizQuestionGenerationType.SINGLE_CHOICE));
        assertThat(HyperionQuizQuestionPoolBuildService.toQuestion(new HyperionQuizQuestionPoolBuildService.PoolQuestionOutput("Multiple Choice", "T", "Q", options, null),
                QuizQuestionGenerationType.SINGLE_CHOICE)).hasValueSatisfying(mapped -> assertThat(mapped.type()).isEqualTo(QuizQuestionGenerationType.MULTIPLE_CHOICE));
        assertThat(HyperionQuizQuestionPoolBuildService.toQuestion(new HyperionQuizQuestionPoolBuildService.PoolQuestionOutput("essay", "T", "Q", options, null),
                QuizQuestionGenerationType.SINGLE_CHOICE)).isEmpty();
        assertThat(HyperionQuizQuestionPoolBuildService.toQuestion(new HyperionQuizQuestionPoolBuildService.PoolQuestionOutput(null, "T", "Q", null, null),
                QuizQuestionGenerationType.SINGLE_CHOICE)).isEmpty();
    }

    @Test
    void saveIfStillOwned_savesAnItemWhoseClaimHolds() {
        HyperionQuizQuestionPoolBuildService service = service();
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setId(3L);
        when(itemRepository.save(item)).thenReturn(item);

        assertThat(service.saveIfStillOwned(item)).isTrue();
    }

    @Test
    void saveIfStillOwned_discardsTheResultWhenAnotherNodeTookTheItem() {
        HyperionQuizQuestionPoolBuildService service = service();
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setId(3L);
        when(itemRepository.save(item)).thenThrow(new ObjectOptimisticLockingFailureException(QuizQuestionPoolItem.class, 3L));

        assertThat(service.saveIfStillOwned(item)).isFalse();
    }

    @Test
    void build_throwsWhenChatClientIsNull() {
        HyperionQuizQuestionPoolBuildService service = service();

        assertThatThrownBy(() -> service.build(5L, 7L, "lease")).isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("AI chat client is not configured");
    }

    @Test
    void buildAndRelease_freesTheSlotWhenTheBuildFails() {
        HyperionQuizQuestionPoolBuildService service = service();
        String leaseToken = service.tryStart(5L);

        service.buildAndRelease(5L, 7L, leaseToken);

        assertThat(service.isRunning(5L)).isFalse();
    }

    private static QuizQuestionPoolItem item(long id, QuizQuestionGenerationType type) {
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setId(id);
        item.setQuestionType(type);
        return item;
    }

    private static QuizQuestionPoolItem pendingItem(long id, Course course) {
        QuizQuestionPoolItem item = item(id, QuizQuestionGenerationType.MULTIPLE_CHOICE);
        item.setCourse(course);
        item.setCompetencyId(9L);
        item.setLectureUnitId(4L);
        item.setLanguage(QuizQuestionGenerationLanguage.EN);
        item.setDifficulty(QuizQuestionDifficulty.MEDIUM);
        item.setItemIndex((int) id);
        item.setState(QuizQuestionPoolItemState.PENDING);
        item.setGenerationAttempts(0);
        item.setFilterAttempts(0);
        item.setVersion(0L);
        return item;
    }

    /** A multiple-choice question whose first {@code correctCount} options are correct. */
    private static String question(String title, int correctCount) {
        return """
                { "type": "multiple-choice", "title": "%s", "questionText": "Which claims about base cases hold?",
                  "options": [ { "text": "First claim", "correct": %s }, { "text": "Second claim", "correct": %s },
                               { "text": "Third claim", "correct": %s }, { "text": "Fourth claim", "correct": %s } ],
                  "explanation": "The marked claims follow from the material." }
                """.formatted(title, correctCount >= 1, correctCount >= 2, correctCount >= 3, correctCount >= 4);
    }

    private HyperionQuizQuestionPoolBuildService service() {
        return new HyperionQuizQuestionPoolBuildService(null, new HyperionPromptTemplateService(), materialService, Optional.of(courseCompetencyApi), filterService,
                llmTokenUsageService, itemRepository, settingsService, courseRepository, distributedDataProvider, "", 8, 30, 10);
    }

    private HyperionQuizQuestionPoolBuildService serviceWithChatClient() {
        return new HyperionQuizQuestionPoolBuildService(ChatClient.create(chatModel), new HyperionPromptTemplateService(), materialService, Optional.of(courseCompetencyApi),
                filterService, llmTokenUsageService, itemRepository, settingsService, courseRepository, distributedDataProvider, "", 8, 30, 10);
    }
}
