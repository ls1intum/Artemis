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
                // Regeneration must hold the attachment row lock while coordinating database state with student-PDF file replacement, including for callers without a transaction.
                "de.tum.cit.aet.artemis.lecture.service.AttachmentService.regenerateStudentVersion(de.tum.cit.aet.artemis.lecture.domain.Attachment)",
                // Keep the defensive attachment lock active if a caller does not already have a transaction.
                "de.tum.cit.aet.artemis.lecture.service.AttachmentService.markStudentVersionRegenerationPending(de.tum.cit.aet.artemis.lecture.domain.Attachment)",
                // These entry points update slide visibility and coordinate student-version regeneration in one transaction.
                "de.tum.cit.aet.artemis.lecture.service.SlideService.handleDueDateChange(de.tum.cit.aet.artemis.exercise.domain.Exercise, de.tum.cit.aet.artemis.exercise.domain.Exercise)",
                "de.tum.cit.aet.artemis.lecture.service.SlideService.handleDueDateChange(java.time.ZonedDateTime, de.tum.cit.aet.artemis.exercise.domain.Exercise)",
                "de.tum.cit.aet.artemis.lecture.service.SlideService.updateSlidesHiddenDate(de.tum.cit.aet.artemis.exercise.domain.Exercise)",
                // Slide splitting holds a pessimistic unit lock while coordinating database rows with file-system rollback and after-commit actions.
                "de.tum.cit.aet.artemis.lecture.service.SlideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(de.tum.cit.aet.artemis.lecture.service.AttachmentVideoUnitSlideSplitJob)",
                "de.tum.cit.aet.artemis.lecture.service.SlideSplitterService.updateSlideVisibility(de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit, java.util.List)",
                "de.tum.cit.aet.artemis.lecture.service.SlideVisibilityUpdateService.updateVisibilityAndStudentVersion(de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit, java.util.List)",
                "de.tum.cit.aet.artemis.lecture.service.SlideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(org.apache.pdfbox.pdmodel.PDDocument, de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit, java.lang.String)",
                "de.tum.cit.aet.artemis.lecture.service.SlideSplitterService.splitAttachmentVideoUnitIntoSingleSlides(org.apache.pdfbox.pdmodel.PDDocument, de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit, java.lang.String, java.util.List, java.util.List)");
    }
}
