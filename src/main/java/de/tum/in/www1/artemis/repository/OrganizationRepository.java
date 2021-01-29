package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Organization;

/**
 * Spring JPA repository for Organization entities
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    @Query("select organization from Organization organization left join fetch organization.courses oc where organization.id = :#{#organizationId}")
    Optional<Organization> findByIdWithEagerCourses(@Param("organizationId") long organizationId);

    @Query("select organization from Organization organization left join fetch organization.users ou where organization.id = :#{#organizationId}")
    Optional<Organization> findByIdWithEagerUsers(@Param("organizationId") long organizationId);

    @Query("select organization from Organization organization left join fetch organization.users ou left join fetch organization.courses oc where organization.id = :#{#organizationId}")
    Optional<Organization> findByIdWithEagerUsersAndCourses(@Param("organizationId") long organizationId);

    @Query("select distinct organization from Organization organization join organization.users ou where ou.id = :#{#userId}")
    Set<Organization> findAllOrganizationsByUserId(@Param("userId") long userId);

    @Query("select distinct organization from Organization organization join organization.courses oc where oc.id = :#{#courseId}")
    Set<Organization> findAllOrganizationsByCourseId(@Param("courseId") long courseId);

    @Query("select count(users.id) as num_user from Organization organization left join organization.users users where organization.id = :#{#organizationId} group by organization.id")
    Long getNumberOfUsersByOrganizationId(@Param("organizationId") long organizationId);

    @Query("select count(courses.id) as num_courses from Organization organization left join organization.courses courses where organization.id = :#{#organizationId} group by organization.id")
    Long getNumberOfCoursesByOrganizationId(@Param("organizationId") long organizationId);
}
