package de.tum.cit.aet.artemis.lecture.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.FileService;
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

    private final AttachmentRepository attachmentRepository;

    private final SlideRepository slideRepository;

    private final FileService fileService;

    public AttachmentService(AttachmentRepository attachmentRepository, SlideRepository slideRepository, FileService fileService) {
        this.attachmentRepository = attachmentRepository;
        this.slideRepository = slideRepository;
        this.fileService = fileService;
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
                oldFilePath = new FileSystemLocation.LectureAttachment(existingAttachment.getLecture().getId(), existingAttachment.getLink()).path();
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
     *
     * @param attachment The attachment whose student version needs to be regenerated
     */
    public void regenerateStudentVersion(Attachment attachment) {
        AttachmentVideoUnit attachmentVideoUnit = attachment.getAttachmentVideoUnit();
        if (attachmentVideoUnit == null) {
            return;
        }

        List<Slide> hiddenSlides = slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNull(attachmentVideoUnit.getId());

        // If no slides are marked as hidden, remove student version if it exists
        if (hiddenSlides.isEmpty()) {
            if (attachment.getStudentVersion() != null) {
                deleteStudentVersionFile(attachment, attachmentVideoUnit.getId());
                attachment.setStudentVersion(null);
                attachmentRepository.save(attachment);
            }
            return;
        }

        try {
            Path pdfPath = new FileSystemLocation.AttachmentVideoUnitFile(attachmentVideoUnit.getId(), attachment.getLink()).path();

            byte[] studentVersionPdf = generateStudentVersionPdf(pdfPath.toFile(), hiddenSlides);

            handleStudentVersionFile(studentVersionPdf, attachment, attachmentVideoUnit.getId());
            attachmentRepository.save(attachment);
        }
        catch (Exception e) {
            throw new InternalServerErrorException("Failed to regenerate student version: " + e.getMessage());
        }
    }

    /**
     * Deletes the student version file and cleans up associated resources.
     *
     * @param attachment            The attachment whose student version should be deleted
     * @param attachmentVideoUnitId The id of the attachment video unit the student version is stored under
     */
    private void deleteStudentVersionFile(Attachment attachment, long attachmentVideoUnitId) {
        if (attachment.getStudentVersion() != null) {
            try {
                Path oldStudentVersionPath = new FileSystemLocation.StudentVersionSlides(attachmentVideoUnitId, attachment.getStudentVersion()).path();
                fileService.schedulePathForDeletion(oldStudentVersionPath, 0);
                fileService.evictCacheForPath(oldStudentVersionPath);
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
    private void handleStudentVersionFile(byte[] pdfData, Attachment attachment, Long attachmentVideoUnitId) throws IOException {
        // Delete the old student version if it exists
        if (attachment.getStudentVersion() != null) {
            deleteStudentVersionFile(attachment, attachmentVideoUnitId);
        }

        // Create the student version directory if it doesn't exist
        Path basePath = FilePathConverter.getAttachmentVideoUnitFileSystemPath().resolve(attachmentVideoUnitId.toString()).resolve("student");
        Files.createDirectories(basePath);

        String sanitizedName = FileUtil.checkAndSanitizeFilename(attachment.getName());
        String filename = FileUtil.generateFilename(FileUtil.generateTargetFilenameBase(FilePathType.STUDENT_VERSION_SLIDES), sanitizedName + ".pdf", false);
        Path savePath = basePath.resolve(filename);

        FileUtils.writeByteArrayToFile(savePath.toFile(), pdfData);

        attachment.setStudentVersion(FilePathConverter.externalUriForFileSystemPath(savePath, FilePathType.STUDENT_VERSION_SLIDES, attachmentVideoUnitId).toString());
    }
}
