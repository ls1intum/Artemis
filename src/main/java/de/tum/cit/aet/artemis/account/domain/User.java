package de.tum.cit.aet.artemis.account.domain;

import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MIN_LENGTH;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Hibernate;
import org.hibernate.annotations.BatchSize;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.webauthn.api.Bytes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyProgress;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.domain.converter.BytesConverter;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.notification.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;

/**
 * A user.
 */
@Entity
@Table(name = "jhi_user")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class User extends AbstractAuditingEntity implements Participant {

    public static final String IRIS_BOT_LOGIN = "iris_bot";

    @NonNull
    @Pattern(regexp = Constants.LOGIN_REGEX)
    @Size(min = USERNAME_MIN_LENGTH, max = USERNAME_MAX_LENGTH)
    @Column(length = USERNAME_MAX_LENGTH, unique = true, nullable = false)
    private String login;

    @JsonIgnore
    @Column(name = "password_hash")
    private String password;

    @Size(max = 50)
    @Column(name = "first_name", length = 50)
    private String firstName;

    @Size(max = 50)
    @Column(name = "last_name", length = 50)
    private String lastName;

    @Size(max = 20)
    @Column(name = "registration_number", length = 20)
    @JsonIgnore
    private String registrationNumber;

    // this value is typically null, except the registration number should be explicitly shown in the client
    // currently this is only the case for the course scores page and its csv export, and also for the individual student exam detail
    @Transient
    private String visibleRegistrationNumberTransient = null;

    @Email
    @Size(max = 100)
    @Column(length = 100)
    private String email;

    /**
     * Whether this account may authenticate. Every authentication path enforces it: the internal, SAML2, OIDC and passkey
     * providers, and both git paths (HTTPS via {@code LocalVCServletService} and SSH via {@code GitPublickeyAuthenticatorService}).
     * <p>
     * <b>An account is only ever created unactivated when its own owner is expected to activate it</b>, which requires the
     * account to be <b>internal</b> ({@link #isInternal()}). That is what
     * {@link de.tum.cit.aet.artemis.account.service.user.UserCreationService#createUser} checks. An externally managed
     * account authenticates against the external identity provider, so Artemis has no activation step to offer it: the
     * {@link #activationKey} is redeemable only through {@code GET /activate}, which never sends an external account there.
     * Creating an external account unactivated therefore produces an account that <em>nothing</em> can ever activate. This
     * really happened: the student import created LDAP users unactivated, and they lost repository access as soon as git
     * authentication began enforcing this flag.
     * <p>
     * Being internal is necessary but not by itself sufficient for the key to be redeemable: {@code GET /activate} and the
     * mail carrying the key are both gated behind {@code artemis.user-management.registration.enabled}, so on an instance
     * with self-registration disabled even an internal account has no way to redeem one. Creation is deliberately
     * <em>not</em> narrowed to match, because the LTI launch also creates an internal account through the factory and reads
     * this flag as its own record of whether it still owes the account holder the generated password.
     * <p>
     * Only three kinds of writes set this to {@code false}, and only the first is the activation workflow:
     * <ol>
     * <li><b>awaiting activation</b> - {@code UserCreationService.createUser} for an internal account, and
     * {@code UserService.registerUser}, whose accounts are always internal. Paired with a non-null
     * {@link #activationKey}.</li>
     * <li><b>deliberate deactivation</b> - {@code UserCreationService.deactivateUser} and the admin edit form. Applies to
     * any account regardless of type, and never sets an activation key.</li>
     * <li><b>soft deletion</b> - {@code UserService.anonymizeUser}, alongside {@link #deleted}.</li>
     * </ol>
     * The presence of an {@link #activationKey} consequently distinguishes (1) from (2), which is what made it possible to
     * repair the affected rows without touching accounts an admin had deactivated on purpose.
     */
    @NonNull
    @Column(nullable = false)
    private boolean activated = false;

    @NonNull
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false; // default value

    /**
     * When the user last logged in. Set on every successful authentication and used as the activity signal for the
     * data-privacy not-enrolled-user cleanup (a real login signal, unlike the auditing {@code lastModifiedDate}, which is
     * bumped by any write to the user row such as group synchronization).
     */
    @Column(name = "last_login_date")
    private Instant lastLoginDate;

    /**
     * When the data-privacy cleanup warned the user that their (not-enrolled, inactive) account will be deleted after a
     * grace period. Stays {@code null} until the user has been warned; cleared if the user becomes active or enrolled
     * again. Anchors the grace period to the real warning so an account is never deleted without prior notice.
     */
    @Column(name = "deletion_warning_sent_date")
    private Instant deletionWarningSentDate;

    @Size(min = 2, max = 6)
    @Column(name = "lang_key", length = 6)
    private String langKey;

    @Size(max = 256)
    @Column(name = "image_url", length = 256)
    private String imageUrl;

    /**
     * One-time key a user redeems through {@code GET /activate} to activate their own account. Only ever set on an
     * <b>internal</b> account, and only together with {@code activated = false} - see {@link #activated} for why an
     * externally managed account must never be given one, and for how the key's presence tells an account awaiting
     * activation apart from one an admin deactivated.
     * <p>
     * Cleared by every write that activates the account, so the two fields stay consistent: {@code activateUser} for the
     * activation workflow and the administrative action, and {@code setRandomPasswordAndReturn} for the LTI launch.
     */
    @Size(max = 20)
    @Column(name = "activation_key", length = 20)
    @JsonIgnore
    private String activationKey;

    @Size(max = 20)
    @Column(name = "reset_key", length = 20)
    @JsonIgnore
    private String resetKey;

    @Column(name = "reset_date")
    private Instant resetDate = null;

    @Column(name = "is_internal", nullable = false)
    private boolean internal = true;          // default value

    // Marks accounts used only for testing/load-testing (e.g. QA or synthetic users). These are excluded from usage statistics.
    // The value is managed explicitly, not derived at runtime: it is backfilled by the migration for existing logins containing "test", and can afterwards be set or cleared in
    // Admin -> User Management (create/edit form) or via the user CSV import.
    @Column(name = "is_test_user", nullable = false)
    private boolean isTestUser = false;       // default value

    /**
     * The token the user can use to authenticate with the VCS.
     * This token is generated by Artemis when the user is created in the VCS.
     * It will e.g. be included in the repository clone URL.
     */
    @Nullable
    @JsonIgnore
    @Column(name = "vcs_access_token")
    private String vcsAccessToken = null;

    /**
     * The expiry date of the VCS access token.
     * This is used for checking if an access token needs to be renewed.
     */
    @Nullable
    @JsonIgnore
    @Column(name = "vcs_access_token_expiry_date")
    private ZonedDateTime vcsAccessTokenExpiryDate = null;

    /**
     * When the account's credentials last changed - a completed password reset, a password change, or a deactivation.
     * A session issued before this point is not extended any further, so those events end long-lived sessions within one
     * rotation interval instead of leaving them to run to their full lifetime.
     */
    @Column(name = "credentials_changed_date")
    private ZonedDateTime credentialsChangedDate = null;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JsonIgnore
    private Set<UserCourseRole> courseRoles = new HashSet<>();

    @Column(name = "lti_created", nullable = false)
    private boolean ltiCreated = false; // default value

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    private final Set<SavedPost> savedPosts = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "jhi_user_authority", joinColumns = { @JoinColumn(name = "user_id", referencedColumnName = "id") }, inverseJoinColumns = {
            @JoinColumn(name = "authority_name", referencedColumnName = "name") })
    @BatchSize(size = 20)
    private Set<Authority> authorities = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "user_organization", joinColumns = { @JoinColumn(name = "user_id", referencedColumnName = "id") }, inverseJoinColumns = {
            @JoinColumn(name = "organization_id", referencedColumnName = "id") })
    @JsonIgnoreProperties(value = "user", allowSetters = true)
    private Set<Organization> organizations = new HashSet<>();

    @OneToMany(mappedBy = "student", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnoreProperties(value = "student", allowSetters = true)
    public Set<TutorialGroupRegistration> tutorialGroupRegistrations = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore
    private Set<LectureUnitCompletion> completedLectureUnits = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore
    private Set<CompetencyProgress> competencyProgresses = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore
    private Set<LearningPath> learningPaths = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<ExamUser> examUsers = new HashSet<>();

    @OneToMany(mappedBy = "owner", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    @JsonIgnore
    private Set<PushNotificationDeviceConfiguration> pushNotificationDeviceConfigurations = new HashSet<>();

    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_selection_decision")
    private AiSelectionDecision aiSelectionDecision = null;

    @Nullable
    @Column(name = "ai_selection_decision_date")
    private ZonedDateTime aiSelectionDecisionDate = null;

    @NonNull
    @Column(name = "memiris_enabled", nullable = false)
    private boolean memirisEnabled = true;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties(value = "user", allowSetters = true)
    @JoinColumn(name = "learner_profile_id")
    private LearnerProfile learnerProfile;

    public User() {
    }

    public User(Long id) {
        this.setId(id);
    }

    public User(Long id, String firstName, String lastName) {
        this(id);
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public User(Long id, String login, String firstName, String lastName, String langKey, String email) {
        this(id);
        this.login = login;
        this.firstName = firstName;
        this.lastName = lastName;
        this.langKey = langKey;
        this.email = email;
    }

    public String getLogin() {
        return login;
    }

    // Lowercase the login before saving it in database
    public void setLogin(String login) {
        this.login = StringUtils.lowerCase(login, Locale.ENGLISH);
    }

    @Override
    public String getParticipantIdentifier() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * @return name as a concatenation of first name and last name
     */
    @Override
    public String getName() {
        if (lastName != null && !lastName.isEmpty()) {
            return firstName + " " + lastName;
        }
        else {
            return firstName;
        }
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean getActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public String getActivationKey() {
        return activationKey;
    }

    public void setActivationKey(String activationKey) {
        this.activationKey = activationKey;
    }

    public String getResetKey() {
        return resetKey;
    }

    public void setResetKey(String resetKey) {
        this.resetKey = resetKey;
    }

    public Instant getResetDate() {
        return resetDate;
    }

    public void setResetDate(Instant resetDate) {
        this.resetDate = resetDate;
    }

    public String getLangKey() {
        return langKey;
    }

    public void setLangKey(String langKey) {
        this.langKey = langKey;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getVisibleRegistrationNumber() {
        return visibleRegistrationNumberTransient;
    }

    public void setVisibleRegistrationNumber(String visibleRegistrationNumber) {
        this.visibleRegistrationNumberTransient = visibleRegistrationNumber;
    }

    public void setVisibleRegistrationNumber() {
        this.visibleRegistrationNumberTransient = this.getRegistrationNumber();
    }

    /**
     * Returns an unmodifiable view: {@link #getCourseRolesByCourseId()} caches an index over this collection and can
     * only invalidate it in {@link #setCourseRoles(Set)}, so mutating the returned set in place would leave the index
     * stale and yield wrong authorization decisions. Replace the whole set via {@link #setCourseRoles(Set)} instead.
     * <p>
     * Note for callers that need to know whether the collection was loaded: do NOT test the returned value with
     * {@code Hibernate.isInitialized(...)} — the wrapper is never a {@code PersistentSet}, so it always reports
     * initialised. Use {@code Persistence.getPersistenceUtil().isLoaded(user, "courseRoles")}, which inspects the
     * attribute itself.
     *
     * @return an unmodifiable view of this user's course roles
     */
    public Set<UserCourseRole> getCourseRoles() {
        return Collections.unmodifiableSet(courseRoles);
    }

    /**
     * Whether the lazy {@code courseRoles} collection has been loaded, so callers can decide between the in-memory
     * index from {@link #getCourseRolesByCourseId()} and a database query.
     * <p>
     * This has to live on the entity: {@link #getCourseRoles()} hands out an unmodifiable wrapper, which is never a
     * Hibernate {@code PersistentSet} and therefore always reports as initialised. Only code with access to the field
     * itself can answer the question.
     *
     * @return true if the course roles are loaded and can be read without hitting the database
     */
    @JsonIgnore
    public boolean isCourseRolesLoaded() {
        return Hibernate.isInitialized(courseRoles);
    }

    public void setCourseRoles(Set<UserCourseRole> courseRoles) {
        this.courseRoles = courseRoles;
        this.courseRolesByCourseIdTransient = null;
    }

    @Transient
    @JsonIgnore
    private transient Map<Long, EnumSet<CourseRole>> courseRolesByCourseIdTransient = null;

    /**
     * In-memory index of this user's course roles grouped by course id, built lazily from {@link #courseRoles} and
     * cached for the lifetime of this (request-scoped) entity instance. Enables O(1) membership lookups on hot paths
     * that check many courses (e.g. the course dashboard), instead of scanning the whole collection per check.
     *
     * @return a map from course id to the set of roles the user holds in that course (empty if no roles loaded)
     */
    @JsonIgnore
    public Map<Long, EnumSet<CourseRole>> getCourseRolesByCourseId() {
        if (courseRolesByCourseIdTransient == null) {
            Map<Long, EnumSet<CourseRole>> map = new HashMap<>();
            for (UserCourseRole courseRole : courseRoles) {
                map.computeIfAbsent(courseRole.getCourse().getId(), key -> EnumSet.noneOf(CourseRole.class)).add(courseRole.getRole());
            }
            courseRolesByCourseIdTransient = map;
        }
        return courseRolesByCourseIdTransient;
    }

    public boolean isLtiCreated() {
        return ltiCreated;
    }

    public void setLtiCreated(boolean ltiCreated) {
        this.ltiCreated = ltiCreated;
    }

    public Set<Authority> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(Set<Authority> authorities) {
        this.authorities = authorities;
    }

    public Set<Organization> getOrganizations() {
        return organizations;
    }

    public void setOrganizations(Set<Organization> organizations) {
        this.organizations = organizations;
    }

    public Set<LectureUnitCompletion> getCompletedLectureUnits() {
        return completedLectureUnits;
    }

    public void setCompletedLectureUnits(Set<LectureUnitCompletion> completedLectureUnits) {
        this.completedLectureUnits = completedLectureUnits;
    }

    public Set<CompetencyProgress> getCompetencyProgresses() {
        return competencyProgresses;
    }

    public void setCompetencyProgresses(Set<CompetencyProgress> competencyProgresses) {
        this.competencyProgresses = competencyProgresses;
    }

    public Set<LearningPath> getLearningPaths() {
        return learningPaths;
    }

    public void setLearningPaths(Set<LearningPath> learningPaths) {
        this.learningPaths = learningPaths;
    }

    public Set<ExamUser> getExamUsers() {
        return examUsers;
    }

    public void setExamUsers(Set<ExamUser> examUsers) {
        this.examUsers = examUsers;
    }

    @Override
    @JsonIgnore
    public Set<User> getParticipants() {
        return Set.of(this);
    }

    /**
     * @return an unmodifiable list of all granted authorities
     */
    @JsonIgnore
    public List<SimpleGrantedAuthority> getGrantedAuthorities() {
        return getAuthorities().stream().map(authority -> new SimpleGrantedAuthority(authority.getName())).toList();
    }

    @Override
    public String toString() {
        return "User{" + "login='" + login + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + ", email='" + email + '\'' + ", imageUrl='" + imageUrl
                + '\'' + ", activated='" + activated + '\'' + ", langKey='" + langKey + '\'' + ", activationKey='" + activationKey + '\'' + "}";
    }

    @JsonIgnore
    public String toDatabaseString() {
        return "Student: login='" + login + '\'' + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + ", registrationNumber='" + registrationNumber + '\'';
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public boolean isTestUser() {
        return isTestUser;
    }

    public void setTestUser(boolean isTestUser) {
        this.isTestUser = isTestUser;
    }

    @JsonProperty("bot")
    public boolean isBot() {
        return IRIS_BOT_LOGIN.equals(this.login);
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @JsonIgnore
    public Instant getLastLoginDate() {
        return lastLoginDate;
    }

    public void setLastLoginDate(Instant lastLoginDate) {
        this.lastLoginDate = lastLoginDate;
    }

    @JsonIgnore
    public Instant getDeletionWarningSentDate() {
        return deletionWarningSentDate;
    }

    public void setDeletionWarningSentDate(Instant deletionWarningSentDate) {
        this.deletionWarningSentDate = deletionWarningSentDate;
    }

    @Nullable
    public String getVcsAccessToken() {
        return vcsAccessToken;
    }

    @Nullable
    public ZonedDateTime getCredentialsChangedDate() {
        return credentialsChangedDate;
    }

    public void setCredentialsChangedDate(@Nullable ZonedDateTime credentialsChangedDate) {
        this.credentialsChangedDate = credentialsChangedDate;
    }

    public void setVcsAccessToken(@Nullable String vcsAccessToken) {
        this.vcsAccessToken = vcsAccessToken;
    }

    @Nullable
    public ZonedDateTime getVcsAccessTokenExpiryDate() {
        return vcsAccessTokenExpiryDate;
    }

    public void setVcsAccessTokenExpiryDate(@Nullable ZonedDateTime vcsAccessTokenExpiryDate) {
        this.vcsAccessTokenExpiryDate = vcsAccessTokenExpiryDate;
    }

    public Set<TutorialGroupRegistration> getTutorialGroupRegistrations() {
        return tutorialGroupRegistrations;
    }

    public void setTutorialGroupRegistrations(Set<TutorialGroupRegistration> tutorialGroupRegistrations) {
        this.tutorialGroupRegistrations = tutorialGroupRegistrations;
    }

    public Set<PushNotificationDeviceConfiguration> getPushNotificationDeviceConfigurations() {
        return pushNotificationDeviceConfigurations;
    }

    public void setPushNotificationDeviceConfigurations(Set<PushNotificationDeviceConfiguration> pushNotificationDeviceConfigurations) {
        this.pushNotificationDeviceConfigurations = pushNotificationDeviceConfigurations;
    }

    @Nullable
    public ZonedDateTime getSelectedLLMUsageTimestamp() {
        return aiSelectionDecisionDate;
    }

    public void setSelectedLLMUsageTimestamp(@Nullable ZonedDateTime aiSelectionDecisionDate) {
        this.aiSelectionDecisionDate = aiSelectionDecisionDate;
    }

    public boolean hasOptedIntoLLMUsage() {
        return aiSelectionDecision != null && aiSelectionDecision != AiSelectionDecision.NO_AI;
    }

    public AiSelectionDecision getSelectedLLMUsage() {
        return aiSelectionDecision;
    }

    public void setSelectedLLMUsage(@Nullable AiSelectionDecision aiSelectionDecision) {
        this.aiSelectionDecision = aiSelectionDecision;
    }

    /**
     * Checks if the user has selected to use AI.
     * If not, an {@link AccessForbiddenException} is thrown.
     */
    public void hasOptedIntoLLMUsageElseThrow() {
        if (!hasOptedIntoLLMUsage()) {
            throw new AccessForbiddenException("The user has not selected to use AI.");
        }
    }

    public LearnerProfile getLearnerProfile() {
        return learnerProfile;
    }

    public void setLearnerProfile(LearnerProfile learnerProfile) {
        this.learnerProfile = learnerProfile;
    }

    /**
     * In our case the external id matches our internal id, but it is expected in a different format
     *
     * @return the external id of the user, or null if the id is null
     */
    @JsonIgnore
    public Bytes getExternalId() {
        if (this.getId() == null) {
            return null;
        }
        return BytesConverter.longToBytes(this.getId());
    }

    public boolean isMemirisEnabled() {
        return memirisEnabled;
    }

    public void setMemirisEnabled(boolean memirisEnabled) {
        this.memirisEnabled = memirisEnabled;
    }
}
