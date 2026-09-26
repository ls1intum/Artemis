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

        assertThat(stamped).isEqualTo("See [cite:L:42:7:::Deadlocks:A summary.:va3] for details.");
    }

    @Test
    void stampCitationVersions_pinsTranscriptionVersionForVideoCitation() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(LECTURE_UNIT_ID, 3, 2)));

        var stamped = citationService.stampCitationVersions("[cite:L:42:7:120:180:Deadlocks:A summary.]");

        // A transcript segment carries a companion page number, but only the video revision is pinned: the timestamp is what the citation points at.
        assertThat(stamped).isEqualTo("[cite:L:42:7:120:180:Deadlocks:A summary.:vt2]");
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

        var alreadyStamped = "[cite:L:42:7:::Deadlocks:A summary.:va3]";

        assertThat(citationService.stampCitationVersions(alreadyStamped)).isEqualTo(alreadyStamped);
    }

    @Test
    void stampCitationVersions_preservesKeywordsAndSummariesWithSpecialCharacters() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(LECTURE_UNIT_ID, 3, null)));

        var stamped = citationService.stampCitationVersions("[cite:L:42:7:::Costs:A price of $5 and a backslash \\ stay intact.]");

        assertThat(stamped).isEqualTo("[cite:L:42:7:::Costs:A price of $5 and a backslash \\ stay intact.:va3]");
    }

    /**
     * The version fields are read from the right, so an ordinary summary containing a colon still has to be stamped correctly.
     */
    @Test
    void stampCitationVersions_stampsSummaryContainingColons() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(LECTURE_UNIT_ID, 3, null)));

        var stamped = citationService.stampCitationVersions("[cite:L:42:7:::Ratio:The split is 3:1]");

        assertThat(stamped).isEqualTo("[cite:L:42:7:::Ratio:The split is 3:1:va3]");
    }

    /**
     * The tag is what keeps a summary from being mistaken for a version. Without it, a summary ending in colon-separated numbers read as an already stamped citation: it
     * was left unpinned here, while the client read those very numbers as the pinned versions and would have called the citation current whenever they happened to match
     * the ones the unit currently has.
     */
    @Test
    void stampCitationVersions_stampsSummaryThatEndsInColonSeparatedNumbers() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(LECTURE_UNIT_ID, 3, null)));

        var stamped = citationService.stampCitationVersions("[cite:L:42:7:::Key:Ratios:3:1]");

        assertThat(stamped).isEqualTo("[cite:L:42:7:::Key:Ratios:3:1:va3]");
    }

    /**
     * Stamping the same text twice must not append a second version field, not even when the summary itself ends in numbers.
     */
    @Test
    void stampCitationVersions_isIdempotentForASummaryThatEndsInColonSeparatedNumbers() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(LECTURE_UNIT_ID, 3, null)));

        var alreadyStamped = "[cite:L:42:7:::Key:Ratios:3:1:va3]";

        assertThat(citationService.stampCitationVersions(alreadyStamped)).isEqualTo(alreadyStamped);
    }

    /**
     * A citation carrying only an end time is rendered as a slide citation by the client, because it builds the link from the start time. Pinning has to follow that same
     * choice, otherwise the pinned version describes material the click never navigates to.
     */
    @Test
    void stampCitationVersions_pinsAttachmentVersionWhenOnlyAnEndTimeIsPresent() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(LECTURE_UNIT_ID, 3, 2)));

        var stamped = citationService.stampCitationVersions("[cite:L:42:7::180:Deadlocks:A summary.]");

        assertThat(stamped).isEqualTo("[cite:L:42:7::180:Deadlocks:A summary.:va3]");
    }

    @Test
    void stampCitationVersions_stampsEveryCitationInTheText() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(LECTURE_UNIT_ID, 3, 2), ingested(SECOND_LECTURE_UNIT_ID, 8, null)));

        var stamped = citationService.stampCitationVersions("A [cite:L:42::30:60:Locks:First.] and B [cite:L:7:2:::Threads:Second.]");

        assertThat(stamped).isEqualTo("A [cite:L:42::30:60:Locks:First.:vt2] and B [cite:L:7:2:::Threads:Second.:va8]");
    }

    /**
     * The citation pattern accepts any run of digits, so a model inventing an ID beyond {@code long} produces a well-formed citation that cannot name a lecture unit. It
     * has to be skipped rather than throw: the lookup already ignores it, and a valid citation next to it is enough to reach the stamping loop, where an escaping
     * {@link NumberFormatException} would abort stamping for the whole answer — and with it the persistence of the assistant message.
     */
    @Test
    void stampCitationVersions_skipsAnOversizedEntityIdAndStampsTheValidCitationBesideIt() {
        when(lectureUnitRepositoryApi.findIngestedVersionsByIds(anyCollection())).thenReturn(List.of(ingested(LECTURE_UNIT_ID, 3, null)));

        var stamped = citationService.stampCitationVersions("Bad [cite:L:99999999999999999999:1:::Key:Summary.] and good [cite:L:42:7:::Deadlocks:A summary.]");

        assertThat(stamped).isEqualTo("Bad [cite:L:99999999999999999999:1:::Key:Summary.] and good [cite:L:42:7:::Deadlocks:A summary.:va3]");
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
