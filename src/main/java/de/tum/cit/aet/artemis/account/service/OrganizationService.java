package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
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

import de.tum.cit.aet.artemis.account.domain.Organization;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.dto.OrganizationCourseDTO;
import de.tum.cit.aet.artemis.account.dto.OrganizationDTO;
import de.tum.cit.aet.artemis.account.dto.OrganizationMemberDTO;
import de.tum.cit.aet.artemis.account.repository.OrganizationRepository;
import de.tum.cit.aet.artemis.account.repository.OrganizationSpecs;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.dto.UserForRegistrationDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

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
        userRepository.findAllByOrganizationId(organization.getId()).forEach(user -> userRepository.removeOrganizationFromUser(user.getId(), organization));
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
        addedOrganization = organizationRepository.findByIdElseThrow(addedOrganization.getId());
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
        var oldOrganization = organizationRepository.findByIdElseThrow(organization.getId());
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
     * Returns a paginated, filtered, and sorted list of all organizations.
     * When {@code withCounts} is {@code true}, user and course counts are loaded via two batch queries
     * (one per count type) instead of N+1 individual queries.
     *
     * @param search     the search criteria containing the search term and pagination/sorting info
     * @param withCounts whether to include aggregated user and course counts
     * @return a page of {@link OrganizationDTO}s
     */
    public Page<OrganizationDTO> getAllOrganizations(SearchTermPageableSearchDTO<String> search, boolean withCounts) {
        Specification<Organization> spec = Specification.where(OrganizationSpecs.getOrganizationSpecification(search.getSearchTerm()))
                .and(OrganizationSpecs.orderedForOrganizations(search.getSortedColumn(), search.getSortingOrder(), withCounts));
        var pageable = PageRequest.of(search.getPage(), search.getPageSize(), Sort.unsorted());
        Page<Organization> orgPage = organizationRepository.findAll(spec, pageable);
        if (!withCounts) {
            return orgPage.map(o -> new OrganizationDTO(o.getId(), o.getName(), o.getShortName(), o.getEmailPattern(), o.getLogoUrl(), null, null));
        }
        List<Long> orgIds = orgPage.getContent().stream().map(Organization::getId).toList();
        if (orgIds.isEmpty()) {
            return Page.empty(pageable);
        }
        Map<Long, Long> userCounts = organizationRepository.findUserCountsByOrganizationIds(orgIds).stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        Map<Long, Long> courseCounts = organizationRepository.findCourseCountsByOrganizationIds(orgIds).stream().collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        return orgPage.map(o -> new OrganizationDTO(o.getId(), o.getName(), o.getShortName(), o.getEmailPattern(), o.getLogoUrl(), userCounts.getOrDefault(o.getId(), 0L),
                courseCounts.getOrDefault(o.getId(), 0L)));
    }

    /**
     * Returns a paginated, filtered, and sorted list of users belonging to the given organization.
     *
     * @param organizationId the id of the organization
     * @param search         the search criteria containing the search term and pagination/sorting info
     * @return a page of {@link OrganizationMemberDTO}s
     */
    public Page<OrganizationMemberDTO> getUsersByOrganizationId(long organizationId, SearchTermPageableSearchDTO<String> search) {
        Specification<User> spec = OrganizationSpecs.getMemberSpecification(organizationId, search.getSearchTerm())
                .and(OrganizationSpecs.orderedForMembers(search.getSortedColumn(), search.getSortingOrder()));
        var pageable = PageRequest.of(search.getPage(), search.getPageSize(), Sort.unsorted());
        return userRepository.findAll(spec, pageable).map(u -> new OrganizationMemberDTO(u.getId(), u.getLogin(),
                ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim(), u.getEmail()));
    }

    /**
     * Returns a paginated, filtered, and sorted list of courses belonging to the given organization.
     *
     * @param organizationId the id of the organization
     * @param search         the search criteria containing the search term and pagination/sorting info
     * @return a page of {@link OrganizationCourseDTO}s
     */
    public Page<OrganizationCourseDTO> getCoursesByOrganizationId(long organizationId, SearchTermPageableSearchDTO<String> search) {
        Specification<Course> spec = OrganizationSpecs.getCourseSpecification(organizationId, search.getSearchTerm())
                .and(OrganizationSpecs.orderedForCourses(search.getSortedColumn(), search.getSortingOrder()));
        var pageable = PageRequest.of(search.getPage(), search.getPageSize(), Sort.unsorted());
        return courseRepository.findAll(spec, pageable).map(c -> new OrganizationCourseDTO(c.getId(), c.getTitle(), c.getShortName()));
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
     * @param loginOrName    the raw search term entered by the admin (escaping is handled by the repository default method)
     * @param pageIndex      zero-based page index
     * @param pageSize       number of results per page
     * @return a page of {@link UserForRegistrationDTO} with {@code isRegistered} set appropriately
     */
    public Page<UserForRegistrationDTO> searchUsersForOrganizationRegistration(long organizationId, String loginOrName, int pageIndex, int pageSize) {
        PageRequest pageable = PageRequest.of(pageIndex, pageSize);
        Page<User> users = userRepository.searchAllByLoginOrNameOrEmailOrRegistrationNumber(pageable, loginOrName);

        List<Long> userIds = users.getContent().stream().map(User::getId).toList();
        Set<Long> memberIds = userIds.isEmpty() ? Set.of() : organizationRepository.findMemberUserIdsByOrganizationIdAndUserIds(organizationId, userIds);

        List<UserForRegistrationDTO> dtos = users.getContent().stream().map(user -> new UserForRegistrationDTO(user.getId(), user.getLogin(), user.getName(), user.getEmail(),
                user.getRegistrationNumber(), user.getImageUrl(), memberIds.contains(user.getId()))).toList();

        return new PageImpl<>(dtos, pageable, users.getTotalElements());
    }

    /**
     * Delete an organization
     *
     * @param organizationId the id of the organization to delete
     */
    public void deleteOrganization(Long organizationId) {
        final Organization organization = organizationRepository.findByIdElseThrow(organizationId);

        // we make sure to delete all relations before deleting the organization
        userRepository.findAllByOrganizationId(organizationId).forEach(user -> userRepository.removeOrganizationFromUser(user.getId(), organization));
        courseRepository.findAllByOrganizationId(organizationId).forEach(course -> courseRepository.removeOrganizationFromCourse(course.getId(), organization));

        organizationRepository.delete(organization);
    }
}
