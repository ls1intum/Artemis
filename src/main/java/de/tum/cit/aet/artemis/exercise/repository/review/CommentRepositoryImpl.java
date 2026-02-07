package de.tum.cit.aet.artemis.exercise.repository.review;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import de.tum.cit.aet.artemis.exercise.domain.review.Comment;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThread;
import de.tum.cit.aet.artemis.exercise.domain.review.CommentThreadGroup;

/**
 * Custom repository implementation for comment operations.
 */
public class CommentRepositoryImpl implements CommentRepositoryCustom {

    private final EntityManager entityManager;

    public CommentRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void deleteCommentWithCascade(long commentId) {
        Comment comment = findCommentWithThreadAndGroup(commentId);
        if (comment == null) {
            return;
        }

        CommentThread thread = comment.getThread();
        CommentThreadGroup group = thread.getGroup();
        entityManager.remove(comment);
        entityManager.flush();

        if (countCommentsByThreadId(thread.getId()) == 0) {
            entityManager.remove(entityManager.contains(thread) ? thread : entityManager.merge(thread));
            entityManager.flush();
            if (group != null && countThreadsByGroupId(group.getId()) == 0) {
                entityManager.remove(entityManager.contains(group) ? group : entityManager.merge(group));
            }
        }
    }

    private Comment findCommentWithThreadAndGroup(long commentId) {
        TypedQuery<Comment> query = entityManager.createQuery("""
                SELECT c
                FROM Comment c
                    JOIN FETCH c.thread t
                    LEFT JOIN FETCH t.group
                WHERE c.id = :commentId
                """, Comment.class);
        query.setParameter("commentId", commentId);
        return query.getResultList().stream().findFirst().orElse(null);
    }

    private long countCommentsByThreadId(long threadId) {
        TypedQuery<Long> query = entityManager.createQuery("""
                SELECT COUNT(c)
                FROM Comment c
                WHERE c.thread.id = :threadId
                """, Long.class);
        query.setParameter("threadId", threadId);
        return query.getSingleResult();
    }

    private long countThreadsByGroupId(long groupId) {
        TypedQuery<Long> query = entityManager.createQuery("""
                SELECT COUNT(t)
                FROM CommentThread t
                WHERE t.group.id = :groupId
                """, Long.class);
        query.setParameter("groupId", groupId);
        return query.getSingleResult();
    }
}
