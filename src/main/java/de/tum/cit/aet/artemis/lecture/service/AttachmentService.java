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
import org.jspecify.annotations.Nullable;
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
import de.tum.cit.aet.artemis.lecture.dto.AttachmentStudentVersionStateDTO;
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

    /** How often a regeneration starts over because the attachment changed while it ran, before it gives up and fails closed. */
    private static final int MAX_REGENERATION_ATTEMPTS = 3;

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
     * <p>
     * The reference is stored only while the file version, the previous reference and the hidden slides are still those the PDF was generated from (see
     * {@link AttachmentRepository#publishStudentVersionIfUnchanged}). If one of them changed, for example because an overlapping update hid another slide and published its own
     * student version, the new file is discarded and the student version is generated again from the new state, so an older regeneration does not publish over a newer one.
     * <p>
     * Slides record the file revision they were split from. While they belong to another revision than the stored file, for example during the split of a new file or after
     * that split failed, nothing is generated: the student version this attempt read is removed, so downloads fail closed until a split makes the slides match again.
     *
     * @param attachment The attachment whose student version needs to be regenerated
     */
    public void regenerateStudentVersion(Attachment attachment) {
        regenerate(attachment, false, false);
    }

    /**
     * Generates the student version of an attachment that hides slides but has none, for example because an earlier regeneration failed. Unlike
     * {@link #regenerateStudentVersion}, it never replaces a student version: one stored in the meantime, such as one an instructor submitted with a new file, is kept.
     *
     * @param attachment the attachment whose missing student version should be generated
     */
    public void generateMissingStudentVersion(Attachment attachment) {
        regenerate(attachment, true, false);
    }

    /**
     * @param onlyIfMissing           whether to generate only while the attachment has no student version
     * @param removeOutdatedOnFailure whether a failed attempt removes the student version it started from, so downloads fail closed; only while no newer one replaced it
     */
    private void regenerate(Attachment attachment, boolean onlyIfMissing, boolean removeOutdatedOnFailure) {
        AttachmentVideoUnit attachmentVideoUnit = attachment.getAttachmentVideoUnit();
        if (attachmentVideoUnit == null || attachment.getId() == null) {
            return;
        }

        for (int attempt = 1;; attempt++) {
            Optional<AttachmentStudentVersionStateDTO> state = attachmentRepository.findStudentVersionStateById(attachment.getId());
            if (state.isEmpty() || (onlyIfMissing && state.get().studentVersion() != null)) {
                return;
            }
            if (!Objects.equals(state.get().version(), attachment.getVersion()) || !Objects.equals(state.get().sha256Hash(), attachment.getSha256Hash())) {
                // The file was replaced after the caller loaded the attachment, so its file location is outdated. Whoever replaced the file takes care of the student version:
                // an editor change submits one, and a plain upload removes the old one.
                log.debug("Attachment {} has a newer file than the one to regenerate the student version from; leaving the student version to that change", attachment.getId());
                return;
            }
            String startedFromStudentVersion = state.get().studentVersion();
            // Everything after reading the state can fail, the slide queries included. A failure leaves the slides saved and the student version possibly outdated, so
            // remove the reference this attempt read before passing the failure on.
            try {
                // Before anything else, including removing the student version for a deck that hides nothing: slide numbers only mean something for the file they were split
                // from. While the split of a new file runs, or after it failed and the previous slides were restored, they belong to another file, and removing them by number
                // would remove the wrong pages. Close the downloads instead; the regeneration after the next successful split builds the student version.
                if (slideRepository.existsCurrentSlideOfOtherRevision(attachmentVideoUnit.getId(), state.get().version(), state.get().sha256Hash())) {
                    if (!onlyIfMissing) {
                        removeStudentVersionIfUnchanged(attachment, FileSystemLocation.storedFilename(startedFromStudentVersion));
                    }
                    log.debug("The slides of attachment {} do not belong to its stored file, so its student version is not regenerated until they do", attachment.getId());
                    return;
                }
                List<Slide> hiddenSlides = slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(attachmentVideoUnit.getId());

                // If no slides are marked as hidden, remove student version if it exists
                if (hiddenSlides.isEmpty()) {
                    removeStudentVersionIfUnchanged(attachment, FileSystemLocation.storedFilename(startedFromStudentVersion));
                    return;
                }

                // The attachment says where its file is; a unit created for an attachment that used to hang off a lecture still has it under that lecture's directory. An
                // attachment that links to a document hosted elsewhere has no file here to redact, and slides are only ever split out of a stored PDF, so hidden slides on such an
                // attachment are a data inconsistency rather than something to regenerate from.
                Optional<FileSystemLocation> fileLocation = attachment.fileLocation();
                if (fileLocation.isEmpty()) {
                    log.warn("Attachment {} links to a document this application does not store, so no student version can be regenerated for its {} hidden slide(s).",
                            attachment.getId(), hiddenSlides.size());
                    return;
                }

                String filename = newStudentVersionFilename();
                try {
                    writeStudentVersionFile(generateStudentVersionPdf(fileLocation.get().path().toFile(), hiddenSlides), attachmentVideoUnit.getId(), filename);
                }
                catch (Exception e) {
                    throw new InternalServerErrorException("Failed to regenerate student version: " + e.getMessage(), e);
                }

                List<Long> hiddenSlideIds = hiddenSlides.stream().map(Slide::getId).toList();
                int published;
                try {
                    published = attachmentRepository.publishStudentVersionIfUnchanged(attachment.getId(), attachmentVideoUnit.getId(), filename, state.get().version(),
                            state.get().sha256Hash(), startedFromStudentVersion, hiddenSlideIds, hiddenSlideIds.size());
                }
                catch (RuntimeException exception) {
                    deleteStudentVersionFile(attachmentVideoUnit.getId(), filename);
                    throw exception;
                }
                if (published == 1) {
                    attachment.setStudentVersion(filename);
                    String oldStudentVersion = FileSystemLocation.storedFilename(startedFromStudentVersion);
                    if (oldStudentVersion != null && !oldStudentVersion.equals(filename)) {
                        deleteStudentVersionFile(attachmentVideoUnit.getId(), oldStudentVersion);
                    }
                    return;
                }

                deleteStudentVersionFile(attachmentVideoUnit.getId(), filename);
                if (attempt >= MAX_REGENERATION_ATTEMPTS) {
                    throw new InternalServerErrorException("Failed to regenerate student version: attachment " + attachment.getId() + " kept changing while it was generated");
                }
                log.debug("Attachment {} changed while its student version was generated; generating it again", attachment.getId());
            }
            catch (RuntimeException exception) {
                removeOutdatedAfterFailure(attachment, startedFromStudentVersion, removeOutdatedOnFailure);
                throw exception;
            }
        }
    }

    /**
     * After a failed attempt, removes the student version reference that attempt read from the database, since it no longer matches the slides. A reference published after
     * that read is newer and stays.
     */
    private void removeOutdatedAfterFailure(Attachment attachment, String startedFromStudentVersion, boolean removeOutdatedOnFailure) {
        if (removeOutdatedOnFailure) {
            removeStudentVersionIfUnchanged(attachment, FileSystemLocation.storedFilename(startedFromStudentVersion));
        }
    }

    /**
     * Regenerates the student version without letting a failure abort the caller.
     * <p>
     * Callers use this after slide visibility has already been saved, so a failure must not undo or interrupt the rest of their work. If regeneration fails, the student version
     * the failed attempt read from the database no longer matches the slides, so its reference is removed. With hidden slides left, downloads then fail closed instead of serving
     * an outdated student version. The reference is only removed if no concurrent writer has replaced it in the meantime.
     *
     * @param attachment the attachment whose student version should be regenerated
     * @return whether the student version was regenerated
     */
    public boolean regenerateStudentVersionOrRemoveOutdated(Attachment attachment) {
        try {
            regenerate(attachment, false, true);
            return true;
        }
        catch (RuntimeException exception) {
            log.error("Failed to regenerate the student version of attachment {}: {}", attachment.getId(), exception.getMessage(), exception);
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
     * Stores a student version uploaded by an instructor and replaces the previous one.
     *
     * @param pdfData               the uploaded PDF bytes
     * @param attachment            the attachment to update
     * @param attachmentVideoUnitId the id of the attachment video unit
     * @param originalFilename      the client-provided filename
     * @throws IOException if the file cannot be written
     */
    public void replaceUploadedStudentVersionFile(byte[] pdfData, Attachment attachment, long attachmentVideoUnitId, String originalFilename) throws IOException {
        // Read before staging, so a student version regenerated meanwhile is not replaced by this upload
        String studentVersionBeforeStaging = findStoredStudentVersion(attachment.getId());
        String filename = stageUploadedStudentVersionFile(pdfData, attachmentVideoUnitId, originalFilename);
        publishSubmittedStudentVersionFile(attachment, attachmentVideoUnitId, filename, studentVersionBeforeStaging);
    }

    /**
     * Validates and writes a student version uploaded by an instructor, without referencing it yet. Callers that change more than the student version stage it before their
     * other changes, so an invalid or unwritable file fails the request before anything else changed, and publish it with {@link #publishSubmittedStudentVersionFile} afterwards.
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
     * Stores the reference to a submitted student version, and only then deletes the previous file. It is stored only while the attachment still has the file version and
     * hash it was submitted for and the student version reference is still the one the request read before staging; a student version published in the meantime, such as
     * one regenerated after the split, is newer and stays, and the submitted file is deleted. If storing the reference fails, the new file is deleted and the previous one
     * stays referenced. Once the reference is stored, nothing here deletes or clears it again.
     *
     * @param attachment             the attachment the student version belongs to, with the file version and hash the request stored
     * @param attachmentVideoUnitId  the id of the attachment video unit
     * @param filename               the stored filename of the written student version
     * @param expectedStudentVersion the stored student version value the request read at its start, or null if there was none
     */
    public void publishSubmittedStudentVersionFile(Attachment attachment, long attachmentVideoUnitId, String filename, @Nullable String expectedStudentVersion) {
        int updated;
        try {
            updated = attachmentRepository.updateStudentVersionIfUnchangedSince(attachment.getId(), filename, attachment.getVersion(), attachment.getSha256Hash(),
                    expectedStudentVersion);
        }
        catch (RuntimeException exception) {
            deleteStudentVersionFile(attachmentVideoUnitId, filename);
            throw exception;
        }
        if (updated == 0) {
            log.info("Attachment {} changed before its student version was stored, so that student version is discarded and the newer state stays", attachment.getId());
            deleteStudentVersionFile(attachmentVideoUnitId, filename);
            return;
        }
        attachment.setStudentVersion(filename);

        // The file the statement actually replaced: the reference it matched, not what the caller's entity may still hold
        String replacedStudentVersion = FileSystemLocation.storedFilename(expectedStudentVersion);
        if (replacedStudentVersion != null && !replacedStudentVersion.equals(filename)) {
            deleteStudentVersionFile(attachmentVideoUnitId, replacedStudentVersion);
        }
    }

    /**
     * Removes the student version reference of an attachment, but only while it is still the given stored value, and deletes its file. Used after a failed update that may
     * have replaced the file the student version belongs to.
     *
     * @param attachment          the attachment
     * @param expectedStoredValue the stored student version value read before the update, or null if there was none
     */
    public void removeStudentVersionIfStill(Attachment attachment, @Nullable String expectedStoredValue) {
        if (attachment != null) {
            removeStudentVersionIfUnchanged(attachment, FileSystemLocation.storedFilename(expectedStoredValue));
        }
    }

    /**
     * Reads the stored student version value of an attachment, as the conditional publications compare it.
     *
     * @param attachmentId the id of the attachment
     * @return the stored value, or null if the attachment has no student version or does not exist
     */
    @Nullable
    public String findStoredStudentVersion(long attachmentId) {
        return attachmentRepository.findStudentVersionStateById(attachmentId).map(AttachmentStudentVersionStateDTO::studentVersion).orElse(null);
    }

}
