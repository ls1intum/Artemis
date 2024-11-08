package de.tum.cit.aet.artemis.atlas.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface CourseLearnerProfileRepository extends ArtemisJpaRepository<CourseLearnerProfile, Long> {

    @Transactional // ok because of delete
    @Modifying
    @Query("""
                DELETE FROM CourseLearnerProfile clp
                WHERE clp.course = :course AND clp.learnerProfile.user = :user
            """)
    void deleteByCourseAndUser(@Param("course") Course course, @Param("user") User user);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByCourse(Course couese);
}
