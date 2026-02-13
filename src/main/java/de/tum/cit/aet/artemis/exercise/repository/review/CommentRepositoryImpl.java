package de.tum.cit.aet.artemis.exercise.repository.review;

import jakarta.persistence.EntityManager;

import de.tum.cit.aet.artemis.exercise.domain.review.Comment;

public class CommentRepositoryImpl implements CommentRepositoryCustom {

    private final EntityManager entityManager;

    public CommentRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void deleteCommentWithCascade(Comment comment) {
        long commentId = comment.getId();
        long threadId = comment.getThread().getId();
        Long groupId = comment.getThread().getGroup() != null ? comment.getThread().getGroup().getId() : null;

        deleteCommentById(commentId);

        if (countCommentsByThreadId(threadId) == 0) {
            deleteThreadById(threadId);
            if (groupId != null && countThreadsByGroupId(groupId) == 0) {
                deleteGroupById(groupId);
            }
        }
    }

    private void deleteCommentById(long commentId) {
        entityManager.createQuery("DELETE FROM Comment c WHERE c.id = :commentId").setParameter("commentId", commentId).executeUpdate();
    }

    private void deleteThreadById(long threadId) {
        entityManager.createQuery("DELETE FROM CommentThread ct WHERE ct.id = :threadId").setParameter("threadId", threadId).executeUpdate();
    }

    private void deleteGroupById(long groupId) {
        entityManager.createQuery("DELETE FROM CommentThreadGroup g WHERE g.id = :groupId").setParameter("groupId", groupId).executeUpdate();
    }

    private long countCommentsByThreadId(long threadId) {
        return entityManager.createQuery("SELECT COUNT(c) FROM Comment c WHERE c.thread.id = :threadId", Long.class).setParameter("threadId", threadId).getSingleResult();
    }

    private long countThreadsByGroupId(long groupId) {
        return entityManager.createQuery("SELECT COUNT(ct) FROM CommentThread ct WHERE ct.group.id = :groupId", Long.class).setParameter("groupId", groupId).getSingleResult();
    }
}
