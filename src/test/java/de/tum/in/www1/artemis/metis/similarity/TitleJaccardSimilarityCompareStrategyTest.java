package de.tum.in.www1.artemis.metis.similarity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.service.metis.similarity.TitleJaccardSimilarityCompareStrategy;

class TitleJaccardSimilarityCompareStrategyTest {

    private TitleJaccardSimilarityCompareStrategy compareStrategy;

    @BeforeEach
    void setUp() {
        compareStrategy = new TitleJaccardSimilarityCompareStrategy();
    }

    @Test
    void testSimilarTitle_equal() {
        Post post1 = new Post();
        post1.setTitle("Some title");
        Post post2 = new Post();
        post2.setTitle("Title some");
        Double expectedResult = 1.0;

        Double actualResult = compareStrategy.performSimilarityCheck(post1, post2);

        assertThat(actualResult).isEqualTo(expectedResult);
    }

    @Test
    void testSimilarTitle_different() {
        Post post1 = new Post();
        post1.setTitle("Totally different");
        Post post2 = new Post();
        post2.setTitle("Something else");

        Double actualResult = compareStrategy.performSimilarityCheck(post1, post2);

        assertThat(actualResult).isLessThan(0.5);
    }

    @Test
    void testSimilarTitle_similar() {
        Post post1 = new Post();
        post1.setTitle("Totally different");
        Post post2 = new Post();
        post2.setTitle("Somewhat different");

        Double actualResult = compareStrategy.performSimilarityCheck(post1, post2);

        assertThat(actualResult).isGreaterThan(0.5).isLessThan(1);
    }

    @Test
    void testSimilarTitle_missing() {
        Post post1 = new Post();
        Post post2 = new Post();
        Double expectedResult = 0.0;

        Double actualResult = compareStrategy.performSimilarityCheck(post1, post2);

        assertThat(actualResult).isEqualTo(expectedResult);
    }
}
