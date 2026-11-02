package de.tum.cit.aet.artemis.atlas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress.CompetencyUserId;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;

class CompetencyIdentityBoundaryTest {

    @Test
    void courseValidationRejectsForeignCourseAndAcceptsUnassignedEntities() {
        Competency competency = new Competency();
        assertThatCode(() -> competency.validateCompetencyBelongsToExerciseCourse(1L)).doesNotThrowAnyException();
        Course course = new Course();
        competency.setCourse(course);
        assertThatCode(() -> competency.validateCompetencyBelongsToExerciseCourse(1L)).doesNotThrowAnyException();
        course.setId(1L);
        assertThatCode(() -> competency.validateCompetencyBelongsToExerciseCourse(null)).doesNotThrowAnyException();
        assertThatCode(() -> competency.validateCompetencyBelongsToExerciseCourse(1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> competency.validateCompetencyBelongsToExerciseCourse(2L)).isInstanceOf(BadRequestAlertException.class)
                .hasMessage("The competency does not belong to the exercise's course.");
    }

    @Test
    void progressKeysDistinguishBothUserAndCompetencyAndSupportMapLookup() {
        var key = new CompetencyUserId(1L, 2L);
        var same = new CompetencyUserId(1L, 2L);
        Map<CompetencyUserId, String> progress = new HashMap<>();
        progress.put(key, "mastered");
        assertThat(progress.get(same)).isEqualTo("mastered");
        assertThat(key).isEqualTo(key).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(key).isNotEqualTo(new CompetencyUserId(2L, 2L)).isNotEqualTo(new CompetencyUserId(1L, 3L)).isNotEqualTo(null).isNotEqualTo("1:2");
        var transientKey = new CompetencyUserId();
        assertThat(transientKey).isEqualTo(transientKey).isNotEqualTo(key).isNotEqualTo(new CompetencyUserId());
        assertThat(key).isNotEqualTo(transientKey);
    }
}
