package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.service.FilePathService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

@Service
@Profile(PROFILE_CORE)
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
     * Regenerates the student version of an attachment based on currently visible slides.
     * This should be called after slides are unhidden to ensure the student version is up-to-date.
     *
     * @param attachment The attachment whose student version needs to be regenerated
     */
    public void regenerateStudentVersion(Attachment attachment) {
        AttachmentVideoUnit attachmentUnit = attachment.getAttachmentVideoUnit();
        if (attachmentUnit == null) {
            return;
        }

        List<Slide> hiddenSlides = slideRepository.findByAttachmentVideoUnitIdAndHiddenNotNull(attachmentUnit.getId());

        // If no slides are marked as hidden, remove student version if it exists
        if (hiddenSlides.isEmpty()) {
            if (attachment.getStudentVersion() != null) {
                deleteStudentVersionFile(attachment);
                attachment.setStudentVersion(null);
                attachmentRepository.save(attachment);
            }
            return;
        }

        try {
            String originalPdfPath = attachment.getLink();
            Path pdfPath = FilePathService.actualPathForPublicPath(URI.create(originalPdfPath));

            byte[] studentVersionPdf = generateStudentVersionPdf(pdfPath.toFile(), hiddenSlides);

            handleStudentVersionFile(studentVersionPdf, attachment, attachmentUnit.getId());
            attachmentRepository.save(attachment);
        }
        catch (Exception e) {
            throw new InternalServerErrorException("Failed to regenerate student version: " + e.getMessage());
        }
    }

    /**
     * Deletes the student version file and cleans up associated resources.
     *
     * @param attachment The attachment whose student version should be deleted
     */
    private void deleteStudentVersionFile(Attachment attachment) {
        if (attachment.getStudentVersion() != null) {
            try {
                URI oldStudentVersionPath = URI.create(attachment.getStudentVersion());
                fileService.schedulePathForDeletion(FilePathService.actualPathForPublicPathOrThrow(oldStudentVersionPath), 0);
                fileService.evictCacheForPath(FilePathService.actualPathForPublicPathOrThrow(oldStudentVersionPath));
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
     * @param pdfData          The PDF data as byte array
     * @param attachment       The existing attachment
     * @param attachmentUnitId The id of the attachment unit
     * @throws IOException If there's an error handling the file
     */
    private void handleStudentVersionFile(byte[] pdfData, Attachment attachment, Long attachmentUnitId) throws IOException {
        // Delete the old student version if it exists
        if (attachment.getStudentVersion() != null) {
            deleteStudentVersionFile(attachment);
        }

        // Create the student version directory if it doesn't exist
        Path basePath = FilePathService.getAttachmentVideoUnitFilePath().resolve(attachmentUnitId.toString()).resolve("student");
        Files.createDirectories(basePath);

        String sanitizedName = fileService.checkAndSanitizeFilename(attachment.getName());
        String filename = sanitizedName + ".pdf";
        Path savePath = basePath.resolve(filename);

        FileUtils.writeByteArrayToFile(savePath.toFile(), pdfData);

        attachment.setStudentVersion(FilePathService.publicPathForActualPath(savePath, attachmentUnitId).toString());
    }
}
