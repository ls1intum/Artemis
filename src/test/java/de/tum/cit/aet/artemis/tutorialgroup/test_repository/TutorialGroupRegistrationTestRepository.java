package de.tum.cit.aet.artemis.tutorialgroup.test_repository;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRegistrationRepository;

@Lazy
@Repository
@Primary
public interface TutorialGroupRegistrationTestRepository extends TutorialGroupRegistrationRepository {

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByStudent(User student);

    @Transactional  // ok because of delete
    @Modifying
    void deleteById(@NonNull Long tutorialGroupRegistrationId);

    boolean existsByTutorialGroupTitleAndStudentAndType(String title, User student, TutorialGroupRegistrationType type);

    Integer countByStudentAndTutorialGroupCourseIdAndType(User student, Long courseId, TutorialGroupRegistrationType type);
}
