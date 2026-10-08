package de.tum.cit.aet.artemis.tutorialgroup.test_repository;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupsConfigurationRepository;

@Lazy
@Repository
@Primary
public interface TutorialGroupsConfigurationTestRepository extends TutorialGroupsConfigurationRepository {

    @Query("SELECT configuration FROM TutorialGroupsConfiguration configuration WHERE configuration.course.id = :courseId")
    Optional<TutorialGroupsConfiguration> findStoredByCourseId(@Param("courseId") long courseId);
}
