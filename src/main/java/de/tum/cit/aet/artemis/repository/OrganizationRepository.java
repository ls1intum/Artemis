package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.validation.constraints.NotNull;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.Organization;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring JPA repository for Organization entities
 */
@Profile(PROFILE_CORE)
@Repository
public interface OrganizationRepository extends ArtemisJpaRepository<Organization, Long> {

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
     * Get the number of users currently mapped to the given organization
     *
     * @param organizationId the id of the organization where the users are in
     * @return the number of users contained in the organization
     */
    @Query("""
            SELECT COUNT(users.id) AS num_user
            FROM Organization organization
                LEFT JOIN organization.users users
            WHERE organization.id = :organizationId
            GROUP BY organization.id
            """)
    Long getNumberOfUsersByOrganizationId(@Param("organizationId") long organizationId);

    /**
     * Get the number of courses currently mapped to the given organization
     *
     * @param organizationId the id of the organization where the courses are in
     * @return the number of courses contained in the organization
     */
    @Query("""
            SELECT COUNT(courses.id) AS num_courses
            FROM Organization organization
                LEFT JOIN organization.courses courses
            WHERE organization.id = :organizationId
            GROUP BY organization.id
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
    @NotNull
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
    @NotNull
    default Organization findByIdWithEagerUsersAndCoursesElseThrow(long organizationId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdWithEagerUsersAndCourses(organizationId), organizationId);
    }
}
