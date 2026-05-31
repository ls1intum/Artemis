package de.tum.cit.aet.artemis.account.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
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
public interface OrganizationRepository extends ArtemisJpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization>, CustomOrganizationRepository {

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
     * Get the number of users currently mapped to the given organization
     *
     * @param organizationId the id of the organization where the users are in
     * @return the number of users contained in the organization
     */
    @Query("""
            SELECT COUNT(user.id)
            FROM User user
                JOIN user.organizations organization
            WHERE organization.id = :organizationId
            """)
    Long getNumberOfUsersByOrganizationId(@Param("organizationId") long organizationId);

    /**
     * Get the number of courses currently mapped to the given organization
     *
     * @param organizationId the id of the organization where the courses are in
     * @return the number of courses contained in the organization
     */
    @Query("""
            SELECT COUNT(course.id)
            FROM Course course
                JOIN course.organizations organization
            WHERE organization.id = :organizationId
            """)
    Long getNumberOfCoursesByOrganizationId(@Param("organizationId") long organizationId);

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

}
