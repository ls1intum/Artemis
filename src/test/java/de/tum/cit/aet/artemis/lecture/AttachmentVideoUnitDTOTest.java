package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.dto.AttachmentVideoUnitDTO;
import de.tum.cit.aet.artemis.lecture.dto.SlideDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class AttachmentVideoUnitDTOTest {

    @Test
    void omitsPreviousRevisionWhileReplacementPdfIsBeingSplit() {
        var unit = unitWithAttachment(2, "current");
        var previousSlide = slide(unit, 1, "previous");
        previousSlide.setHidden(ZonedDateTime.now().plusDays(1));
        unit.setSlides(List.of(previousSlide));

        var dto = AttachmentVideoUnitDTO.of(unit);

        assertThat(dto.attachment().version()).isEqualTo(2);
        assertThat(dto.slideMetadataPending()).isTrue();
        assertThat(dto.slides()).isEmpty();
    }

    @Test
    void omitsSlidesWithDifferentFileHashEvenWhenVersionMatches() {
        var unit = unitWithAttachment(2, "current");
        unit.setSlides(List.of(slide(unit, 2, "previous")));

        assertThat(AttachmentVideoUnitDTO.of(unit).slides()).isEmpty();
    }

    @Test
    void preservesCurrentSlideIdentityVisibilityAndExerciseLink() {
        var unit = unitWithAttachment(2, "current");
        var lecture = new Lecture();
        lecture.setId(71L);
        unit.setLecture(lecture);
        var current = slide(unit, 2, "current");
        var hiddenUntil = ZonedDateTime.now().plusDays(1);
        current.setHidden(hiddenUntil);
        var exercise = new TextExercise();
        exercise.setId(42L);
        current.setExercise(exercise);
        var superseded = slide(unit, 2, "current");
        superseded.setId(12L);
        superseded.setSuperseded(true);
        unit.setSlides(List.of(slide(unit, 1, "previous"), current, superseded));

        var dto = AttachmentVideoUnitDTO.of(unit);
        assertThat(dto.lecture().id()).isEqualTo(71L);
        assertThat(dto.slideMetadataPending()).isTrue();
        assertThat(dto.slides()).containsExactly(new SlideDTO(11L, 1, hiddenUntil, 7L, new SlideDTO.SlideExerciseDTO(42L)));
    }

    @Test
    void preservesLegacySlidesWhenBothFileHashesAreAbsent() {
        var unit = unitWithAttachment(1, null);
        unit.setSlides(List.of(slide(unit, 1, null)));

        assertThat(AttachmentVideoUnitDTO.of(unit).slides()).extracting(SlideDTO::id).containsExactly(11L);
    }

    @Test
    void preservesSlideMappingForUnitsWithoutAttachment() {
        var unit = new AttachmentVideoUnit();
        unit.setId(7L);
        unit.setSlides(List.of(slide(unit, null, null)));

        assertThat(AttachmentVideoUnitDTO.of(unit).slides()).extracting(SlideDTO::id).containsExactly(11L);
    }

    @Test
    void omitsUnavailableSlideAssociation() {
        var unit = unitWithAttachment(2, "current");
        unit.setSlides(null);

        assertThat(AttachmentVideoUnitDTO.of(unit).slides()).isEmpty();
    }

    @Test
    void stopsWaitingWhenOldSlidesAreSupersededByTheCurrentDeck() {
        var unit = unitWithAttachment(2, "current");
        var old = slide(unit, 1, "previous");
        old.setSuperseded(true);
        var current = slide(unit, 2, "current");
        unit.setSlides(List.of(old, current));

        var dto = AttachmentVideoUnitDTO.of(unit);

        assertThat(dto.slideMetadataPending()).isFalse();
        assertThat(dto.slides()).extracting(SlideDTO::id).containsExactly(11L);
    }

    private static AttachmentVideoUnit unitWithAttachment(Integer version, String hash) {
        var unit = new AttachmentVideoUnit();
        unit.setId(7L);
        var attachment = new Attachment();
        attachment.setVersion(version);
        attachment.setSha256Hash(hash);
        unit.setAttachment(attachment);
        return unit;
    }

    private static Slide slide(AttachmentVideoUnit unit, Integer version, String hash) {
        var slide = new Slide();
        slide.setId(11L);
        slide.setSlideNumber(1);
        slide.setAttachmentVideoUnit(unit);
        slide.setAttachmentRevision(version, hash);
        return slide;
    }
}
