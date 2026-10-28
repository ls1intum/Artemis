package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

class SimilarStudentCohortTest {

    private Map<Long, Double> averages(int students) {
        return LongStream.range(0, students).boxed().collect(Collectors.toMap(id -> id, Long::doubleValue));
    }

    @Test
    void selectsFourGroupsAndExcludesTheRequester() {
        var cohort = SimilarStudentCohort.select(averages(44), 10);
        assertThat(cohort.groupCount()).isEqualTo(4);
        assertThat(cohort.studentIds()).containsExactlyInAnyOrderElementsOf(LongStream.range(0, 10).boxed().toList());
    }

    @Test
    void selectsThreeGroupsForSmallerCourses() {
        var cohort = SimilarStudentCohort.select(averages(33), 11);
        assertThat(cohort.groupCount()).isEqualTo(3);
        assertThat(cohort.studentIds()).hasSize(10).doesNotContain(11L).allMatch(id -> id >= 11 && id <= 21);
    }

    @Test
    void neverSplitsEqualAveragesAtAGroupBoundary() {
        Map<Long, Double> values = new HashMap<>();
        LongStream.range(0, 44).forEach(id -> values.put(id, id < 12 ? 10D : id < 22 ? 20D : id < 33 ? 30D : 40D));
        var cohort = SimilarStudentCohort.select(values, 0);
        assertThat(cohort.groupCount()).isEqualTo(4);
        assertThat(cohort.studentIds()).hasSize(11).allMatch(id -> values.get(id) == 10D);
        assertThat(SimilarStudentCohort.select(values, 12).studentIds()).isEmpty();
    }

    @Test
    void suppressesSmallCoursesAndMissingEvidence() {
        assertThat(SimilarStudentCohort.select(averages(32), 0).studentIds()).isEmpty();
        assertThat(SimilarStudentCohort.select(averages(44), 100).studentIds()).isEmpty();
        assertThat(SimilarStudentCohort.select(Map.of(), 0).studentIds()).isEmpty();
    }

    @Test
    void doesNotInventThreeGroupsWhenAllScoresAreEqual() {
        var values = averages(44);
        values.replaceAll((id, value) -> 70D);
        assertThat(SimilarStudentCohort.select(values, 0).studentIds()).isEmpty();
    }
}
