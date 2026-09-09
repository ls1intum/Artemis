package de.tum.cit.aet.artemis.course.dto;

import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.dto.OrganizationDTO;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * A course management response with its explicitly requested organizations.
 *
 * @param course        the scalar course management data
 * @param organizations the initialized organization summaries
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseWithOrganizationsDTO(CourseManagementDTO course, Set<OrganizationDTO> organizations) {

    /**
     * Maps a course and its already initialized organizations without traversing organization back-references.
     *
     * @param course the course to map
     * @return the course and organization response
     */
    public static CourseWithOrganizationsDTO of(Course course) {
        Set<OrganizationDTO> organizations = Hibernate.isInitialized(course.getOrganizations())
                ? course.getOrganizations().stream().map(OrganizationDTO::of).collect(Collectors.toUnmodifiableSet())
                : Set.of();
        return new CourseWithOrganizationsDTO(CourseManagementDTO.of(course), organizations);
    }
}
