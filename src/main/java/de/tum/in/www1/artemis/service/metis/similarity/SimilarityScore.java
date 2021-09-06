package de.tum.in.www1.artemis.service.metis.similarity;

import org.jetbrains.annotations.NotNull;

import de.tum.in.www1.artemis.domain.metis.Post;

public class SimilarityScore implements Comparable<SimilarityScore> {

    private Post post;

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    private Double score;

    public SimilarityScore(Post post, Double score) {
        this.post = post;
        this.score = score;
    }

    @Override
    public int compareTo(@NotNull SimilarityScore similarityScore) {
        return (int) (this.score - similarityScore.score);
    }
}
