package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.dto.ExerciseManagementStatisticsDTO;

class ExerciseManagementStatisticsDTOTest {

    private static ExerciseManagementStatisticsDTO create() {
        return new ExerciseManagementStatisticsDTO(50.5, 10.0, new int[] { 1, 2, 3 }, 6, 7L, 8L, 9L, 4L);
    }

    @Test
    void equalsIsReflexive() {
        var dto = create();
        assertThat(dto.equals(dto)).isTrue();
    }

    @Test
    void equalsComparesArrayContent() {
        assertThat(create()).isEqualTo(create());
        assertThat(create()).hasSameHashCodeAs(create());
    }

    @Test
    void equalsRejectsNullAndOtherTypes() {
        assertThat(create().equals(null)).isFalse();
        assertThat(create().equals("other")).isFalse();
    }

    @Test
    void equalsDetectsEachDifferingComponent() {
        var base = create();
        assertThat(base).isNotEqualTo(new ExerciseManagementStatisticsDTO(1.0, 10.0, new int[] { 1, 2, 3 }, 6, 7L, 8L, 9L, 4L));
        assertThat(base).isNotEqualTo(new ExerciseManagementStatisticsDTO(50.5, 11.0, new int[] { 1, 2, 3 }, 6, 7L, 8L, 9L, 4L));
        assertThat(base).isNotEqualTo(new ExerciseManagementStatisticsDTO(50.5, 10.0, new int[] { 1, 2, 4 }, 6, 7L, 8L, 9L, 4L));
        assertThat(base).isNotEqualTo(new ExerciseManagementStatisticsDTO(50.5, 10.0, new int[] { 1, 2, 3 }, 5, 7L, 8L, 9L, 4L));
        assertThat(base).isNotEqualTo(new ExerciseManagementStatisticsDTO(50.5, 10.0, new int[] { 1, 2, 3 }, 6, 0L, 8L, 9L, 4L));
        assertThat(base).isNotEqualTo(new ExerciseManagementStatisticsDTO(50.5, 10.0, new int[] { 1, 2, 3 }, 6, 7L, 0L, 9L, 4L));
        assertThat(base).isNotEqualTo(new ExerciseManagementStatisticsDTO(50.5, 10.0, new int[] { 1, 2, 3 }, 6, 7L, 8L, 0L, 4L));
        assertThat(base).isNotEqualTo(new ExerciseManagementStatisticsDTO(50.5, 10.0, new int[] { 1, 2, 3 }, 6, 7L, 8L, 9L, 0L));
    }

    @Test
    void toStringContainsArrayContentAndValues() {
        assertThat(create().toString()).isEqualTo("ExerciseManagementStatisticsDTO[averageScoreOfExercise=50.5, maxPointsOfExercise=10.0, scoreDistribution=[1, 2, 3], "
                + "numberOfExerciseScores=6, numberOfParticipations=7, numberOfStudentsOrTeamsInCourse=8, numberOfPosts=9, numberOfResolvedPosts=4]");
    }
}
