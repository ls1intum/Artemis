package de.tum.cit.aet.artemis.lecture.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.dto.HiddenPageInfoDTO;
import de.tum.cit.aet.artemis.lecture.dto.SlideOrderDTO;

@Conditional(LectureEnabled.class)
@Lazy
@Service
public class AttachmentVideoUnitPostCommitService {

    private final SlideSplitterService slideSplitterService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<LectureContentProcessingService> contentProcessingService;

    private final TransactionAfterCommitService transactionAfterCommitService;

    public AttachmentVideoUnitPostCommitService(SlideSplitterService slideSplitterService, Optional<CompetencyProgressApi> competencyProgressApi,
            Optional<LectureContentProcessingService> contentProcessingService, TransactionAfterCommitService transactionAfterCommitService) {
        this.slideSplitterService = slideSplitterService;
        this.competencyProgressApi = competencyProgressApi;
        this.contentProcessingService = contentProcessingService;
        this.transactionAfterCommitService = transactionAfterCommitService;
    }

    public void triggerContentProcessing(AttachmentVideoUnit attachmentVideoUnit) {
        transactionAfterCommitService.execute(() -> contentProcessingService.ifPresent(service -> service.triggerProcessing(attachmentVideoUnit)));
    }

    public void updateCompetencyProgress(Set<Long> originalCompetencyIds, AttachmentVideoUnit attachmentVideoUnit) {
        transactionAfterCommitService.execute(
                () -> competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(originalCompetencyIds, attachmentVideoUnit)));
    }

    public void splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnit attachmentVideoUnit) {
        AttachmentVideoUnitSlideSplitJob job = AttachmentVideoUnitSlideSplitJob.of(attachmentVideoUnit, null, null);
        transactionAfterCommitService.execute(() -> slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(job));
    }

    public void splitAttachmentVideoUnitIntoSingleSlides(AttachmentVideoUnit attachmentVideoUnit, List<HiddenPageInfoDTO> hiddenPages, List<SlideOrderDTO> pageOrder) {
        AttachmentVideoUnitSlideSplitJob job = AttachmentVideoUnitSlideSplitJob.of(attachmentVideoUnit, hiddenPages, pageOrder);
        transactionAfterCommitService.execute(() -> slideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(job));
    }
}
