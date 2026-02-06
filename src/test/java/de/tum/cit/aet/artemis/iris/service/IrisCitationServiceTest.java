package de.tum.cit.aet.artemis.iris.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.dto.IrisCitationMetaDTO;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

@ExtendWith(MockitoExtension.class)
class IrisCitationServiceTest {

    private static final long LECTURE_UNIT_ID = 42L;

    private static final long SECOND_LECTURE_UNIT_ID = 7L;

    @Mock
    private LectureUnitRepositoryApi lectureUnitRepositoryApi;

    private IrisCitationService citationService;

    @BeforeEach
    void setUp() {
        citationService = new IrisCitationService(Optional.of(lectureUnitRepositoryApi));
    }

    @Test
    void resolveCitationInfo_returnsNullForBlankText() {
        assertThat(citationService.resolveCitationInfo(null)).isNull();
        assertThat(citationService.resolveCitationInfo("   ")).isNull();
    }

    @Test
    void resolveCitationInfo_returnsNullWhenNoReferencesFound() {
        assertThat(citationService.resolveCitationInfo("No citations here.")).isNull();
        verifyNoInteractions(lectureUnitRepositoryApi);
    }

    @Test
    void resolveCitationInfo_skipsInvalidReferences() {
        var text = "[cite:L] [cite:X:12] [cite:L:abc]";

        assertThat(citationService.resolveCitationInfo(text)).isNull();
        verifyNoInteractions(lectureUnitRepositoryApi);
    }

    @Test
    void resolveCitationInfo_resolvesLectureUnitsInOrderAndDeduplicates() {
        var firstUnit = lectureUnit(LECTURE_UNIT_ID, "Intro Lecture", "Basics");
        var secondUnit = lectureUnit(SECOND_LECTURE_UNIT_ID, "Advanced Lecture", "Deep Dive");

        when(lectureUnitRepositoryApi.findByIdElseThrow(LECTURE_UNIT_ID)).thenReturn(firstUnit);
        when(lectureUnitRepositoryApi.findByIdElseThrow(SECOND_LECTURE_UNIT_ID)).thenReturn(secondUnit);

        var text = "First [cite:L:42:::::] again [cite:L:42:::::] then [cite:L:7:::::].";

        var resolved = citationService.resolveCitationInfo(text);

        assertThat(resolved).extracting(IrisCitationMetaDTO::entityId, IrisCitationMetaDTO::lectureTitle, IrisCitationMetaDTO::lectureUnitTitle)
                .containsExactly(tuple(LECTURE_UNIT_ID, "Intro Lecture", "Basics"), tuple(SECOND_LECTURE_UNIT_ID, "Advanced Lecture", "Deep Dive"));
        verify(lectureUnitRepositoryApi, times(1)).findByIdElseThrow(LECTURE_UNIT_ID);
        verify(lectureUnitRepositoryApi, times(1)).findByIdElseThrow(SECOND_LECTURE_UNIT_ID);
    }

    @Test
    void resolveCitationInfo_skipsWhenLectureTitleMissing() {
        var unit = lectureUnit(LECTURE_UNIT_ID, "  ", "Unit Title");
        when(lectureUnitRepositoryApi.findByIdElseThrow(LECTURE_UNIT_ID)).thenReturn(unit);

        assertThat(citationService.resolveCitationInfo("[cite:L:42:::::]")).isNull();
        verify(lectureUnitRepositoryApi).findByIdElseThrow(LECTURE_UNIT_ID);
    }

    @Test
    void resolveCitationInfo_skipsWhenLectureUnitTitleMissing() {
        var unit = lectureUnit(LECTURE_UNIT_ID, "Lecture Title", "   ");
        when(lectureUnitRepositoryApi.findByIdElseThrow(LECTURE_UNIT_ID)).thenReturn(unit);

        assertThat(citationService.resolveCitationInfo("[cite:L:42:::::]")).isNull();
        verify(lectureUnitRepositoryApi).findByIdElseThrow(LECTURE_UNIT_ID);
    }

    @Test
    void resolveCitationInfo_returnsNullWhenLectureUnitNotFound() {
        when(lectureUnitRepositoryApi.findByIdElseThrow(LECTURE_UNIT_ID)).thenThrow(new EntityNotFoundException("LectureUnit", LECTURE_UNIT_ID));

        assertThat(citationService.resolveCitationInfo("[cite:L:42:::::]")).isNull();
        verify(lectureUnitRepositoryApi).findByIdElseThrow(LECTURE_UNIT_ID);
    }

    @Test
    void resolveCitationInfoFromMessages_aggregatesMessageContents() {
        var unit = lectureUnit(LECTURE_UNIT_ID, "Lecture Title", "Unit Title");
        when(lectureUnitRepositoryApi.findByIdElseThrow(LECTURE_UNIT_ID)).thenReturn(unit);

        var messageWithCitation = new IrisMessage();
        messageWithCitation.addContent(new IrisTextMessageContent("Answer [cite:L:42:::::]"));

        var blankMessage = new IrisMessage();
        blankMessage.addContent(new IrisTextMessageContent("  "));

        var nullContentMessage = new IrisMessage();
        nullContentMessage.setContent(null);

        var resolved = citationService.resolveCitationInfoFromMessages(Arrays.asList(messageWithCitation, null, blankMessage, nullContentMessage));

        assertThat(resolved).containsExactly(new IrisCitationMetaDTO(LECTURE_UNIT_ID, "Lecture Title", "Unit Title"));
    }

    @Test
    void resolveCitationInfo_returnsNullWhenRepositoryUnavailable() {
        var serviceWithoutRepository = new IrisCitationService(Optional.empty());

        assertThat(serviceWithoutRepository.resolveCitationInfo("[cite:L:1:::::]")).isNull();
    }

    private static LectureUnit lectureUnit(long id, String lectureTitle, String unitTitle) {
        var lecture = new Lecture();
        lecture.setTitle(lectureTitle);

        var unit = new TextUnit();
        unit.setId(id);
        unit.setLecture(lecture);
        unit.setName(unitTitle);
        return unit;
    }
}
