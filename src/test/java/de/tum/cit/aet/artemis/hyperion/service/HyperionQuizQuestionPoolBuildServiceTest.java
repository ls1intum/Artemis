package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolItemRepository;
import de.tum.cit.aet.artemis.hyperion.service.HyperionQuizQuestionPoolBuildService.Cell;

class HyperionQuizQuestionPoolBuildServiceTest {

    @Mock
    private HyperionCompetencyContextService competencyContextService;

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

    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void deriveCells_crossesCompetenciesWithTypesAndDifficultiesInAFixedOrder() {
        List<Cell> cells = HyperionQuizQuestionPoolBuildService.deriveCells(List.of(7L, 3L), QuizQuestionGenerationLanguage.DE,
                Set.of(QuizQuestionGenerationType.MULTIPLE_CHOICE, QuizQuestionGenerationType.SINGLE_CHOICE), Set.of(QuizQuestionDifficulty.HARD, QuizQuestionDifficulty.EASY));

        assertThat(cells).hasSize(8);
        assertThat(cells.getFirst()).isEqualTo(new Cell(7L, QuizQuestionGenerationLanguage.DE, QuizQuestionGenerationType.SINGLE_CHOICE, QuizQuestionDifficulty.EASY));
        assertThat(cells.get(1)).isEqualTo(new Cell(7L, QuizQuestionGenerationLanguage.DE, QuizQuestionGenerationType.SINGLE_CHOICE, QuizQuestionDifficulty.HARD));
        assertThat(cells.get(2)).isEqualTo(new Cell(7L, QuizQuestionGenerationLanguage.DE, QuizQuestionGenerationType.MULTIPLE_CHOICE, QuizQuestionDifficulty.EASY));
        assertThat(cells.getLast()).isEqualTo(new Cell(3L, QuizQuestionGenerationLanguage.DE, QuizQuestionGenerationType.MULTIPLE_CHOICE, QuizQuestionDifficulty.HARD));
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
    void tryStart_holdsTheSlotUntilReleased() {
        HyperionQuizQuestionPoolBuildService service = service(List.of("single-choice"), List.of("medium"));

        assertThat(service.tryStart(5L)).isTrue();
        assertThat(service.isRunning(5L)).isTrue();
        assertThat(service.tryStart(5L)).isFalse();
        assertThat(service.tryStart(6L)).isTrue();

        service.release(5L);

        assertThat(service.isRunning(5L)).isFalse();
        assertThat(service.tryStart(5L)).isTrue();
    }

    @Test
    void build_throwsWhenChatClientIsNull() {
        HyperionQuizQuestionPoolBuildService service = service(List.of("single-choice"), List.of("medium"));

        assertThatThrownBy(() -> service.build(5L, 7L)).isInstanceOf(InternalServerErrorAlertException.class).hasMessageContaining("AI chat client is not configured");
    }

    @Test
    void buildAndRelease_freesTheSlotWhenTheBuildFails() {
        HyperionQuizQuestionPoolBuildService service = service(List.of("single-choice"), List.of("medium"));
        service.tryStart(5L);

        service.buildAndRelease(5L, 7L);

        assertThat(service.isRunning(5L)).isFalse();
    }

    private HyperionQuizQuestionPoolBuildService service(List<String> questionTypes, List<String> difficulties) {
        return new HyperionQuizQuestionPoolBuildService(null, new HyperionPromptTemplateService(), competencyContextService, Optional.of(courseCompetencyApi), filterService,
                llmTokenUsageService, itemRepository, settingsService, courseRepository, "", questionTypes, difficulties, 8, 30);
    }
}
