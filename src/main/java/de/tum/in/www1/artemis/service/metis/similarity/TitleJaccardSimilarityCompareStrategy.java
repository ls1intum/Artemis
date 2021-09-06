package de.tum.in.www1.artemis.service.metis.similarity;

import org.apache.commons.text.similarity.JaccardSimilarity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.metis.Post;

@Primary
@Component
public class TitleJaccardSimilarityCompareStrategy implements PostContentCompareStrategy {

    @Override
    public Double performSimilarityCheck(Post post1, Post post2) {
        JaccardSimilarity jaccardSimilarity = new JaccardSimilarity();

        return jaccardSimilarity.apply(post1.getTitle().toLowerCase(), post2.getTitle().toLowerCase());
    }
}
