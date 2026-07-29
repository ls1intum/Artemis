package de.tum.cit.aet.artemis.iris.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.dto.IrisCitationMetaDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitIngestedVersionsDTO;

@ExtendWith(MockitoExtension.class)
class IrisCitationServiceTest {

    private static final long LECTURE_UNIT_ID = 42L;

    private static final long SECOND_LECTURE_UNIT_ID = 7L;

    private static final long LECTURE_ID = 1L;

    private static final long SECOND_LECTURE_ID = 2L;

    private static final long COURSE_ID = 1L;

    @Mock
    private LectureUnitRepositoryApi lectureUnitRepositoryApi;

    @Mock
    private IrisSessionRepository irisSessionRepository;

    private IrisCitationService citationService;

    @BeforeEach
    void setUp() {
        citationService = new IrisCitationService(Optional.of(lectureUnitRepositoryApi), irisSessionRepository);
    }

    @Test
    void resolveCitationInfo_returnsEmptyForBlankText() {
        assertThat(citationService.resolveCitationInfo(null)).isEmpty();
        assertThat(citationService.resolveCitationInfo("   ")).isEmpty();
    }

    @Test
    void resolveCitationInfo_returnsEmptyWhenNoReferencesFound() {
        assertThat(citationService.resolveCitationInfo("No citations here.")).isEmpty();
        verifyNoInteractions(lectureUnitRepositoryApi);
    }

    @Test
    void resolveCitationInfo_skipsInvalidReferences() {
        var text = "[cite:L] [cite:X:12] [cite:L:abc]";

        assertThat(citationService.resolveCitationInfo(text)).isEmpty();
        verifyNoInteractions(lectureUnitRepositoryApi);
    }

    @Test
    void resolveCitationInfo_resolvesLectureUnitsInOrderAndDeduplicates() {
        var firstUnit = lectureUnit(LECTURE_UNIT_ID, LECTURE_ID, COURSE_ID, "Intro Lecture", "Basics");
        var secondUnit = lectureUnit(SECOND_LECTURE_UNIT_ID, SECOND_LECTURE_ID, COURSE_ID, "Advanced Lecture", "Deep Dive");

        when(lectureUnitRepositoryApi.findAllByIdsWithLecture(anyCollection())).thenReturn(List.of(firstUnit, secondUnit));

        var text = "First [cite:L:42:::::] again [cite:L:42:::::] then [cite:L:7:::::].";

        var resolved = citationService.resolveCitationInfo(text);

        assertThat(resolved).extracting(IrisCitationMetaDTO::entityId, IrisCitationMetaDTO::lectureTitle, IrisCitationMetaDTO::lectureUnitTitle, IrisCitationMetaDTO::lectureId,
                IrisCitationMetaDTO::courseId).containsExactly(tuple(LECTURE_UNIT_ID, "Intro Lecture", "Basics", LECTURE_ID, COURSE_ID),
                        tuple(SECOND_LECTURE_UNIT_ID, "Advanced Lecture", "Deep Dive", SECOND_LECTURE_ID, COURSE_ID));
        verify(lectureUnitRepositoryApi).findAllByIdsWithLecture(anyCollection());
    }

    @Test
    void resolveCitationInfo_skipsWhenLectureTitleMissing() {
        var unit = lectureUnit(LECTURE_UNIT_ID, LECTURE_ID, COURSE_ID, "  ", "Unit Title");
        when(lectureUnitRepositoryApi.findAllByIdsWithLecture(anyCollection())).thenReturn(List.of(unit));

        assertThat(citationService.resolveCitationInfo("[cite:L:42:::::]")).isEmpty();
        verify(lectureUnitRepositoryApi).findAllByIdsWithLecture(anyCollection());
    }

    @Test
    void resolveCitationInfo_skipsWhenLectureUnitTitleMissing() {
        var unit = lectureUnit(LECTURE_UNIT_ID, LECTURE_ID, COURSE_ID, "Lecture Title", "   ");
        when(lectureUnitRepositoryApi.findAllByIdsWithLecture(anyCollection())).thenReturn(List.of(unit));

        assertThat(citationService.resolveCitationInfo("[cite:L:42:::::]")).isEmpty();
        verify(lectureUnitRepositoryApi).findAllByIdsWithLecture(anyCollection());
    }

    @Test
    void resolveCitationInfo_returnsEmptyWhenLectureUnitNotFound() {
        when(lectureUnitRepositoryApi.findAllByIdsWithLecture(anyCollection())).thenReturn(List.of());

        assertThat(citationService.resolveCitationInfo("[cite:L:42:::::]")).isEmpty();
        verify(lectureUnitRepositoryApi).findAllByIdsWithLecture(anyCollection());
    }

    @Test
    void resolveCitationInfoFromMessages_aggregatesMessageContents() {
        var unit = lectureUnit(LECTURE_UNIT_ID, LECTURE_ID, COURSE_ID, "Lecture Title", "Unit Title");
        when(lectureUnitRepositoryApi.findAllByIdsWithLecture(anyCollection())).thenReturn(List.of(unit));

        var messageWithCitation = new IrisMessage();
        messageWithCitation.addContent(new IrisTextMessageContent("Answer [cite:L:42:::::]"));

        var blankMessage = new IrisMessage();
        blankMessage.addContent(new IrisTextMessageContent("  "));

        var nullContentMessage = new IrisMessage();
        nullContentMessage.setContent(null);

        var resolved = citationService.resolveCitationInfoFromMessages(Arrays.asList(messageWithCitation, null, blankMessage, nullContentMessage));

        assertThat(resolved).containsExactly(new IrisCitationMetaDTO(LECTURE_UNIT_ID, "Lecture Title", "Unit Title", LECTURE_ID, COURSE_ID));
    }

