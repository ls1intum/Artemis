package de.tum.cit.aet.artemis.lecture.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

@Lazy
@Service
@Conditional(LectureEnabled.class)
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentRepository attachmentRepository;

    private final SlideRepository slideRepository;

    private final FileService fileService;

    private final TempFileUtilService tempFileUtilService;

    private final TransactionAfterCommitService transactionAfterCommitService;

    public AttachmentService(AttachmentRepository attachmentRepository, SlideRepository slideRepository, FileService fileService, TempFileUtilService tempFileUtilService,
            TransactionAfterCommitService transactionAfterCommitService) {
        this.attachmentRepository = attachmentRepository;
        this.slideRepository = slideRepository;
        this.fileService = fileService;
        this.tempFileUtilService = tempFileUtilService;
        this.transactionAfterCommitService = transactionAfterCommitService;
    }

    /**
     * Updates a lecture attachment while deriving its cache-busting version from the persisted state instead of trusting the client payload. Metadata-only updates preserve the
     * stored version, while file replacements increment it.
     *
     * @param attachmentId     the attachment to update
     * @param attachmentUpdate client-provided metadata
     * @param file             replacement file, or {@code null} for a metadata-only update
     * @return the updated attachment
     */
    public Attachment updateLectureAttachment(Long attachmentId, Attachment attachmentUpdate, MultipartFile file) {
        Attachment existingAttachment = attachmentRepository.findByIdOrElseThrow(attachmentId);

        existingAttachment.setName(attachmentUpdate.getName());
        existingAttachment.setReleaseDate(attachmentUpdate.getReleaseDate());
        existingAttachment.setUploadDate(attachmentUpdate.getUploadDate());
        existingAttachment.setAttachmentType(attachmentUpdate.getAttachmentType());

        if (file != null) {
            if (existingAttachment.getLecture() == null || existingAttachment.getLecture().getId() == null || existingAttachment.getLink() == null
                    || existingAttachment.getLink().isBlank()) {
                throw new BadRequestAlertException("The attachment must belong to a persisted lecture and have an existing file", "attachment", "invalidLectureAttachment");
            }

            Path oldFilePath;
            try {
                URI oldPath = URI.create(existingAttachment.getLink());
                oldFilePath = FilePathConverter.fileSystemPathForExternalUri(oldPath, FilePathType.LECTURE_ATTACHMENT);
            }
            catch (IllegalArgumentException exception) {
                throw new BadRequestAlertException("The attachment has an invalid file link", "attachment", "invalidLectureAttachment");
            }

            Path basePath = FilePathConverter.getLectureAttachmentFileSystemPath().resolve(existingAttachment.getLecture().getId().toString());
            Path savePath = FileUtil.saveFile(file, basePath, FilePathType.LECTURE_ATTACHMENT, true);
            fileService.schedulePathForDeletion(oldFilePath, 0);
            fileService.evictCacheForPath(oldFilePath);
            existingAttachment
                    .setLink(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.LECTURE_ATTACHMENT, existingAttachment.getLecture().getId()).toString());
            existingAttachment.setVersion(existingAttachment.getVersion() == null ? 1 : existingAttachment.getVersion() + 1);
        }

        return attachmentRepository.save(existingAttachment);
    }

    /**
     * Regenerates the student version of an attachment based on currently visible slides.
     * This should be called after slides are unhidden to ensure the student version is up-to-date.
     * Persisted attachments are reloaded with a pessimistic lock. Callers without a surrounding transaction that pass a detached attachment must reload the attachment to
     * observe changes to its student-version reference.
     *
     * @param attachment The attachment whose student version needs to be regenerated
     */
    @Transactional
    public void regenerateStudentVersion(Attachment attachment) {
        attachment = lockAttachmentIfPersisted(attachment);
        AttachmentVideoUnit attachmentVideoUnit = attachment.getAttachmentVideoUnit();
        if (attachmentVideoUnit == null) {
            return;
        }

        List<Slide> hiddenSlides = slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNull(attachmentVideoUnit.getId());

        // If no slides are marked as hidden, remove student version if it exists
        if (hiddenSlides.isEmpty()) {
            removeStudentVersionFile(attachment);
            return;
        }

        try {
            String originalPdfPath = attachment.getLink();
            Path pdfPath = FilePathConverter.fileSystemPathForExternalUri(URI.create(originalPdfPath), FilePathType.ATTACHMENT_UNIT);

            byte[] studentVersionPdf = generateStudentVersionPdf(pdfPath.toFile(), hiddenSlides);

            replaceStudentVersionFile(studentVersionPdf, attachment, attachmentVideoUnit.getId());
        }
        catch (Exception e) {
            throw new InternalServerErrorException("Failed to regenerate student version: " + e.getMessage());
        }
    }

    /**
     * Attempts regeneration while keeping the surrounding visibility transaction committable on file-generation failure.
     *
     * @param attachment the attachment whose student version should be regenerated
     * @return whether regeneration succeeded
     */
    @Transactional
    public boolean regenerateStudentVersionOrLeavePending(Attachment attachment) {
        try {
            regenerateStudentVersion(attachment);
            return true;
        }
        catch (RuntimeException exception) {
            log.error("Failed to regenerate student version for attachment {}; leaving it pending for retry: {}", attachment.getId(), exception.getMessage(), exception);
            return false;
        }
    }

    @Transactional
    public void markStudentVersionRegenerationPending(Attachment attachment) {
        removeStudentVersionFile(lockAttachmentIfPersisted(attachment));
    }

    private Attachment lockAttachmentIfPersisted(Attachment attachment) {
        if (attachment.getId() == null || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return attachment;
        }
        return attachmentRepository.findByIdWithPessimisticWriteLock(attachment.getId())
                .orElseThrow(() -> new IllegalStateException("Attachment " + attachment.getId() + " no longer exists"));
    }

    /**
     * Clears the persisted student-version reference and deletes the old file after
     * the surrounding transaction commits.
     *
     * @param attachment the attachment whose student version should be removed
     */
    public void removeStudentVersionFile(Attachment attachment) {
        if (attachment.getStudentVersion() == null) {
            return;
        }
        String oldStudentVersion = attachment.getStudentVersion();
        attachment.setStudentVersion(null);
        try {
            attachmentRepository.saveAndFlush(attachment);
        }
        catch (RuntimeException exception) {
            attachment.setStudentVersion(oldStudentVersion);
            throw exception;
        }
        deleteStudentVersionFileAfterCommit(oldStudentVersion);
    }

    /**
     * Deletes the student version file and cleans up associated resources.
     *
     * @param studentVersion the external URI of the student version to delete
     */
    private void deleteStudentVersionFile(String studentVersion) {
        if (studentVersion != null) {
            try {
                URI oldStudentVersionPath = URI.create(studentVersion);
                fileService.schedulePathForDeletion(FilePathConverter.fileSystemPathForExternalUri(oldStudentVersionPath, FilePathType.STUDENT_VERSION_SLIDES), 0);
                fileService.evictCacheForPath(FilePathConverter.fileSystemPathForExternalUri(oldStudentVersionPath, FilePathType.STUDENT_VERSION_SLIDES));
            }
            catch (Exception e) {
                throw new InternalServerErrorException("Failed to delete student version file: " + e.getMessage());
            }
        }
    }

    /**
     * Generates a student version PDF by removing hidden slides from the original.
     *
     * @param originalPdf  The original PDF file
     * @param hiddenSlides List of hidden slides
     * @return Byte array containing the student version PDF
     */
    byte[] generateStudentVersionPdf(File originalPdf, List<Slide> hiddenSlides) throws IOException {
        try (PDDocument doc = Loader.loadPDF(originalPdf)) {
            hiddenSlides.stream().map(Slide::getSlideNumber).map(slideNumber -> slideNumber - 1).sorted(Comparator.reverseOrder()).forEach(doc::removePage);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);

            return baos.toByteArray();
        }
    }

    /**
     * Handles the student version file of an attachment, updates its reference in the database,
     * and deletes the old version if it exists.
     *
     * @param pdfData               The PDF data as byte array
     * @param attachment            The existing attachment
     * @param attachmentVideoUnitId The id of the attachment video unit
     * @throws IOException If there's an error handling the file
     */
    public void replaceStudentVersionFile(byte[] pdfData, Attachment attachment, Long attachmentVideoUnitId) throws IOException {
        String sanitizedName = FileUtil.checkAndSanitizeFilename(attachment.getName());
        String filename = FileUtil.generateFilename(FileUtil.generateTargetFilenameBase(FilePathType.STUDENT_VERSION_SLIDES), sanitizedName + ".pdf", false);
        persistStudentVersionFile(pdfData, attachment, attachmentVideoUnitId, filename);
    }

    /**
     * Replaces a manually uploaded student version while preserving the uploaded filename semantics.
     *
     * @param pdfData               the uploaded PDF bytes
     * @param attachment            the attachment to update
     * @param attachmentVideoUnitId the attachment video unit id
     * @param originalFilename      the client-provided filename
     * @throws IOException if the file cannot be installed
     */
    public void replaceUploadedStudentVersionFile(byte[] pdfData, Attachment attachment, Long attachmentVideoUnitId, String originalFilename) throws IOException {
        String sanitizedFilename = FileUtil.checkAndSanitizeFilename(originalFilename);
        FileUtil.validateExtension(sanitizedFilename, false);
        String filename = FileUtil.generateFilename(FileUtil.generateTargetFilenameBase(FilePathType.STUDENT_VERSION_SLIDES), sanitizedFilename, true);
        persistStudentVersionFile(pdfData, attachment, attachmentVideoUnitId, filename);
    }

    private void persistStudentVersionFile(byte[] pdfData, Attachment attachment, Long attachmentVideoUnitId, String filename) throws IOException {
        Path basePath = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(attachmentVideoUnitId.toString()).resolve("student");
        Path savePath = basePath.resolve(filename);
        String oldStudentVersion = attachment.getStudentVersion();
        String newStudentVersion = FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.STUDENT_VERSION_SLIDES, attachmentVideoUnitId).toString();

        try {
            tempFileUtilService.replaceFileAtomically(FilePathConverter.getAttachmentVideoUnitFileSystemPath(), savePath, pdfData);
            fileService.evictCacheForPath(savePath);
            attachment.setStudentVersion(newStudentVersion);
            attachmentRepository.saveAndFlush(attachment);
        }
        catch (RuntimeException | IOException exception) {
            attachment.setStudentVersion(oldStudentVersion);
            fileService.schedulePathForDeletion(savePath, 0);
            fileService.evictCacheForPath(savePath);
            throw exception;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    if (oldStudentVersion != null && !oldStudentVersion.equals(newStudentVersion)) {
                        deleteStudentVersionFile(oldStudentVersion);
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        attachment.setStudentVersion(oldStudentVersion);
                        fileService.schedulePathForDeletion(savePath, 0);
                        fileService.evictCacheForPath(savePath);
                    }
                }
            });
        }
        else if (oldStudentVersion != null && !oldStudentVersion.equals(newStudentVersion)) {
            deleteStudentVersionFile(oldStudentVersion);
        }
    }

    private void deleteStudentVersionFileAfterCommit(String studentVersion) {
        transactionAfterCommitService.execute(() -> deleteStudentVersionFile(studentVersion));
    }
}
