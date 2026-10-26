package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Repository
@Lazy
@Profile(PROFILE_CORE)
public interface CourseHomeCommunicationRepository extends ArtemisJpaRepository<Post, Long> {

    String ACCESS = """
            c.course.id=:courseId AND (c.isCourseWide=TRUE OR cp.id IS NOT NULL)
            AND (linkedExercise.id IS NULL OR linkedExercise.releaseDate IS NULL OR linkedExercise.releaseDate<=:asOf)
            AND (linkedExam.id IS NULL OR linkedExam.visibleDate<=:asOf)
            AND (p.visibleForStudents IS NULL OR p.visibleForStudents=TRUE) AND p.creationDate<=:asOf
            """;

    String UNREAD = "SUM(CASE WHEN a.author.id<>:userId AND a.creationDate>cp.lastRead THEN 1 ELSE 0 END)";

    String ACTIVITY = "COALESCE(MAX(a.creationDate),p.creationDate)";

    interface Question {

        long getId();

        long getConversationId();

        String getTitle();

        String getExcerpt();

        long getReplies();

        boolean getResolved();

        long getExternalReplies();

        Long getUnread();

        Long getFocusReplyId();

        ZonedDateTime getActivityAt();
    }

    @Query("""
            SELECT p.id AS id,c.id AS conversationId,p.title AS title,SUBSTRING(p.content,1,500) AS excerpt,
            COUNT(a.id) AS replies,p.resolved AS resolved,SUM(CASE WHEN a.author.id<>:userId THEN 1 ELSE 0 END) AS externalReplies,
            CASE WHEN cp.lastRead IS NULL THEN NULL ELSE
            """ + UNREAD + """
            END AS unread,MIN(CASE WHEN a.author.id<>:userId AND a.creationDate>cp.lastRead THEN a.id END) AS focusReplyId,
            """ + ACTIVITY + """
                       AS activityAt FROM Post p JOIN TREAT(p.conversation AS Channel) c
                       LEFT JOIN c.exercise linkedExercise LEFT JOIN c.exam linkedExam
            LEFT JOIN c.conversationParticipants cp ON cp.user.id=:userId
                       LEFT JOIN p.answers a ON a.creationDate<=:asOf AND (a.verified=TRUE OR a.author.login<>'iris_bot' OR a.author IS NULL)
                       WHERE
                       """ + ACCESS + """
            AND p.author.id=:userId AND c.isAnnouncementChannel=FALSE
            GROUP BY p.id,c.id,p.title,p.content,p.resolved,p.creationDate,cp.lastRead
            ORDER BY CASE WHEN
            """ + UNREAD + ">0 THEN 0 ELSE 1 END," + ACTIVITY + " DESC,p.id DESC")
    List<Question> findQuestions(@Param("courseId") long courseId, @Param("userId") long userId, @Param("asOf") ZonedDateTime asOf, Pageable pageable);

    interface Announcement {

        long getId();

        long getConversationId();

        String getTitle();

        String getExcerpt();

        ZonedDateTime getActivityAt();
    }

    @Query("""
                       SELECT p.id AS id,c.id AS conversationId,p.title AS title,SUBSTRING(p.content,1,500) AS excerpt,COALESCE(p.updatedDate,p.creationDate) AS activityAt
                       FROM Post p JOIN TREAT(p.conversation AS Channel) c LEFT JOIN c.exercise linkedExercise LEFT JOIN c.exam linkedExam
            LEFT JOIN c.conversationParticipants cp ON cp.user.id=:userId
                       WHERE
                       """ + ACCESS + """
            AND c.isAnnouncementChannel=TRUE AND c.isArchived=FALSE
            AND p.displayPriority=de.tum.cit.aet.artemis.communication.domain.DisplayPriority.PINNED
            AND COALESCE(p.updatedDate,p.creationDate)>:since AND COALESCE(p.updatedDate,p.creationDate)<=:asOf
            ORDER BY COALESCE(p.updatedDate,p.creationDate) DESC,p.id DESC
            """)
    List<Announcement> findAnnouncements(@Param("courseId") long courseId, @Param("userId") long userId, @Param("asOf") ZonedDateTime asOf, @Param("since") ZonedDateTime since,
            Pageable pageable);

    interface FaqItem {

        long getId();

        String getTitle();

        Instant getActivityAt();
    }

    @Query("""
            SELECT f.id AS id,f.questionTitle AS title,f.lastModifiedDate AS activityAt FROM Faq f
            WHERE f.course.id=:courseId AND f.faqState=de.tum.cit.aet.artemis.communication.domain.FaqState.ACCEPTED
            AND f.lastModifiedDate>:since AND f.lastModifiedDate<=:asOf ORDER BY f.lastModifiedDate DESC,f.id DESC
            """)
    List<FaqItem> findFaqs(@Param("courseId") long courseId, @Param("asOf") Instant asOf, @Param("since") Instant since, Pageable pageable);
}
