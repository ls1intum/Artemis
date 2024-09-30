package de.tum.cit.aet.artemis.tutorialgroups.test_repository;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;

@Repository
@Primary
public interface TutorialGroupTestRepository extends TutorialGroupRepository {

    @Query("""
            SELECT tutorialGroup
            FROM TutorialGroup tutorialGroup
                LEFT JOIN FETCH tutorialGroup.teachingAssistant
                LEFT JOIN FETCH tutorialGroup.registrations
            WHERE tutorialGroup.title = :title
                AND tutorialGroup.course.id = :courseId
            """)
    Optional<TutorialGroup> findByTitleAndCourseIdWithTeachingAssistantAndRegistrations(@Param("title") String title, @Param("courseId") Long courseId);

    boolean existsByTitleAndCourseId(String title, Long courseId);
}
