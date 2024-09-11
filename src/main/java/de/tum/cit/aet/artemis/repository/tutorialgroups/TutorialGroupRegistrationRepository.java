package de.tum.cit.aet.artemis.repository.tutorialgroups;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.cit.aet.artemis.domain.tutorialgroups.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface TutorialGroupRegistrationRepository extends ArtemisJpaRepository<TutorialGroupRegistration, Long> {

    Optional<TutorialGroupRegistration> findTutorialGroupRegistrationByTutorialGroupAndStudentAndType(TutorialGroup tutorialGroup, User student,
            TutorialGroupRegistrationType type);

    Set<TutorialGroupRegistration> findAllByTutorialGroupAndType(TutorialGroup tutorialGroup, TutorialGroupRegistrationType type);

    Set<TutorialGroupRegistration> findAllByTutorialGroup(TutorialGroup tutorialGroup);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByStudent(User student);

    @Transactional  // ok because of delete
    @Modifying
    void deleteById(@NotNull Long tutorialGroupRegistrationId);

    @Transactional  // ok because of delete
    @Modifying
    void deleteAllByStudentIsInAndTypeAndTutorialGroupCourse(Set<User> students, TutorialGroupRegistrationType type, Course course);

    boolean existsByTutorialGroupTitleAndStudentAndType(String title, User student, TutorialGroupRegistrationType type);

    Integer countByStudentAndTutorialGroupCourseIdAndType(User student, Long courseId, TutorialGroupRegistrationType type);

}
