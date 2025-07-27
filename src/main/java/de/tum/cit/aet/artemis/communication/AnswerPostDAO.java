package de.tum.cit.aet.artemis.communication;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.domain.User;

@Repository
public class AnswerPostDAO {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public AnswerPost create(Long authorId, Long postId, String content, boolean resolvesPost) {
        AnswerPost answer = new AnswerPost();
        answer.setContent(content);
        answer.setResolvesPost(resolvesPost);
        answer.setAuthor(em.getReference(User.class, authorId));
        answer.setPost(em.getReference(Post.class, postId));
        em.persist(answer);
        return answer;
    }

    @Transactional
    public AnswerPost update(Long answerId, String newContent, Boolean resolvesPost) {
        AnswerPost answer = em.find(AnswerPost.class, answerId);
        if (answer == null)
            throw new IllegalArgumentException("No AnswerPost with id " + answerId);
        answer.setContent(newContent);
        if (resolvesPost != null)
            answer.setResolvesPost(resolvesPost);
        return answer;
    }

    @Transactional
    public void delete(Long answerId) {
        AnswerPost answer = em.getReference(AnswerPost.class, answerId);
        em.remove(answer);
    }

    public AnswerPost findByIdElseThrow(Long answerId) {
        AnswerPost answer = em.find(AnswerPost.class, answerId);
        if (answer == null) {
            throw new IllegalArgumentException("No AnswerPost with id " + answerId);
        }
        return answer;
    }
}