    @Test
    void resolveCitationInfo_returnsEmptyWhenRepositoryUnavailable() {
        var serviceWithoutRepository = new IrisCitationService(Optional.empty(), irisSessionRepository);

        assertThat(serviceWithoutRepository.resolveCitationInfo("[cite:L:1:::::]")).isEmpty();
    }

    @Test
    void stampCitationVersions_pinsAttachmentVersionForSlideCitation() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(LECTURE_UNIT_ID, 3, null)));

        var stamped = citationService.stampCitationVersions("See [cite:L:42:7:::Deadlocks:A summary.] for details.");

        assertThat(stamped).isEqualTo("See [cite:L:42:7:::Deadlocks:A summary.:3:] for details.");
    }

    @Test
    void stampCitationVersions_pinsTranscriptionVersionForVideoCitation() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(LECTURE_UNIT_ID, 3, 2)));

        var stamped = citationService.stampCitationVersions("[cite:L:42:7:120:180:Deadlocks:A summary.]");

        // A transcript segment carries a companion page number, but only the video revision is pinned: the timestamp is what the citation points at.
        assertThat(stamped).isEqualTo("[cite:L:42:7:120:180:Deadlocks:A summary.::2]");
    }

    /**
     * Transcriptions that were already ingested before transcription versions existed carry no version. Citing them must stay unpinned rather than pin an empty value, so
     * that clicking such a citation keeps behaving exactly as it did before the feature.
     */
    @Test
    void stampCitationVersions_leavesVideoCitationUntouchedWhenTheTranscriptionHasNoVersionYet() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(LECTURE_UNIT_ID, 3, null)));

        var text = "[cite:L:42:7:120:180:Deadlocks:A summary.]";

        // The slides do have a version, but a video citation must not be pinned to it
        assertThat(citationService.stampCitationVersions(text)).isEqualTo(text);
    }

    @Test
    void stampCitationVersions_leavesCitationUntouchedWhenLectureUnitIsUnknown() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(SECOND_LECTURE_UNIT_ID, 3, null)));

        var text = "[cite:L:42:7:::Deadlocks:A summary.]";

        assertThat(citationService.stampCitationVersions(text)).isEqualTo(text);
    }

    @Test
    void stampCitationVersions_isIdempotent() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(LECTURE_UNIT_ID, 3, null)));

        var alreadyStamped = "[cite:L:42:7:::Deadlocks:A summary.:3:]";

        assertThat(citationService.stampCitationVersions(alreadyStamped)).isEqualTo(alreadyStamped);
    }

    @Test
    void stampCitationVersions_preservesKeywordsAndSummariesWithSpecialCharacters() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(LECTURE_UNIT_ID, 3, null)));

        var stamped = citationService.stampCitationVersions("[cite:L:42:7:::Costs:A price of $5 and a backslash \\ stay intact.]");

        assertThat(stamped).isEqualTo("[cite:L:42:7:::Costs:A price of $5 and a backslash \\ stay intact.:3:]");
    }

    @Test
    void stampCitationVersions_stampsEveryCitationInTheText() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(LECTURE_UNIT_ID, 3, 2), ingested(SECOND_LECTURE_UNIT_ID, 8, null)));

        var stamped = citationService.stampCitationVersions("A [cite:L:42::30:60:Locks:First.] and B [cite:L:7:2:::Threads:Second.]");

        assertThat(stamped).isEqualTo("A [cite:L:42::30:60:Locks:First.::2] and B [cite:L:7:2:::Threads:Second.:8:]");
    }

    @Test
    void stampCitationVersions_returnsTextUnchangedWhenNothingToStamp() {
        assertThat(citationService.stampCitationVersions("No citations here.")).isEqualTo("No citations here.");
        assertThat(citationService.stampCitationVersions(null)).isNull();
        verifyNoInteractions(lectureUnitRepositoryApi);
    }

    @Test
    void stampCitationVersions_returnsTextUnchangedWhenRepositoryUnavailable() {
        var serviceWithoutRepository = new IrisCitationService(Optional.empty(), irisSessionRepository);
        var text = "[cite:L:42:7:::Deadlocks:A summary.]";

        assertThat(serviceWithoutRepository.stampCitationVersions(text)).isEqualTo(text);
    }

    private static LectureUnit lectureUnit(long id, long lectureId, long courseId, String lectureTitle, String unitTitle) {
        var course = new Course();
        course.setId(courseId);

        var lecture = new Lecture();
        lecture.setId(lectureId);
        lecture.setTitle(lectureTitle);
        lecture.setCourse(course);

        var unit = new TextUnit();
        unit.setId(id);
        unit.setLecture(lecture);
        unit.setName(unitTitle);
        return unit;
    }

    private static LectureUnitIngestedVersionsDTO ingested(long lectureUnitId, Integer attachmentVersion, Integer videoVersion) {
        return new LectureUnitIngestedVersionsDTO(lectureUnitId, attachmentVersion, videoVersion);
    }
}
