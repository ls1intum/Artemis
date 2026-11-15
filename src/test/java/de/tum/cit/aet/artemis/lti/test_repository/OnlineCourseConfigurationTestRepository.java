package de.tum.cit.aet.artemis.lti.test_repository;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.lti.repository.OnlineCourseConfigurationRepository;

@Lazy
@Repository
@Primary
public interface OnlineCourseConfigurationTestRepository extends OnlineCourseConfigurationRepository {

    @Query("SELECT configuration FROM OnlineCourseConfiguration configuration WHERE configuration.course.id = :courseId")
    Optional<OnlineCourseConfiguration> findStoredByCourseId(@Param("courseId") long courseId);
}
