package de.tum.cit.aet.artemis.account.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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

import de.tum.cit.aet.artemis.account.domain.Organization;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring JPA repository for Organization entities
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface OrganizationRepository extends ArtemisJpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {

    /**
     * Get all organizations where the given user is currently in
     *
     * @param userId the id of the user used to retrieve the organizations
     * @return a Set of all organizations the given user is currently in
     */
    @Query("""
            SELECT DISTINCT organization
            FROM User user
                JOIN user.organizations organization
            WHERE user.id = :userId
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
            FROM Course course
                JOIN course.organizations organization
            WHERE course.id = :courseId
            """)
    Set<Organization> findAllOrganizationsByCourseId(@Param("courseId") long courseId);

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
     * Returns the subset of the given user IDs that are members of the given organization.
     * Uses the owning-side join ({@link User#getOrganizations()}) because {@link Organization}
     * has no inverse-side {@code users} collection.
     *
     * @param organizationId the organization to check membership against
     * @param userIds        the user IDs to check; must not be empty
     * @return the subset of {@code userIds} that belong to the organization
     */
    @Query("""
            SELECT u.id
            FROM User u
                JOIN u.organizations o
            WHERE o.id = :organizationId
                AND u.id IN :userIds
            """)
    Set<Long> findMemberUserIdsByOrganizationIdAndUserIds(@Param("organizationId") long organizationId, @Param("userIds") List<Long> userIds);

    /**
     * Returns the number of users per organization for a batch of organization IDs.
     * Each element is a two-element {@code Object[]} where {@code [0]} is the organization id and {@code [1]} is the user count.
     *
     * @param organizationIds the ids of the organizations to count users for
     * @return a list of [organizationId, userCount] pairs; organizations with no members are omitted
     */
    @Query("""
            SELECT o.id, COUNT(u)
            FROM User u
                JOIN u.organizations o
            WHERE o.id IN :organizationIds
            GROUP BY o.id
            """)
    List<Object[]> findUserCountsByOrganizationIds(@Param("organizationIds") Collection<Long> organizationIds);

    /**
     * Returns the number of courses per organization for a batch of organization IDs.
     * Each element is a two-element {@code Object[]} where {@code [0]} is the organization id and {@code [1]} is the course count.
     *
     * @param organizationIds the ids of the organizations to count courses for
     * @return a list of [organizationId, courseCount] pairs; organizations with no courses are omitted
     */
    @Query("""
            SELECT o.id, COUNT(c)
            FROM Course c
                JOIN c.organizations o
            WHERE o.id IN :organizationIds
            GROUP BY o.id
            """)
    List<Object[]> findCourseCountsByOrganizationIds(@Param("organizationIds") Collection<Long> organizationIds);

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

}
