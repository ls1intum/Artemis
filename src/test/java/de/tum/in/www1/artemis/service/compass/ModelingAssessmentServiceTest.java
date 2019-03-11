package de.tum.in.www1.artemis.service.compass;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.service.ModelingAssessmentService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static java.math.BigDecimal.ROUND_HALF_EVEN;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@RunWith(Parameterized.class)
public class ModelingAssessmentServiceTest {
    private static final int NUMBER_OF_TESTCASES = 1000;
    private Random random = new Random();
    private int numberOfAssessments;


    public ModelingAssessmentServiceTest(int numberOfAssessments) {
        this.numberOfAssessments = numberOfAssessments;
    }


    @Parameterized.Parameters
    public static List<Integer> input() {
        Random random = new Random();
        List<Integer> values = new ArrayList<>();
        IntStream.range(0, NUMBER_OF_TESTCASES)
            .forEach(
                value -> {
                    values.add(1 + random.nextInt(100));
                });
        return values;
    }


    /**
     * Testing whether the sum of doubles isn't something like 0.999999
     */
    // TODO CZ: add test again when calculateTotalScore has been moved to AssessmentService class
//    @Test
//    public void testCalculateTotalScore() {
//        List<Feedback> assessments = new ArrayList<>();
//        BigDecimal totalScore = new BigDecimal(0.0).setScale(2, ROUND_HALF_EVEN);
//        for (int i = 0; i < numberOfAssessments; i++) {
//            BigDecimal credits =
//                new BigDecimal(1.0 / (1.0 + random.nextInt(12))).setScale(2, ROUND_HALF_EVEN);
//            totalScore = totalScore.add(credits);
//            assessments.add(new Feedback().credits(credits.doubleValue()));
//        }
//        assertThat(ModelingAssessmentService.calculateTotalScore(assessments))
//            .isEqualTo(totalScore.doubleValue());
//    }
}
