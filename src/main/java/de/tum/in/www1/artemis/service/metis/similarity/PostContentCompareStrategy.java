package de.tum.in.www1.artemis.service.metis.similarity;

import de.tum.in.www1.artemis.domain.metis.Post;

public interface PostContentCompareStrategy {

    Double performSimilarityCheck(Post post1, Post post2);
}
