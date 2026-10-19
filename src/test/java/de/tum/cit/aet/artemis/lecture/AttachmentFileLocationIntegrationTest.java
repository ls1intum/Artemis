package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentFileLocationDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

/**
 * What {@code MigrationEntry20260907_175735} is handed to work on.
 * <p>
 * The entry moves a file, so a row that reaches it and names no file this application stores is not merely useless, it is dangerous: the last segment of
 * {@code https://example.org/notes.pdf} looks exactly like a filename, and if another attachment of the same lecture genuinely has a file of that name, acting on the external
 * row moves that file into the wrong unit and strands the attachment it belongs to. The projection is therefore where the classification has to happen, not the entry.
 */
class AttachmentFileLocationIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    private Lecture lecture;

    @BeforeEach
    void initTestCase() {
        lecture = lectureUtilService.createCourseWithLecture(true);
    }

    @Test
    void shouldOfferAnAttachmentThatNamesAStoredFile() {
        Attachment attachment = attachmentWithLink("notes.pdf");

        AttachmentFileLocationDTO location = locationsOf(attachment).getFirst();

        assertThat(location.attachmentId()).isEqualTo(attachment.getId());
        assertThat(location.attachmentVideoUnitId()).isEqualTo(attachment.getAttachmentVideoUnit().getId());
        assertThat(location.lectureId()).isEqualTo(lecture.getId());
        assertThat(location.storedFilename()).isEqualTo("notes.pdf");
    }

    /**
     * The collision the exclusion exists for: both attachments hang off the same lecture and the external one ends in the filename of the other. Without the exclusion the entry
     * would be handed two candidates for one file.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("externalLinksEndingInAStoredFilename")
    void shouldNotOfferAnAttachmentHostedElsewhereEvenWhenItEndsInTheFilenameOfAnother(String externalLink) {
        Attachment stored = attachmentWithLink("notes.pdf");
        Attachment external = attachmentWithLink(externalLink);

        assertThat(locationsOf(stored, external)).extracting(AttachmentFileLocationDTO::attachmentId).containsExactly(stored.getId());
    }

    @Test
    void shouldNotOfferAnAttachmentWhoseLinkIsAbsoluteOrEmpty() {
        Attachment absolute = attachmentWithLink("/public/images/placeholder.png");
        Attachment blank = attachmentWithLink("  ");

        assertThat(locationsOf(absolute, blank)).isEmpty();
    }

    /**
     * The same classification, for the general-purpose location method rather than the projection.
     * <p>
     * {@link Attachment#fileLocation()} is what every filesystem caller asks, so the collision above reaches much further than the migration entry: resolving an external link
     * would hand back the location of whichever stored file shares its last segment, and a caller would then serve that file under this attachment's visibility or schedule it
     * for deletion with this unit.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("externalLinksEndingInAStoredFilename")
    void shouldNotLocateAFileForAnAttachmentHostedElsewhere(String externalLink) {
        Attachment stored = attachmentWithLink("notes.pdf");
        Attachment external = attachmentWithLink(externalLink);

        assertThat(external.fileLocation()).isEmpty();
        // The file the external attachment would otherwise have been given.
        assertThat(stored.fileLocation()).isPresent();
        assertThat(stored.fileLocation().orElseThrow().path().getFileName()).hasToString("notes.pdf");
    }

    @Test
    void shouldNotLocateAFileForAnAttachmentWhoseLinkIsAbsoluteOrBlank() {
        assertThat(attachmentWithLink("/public/images/placeholder.png").fileLocation()).isEmpty();
        assertThat(attachmentWithLink("  ").fileLocation()).isEmpty();
    }

    /**
     * External links whose last segment is the filename of a genuinely stored attachment of the same lecture.
     * <p>
     * The second and third carry no authority, which is the case a {@code ://} test misses: RFC 3986 makes the
     * authority optional, so a scheme is a scheme with one slash or none. The third also pins that the scheme is
     * recognised whatever its case.
     *
     * @return the links to classify
     */
    private static Stream<String> externalLinksEndingInAStoredFilename() {
        return Stream.of("https://example.org/lecture/notes.pdf", "file:/lecture/notes.pdf", "FILE:/lecture/notes.pdf");
    }

    private Attachment attachmentWithLink(String link) {
        AttachmentVideoUnit unit = lectureUtilService.createAttachmentVideoUnit(lecture, false);
        Attachment attachment = unit.getAttachment();
        attachment.setLink(link);
        attachment = attachmentRepository.save(attachment);
        attachment.setAttachmentVideoUnit(unit);
        return attachment;
    }

    /**
     * Reads the projection and keeps only the rows of the given attachments, because the query walks the whole table and the suite runs in parallel.
     *
     * @param attachments the attachments this test created
     * @return the locations the projection offers for them, in the order the projection returns them
     */
    private List<AttachmentFileLocationDTO> locationsOf(Attachment... attachments) {
        Set<Long> ids = Arrays.stream(attachments).map(Attachment::getId).collect(Collectors.toSet());
        return attachmentRepository.findAttachmentFileLocationsAfter(0, Pageable.unpaged()).stream().filter(location -> ids.contains(location.attachmentId())).toList();
    }
}
