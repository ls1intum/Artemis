package de.tum.in.www1.artemis.repository.tutorialgroups;

import java.util.Optional;
import java.util.Set;

import javax.transaction.Transactional;
import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupRegistration;

@Repository
public interface TutorialGroupRegistrationRepository extends JpaRepository<TutorialGroupRegistration, Long> {

    Optional<TutorialGroupRegistration> findTutorialGroupRegistrationByTutorialGroupAndStudentAndType(TutorialGroup tutorialGroup, User student,
            TutorialGroupRegistrationType type);

    Set<TutorialGroupRegistration> findAllByTutorialGroupAndType(TutorialGroup tutorialGroup, TutorialGroupRegistrationType type);

    Set<TutorialGroupRegistration> findAllByTutorialGroup(TutorialGroup tutorialGroup);

    @Modifying
    @Transactional
    // ok because of delete
    void deleteAllByStudent(User student);

    @Transactional
    @Modifying
    void deleteById(@NotNull Long tutorialGroupRegistrationId);

    @Modifying
    @Transactional
    void deleteAllByStudentIsInAndTypeAndTutorialGroupCourse(Set<User> students, TutorialGroupRegistrationType type, Course course);

    boolean existsByTutorialGroupTitleAndStudentAndType(String title, User student, TutorialGroupRegistrationType type);

    Integer countByStudentAndTutorialGroupCourseIdAndType(User student, Long courseId, TutorialGroupRegistrationType type);

}
