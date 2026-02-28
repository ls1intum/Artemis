package de.tum.cit.aet.artemis.tutorialgroup.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupRegisteredStudentDTO;

@Conditional(TutorialGroupEnabled.class)
@Lazy
@Repository
public interface TutorialGroupRegistrationRepository extends ArtemisJpaRepository<TutorialGroupRegistration, Long> {

    Optional<TutorialGroupRegistration> findTutorialGroupRegistrationByTutorialGroupAndStudentAndType(TutorialGroup tutorialGroup, User student,
            TutorialGroupRegistrationType type);

    Set<TutorialGroupRegistration> findAllByTutorialGroupAndType(TutorialGroup tutorialGroup, TutorialGroupRegistrationType type);

    Set<TutorialGroupRegistration> findAllByTutorialGroup(TutorialGroup tutorialGroup);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByStudentIsInAndTypeAndTutorialGroupCourse(Set<User> students, TutorialGroupRegistrationType type, Course course);

    /**
     * Deletes all tutorial group registrations for a given course.
     *
     * @param courseId the ID of the course
     */
    @Transactional // ok because of delete
    @Modifying
    void deleteAllByTutorialGroupCourseId(long courseId);

    /**
     * Finds all tutorial group registrations for a given course with student and tutorial group information.
     *
     * @param courseId the ID of the course
     * @return set of all registrations in the course
     */
    Set<TutorialGroupRegistration> findAllByTutorialGroupCourseId(long courseId);

    /**
     * Finds all tutorial group registrations for a given user for GDPR data export.
     *
     * @param userId the ID of the user
     * @return list of all registrations for the user with tutorial group information
     */
    @Query("""
            SELECT r
            FROM TutorialGroupRegistration r
            LEFT JOIN FETCH r.tutorialGroup tg
            LEFT JOIN FETCH tg.course
            WHERE r.student.id = :userId
            ORDER BY tg.course.title, tg.title
            """)
    List<TutorialGroupRegistration> findAllByStudentIdWithTutorialGroupAndCourse(@Param("userId") long userId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupRegisteredStudentDTO(
                    student.id,
                    CONCAT(COALESCE(student.firstName, ''), ' ', COALESCE(student.lastName, '')),
                    student.imageUrl,
                    student.login,
                    student.email,
                    student.registrationNumber
            )
            FROM User student
                JOIN student.groups groupName
            WHERE groupName = :studentGroupName
                AND (student.login LIKE %:loginOrName% OR CONCAT(student.firstName, ' ', student.lastName) LIKE %:loginOrName%)
                AND NOT EXISTS (
                    SELECT 1
                    FROM TutorialGroupRegistration registration
                    WHERE registration.tutorialGroup.id = :tutorialGroupId AND registration.student.id = student.id
                )
            ORDER BY
                student.login ASC,
                CONCAT(COALESCE(student.firstName, ''), ' ', COALESCE(student.lastName, '')) ASC,
                student.id ASC
            """)
    List<TutorialGroupRegisteredStudentDTO> searchUnregisteredStudents(@Param("tutorialGroupId") long tutorialGroupId, @Param("studentGroupName") String studentGroupName,
            @Param("loginOrName") String loginOrName, Pageable pageable);
}
