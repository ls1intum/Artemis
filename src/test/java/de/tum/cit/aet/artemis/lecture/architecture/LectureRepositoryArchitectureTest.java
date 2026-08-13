package de.tum.cit.aet.artemis.lecture.architecture;

import java.util.Set;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleRepositoryArchitectureTest;

class LectureRepositoryArchitectureTest extends AbstractModuleRepositoryArchitectureTest {

    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".lecture";
    }

    // TODO: This method should be removed once all repositories are tested
    @Override
    protected Set<String> testTransactionalExclusions() {
        return Set.of(
                "de.tum.cit.aet.artemis.lecture.service.LectureImportService.importLecture(de.tum.cit.aet.artemis.lecture.domain.Lecture, de.tum.cit.aet.artemis.course.domain.Course, boolean)",
                // dispatchPendingJobs needs @Transactional because it uses FOR UPDATE SKIP LOCKED and its callers have no transaction context.
                "de.tum.cit.aet.artemis.lecture.service.ProcessingStateCallbackService.dispatchPendingJobs()",
                "de.tum.cit.aet.artemis.lecture.service.ProcessingStateCallbackService.handleIngestionComplete(java.lang.Long, java.lang.String, boolean, java.lang.String, java.util.List)",
                "de.tum.cit.aet.artemis.lecture.service.ProcessingStateCallbackService.handleCheckpointData(long, java.lang.String, java.lang.String)",
                "de.tum.cit.aet.artemis.lecture.service.ProcessingStateCallbackService.handleHeartbeat(long, java.lang.String)",
                "de.tum.cit.aet.artemis.lecture.service.LectureContentProcessingService.prepareForReprocessing(de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit)",
                "de.tum.cit.aet.artemis.lecture.service.IrisLectureUnitSyncService.markCurrentStateDirtyAfterIngestion(long)",
                "de.tum.cit.aet.artemis.lecture.service.IrisLectureUnitSyncDispatchService.triggerSyncForUpdateKind(de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit, de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind)",
                "de.tum.cit.aet.artemis.lecture.service.IrisLectureUnitSyncDispatchService.triggerSyncForUpdateKind(de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit, de.tum.cit.aet.artemis.lecture.domain.LectureContentUpdateKind, java.util.Map)",
                // Slide splitting holds a pessimistic unit lock while coordinating database rows with file-system rollback and after-commit actions.
                "de.tum.cit.aet.artemis.lecture.service.SlideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(de.tum.cit.aet.artemis.lecture.service.AttachmentVideoUnitSlideSplitJob)",
                "de.tum.cit.aet.artemis.lecture.service.SlideSplitterService.updateSlideVisibility(de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit, java.util.List)",
                "de.tum.cit.aet.artemis.lecture.service.SlideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(org.apache.pdfbox.pdmodel.PDDocument, de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit, java.lang.String)",
                "de.tum.cit.aet.artemis.lecture.service.SlideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(org.apache.pdfbox.pdmodel.PDDocument, de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit, java.lang.String, java.util.List, java.util.List)");
    }
}
