package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.atlas.config.AtlasOrchestratorProperties;
import de.tum.cit.aet.artemis.atlas.domain.competency.ContentChangeAccumulator;
import de.tum.cit.aet.artemis.atlas.dto.CourseAutoOrchestrationConfigDTO;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.domain.event.LectureUnitContentChangedEvent;

/**
 * Behaviour of {@link AutonomousCompetencyLectureUnitEventListener} — the event listener that feeds
 * the automatic pipeline from lecture-unit content-changed events. Verifies the feature-toggle gate,
 * the per-course kill switch (with flush-on-disable), the ExerciseUnit skip, and the null guards
 * without needing a full Spring context (mirrors {@link AutonomousCompetencyExerciseEventListenerTest}).
 */
@ExtendWith(MockitoExtension.class)
class AutonomousCompetencyLectureUnitEventListenerTest {

    private static final long COURSE_ID = 77L;

    private static final long LECTURE_UNIT_ID = 30L;

    @Mock
    private ContentChangeAccumulatorService accumulator;

    @Mock
    private FeatureToggleService featureToggleService;

    @Mock
    private CourseConfigurationRepository courseConfigurationRepository;

    @Mock
    private LectureUnitRepositoryApi lectureUnitRepositoryApi;

    private AutonomousCompetencyLectureUnitEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AutonomousCompetencyLectureUnitEventListener(accumulator, featureToggleService, courseConfigurationRepository, Optional.of(lectureUnitRepositoryApi));
    }

    private void stubCourseEnabled(boolean enabled) {
        when(courseConfigurationRepository.findAutoOrchestrationConfigByCourseId(COURSE_ID)).thenReturn(Optional.of(new CourseAutoOrchestrationConfigDTO(enabled, null, null)));
    }

    @Test
    void onLectureUnitContentChanged_toggleEnabledCourseEnabled_recordsAccumulator() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(true);
        TextUnit unit = courseLectureUnit();
        when(lectureUnitRepositoryApi.findWithLectureById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(unit));

        verifyRefresh(true);
    }

    @Test
    void onLectureUnitContentChanged_toggleDisabled_doesNothing() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(false);

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(courseLectureUnit()));

        verify(accumulator, never()).refreshLectureUnit(anyLong(), anyLong(), any());
        verify(accumulator, never()).flush(anyLong());
    }

    @Test
    void onLectureUnitContentChanged_courseDisabled_flushesAndDoesNotRecord() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(false);

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(courseLectureUnit()));

        verify(accumulator).flush(COURSE_ID);
        verify(accumulator, never()).refreshLectureUnit(anyLong(), anyLong(), any());
    }

    @Test
    void onLectureUnitContentChanged_exerciseUnit_skipped() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setLecture(lectureInCourse());

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(exerciseUnit));

        verify(accumulator, never()).refreshLectureUnit(anyLong(), anyLong(), any());
        verify(accumulator, never()).flush(anyLong());
    }

    @Test
    void onLectureUnitContentChanged_nullLectureUnit_safe() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(null));

        verify(accumulator, never()).refreshLectureUnit(anyLong(), anyLong(), any());
    }

    @Test
    void onLectureUnitContentChanged_noLecture_safe() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        TextUnit unit = new TextUnit();
        unit.setId(LECTURE_UNIT_ID);
        // No lecture set: the null guard must short-circuit.

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(unit));

        verify(accumulator, never()).refreshLectureUnit(anyLong(), anyLong(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t\n" })
    void blankAttachmentUpdate_doesNotRecord(String description) {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(true);
        AttachmentVideoUnit unit = attachmentUnit(description);
        when(lectureUnitRepositoryApi.findWithLectureById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(unit));

        verifyRefresh(false);
    }

    @Test
    void attachmentWithDescription_records() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(true);

        AttachmentVideoUnit unit = attachmentUnit("Recursion basics");
        when(lectureUnitRepositoryApi.findWithLectureById(LECTURE_UNIT_ID)).thenReturn(Optional.of(unit));
        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(unit));

        verifyRefresh(true);
    }

    @Test
    void blankAttachmentWhenCourseDisabled_stillFlushes() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(false);

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(attachmentUnit("")));

        verify(accumulator).flush(COURSE_ID);
        verify(accumulator, never()).refreshLectureUnit(anyLong(), anyLong(), any());
    }

    @Test
    void nonblankToBlankBurst_removesPreviouslyBufferedId() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(true);
        LocalDataProviderService provider = new LocalDataProviderService();
        AtlasOrchestratorProperties properties = new AtlasOrchestratorProperties("test", 1.0, "", "test", "high", false, 60, 3, 30000L, 10);
        ContentChangeAccumulatorService realAccumulator = new ContentChangeAccumulatorService(Optional.of(provider), Clock.systemUTC(), properties, courseConfigurationRepository);
        listener = new AutonomousCompetencyLectureUnitEventListener(realAccumulator, featureToggleService, courseConfigurationRepository, Optional.of(lectureUnitRepositoryApi));
        AttachmentVideoUnit nonblank = attachmentUnit("Recursion basics");
        AttachmentVideoUnit blank = attachmentUnit(" ");
        when(lectureUnitRepositoryApi.findWithLectureById(LECTURE_UNIT_ID)).thenReturn(Optional.of(nonblank), Optional.of(blank));

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(nonblank));
        assertThat(provider.<Long, ContentChangeAccumulator>getMap(ContentChangeAccumulatorService.MAP_NAME).get(COURSE_ID).lectureUnitIds()).containsExactly(LECTURE_UNIT_ID);
        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(blank));

        assertThat(realAccumulator.claimBatchNow(COURSE_ID)).isEmpty();
    }

    @Test
    void delayedEligibleEvent_usesCurrentBlankDescription() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(true);
        when(lectureUnitRepositoryApi.findWithLectureById(LECTURE_UNIT_ID)).thenReturn(Optional.of(attachmentUnit(" ")));

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(attachmentUnit("Earlier description")));

        verifyRefresh(false);
    }

    @Test
    void delayedBlankEvent_preservesCurrentlyEligibleUnit() {
        when(featureToggleService.isFeatureEnabled(Feature.AtlasAgent)).thenReturn(true);
        stubCourseEnabled(true);
        when(lectureUnitRepositoryApi.findWithLectureById(LECTURE_UNIT_ID)).thenReturn(Optional.of(attachmentUnit("Current description")));

        listener.onLectureUnitContentChanged(new LectureUnitContentChangedEvent(attachmentUnit("")));

        verifyRefresh(true);
    }

    private void verifyRefresh(boolean eligible) {
        ArgumentCaptor<BooleanSupplier> lookup = ArgumentCaptor.forClass(BooleanSupplier.class);
        verify(accumulator).refreshLectureUnit(eq(COURSE_ID), eq(LECTURE_UNIT_ID), lookup.capture());
        assertThat(lookup.getValue().getAsBoolean()).isEqualTo(eligible);
    }

    private AttachmentVideoUnit attachmentUnit(String description) {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        unit.setLecture(lectureInCourse());
        unit.setDescription(description);
        return unit;
    }

    private TextUnit courseLectureUnit() {
        TextUnit unit = new TextUnit();
        unit.setId(LECTURE_UNIT_ID);
        unit.setName("Recursion basics");
        unit.setLecture(lectureInCourse());
        return unit;
    }

    private static Lecture lectureInCourse() {
        Course course = new Course();
        course.setId(COURSE_ID);
        Lecture lecture = new Lecture();
        lecture.setCourse(course);
        return lecture;
    }
}
