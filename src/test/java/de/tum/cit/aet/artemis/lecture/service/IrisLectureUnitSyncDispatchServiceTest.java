package de.tum.cit.aet.artemis.lecture.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.iris.api.IrisLectureUnitSyncApi;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.test_repository.SlideTestRepository;

class IrisLectureUnitSyncDispatchServiceTest {

    private static final long LECTURE_UNIT_ID = 30L;

    private SlideTestRepository slideRepository;

    private IrisLectureUnitSyncApi irisLectureUnitSyncApi;

    private IrisLectureUnitSyncDispatchService service;

    @BeforeEach
    void setUp() {
        slideRepository = mock(SlideTestRepository.class);
        irisLectureUnitSyncApi = mock(IrisLectureUnitSyncApi.class);

        service = new IrisLectureUnitSyncDispatchService(slideRepository, Optional.of(irisLectureUnitSyncApi));
    }

    @Test
    void triggerSyncForUpdateKindRoutesMetadataWithoutLoadingSlides() {
        AttachmentVideoUnit unit = attachmentVideoUnit();

        service.triggerSyncForUpdateKind(unit, LectureContentUpdateKind.METADATA);

        verify(irisLectureUnitSyncApi).updateLectureUnitMetadataInPyris(unit);
        verify(irisLectureUnitSyncApi, never()).updateLectureUnitVisibilityInPyris(any(), any());
        verify(slideRepository, never()).findAllByAttachmentVideoUnitId(any());
    }

    @Test
    void triggerSyncForUpdateKindRoutesVisibilityWithUnitSlides() {
        AttachmentVideoUnit unit = attachmentVideoUnit();
        List<Slide> slides = List.of(slide(2, null), slide(1, ZonedDateTime.parse("2026-07-03T10:15:30+02:00[Europe/Berlin]")));
        when(slideRepository.findAllByAttachmentVideoUnitId(LECTURE_UNIT_ID)).thenReturn(slides);

        service.triggerSyncForUpdateKind(unit, LectureContentUpdateKind.VISIBILITY);

        ArgumentCaptor<List<Slide>> slidesCaptor = ArgumentCaptor.captor();
        verify(irisLectureUnitSyncApi).updateLectureUnitVisibilityInPyris(eq(unit), slidesCaptor.capture());
        assertThat(slidesCaptor.getValue()).containsExactlyElementsOf(slides);
        verify(irisLectureUnitSyncApi, never()).updateLectureUnitMetadataInPyris(any());
    }

    @Test
    void triggerSyncForUpdateKindDoesNothingWhenIrisApiIsUnavailable() {
        service = new IrisLectureUnitSyncDispatchService(slideRepository, Optional.empty());

        service.triggerSyncForUpdateKind(attachmentVideoUnit(), LectureContentUpdateKind.METADATA);
        service.triggerSyncForUpdateKind(attachmentVideoUnit(), LectureContentUpdateKind.VISIBILITY);

        verify(slideRepository, never()).findAllByAttachmentVideoUnitId(any());
    }

    @Test
    void triggerSyncForUpdateKindRejectsContentUpdates() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.triggerSyncForUpdateKind(attachmentVideoUnit(), LectureContentUpdateKind.CONTENT))
                .withMessage("Only metadata and visibility updates are supported by the retryable sync dispatcher");
    }

    private static AttachmentVideoUnit attachmentVideoUnit() {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setId(LECTURE_UNIT_ID);
        return unit;
    }

    private static Slide slide(int slideNumber, ZonedDateTime hidden) {
        Slide slide = new Slide();
        slide.setSlideNumber(slideNumber);
        slide.setHidden(hidden);
        return slide;
    }
}
