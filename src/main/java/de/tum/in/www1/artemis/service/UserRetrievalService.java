package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service class for retrieving users. Should have no dependencies to other services and should be kept as simple as possible to avoid circular dependency issues
 */
@Service
public class UserRetrievalService {

    private final UserRepository userRepository;

    private final AuthorityRepository authorityRepository;

    public UserRetrievalService(UserRepository userRepository, AuthorityRepository authorityRepository) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
    }

    /**
     * Get all managed users
     *
     * @param userSearch used to find users
     * @return all users
     */
    public Page<UserDTO> getAllManagedUsers(PageableSearchDTO<String> userSearch) {
        final var searchTerm = userSearch.getSearchTerm();
        var sorting = Sort.by(userSearch.getSortedColumn());
        sorting = userSearch.getSortingOrder() == SortingOrder.ASCENDING ? sorting.ascending() : sorting.descending();
        final var sorted = PageRequest.of(userSearch.getPage(), userSearch.getPageSize(), sorting);
        return userRepository.searchByLoginOrNameWithGroups(searchTerm, sorted).map(UserDTO::new);
    }

    /**
     * Search for all users by login or name
     *
     * @param pageable    Pageable configuring paginated access (e.g. to limit the number of records returned)
     * @param loginOrName Search query that will be searched for in login and name field
     * @return all users matching search criteria
     */
    public Page<UserDTO> searchAllUsersByLoginOrName(Pageable pageable, String loginOrName) {
        Page<User> users = userRepository.searchAllByLoginOrName(pageable, loginOrName);
        users.forEach(user -> user.setVisibleRegistrationNumber(user.getRegistrationNumber()));
        return users.map(UserDTO::new);
    }

    /**
     * Get user with groups and authorities by given login string
     *
     * @param login user login string
     * @return existing user with given login string or null
     */
    public Optional<User> getUserWithGroupsAndAuthoritiesByLogin(String login) {
        return userRepository.findOneWithGroupsAndAuthoritiesByLogin(login);
    }

    /**
     * Get user with authorities by given login string
     *
     * @param login user login string
     * @return existing user with given login string or null
     */
    public Optional<User> getUserWithAuthoritiesByLogin(String login) {
        return userRepository.findOneWithGroupsAndAuthoritiesByLogin(login);
    }

    /**
     * Get current user for login string
     *
     * @param login user login string
     * @return existing user for the given login string or null
     */
    public Optional<User> getUserByLogin(String login) {
        return userRepository.findOneByLogin(login);
    }

    /**
     * @return existing user object by current user login
     */
    @NotNull
    public User getUser() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = userRepository.findOneByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    /**
     * Get user with user groups and authorities of currently logged in user
     *
     * @return currently logged in user
     */
    @NotNull
    public User getUserWithGroupsAndAuthorities() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    /**
     * Get user with user groups, authorities and guided tour settings of currently logged in user
     * Note: this method should only be invoked if the guided tour settings are really needed
     *
     * @return currently logged in user
     */
    @NotNull
    public User getUserWithGroupsAuthoritiesAndGuidedTourSettings() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = userRepository.findOneWithGroupsAuthoritiesAndGuidedTourSettingsByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    @NotNull
    private User unwrapOptionalUser(Optional<User> optionalUser, String currentUserLogin) {
        if (optionalUser.isPresent()) {
            return optionalUser.get();
        }
        throw new EntityNotFoundException("No user found with login: " + currentUserLogin);
    }

    private String getCurrentUserLogin() {
        Optional<String> currentUserLogin = SecurityUtils.getCurrentUserLogin();
        if (currentUserLogin.isPresent()) {
            return currentUserLogin.get();
        }
        throw new EntityNotFoundException("ERROR: No current user login found!");
    }

    /**
     * Get user with user groups and authorities with the username (i.e. user.getLogin() or principal.getName())
     *
     * @param username the username of the user who should be retrieved from the database
     * @return the user that belongs to the given principal with eagerly loaded groups and authorities
     */
    public User getUserWithGroupsAndAuthorities(@NotNull String username) {
        Optional<User> user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(username);
        return unwrapOptionalUser(user, username);
    }

    /**
     * @return a list of all the authorities
     */
    public List<String> getAuthorities() {
        return authorityRepository.findAll().stream().map(Authority::getName).collect(Collectors.toList());
    }

    /**
     * Get all users in a given team
     *
     * @param course        The course to which the team belongs (acts as a scope for the team short name)
     * @param teamShortName The short name of the team for which to get all students
     * @return A set of all users that belong to the team
     */
    public Set<User> findAllUsersInTeam(Course course, String teamShortName) {
        return userRepository.findAllInTeam(course.getId(), teamShortName);
    }

    /**
     * Finds a single user with groups and authorities using the registration number
     *
     * @param registrationNumber user registration number as string
     * @return the user with groups and authorities
     */
    public Optional<User> findUserWithGroupsAndAuthoritiesByRegistrationNumber(String registrationNumber) {
        if (!StringUtils.hasText(registrationNumber)) {
            return Optional.empty();
        }
        return userRepository.findOneWithGroupsAndAuthoritiesByRegistrationNumber(registrationNumber);
    }

    /**
     * Finds a single user with groups and authorities using the login name
     *
     * @param login user login string
     * @return the user with groups and authorities
     */
    public Optional<User> findUserWithGroupsAndAuthoritiesByLogin(String login) {
        if (!StringUtils.hasText(login)) {
            return Optional.empty();
        }
        return userRepository.findOneWithGroupsAndAuthoritiesByLogin(login);
    }

    /**
     * Get students by given course
     *
     * @param course object
     * @return list of students for given course
     */
    public List<User> getStudents(Course course) {
        return findAllUsersInGroupWithAuthorities(course.getStudentGroupName());
    }

    /**
     * Get tutors by given course
     *
     * @param course object
     * @return list of tutors for given course
     */
    public List<User> getTutors(Course course) {
        return findAllUsersInGroupWithAuthorities(course.getTeachingAssistantGroupName());
    }

    /**
     * Get all instructors for a given course
     *
     * @param course The course for which to fetch all instructors
     * @return A list of all users that have the role of instructor in the course
     */
    public List<User> getInstructors(Course course) {
        return findAllUsersInGroupWithAuthorities(course.getInstructorGroupName());
    }

    /**
     * Get all users in a given group
     *
     * @param groupName The group name for which to return all members
     * @return A list of all users that belong to the group
     */
    public List<User> findAllUsersInGroupWithAuthorities(String groupName) {
        return userRepository.findAllInGroupWithAuthorities(groupName);
    }

    public Long countUserInGroup(String groupName) {
        return userRepository.countByGroupsIsContaining(groupName);
    }
}
