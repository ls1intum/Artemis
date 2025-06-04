package de.tum.cit.aet.artemis.atlas.test_repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.CourseLearnerProfileRepository;
import de.tum.cit.aet.artemis.core.domain.Course;

public interface CourseLearnerProfileTestRepository extends CourseLearnerProfileRepository {

    @Query("""
                SELECT clp
                FROM CourseLearnerProfile clp
                    LEFT JOIN FETCH clp.course
                WHERE clp.learnerProfile.user.login = :login AND clp.course = :course
            """)
    Optional<CourseLearnerProfile> findByLoginWithCourse(@Param("login") String login, @Param("course") Course course);
}
