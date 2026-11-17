package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettings;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionFailureMode;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolBuild;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolBuildTrigger;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolLectureUnit;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionReviewState;
import de.tum.cit.aet.artemis.hyperion.dto.HyperionCourseSettingsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionFilterDecisionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionFilterVerdictDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolBuildDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolBuildStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolCompetencyDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolLectureUnitCountDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolLectureUnitDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolPageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolProgressDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolStateCountDTO;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolBuildRepository;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolItemRepository;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolLectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
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

    @Mock
    private QuizQuestionPoolLectureUnitRepository lectureUnitRepository;

    @Mock
    private QuizQuestionPoolBuildRepository buildRepository;

    @Mock
    private HyperionQuizQuestionPoolMaterialService materialService;

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private HyperionQuizQuestionPoolModelService modelService;

    private HyperionQuizQuestionPoolService service;

    private AutoCloseable mocks;

    private Course course;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(modelService.isConfigured()).thenReturn(true);
        service = new HyperionQuizQuestionPoolService(buildService, taskService, itemRepository, settingsService, Optional.of(courseCompetencyApi), Optional.of(lectureUnitApi),
                lectureUnitRepository, buildRepository, materialService, courseRepository, modelService);
        course = new Course();
        course.setId(5L);
        lenient().when(buildRepository.findAllByCourseIdNewestFirst(eq(5L), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void startBuild_enqueuesRecordsAndDispatchesWhenTheLeaseIsFree() {
        when(buildService.tryStart(5L)).thenReturn("lease");
        when(buildService.enqueue(course)).thenReturn(12);
        QuizQuestionPoolBuild build = new QuizQuestionPoolBuild();
        build.setId(3L);
        when(buildService.recordStart(course, QuizQuestionPoolBuildTrigger.MANUAL, 12)).thenReturn(build);

        QuizQuestionPoolBuildStartDTO result = service.startBuild(course, 7L);

        assertThat(result).isEqualTo(new QuizQuestionPoolBuildStartDTO(true, 12));
        verify(taskService).runBuildAsync(5L, 7L, 3L, "lease");
    }

    @Test
    void startBuild_isRefusedWithoutAConfiguredModel() {
        when(modelService.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.startBuild(course, 7L)).isInstanceOf(ServiceUnavailableAlertException.class).hasMessageContaining("no local model configured");
        verify(buildService, never()).tryStart(anyLong());
        verify(buildService, never()).enqueue(course);
    }

    @Test
    void startBuild_reportsAConflictWhenABuildIsRunning() {
        when(buildService.tryStart(5L)).thenReturn(null);

        QuizQuestionPoolBuildStartDTO result = service.startBuild(course, 7L);

        assertThat(result).isEqualTo(new QuizQuestionPoolBuildStartDTO(false, 0));
        verify(buildService, never()).enqueue(course);
        verify(taskService, never()).runBuildAsync(eq(5L), eq(7L), anyLong(), any());
    }

    @Test
    void startBuild_freesTheSlotWhenDispatchFails() {
        when(buildService.tryStart(5L)).thenReturn("lease");
        when(buildService.enqueue(course)).thenReturn(12);
        QuizQuestionPoolBuild build = new QuizQuestionPoolBuild();
        build.setId(3L);
        when(buildService.recordStart(course, QuizQuestionPoolBuildTrigger.MANUAL, 12)).thenReturn(build);
        TaskRejectedException rejected = new TaskRejectedException("executor queue full");
        doThrow(rejected).when(taskService).runBuildAsync(5L, 7L, 3L, "lease");

        assertThatThrownBy(() -> service.startBuild(course, 7L)).isSameAs(rejected);

        verify(buildService).recordDispatchFailure(3L);
        verify(buildService).release(5L, "lease");
    }

    @Test
    void startBuild_freesTheSlotAndKeepsTheDispatchFailureWhenRecordingItFails() {
        when(buildService.tryStart(5L)).thenReturn("lease");
        when(buildService.enqueue(course)).thenReturn(12);
        QuizQuestionPoolBuild build = new QuizQuestionPoolBuild();
        build.setId(3L);
        when(buildService.recordStart(course, QuizQuestionPoolBuildTrigger.MANUAL, 12)).thenReturn(build);
        TaskRejectedException rejected = new TaskRejectedException("executor queue full");
        doThrow(rejected).when(taskService).runBuildAsync(5L, 7L, 3L, "lease");
        IllegalStateException recordFailure = new IllegalStateException("database unavailable");
        doThrow(recordFailure).when(buildService).recordDispatchFailure(3L);

        assertThatThrownBy(() -> service.startBuild(course, 7L)).isSameAs(rejected).hasSuppressedException(recordFailure);

        verify(buildService).release(5L, "lease");
    }

    @Test
    void startBuild_freesTheSlotWhenEnqueuingFails() {
        when(buildService.tryStart(5L)).thenReturn("lease");
        when(buildService.enqueue(course)).thenThrow(new IllegalStateException("no competencies"));

        assertThatThrownBy(() -> service.startBuild(course, 7L)).isInstanceOf(IllegalStateException.class);

        verify(buildService).release(5L, "lease");
        verify(taskService, never()).runBuildAsync(eq(5L), eq(7L), anyLong(), any());
    }

    @Test
    void getProgress_aggregatesTheStateCounts() {
        when(buildService.isRunning(5L)).thenReturn(true);
        when(itemRepository.countByStateForCourse(5L)).thenReturn(List.of(new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.PENDING, null, 3),
                new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.GENERATING, null, 1), new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.GENERATED, null, 2),
                new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.FILTERED, true, 8), new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.FILTERED, false, 2),
                new QuizQuestionPoolStateCountDTO(QuizQuestionPoolItemState.FAILED_FILTER, null, 1)));

        QuizQuestionPoolProgressDTO progress = service.getProgress(5L);

        assertThat(progress).isEqualTo(new QuizQuestionPoolProgressDTO(true, 17, 4, 2, 10, 8, 2, 1, null, null, true));
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
    void getProgress_carriesTheLastBuild() {
        when(buildService.isRunning(5L)).thenReturn(false);
        when(itemRepository.countByStateForCourse(5L)).thenReturn(List.of());
        QuizQuestionPoolBuild build = new QuizQuestionPoolBuild();
        build.setId(3L);
        build.setCourse(course);
        build.setTrigger(QuizQuestionPoolBuildTrigger.SCHEDULED);
        build.setEnqueued(12);
        build.setGenerated(12);
        build.setJudged(12);
        build.setAccepted(9);
        build.setRejected(3);
        when(buildRepository.findAllByCourseIdNewestFirst(eq(5L), any())).thenReturn(List.of(build));

        QuizQuestionPoolProgressDTO progress = service.getProgress(5L);

        assertThat(progress.lastBuild()).isEqualTo(QuizQuestionPoolBuildDTO.of(build));
        assertThat(progress.lastBuild().trigger()).isEqualTo(QuizQuestionPoolBuildTrigger.SCHEDULED);
        assertThat(progress.lastBuild().accepted()).isEqualTo(9);
    }

    @Test
    void getLectureUnits_listsTheUnitsOfEachCompetencyWithCountsAndExclusion() {
        Competency competency = new Competency();
        competency.setId(9L);
        competency.setTitle("Arrays");
        when(courseCompetencyApi.findAllForCourse(5L)).thenReturn(Set.of(competency));
        TextUnit unit = unitOfCourse(4L, "Arrays in Java", 5L);
        TextUnit other = unitOfCourse(6L, "Arrays, continued", 5L);
        when(materialService.findGroundableUnits(List.of(9L))).thenReturn(Map.of(9L, List.of(unit, other)));
        QuizQuestionPoolLectureUnit record = new QuizQuestionPoolLectureUnit();
        record.setLectureUnitId(6L);
        record.setExcluded(true);
        when(lectureUnitRepository.findAllByCourseId(5L)).thenReturn(List.of(record));
        when(itemRepository.countByLectureUnitForCourse(5L, QuizQuestionReviewState.APPROVED)).thenReturn(List.of(new QuizQuestionPoolLectureUnitCountDTO(4L, 12L, 3L, 1L)));

        List<QuizQuestionPoolLectureUnitDTO> units = service.getLectureUnits(5L);

        assertThat(units).hasSize(2);
        assertThat(units.getFirst()).isEqualTo(new QuizQuestionPoolLectureUnitDTO(9L, "Arrays", 4L, "Arrays in Java", "Lecture 1", false, true, 12, 3, null));
        assertThat(units.get(1)).isEqualTo(new QuizQuestionPoolLectureUnitDTO(9L, "Arrays", 6L, "Arrays, continued", "Lecture 1", true, false, 0, 0, null));
    }

    @Test
    void updateLectureUnitRelevance_excludesTheUnitAndDropsItsUnreviewedItems() {
        TextUnit unit = unitOfCourse(4L, "Arrays in Java", 5L);
        when(materialService.findUnits(List.of(4L))).thenReturn(Map.of(4L, unit));
        when(courseRepository.findByIdElseThrow(5L)).thenReturn(course);
        when(buildService.tryStart(5L)).thenReturn("lease");

        service.updateLectureUnitRelevance(5L, 4L, true);

        verify(lectureUnitRepository).save(any(QuizQuestionPoolLectureUnit.class));
        verify(itemRepository).deleteAllByLectureUnitExceptReviewState(5L, 4L, QuizQuestionReviewState.APPROVED);
        verify(buildService).release(5L, "lease");
    }

    @Test
    void updateLectureUnitRelevance_refusesToExcludeWhileABuildRunsButIncludes() {
        TextUnit unit = unitOfCourse(4L, "Arrays in Java", 5L);
        when(materialService.findUnits(List.of(4L))).thenReturn(Map.of(4L, unit));
        when(buildService.tryStart(5L)).thenReturn(null);
        when(courseRepository.findByIdElseThrow(5L)).thenReturn(course);

        assertThatThrownBy(() -> service.updateLectureUnitRelevance(5L, 4L, true)).isInstanceOf(ConflictException.class);
        verify(itemRepository, never()).deleteAllByLectureUnitExceptReviewState(anyLong(), anyLong(), any());
        verify(lectureUnitRepository, never()).save(any(QuizQuestionPoolLectureUnit.class));

        service.updateLectureUnitRelevance(5L, 4L, false);
        verify(lectureUnitRepository).updateExcluded(eq(5L), eq(4L), eq(false), any(ZonedDateTime.class));
        verify(lectureUnitRepository, never()).save(any(QuizQuestionPoolLectureUnit.class));
    }

    @Test
    void updateLectureUnitRelevance_changesOnlyTheExclusionOfAnExistingRecord() {
        TextUnit unit = unitOfCourse(4L, "Arrays in Java", 5L);
        when(materialService.findUnits(List.of(4L))).thenReturn(Map.of(4L, unit));
        when(buildService.tryStart(5L)).thenReturn("lease");
        when(lectureUnitRepository.updateExcluded(eq(5L), eq(4L), eq(true), any(ZonedDateTime.class))).thenReturn(1);

        service.updateLectureUnitRelevance(5L, 4L, true);

        verify(lectureUnitRepository, never()).save(any(QuizQuestionPoolLectureUnit.class));
        verify(itemRepository).deleteAllByLectureUnitExceptReviewState(5L, 4L, QuizQuestionReviewState.APPROVED);
    }

    @Test
    void updateLectureUnitRelevance_rejectsAUnitOfAnotherCourse() {
        TextUnit unit = unitOfCourse(4L, "Arrays in Java", 6L);
        when(materialService.findUnits(List.of(4L))).thenReturn(Map.of(4L, unit));

        assertThatThrownBy(() -> service.updateLectureUnitRelevance(5L, 4L, true)).isInstanceOf(EntityNotFoundException.class);

        verify(lectureUnitRepository, never()).save(any(QuizQuestionPoolLectureUnit.class));
    }

    private static TextUnit unitOfCourse(long unitId, String name, long courseId) {
        Course owner = new Course();
        owner.setId(courseId);
        Lecture lecture = new Lecture();
        lecture.setTitle("Lecture 1");
        lecture.setCourse(owner);
        TextUnit unit = new TextUnit();
        unit.setId(unitId);
        unit.setName(name);
        unit.setContent("content");
        unit.setLecture(lecture);
        return unit;
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

        assertThat(page.pendingCount()).isNull();
        verify(itemRepository, never()).findAllWithoutFlagForCourse(anyLong(), any());

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
        when(itemRepository.findAllWithoutFlagForCourse(5L, QuizQuestionPoolItemState.FILTERED)).thenReturn(List.of(item));
        when(itemRepository.findFlaggedPageForCourse(5L, PageRequest.of(1, 50))).thenReturn(new PageImpl<>(List.of(item), PageRequest.of(1, 50), 51));
        when(itemRepository.countFlaggedPendingForCourse(5L)).thenReturn(7L);
        when(lectureUnitApi.findAllByIds(anyCollection())).thenReturn(List.of());

        QuizQuestionPoolPageDTO page = service.getItems(5L, null, true, 1, 50);

        verify(itemRepository).storeFlag(2L, true);
        assertThat(page.totalCount()).isEqualTo(51);
        assertThat(page.pendingCount()).isEqualTo(7L);
        assertThat(page.items().getFirst().flagged()).isTrue();
        assertThat(page.items().getFirst().decision().verdicts().getFirst().flagged()).isTrue();
    }

    @Test
    void deleteItem_removesAnItemOfTheCourseWhileNoBuildRuns() {
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setId(3L);
        item.setCourse(course);
        when(itemRepository.findByIdElseThrow(3L)).thenReturn(item);
        when(buildService.tryStart(5L)).thenReturn("lease");

        service.deleteItem(5L, 3L);

        InOrder order = inOrder(buildService, itemRepository);
        order.verify(buildService).tryStart(5L);
        order.verify(itemRepository).deleteById(3L);
        order.verify(buildService).release(5L, "lease");
    }

    @Test
    void deleteItem_refusesAnItemOfAnotherCourseAndADeletionDuringABuild() {
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setId(3L);
        item.setCourse(course);
        when(itemRepository.findByIdElseThrow(3L)).thenReturn(item);

        assertThatThrownBy(() -> service.deleteItem(6L, 3L)).isInstanceOf(EntityNotFoundException.class);
        when(buildService.tryStart(5L)).thenReturn(null);
        assertThatThrownBy(() -> service.deleteItem(5L, 3L)).isInstanceOf(ConflictException.class);
        verify(itemRepository, never()).deleteById(anyLong());
        verify(buildService, never()).release(anyLong(), any());
    }

    @Test
    void deleteItems_clearsTheCompetencyOrThePoolUnderTheBuildLeaseAndReportsTheCount() {
        when(buildService.tryStart(5L)).thenReturn("lease");
        when(itemRepository.deleteAllForCourse(5L, 9L)).thenReturn(4);
        when(itemRepository.deleteAllForCourse(5L, null)).thenReturn(12);

        assertThat(service.deleteItems(5L, 9L).deleted()).isEqualTo(4);
        assertThat(service.deleteItems(5L, null).deleted()).isEqualTo(12);
        verify(buildService, times(2)).release(5L, "lease");

        when(buildService.tryStart(5L)).thenReturn(null);
        assertThatThrownBy(() -> service.deleteItems(5L, null)).isInstanceOf(ConflictException.class);
        verify(itemRepository, never()).deleteAllForCourse(5L, 10L);
    }

    @Test
    void deleteItems_releasesTheBuildLeaseWhenTheDeletionFails() {
        when(buildService.tryStart(5L)).thenReturn("lease");
        when(itemRepository.deleteAllForCourse(5L, null)).thenThrow(new IllegalStateException("database down"));

        assertThatThrownBy(() -> service.deleteItems(5L, null)).isInstanceOf(IllegalStateException.class);

        verify(buildService).release(5L, "lease");
    }

    @Test
    void getItems_rejectsAPageOutOfRange() {
        assertThatThrownBy(() -> service.getItems(5L, null, false, -1, 100)).isInstanceOf(BadRequestAlertException.class);
        assertThatThrownBy(() -> service.getItems(5L, null, false, 0, 501)).isInstanceOf(BadRequestAlertException.class);
        assertThatThrownBy(() -> service.getItems(5L, null, false, 0, 0)).isInstanceOf(BadRequestAlertException.class);
    }
}
