package de.tum.cit.aet.artemis.service.metis.similarity;

import de.tum.cit.aet.artemis.domain.metis.Post;

/**
 * This interface offers a method that performs a similarity check on two posts that are compared to each other.
 * Every strategy that implements this interface has to provide this method in order to be applicable as post similarity comparison strategy, that can be interchanged easily.
 */
public interface PostSimilarityComparisonStrategy {

    /**
     * Method implemented by every strategy; compares two posts using any suitable algorithm to determine similarity
     *
     * @param post1 first post object that is compared against
     * @param post2 second post object that is compared against
     * @return the calculated similarity score
     */
    Double performSimilarityCheck(Post post1, Post post2);
}
