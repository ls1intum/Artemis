package de.tum.cit.aet.artemis.tutorialgroup.test_repository;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRegistrationRepository;

@Lazy
@Repository
@Primary
public interface TutorialGroupRegistrationTestRepository extends TutorialGroupRegistrationRepository {

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByStudent(User student);

    @Transactional // ok because of delete
    @Modifying
    void deleteById(@NonNull Long tutorialGroupRegistrationId);

    boolean existsByTutorialGroupTitleAndStudentAndType(String title, User student, TutorialGroupRegistrationType type);

    Integer countByStudentAndTutorialGroupCourseIdAndType(User student, Long courseId, TutorialGroupRegistrationType type);

    /**
     * Removes the registration date of a student's registration in a tutorial group, to recreate a registration from before the date was recorded.
     * A native query, because the audited column is not updatable through the entity.
     *
     * @param tutorialGroupId the tutorial group of the registration
     * @param studentId       the registered student
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional // ok because of modifying query
    @Query(value = "UPDATE tutorial_group_registration SET registration_date = NULL WHERE tutorial_group_id = :tutorialGroupId AND student_id = :studentId", nativeQuery = true)
    void clearRegistrationDate(@Param("tutorialGroupId") long tutorialGroupId, @Param("studentId") long studentId);
}
