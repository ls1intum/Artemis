package de.tum.cit.aet.artemis.core.user.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.test_repository.ScienceEventTestRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.UserDTO;
import de.tum.cit.aet.artemis.core.dto.UserInitializationDTO;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.AuthorityRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.lti.service.LtiService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.UserSshPublicKey;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.service.ci.CIUserManagementService;
import de.tum.cit.aet.artemis.programming.util.MockDelegate;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Note: this class should be independent of the actual VCS and CIS and contains common test logic for scenarios:
 * 1) Jenkins + LocalVC
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class UserTestService {

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private UserTestRepository userTestRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    private Optional<CIUserManagementService> optionalCIUserManagementService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TeamUtilService teamUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ScienceEventTestRepository scienceEventRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository;

    @Autowired
    private ParticipationTestRepository participationRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private Optional<LearnerProfileRepository> learnerProfileRepository;

    @Autowired
    private ExerciseTestRepository exerciseTestRepository;

    private String TEST_PREFIX;

    private MockDelegate mockDelegate;

    public User student;

    private ScienceEvent scienceEvent;

    private static final int NUMBER_OF_STUDENTS = 2;

    private static final int NUMBER_OF_TUTORS = 1;

    private static final int NUMBER_OF_EDITORS = 1;

    private static final int NUMBER_OF_INSTRUCTORS = 1;

    public void setup(String testPrefix, MockDelegate mockDelegate) throws Exception {
        this.TEST_PREFIX = testPrefix;
        this.mockDelegate = mockDelegate;
        List<User> users = userUtilService.addUsers(testPrefix, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, NUMBER_OF_EDITORS, NUMBER_OF_INSTRUCTORS);
        student = userTestRepository.getUserByLoginElseThrow(testPrefix + "student1");
        student.setInternal(true);
        student = userTestRepository.save(student);
        student = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()).orElseThrow();

        users.forEach(user -> cacheManager.getCache(UserRepository.USERS_CACHE).evict(user.getLogin()));

        final var event = new ScienceEvent();
        event.setIdentity(student.getLogin());
        event.setTimestamp(ZonedDateTime.now());
        event.setType(ScienceEventType.EXERCISE__OPEN);
        scienceEvent = scienceEventRepository.save(event);
    }

    public void tearDown() throws IOException {
        userTestRepository.deleteAll(userTestRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
    }

    public User getStudent() {
        return student;
    }

    private void assertThatUserWasSoftDeleted(User originalUser, User deletedUser) throws Exception {
        assertThat(deletedUser.isDeleted()).isTrue();
        assertThat(deletedUser.getFirstName()).isEqualTo(Constants.USER_FIRST_NAME_AFTER_SOFT_DELETE);
        assertThat(deletedUser.getLastName()).isEqualTo(Constants.USER_LAST_NAME_AFTER_SOFT_DELETE);
        assertThat(deletedUser.getLogin()).isNotEqualTo(originalUser.getLogin());
        assertThat(deletedUser.getPassword()).isNotEqualTo(originalUser.getPassword());
        assertThat(deletedUser.getEmail()).endsWith(Constants.USER_EMAIL_DOMAIN_AFTER_SOFT_DELETE);
        assertThat(deletedUser.getRegistrationNumber()).isNull();
        assertThat(deletedUser.getImageUrl()).isNull();
        assertThat(deletedUser.getActivated()).isFalse();

        // check only if owner of event is asserted
        if (originalUser.getLogin().equals(scienceEvent.getIdentity())) {
            final var deletedEvent = scienceEventRepository.findById(scienceEvent.getId()).orElseThrow();
            assertThat(deletedEvent.getIdentity()).isEqualTo(deletedUser.getLogin());
        }
    }

    private void assertThatUserWasNotSoftDeleted(User originalUser, User deletedUser) throws Exception {
        assertThat(deletedUser.isDeleted()).isFalse();
        assertThat(deletedUser.getFirstName()).isEqualTo(originalUser.getFirstName());
        assertThat(deletedUser.getLastName()).isEqualTo(originalUser.getLastName());
        assertThat(deletedUser.getLogin()).isEqualTo(originalUser.getLogin());
        assertThat(deletedUser.getPassword()).isEqualTo(originalUser.getPassword());
        assertThat(deletedUser.getEmail()).isEqualTo(originalUser.getEmail());
        assertThat(deletedUser.getRegistrationNumber()).isEqualTo(originalUser.getVisibleRegistrationNumber());
        assertThat(deletedUser.getImageUrl()).isEqualTo(originalUser.getImageUrl());

        // check only if owner of event is asserted
        if (originalUser.getLogin().equals(scienceEvent.getIdentity())) {
            final var unchangedEvent = scienceEventRepository.findById(scienceEvent.getId()).orElseThrow();
            assertThat(unchangedEvent.getIdentity()).isEqualTo(originalUser.getLogin());
        }
    }

    // Test
    public void deleteUser_isSuccessful() throws Exception {
        student.setRegistrationNumber("123");
        student.setImageUrl("images/user/profiles-pictures/image.jpg");
        userTestRepository.save(student);

        request.delete("/api/core/admin/users/" + student.getLogin(), HttpStatus.OK);

        final var deletedUser = userTestRepository.findById(student.getId()).orElseThrow();
        assertThatUserWasSoftDeleted(student, deletedUser);
    }

    // Test
    public void deleteSelf_isNotSuccessful(String currentUserLogin) throws Exception {
        request.delete("/api/core/admin/users/" + currentUserLogin, HttpStatus.BAD_REQUEST);
        final var deletedUser = userTestRepository.findById(student.getId()).orElseThrow();
        assertThatUserWasNotSoftDeleted(student, deletedUser);
    }

    // Test
    public void deleteUsers(String currentUserLogin) throws Exception {
        userTestRepository.deleteAll(userTestRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        var users = Stream.of("student1", "tutor1", "editor1", "instructor1").map(login -> {
            final User user = userUtilService.getUserByLogin(TEST_PREFIX + login);
            user.getGroups().clear();
            return userTestRepository.save(user);
        }).collect(Collectors.toSet());

        var logins = users.stream().map(User::getLogin).toList();
        request.delete("/api/core/admin/users", HttpStatus.OK, logins);

        for (var user : users) {
            final var deletedUser = userTestRepository.findById(user.getId()).orElseThrow();

            if (deletedUser.getLogin().equals(currentUserLogin)) {
                assertThatUserWasNotSoftDeleted(user, deletedUser);
            }
            else {
                assertThatUserWasSoftDeleted(user, deletedUser);
            }
        }
    }

    // Test
    public void updateUser_asAdmin_isSuccessful() throws Exception {
        student.setInternal(true);
        student = userTestRepository.save(student);
        final var newPassword = "bonobo42";
        final var newEmail = "bonobo42@tum.com";
        final var newFirstName = "Bruce";
        final var newGroups = Set.of("foo", "bar");
        final var newLastName = "Wayne";
        final var newImageUrl = "foobar.png";
        final var newLangKey = "DE";
        final var newAuthorities = Stream.of(Role.TEACHING_ASSISTANT.getAuthority()).map(authorityRepository::findById).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toSet());
        final var oldGroups = student.getGroups();
        student.setAuthorities(newAuthorities);
        student.setEmail(newEmail);
        student.setFirstName(newFirstName);
        student.setGroups(newGroups);
        student.setLastName(newLastName);
        student.setImageUrl(newImageUrl);
        student.setLangKey(newLangKey);

        student.setPassword(newPassword);
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, newPassword, oldGroups);

        var managedUserVM = new ManagedUserVM(student, newPassword);
        managedUserVM.setPassword(newPassword);
        final var response = request.putWithResponseBody("/api/core/admin/users", managedUserVM, User.class, HttpStatus.OK);
        final var updatedUserIndDB = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()).orElseThrow();

        assertThat(response).isNotNull();
        assertThat(passwordService.checkPasswordMatch(newPassword, updatedUserIndDB.getPassword())).isTrue();

        // set passwords to null to exclude them from the comparison
        student.setPassword(null);
        updatedUserIndDB.setPassword(null);

        assertThat(student).as("Returned user is equal to sent update").isEqualTo(response);
        assertThat(student).as("Updated user in DB is equal to sent update").isEqualTo(updatedUserIndDB);
    }

    // Test
    public void updateUserWithEmptyRoles() throws Exception {
        student.setInternal(true);
        student.setAuthorities(null);

        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, "foobar1234", student.getGroups());

        var managedUserVM = new ManagedUserVM(student, "foobar1234");

        final var response = request.putWithResponseBody("/api/core/admin/users", managedUserVM, User.class, HttpStatus.OK);
        assertThat(response).isNotNull();

        // do not allow empty authorities
        final var updatedUserInDB = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()).orElseThrow();
        assertThat(updatedUserInDB.getAuthorities()).containsExactly(new Authority(Role.STUDENT.getAuthority()));
    }

    // Test
    public void updateUser_withNullPassword_oldPasswordNotChanged() throws Exception {
        student.setPassword(null);
        final var oldPassword = userTestRepository.findById(student.getId()).orElseThrow().getPassword();
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, null, student.getGroups());

        request.put("/api/core/admin/users", new ManagedUserVM(student), HttpStatus.OK);
        final var userInDB = userTestRepository.findById(student.getId()).orElseThrow();

        assertThat(oldPassword).as("Password did not change").isEqualTo(userInDB.getPassword());
    }

    // Test
    public void updateUserLogin() throws Exception {
        var oldLogin = student.getLogin();
        student.setLogin("new-login");
        mockDelegate.mockUpdateUserInUserManagement(oldLogin, student, null, student.getGroups());

        request.put("/api/core/admin/users", new ManagedUserVM(student), HttpStatus.OK);
        final var userInDB = userTestRepository.findById(student.getId()).orElseThrow();

        assertThat(userInDB.getLogin()).isEqualTo(student.getLogin());
        assertThat(userInDB.getId()).isEqualTo(student.getId());
    }

    // Test
    public void updateUserInvalidId() throws Exception {
        long oldId = student.getId();
        student.setId(oldId + 1);
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, null, student.getGroups());

        request.put("/api/core/admin/users", new ManagedUserVM(student, student.getPassword()), HttpStatus.BAD_REQUEST);
        final var userInDB = userTestRepository.findById(oldId).orElseThrow();
        assertThat(userInDB).isNotEqualTo(student);
        assertThat(userTestRepository.findById(oldId + 1)).isNotEqualTo(student);
    }

    // Test
    public void updateUserExistingEmail() throws Exception {
        long oldId = student.getId();
        student.setId(oldId + 1);
        student.setEmail("newEmail@testing.user");
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, null, student.getGroups());

        request.put("/api/core/admin/users", new ManagedUserVM(student, student.getPassword()), HttpStatus.BAD_REQUEST);
        final var userInDB = userTestRepository.findById(oldId).orElseThrow();
        assertThat(userInDB).isNotEqualTo(student);
        assertThat(userTestRepository.findById(oldId + 1)).isNotEqualTo(student);
    }

    // Test
    public void updateUser_withExternalUserManagement() throws Exception {
        student.setFirstName("changed");
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, null, student.getGroups());

        request.put("/api/core/admin/users", new ManagedUserVM(student), HttpStatus.OK);

        var updatedUser = userTestRepository.findById(student.getId());
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getFirstName()).isEqualTo("changed");
    }

    // Test
    public void updateUserGroups() throws Exception {
        var course = courseUtilService.addEmptyCourse();
        programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        courseRepository.save(course);

        // First we create a new user with group
        student.setGroups(Set.of("instructor"));
        student = userTestRepository.save(student);

        // We will then update the user by modifying the groups
        var updatedUser = student;
        updatedUser.setGroups(Set.of("tutor"));
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), updatedUser, null, student.getGroups());
        request.put("/api/core/admin/users", new ManagedUserVM(updatedUser, "this is a password"), HttpStatus.OK);

        var updatedUserOrEmpty = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(updatedUser.getLogin());
        assertThat(updatedUserOrEmpty).isPresent();

        updatedUser = updatedUserOrEmpty.get();
        assertThat(updatedUser.getId()).isEqualTo(student.getId());
        assertThat(updatedUser.getGroups()).hasSize(1).contains("tutor");
    }

    // Test
    public User createExternalUser_asAdmin_isSuccessful() throws Exception {
        String password = "foobar1234";
        student.setId(null);
        student.setLogin("batman");
        student.setPassword(password);
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/core/admin/users", new ManagedUserVM(student, password), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();
        final var userInDB = userTestRepository.findById(response.getId()).orElseThrow();
        assertThat(passwordService.checkPasswordMatch(password, userInDB.getPassword())).isTrue();
        student.setId(response.getId());

        // Exclude passwords from comparison
        response.setPassword(null);
        userInDB.setPassword(null);

        assertThat(student).as("New user is equal to request response").isEqualTo(response);
        assertThat(student).as("New user is equal to new user in DB").isEqualTo(userInDB);

        return userInDB;
    }

    // Test
    public void createExternalUser_asAdmin_withVcsToken_isSuccessful() throws Exception {
        var user = this.createExternalUser_asAdmin_isSuccessful();
        assertThat(user.getVcsAccessToken()).as("VCS Access token is set correctly").isEqualTo("acccess-token-value");
    }

    // Test
    public void createInternalUser_asAdmin_isSuccessful() throws Exception {
        createInternalUserIsSuccessful(Set.of(Role.STUDENT));
    }

    // Test
    public void createInternalUserWithoutRoles_asAdmin_isSuccessful() throws Exception {
        createInternalUserIsSuccessful(Collections.emptySet());
    }

    private void createInternalUserIsSuccessful(final Set<Role> roles) throws Exception {
        String password = "foobar1234";
        student.setId(null);
        student.setLogin("batman");
        student.setPassword(password);
        student.setEmail("batman@secret.invalid");
        student.setInternal(true);

        final Set<Authority> authorities = roles.stream().map(Role::getAuthority).map(auth -> authorityRepository.findById(auth).orElseThrow()).collect(Collectors.toSet());
        student.setAuthorities(authorities);

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/core/admin/users", new ManagedUserVM(student, student.getPassword()), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();
        final var userInDB = userTestRepository.findById(response.getId()).orElseThrow();
        userInDB.setPassword(password);
        student.setId(response.getId());
        response.setPassword(password);

        assertThat(student).as("New user is equal to request response").isEqualTo(response);
        assertThat(student).as("New user is equal to new user in DB").isEqualTo(userInDB);
    }

    // Test
    public void createUser_asAdmin_hasId() throws Exception {
        userTestRepository.findOneByLogin("batman").ifPresent(userTestRepository::delete);

        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");
        student = userTestRepository.save(student);

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/core/admin/users", new ManagedUserVM(student), User.class, HttpStatus.BAD_REQUEST);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_asAdmin_existingLogin() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/core/admin/users", new ManagedUserVM(student), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();

        User student2 = new User();
        student2.setId(null);
        student2.setLogin("batman");
        student2.setPassword("barfoo");
        student2.setEmail("batman2@secret.stillinvalid");

        final var response2 = request.postWithResponseBody("/api/core/admin/users", new ManagedUserVM(student2), User.class, HttpStatus.BAD_REQUEST);
        assertThat(response2).isNull();
    }

    // Test
    public void createUser_asAdmin_existingEmail() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/core/admin/users", new ManagedUserVM(student), User.class, HttpStatus.BAD_REQUEST);
        assertThat(response).isNull();
    }

    // Test
    public void createUserAsAdminExistsInCi() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockCreateUserInUserManagement(student, true);

        final var response = request.postWithResponseBody("/api/core/admin/users", new ManagedUserVM(student), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();
    }

    // Test
    public void createUser_asAdmin_illegalLogin_internalError() throws Exception {
        student.setId(null);
        student.setLogin("@someusername");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/core/admin/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_asAdmin_failInExternalCiUserManagement_internalError() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockFailToCreateUserInExternalUserManagement(student, false, true, false);

        final var response = request.postWithResponseBody("/api/core/admin/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_asAdmin_failInExternalCiUserManagement_cannotGetCiUser_internalError() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockFailToCreateUserInExternalUserManagement(student, false, false, true);

        final var response = request.postWithResponseBody("/api/core/admin/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_asAdmin_failInExternalVcsUserManagement_internalError() throws Exception {
        userTestRepository.findOneByLogin("batman").ifPresent(userTestRepository::delete);

        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockFailToCreateUserInExternalUserManagement(student, true, false, false);

        final var response = request.postWithResponseBody("/api/core/admin/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_withNullAsPassword_generatesRandomPassword() throws Exception {
        userTestRepository.findOneByLogin("batman").ifPresent(userTestRepository::delete);

        student.setId(null);
        student.setEmail("batman@invalid.tum");
        student.setLogin("batman");
        student.setPassword(null);

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/core/admin/users", new ManagedUserVM(student), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();
        final var userInDB = userTestRepository.findById(response.getId()).orElseThrow();

        assertThat(userInDB.getPassword()).isNotBlank();
    }

    // Test
    public void createUser_withExternalUserManagement() throws Exception {
        userTestRepository.findOneByLogin("batman").ifPresent(userTestRepository::delete);

        var newUser = student;
        newUser.setId(null);
        newUser.setLogin("batman");
        newUser.setEmail("foobar@tum.com");

        mockDelegate.mockCreateUserInUserManagement(newUser, false);

        request.post("/api/core/admin/users", new ManagedUserVM(newUser), HttpStatus.CREATED);

        var createdUser = userTestRepository.findOneByEmailIgnoreCase(newUser.getEmail());
        assertThat(createdUser).isPresent();
        assertThat(createdUser.get().getId()).isNotNull();
    }

    // Test
    public void createUserWithGroups() throws Exception {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> userTestRepository.findByIdWithGroupsAndAuthoritiesElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> userTestRepository.findByIdWithGroupsAndAuthoritiesAndOrganizationsElseThrow(Long.MAX_VALUE));

        var course = courseUtilService.addEmptyCourse();
        programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        course = courseUtilService.addEmptyCourse();
        course.setInstructorGroupName("instructor2");
        courseRepository.save(course);

        userTestRepository.findOneByLogin("batman").ifPresent(userTestRepository::delete);

        var newUser = student;
        newUser.setId(null);
        newUser.setLogin("batman");
        newUser.setEmail("foobar@tum.com");
        newUser.setGroups(Set.of("tutor", "instructor2"));

        mockDelegate.mockCreateUserInUserManagement(newUser, false);

        request.post("/api/core/admin/users", new ManagedUserVM(newUser), HttpStatus.CREATED);

        var createdUserOrEmpty = userTestRepository.findOneWithGroupsAndAuthoritiesByLogin(newUser.getLogin());
        assertThat(createdUserOrEmpty).isPresent();

        var createdUser = createdUserOrEmpty.get();
        assertThat(createdUser.getId()).isNotNull();
        assertThat(createdUser.getGroups()).hasSize(2).isEqualTo(newUser.getGroups());
    }

    // Test
    public void getUsers_asAdmin_isSuccessful() throws Exception {
        var usersDb = userTestRepository.findAllWithGroupsAndAuthoritiesByDeletedIsFalse().stream().peek(user -> user.setGroups(Collections.emptySet())).toList();
        userTestRepository.saveAll(usersDb);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("page", "0");
        params.add("pageSize", "100");
        params.add("searchTerm", TEST_PREFIX);
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "id");
        params.add("authorities", "");
        params.add("origins", "");
        params.add("registrationNumbers", "");
        params.add("status", "");
        params.add("courseIds", "");
        List<UserDTO> users = request.getList("/api/core/admin/users", HttpStatus.OK, UserDTO.class, params);
        assertThat(users).hasSize(NUMBER_OF_STUDENTS + NUMBER_OF_TUTORS + NUMBER_OF_EDITORS + NUMBER_OF_INSTRUCTORS); // admin is not returned
    }

    // Test
    public void searchUsers_asInstructor_isSuccessful() throws Exception {
        final String loginOrName = TEST_PREFIX + "student1";
        List<UserDTO> users = request.getList("/api/core/users/search?loginOrName=" + loginOrName, HttpStatus.OK, UserDTO.class);
        assertThat(users).hasSize(1); // size([student1]) = 1
    }

    // Test
    public void searchUsers_asAdmin_badRequest() throws Exception {
        final String loginOrName = "ab"; // too short (needs at least 3 characters)
        request.getList("/api/core/users/search?loginOrName=" + loginOrName, HttpStatus.BAD_REQUEST, UserDTO.class);
    }

    // Test
    public void searchUsers_asTutor_forbidden() throws Exception {
        final String loginOrName = "student";
        request.getList("/api/core/users/search?loginOrName=" + loginOrName, HttpStatus.FORBIDDEN, UserDTO.class);
    }

    // Test
    public void getUserViaFilter_asAdmin_isSuccessful() throws Exception {
        student.setGroups(Collections.emptySet());
        userTestRepository.save(student);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("page", "0");
        params.add("pageSize", "100");
        params.add("searchTerm", student.getEmail());
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "id");
        params.add("authorities", "USER");
        params.add("origins", "");
        params.add("status", "");
        params.add("registrationNumbers", "");
        params.add("courseIds", "");
        List<User> users = request.getList("/api/core/admin/users", HttpStatus.OK, User.class, params);
        assertThat(users).hasSize(1);
        assertThat(users.getFirst().getEmail()).isEqualTo(student.getEmail());
    }

    // Test
    public void getAuthorities_asAdmin_isSuccessful() throws Exception {
        List<String> authorities = request.getList("/api/core/admin/users/authorities", HttpStatus.OK, String.class);
        assertThat(authorities).hasSameElementsAs(List.of("ROLE_ADMIN", "ROLE_EDITOR", "ROLE_INSTRUCTOR", "ROLE_TA", "ROLE_USER"));
    }

    // Test
    public void getUsersOrAuthorities_asInstructor_forbidden() throws Exception {
        getUsersOrAuthorities_forbidden();
    }

    // Test
    public void getUsersOrAuthorities_asTutor_forbidden() throws Exception {
        getUsersOrAuthorities_forbidden();
    }

    // Test
    public void getUsersOrAuthorities_asStudent_forbidden() throws Exception {
        getUsersOrAuthorities_forbidden();
    }

    private void getUsersOrAuthorities_forbidden() throws Exception {
        request.getList("/api/core/admin/users", HttpStatus.FORBIDDEN, User.class);
        request.getList("/api/core/admin/users/authorities", HttpStatus.FORBIDDEN, String.class);
    }

    // Test
    public void getUser_asAdmin_isSuccessful() throws Exception {
        final String userLogin = TEST_PREFIX + "student1";
        UserDTO userDTO = request.get("/api/core/admin/users/" + userLogin, HttpStatus.OK, UserDTO.class);
        assertThat(userDTO.getLogin()).isEqualTo(userLogin);
    }

    // Test
    public void updateUserProfilePicture_asStudent_isSuccessful() throws Exception {
        User userInDB = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(userInDB.getImageUrl()).isNull();

        MockMultipartFile mockImageFile = new MockMultipartFile("file", "test-image.jpeg", "image/jpeg", "test image".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/core/account/profile-picture").file(mockImageFile).with(request -> {
            request.setMethod("PUT");
            return request;
        })).andExpect(status().isOk());

        userInDB = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(userInDB.getImageUrl()).isNotNull();
    }

    // Test
    public void updateAndDeleteUserProfilePicture_asStudent_isSuccessful() throws Exception {
        User userInDB = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(userInDB.getImageUrl()).isNull();

        MockMultipartFile mockImageFile = new MockMultipartFile("file", "test-image.jpeg", "image/jpeg", "test image".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/core/account/profile-picture").file(mockImageFile).with(request -> {
            request.setMethod("PUT");
            return request;
        })).andExpect(status().isOk());
        userInDB = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(userInDB.getImageUrl()).isNotNull();

        request.delete("/api/core/account/profile-picture", HttpStatus.OK);
        userInDB = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(userInDB.getImageUrl()).isNull();
    }

    // Test
    public void initializeUser(boolean mock) throws Exception {
        String password = passwordService.hashPassword("ThisIsAPassword");
        User repoUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        repoUser.setPassword(password);
        repoUser.setInternal(true);
        repoUser.setActivated(false);
        repoUser.setGroups(Set.of(LtiService.LTI_GROUP_NAME));
        final User user = userTestRepository.save(repoUser);

        if (mock) {
            // Mock user creation and update calls to prevent issues in Jenkins tests
            mockDelegate.mockCreateUserInUserManagement(user, false);
            mockDelegate.mockUpdateUserInUserManagement(user.getLogin(), user, null, new HashSet<>());
        }

        optionalCIUserManagementService.ifPresent(ciUserManagementService -> ciUserManagementService.createUser(user, password));

        UserInitializationDTO dto = request.putWithResponseBody("/api/core/users/initialize", false, UserInitializationDTO.class, HttpStatus.OK);

        assertThat(dto.password()).isNotEmpty();

        User currentUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        assertThat(passwordService.checkPasswordMatch(dto.password(), currentUser.getPassword())).isTrue();
        assertThat(passwordService.checkPasswordMatch(password, currentUser.getPassword())).isFalse();
        assertThat(currentUser.getActivated()).isTrue();
        assertThat(currentUser.isInternal()).isTrue();
    }

    // Test
    public void initializeUserWithoutFlag() throws Exception {
        String password = passwordService.hashPassword("ThisIsAPassword");
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setPassword(password);
        user.setInternal(true);
        user.setActivated(true);
        user.setGroups(Set.of(LtiService.LTI_GROUP_NAME));
        userTestRepository.save(user);

        UserInitializationDTO dto = request.putWithResponseBody("/api/core/users/initialize", false, UserInitializationDTO.class, HttpStatus.OK);

        assertThat(dto.password()).isNull();

        User currentUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        assertThat(currentUser.getPassword()).isEqualTo(password);
        assertThat(currentUser.getActivated()).isTrue();
        assertThat(currentUser.isInternal()).isTrue();
    }

    // Test
    public void initializeUserNonLTI() throws Exception {
        String password = passwordService.hashPassword("ThisIsAPassword");
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setPassword(password);
        user.setInternal(true);
        user.setActivated(false);
        userTestRepository.save(user);

        UserInitializationDTO dto = request.putWithResponseBody("/api/core/users/initialize", false, UserInitializationDTO.class, HttpStatus.OK);
        assertThat(dto.password()).isNull();

        User currentUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(currentUser.getPassword()).isEqualTo(password);
        assertThat(currentUser.getActivated()).isTrue();
        assertThat(currentUser.isInternal()).isTrue();
    }

    // Test
    public void initializeUserExternal() throws Exception {
        String password = passwordService.hashPassword("ThisIsAPassword");
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setPassword(password);
        user.setInternal(false);
        user.setActivated(false);
        userTestRepository.save(user);

        UserInitializationDTO dto = request.putWithResponseBody("/api/core/users/initialize", false, UserInitializationDTO.class, HttpStatus.OK);

        assertThat(dto.password()).isNull();

        User currentUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        assertThat(currentUser.getPassword()).isEqualTo(password);
        assertThat(currentUser.getActivated()).isTrue();
        assertThat(currentUser.isInternal()).isFalse();
    }

    // Test
    public void getAndCreateParticipationVcsAccessToken() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // try to get token for non existent participation
        request.get("/api/core/account/participation-vcs-access-token?participationId=11", HttpStatus.NOT_FOUND, String.class);

        var course = courseUtilService.addEmptyCourse();
        var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        courseRepository.save(course);

        var submission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").type(SubmissionType.MANUAL).submitted(true);
        submission = programmingExerciseUtilService.addProgrammingSubmission(exercise, submission, user.getLogin());
        // request existing token
        var token = request.get("/api/core/account/participation-vcs-access-token?participationId=" + submission.getParticipation().getId(), HttpStatus.OK, String.class);
        assertThat(token).isNotNull();

        // delete all tokens
        participationVCSAccessTokenRepository.deleteAll();

        // check that token was deleted
        request.get("/api/core/account/participation-vcs-access-token?participationId=" + submission.getParticipation().getId(), HttpStatus.NOT_FOUND, String.class);
        var newToken = request.putWithResponseBody("/api/core/account/participation-vcs-access-token?participationId=" + submission.getParticipation().getId(), null, String.class,
                HttpStatus.OK);
        assertThat(newToken).isNotEqualTo(token);

        submissionRepository.delete(submission);
        participationVCSAccessTokenRepository.deleteAll();
        participationRepository.deleteById(submission.getParticipation().getId());
    }

    // Test
    public void getAndCreateParticipationVcsAccessTokenForTeamExercise() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var course = courseUtilService.addEmptyCourse();
        var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        exercise.setMode(ExerciseMode.TEAM);
        exerciseTestRepository.save(exercise);
        courseRepository.save(course);
        User tutor1 = userTestRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
        Team team = teamUtilService.createTeam(Set.of(user), tutor1, exercise, "team1");

        var submission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").type(SubmissionType.MANUAL).submitted(true);
        submission = programmingExerciseUtilService.addProgrammingSubmissionToTeamExercise(exercise, submission, team);

        // request existing token
        request.get("/api/core/account/participation-vcs-access-token?participationId=" + submission.getParticipation().getId(), HttpStatus.NOT_FOUND, String.class);

        var token = request.putWithResponseBody("/api/core/account/participation-vcs-access-token?participationId=" + submission.getParticipation().getId(), null, String.class,
                HttpStatus.OK);
        assertThat(token).isNotNull();

        var token2 = request.get("/api/core/account/participation-vcs-access-token?participationId=" + submission.getParticipation().getId(), HttpStatus.OK, String.class);
        assertThat(token2).isEqualTo(token);

        submissionRepository.delete(submission);
        participationVCSAccessTokenRepository.deleteAll();
        participationRepository.deleteById(submission.getParticipation().getId());
        teamUtilService.deleteTeam(team);
    }

    // Test
    public void createAndDeleteUserVcsAccessToken() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(user.getVcsAccessToken()).isNull();

        // Set expiry date to already past date -> Bad Request
        ZonedDateTime expiryDate = ZonedDateTime.now().minusMonths(1);
        var userDTO = request.putWithResponseBody("/api/core/account/user-vcs-access-token?expiryDate=" + expiryDate, null, UserDTO.class, HttpStatus.BAD_REQUEST);
        assertThat(userDTO).isNull();

        // Correct expiry date -> OK
        expiryDate = ZonedDateTime.now().plusMonths(1);
        userDTO = request.putWithResponseBody("/api/core/account/user-vcs-access-token?expiryDate=" + expiryDate, null, UserDTO.class, HttpStatus.OK);
        user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(user.getVcsAccessToken()).isEqualTo(userDTO.getVcsAccessToken());
        assertThat(user.getVcsAccessTokenExpiryDate()).isEqualTo(userDTO.getVcsAccessTokenExpiryDate());

        // Delete token
        request.delete("/api/core/account/user-vcs-access-token", HttpStatus.OK);
        user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(user.getVcsAccessToken()).isNull();
        assertThat(user.getVcsAccessTokenExpiryDate()).isNull();
    }

    public UserRepository getUserTestRepository() {
        return userTestRepository;
    }

    /**
     * Helper method to create the params.
     *
     * @param authorities         authorities of the users
     * @param origins             of the users
     * @param registrationNumbers of the users
     * @param status              of the users
     * @return params for request
     */
    private LinkedMultiValueMap<String, String> createParamsForPagingRequest(String authorities, String origins, String registrationNumbers, String status,
            boolean findWithoutUserGroups) {
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("page", "0");
        params.add("pageSize", "1000");
        params.add("searchTerm", "");
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "id");
        params.add("authorities", authorities);
        params.add("origins", origins);
        params.add("registrationNumbers", registrationNumbers);
        params.add("status", status);
        params.add("findWithoutUserGroups", Boolean.toString(findWithoutUserGroups));
        return params;
    }

    /**
     * Helper method to determine the user authority which is used most often in a user creation matrix
     *
     * @param userNumbers the user creation matrix
     * @return String of the user authority with the most users
     */
    private String getMainUserAuthority(Integer[] userNumbers) throws Exception {
        List<Integer> userNumbersList = Arrays.asList(userNumbers);
        var authorityIndex = userNumbersList.indexOf(Collections.max(userNumbersList));
        return switch (authorityIndex) {
            case 0 -> "student";
            case 1 -> "tutor";
            case 2 -> "editor";
            case 3 -> "instructor";
            default -> throw new Exception("Couldn't match the input user array to an authority.");
        };
    }

    // Test
    public void testUser() throws Exception {
        final var params = createParamsForPagingRequest("USER", "", "", "", false);

        List<User> result;

        courseUtilService.addEmptyCourse();

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 }, };
        for (Integer[] number : numbers) {
            userTestRepository.deleteAll(userTestRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            user1.setGroups(Collections.emptySet());
            user2.setGroups(Set.of("tumuser"));
            userTestRepository.saveAll(List.of(user1, user2));
            result = request.getList("/api/core/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).contains(user1).contains(user2);
        }
    }

    // Test
    public void testUserWithoutGroups() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        courseRepository.save(course);

        final var params = createParamsForPagingRequest("USER", "", "", Long.toString(course.getId()), true);

        List<User> result;

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userTestRepository.deleteAll(userTestRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            user1.setGroups(Collections.emptySet());
            user2.setGroups(Set.of("tumuser"));
            userTestRepository.saveAll(List.of(user1, user2));
            result = request.getList("/api/core/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).contains(user1).doesNotContain(user2);
        }
    }

    // Test
    public void testUserWithActivatedStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "", "WITHOUT_REG_NO", "ACTIVATED", false);

        List<User> result;

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userTestRepository.deleteAll(userTestRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            User admin = userTestRepository.getUserByLoginElseThrow("admin");
            user1.setActivated(true);
            user2.setActivated(false);
            userTestRepository.saveAll(List.of(user1, user2, admin));
            result = request.getList("/api/core/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).contains(user1, admin).doesNotContain(user2);
        }
    }

    // Test
    public void testUserWithDeactivatedStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "", "WITHOUT_REG_NO", "DEACTIVATED", false);

        List<User> result;

        courseUtilService.addEmptyCourse();

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userTestRepository.deleteAll(userTestRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            user1.setActivated(true);
            user2.setActivated(false);
            userTestRepository.saveAll(List.of(user1, user2));
            result = request.getList("/api/core/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).contains(user2).doesNotContain(user1);
        }
    }

    // Test
    public void testUserWithInternalStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "INTERNAL", "WITHOUT_REG_NO", "", false);

        List<User> result;

        courseUtilService.addEmptyCourse();

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userTestRepository.deleteAll(userTestRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            User admin = userTestRepository.getUserByLoginElseThrow("admin");
            user1.setInternal(true);
            user2.setInternal(false);
            userTestRepository.saveAll(List.of(user1, user2, admin));
            result = request.getList("/api/core/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).contains(user1, admin).doesNotContain(user2);
        }
    }

    // Test
    public void testUserWithExternalStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "EXTERNAL", "WITHOUT_REG_NO", "", false);

        List<User> result;

        courseUtilService.addEmptyCourse();

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userTestRepository.deleteAll(userTestRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            user1.setInternal(true);
            user2.setInternal(false);
            userTestRepository.saveAll(List.of(user1, user2));
            result = request.getList("/api/core/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).contains(user2).doesNotContain(user1);
        }
    }

    public static UserSshPublicKey createNewValidSSHKey(User user, String keyString) {
        UserSshPublicKey userSshPublicKey = new UserSshPublicKey();
        userSshPublicKey.setPublicKey(keyString);
        userSshPublicKey.setLabel("Key 1");
        userSshPublicKey.setUserId(user.getId());
        return userSshPublicKey;
    }

    // Test
    public void testUserWithExternalAndInternalStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "INTERNAL,EXTERNAL", "WITHOUT_REG_NO", "", false);

        List<User> result;

        courseUtilService.addEmptyCourse();

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userTestRepository.deleteAll(userTestRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            user1.setInternal(true);
            user2.setInternal(false);
            userTestRepository.saveAll(List.of(user1, user2));
            result = request.getList("/api/core/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).isEqualTo(Collections.emptyList());
        }
    }

    /**
     * Test for the user search with the following parameters:
     *
     * @throws Exception if the user is not the same as the expected user
     */
    public void testUserWithRegistrationNumber() throws Exception {
        final var params = createParamsForPagingRequest("USER", "INTERNAL,EXTERNAL", "WITH_REG_NO", "", false);

        List<User> result;

        courseUtilService.addEmptyCourse();

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userTestRepository.deleteAll(userTestRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            user1.setRegistrationNumber(null);
            user2.setRegistrationNumber(null);
            userTestRepository.saveAll(List.of(user1, user2));
            result = request.getList("/api/core/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).isEqualTo(Collections.emptyList());
        }
    }

    /**
     * Test for the user search with the following parameters:
     *
     * @throws Exception if the user is not the same as the expected user
     */
    public void testUserWithoutRegistrationNumber() throws Exception {
        final var params = createParamsForPagingRequest("USER", "", "WITHOUT_REG_NO", "", false);

        List<User> result;

        courseUtilService.addEmptyCourse();

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userTestRepository.deleteAll(userTestRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            user1.setRegistrationNumber("5461351");
            user2.setRegistrationNumber("");
            User admin = userTestRepository.getUserByLoginElseThrow("admin");
            userTestRepository.saveAll(List.of(user1, user2, admin));
            result = request.getList("/api/core/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).contains(admin).doesNotContain(user1, user2);
        }
    }
}
