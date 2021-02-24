package de.tum.in.www1.artemis.repository;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.EntityNotFoundException;
import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.User;

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

    /**
     * Get all organizations where the given user is currently in
     * @param userId the id of the user used to retrieve the organizations
     * @return a Set of all organizations the given user is currently in
     */
    @Query("select distinct organization from Organization organization join organization.users ou where ou.id = :#{#userId}")
    Set<Organization> findAllOrganizationsByUserId(@Param("userId") long userId);

    /**
     * Get all organizations where the given course is currently in
     * @param courseId the id of the course used to retrieve the organizations
     * @return a Set of all organizations the given course is currently in
     */
    @Query("select distinct organization from Organization organization join organization.courses oc where oc.id = :#{#courseId}")
    Set<Organization> findAllOrganizationsByCourseId(@Param("courseId") long courseId);

    /**
     * Get the number of users currently mapped to the given organization
     * @param organizationId the id of the organization where the users are in
     * @return the number of users contained in the organization
     */
    @Query("select count(users.id) as num_user from Organization organization left join organization.users users where organization.id = :#{#organizationId} group by organization.id")
    Long getNumberOfUsersByOrganizationId(@Param("organizationId") long organizationId);

    /**
     * Get the number of courses currently mapped to the given organization
     * @param organizationId the id of the organization where the courses are in
     * @return the number of courses contained in the organization
     */
    @Query("select count(courses.id) as num_courses from Organization organization left join organization.courses courses where organization.id = :#{#organizationId} group by organization.id")
    Long getNumberOfCoursesByOrganizationId(@Param("organizationId") long organizationId);

    /**
     * Get an organization with eagerly loaded users, or else throw exception
     * @param organizationId the id of the organization to find
     * @return the organization entity with eagerly loaded users, if it exists
     */
    @NotNull
    default Organization findByIdWithUsersElseThrow(Long organizationId) {
        return findByIdWithEagerUsers(organizationId).orElseThrow(() -> new EntityNotFoundException("Organization: " + organizationId));
    }

    /**
     * Get an organization with eagerly loaded courses, or else throw exception
     * @param organizationId the id of the organization to find
     * @return the organization entity with eagerly loaded courses, if it exists
     */
    @NotNull
    default Organization findByIdWithCoursesElseThrow(Long organizationId) {
        return findByIdWithEagerCourses(organizationId).orElseThrow(() -> new EntityNotFoundException("Organization: " + organizationId));
    }

    /**
     * Add user to an organization, if not contained already
     * @param user the user to add to the organization
     * @param organizationId the id of the organization where the user should be added
     */
    @NotNull
    default void addUserToOrganization(User user, Long organizationId) {
        Organization organization = findByIdWithUsersElseThrow(organizationId);
        if (!organization.getUsers().contains(user)) {
            organization.getUsers().add(user);
            save(organization);
        }
    }

    /**
     * Remove user from an organization, if currently contained
     * @param user the user to remove from the organization
     * @param organizationId the id of the organization from which the user should be removed
     */
    @NotNull
    default void removeUserFromOrganization(User user, Long organizationId) {
        Organization organization = findByIdWithUsersElseThrow(organizationId);
        if (organization.getUsers().contains(user)) {
            organization.getUsers().remove(user);
            save(organization);
        }
    }

    /**
     * Add course to an organization, if not contained already
     * @param course the course to add to the organization
     * @param organizationId the id of the organization where the course should be added
     */
    @NotNull
    default void addCourseToOrganization(Course course, Long organizationId) {
        Organization organization = findByIdWithCoursesElseThrow(organizationId);
        if (!organization.getCourses().contains(course)) {
            organization.getCourses().add(course);
            save(organization);
        }
    }

    /**
     * Remove course from an organization, if currently contained
     * @param course the course to remove from the organization
     * @param organizationId the id of the organization from which the course should be removed
     */
    @NotNull
    default void removeCourseFromOrganization(Course course, Long organizationId) {
        Organization organization = findByIdWithCoursesElseThrow(organizationId);
        if (organization.getCourses().contains(course)) {
            organization.getCourses().remove(course);
            save(organization);
        }
    }

    /**
     * Retrieve a set containing all organizations with an emailPattern matching the
     * provided user's email.
     * @param userEmail the email of the user to match
     * @return a set of all matching organizations
     */
    @NotNull
    default Set<Organization> getAllMatchingOrganizationsByUserEmail(String userEmail) {
        Set<Organization> matchingOrganizations = new HashSet<>();
        this.findAll().forEach(organization -> {
            Pattern pattern = Pattern.compile(organization.getEmailPattern());
            Matcher matcher = pattern.matcher(userEmail);
            if (matcher.matches()) {
                matchingOrganizations.add(organization);
            }
        });
        return matchingOrganizations;
    }

    /**
     * Get an organization by its Id, or else throw exception
     * @param organizationId the id of the organization to find
     * @return the organization entity, if it exists
     */
    @NotNull
    default Organization findOneOrElseThrow(long organizationId) {
        return findById(organizationId).orElseThrow(() -> new EntityNotFoundException("Organization with id: \"" + organizationId + "\" does not exist"));
    }

    /**
     * Get an organization containing the eagerly loaded list of users and courses
     * @param organizationId the id of the organization to retrieve
     * @return the organization with the given Id containing eagerly loaded list of users and courses
     */
    @NotNull
    default Organization findOneWithEagerUsersAndCoursesOrElseThrow(long organizationId) {
        return findByIdWithEagerUsersAndCourses(organizationId).orElseThrow(() -> new EntityNotFoundException("Organization with id: \"" + organizationId + "\" does not exist"));
    }
}
