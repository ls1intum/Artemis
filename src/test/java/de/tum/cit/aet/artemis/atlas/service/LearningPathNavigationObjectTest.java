package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationObjectDTO;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class LearningPathNavigationObjectTest {

    @Test
    void navigationIdentityIncludesCompetencyAndLearningObjectType() {
        TextExercise exercise = new TextExercise();
        exercise.setId(1L);
        exercise.setTitle("Sorting");
        Competency competency = new Competency();
        competency.setId(2L);
        TextUnit unit = new TextUnit();
        unit.setId(1L);
        unit.setName("Reading");
        var dto = LearningPathNavigationObjectDTO.of(exercise, true, false, 2L);
        assertThat(dto.equalsLearningObject(exercise, competency)).isTrue();
        assertThat(dto.equalsLearningObject(null, competency)).isFalse();
        assertThat(dto.equalsLearningObject(unit, competency)).isFalse();
        unit.setId(3L);
        assertThat(dto.equalsLearningObject(unit, competency)).isFalse();
        competency.setId(4L);
        assertThat(dto.equalsLearningObject(exercise, competency)).isFalse();
        competency.setId(2L);
        unit.setId(1L);
        var lectureDto = LearningPathNavigationObjectDTO.of(unit, false, true, 2L);
        assertThat(lectureDto.equalsLearningObject(unit, competency)).isTrue();
        assertThat(lectureDto.equalsLearningObject(exercise, competency)).isFalse();
        assertThat(dto.name()).isEqualTo("Sorting");
        assertThat(dto.repeatedTest()).isTrue();
        assertThat(lectureDto.completed()).isTrue();
    }

    @Test
    void unreleasedLectureTitleIsHiddenUntilRelease() {
        TextUnit unit = new TextUnit();
        unit.setId(1L);
        unit.setName("Secret reading");
        unit.setReleaseDate(ZonedDateTime.now().plusYears(1));
        var hidden = LearningPathNavigationObjectDTO.of(unit, false, false, 2L);
        assertThat(hidden.name()).isEmpty();
        assertThat(hidden.unreleased()).isTrue();
        unit.setReleaseDate(null);
        var visible = LearningPathNavigationObjectDTO.of(unit, false, false, 2L);
        assertThat(visible.name()).isEqualTo("Secret reading");
        assertThat(visible.unreleased()).isFalse();
    }
}
