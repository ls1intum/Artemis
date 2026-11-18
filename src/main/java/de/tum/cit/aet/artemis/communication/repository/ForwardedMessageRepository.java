package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.ForwardedMessage;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ForwardedMessageRepository extends ArtemisJpaRepository<ForwardedMessage, Long> {

    @Query("""
            SELECT fm
            FROM ForwardedMessage fm
            WHERE fm.destinationPost.id IN :destinationPostIds
            """)
    Set<ForwardedMessage> findAllByDestinationPostIds(@Param("destinationPostIds") Set<Long> destinationPostIds);

    @Query("""
            SELECT fm
            FROM ForwardedMessage fm
            WHERE fm.destinationAnswerPost.id IN :destinationAnswerPostIds
            """)
    Set<ForwardedMessage> findAllByDestinationAnswerPostIds(@Param("destinationAnswerPostIds") Set<Long> destinationAnswerPostIds);

    /**
     * A recipient may read the selected source content when a forward exists in a conversation they can read.
     * This does not grant access to the source conversation or any other message in it.
     * Visibility mirrors Channel.isVisibleToStudents and AnswerPost.isUnverifiedIrisReply. Return scalar IDs so resolving
     * a popular source never loads all destination threads through their eager post associations.
     *
     * @param sourceIds    selected source IDs
     * @param sourceType   source message type
     * @param courseId     course containing the source and destination
     * @param userId       requesting user ID
     * @param isTutor      whether the requester may see unpublished course content
     * @param now          current time for exercise and exam visibility
     * @param irisBotLogin login identifying Iris-generated answers
     * @return IDs whose content was forwarded to a visible, accessible destination
     */
    @Query("""
            SELECT DISTINCT fm.sourceId
            FROM ForwardedMessage fm
                LEFT JOIN fm.destinationPost destinationPost
                LEFT JOIN fm.destinationAnswerPost destinationAnswer
                LEFT JOIN destinationAnswer.post answerParent
                LEFT JOIN destinationPost.conversation postConversation
                LEFT JOIN postConversation.conversationParticipants postParticipant
                LEFT JOIN answerParent.conversation answerConversation
                LEFT JOIN answerConversation.conversationParticipants answerParticipant
                LEFT JOIN Channel destinationChannel ON destinationChannel.id = COALESCE(postConversation.id, answerConversation.id)
                LEFT JOIN destinationChannel.exercise destinationExercise
                LEFT JOIN destinationChannel.exam destinationExam
                LEFT JOIN destinationAnswer.author destinationAuthor
            WHERE fm.sourceId IN :sourceIds AND fm.sourceType = :sourceType
                AND (
                    (postConversation.course.id = :courseId AND (
                        (TYPE(postConversation) = Channel AND TREAT(postConversation AS Channel).isCourseWide = TRUE)
                        OR postParticipant.user.id = :userId
                    ))
                    OR (answerConversation.course.id = :courseId AND (
                        (TYPE(answerConversation) = Channel AND TREAT(answerConversation AS Channel).isCourseWide = TRUE)
                        OR answerParticipant.user.id = :userId
                    ))
                )
                AND (:isTutor = TRUE OR (
                    (destinationExercise.id IS NULL OR (destinationExercise.exerciseGroup IS NULL
                        AND (destinationExercise.releaseDate IS NULL OR destinationExercise.releaseDate < :now)))
                    AND (destinationExercise.id IS NOT NULL OR destinationExam.id IS NULL OR destinationExam.visibleDate < :now)
                    AND (destinationAnswer.id IS NULL OR destinationAnswer.verified = TRUE
                        OR destinationAuthor.id IS NULL OR destinationAuthor.login <> :irisBotLogin)
                ))
            """)
    Set<Long> findAccessibleSourceIds(@Param("sourceIds") Collection<Long> sourceIds, @Param("sourceType") PostingType sourceType, @Param("courseId") Long courseId,
            @Param("userId") Long userId, @Param("isTutor") boolean isTutor, @Param("now") ZonedDateTime now, @Param("irisBotLogin") String irisBotLogin);
}
