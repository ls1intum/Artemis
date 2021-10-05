package de.tum.in.www1.artemis.service.metis.similarity;

import de.tum.in.www1.artemis.domain.metis.Post;

/**
 * This interface offers a method that performs a similarity check on two posts that are compared to each other.
 * Every strategy that implements this interface has to provide this method in order to be applicable as post content compare strategy, that can be interchanged easily.
 */
public interface PostContentCompareStrategy {

    /**
     * Method implemented by every strategy; compares the content of two posts using any suitable algorithm to determine content similarity
     * @param post1 first post object that is compared against
     * @param post2 second post object that is compared against
     * @return the calculated similarity score
     */
    Double performSimilarityCheck(Post post1, Post post2);
}
