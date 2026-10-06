package de.tum.cit.aet.artemis.lecture.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
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

    /**
     * Only the extension of this name ends up in a student version filename; the rest is a timestamp and a random part. No part of a name an instructor chose reaches the
     * file system.
     */
    private static final String STUDENT_VERSION_FILENAME_TEMPLATE = "student_version.pdf";

    private final AttachmentRepository attachmentRepository;

    private final SlideRepository slideRepository;

    private final FileService fileService;

    private final TempFileUtilService tempFileUtilService;

    public AttachmentService(AttachmentRepository attachmentRepository, SlideRepository slideRepository, FileService fileService, TempFileUtilService tempFileUtilService) {
        this.attachmentRepository = attachmentRepository;
        this.slideRepository = slideRepository;
        this.fileService = fileService;
        this.tempFileUtilService = tempFileUtilService;
    }

    /**
     * Regenerates the student version of an attachment based on the hidden slides of its current deck.
     * This should be called after slide visibility changed to ensure the student version is up-to-date.
     * <p>
     * A new student version is written under a fresh filename, its reference is stored, and only then is the previous file deleted. A failure therefore never deletes a file
     * the database still references.
     *
     * @param attachment The attachment whose student version needs to be regenerated
     */
    public void regenerateStudentVersion(Attachment attachment) {
        AttachmentVideoUnit attachmentVideoUnit = attachment.getAttachmentVideoUnit();
        if (attachmentVideoUnit == null) {
            return;
        }

        List<Slide> hiddenSlides = slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(attachmentVideoUnit.getId());

        // If no slides are marked as hidden, remove student version if it exists
        if (hiddenSlides.isEmpty()) {
            removeStudentVersionFile(attachment);
            return;
        }

        // The attachment says where its file is; a unit created for an attachment that used to hang off a lecture still has it under that lecture's directory. An attachment
        // that links to a document hosted elsewhere has no file here to redact, and slides are only ever split out of a stored PDF, so hidden slides on such an attachment are
        // a data inconsistency rather than something to regenerate from.
        Optional<FileSystemLocation> fileLocation = attachment.fileLocation();
        if (fileLocation.isEmpty()) {
            log.warn("Attachment {} links to a document this application does not store, so no student version can be regenerated for its {} hidden slide(s).", attachment.getId(),
                    hiddenSlides.size());
            return;
        }

        try {
            Path pdfPath = fileLocation.get().path();
            byte[] studentVersionPdf = generateStudentVersionPdf(pdfPath.toFile(), hiddenSlides);
            replaceStudentVersionFile(studentVersionPdf, attachment, attachmentVideoUnit.getId());
        }
        catch (Exception e) {
            throw new InternalServerErrorException("Failed to regenerate student version: " + e.getMessage(), e);
        }
    }

    /**
     * Regenerates the student version without letting a failure abort the caller.
     * <p>
     * Callers use this after slide visibility has already been saved, so a failure must not undo or interrupt the rest of their work. If regeneration fails, the student version
     * this attempt started from no longer matches the slides, so its reference is removed. With hidden slides left, downloads then fail closed instead of serving an outdated
     * student version. The reference is only removed if no concurrent writer has replaced it in the meantime.
     *
     * @param attachment the attachment whose student version should be regenerated
     * @return whether the student version was regenerated
     */
    public boolean regenerateStudentVersionOrRemoveOutdated(Attachment attachment) {
        String startedFromStudentVersion = FileSystemLocation.storedFilename(attachment.getStudentVersion());
        try {
            regenerateStudentVersion(attachment);
            return true;
        }
        catch (RuntimeException exception) {
            log.error("Failed to regenerate the student version of attachment {}: {}", attachment.getId(), exception.getMessage(), exception);
            removeStudentVersionIfUnchanged(attachment, startedFromStudentVersion);
            return false;
        }
    }

    /**
     * Removes the student version of an attachment, for example because its file was replaced or no slide is hidden any more.
     * <p>
     * The reference is removed with a statement that only matches while the attachment still points at the same file, and the file is deleted only when it did.
     *
     * @param attachment the attachment whose student version should be removed
     */
    public void removeStudentVersionFile(Attachment attachment) {
        removeStudentVersionIfUnchanged(attachment, FileSystemLocation.storedFilename(attachment.getStudentVersion()));
    }

    private void removeStudentVersionIfUnchanged(Attachment attachment, String expectedStudentVersion) {
        AttachmentVideoUnit attachmentVideoUnit = attachment.getAttachmentVideoUnit();
        if (expectedStudentVersion == null || attachment.getId() == null || attachmentVideoUnit == null) {
            return;
        }
        try {
            if (attachmentRepository.clearStudentVersionIfUnchanged(attachment.getId(), expectedStudentVersion) == 0) {
                log.debug("Student version of attachment {} was replaced concurrently, so it is kept", attachment.getId());
                return;
            }
        }
        catch (RuntimeException exception) {
            log.error("Failed to remove the student version reference of attachment {}: {}", attachment.getId(), exception.getMessage(), exception);
            return;
        }
        if (Objects.equals(FileSystemLocation.storedFilename(attachment.getStudentVersion()), expectedStudentVersion)) {
            attachment.setStudentVersion(null);
        }
        deleteStudentVersionFile(attachmentVideoUnit.getId(), expectedStudentVersion);
    }

    /**
     * Schedules the deletion of a student version file that is no longer referenced. A failure is logged and not rethrown: the reference has already moved on, so the
     * worst outcome is an orphaned file.
     *
     * @param attachmentVideoUnitId  the id of the attachment video unit the student version is stored under
     * @param studentVersionFilename the stored filename of the student version to delete
     */
    private void deleteStudentVersionFile(long attachmentVideoUnitId, String studentVersionFilename) {
        try {
            Path studentVersionDirectory = studentVersionDirectory(attachmentVideoUnitId).toAbsolutePath().normalize();
            Path oldStudentVersionPath = new FileSystemLocation.StudentVersionSlides(attachmentVideoUnitId, studentVersionFilename).path().toAbsolutePath().normalize();
            // A stored value must never make this delete anything outside the student version directory of its unit.
            if (!oldStudentVersionPath.startsWith(studentVersionDirectory) || oldStudentVersionPath.equals(studentVersionDirectory)) {
                log.warn("Not deleting student version {} of attachment video unit {}: it is not inside the student version directory", studentVersionFilename,
                        attachmentVideoUnitId);
                return;
            }
            fileService.schedulePathForDeletion(oldStudentVersionPath, 0);
            fileService.evictCacheForPath(oldStudentVersionPath);
        }
        catch (RuntimeException exception) {
            log.warn("Failed to delete the unreferenced student version {} of attachment video unit {}: {}", studentVersionFilename, attachmentVideoUnitId, exception.getMessage(),
                    exception);
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
     * Stores a generated student version of an attachment and replaces the previous one.
     *
     * @param pdfData               The PDF data as byte array
     * @param attachment            The existing attachment
     * @param attachmentVideoUnitId The id of the attachment video unit
     * @throws IOException If there's an error handling the file
     */
    private void replaceStudentVersionFile(byte[] pdfData, Attachment attachment, long attachmentVideoUnitId) throws IOException {
        persistStudentVersionFile(pdfData, attachment, attachmentVideoUnitId, newStudentVersionFilename());
    }

    /**
     * Stores a student version uploaded by an instructor and replaces the previous one.
     *
     * @param pdfData               the uploaded PDF bytes
     * @param attachment            the attachment to update
     * @param attachmentVideoUnitId the id of the attachment video unit
     * @param originalFilename      the client-provided filename
     * @throws IOException if the file cannot be written
     */
    public void replaceUploadedStudentVersionFile(byte[] pdfData, Attachment attachment, long attachmentVideoUnitId, String originalFilename) throws IOException {
        String filename = stageUploadedStudentVersionFile(pdfData, attachmentVideoUnitId, originalFilename);
        publishStudentVersionFile(attachment, attachmentVideoUnitId, filename);
    }

    /**
     * Validates and writes a student version uploaded by an instructor, without referencing it yet. Callers that change more than the student version stage it before their
     * other changes, so an invalid or unwritable file fails the request before anything else changed, and publish it with {@link #publishStudentVersionFile} afterwards.
     *
     * @param pdfData               the uploaded PDF bytes
     * @param attachmentVideoUnitId the id of the attachment video unit
     * @param originalFilename      the client-provided filename
     * @return the stored filename of the staged student version
     * @throws IOException if the file cannot be written
     */
    public String stageUploadedStudentVersionFile(byte[] pdfData, long attachmentVideoUnitId, String originalFilename) throws IOException {
        // The uploaded name is only checked, never used: a student version is always a PDF stored under a new, unique name, so the previously referenced file is never
        // overwritten in place.
        if (!"pdf".equalsIgnoreCase(FilenameUtils.getExtension(FileUtil.checkAndSanitizeFilename(originalFilename)))) {
            throw new BadRequestAlertException("The student version must be a PDF file", "attachment", "studentVersionNotPdf");
        }
        String filename = newStudentVersionFilename();
        writeStudentVersionFile(pdfData, attachmentVideoUnitId, filename);
        return filename;
    }

    /**
     * Deletes a staged student version that will not be published, for example because the request that staged it failed.
     *
     * @param attachmentVideoUnitId  the id of the attachment video unit
     * @param studentVersionFilename the stored filename of the staged student version
     */
    public void discardStagedStudentVersionFile(long attachmentVideoUnitId, String studentVersionFilename) {
        deleteStudentVersionFile(attachmentVideoUnitId, studentVersionFilename);
    }

    /**
     * Writes the new student version and publishes it. See {@link #publishStudentVersionFile}.
     */
    private void persistStudentVersionFile(byte[] pdfData, Attachment attachment, long attachmentVideoUnitId, String filename) throws IOException {
        writeStudentVersionFile(pdfData, attachmentVideoUnitId, filename);
        publishStudentVersionFile(attachment, attachmentVideoUnitId, filename);
    }

    private static String newStudentVersionFilename() {
        return FileUtil.generateFilename(FileUtil.generateTargetFilenameBase(FilePathType.STUDENT_VERSION_SLIDES), STUDENT_VERSION_FILENAME_TEMPLATE, false);
    }

    private static Path studentVersionDirectory(long attachmentVideoUnitId) {
        return FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(String.valueOf(attachmentVideoUnitId)).resolve("student");
    }

    private void writeStudentVersionFile(byte[] pdfData, long attachmentVideoUnitId, String filename) throws IOException {
        Path basePath = studentVersionDirectory(attachmentVideoUnitId);
        Files.createDirectories(basePath);
        tempFileUtilService.replaceFileAtomically(FilePathConverter.getAttachmentVideoUnitFileSystemPath(), basePath.resolve(filename), pdfData);
    }

    /**
     * Stores the reference to a written student version, and only then deletes the previous file. If storing the reference fails, the new file is deleted and the previous
     * one stays referenced. Once the reference is stored, nothing here deletes or clears it again.
     *
     * @param attachment            the attachment the student version belongs to
     * @param attachmentVideoUnitId the id of the attachment video unit
     * @param filename              the stored filename of the written student version
     */
    public void publishStudentVersionFile(Attachment attachment, long attachmentVideoUnitId, String filename) {
        String oldStudentVersion = FileSystemLocation.storedFilename(attachment.getStudentVersion());
        try {
            attachmentRepository.updateStudentVersion(attachment.getId(), filename);
        }
        catch (RuntimeException exception) {
            deleteStudentVersionFile(attachmentVideoUnitId, filename);
            throw exception;
        }
        attachment.setStudentVersion(filename);

        if (oldStudentVersion != null && !oldStudentVersion.equals(filename)) {
            deleteStudentVersionFile(attachmentVideoUnitId, oldStudentVersion);
        }
    }
}
