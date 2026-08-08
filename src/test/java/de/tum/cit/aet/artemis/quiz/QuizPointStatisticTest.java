package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.quiz.domain.PointCounter;
import de.tum.cit.aet.artemis.quiz.domain.QuizPointStatistic;

class QuizPointStatisticTest {

    @Test
    void shouldPreserveExistingIdsAndAssignLocalIdsToNewCounters() {
        var pointStatistic = new QuizPointStatistic();
        var existingCounter = pointCounter(42L, 2.0);
        pointStatistic.setPointCounters(List.of(existingCounter));

        pointStatistic.addScore(1.0);
        pointStatistic.addScore(1.0);

        assertThat(pointStatistic.getPointCounters()).extracting(PointCounter::getPoints).containsExactly(1.0, 2.0);
        assertThat(pointStatistic.getPointCounters()).extracting(PointCounter::getId).containsExactly(43L, 42L);
    }

    @Test
    void shouldReuseHighestRemovedLocalIdLikeQuestionComponents() {
        var pointStatistic = new QuizPointStatistic();
        pointStatistic.setPointCounters(List.of(pointCounter(41L, 0.0), pointCounter(42L, 1.0)));
        pointStatistic.removePointCounters(pointStatistic.getPointCounters().getLast());

        pointStatistic.addScore(2.0);

        assertThat(pointStatistic.getPointCounters()).extracting(PointCounter::getId).containsExactly(41L, 42L);
    }

    @Test
    void shouldSerializePointCounterWireContract() throws Exception {
        var pointStatistic = new QuizPointStatistic();
        var pointCounter = pointCounter(42L, 2.0);
        pointCounter.setRatedCounter(3);
        pointCounter.setUnRatedCounter(4);
        pointStatistic.setPointCounters(List.of(pointCounter));

        var objectMapper = new ObjectMapper();
        var actualJson = objectMapper.readTree(objectMapper.writeValueAsString(pointStatistic.getPointCounters()));

        assertThat(actualJson).isEqualTo(objectMapper.readTree("""
                [{ "id": 42, "points": 2.0, "ratedCounter": 3, "unRatedCounter": 4 }]
                """));
    }

    @Test
    void shouldNormalizeNullCollectionToEmptyList() {
        var pointStatistic = new QuizPointStatistic();

        pointStatistic.setPointCounters(null);

        assertThat(pointStatistic.getPointCounters()).isEmpty();
    }

    @Test
    void shouldRejectLocalIdOverflow() {
        var pointStatistic = new QuizPointStatistic();
        pointStatistic.setPointCounters(List.of(pointCounter(Long.MAX_VALUE, 0.0)));

        assertThatThrownBy(() -> pointStatistic.addScore(1.0)).isInstanceOf(ArithmeticException.class);
    }

    private PointCounter pointCounter(long id, double points) {
        var pointCounter = new PointCounter();
        pointCounter.setId(id);
        pointCounter.setPoints(points);
        return pointCounter;
    }
}
