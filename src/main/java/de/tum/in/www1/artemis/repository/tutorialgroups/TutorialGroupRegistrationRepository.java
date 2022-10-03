package de.tum.in.www1.artemis.repository.tutorialgroups;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("""
            SELECT registration
            FROM TutorialGroupRegistration registration
            JOIN registration.tutorialGroup tutorialGroup
            JOIN registration.student student
            JOIN tutorialGroup.course course
            WHERE course = :#{#course} AND student = :#{#user}
            """)
    Set<TutorialGroupRegistration> findByCourseAndUserWithTutorialGroups(@Param("course") Course course, @Param("user") User user);

    void deleteAllByStudent(User student);

}
