package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
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

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        leases = new LocalMap<>();
        lenient().when(distributedDataProvider.<Long, String>getMap(anyString())).thenReturn(leases);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void deriveCells_crossesCompetenciesAndUnitsWithTypesAndDifficultiesInAFixedOrder() {
        List<Cell> cells = HyperionQuizQuestionPoolBuildService.deriveCells(Map.of(7L, List.of(2L, 1L), 3L, List.of(5L)), QuizQuestionGenerationLanguage.DE,
                Set.of(QuizQuestionGenerationType.MULTIPLE_CHOICE, QuizQuestionGenerationType.SINGLE_CHOICE), Set.of(QuizQuestionDifficulty.HARD, QuizQuestionDifficulty.EASY));

        assertThat(cells).hasSize(12);
        assertThat(cells.getFirst()).isEqualTo(new Cell(3L, 5L, QuizQuestionGenerationLanguage.DE, QuizQuestionGenerationType.SINGLE_CHOICE, QuizQuestionDifficulty.EASY));
        assertThat(cells.get(1)).isEqualTo(new Cell(3L, 5L, QuizQuestionGenerationLanguage.DE, QuizQuestionGenerationType.SINGLE_CHOICE, QuizQuestionDifficulty.HARD));
        assertThat(cells.get(2)).isEqualTo(new Cell(3L, 5L, QuizQuestionGenerationLanguage.DE, QuizQuestionGenerationType.MULTIPLE_CHOICE, QuizQuestionDifficulty.EASY));
        assertThat(cells.get(4)).isEqualTo(new Cell(7L, 1L, QuizQuestionGenerationLanguage.DE, QuizQuestionGenerationType.SINGLE_CHOICE, QuizQuestionDifficulty.EASY));
        assertThat(cells.getLast()).isEqualTo(new Cell(7L, 2L, QuizQuestionGenerationLanguage.DE, QuizQuestionGenerationType.MULTIPLE_CHOICE, QuizQuestionDifficulty.HARD));
    }

    @Test
    void deriveCells_isEmptyWithoutLectureUnits() {
        assertThat(HyperionQuizQuestionPoolBuildService.deriveCells(Map.of(), QuizQuestionGenerationLanguage.EN, Set.of(QuizQuestionGenerationType.SINGLE_CHOICE),
                Set.of(QuizQuestionDifficulty.MEDIUM))).isEmpty();
    }

    @Test
    void itemsPerCell_roundsUpSoEveryCellHoldsAtLeastOneQuestion() {
        assertThat(HyperionQuizQuestionPoolBuildService.itemsPerCell(20, 6)).isEqualTo(4);
        assertThat(HyperionQuizQuestionPoolBuildService.itemsPerCell(12, 6)).isEqualTo(2);
        assertThat(HyperionQuizQuestionPoolBuildService.itemsPerCell(1, 6)).isEqualTo(1);
    }

    @Test
    void constructor_rejectsAnUnknownQuestionType() {
        assertThatThrownBy(() -> service(List.of("single-choice", "essay"), List.of("medium"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("essay");
    }

    @Test
    void constructor_rejectsAnUnknownDifficulty() {
        assertThatThrownBy(() -> service(List.of("single-choice"), List.of("impossible"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("impossible");
    }

    @Test
    void constructor_rejectsEmptyGridDimensions() {
        assertThatThrownBy(() -> service(List.of(" "), List.of("medium"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("question-types");
        assertThatThrownBy(() -> service(List.of("single-choice"), List.of())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("difficulties");
    }

    @Test
    void enqueue_topsEveryCellUpToItsTargetFromOneCountQuery() {
        HyperionQuizQuestionPoolBuildService service = service(List.of("single-choice"), List.of("medium"));
        Course course = new Course();
        course.setId(5L);
        Competency competency = new Competency();
        competency.setId(9L);
        TextUnit unit = new TextUnit();
        unit.setId(4L);
        when(courseCompetencyApi.findAllForCourse(5L)).thenReturn(Set.<CourseCompetency>of(competency));
        when(materialService.findGroundableUnits(List.of(9L))).thenReturn(Map.of(9L, List.<LectureUnit>of(unit)));
        when(settingsService.getSettings(5L)).thenReturn(HyperionCourseSettings.defaultSettings());
        when(itemRepository.countByCellForCourse(5L)).thenReturn(
                List.of(new QuizQuestionPoolCellCountDTO(9L, 4L, QuizQuestionGenerationLanguage.EN, QuizQuestionGenerationType.SINGLE_CHOICE, QuizQuestionDifficulty.MEDIUM, 10)));

        int created = service.enqueue(course);

        ArgumentCaptor<List<QuizQuestionPoolItem>> saved = ArgumentCaptor.captor();
        verify(itemRepository).saveAll(saved.capture());
        assertThat(created).isEqualTo(2);
        assertThat(saved.getValue()).extracting(QuizQuestionPoolItem::getItemIndex).containsExactly(10, 11);
    }

    @Test
    void tryStart_holdsTheLeaseUntilReleased() {
        HyperionQuizQuestionPoolBuildService service = service(List.of("single-choice"), List.of("medium"));

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
        HyperionQuizQuestionPoolBuildService service = service(List.of("single-choice"), List.of("medium"));
        leases.put(5L, "other-node/dead|" + (System.currentTimeMillis() - Duration.ofMinutes(11).toMillis()));

        assertThat(service.isRunning(5L)).isFalse();
        String leaseToken = service.tryStart(5L);

        assertThat(leaseToken).isNotNull();
        assertThat(leases.get(5L)).startsWith(leaseToken + "|");
        assertThat(service.isRunning(5L)).isTrue();
    }

    @Test
    void renewLease_failsOnceAnotherBuildTookTheCourse() {
        HyperionQuizQuestionPoolBuildService service = service(List.of("single-choice"), List.of("medium"));
        String leaseToken = service.tryStart(5L);
        leases.put(5L, "other-node/replacement|" + System.currentTimeMillis());

        assertThat(service.renewLease(5L, leaseToken)).isFalse();
        assertThat(leases.get(5L)).startsWith("other-node/replacement|");
    }

    @Test
    void renewLease_extendsAnOverdueLeaseNobodyElseTook() {
        HyperionQuizQuestionPoolBuildService service = service(List.of("single-choice"), List.of("medium"));
        String leaseToken = service.tryStart(5L);
        leases.put(5L, leaseToken + "|" + (System.currentTimeMillis() - Duration.ofMinutes(11).toMillis()));

        assertThat(service.renewLease(5L, leaseToken)).isTrue();
        assertThat(service.isRunning(5L)).isTrue();
    }

    @Test
    void release_leavesTheLeaseOfAnotherBuildAlone() {
        HyperionQuizQuestionPoolBuildService service = service(List.of("single-choice"), List.of("medium"));
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
        HyperionQuizQuestionPoolBuildService service = service(List.of("single-choice"), List.of("medium"));
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setId(3L);
        when(itemRepository.save(item)).thenReturn(item);

        assertThat(service.saveIfStillOwned(item)).isTrue();
    }

    @Test
    void saveIfStillOwned_discardsTheResultWhenAnotherNodeTookTheItem() {
        HyperionQuizQuestionPoolBuildService service = service(List.of("single-choice"), List.of("medium"));
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setId(3L);
        when(itemRepository.save(item)).thenThrow(new ObjectOptimisticLockingFailureException(QuizQuestionPoolItem.class, 3L));

        assertThat(service.saveIfStillOwned(item)).isFalse();
    }

    @Test
    void build_throwsWhenChatClientIsNull() {
        HyperionQuizQuestionPoolBuildService service = service(List.of("single-choice"), List.of("medium"));

        assertThatThrownBy(() -> service.build(5L, 7L, "lease")).isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("AI chat client is not configured");
    }

    @Test
    void buildAndRelease_freesTheSlotWhenTheBuildFails() {
        HyperionQuizQuestionPoolBuildService service = service(List.of("single-choice"), List.of("medium"));
        String leaseToken = service.tryStart(5L);

        service.buildAndRelease(5L, 7L, leaseToken);

        assertThat(service.isRunning(5L)).isFalse();
    }

    private HyperionQuizQuestionPoolBuildService service(List<String> questionTypes, List<String> difficulties) {
        return new HyperionQuizQuestionPoolBuildService(null, new HyperionPromptTemplateService(), materialService, Optional.of(courseCompetencyApi), filterService,
                llmTokenUsageService, itemRepository, settingsService, courseRepository, distributedDataProvider, "", questionTypes, difficulties, 8, 30, 10);
    }

    private HyperionQuizQuestionPoolBuildService serviceWithChatClient() {
        return new HyperionQuizQuestionPoolBuildService(ChatClient.create(chatModel), new HyperionPromptTemplateService(), materialService, Optional.of(courseCompetencyApi),
                filterService, llmTokenUsageService, itemRepository, settingsService, courseRepository, distributedDataProvider, "", List.of("single-choice"), List.of("medium"), 8,
                30, 10);
    }
}
