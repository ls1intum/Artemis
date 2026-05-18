package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring JPA repository for Organization entities
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface OrganizationRepository extends ArtemisJpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {

    @Query("""
            SELECT organization
            FROM Organization organization
                LEFT JOIN FETCH organization.users
                LEFT JOIN FETCH organization.courses
            WHERE organization.id = :organizationId
            """)
    Optional<Organization> findByIdWithEagerUsersAndCourses(@Param("organizationId") long organizationId);

    /**
     * Get all organizations where the given user is currently in
     *
     * @param userId the id of the user used to retrieve the organizations
     * @return a Set of all organizations the given user is currently in
     */
    @Query("""
            SELECT DISTINCT organization
            FROM Organization organization
                JOIN organization.users ou
            WHERE ou.id = :userId
            """)
    Set<Organization> findAllOrganizationsByUserId(@Param("userId") long userId);

    /**
     * Get all organizations where the given course is currently in
     *
     * @param courseId the id of the course used to retrieve the organizations
     * @return a Set of all organizations the given course is currently in
     */
    @Query("""
            SELECT DISTINCT organization
            FROM Organization organization
                JOIN organization.courses oc
            WHERE oc.id = :courseId
            """)
    Set<Organization> findAllOrganizationsByCourseId(@Param("courseId") long courseId);

    /**
     * Returns the subset of the given user IDs that are members of the given organization.
     *
     * @param organizationId the organization to check membership against
     * @param userIds        the user IDs to check; must not be empty
     * @return the subset of {@code userIds} that belong to the organization
     */
    @Query("""
            SELECT u.id
            FROM Organization o
                JOIN o.users u
            WHERE o.id = :organizationId
                AND u.id IN :userIds
            """)
    Set<Long> findMemberUserIdsByOrganizationIdAndUserIds(@Param("organizationId") long organizationId, @Param("userIds") List<Long> userIds);

    /**
     * Returns the number of users for each organization in the given list, as [organizationId, userCount] pairs.
     *
     * @param ids the organization ids to query
     * @return list of two-element arrays [organizationId, userCount]
     */
    @Query("""
            SELECT o.id, COUNT(u.id)
            FROM Organization o
                LEFT JOIN o.users u
            WHERE o.id IN :ids
            GROUP BY o.id
            """)
    List<Object[]> getUserCountsByOrganizationIds(@Param("ids") List<Long> ids);

    /**
     * Returns the number of courses for each organization in the given list, as [organizationId, courseCount] pairs.
     *
     * @param ids the organization ids to query
     * @return list of two-element arrays [organizationId, courseCount]
     */
    @Query("""
            SELECT o.id, COUNT(c.id)
            FROM Organization o
                LEFT JOIN o.courses c
            WHERE o.id IN :ids
            GROUP BY o.id
            """)
    List<Object[]> getCourseCountsByOrganizationIds(@Param("ids") List<Long> ids);

    /**
     * Returns the title of the organization with the given id.
     *
     * @param organizationId the id of the organization
     * @return the name/title of the organization or null if the organization does not exist
     */
    @Query("""
            SELECT o.name
            FROM Organization o
            WHERE o.id = :organizationId
            """)
    @Cacheable(cacheNames = "organizationTitle", key = "#organizationId", unless = "#result == null")
    String getOrganizationTitle(@Param("organizationId") Long organizationId);

    /**
     * Retrieve a set containing all organizations with an emailPattern matching the
     * provided user's email.
     *
     * @param userEmail the email of the user to match
     * @return a set of all matching organizations
     */
    @NonNull
    default Set<Organization> getAllMatchingOrganizationsByUserEmail(String userEmail) {
        Set<Organization> matchingOrganizations = new HashSet<>();
        // TODO: we should avoid findAll() and instead try to filter this directly in the database
        findAll().forEach(organization -> {
            Pattern pattern = Pattern.compile(organization.getEmailPattern());
            Matcher matcher = pattern.matcher(userEmail);
            if (matcher.matches()) {
                matchingOrganizations.add(organization);
            }
        });
        return matchingOrganizations;
    }

    /**
     * Get an organization containing the eagerly loaded list of users and courses
     *
     * @param organizationId the id of the organization to retrieve
     * @return the organization with the given id containing eagerly loaded list of users and courses
     */
    @NonNull
    default Organization findByIdWithEagerUsersAndCoursesElseThrow(long organizationId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdWithEagerUsersAndCourses(organizationId), organizationId);
    }
}
