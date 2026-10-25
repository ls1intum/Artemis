package de.tum.cit.aet.artemis.course.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.Course;

@Repository
@Lazy
@Profile(PROFILE_CORE)
public interface CourseHomeRepository extends ArtemisJpaRepository<Course, Long> {

    interface Staff {

        long getId();

        String getFirstName();

        String getLastName();

        String getImageUrl();

        String getLogin();

        int getRank();
    }

    @Query("""
            SELECT u.id AS id, u.firstName AS firstName, u.lastName AS lastName, u.imageUrl AS imageUrl, u.login AS login,
            MIN(CASE r.role WHEN de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR THEN 0
            WHEN de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR THEN 1 ELSE 2 END) AS rank
            FROM UserCourseRole r JOIN r.user u WHERE r.course.id = :courseId AND u.activated = TRUE AND u.deleted = FALSE
            AND r.role <> de.tum.cit.aet.artemis.core.domain.CourseRole.STUDENT
            GROUP BY u.id, u.firstName, u.lastName, u.imageUrl, u.login
            ORDER BY MIN(CASE r.role WHEN de.tum.cit.aet.artemis.core.domain.CourseRole.INSTRUCTOR THEN 0
            WHEN de.tum.cit.aet.artemis.core.domain.CourseRole.EDITOR THEN 1 ELSE 2 END), u.lastName, u.firstName, u.id
            """)
    Slice<Staff> findStaff(@Param("courseId") long courseId, Pageable pageable);
}
