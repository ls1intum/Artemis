package de.tum.in.www1.artemis.service.metis.similarity;

import org.apache.commons.text.similarity.JaccardSimilarity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.metis.Post;

/**
 * Implementation of a PostSimilarityComparisonStrategy to be used when searching for duplicates during post creation.
 * Jaccard Similarity is a common proximity measurement used to compute the similarity between two objects, such as two text documents;
 * In the context of post comparison, the TitleJaccardSimilarityCompareStrategy determines the similarity between two titles (i.e. document) using the number of terms used in both
 * documents.
 * We use the JaccardSimilarity implementation provided by the org.apache.commons.text.similarity package.
 */
@Primary
@Component
public class TitleJaccardSimilarityCompareStrategy implements PostSimilarityComparisonStrategy {

    @Override
    public Double performSimilarityCheck(Post post1, Post post2) {
        JaccardSimilarity jaccardSimilarity = new JaccardSimilarity();
        Double similarityScore = 0.0;

        // we only compute a similarity score if the title of both posts are defined
        if (post1.getTitle() != null && post2.getTitle() != null) {
            similarityScore = jaccardSimilarity.apply(post1.getTitle().toLowerCase(), post2.getTitle().toLowerCase());
        }
        return similarityScore;
    }
}
