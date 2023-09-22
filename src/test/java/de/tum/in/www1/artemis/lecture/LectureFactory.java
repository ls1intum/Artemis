package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.springframework.util.ResourceUtils;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;

/**
 * Factory for creating Lectures and related objects.
 */
public class LectureFactory {

    public static Lecture generateLecture(ZonedDateTime startDate, ZonedDateTime endDate, Course course) {
        Lecture lecture = new Lecture();
        lecture.setVisibleDate(startDate);
        lecture.setStartDate(startDate);
        lecture.setDescription("Description");
        lecture.setTitle("Lecture");
        lecture.setEndDate(endDate);
        lecture.setCourse(course);
        return lecture;
    }

    /**
     * Create a dummy attachment unit for testing. Includes a dummy attachment with optional placeholder image file on disk
     *
     * @param withFile Whether to include a placeholder image file on disk
     * @return AttachmentUnit that was created
     */
    public static AttachmentUnit generateAttachmentUnit(boolean withFile) {
        ZonedDateTime started = ZonedDateTime.now().minusDays(5);
        Attachment attachmentOfAttachmentUnit = withFile ? LectureFactory.generateAttachmentWithFile(started) : LectureFactory.generateAttachment(started);
        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setDescription("Lorem Ipsum");
        attachmentOfAttachmentUnit.setAttachmentUnit(attachmentUnit);
        attachmentUnit.setAttachment(attachmentOfAttachmentUnit);
        return attachmentUnit;
    }

    /**
     * Create a dummy attachment for testing
     *
     * @param date The optional upload and release date to set on the attachment
     * @return Attachment that was created
     */
    public static Attachment generateAttachment(ZonedDateTime date) {
        Attachment attachment = new Attachment();
        attachment.setAttachmentType(AttachmentType.FILE);
        if (date != null) {
            attachment.setReleaseDate(date);
            attachment.setUploadDate(date);
        }
        attachment.setName("TestAttachment");
        attachment.setVersion(1);
        return attachment;
    }

    /**
     * Create a dummy attachment for testing with a placeholder image file on disk
     *
     * @param startDate The release date to set on the attachment
     * @return Attachment that was created with its link set to a testing file on disk
     */
    public static Attachment generateAttachmentWithFile(ZonedDateTime startDate) {
        Attachment attachment = generateAttachment(startDate);
        String testFileName = "test_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
        try {
            FileUtils.copyFile(ResourceUtils.getFile("classpath:test-data/attachment/placeholder.jpg"), FilePathService.getTempFilePath().resolve(testFileName).toFile());
        }
        catch (IOException ex) {
            fail("Failed while copying test attachment files", ex);
        }
        // Path.toString() uses platform dependant path separators. Since we want to use this as a URL later, we need to replace \ with /.
        attachment.setLink(Path.of(FileService.DEFAULT_FILE_SUBPATH, testFileName).toString().replace('\\', '/'));
        return attachment;
    }
}
