package de.tum.in.www1.artemis.service.compass;
import de.tum.in.www1.artemis.service.compass.grade.CompassGrade;
import de.tum.in.www1.artemis.service.compass.grade.Grade;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

public class CompassUtilTest {
    @Test
    public void roundingTest() {
        CompassService compassService = new CompassService(null, null, null, null);
        double[] points = {0.0000001, 0.1, 5.09, 6.12, 7.5, 7.59, 8, 9.68, 9.9999999999999};
        double pointSum = 0;
        Map<String, Double> idToPoints = new HashMap<>();

        for (double point : points) {
            idToPoints.put(String.valueOf(point), point);
            pointSum += point;
        }

        Grade grade = new CompassGrade(pointSum, 0.9, 0.9, new HashMap<>(), idToPoints);
        Grade newGrade = compassService.roundGrades(grade);
        double sum = 0 + 0.5 + 5 + 6.5 + 7.5 + 7.5 + 8 + 10 + 10;

        assertThat(newGrade.getPoints()).isCloseTo(sum, offset(0.0000000001));
        assertThat(newGrade.getCoverage()).isEqualTo(grade.getCoverage());
        assertThat(newGrade.getConfidence()).isEqualTo(grade.getConfidence());
        assertThat(newGrade.getJsonIdCommentsMapping()).isEqualTo(grade.getJsonIdCommentsMapping());
    }

}
