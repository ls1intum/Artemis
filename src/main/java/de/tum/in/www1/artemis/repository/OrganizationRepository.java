package de.tum.in.www1.artemis.repository;

import java.util.HashSet;
import java.util.List;
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

    @Query("select distinct organization from Organization organization join organization.users ou where ou.id = :#{#userId}")
    Set<Organization> findAllOrganizationsByUserId(@Param("userId") long userId);

    @Query("select distinct organization from Organization organization join organization.courses oc where oc.id = :#{#courseId}")
    Set<Organization> findAllOrganizationsByCourseId(@Param("courseId") long courseId);

    @Query("select count(users.id) as num_user from Organization organization left join organization.users users where organization.id = :#{#organizationId} group by organization.id")
    Long getNumberOfUsersByOrganizationId(@Param("organizationId") long organizationId);

    @Query("select count(courses.id) as num_courses from Organization organization left join organization.courses courses where organization.id = :#{#organizationId} group by organization.id")
    Long getNumberOfCoursesByOrganizationId(@Param("organizationId") long organizationId);

    @NotNull
    default Organization findByIdWithUsersElseThrow(Long organizationId) {
        return findByIdWithEagerUsers(organizationId).orElseThrow(() -> new EntityNotFoundException("Organization: " + organizationId));
    }

    @NotNull
    default Organization findByIdWithCoursesElseThrow(Long organizationId) {
        return findByIdWithEagerCourses(organizationId).orElseThrow(() -> new EntityNotFoundException("Organization: " + organizationId));
    }

    @NotNull
    default void addUserToOrganization(User user, Long organizationId) {
        Organization organization = findByIdWithUsersElseThrow(organizationId);
        if (!organization.getUsers().contains(user)) {
            organization.getUsers().add(user);
            save(organization);
        }
    }

    @NotNull
    default void removeUserFromOrganization(User user, Long organizationId) {
        Organization organization = findByIdWithUsersElseThrow(organizationId);
        if (organization.getUsers().contains(user)) {
            organization.getUsers().remove(user);
            save(organization);
        }
    }

    @NotNull
    default void addCourseToOrganization(Course course, Long organizationId) {
        Organization organization = findByIdWithCoursesElseThrow(organizationId);
        if (!organization.getCourses().contains(course)) {
            organization.getCourses().add(course);
            save(organization);
        }
    }

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
}
