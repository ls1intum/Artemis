package de.tum.in.www1.artemis.repository.tutorialgroups;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupRegistration;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

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
