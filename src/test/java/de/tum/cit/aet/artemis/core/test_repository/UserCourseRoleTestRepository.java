package de.tum.cit.aet.artemis.core.test_repository;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.UserCourseRoleRepository;

@Lazy
@Repository
@Primary
public interface UserCourseRoleTestRepository extends UserCourseRoleRepository {

    @Transactional
    @Modifying
    void deleteByCourse_Id(Long courseId);
}
