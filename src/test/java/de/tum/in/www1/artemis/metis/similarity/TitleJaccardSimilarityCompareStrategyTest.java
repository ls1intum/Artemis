package de.tum.in.www1.artemis.metis.similarity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.service.metis.similarity.TitleJaccardSimilarityCompareStrategy;

public class TitleJaccardSimilarityCompareStrategyTest {

    private TitleJaccardSimilarityCompareStrategy compareStrategy;

    @BeforeEach
    void setUp() {
        compareStrategy = new TitleJaccardSimilarityCompareStrategy();
    }

    @Test
    public void testSimilarTitle() {
        // given
        Post post1 = new Post();
        post1.setTitle("Some title");
        Post post2 = new Post();
        post2.setTitle("Title some");
        Double expectedResult = 1.0;

        // when
        Double actualResult = compareStrategy.performSimilarityCheck(post1, post2);

        // then
        assertThat(actualResult).isEqualTo(expectedResult);
    }

}
