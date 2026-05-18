package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.OrganizationCourseDTO;
import de.tum.cit.aet.artemis.core.dto.OrganizationDTO;
import de.tum.cit.aet.artemis.core.dto.OrganizationMemberDTO;
import de.tum.cit.aet.artemis.core.dto.UserForRegistrationDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.OrganizationRepository;
import de.tum.cit.aet.artemis.core.repository.OrganizationSpecs;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

/**
 * Service implementation for managing Organization entities
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    public OrganizationService(OrganizationRepository organizationRepository, UserRepository userRepository, CourseRepository courseRepository) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Performs indexing over all users using the email pattern of the provided organization.
     * Users matching the pattern will be added to the organization.
     * Users not matching the pattern will be removed from the organization (if contained).
     *
     * @param organization the organization used to perform the indexing
     */
    public void index(final Organization organization) {
        log.debug("Start indexing for organization: {}", organization.getName());
        organization.getUsers().forEach(user -> userRepository.removeOrganizationFromUser(user.getId(), organization));
        userRepository.findAllMatchingEmailPattern(organization.getEmailPattern()).forEach(user -> {
            log.debug("User {} matches {} email pattern. Adding", user.getLogin(), organization.getName());
            userRepository.addOrganizationToUser(user.getId(), organization);
        });
    }

    /**
     * Save an organization
     *
     * @param organization the entity to save
     * @return the persisted entity
     */
    public Organization save(Organization organization) {
        log.debug("Request to save Organization : {}", organization.getName());
        return organizationRepository.save(organization);
    }

    /**
     * Add a new organization and execute indexing based on its emailPattern
     *
     * @param organization the organization to add
     * @return the persisted organization entity
     */
    public Organization add(Organization organization) {
        Organization addedOrganization = save(organization);
        addedOrganization = organizationRepository.findByIdWithEagerUsersAndCoursesElseThrow(addedOrganization.getId());
        index(addedOrganization);
        return addedOrganization;
    }

    /**
     * Update an organization
     * To avoid removing the currently mapped users and courses of the organization,
     * these are loaded eagerly and the edited values changed within the loaded entity.
     *
     * @param organization the organization to update
     * @return the updated organization
     */
    public Organization update(Organization organization) {
        log.debug("Request to update Organization : {}", organization);
        boolean indexingRequired = false;
        var oldOrganization = organizationRepository.findByIdWithEagerUsersAndCoursesElseThrow(organization.getId());
        if (!oldOrganization.getEmailPattern().equals(organization.getEmailPattern())) {
            indexingRequired = true;
        }
        oldOrganization.setName(organization.getName());
        oldOrganization.setShortName(organization.getShortName());
        oldOrganization.setUrl(organization.getUrl());
        oldOrganization.setDescription(organization.getDescription());
        oldOrganization.setLogoUrl(organization.getLogoUrl());
        oldOrganization.setEmailPattern(organization.getEmailPattern());
        if (indexingRequired) {
            index(oldOrganization);
        }
        return organizationRepository.save(oldOrganization);
    }

    /**
     * Delete an organization
     *
     * @param organizationId the id of the organization to delete
     */
    public void deleteOrganization(Long organizationId) {
        final Organization organization = organizationRepository.findByIdWithEagerUsersAndCoursesElseThrow(organizationId);

        // we make sure to delete all relations before deleting the organization
        organization.getUsers().forEach(user -> userRepository.removeOrganizationFromUser(user.getId(), organization));
        organization.getCourses().forEach(course -> courseRepository.removeOrganizationFromCourse(course.getId(), organization));

        organizationRepository.delete(organization);
    }

    /**
     * Get a paginated, filtered, and sorted list of all organizations.
     * When {@code withCounts} is {@code true}, user and course counts are fetched per organization.
     *
     * @param search     the search criteria containing search term and pagination/sorting info
     * @param withCounts whether to include aggregated user and course counts
     * @return a page of {@link OrganizationDTO} filtered and sorted according to criteria
     */
    public Page<OrganizationDTO> getOrganizations(SearchTermPageableSearchDTO<String> search, boolean withCounts) {
        Specification<Organization> spec = Specification.where(OrganizationSpecs.searchOrganizations(search.getSearchTerm()))
                .and(OrganizationSpecs.orderedForOrganizations(search.getSortedColumn(), search.getSortingOrder(), withCounts));

        PageRequest pageable = PageRequest.of(search.getPage(), search.getPageSize(), Sort.unsorted());
        Page<Organization> orgPage = organizationRepository.findAll(spec, pageable);

        if (!withCounts) {
            return orgPage.map(org -> new OrganizationDTO(org.getId(), org.getName(), org.getShortName(), org.getEmailPattern(), org.getLogoUrl(), null, null));
        }

        List<Long> ids = orgPage.stream().map(Organization::getId).toList();
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }

        Map<Long, Long> userCounts = organizationRepository.getUserCountsByOrganizationIds(ids).stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        Map<Long, Long> courseCounts = organizationRepository.getCourseCountsByOrganizationIds(ids).stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        return orgPage.map(org -> new OrganizationDTO(org.getId(), org.getName(), org.getShortName(), org.getEmailPattern(), org.getLogoUrl(),
                userCounts.getOrDefault(org.getId(), 0L), courseCounts.getOrDefault(org.getId(), 0L)));
    }

    /**
     * Get a paginated, filtered, and sorted list of users belonging to the given organization.
     *
     * @param organizationId the id of the organization
     * @param search         the search criteria containing search term and pagination/sorting info
     * @return a page of {@link OrganizationMemberDTO} for users in the organization
     */
    public Page<OrganizationMemberDTO> getUsersByOrganizationId(long organizationId, SearchTermPageableSearchDTO<String> search) {
        Specification<User> spec = Specification.where(OrganizationSpecs.membersInOrganization(organizationId)).and(OrganizationSpecs.searchMembers(search.getSearchTerm()))
                .and(OrganizationSpecs.orderedForMembers(search.getSortedColumn(), search.getSortingOrder()));

        PageRequest pageable = PageRequest.of(search.getPage(), search.getPageSize(), Sort.unsorted());
        return userRepository.findAll(spec, pageable).map(user -> {
            String firstName = user.getFirstName() != null ? user.getFirstName() : "";
            String lastName = user.getLastName() != null ? user.getLastName() : "";
            String name = (firstName + " " + lastName).trim();
            return new OrganizationMemberDTO(user.getId(), user.getLogin(), name, user.getEmail());
        });
    }

    /**
     * Get a paginated, filtered, and sorted list of courses belonging to the given organization.
     *
     * @param organizationId the id of the organization
     * @param search         the search criteria containing search term and pagination/sorting info
     * @return a page of {@link OrganizationCourseDTO} for courses in the organization
     */
    public Page<OrganizationCourseDTO> getCoursesByOrganizationId(long organizationId, SearchTermPageableSearchDTO<String> search) {
        Specification<Course> spec = Specification.where(OrganizationSpecs.coursesInOrganization(organizationId)).and(OrganizationSpecs.searchCourses(search.getSearchTerm()))
                .and(OrganizationSpecs.orderedForCourses(search.getSortedColumn(), search.getSortingOrder()));

        PageRequest pageable = PageRequest.of(search.getPage(), search.getPageSize(), Sort.unsorted());
        return courseRepository.findAll(spec, pageable).map(course -> new OrganizationCourseDTO(course.getId(), course.getTitle(), course.getShortName()));
    }

    /**
     * Add multiple users to an organization.
     * Users not found or already members are silently skipped.
     *
     * @param organizationId the id of the organization
     * @param logins         the logins of the users to add
     */
    public void addUsersToOrganization(long organizationId, List<String> logins) {
        if (logins.isEmpty()) {
            return;
        }
        Organization organization = organizationRepository.findByIdElseThrow(organizationId);
        List<User> users = userRepository.findAllByLoginsWithOrganizations(logins);
        List<User> toUpdate = users.stream().filter(user -> !user.getOrganizations().contains(organization)).peek(user -> user.getOrganizations().add(organization)).toList();
        userRepository.saveAll(toUpdate);
    }

    /**
     * Searches Artemis users by login prefix, full-name substring, email substring, or registration-number substring,
     * and marks each result as already a member of the given organization.
     *
     * @param organizationId the organization to check membership against
     * @param loginOrName    the search term entered by the admin
     * @param pageIndex      zero-based page index
     * @param pageSize       number of results per page
     * @return a page of {@link UserForRegistrationDTO} with {@code isRegistered} set appropriately
     */
    public Page<UserForRegistrationDTO> searchUsersForOrganizationRegistration(long organizationId, String loginOrName, int pageIndex, int pageSize) {
        PageRequest pageable = PageRequest.of(pageIndex, pageSize);
        String escaped = loginOrName.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        Page<User> users = userRepository.searchAllByLoginOrNameOrEmailOrRegistrationNumber(pageable, escaped);

        List<Long> userIds = users.getContent().stream().map(User::getId).toList();
        Set<Long> memberIds = userIds.isEmpty() ? Set.of() : organizationRepository.findMemberUserIdsByOrganizationIdAndUserIds(organizationId, userIds);

        List<UserForRegistrationDTO> dtos = users.getContent().stream().map(user -> new UserForRegistrationDTO(user.getId(), user.getLogin(), user.getName(), user.getEmail(),
                user.getRegistrationNumber(), user.getImageUrl(), memberIds.contains(user.getId()))).toList();

        return new PageImpl<>(dtos, pageable, users.getTotalElements());
    }
}
