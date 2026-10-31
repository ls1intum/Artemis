package de.tum.cit.aet.artemis.lecture.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

@Repository
@Lazy
@Conditional(LectureEnabled.class)
public interface LectureHomeRepository extends ArtemisJpaRepository<Lecture, Long> {

    // A unit becomes visible only after every configured release date has passed. Compare nullable dates explicitly so legacy undated material still uses ADDED.
    String UNIT_AND_ATTACHMENT_RELEASE = "CASE WHEN u.releaseDate IS NULL OR a.releaseDate > u.releaseDate THEN a.releaseDate ELSE u.releaseDate END";

    String RELEASE = "CASE WHEN e.releaseDate IS NULL OR (" + UNIT_AND_ATTACHMENT_RELEASE + ") > e.releaseDate THEN (" + UNIT_AND_ATTACHMENT_RELEASE + ") ELSE e.releaseDate END";

    String ADDED = "CASE WHEN u.createdDate IS NULL THEN a.uploadDate WHEN a.uploadDate > u.createdDate THEN a.uploadDate ELSE u.createdDate END";

    String PUBLICATION = "CASE WHEN " + RELEASE + " IS NULL THEN " + ADDED + " WHEN " + ADDED + " IS NULL OR " + RELEASE + " > " + ADDED + " THEN " + RELEASE + " ELSE " + ADDED
            + " END";

    String ACCESSIBLE = """
            FROM Lecture l JOIN l.lectureUnits u
            LEFT JOIN ExerciseUnit eu ON eu.id=u.id LEFT JOIN eu.exercise e
            LEFT JOIN AttachmentVideoUnit av ON av.id=u.id LEFT JOIN av.attachment a
            WHERE l.course.id=:courseId
            AND (TYPE(u) <> ExerciseUnit OR (e.course.id=:courseId AND e.exerciseGroup IS NULL
            AND (COALESCE(l.course.onlineCourse,FALSE)=FALSE OR EXISTS(SELECT launch.id FROM LtiResourceLaunch launch WHERE launch.exercise=e AND launch.user.id=:userId))
            AND (e.mode=de.tum.cit.aet.artemis.exercise.domain.ExerciseMode.INDIVIDUAL OR EXISTS(SELECT t.id FROM Team t JOIN t.students m WHERE t.exercise=e AND m.id=:userId))))
            """;

    String BASE = ACCESSIBLE + " AND (u.releaseDate IS NULL OR u.releaseDate<=:asOf) AND (a.releaseDate IS NULL OR a.releaseDate<=:asOf) "
            + "AND (e.releaseDate IS NULL OR e.releaseDate<=:asOf) ";

    interface Item {

        long getId();

        String getTitle();

        ZonedDateTime getPublicationDate();
    }

    interface Changes {

        @Nullable
        ZonedDateTime getUnitRelease();

        @Nullable
        ZonedDateTime getAttachmentRelease();

        @Nullable
        ZonedDateTime getExerciseRelease();
    }

    @Query("SELECT MIN(CASE WHEN u.releaseDate > :asOf THEN u.releaseDate END) AS unitRelease, "
            + "MIN(CASE WHEN a.releaseDate > :asOf THEN a.releaseDate END) AS attachmentRelease, "
            + "MIN(CASE WHEN e.releaseDate > :asOf THEN e.releaseDate END) AS exerciseRelease " + ACCESSIBLE)
    Changes findChanges(@Param("courseId") long courseId, @Param("userId") long userId, @Param("asOf") ZonedDateTime asOf);

    @Query("SELECT l.id AS id,l.title AS title,MAX(" + PUBLICATION + ") AS publicationDate " + BASE + " GROUP BY l.id,l.title HAVING MAX(" + PUBLICATION
            + ") > :since ORDER BY MAX(" + PUBLICATION + ") DESC,l.id")
    List<Item> findUpdates(@Param("courseId") long courseId, @Param("userId") long userId, @Param("asOf") ZonedDateTime asOf, @Param("since") ZonedDateTime since,
            Pageable pageable);

    @Query("SELECT l.id AS id,l.title AS title,MAX(" + PUBLICATION + ") AS publicationDate " + BASE + " GROUP BY l.id,l.title ORDER BY MAX(" + PUBLICATION
            + ") DESC NULLS LAST,l.id")
    List<Item> findReview(@Param("courseId") long courseId, @Param("userId") long userId, @Param("asOf") ZonedDateTime asOf, Pageable pageable);
}
