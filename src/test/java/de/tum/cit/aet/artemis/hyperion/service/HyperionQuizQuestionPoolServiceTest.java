package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolBuildStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolItemDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolProgressDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolStateCountDTO;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolItemRepository;

class HyperionQuizQuestionPoolServiceTest {

    @Mock
    private HyperionQuizQuestionPoolBuildService buildService;

    @Mock
    private HyperionQuizQuestionPoolTaskService taskService;

    @Mock
    private QuizQuestionPoolItemRepository itemRepository;

    @Mock
    private HyperionCourseSettingsService settingsService;

    @Mock
    private CourseCompetencyApi courseCompetencyApi;

    private HyperionQuizQuestionPoolService service;

    private AutoCloseable mocks;

    private Course course;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new HyperionQuizQuestionPoolService(buildService, taskService, itemRepository, settingsService, Optional.of(courseCompetencyApi));
        course = new Course();
        course.setId(5L);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void startBuild_enqueuesAndDispatchesWhenTheSlotIsFree() {
        when(buildService.tryStart(5L)).thenReturn(true);
        when(buildService.enqueue(course)).thenReturn(12);

        QuizQuestionPoolBuildStartDTO result = service.startBuild(course, 7L);

        assertThat(result).isEqualTo(new QuizQuestionPoolBuildStartDTO(true, 12));
        verify(taskService).runBuildAsync(5L, 7L);
    }

    @Test
    void startBuild_reportsAConflictWhenABuildIsRunning() {
        when(buildService.tryStart(5L)).thenReturn(false);

        QuizQuestionPoolBuildStartDTO result = service.startBuild(course, 7L);

        assertThat(result).isEqualTo(new QuizQuestionPoolBuildStartDTO(false, 0));
        verify(buildService, never()).enqueue(course);
        verify(taskService, never()).runBuildAsync(5L, 7L);
    }

    @Test
    void startBuild_freesTheSlotWhenEnqueuingFails() {
        when(buildService.tryStart(5L)).thenReturn(true);
        when(buildService.enqueue(course)).thenThrow(new IllegalStateException("no competencies"));

        assertThatThrownBy(() -> service.startBuild(course, 7L)).isInstanceOf(IllegalStateException.class);

        verify(buildService).release(5L);
        verify(taskService, never()).runBuildAsync(5L, 7L);
    }

    @Test
    void getProgress_aggregatesTheStateCounts() {
        when(buildService.isRunning(5L)).thenReturn(true);
        when(itemRepository.countByStateForCourse(5L)).thenReturn(List.of(new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.PENDING, 3),
                new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.GENERATING, 1), new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.GENERATED, 2),
                new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.FILTERED, 10), new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.FAILED_FILTER, 1)));
        when(itemRepository.countByCourseIdAndAccepted(5L, true)).thenReturn(8);
        when(itemRepository.countByCourseIdAndAccepted(5L, false)).thenReturn(2);

        QuizQuestionPoolProgressDTO progress = service.getProgress(5L);

        assertThat(progress).isEqualTo(new QuizQuestionPoolProgressDTO(true, 17, 4, 2, 10, 8, 2, 1));
    }

    @Test
    void getItems_mapsItemsWithTheirCompetencyTitles() {
        Competency competency = new Competency();
        competency.setId(9L);
        competency.setTitle("Arrays");
        when(courseCompetencyApi.findAllForCourse(5L)).thenReturn(Set.of(competency));
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setId(1L);
        item.setCourse(course);
        item.setCompetencyId(9L);
        item.setLanguage(QuizQuestionGenerationLanguage.EN);
        item.setQuestionType(QuizQuestionGenerationType.SINGLE_CHOICE);
        item.setDifficulty(QuizQuestionDifficulty.MEDIUM);
        item.setUpdatedAt(ZonedDateTime.now());
        when(itemRepository.findAllByCourseIdAndCompetencyIdOrdered(5L, 9L)).thenReturn(List.of(item));

        List<QuizQuestionPoolItemDTO> items = service.getItems(5L, 9L);

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().competencyTitle()).isEqualTo("Arrays");
        assertThat(items.getFirst().state()).isEqualTo(QuizQuestionPoolItemState.PENDING);
        verify(itemRepository, never()).findAllByCourseIdOrdered(5L);
    }

    @Test
    void getItems_listsTheWholePoolWithoutACompetency() {
        when(courseCompetencyApi.findAllForCourse(5L)).thenReturn(Set.of());
        when(itemRepository.findAllByCourseIdOrdered(5L)).thenReturn(List.of());

        assertThat(service.getItems(5L, null)).isEmpty();
    }
}
