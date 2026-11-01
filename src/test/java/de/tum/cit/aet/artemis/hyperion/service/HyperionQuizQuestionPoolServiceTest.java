package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettings;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionFailureMode;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;
import de.tum.cit.aet.artemis.hyperion.dto.HyperionCourseSettingsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionFilterDecisionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionFilterVerdictDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolBuildStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolCompetencyDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolPageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolProgressDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolStateCountDTO;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolItemRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitApi;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

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

    @Mock
    private LectureUnitApi lectureUnitApi;

    private HyperionQuizQuestionPoolService service;

    private AutoCloseable mocks;

    private Course course;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new HyperionQuizQuestionPoolService(buildService, taskService, itemRepository, settingsService, Optional.of(courseCompetencyApi), Optional.of(lectureUnitApi));
        course = new Course();
        course.setId(5L);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void startBuild_enqueuesAndDispatchesWhenTheSlotIsFree() {
        when(buildService.tryStart(5L)).thenReturn("lease");
        when(buildService.enqueue(course)).thenReturn(12);

        QuizQuestionPoolBuildStartDTO result = service.startBuild(course, 7L);

        assertThat(result).isEqualTo(new QuizQuestionPoolBuildStartDTO(true, 12));
        verify(taskService).runBuildAsync(5L, 7L, "lease");
    }

    @Test
    void startBuild_reportsAConflictWhenABuildIsRunning() {
        when(buildService.tryStart(5L)).thenReturn(null);

        QuizQuestionPoolBuildStartDTO result = service.startBuild(course, 7L);

        assertThat(result).isEqualTo(new QuizQuestionPoolBuildStartDTO(false, 0));
        verify(buildService, never()).enqueue(course);
        verify(taskService, never()).runBuildAsync(eq(5L), eq(7L), any());
    }

    @Test
    void startBuild_freesTheSlotWhenDispatchFails() {
        when(buildService.tryStart(5L)).thenReturn("lease");
        when(buildService.enqueue(course)).thenReturn(12);
        doThrow(new TaskRejectedException("executor queue full")).when(taskService).runBuildAsync(5L, 7L, "lease");

        assertThatThrownBy(() -> service.startBuild(course, 7L)).isInstanceOf(TaskRejectedException.class);

        verify(buildService).release(5L, "lease");
    }

    @Test
    void startBuild_freesTheSlotWhenEnqueuingFails() {
        when(buildService.tryStart(5L)).thenReturn("lease");
        when(buildService.enqueue(course)).thenThrow(new IllegalStateException("no competencies"));

        assertThatThrownBy(() -> service.startBuild(course, 7L)).isInstanceOf(IllegalStateException.class);

        verify(buildService).release(5L, "lease");
        verify(taskService, never()).runBuildAsync(eq(5L), eq(7L), any());
    }

    @Test
    void getProgress_aggregatesTheStateCounts() {
        when(buildService.isRunning(5L)).thenReturn(true);
        when(itemRepository.countByStateForCourse(5L)).thenReturn(List.of(new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.PENDING, null, 3),
                new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.GENERATING, null, 1), new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.GENERATED, null, 2),
                new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.FILTERED, true, 8), new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.FILTERED, false, 2),
                new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.FAILED_FILTER, null, 1)));

        QuizQuestionPoolProgressDTO progress = service.getProgress(5L);

        assertThat(progress).isEqualTo(new QuizQuestionPoolProgressDTO(true, 17, 4, 2, 10, 8, 2, 1, null));
    }

    @Test
    void cancelBuild_throwsConflictWithoutARunningBuild() {
        when(buildService.requestCancel(5L)).thenReturn(false);

        assertThatThrownBy(() -> service.cancelBuild(5L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelBuild_asksTheRunningBuildToStop() {
        when(buildService.requestCancel(5L)).thenReturn(true);

        service.cancelBuild(5L);

        verify(buildService).requestCancel(5L);
    }

    @Test
    void getProgress_carriesTheSettingsOfTheRunningBuild() {
        HyperionCourseSettings settings = new HyperionCourseSettings(false, 5, false, 12, 1, 4, 20, 50, 30);
        when(buildService.isRunning(5L)).thenReturn(true);
        when(buildService.settingsOfRunningBuild(5L)).thenReturn(settings);
        when(itemRepository.countByStateForCourse(5L)).thenReturn(List.of());

        assertThat(service.getProgress(5L).buildSettings()).isEqualTo(HyperionCourseSettingsDTO.of(settings));
    }

    @Test
    void getItems_mapsItemsWithTheirCompetencyTitlesAndLectureUnitNames() {
        Competency competency = new Competency();
        competency.setId(9L);
        competency.setTitle("Arrays");
        when(courseCompetencyApi.findAllForCourse(5L)).thenReturn(Set.of(competency));
        TextUnit unit = new TextUnit();
        unit.setId(4L);
        unit.setName("Arrays in Java");
        when(lectureUnitApi.findAllByIds(anyCollection())).thenReturn(List.of(unit));
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setId(1L);
        item.setCourse(course);
        item.setCompetencyId(9L);
        item.setLectureUnitId(4L);
        item.setLanguage(QuizQuestionGenerationLanguage.EN);
        item.setQuestionType(QuizQuestionGenerationType.SINGLE_CHOICE);
        item.setDifficulty(QuizQuestionDifficulty.MEDIUM);
        item.setUpdatedAt(ZonedDateTime.now());
        when(itemRepository.findPageForCourse(5L, 9L, PageRequest.of(0, 100))).thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 100), 1));
        when(itemRepository.findCompetencyIdsForCourse(5L)).thenReturn(List.of(9L, 10L));

        QuizQuestionPoolPageDTO page = service.getItems(5L, 9L, false, 0, 100);

        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().competencyTitle()).isEqualTo("Arrays");
        assertThat(page.items().getFirst().lectureUnitId()).isEqualTo(4L);
        assertThat(page.items().getFirst().lectureUnitName()).isEqualTo("Arrays in Java");
        assertThat(page.items().getFirst().state()).isEqualTo(QuizQuestionPoolItemState.PENDING);
        assertThat(page.items().getFirst().flagged()).isNull();
        assertThat(page.competencies()).containsExactly(new QuizQuestionPoolCompetencyDTO(9L, "Arrays"), new QuizQuestionPoolCompetencyDTO(10L, null));
    }

    @Test
    void getItems_pagesTheWholePoolAndDerivesTheFlagOfOlderJudgedItems() {
        when(courseCompetencyApi.findAllForCourse(5L)).thenReturn(Set.of());
        when(itemRepository.findCompetencyIdsForCourse(5L)).thenReturn(List.of());
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setId(2L);
        item.setCourse(course);
        item.setCompetencyId(9L);
        item.setLectureUnitId(4L);
        item.setLanguage(QuizQuestionGenerationLanguage.EN);
        item.setQuestionType(QuizQuestionGenerationType.SINGLE_CHOICE);
        item.setDifficulty(QuizQuestionDifficulty.MEDIUM);
        item.setState(QuizQuestionPoolItemState.FILTERED);
        item.setAccepted(true);
        item.setDecision(new QuizQuestionFilterDecisionDTO(true, 0.6, List.of(new QuizQuestionFilterVerdictDTO(QuizQuestionFailureMode.ILL_FORMED_DISTRACTORS, 0.4, false, "weak")),
                "checked"));
        item.setUpdatedAt(ZonedDateTime.now());
        when(itemRepository.findFlaggedPageForCourse(5L, PageRequest.of(1, 50))).thenReturn(new PageImpl<>(List.of(item), PageRequest.of(1, 50), 51));
        when(lectureUnitApi.findAllByIds(anyCollection())).thenReturn(List.of());

        QuizQuestionPoolPageDTO page = service.getItems(5L, null, true, 1, 50);

        assertThat(page.totalCount()).isEqualTo(51);
        assertThat(page.items().getFirst().flagged()).isTrue();
        assertThat(page.items().getFirst().decision().verdicts().getFirst().flagged()).isTrue();
    }

    @Test
    void deleteItem_removesAnItemOfTheCourseWhileNoBuildRuns() {
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setId(3L);
        item.setCourse(course);
        when(itemRepository.findByIdElseThrow(3L)).thenReturn(item);
        when(buildService.isRunning(5L)).thenReturn(false);

        service.deleteItem(5L, 3L);

        verify(itemRepository).deleteById(3L);
    }

    @Test
    void deleteItem_refusesAnItemOfAnotherCourseAndADeletionDuringABuild() {
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setId(3L);
        item.setCourse(course);
        when(itemRepository.findByIdElseThrow(3L)).thenReturn(item);

        assertThatThrownBy(() -> service.deleteItem(6L, 3L)).isInstanceOf(EntityNotFoundException.class);
        when(buildService.isRunning(5L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteItem(5L, 3L)).isInstanceOf(ConflictException.class);
        verify(itemRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteItems_clearsTheCompetencyOrThePoolAndReportsTheCount() {
        when(buildService.isRunning(5L)).thenReturn(false);
        when(itemRepository.deleteAllForCourse(5L, 9L)).thenReturn(4);
        when(itemRepository.deleteAllForCourse(5L, null)).thenReturn(12);

        assertThat(service.deleteItems(5L, 9L).deleted()).isEqualTo(4);
        assertThat(service.deleteItems(5L, null).deleted()).isEqualTo(12);

        when(buildService.isRunning(5L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteItems(5L, null)).isInstanceOf(ConflictException.class);
    }

    @Test
    void getItems_rejectsAPageOutOfRange() {
        assertThatThrownBy(() -> service.getItems(5L, null, false, -1, 100)).isInstanceOf(BadRequestAlertException.class);
        assertThatThrownBy(() -> service.getItems(5L, null, false, 0, 501)).isInstanceOf(BadRequestAlertException.class);
        assertThatThrownBy(() -> service.getItems(5L, null, false, 0, 0)).isInstanceOf(BadRequestAlertException.class);
    }
}
