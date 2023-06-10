package de.tum.in.www1.artemis.util;

import static com.google.gson.JsonParser.parseString;
import static de.tum.in.www1.artemis.util.ModelFactory.DEFAULT_BRANCH;
import static de.tum.in.www1.artemis.util.ModelFactory.USER_PASSWORD;
import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.*;
import static org.assertj.core.api.Assertions.*;

import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.ResourceUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.opencsv.CSVReader;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.enumeration.tutorialgroups.TutorialGroupRegistrationType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.domain.lecture.*;
import de.tum.in.www1.artemis.domain.metis.*;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.domain.tutorialgroups.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.metis.ReactionRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.OneToOneChatRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;

/**
 * Service responsible for initializing the database with specific testdata for a testscenario
 */
@Service
public class DatabaseUtilService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    private static final Authority userAuthority = new Authority(Role.STUDENT.getAuthority());

    private static final Authority tutorAuthority = new Authority(Role.TEACHING_ASSISTANT.getAuthority());

    private static final Authority editorAuthority = new Authority(Role.EDITOR.getAuthority());

    private static final Authority instructorAuthority = new Authority(Role.INSTRUCTOR.getAuthority());

    private static final Authority adminAuthority = new Authority(Role.ADMIN.getAuthority());

    private static final Set<Authority> studentAuthorities = Set.of(userAuthority);

    private static final Set<Authority> tutorAuthorities = Set.of(userAuthority, tutorAuthority);

    private static final Set<Authority> editorAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority);

    private static final Set<Authority> instructorAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority, instructorAuthority);

    private static final Set<Authority> adminAuthorities = Set.of(userAuthority, tutorAuthority, editorAuthority, instructorAuthority, adminAuthority);

    private static int dayCount = 1;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private LectureRepository lectureRepo;

    @Autowired
    private CompetencyRepository competencyRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private AttachmentRepository attachmentRepo;

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ExerciseHintRepository exerciseHintRepository;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private TeamRepository teamRepo;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepo;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PlagiarismResultRepository plagiarismResultRepo;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepo;

    @Autowired
    private TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepo;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepo;

    @Autowired
    private ModelingSubmissionRepository modelingSubmissionRepo;

    @Autowired
    private TextSubmissionRepository textSubmissionRepo;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private TextBlockRepository textBlockRepo;

    @Autowired
    private FileUploadSubmissionRepository fileUploadSubmissionRepo;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepo;

    @Autowired
    private FeedbackRepository feedbackRepo;

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    private ComplaintResponseRepository complaintResponseRepo;

    @Autowired
    private ExampleSubmissionRepository exampleSubmissionRepo;

    @Autowired
    private TutorParticipationRepository tutorParticipationRepo;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationParticipantRepository conversationParticipantRepository;

    @Autowired
    private ModelingSubmissionService modelSubmissionService;

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseTestRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private AttachmentUnitRepository attachmentUnitRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private ExerciseUnitRepository exerciseUnitRepository;

    @Autowired
    private TextUnitRepository textUnitRepository;

    @Autowired
    private VideoUnitRepository videoUnitRepository;

    @Autowired
    private OnlineUnitRepository onlineUnitRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    private SubmissionPolicyRepository submissionPolicyRepository;

    @Autowired
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    @Autowired
    private ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    @Autowired
    private CodeHintRepository codeHintRepository;

    @Autowired
    private RatingRepository ratingRepo;

    @Autowired
    private BuildPlanRepository buildPlanRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private TutorialGroupRepository tutorialGroupRepository;

    @Autowired
    private SlideRepository slideRepository;

    @Autowired
    private TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    @Autowired
    private TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    @Autowired
    private TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    @Autowired
    private TutorialGroupSessionRepository tutorialGroupSessionRepository;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private OneToOneChatRepository oneToOneChatRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @Value("${info.guided-tour.course-group-students:#{null}}")
    private Optional<String> tutorialGroupStudents;

    @Value("${info.guided-tour.course-group-tutors:#{null}}")
    private Optional<String> tutorialGroupTutors;

    @Value("${info.guided-tour.course-group-editors:#{null}}")
    private Optional<String> tutorialGroupEditors;

    @Value("${info.guided-tour.course-group-instructors:#{null}}")
    private Optional<String> tutorialGroupInstructors;

    @Autowired
    private QuizSubmissionRepository quizSubmissionRepository;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    // TODO: this should probably be moved into another service
    public void changeUser(String username) {
        User user = getUserByLogin(username);
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        for (Authority authority : user.getAuthorities()) {
            grantedAuthorities.add(new SimpleGrantedAuthority(authority.getName()));
        }
        org.springframework.security.core.userdetails.User securityContextUser = new org.springframework.security.core.userdetails.User(user.getLogin(), user.getPassword(),
                grantedAuthorities);
        Authentication authentication = new UsernamePasswordAuthenticationToken(securityContextUser, securityContextUser.getPassword(), grantedAuthorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        TestSecurityContextHolder.setContext(context);
    }

    /**
     * Generate users that have registration numbers
     *
     * @param loginPrefix              prefix that will be added in front of every user's login
     * @param groups                   groups that the users will be added
     * @param authorities              authorities that the users will have
     * @param amount                   amount of users to generate
     * @param registrationNumberPrefix prefix that will be added in front of every user
     * @return users that were generated
     */
    public List<User> generateActivatedUsersWithRegistrationNumber(String loginPrefix, String[] groups, Set<Authority> authorities, int amount, String registrationNumberPrefix) {
        List<User> generatedUsers = generateAndSaveActivatedUsers(loginPrefix, groups, authorities, amount);
        for (int i = 0; i < generatedUsers.size(); i++) {
            generatedUsers.get(i).setRegistrationNumber(registrationNumberPrefix + "R" + i);
        }
        return generatedUsers;
    }

    public List<User> generateAndSaveActivatedUsers(String loginPrefix, String[] groups, Set<Authority> authorities, int amount) {
        return generateAndSaveActivatedUsers(loginPrefix, USER_PASSWORD, groups, authorities, amount);
    }

    public List<User> generateAndSaveActivatedUsers(String loginPrefix, String commonPasswordHash, String[] groups, Set<Authority> authorities, int amount) {
        List<User> generatedUsers = new ArrayList<>();
        for (int i = 1; i <= amount; i++) {
            var login = loginPrefix + i;
            // the following line either creates the user or resets and existing user to its original state
            User user = createOrReuseExistingUser(login, commonPasswordHash);
            if (groups != null) {
                user.setGroups(Set.of(groups));
                user.setAuthorities(authorities);
            }
            user = userRepo.save(user);
            generatedUsers.add(user);
        }
        return generatedUsers;
    }

    public List<User> generateActivatedUsers(String loginPrefix, String commonPasswordHash, String[] groups, Set<Authority> authorities, int amount) {
        return generateActivatedUsers(loginPrefix, commonPasswordHash, groups, authorities, 1, amount);
    }

    public List<User> generateActivatedUsers(String loginPrefix, String commonPasswordHash, String[] groups, Set<Authority> authorities, int from, int to) {
        List<User> generatedUsers = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            var login = loginPrefix + i;
            // the following line either creates the user or resets and existing user to its original state
            User user = createOrReuseExistingUser(login, commonPasswordHash);
            if (groups != null) {
                user.setGroups(Set.of(groups));
                user.setAuthorities(authorities);
            }
            generatedUsers.add(user);
        }
        return generatedUsers;
    }

    /**
     * Generate a team
     *
     * @param exercise           exercise of the team
     * @param name               name of the team
     * @param shortName          short name of the team
     * @param loginPrefix        prefix that will be added in front of every user's login
     * @param numberOfStudents   amount of users to generate for team as students
     * @param owner              owner of the team generally a tutor
     * @param creatorLogin       login of user that creates the teams
     * @param registrationPrefix prefix that will be added in front of every student's registration number
     * @return team that was generated
     */
    public Team generateTeamForExercise(Exercise exercise, String name, String shortName, String loginPrefix, int numberOfStudents, User owner, String creatorLogin,
            String registrationPrefix) {
        List<User> students = generateActivatedUsersWithRegistrationNumber(shortName + loginPrefix, new String[] { "tumuser", "testgroup" },
                Set.of(new Authority(Role.STUDENT.getAuthority())), numberOfStudents, registrationPrefix);

        Team team = new Team();
        team.setName(name);
        team.setShortName(shortName);
        team.setExercise(exercise);
        team.setStudents(new HashSet<>(students));
        if (owner != null) {
            team.setOwner(owner);
        }
        if (creatorLogin != null) {
            team.setCreatedBy(creatorLogin);
            team.setLastModifiedBy(creatorLogin);
        }
        return team;
    }

    /**
     * Generate a team
     *
     * @param exercise         exercise of the team
     * @param name             name of the team
     * @param shortName        short name of the team
     * @param numberOfStudents amount of users to generate for team as students
     * @param owner            owner of the team generally a tutor
     * @return team that was generated
     */
    public Team generateTeamForExercise(Exercise exercise, String name, String shortName, int numberOfStudents, User owner) {
        return generateTeamForExercise(exercise, name, shortName, "student", numberOfStudents, owner, null, "R");
    }

    /**
     * Generate teams
     *
     * @param exercise        exercise of the teams
     * @param shortNamePrefix prefix that will be added in front of every team's short name
     * @param loginPrefix     prefix that will be added in front of every student's login
     * @param numberOfTeams   amount of teams to generate
     * @param owner           owner of the teams generally a tutor
     * @param creatorLogin    login of user that created the teams
     * @return teams that were generated
     */
    public List<Team> generateTeamsForExercise(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner, String creatorLogin) {
        return generateTeamsForExercise(exercise, shortNamePrefix, loginPrefix, numberOfTeams, owner, creatorLogin, "R");
    }

    /**
     * Generate teams
     *
     * @param exercise           exercise of the teams
     * @param shortNamePrefix    prefix that will be added in front of every team's short name
     * @param loginPrefix        prefix that will be added in front of every student's login
     * @param numberOfTeams      amount of teams to generate
     * @param owner              owner of the teams generally a tutor
     * @param creatorLogin       login of user that created the teams
     * @param registrationPrefix prefix that will be added in front of every student's registration number
     * @return teams that were generated
     */
    public List<Team> generateTeamsForExercise(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner, String creatorLogin,
            String registrationPrefix) {
        List<Team> teams = new ArrayList<>();
        for (int i = 1; i <= numberOfTeams; i++) {
            int numberOfStudents = new Random().nextInt(4) + 1; // range: 1-4 students
            teams.add(generateTeamForExercise(exercise, "Team " + i, shortNamePrefix + i, loginPrefix, numberOfStudents, owner, creatorLogin, registrationPrefix + i));
        }
        return teams;
    }

    /**
     * Generate teams
     *
     * @param exercise           exercise of the teams
     * @param shortNamePrefix    prefix that will be added in front of every team's short name
     * @param loginPrefix        prefix that will be added in front of every student's login
     * @param numberOfTeams      amount of teams to generate
     * @param owner              owner of the teams generally a tutor
     * @param creatorLogin       login of user that created the teams
     * @param registrationPrefix prefix that will be added in front of every student's registration number
     * @param teamSize           size of each individual team
     * @return teams that were generated
     */
    public List<Team> generateTeamsForExerciseFixedTeamSize(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner, String creatorLogin,
            String registrationPrefix, int teamSize) {
        List<Team> teams = new ArrayList<>();
        for (int i = 1; i <= numberOfTeams; i++) {
            teams.add(generateTeamForExercise(exercise, "Team " + i, shortNamePrefix + i, loginPrefix, teamSize, owner, creatorLogin, registrationPrefix + i));
        }
        return teams;
    }

    public User createAndSaveUser(String login, String hashedPassword) {
        User user = ModelFactory.generateActivatedUser(login, hashedPassword);
        if (userExistsWithLogin(login)) {
            // save the user with the newly created values (to override previous changes) with the same ID
            user.setId(getUserByLogin(login).getId());
        }
        return userRepo.save(user);
    }

    public User createOrReuseExistingUser(String login, String hashedPassword) {
        User user = ModelFactory.generateActivatedUser(login, hashedPassword);
        if (userExistsWithLogin(login)) {
            // save the user with the newly created values (to override previous changes) with the same ID
            user.setId(getUserByLogin(login).getId());
        }
        return user;
    }

    public User createAndSaveUser(String login) {
        User user = ModelFactory.generateActivatedUser(login);
        if (userExistsWithLogin(login)) {
            // save the user with the newly created values (to override previous changes) with the same ID
            user.setId(getUserByLogin(login).getId());
        }
        return userRepo.save(user);
    }

    public List<User> addUsers(int numberOfStudents, int numberOfTutors, int numberOfEditors, int numberOfInstructors) {
        return addUsers("", numberOfStudents, numberOfTutors, numberOfEditors, numberOfInstructors);
    }

    /**
     * Adds the provided number of students and tutors into the user repository. Students login is a concatenation of the prefix "student" and a number counting from 1 to
     * numberOfStudents Tutors login is a concatenation of the prefix "tutor" and a number counting from 1 to numberOfStudents Tutors are all in the "tutor" group and students in
     * the "tumuser" group
     *
     * @param prefix              the prefix for the user login
     * @param numberOfStudents    the number of students that will be added to the database
     * @param numberOfTutors      the number of tutors that will be added to the database
     * @param numberOfEditors     the number of editors that will be added to the database
     * @param numberOfInstructors the number of instructors that will be added to the database
     */
    public List<User> addUsers(String prefix, int numberOfStudents, int numberOfTutors, int numberOfEditors, int numberOfInstructors) {
        if (authorityRepository.count() == 0) {
            authorityRepository.saveAll(adminAuthorities);
        }
        log.debug("Generate {} students...", numberOfStudents);
        var students = generateActivatedUsers(prefix + "student", passwordService.hashPassword(USER_PASSWORD), new String[] { "tumuser", "testgroup", prefix + "tumuser" },
                studentAuthorities, numberOfStudents);
        log.debug("{} students generated. Generate {} tutors...", numberOfStudents, numberOfTutors);
        var tutors = generateActivatedUsers(prefix + "tutor", passwordService.hashPassword(USER_PASSWORD), new String[] { "tutor", "testgroup", prefix + "tutor" },
                tutorAuthorities, numberOfTutors);
        log.debug("{} tutors generated. Generate {} editors...", numberOfTutors, numberOfEditors);
        var editors = generateActivatedUsers(prefix + "editor", passwordService.hashPassword(USER_PASSWORD), new String[] { "editor", "testgroup", prefix + "editor" },
                editorAuthorities, numberOfEditors);
        log.debug("{} editors generated. Generate {} instructors...", numberOfEditors, numberOfInstructors);
        var instructors = generateActivatedUsers(prefix + "instructor", passwordService.hashPassword(USER_PASSWORD),
                new String[] { "instructor", "testgroup", prefix + "instructor" }, instructorAuthorities, numberOfInstructors);
        log.debug("{} instructors generated", numberOfInstructors);

        List<User> usersToAdd = new ArrayList<>();
        usersToAdd.addAll(students);
        usersToAdd.addAll(tutors);
        usersToAdd.addAll(editors);
        usersToAdd.addAll(instructors);

        if (!userExistsWithLogin("admin")) {
            log.debug("Generate admin");
            User admin = ModelFactory.generateActivatedUser("admin", passwordService.hashPassword(USER_PASSWORD));
            admin.setGroups(Set.of("admin"));
            admin.setAuthorities(adminAuthorities);
            usersToAdd.add(admin);
            log.debug("Generate admin done");
        }

        if (usersToAdd.size() > 0) {
            log.debug("Save {} users to database...", usersToAdd.size());
            usersToAdd = userRepo.saveAll(usersToAdd);
            log.debug("Save {} users to database. Done", usersToAdd.size());
        }

        return usersToAdd;
    }

    /**
     * generates and adds students to the repo, starting with student with the index to
     *
     * @param prefix the test prefix
     * @param from   first student to be added (inclusive)
     * @param to     last student to be added (inclusive)
     */
    public void addStudents(String prefix, int from, int to) {
        var students = generateActivatedUsers(prefix + "student", passwordService.hashPassword(USER_PASSWORD), new String[] { "tumuser", "testgroup", prefix + "tumuser" },
                studentAuthorities, from, to);
        userRepo.saveAll(students);
    }

    public List<Team> addTeamsForExercise(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner) {
        List<Team> teams = generateTeamsForExercise(exercise, shortNamePrefix, loginPrefix, numberOfTeams, owner, null);
        var users = teams.stream().map(Team::getStudents).flatMap(Collection::stream).toList();
        users.forEach(this::cleanUpRegistrationNumberForUser);
        userRepo.saveAll(users);
        return teamRepo.saveAll(teams);
    }

    public List<Team> addTeamsForExercise(Exercise exercise, String shortNamePrefix, int numberOfTeams, User owner) {
        return addTeamsForExercise(exercise, shortNamePrefix, "student", numberOfTeams, owner);
    }

    public List<Team> addTeamsForExercise(Exercise exercise, int numberOfTeams, User owner) {
        return addTeamsForExercise(exercise, "team", numberOfTeams, owner);
    }

    public List<Team> addTeamsForExerciseFixedTeamSize(String userPrefix, String regNumberPrefix, Exercise exercise, int numberOfTeams, User owner, int noOfStudentsPerTeam) {
        List<Team> teams = generateTeamsForExerciseFixedTeamSize(exercise, userPrefix + "team", "student", numberOfTeams, owner, null, regNumberPrefix, noOfStudentsPerTeam);
        var users = teams.stream().map(Team::getStudents).flatMap(Collection::stream).toList();
        users.forEach(this::cleanUpRegistrationNumberForUser);
        userRepo.saveAll(users);
        return teamRepo.saveAll(teams);
    }

    public void cleanUpRegistrationNumberForUser(User user) {
        if (user.getRegistrationNumber() == null) {
            return;
        }

        var existingUserWithRegistrationNumber = userRepo.findOneWithGroupsAndAuthoritiesByRegistrationNumber(user.getRegistrationNumber());
        if (existingUserWithRegistrationNumber.isPresent()) {
            existingUserWithRegistrationNumber.get().setRegistrationNumber(null);
            userRepo.save(existingUserWithRegistrationNumber.get());
        }
    }

    public Team addTeamForExercise(Exercise exercise, User owner) {
        return addTeamsForExercise(exercise, 1, owner).get(0);
    }

    public Team addTeamForExercise(Exercise exercise, User owner, String loginPrefix) {
        return addTeamsForExercise(exercise, "team", loginPrefix, 1, owner).get(0);
    }

    public Result addProgrammingParticipationWithResultForExercise(ProgrammingExercise exercise, String login) {
        var storedParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
        final StudentParticipation studentParticipation;
        if (storedParticipation.isEmpty()) {
            final var user = getUserByLogin(login);
            final var participation = new ProgrammingExerciseStudentParticipation();
            final var buildPlanId = exercise.getProjectKey().toUpperCase() + "-" + login.toUpperCase();
            final var repoName = (exercise.getProjectKey() + "-" + login).toLowerCase();
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setParticipant(user);
            participation.setBuildPlanId(buildPlanId);
            participation.setProgrammingExercise(exercise);
            participation.setInitializationState(InitializationState.INITIALIZED);
            participation.setRepositoryUrl(String.format("http://some.test.url/%s/%s.git", exercise.getProjectKey(), repoName));
            programmingExerciseStudentParticipationRepo.save(participation);
            storedParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
            assertThat(storedParticipation).isPresent();
            studentParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).get();
        }
        else {
            studentParticipation = storedParticipation.get();
        }
        return addResultToParticipation(null, null, studentParticipation);
    }

    public void addInstructor(final String instructorGroup, final String instructorName) {
        if (!userExistsWithLogin(instructorName)) {
            var newUsers = generateAndSaveActivatedUsers(instructorName, new String[] { instructorGroup, "testgroup" }, instructorAuthorities, 1);
            if (!newUsers.isEmpty()) {
                var instructor = userRepo.save(newUsers.get(0));
                assertThat(instructor.getId()).as("Instructor has been created").isNotNull();
            }
        }
    }

    public void addEditor(final String editorGroup, final String editorName) {
        if (!userExistsWithLogin(editorName)) {
            var newUsers = generateAndSaveActivatedUsers(editorName, new String[] { editorGroup, "testgroup" }, editorAuthorities, 1);
            if (!newUsers.isEmpty()) {
                var editor = userRepo.save(newUsers.get(0));
                assertThat(editor.getId()).as("Editor has been created").isNotNull();
            }
        }
    }

    public void addTeachingAssistant(final String taGroup, final String taName) {
        if (!userExistsWithLogin(taName)) {
            var newUsers = generateAndSaveActivatedUsers(taName, new String[] { taGroup, "testgroup" }, tutorAuthorities, 1);
            if (!newUsers.isEmpty()) {
                var ta = userRepo.save(newUsers.get(0));
                assertThat(ta.getId()).as("Teaching assistant has been created").isNotNull();
            }
        }
    }

    public void addStudent(final String studentGroup, final String studentName) {
        if (!userExistsWithLogin(studentName)) {
            var newUsers = generateAndSaveActivatedUsers(studentName, new String[] { studentGroup, "testgroup" }, studentAuthorities, 1);
            if (!newUsers.isEmpty()) {
                var student = userRepo.save(newUsers.get(0));
                assertThat(student.getId()).as("Student has been created").isNotNull();
            }
        }
    }

    public Lecture createCourseWithLecture(boolean saveLecture) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");

        Lecture lecture = new Lecture();
        lecture.setDescription("Test Lecture");
        lecture.setCourse(course);
        courseRepo.save(course);
        if (saveLecture) {
            lectureRepo.save(lecture);
        }
        return lecture;
    }

    public Course createCourse() {
        return createCourse(null);
    }

    public Course createCourse(Long id) {
        Course course = ModelFactory.generateCourse(id, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        return courseRepo.save(course);
    }

    public Course createCourseWithPostsDisabled() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.DISABLED);
        return courseRepo.save(course);
    }

    public Course createCourseWithOrganizations(String name, String shortName, String url, String description, String logoUrl, String emailPattern) {
        Course course = createCourse();
        Set<Organization> organizations = new HashSet<>();
        Organization organization = createOrganization(name, shortName, url, description, logoUrl, emailPattern);
        organizations.add(organization);
        course.setOrganizations(organizations);
        return courseRepo.save(course);
    }

    public Course createCourseWithOrganizations() {
        return createCourseWithOrganizations("organization1", "org1", "org.org", "This is organization1", null, "^.*@matching.*$");
    }

    public Competency createCompetency(Course course) {
        Competency competency = new Competency();
        competency.setTitle("Example Competency");
        competency.setDescription("Magna pars studiorum, prodita quaerimus.");
        competency.setCourse(course);
        return competencyRepo.save(competency);
    }

    public TextExercise createIndividualTextExercise(Course course, ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        return exerciseRepo.save(textExercise);
    }

    public Team createTeam(Set<User> students, User owner, Exercise exercise, String teamName) {
        Team team = new Team();
        for (User student : students) {
            team.addStudents(student);
        }
        team.setOwner(owner);
        team.setShortName(teamName);
        team.setName(teamName);
        team.setExercise(exercise);
        return teamRepo.saveAndFlush(team);
    }

    public TextExercise createTeamTextExercise(Course course, ZonedDateTime pastTimestamp, ZonedDateTime futureTimestamp, ZonedDateTime futureFutureTimestamp) {
        TextExercise teamTextExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        teamTextExercise.setMaxPoints(10.0);
        teamTextExercise.setBonusPoints(0.0);
        teamTextExercise.setMode(ExerciseMode.TEAM);
        return exerciseRepo.save(teamTextExercise);
    }

    public Result createParticipationSubmissionAndResult(long exerciseId, Participant participant, Double points, Double bonusPoints, long scoreAwarded, boolean rated) {
        Exercise exercise = exerciseRepo.findById(exerciseId).get();
        if (!exercise.getMaxPoints().equals(points)) {
            exercise.setMaxPoints(points);
        }
        if (!exercise.getBonusPoints().equals(bonusPoints)) {
            exercise.setBonusPoints(bonusPoints);
        }
        exercise = exerciseRepo.saveAndFlush(exercise);
        StudentParticipation studentParticipation = participationService.startExercise(exercise, participant, false);
        return createSubmissionAndResult(studentParticipation, scoreAwarded, rated);
    }

    public Result createSubmissionAndResult(StudentParticipation studentParticipation, long scoreAwarded, boolean rated) {
        Exercise exercise = studentParticipation.getExercise();
        Submission submission;
        if (exercise instanceof ProgrammingExercise) {
            submission = new ProgrammingSubmission();
        }
        else if (exercise instanceof ModelingExercise) {
            submission = new ModelingSubmission();
        }
        else if (exercise instanceof TextExercise) {
            submission = new TextSubmission();
        }
        else if (exercise instanceof FileUploadExercise) {
            submission = new FileUploadSubmission();
        }
        else if (exercise instanceof QuizExercise) {
            submission = new QuizSubmission();
        }
        else {
            throw new RuntimeException("Unsupported exercise type: " + exercise);
        }

        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        submission = submissionRepository.saveAndFlush(submission);

        Result result = ModelFactory.generateResult(rated, scoreAwarded);
        result.setParticipation(studentParticipation);
        result.setSubmission(submission);
        result.completionDate(ZonedDateTime.now());
        submission.addResult(result);
        submission = submissionRepository.saveAndFlush(submission);
        return submission.getResults().get(0);
    }

    public Course createCourseWithExamAndExerciseGroupAndExercises(User user, ZonedDateTime visible, ZonedDateTime start, ZonedDateTime end) {
        Course course = createCourse();
        Exam exam = addExam(course, user, visible, start, end);
        course.addExam(exam);
        addExerciseGroupsAndExercisesToExam(exam, false);
        return courseRepo.save(course);
    }

    public Course createCourseWithExamAndExerciseGroupAndExercises(User user) {
        Course course = createCourse();
        Exam exam = addExam(course, user, ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(1));
        course.addExam(exam);
        addExerciseGroupsAndExercisesToExam(exam, false);
        return courseRepo.save(course);
    }

    public List<Course> createCoursesWithExercisesAndLecturesAndLectureUnits(String userPrefix, boolean withParticipations, boolean withFiles, int numberOfTutorParticipations)
            throws Exception {
        List<Course> courses = this.createCoursesWithExercisesAndLectures(userPrefix, withParticipations, withFiles, numberOfTutorParticipations);
        return courses.stream().peek(course -> {
            List<Lecture> lectures = new ArrayList<>(course.getLectures());
            for (int i = 0; i < lectures.size(); i++) {
                TextExercise textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).stream().findFirst().get();
                VideoUnit videoUnit = createVideoUnit();
                TextUnit textUnit = createTextUnit();
                AttachmentUnit attachmentUnit = createAttachmentUnit(withFiles);
                ExerciseUnit exerciseUnit = createExerciseUnit(textExercise);
                lectures.set(i, addLectureUnitsToLecture(lectures.get(i), List.of(videoUnit, textUnit, attachmentUnit, exerciseUnit)));
            }
            course.setLectures(new HashSet<>(lectures));
        }).toList();
    }

    public List<Course> createCoursesWithExercisesAndLecturesAndLectureUnitsAndCompetencies(String userPrefix, boolean withParticipations, boolean withFiles,
            int numberOfTutorParticipations) throws Exception {
        List<Course> courses = this.createCoursesWithExercisesAndLecturesAndLectureUnits(userPrefix, withParticipations, withFiles, numberOfTutorParticipations);
        return courses.stream().peek(course -> {
            List<Lecture> lectures = new ArrayList<>(course.getLectures());
            lectures.replaceAll(lecture -> addCompetencyToLectureUnits(lecture, Set.of(createCompetency(course))));
            course.setLectures(new HashSet<>(lectures));
        }).toList();
    }

    public Lecture addCompetencyToLectureUnits(Lecture lecture, Set<Competency> competencies) {
        Lecture l = lectureRepo.findByIdWithLectureUnitsAndCompetenciesElseThrow(lecture.getId());
        l.getLectureUnits().forEach(lectureUnit -> {
            lectureUnit.setCompetencies(competencies);
            lectureUnitRepository.save(lectureUnit);
        });
        return l;
    }

    public Lecture addLectureUnitsToLecture(Lecture lecture, List<LectureUnit> lectureUnits) {
        Lecture l = lectureRepo.findByIdWithLectureUnits(lecture.getId()).get();
        for (LectureUnit lectureUnit : lectureUnits) {
            l.addLectureUnit(lectureUnit);
        }
        return lectureRepo.save(l);
    }

    public ExerciseUnit createExerciseUnit(Exercise exercise) {
        ExerciseUnit exerciseUnit = new ExerciseUnit();
        exerciseUnit.setExercise(exercise);
        return exerciseUnitRepository.save(exerciseUnit);
    }

    public AttachmentUnit createAttachmentUnit(Boolean withFile) {
        ZonedDateTime started = ZonedDateTime.now().minusDays(5);
        Attachment attachmentOfAttachmentUnit = withFile ? ModelFactory.generateAttachmentWithFile(started) : ModelFactory.generateAttachment(started);
        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setDescription("Lorem Ipsum");
        attachmentUnit = attachmentUnitRepository.save(attachmentUnit);
        attachmentOfAttachmentUnit.setAttachmentUnit(attachmentUnit);
        attachmentOfAttachmentUnit = attachmentRepository.save(attachmentOfAttachmentUnit);
        attachmentUnit.setAttachment(attachmentOfAttachmentUnit);
        return attachmentUnitRepository.save(attachmentUnit);
    }

    public AttachmentUnit createAttachmentUnitWithSlides(int numberOfSlides) {
        ZonedDateTime started = ZonedDateTime.now().minusDays(5);
        Attachment attachmentOfAttachmentUnit = ModelFactory.generateAttachment(started);
        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setDescription("Lorem Ipsum");
        attachmentUnit = attachmentUnitRepository.save(attachmentUnit);
        attachmentOfAttachmentUnit.setAttachmentUnit(attachmentUnit);
        attachmentOfAttachmentUnit = attachmentRepository.save(attachmentOfAttachmentUnit);
        attachmentUnit.setAttachment(attachmentOfAttachmentUnit);
        for (int i = 1; i <= numberOfSlides; i++) {
            Slide slide = new Slide();
            slide.setSlideNumber(i);
            slide.setSlideImagePath("path/to/slide" + i + ".png");
            slide.setAttachmentUnit(attachmentUnit);
            slideRepository.save(slide);
        }
        return attachmentUnitRepository.save(attachmentUnit);
    }

    public TextUnit createTextUnit() {
        TextUnit textUnit = new TextUnit();
        textUnit.setContent("Lorem Ipsum");
        return textUnitRepository.save(textUnit);
    }

    public VideoUnit createVideoUnit() {
        VideoUnit videoUnit = new VideoUnit();
        videoUnit.setDescription("Lorem Ipsum");
        videoUnit.setSource("http://video.fake");
        return videoUnitRepository.save(videoUnit);
    }

    public OnlineUnit createOnlineUnit() {
        OnlineUnit onlineUnit = new OnlineUnit();
        onlineUnit.setDescription("Lorem Ipsum");
        onlineUnit.setSource("http://video.fake");
        return onlineUnitRepository.save(onlineUnit);
    }

    public List<Course> createCoursesWithExercisesAndLectures(String prefix, boolean withParticipations, int numberOfTutorParticipations) throws Exception {
        return createCoursesWithExercisesAndLectures(prefix, withParticipations, false, numberOfTutorParticipations);
    }

    public List<Course> createCoursesWithExercisesAndLectures(String prefix, boolean withParticipations, boolean withFiles, int numberOfTutorParticipations) throws Exception {
        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), prefix + "tumuser", prefix + "tutor", prefix + "editor",
                prefix + "instructor");
        Course course2 = ModelFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), pastTimestamp, new HashSet<>(), prefix + "tumuser", prefix + "tutor",
                prefix + "editor", prefix + "instructor");

        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course1);
        modelingExercise.setGradingInstructions("some grading instructions");
        modelingExercise.setExampleSolutionModel("Example solution model");
        modelingExercise.setExampleSolutionExplanation("Example Solution");
        addGradingInstructionsToExercise(modelingExercise);
        modelingExercise.getCategories().add("Modeling");
        course1.addExercises(modelingExercise);

        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course1);
        textExercise.setGradingInstructions("some grading instructions");
        textExercise.setExampleSolution("Example Solution");
        addGradingInstructionsToExercise(textExercise);
        textExercise.getCategories().add("Text");
        course1.addExercises(textExercise);

        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, "png", course1);
        fileUploadExercise.setGradingInstructions("some grading instructions");
        fileUploadExercise.setExampleSolution("Example Solution");
        addGradingInstructionsToExercise(fileUploadExercise);
        fileUploadExercise.getCategories().add("File");
        course1.addExercises(fileUploadExercise);

        ProgrammingExercise programmingExercise = ModelFactory.generateProgrammingExercise(pastTimestamp, futureTimestamp, course1);
        programmingExercise.setGradingInstructions("some grading instructions");
        addGradingInstructionsToExercise(programmingExercise);
        programmingExercise.getCategories().add("Programming");
        course1.addExercises(programmingExercise);

        QuizExercise quizExercise = ModelFactory.generateQuizExercise(pastTimestamp, futureTimestamp, QuizMode.SYNCHRONIZED, course1);
        programmingExercise.getCategories().add("Quiz");
        course1.addExercises(quizExercise);

        Lecture lecture1 = ModelFactory.generateLecture(pastTimestamp, futureFutureTimestamp, course1);
        Attachment attachment1 = withFiles ? ModelFactory.generateAttachmentWithFile(pastTimestamp) : ModelFactory.generateAttachment(pastTimestamp);
        attachment1.setLecture(lecture1);
        lecture1.addAttachments(attachment1);
        course1.addLectures(lecture1);

        Lecture lecture2 = ModelFactory.generateLecture(pastTimestamp, futureFutureTimestamp, course1);
        Attachment attachment2 = withFiles ? ModelFactory.generateAttachmentWithFile(pastTimestamp) : ModelFactory.generateAttachment(pastTimestamp);
        attachment2.setLecture(lecture2);
        lecture2.addAttachments(attachment2);
        course1.addLectures(lecture2);

        course1 = courseRepo.save(course1);
        course2 = courseRepo.save(course2);

        lectureRepo.save(lecture1);
        lectureRepo.save(lecture2);

        attachmentRepo.save(attachment1);
        attachmentRepo.save(attachment2);

        modelingExercise = exerciseRepo.save(modelingExercise);
        textExercise = exerciseRepo.save(textExercise);
        exerciseRepo.save(fileUploadExercise);
        exerciseRepo.save(programmingExercise);
        exerciseRepo.save(quizExercise);

        if (withParticipations) {

            // create 5 tutor participations and 5 example submissions and connect all of them (to test the many-to-many relationship)
            Set<TutorParticipation> tutorParticipations = new HashSet<>();
            for (int i = 1; i < numberOfTutorParticipations + 1; i++) {
                var tutorParticipation = new TutorParticipation().tutor(getUserByLogin(prefix + "tutor" + i)).status(TutorParticipationStatus.NOT_PARTICIPATED)
                        .assessedExercise(modelingExercise);
                tutorParticipationRepo.save(tutorParticipation);
                tutorParticipations.add(tutorParticipation);
            }

            for (int i = 1; i < numberOfTutorParticipations + 1; i++) {
                String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
                var exampleSubmission = addExampleSubmission(generateExampleSubmission(validModel, modelingExercise, true));
                exampleSubmission.assessmentExplanation("exp");
                exampleSubmission.setTutorParticipations(tutorParticipations);
                exampleSubmissionRepo.save(exampleSubmission);
            }

            User user = getUserByLogin(prefix + "student1");
            StudentParticipation participation1 = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, modelingExercise, user);
            StudentParticipation participation2 = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise, user);
            StudentParticipation participation3 = ModelFactory.generateStudentParticipation(InitializationState.UNINITIALIZED, modelingExercise, user);
            StudentParticipation participation4 = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.FINISHED, programmingExercise, user);
            StudentParticipation participation5 = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise, user);
            participation5.setTestRun(true);

            Submission modelingSubmission1 = ModelFactory.generateModelingSubmission("model1", true);
            Submission modelingSubmission2 = ModelFactory.generateModelingSubmission("model2", true);
            Submission textSubmission = ModelFactory.generateTextSubmission("text", Language.ENGLISH, true);
            Submission programmingSubmission1 = ModelFactory.generateProgrammingSubmission(true, "1234", SubmissionType.MANUAL);
            Submission programmingSubmission2 = ModelFactory.generateProgrammingSubmission(true, "5678", SubmissionType.MANUAL);

            Result result1 = ModelFactory.generateResult(true, 10D);
            Result result2 = ModelFactory.generateResult(true, 12D);
            Result result3 = ModelFactory.generateResult(false, 0D);
            Result result4 = ModelFactory.generateResult(true, 12D);
            Result result5 = ModelFactory.generateResult(false, 42D);

            participation1 = studentParticipationRepo.save(participation1);
            participation2 = studentParticipationRepo.save(participation2);
            participation3 = studentParticipationRepo.save(participation3);
            participation4 = studentParticipationRepo.save(participation4);
            participation5 = studentParticipationRepo.save(participation5);

            submissionRepository.save(modelingSubmission1);
            submissionRepository.save(modelingSubmission2);
            submissionRepository.save(textSubmission);
            submissionRepository.save(programmingSubmission1);
            submissionRepository.save(programmingSubmission2);

            modelingSubmission1.setParticipation(participation1);
            textSubmission.setParticipation(participation2);
            modelingSubmission2.setParticipation(participation3);
            programmingSubmission1.setParticipation(participation4);
            programmingSubmission2.setParticipation(participation5);

            result1.setParticipation(participation1);
            result2.setParticipation(participation3);
            result3.setParticipation(participation2);
            result4.setParticipation(participation4);
            result5.setParticipation(participation5);

            result1 = resultRepo.save(result1);
            result2 = resultRepo.save(result2);
            result3 = resultRepo.save(result3);
            result4 = resultRepo.save(result4);
            result5 = resultRepo.save(result5);

            result1.setSubmission(modelingSubmission1);
            result2.setSubmission(modelingSubmission2);
            result3.setSubmission(textSubmission);
            result4.setSubmission(programmingSubmission1);
            result5.setSubmission(programmingSubmission2);

            modelingSubmission1.addResult(result1);
            modelingSubmission2.addResult(result2);
            textSubmission.addResult(result3);
            programmingSubmission1.addResult(result4);
            programmingSubmission2.addResult(result5);

            submissionRepository.save(modelingSubmission1);
            submissionRepository.save(modelingSubmission2);
            submissionRepository.save(textSubmission);
            submissionRepository.save(programmingSubmission1);
            submissionRepository.save(programmingSubmission2);
        }

        return Arrays.asList(course1, course2);
    }

    public List<Post> createPostsWithinCourse(String userPrefix) {

        Course course1 = createCourse();
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course1);
        course1.addExercises(textExercise);
        textExercise = exerciseRepo.save(textExercise);

        Lecture lecture = ModelFactory.generateLecture(pastTimestamp, futureFutureTimestamp, course1);
        course1.addLectures(lecture);
        lecture = lectureRepo.save(lecture);

        courseRepo.save(course1);

        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(textExercise);
        plagiarismCase.setStudent(getUserByLogin(userPrefix + "student1"));
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);

        List<Post> posts = new ArrayList<>();

        // add posts to exercise
        posts.addAll(createBasicPosts(textExercise, userPrefix));

        // add posts to lecture
        posts.addAll(createBasicPosts(lecture, userPrefix));

        // add post to plagiarismCase
        posts.add(createBasicPost(plagiarismCase, userPrefix));

        // add posts to course with different course-wide contexts provided in input array
        CourseWideContext[] courseWideContexts = new CourseWideContext[] { CourseWideContext.ORGANIZATION, CourseWideContext.RANDOM, CourseWideContext.TECH_SUPPORT,
                CourseWideContext.ANNOUNCEMENT };
        posts.addAll(createBasicPosts(course1, courseWideContexts, userPrefix));
        posts.addAll(createBasicPosts(createOneToOneChat(course1, userPrefix), userPrefix));

        return posts;
    }

    public List<Post> createPostsWithAnswersAndReactionsAndConversation(Course course, User student1, User student2, int numberOfPosts, String userPrefix) {
        var chat = new OneToOneChat();
        chat.setCourse(course);
        chat.setCreator(student1);
        chat.setCreationDate(ZonedDateTime.now());
        chat.setLastMessageDate(ZonedDateTime.now());
        chat = oneToOneChatRepository.save(chat);
        var participant1 = new ConversationParticipant();
        participant1.setConversation(chat);
        participant1.setUser(student1);
        participant1.setUnreadMessagesCount(0L);
        participant1.setLastRead(ZonedDateTime.now().minusYears(2));
        conversationParticipantRepository.save(participant1);
        var participant2 = new ConversationParticipant();
        participant2.setConversation(chat);
        participant2.setUser(student2);
        participant2.setUnreadMessagesCount(0L);
        participant2.setLastRead(ZonedDateTime.now().minusYears(2));
        conversationParticipantRepository.save(participant2);
        chat = oneToOneChatRepository.findByIdWithConversationParticipantsAndUserGroups(chat.getId()).get();

        var posts = new ArrayList<Post>();
        for (int i = 0; i < numberOfPosts; i++) {
            var post = new Post();
            post.setAuthor(student1);
            post.setDisplayPriority(DisplayPriority.NONE);
            post.setConversation(chat);
            post = postRepository.save(post);
            posts.add(post);
        }

        // add many answers for all posts in conversation
        for (var post : posts) {
            post.setAnswers(createBasicAnswers(post, userPrefix));
            postRepository.save(post);
        }

        // add many reactions for all posts in conversation
        for (var post : posts) {
            Reaction reaction = new Reaction();
            reaction.setEmojiId("smiley");
            reaction.setPost(post);
            reaction.setUser(student1);
            reactionRepository.save(reaction);
            post.setReactions(Set.of(reaction));
            postRepository.save(post);
        }
        return posts;
    }

    public List<Post> createPostsWithAnswerPostsWithinCourse(String userPrefix) {
        List<Post> posts = createPostsWithinCourse(userPrefix);

        // add answer for one post in each context (lecture, exercise, course-wide, conversation)
        Post lecturePost = posts.stream().filter(coursePost -> coursePost.getLecture() != null).findFirst().orElseThrow();
        lecturePost.setAnswers(createBasicAnswers(lecturePost, userPrefix));
        lecturePost.getAnswers().addAll(createBasicAnswers(lecturePost, userPrefix));
        postRepository.save(lecturePost);

        Post exercisePost = posts.stream().filter(coursePost -> coursePost.getExercise() != null).findFirst().orElseThrow();
        exercisePost.setAnswers(createBasicAnswers(exercisePost, userPrefix));
        postRepository.save(exercisePost);

        // resolved post
        Post courseWidePost = posts.stream().filter(coursePost -> coursePost.getCourseWideContext() != null).findFirst().orElseThrow();
        courseWidePost.setAnswers(createBasicAnswersThatResolves(courseWidePost, userPrefix));
        postRepository.save(courseWidePost);

        Post conversationPost = posts.stream().filter(coursePost -> coursePost.getConversation() != null).findFirst().orElseThrow();
        conversationPost.setAnswers(createBasicAnswers(conversationPost, userPrefix));
        postRepository.save(conversationPost);

        return posts;
    }

    private List<Post> createBasicPosts(Exercise exerciseContext, String userPrefix) {
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Post postToAdd = createBasicPost(i, userPrefix + "student");
            postToAdd.setExercise(exerciseContext);
            postRepository.save(postToAdd);
            posts.add(postToAdd);
        }
        return posts;
    }

    private List<Post> createBasicPosts(Lecture lectureContext, String userPrefix) {
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Post postToAdd = createBasicPost(i, userPrefix + "tutor");
            postToAdd.setLecture(lectureContext);
            postRepository.save(postToAdd);
            posts.add(postToAdd);
        }
        return posts;
    }

    private List<Post> createBasicPosts(Course courseContext, CourseWideContext[] courseWideContexts, String userPrefix) {
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < courseWideContexts.length; i++) {
            Post postToAdd = createBasicPost(i, userPrefix + "editor");
            postToAdd.setCourse(courseContext);
            postToAdd.setCourseWideContext(courseWideContexts[i]);
            postRepository.save(postToAdd);
            posts.add(postToAdd);
        }
        return posts;
    }

    private Post createBasicPost(PlagiarismCase plagiarismCase, String userPrefix) {
        Post postToAdd = createBasicPost(0, userPrefix + "instructor");
        postToAdd.setPlagiarismCase(plagiarismCase);
        postToAdd.getPlagiarismCase().setExercise(null);
        return postRepository.save(postToAdd);
    }

    private Post createBasicPost(Integer i, String usernamePrefix) {
        Post post = new Post();
        post.setTitle(String.format("Title Post %s", (i + 1)));
        post.setContent(String.format("Content Post %s", (i + 1)));
        post.setVisibleForStudents(true);
        post.setDisplayPriority(DisplayPriority.NONE);
        post.setAuthor(getUserByLoginWithoutAuthorities(String.format("%s%s", usernamePrefix, (i + 1))));
        post.setCreationDate(ZonedDateTime.of(2015, 11, dayCount, 23, 45, 59, 1234, ZoneId.of("UTC")));
        String tag = String.format("Tag %s", (i + 1));
        Set<String> tags = new HashSet<>();
        tags.add(tag);
        post.setTags(tags);

        dayCount = (dayCount % 25) + 1;
        return post;
    }

    private List<Post> createBasicPosts(Conversation conversation, String userPrefix) {
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Post postToAdd = createBasicPost(i, userPrefix + "tutor");
            postToAdd.setConversation(conversation);
            postRepository.save(postToAdd);
            posts.add(postToAdd);
        }
        return posts;
    }

    private Set<AnswerPost> createBasicAnswers(Post post, String userPrefix) {
        Set<AnswerPost> answerPosts = new HashSet<>();
        AnswerPost answerPost = new AnswerPost();
        answerPost.setContent(post.getContent() + " Answer");
        answerPost.setAuthor(getUserByLoginWithoutAuthorities(userPrefix + "student1"));
        answerPost.setPost(post);
        answerPosts.add(answerPost);
        answerPostRepository.save(answerPost);
        post.setAnswerCount(post.getAnswerCount() + 1);
        return answerPosts;
    }

    private Set<AnswerPost> createBasicAnswersThatResolves(Post post, String userPrefix) {
        Set<AnswerPost> answerPosts = new HashSet<>();
        AnswerPost answerPost = new AnswerPost();
        answerPost.setContent(post.getContent() + " Answer");
        answerPost.setAuthor(getUserByLoginWithoutAuthorities(userPrefix + "student1"));
        answerPost.setPost(post);
        answerPost.setResolvesPost(true);
        answerPosts.add(answerPost);
        answerPostRepository.save(answerPost);
        post.setAnswerCount(post.getAnswerCount() + 1);
        post.setResolved(true);
        return answerPosts;
    }

    public List<Course> createMultipleCoursesWithAllExercisesAndLectures(String userPrefix, int numberOfCoursesWithExercises, int numberOfCoursesWithLectures,
            int numberOfTutorParticipations) throws Exception {
        List<Course> courses = new ArrayList<>();
        for (int i = 0; i < numberOfCoursesWithExercises; i++) {
            var course = createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(userPrefix, true);
            courses.add(course);
        }
        for (int i = 0; i < numberOfCoursesWithLectures; i++) {
            var coursesWithLectures = createCoursesWithExercisesAndLecturesAndLectureUnits(userPrefix, true, true, numberOfTutorParticipations);
            courses.addAll(coursesWithLectures);
        }
        return courses;
    }

    public Course createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(String userPrefix, boolean hasAssessmentDueDatePassed) {
        var assessmentTimestamp = hasAssessmentDueDatePassed ? ZonedDateTime.now().minusMinutes(10L) : ZonedDateTime.now().plusMinutes(10L);
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");

        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, "png", course);
        ProgrammingExercise programmingExercise = ModelFactory.generateProgrammingExercise(pastTimestamp, futureTimestamp, course);
        QuizExercise quizExercise = ModelFactory.generateQuizExercise(pastTimestamp, assessmentTimestamp, QuizMode.SYNCHRONIZED, course);

        // Set assessment due dates
        modelingExercise.setAssessmentDueDate(assessmentTimestamp);
        textExercise.setAssessmentDueDate(assessmentTimestamp);
        fileUploadExercise.setAssessmentDueDate(assessmentTimestamp);
        programmingExercise.setAssessmentDueDate(assessmentTimestamp);

        // Add exercises to course
        course.addExercises(modelingExercise);
        course.addExercises(textExercise);
        course.addExercises(fileUploadExercise);
        course.addExercises(programmingExercise);
        course.addExercises(quizExercise);

        // Save course and exercises to database
        Course courseSaved = courseRepo.save(course);
        modelingExercise = exerciseRepo.save(modelingExercise);
        textExercise = exerciseRepo.save(textExercise);
        fileUploadExercise = exerciseRepo.save(fileUploadExercise);
        programmingExercise = exerciseRepo.save(programmingExercise);
        quizExercise = exerciseRepo.save(quizExercise);

        // Get user and setup participations
        User user = (userRepo.findOneByLogin(userPrefix + "student1")).get();
        StudentParticipation participationModeling = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, modelingExercise, user);
        StudentParticipation participationText = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise, user);
        StudentParticipation participationFileUpload = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, fileUploadExercise, user);
        StudentParticipation participationQuiz = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, quizExercise, user);
        StudentParticipation participationProgramming = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, programmingExercise, user);

        // Save participations
        participationModeling = studentParticipationRepo.save(participationModeling);
        participationText = studentParticipationRepo.save(participationText);
        participationFileUpload = studentParticipationRepo.save(participationFileUpload);
        participationQuiz = studentParticipationRepo.save(participationQuiz);
        participationProgramming = studentParticipationRepo.save(participationProgramming);

        // Setup results
        Result resultModeling = ModelFactory.generateResult(true, 10D);
        resultModeling.setAssessmentType(AssessmentType.MANUAL);
        resultModeling.setCompletionDate(ZonedDateTime.now());

        Result resultText = ModelFactory.generateResult(true, 12D);
        resultText.setAssessmentType(AssessmentType.MANUAL);
        resultText.setCompletionDate(ZonedDateTime.now());

        Result resultFileUpload = ModelFactory.generateResult(true, 0D);
        resultFileUpload.setAssessmentType(AssessmentType.MANUAL);
        resultFileUpload.setCompletionDate(ZonedDateTime.now());

        Result resultQuiz = ModelFactory.generateResult(true, 0D);
        resultQuiz.setAssessmentType(AssessmentType.AUTOMATIC);
        resultQuiz.setCompletionDate(ZonedDateTime.now());

        Result resultProgramming = ModelFactory.generateResult(true, 20D);
        resultProgramming.setAssessmentType(AssessmentType.AUTOMATIC);
        resultProgramming.setCompletionDate(ZonedDateTime.now());

        // Connect participations to results and vice versa
        resultModeling.setParticipation(participationModeling);
        resultText.setParticipation(participationText);
        resultFileUpload.setParticipation(participationFileUpload);
        resultQuiz.setParticipation(participationQuiz);
        resultProgramming.setParticipation(participationProgramming);

        participationModeling.addResult(resultModeling);
        participationText.addResult(resultText);
        participationFileUpload.addResult(resultFileUpload);
        participationQuiz.addResult(resultQuiz);
        participationProgramming.addResult(resultProgramming);

        // Save results and participations
        resultModeling = resultRepo.save(resultModeling);
        resultText = resultRepo.save(resultText);
        resultFileUpload = resultRepo.save(resultFileUpload);
        resultQuiz = resultRepo.save(resultQuiz);
        resultProgramming = resultRepo.save(resultProgramming);

        participationModeling = studentParticipationRepo.save(participationModeling);
        participationText = studentParticipationRepo.save(participationText);
        participationFileUpload = studentParticipationRepo.save(participationFileUpload);
        participationQuiz = studentParticipationRepo.save(participationQuiz);
        participationProgramming = studentParticipationRepo.save(participationProgramming);

        // Connect exercises with participations
        modelingExercise.addParticipation(participationModeling);
        textExercise.addParticipation(participationText);
        fileUploadExercise.addParticipation(participationFileUpload);
        quizExercise.addParticipation(participationQuiz);
        programmingExercise.addParticipation(participationProgramming);

        // Setup submissions and connect with participations
        ModelingSubmission modelingSubmission = ModelFactory.generateModelingSubmission("model1", true);
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("text of text submission", Language.ENGLISH, true);
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        QuizSubmission quizSubmission = ModelFactory.generateQuizSubmission(true);
        ProgrammingSubmission programmingSubmission = ModelFactory.generateProgrammingSubmission(true);

        // Save submissions
        modelingSubmission = submissionRepository.save(modelingSubmission);
        textSubmission = submissionRepository.save(textSubmission);
        fileUploadSubmission = submissionRepository.save(fileUploadSubmission);
        quizSubmission = submissionRepository.save(quizSubmission);
        programmingSubmission = submissionRepository.save(programmingSubmission);

        modelingSubmission.setParticipation(participationModeling);
        modelingSubmission.addResult(resultModeling);
        textSubmission.setParticipation(participationText);
        textSubmission.addResult(resultText);
        fileUploadSubmission.setParticipation(participationFileUpload);
        fileUploadSubmission.addResult(resultFileUpload);
        quizSubmission.setParticipation(participationQuiz);
        quizSubmission.addResult(resultQuiz);
        programmingSubmission.setParticipation(participationProgramming);
        programmingSubmission.addResult(resultProgramming);

        // Save submissions
        modelingSubmission = submissionRepository.save(modelingSubmission);
        textSubmission = submissionRepository.save(textSubmission);
        fileUploadSubmission = submissionRepository.save(fileUploadSubmission);
        quizSubmission = submissionRepository.save(quizSubmission);
        programmingSubmission = submissionRepository.save(programmingSubmission);

        // Save exercises
        exerciseRepo.save(modelingExercise);
        exerciseRepo.save(textExercise);
        exerciseRepo.save(fileUploadExercise);
        exerciseRepo.save(programmingExercise);
        exerciseRepo.save(quizExercise);

        // Connect participations with submissions
        participationModeling.setSubmissions(Set.of(modelingSubmission));
        participationText.setSubmissions(Set.of(textSubmission));
        participationFileUpload.setSubmissions(Set.of(fileUploadSubmission));
        participationQuiz.setSubmissions(Set.of(quizSubmission));
        participationProgramming.setSubmissions(Set.of(programmingSubmission));

        // Save participations
        studentParticipationRepo.save(participationModeling);
        studentParticipationRepo.save(participationText);
        studentParticipationRepo.save(participationFileUpload);
        studentParticipationRepo.save(participationQuiz);
        studentParticipationRepo.save(participationProgramming);

        return courseSaved;
    }

    public Organization createOrganization(String name, String shortName, String url, String description, String logoUrl, String emailPattern) {
        Organization organization = ModelFactory.generateOrganization(name, shortName, url, description, logoUrl, emailPattern);
        return organizationRepository.save(organization);
    }

    public Organization createOrganization() {
        return createOrganization(UUID.randomUUID().toString().replace("-", ""), UUID.randomUUID().toString().replace("-", ""), UUID.randomUUID().toString().replace("-", ""),
                UUID.randomUUID().toString().replace("-", ""), null, "^.*@matching.*$");
    }

    public StudentExam setupTestRunForExamWithExerciseGroupsForInstructor(Exam exam, User instructor, List<ExerciseGroup> exerciseGroupsWithExercises) {
        List<Exercise> exercises = new ArrayList<>();
        exerciseGroupsWithExercises.forEach(exerciseGroup -> exercises.add(exerciseGroup.getExercises().iterator().next()));
        var testRun = generateTestRunForInstructor(exam, instructor, exercises);
        return studentExamRepository.save(testRun);
    }

    public StudentExam generateTestRunForInstructor(Exam exam, User instructor, List<Exercise> exercises) {
        var testRun = ModelFactory.generateExamTestRun(exam);
        testRun.setUser(instructor);
        for (final var exercise : exercises) {
            testRun.addExercise(exercise);
            assertThat(exercise.isExamExercise()).isTrue();
            Submission submission = null;
            if (exercise instanceof ModelingExercise modelingExercise) {
                submission = addModelingSubmission(modelingExercise, ModelFactory.generateModelingSubmission("", false), instructor.getLogin());
            }
            else if (exercise instanceof TextExercise textExercise) {
                submission = saveTextSubmission(textExercise, ModelFactory.generateTextSubmission("", null, false), instructor.getLogin());
            }
            else if (exercise instanceof QuizExercise quizExercise) {
                submission = saveQuizSubmission(quizExercise, ModelFactory.generateQuizSubmission(false), instructor.getLogin());
            }
            else if (exercise instanceof ProgrammingExercise programmingExercise) {
                submission = new ProgrammingSubmission().submitted(true);
                addProgrammingSubmission(programmingExercise, (ProgrammingSubmission) submission, instructor.getLogin());
                submission = submissionRepository.save(submission);
            }
            else if (exercise instanceof FileUploadExercise fileUploadExercise) {
                submission = saveFileUploadSubmission(fileUploadExercise, ModelFactory.generateFileUploadSubmission(false), instructor.getLogin());
            }
            var studentParticipation = (StudentParticipation) submission.getParticipation();
            studentParticipation.setTestRun(true);
            studentParticipationRepo.save(studentParticipation);
        }
        return testRun;
    }

    public Exam setupSimpleExamWithExerciseGroupExercise(Course course) {
        var exam = ModelFactory.generateExam(course);
        exam.setNumberOfExercisesInExam(1);
        exam.setRandomizeExerciseOrder(true);
        exam.setStartDate(ZonedDateTime.now().plusHours(2));
        exam.setEndDate(ZonedDateTime.now().plusHours(4));
        exam.setExamMaxPoints(20);
        exam = examRepository.save(exam);

        // add exercise group: 1 mandatory
        ModelFactory.generateExerciseGroup(true, exam);
        exam = examRepository.save(exam);

        // add exercises
        var exercise1a = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        var exercise1b = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        var exercise1c = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        exerciseRepo.saveAll(List.of(exercise1a, exercise1b, exercise1c));

        return examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
    }

    public Exam setupExamWithExerciseGroupsExercisesRegisteredStudents(String userPrefix, Course course) {
        return setupExamWithExerciseGroupsExercisesRegisteredStudents(userPrefix, course, 4);
    }

    public Exam setupExamWithExerciseGroupsExercisesRegisteredStudents(String userPrefix, Course course, int numberOfStudents) {
        Exam exam = ModelFactory.generateExam(course);
        exam.setNumberOfExercisesInExam(4);
        exam.setRandomizeExerciseOrder(true);
        exam.setStartDate(ZonedDateTime.now().plusHours(2));
        exam.setEndDate(ZonedDateTime.now().plusHours(4));
        exam.setWorkingTime(2 * 60 * 60);
        exam.setExamMaxPoints(20);
        exam = examRepository.save(exam);

        // add exercise groups: 3 mandatory, 2 optional
        ModelFactory.generateExerciseGroup(true, exam);
        ModelFactory.generateExerciseGroup(true, exam);
        ModelFactory.generateExerciseGroup(true, exam);
        ModelFactory.generateExerciseGroup(false, exam);
        ModelFactory.generateExerciseGroup(false, exam);
        exam = examRepository.save(exam);

        // TODO: also add other exercise types

        // add exercises
        var exercise1a = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        var exercise1b = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        var exercise1c = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        exerciseRepo.saveAll(List.of(exercise1a, exercise1b, exercise1c));

        var exercise2a = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(1));
        var exercise2b = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(1));
        var exercise2c = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(1));
        exerciseRepo.saveAll(List.of(exercise2a, exercise2b, exercise2c));

        var exercise3a = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(2));
        var exercise3b = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(2));
        var exercise3c = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(2));
        exerciseRepo.saveAll(List.of(exercise3a, exercise3b, exercise3c));

        var exercise4a = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(3));
        var exercise4b = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(3));
        var exercise4c = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(3));
        exerciseRepo.saveAll(List.of(exercise4a, exercise4b, exercise4c));

        var exercise5a = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(4));
        var exercise5b = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(4));
        var exercise5c = ModelFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(4));
        exerciseRepo.saveAll(List.of(exercise5a, exercise5b, exercise5c));

        // register user
        return registerUsersForExamAndSaveExam(exam, userPrefix, numberOfStudents);
    }

    public Exam registerUsersForExamAndSaveExam(Exam exam, String userPrefix, int numberOfStudents) {
        return registerUsersForExamAndSaveExam(exam, userPrefix, 1, numberOfStudents);
    }

    /**
     * registers students for exam and saves the exam in the repository
     *
     * @param exam       exam to which students should be registered to
     * @param userPrefix prefix of the users
     * @param from       index of the first student to be registered
     * @param to         index of the last student to be registered
     * @return exam that was saved in the repository
     */
    public Exam registerUsersForExamAndSaveExam(Exam exam, String userPrefix, int from, int to) {

        for (int i = from; i <= to; i++) {
            ExamUser registeredExamUser = new ExamUser();
            registeredExamUser.setUser(getUserByLogin(userPrefix + "student" + i));
            registeredExamUser.setExam(exam);
            exam.addExamUser(registeredExamUser);
            examUserRepository.save(registeredExamUser);
        }

        return examRepository.save(exam);
    }

    public Exam addExam(Course course) {
        Exam exam = ModelFactory.generateExam(course);
        return examRepository.save(exam);
    }

    public Exam addTestExam(Course course) {
        Exam exam = ModelFactory.generateTestExam(course);
        return examRepository.save(exam);
    }

    public Exam addTestExamWithRegisteredUser(Course course, User user) {
        Exam exam = ModelFactory.generateTestExam(course);
        exam = examRepository.save(exam);
        var registeredExamUser = new ExamUser();
        registeredExamUser.setUser(user);
        registeredExamUser.setExam(exam);
        registeredExamUser = examUserRepository.save(registeredExamUser);
        exam.addExamUser(registeredExamUser);
        examRepository.save(exam);
        return exam;
    }

    public Exam addExam(Course course, User user, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate) {
        Exam exam = ModelFactory.generateExam(course);
        exam = examRepository.save(exam);
        var registeredExamUser = new ExamUser();
        registeredExamUser.setUser(user);
        registeredExamUser.setExam(exam);
        registeredExamUser = examUserRepository.save(registeredExamUser);
        exam.addExamUser(registeredExamUser);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setNumberOfCorrectionRoundsInExam(1);
        examRepository.save(exam);
        return exam;
    }

    public Exam addExamWithExerciseGroup(Course course, boolean mandatory) {
        Exam exam = ModelFactory.generateExam(course);
        ModelFactory.generateExerciseGroup(mandatory, exam);
        exam = examRepository.save(exam);
        return exam;
    }

    public Exam addTestExamWithExerciseGroup(Course course, boolean mandatory) {
        Exam exam = ModelFactory.generateTestExam(course);
        ModelFactory.generateExerciseGroup(mandatory, exam);
        exam = examRepository.save(exam);
        return exam;
    }

    public Exam addExam(Course course, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate) {
        Exam exam = ModelFactory.generateExam(course);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setWorkingTime((int) Duration.between(startDate, endDate).toSeconds());
        exam.setGracePeriod(180);
        exam = examRepository.save(exam);
        return exam;
    }

    public Exam addExam(Course course, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate, ZonedDateTime publishResultDate) {
        Exam exam = ModelFactory.generateExam(course);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setPublishResultsDate(publishResultDate);
        exam.setWorkingTime((int) Duration.between(startDate, endDate).toSeconds());
        exam.setGracePeriod(180);
        exam = examRepository.save(exam);
        return exam;
    }

    public Exam addActiveExamWithRegisteredUser(Course course, User user) {
        Exam exam = ModelFactory.generateExam(course);
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(1));
        exam.setWorkingTime(2 * 60 * 60);
        exam = examRepository.save(exam);
        var registeredExamUser = new ExamUser();
        registeredExamUser.setUser(user);
        registeredExamUser.setExam(exam);
        registeredExamUser = examUserRepository.save(registeredExamUser);
        exam.addExamUser(registeredExamUser);
        exam.setTestExam(false);
        examRepository.save(exam);
        var studentExam = new StudentExam();
        studentExam.setExam(exam);
        studentExam.setTestRun(false);
        studentExam.setUser(user);
        studentExam.setWorkingTime((int) Duration.between(exam.getStartDate(), exam.getEndDate()).toSeconds());
        studentExamRepository.save(studentExam);
        return exam;
    }

    public Exam addActiveTestExamWithRegisteredUserWithoutStudentExam(Course course, User user) {
        Exam exam = ModelFactory.generateTestExam(course);
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(1));
        exam.setWorkingTime(2 * 60 * 60);
        exam = examRepository.save(exam);
        var registeredExamUser = new ExamUser();
        registeredExamUser.setUser(user);
        registeredExamUser.setExam(exam);
        registeredExamUser = examUserRepository.save(registeredExamUser);
        exam.addExamUser(registeredExamUser);
        examRepository.save(exam);
        return exam;
    }

    public Exam addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(Course course) {
        Exam exam = addExam(course);
        for (int i = 0; i <= 4; i++) {
            ModelFactory.generateExerciseGroup(true, exam);
        }
        exam.setNumberOfExercisesInExam(5);
        exam.setExamMaxPoints(5 * 5);
        exam = examRepository.save(exam);

        ExerciseGroup modellingGroup = exam.getExerciseGroups().get(0);
        Exercise modelling = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, modellingGroup);
        modellingGroup.addExercise(modelling);
        exerciseRepo.save(modelling);

        ExerciseGroup textGroup = exam.getExerciseGroups().get(1);
        Exercise text = ModelFactory.generateTextExerciseForExam(textGroup);
        textGroup.addExercise(text);
        exerciseRepo.save(text);

        ExerciseGroup fileUploadGroup = exam.getExerciseGroups().get(2);
        Exercise fileUpload = ModelFactory.generateFileUploadExerciseForExam("png", fileUploadGroup);
        fileUploadGroup.addExercise(fileUpload);
        exerciseRepo.save(fileUpload);

        ExerciseGroup quizGroup = exam.getExerciseGroups().get(3);
        Exercise quiz = ModelFactory.generateQuizExerciseForExam(quizGroup);
        quizGroup.addExercise(quiz);
        exerciseRepo.save(quiz);

        return exam;
    }

    public StudentExam addStudentExam(Exam exam) {
        StudentExam studentExam = ModelFactory.generateStudentExam(exam);
        studentExam = studentExamRepository.save(studentExam);
        return studentExam;
    }

    public StudentExam addStudentExamWithUser(Exam exam, String user) {
        return addStudentExamWithUser(exam, userRepo.findOneByLogin(user).orElseThrow());
    }

    public StudentExam addStudentExamWithUser(Exam exam, User user) {
        StudentExam studentExam = ModelFactory.generateStudentExam(exam);
        studentExam.setUser(user);
        studentExam = studentExamRepository.save(studentExam);
        return studentExam;
    }

    public StudentExam addStudentExamForTestExam(Exam exam, User user) {
        StudentExam studentExam = ModelFactory.generateStudentExamForTestExam(exam);
        studentExam.setUser(user);
        studentExam = studentExamRepository.save(studentExam);
        return studentExam;
    }

    public StudentExam addStudentExamWithUser(Exam exam, User user, int additionalWorkingTime) {
        StudentExam studentExam = ModelFactory.generateStudentExam(exam);
        studentExam.setUser(user);
        studentExam.setWorkingTime((int) Duration.between(exam.getStartDate(), exam.getEndDate()).toSeconds() + additionalWorkingTime);
        studentExam = studentExamRepository.save(studentExam);
        return studentExam;
    }

    public Exam addExerciseGroupsAndExercisesToExam(Exam exam, boolean withProgrammingExercise) {
        ModelFactory.generateExerciseGroup(true, exam); // text
        ModelFactory.generateExerciseGroup(true, exam); // quiz
        ModelFactory.generateExerciseGroup(true, exam); // file upload
        ModelFactory.generateExerciseGroup(true, exam); // modeling
        ModelFactory.generateExerciseGroup(true, exam); // bonus text
        ModelFactory.generateExerciseGroup(true, exam); // not included text
        exam.setNumberOfExercisesInExam(6);
        exam.setExamMaxPoints(24);
        exam = examRepository.save(exam);
        // NOTE: we have to reassign, otherwise we get problems, because the objects have changed
        var exerciseGroup0 = exam.getExerciseGroups().get(0);
        var exerciseGroup1 = exam.getExerciseGroups().get(1);
        var exerciseGroup2 = exam.getExerciseGroups().get(2);
        var exerciseGroup3 = exam.getExerciseGroups().get(3);
        var exerciseGroup4 = exam.getExerciseGroups().get(4);
        var exerciseGroup5 = exam.getExerciseGroups().get(5);

        TextExercise textExercise1 = ModelFactory.generateTextExerciseForExam(exerciseGroup0);
        TextExercise textExercise2 = ModelFactory.generateTextExerciseForExam(exerciseGroup0);
        exerciseGroup0.setExercises(Set.of(textExercise1, textExercise2));
        exerciseRepo.save(textExercise1);
        exerciseRepo.save(textExercise2);

        QuizExercise quizExercise1 = createQuizForExam(exerciseGroup1);
        QuizExercise quizExercise2 = createQuizForExam(exerciseGroup1);
        exerciseGroup1.setExercises(Set.of(quizExercise1, quizExercise2));
        exerciseRepo.save(quizExercise1);
        exerciseRepo.save(quizExercise2);

        FileUploadExercise fileUploadExercise1 = ModelFactory.generateFileUploadExerciseForExam("pdf", exerciseGroup2);
        FileUploadExercise fileUploadExercise2 = ModelFactory.generateFileUploadExerciseForExam("pdf", exerciseGroup2);
        exerciseGroup2.setExercises(Set.of(fileUploadExercise1, fileUploadExercise2));
        exerciseRepo.save(fileUploadExercise1);
        exerciseRepo.save(fileUploadExercise2);

        ModelingExercise modelingExercise1 = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup3);
        ModelingExercise modelingExercise2 = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup3);
        exerciseGroup3.setExercises(Set.of(modelingExercise1, modelingExercise2));
        exerciseRepo.save(modelingExercise1);
        exerciseRepo.save(modelingExercise2);

        TextExercise bonusTextExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup4);
        bonusTextExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        exerciseGroup4.setExercises(Set.of(bonusTextExercise));
        exerciseRepo.save(bonusTextExercise);

        TextExercise notIncludedTextExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup5);
        notIncludedTextExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        exerciseGroup5.setExercises(Set.of(notIncludedTextExercise));
        exerciseRepo.save(notIncludedTextExercise);

        if (withProgrammingExercise) {
            ModelFactory.generateExerciseGroup(true, exam); // programming
            exam.setNumberOfExercisesInExam(7);
            exam.setExamMaxPoints(29);
            exam = examRepository.save(exam);
            var exerciseGroup6 = exam.getExerciseGroups().get(6);
            // Programming exercises need a proper setup for 'prepare exam start' to work
            ProgrammingExercise programmingExercise1 = ModelFactory.generateProgrammingExerciseForExam(exerciseGroup6);
            exerciseRepo.save(programmingExercise1);
            addTemplateParticipationForProgrammingExercise(programmingExercise1);
            addSolutionParticipationForProgrammingExercise(programmingExercise1);

            exerciseGroup6.setExercises(Set.of(programmingExercise1));
        }

        return exam;
    }

    public Exam addTextModelingProgrammingExercisesToExam(Exam initialExam, boolean withProgrammingExercise, boolean withQuizExercise) {
        ModelFactory.generateExerciseGroup(true, initialExam); // text
        ModelFactory.generateExerciseGroup(true, initialExam); // modeling
        initialExam.setNumberOfExercisesInExam(2);
        var exam = examRepository.save(initialExam);
        // NOTE: we have to reassign, otherwise we get problems, because the objects have changed
        var exerciseGroup0 = exam.getExerciseGroups().get(0);
        var exerciseGroup1 = exam.getExerciseGroups().get(1);

        TextExercise textExercise1 = ModelFactory.generateTextExerciseForExam(exerciseGroup0);
        TextExercise textExercise2 = ModelFactory.generateTextExerciseForExam(exerciseGroup0);
        exerciseGroup0.setExercises(Set.of(textExercise1, textExercise2));
        exerciseRepo.save(textExercise1);
        exerciseRepo.save(textExercise2);

        ModelingExercise modelingExercise1 = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        ModelingExercise modelingExercise2 = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        exerciseGroup1.setExercises(Set.of(modelingExercise1, modelingExercise2));
        exerciseRepo.save(modelingExercise1);
        exerciseRepo.save(modelingExercise2);

        if (withProgrammingExercise) {
            ModelFactory.generateExerciseGroup(true, exam); // programming
            exam.setNumberOfExercisesInExam(3);
            exam = examRepository.save(exam);
            var exerciseGroup2 = exam.getExerciseGroups().get(2);
            // Programming exercises need a proper setup for 'prepare exam start' to work
            ProgrammingExercise programmingExercise1 = ModelFactory.generateProgrammingExerciseForExam(exerciseGroup2);
            exerciseRepo.save(programmingExercise1);
            addTemplateParticipationForProgrammingExercise(programmingExercise1);
            addSolutionParticipationForProgrammingExercise(programmingExercise1);
            exerciseGroup2.setExercises(Set.of(programmingExercise1));
        }

        if (withQuizExercise) {
            ModelFactory.generateExerciseGroup(true, exam); // modeling
            exam.setNumberOfExercisesInExam(3 + (withProgrammingExercise ? 1 : 0));
            exam = examRepository.save(exam);
            var exerciseGroup3 = exam.getExerciseGroups().get(2 + (withProgrammingExercise ? 1 : 0));
            // Programming exercises need a proper setup for 'prepare exam start' to work
            QuizExercise quizExercise = createQuizForExam(exerciseGroup3);
            exerciseRepo.save(quizExercise);
            exerciseGroup3.setExercises(Set.of(quizExercise));
        }
        return exam;
    }

    /**
     * Stores participation of the user with the given login for the given exercise
     *
     * @param exercise the exercise for which the participation will be created
     * @param login    login of the user
     * @return eagerly loaded representation of the participation object stored in the database
     */
    public StudentParticipation createAndSaveParticipationForExercise(Exercise exercise, String login) {
        Optional<StudentParticipation> storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), login,
                false);
        if (storedParticipation.isEmpty()) {
            User user = getUserByLogin(login);
            StudentParticipation participation = new StudentParticipation();
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setParticipant(user);
            participation.setExercise(exercise);
            studentParticipationRepo.save(participation);
            storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), login, false);
            assertThat(storedParticipation).isPresent();
        }
        return studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).get();
    }

    public StudentParticipation createAndSaveParticipationForExerciseInTheFuture(Exercise exercise, String login) {
        Optional<StudentParticipation> storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), login,
                false);
        storedParticipation.ifPresent(studentParticipation -> studentParticipationRepo.delete(studentParticipation));
        User user = getUserByLogin(login);
        StudentParticipation participation = new StudentParticipation();
        participation.setInitializationDate(ZonedDateTime.now().plusDays(2));
        participation.setParticipant(user);
        participation.setExercise(exercise);
        studentParticipationRepo.save(participation);
        storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), login, false);
        assertThat(storedParticipation).isPresent();
        return studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).get();
    }

    /**
     * Stores participation of the team with the given id for the given exercise
     *
     * @param exercise the exercise for which the participation will be created
     * @param teamId   id of the team
     * @return eagerly loaded representation of the participation object stored in the database
     */
    public StudentParticipation addTeamParticipationForExercise(Exercise exercise, long teamId) {
        Optional<StudentParticipation> storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndTeamId(exercise.getId(), teamId);
        if (storedParticipation.isEmpty()) {
            Team team = teamRepo.findById(teamId).orElseThrow();
            StudentParticipation participation = new StudentParticipation();
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setParticipant(team);
            participation.setExercise(exercise);
            studentParticipationRepo.save(participation);
            storedParticipation = studentParticipationRepo.findWithEagerLegalSubmissionsByExerciseIdAndTeamId(exercise.getId(), teamId);
            assertThat(storedParticipation).isPresent();
        }
        return studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(storedParticipation.get().getId()).get();
    }

    public ProgrammingExerciseStudentParticipation addStudentParticipationForProgrammingExercise(ProgrammingExercise exercise, String login) {
        final var existingParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
        if (existingParticipation.isPresent()) {
            return existingParticipation.get();
        }
        ProgrammingExerciseStudentParticipation participation = configureIndividualParticipation(exercise, login);
        final var repoName = (exercise.getProjectKey() + "-" + login).toLowerCase();
        participation.setRepositoryUrl(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation = programmingExerciseStudentParticipationRepo.save(participation);

        return (ProgrammingExerciseStudentParticipation) studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(participation.getId()).get();
    }

    public ProgrammingExerciseStudentParticipation addTeamParticipationForProgrammingExercise(ProgrammingExercise exercise, Team team) {

        final var existingParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndTeamId(exercise.getId(), team.getId());
        if (existingParticipation.isPresent()) {
            return existingParticipation.get();
        }
        ProgrammingExerciseStudentParticipation participation = configureTeamParticipation(exercise, team);
        final var repoName = (exercise.getProjectKey() + "-" + team.getShortName()).toLowerCase();
        participation.setRepositoryUrl(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation = programmingExerciseStudentParticipationRepo.save(participation);

        return (ProgrammingExerciseStudentParticipation) studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(participation.getId()).get();
    }

    public ProgrammingExerciseStudentParticipation addStudentParticipationForProgrammingExerciseForLocalRepo(ProgrammingExercise exercise, String login, URI localRepoPath) {
        final var existingParticipation = programmingExerciseStudentParticipationRepo.findByExerciseIdAndStudentLogin(exercise.getId(), login);
        if (existingParticipation.isPresent()) {
            return existingParticipation.get();
        }
        ProgrammingExerciseStudentParticipation participation = configureIndividualParticipation(exercise, login);
        final var repoName = (exercise.getProjectKey() + "-" + login).toLowerCase();
        participation.setRepositoryUrl(String.format(localRepoPath.toString() + "%s/%s.git", exercise.getProjectKey(), repoName));
        participation = programmingExerciseStudentParticipationRepo.save(participation);

        return (ProgrammingExerciseStudentParticipation) studentParticipationRepo.findWithEagerLegalSubmissionsAndResultsAssessorsById(participation.getId()).get();
    }

    private ProgrammingExerciseStudentParticipation configureIndividualParticipation(ProgrammingExercise exercise, String login) {
        final var user = getUserByLogin(login);
        var participation = new ProgrammingExerciseStudentParticipation();
        final var buildPlanId = exercise.getProjectKey().toUpperCase() + "-" + login.toUpperCase();
        participation.setInitializationDate(ZonedDateTime.now());
        participation.setParticipant(user);
        participation.setBuildPlanId(buildPlanId);
        participation.setProgrammingExercise(exercise);
        participation.setInitializationState(InitializationState.INITIALIZED);
        participation.setBranch(DEFAULT_BRANCH);
        return participation;
    }

    private ProgrammingExerciseStudentParticipation configureTeamParticipation(ProgrammingExercise exercise, Team team) {
        var participation = new ProgrammingExerciseStudentParticipation();
        final var buildPlanId = exercise.getProjectKey().toUpperCase() + "-" + team.getShortName().toUpperCase();
        participation.setInitializationDate(ZonedDateTime.now());
        participation.setParticipant(team);
        participation.setBuildPlanId(buildPlanId);
        participation.setProgrammingExercise(exercise);
        participation.setInitializationState(InitializationState.INITIALIZED);
        return participation;
    }

    public ProgrammingExercise addTemplateParticipationForProgrammingExercise(ProgrammingExercise exercise) {
        final var repoName = exercise.generateRepositoryName(RepositoryType.TEMPLATE);
        TemplateProgrammingExerciseParticipation participation = new TemplateProgrammingExerciseParticipation();
        participation.setProgrammingExercise(exercise);
        participation.setBuildPlanId(exercise.generateBuildPlanId(BuildPlanType.TEMPLATE));
        participation.setRepositoryUrl(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation.setInitializationState(InitializationState.INITIALIZED);
        templateProgrammingExerciseParticipationRepo.save(participation);
        exercise.setTemplateParticipation(participation);
        return programmingExerciseRepository.save(exercise);
    }

    public ProgrammingExercise addSolutionParticipationForProgrammingExercise(ProgrammingExercise exercise) {
        final var repoName = exercise.generateRepositoryName(RepositoryType.SOLUTION);
        SolutionProgrammingExerciseParticipation participation = new SolutionProgrammingExerciseParticipation();
        participation.setProgrammingExercise(exercise);
        participation.setBuildPlanId(exercise.generateBuildPlanId(BuildPlanType.SOLUTION));
        participation.setRepositoryUrl(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation.setInitializationState(InitializationState.INITIALIZED);
        solutionProgrammingExerciseParticipationRepo.save(participation);
        exercise.setSolutionParticipation(participation);
        return programmingExerciseRepository.save(exercise);
    }

    public Result addResultToParticipation(AssessmentType type, ZonedDateTime completionDate, Participation participation, boolean successful, boolean rated, double score) {
        Result result = new Result().participation(participation).successful(successful).rated(rated).score(score).assessmentType(type).completionDate(completionDate);
        return resultRepo.save(result);
    }

    public Result addResultToParticipation(AssessmentType assessmentType, ZonedDateTime completionDate, Participation participation) {
        Result result = new Result().participation(participation).successful(true).rated(true).score(100D).assessmentType(assessmentType).completionDate(completionDate);
        return resultRepo.save(result);
    }

    public Result addResultToParticipation(AssessmentType assessmentType, ZonedDateTime completionDate, Participation participation, String assessorLogin,
            List<Feedback> feedbacks) {
        Result result = new Result().participation(participation).assessmentType(assessmentType).completionDate(completionDate).feedbacks(feedbacks);
        result.setAssessor(getUserByLogin(assessorLogin));
        return resultRepo.save(result);
    }

    public Result addResultToParticipation(Participation participation, Submission submission) {
        Result result = new Result().participation(participation).successful(true).score(100D);
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        submission.setParticipation(participation);
        submissionRepository.save(submission);
        return result;
    }

    public Result addSampleFeedbackToResults(Result result) {
        Feedback feedback1 = feedbackRepo.save(new Feedback().detailText("detail1"));
        Feedback feedback2 = feedbackRepo.save(new Feedback().detailText("detail2"));
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(feedback1);
        feedbacks.add(feedback2);
        result.addFeedbacks(feedbacks);
        return resultRepo.save(result);
    }

    // @formatter:off
    public Result addVariousFeedbackTypeFeedbacksToResult(Result result) {
        // The order of declaration here should be the same order as in FeedbackType for each enum type
        List<Feedback> feedbacks = feedbackRepo.saveAll(Arrays.asList(
            new Feedback().detailText("manual").type(FeedbackType.MANUAL),
            new Feedback().detailText("manual_unreferenced").type(FeedbackType.MANUAL_UNREFERENCED),
            new Feedback().detailText("automatic_adapted").type(FeedbackType.AUTOMATIC_ADAPTED),
            new Feedback().detailText("automatic").type(FeedbackType.AUTOMATIC)
        ));

        result.addFeedbacks(feedbacks);
        return resultRepo.save(result);
    }

    public Result addVariousVisibilityFeedbackToResult(Result result) {
        List<Feedback> feedbacks = feedbackRepo.saveAll(Arrays.asList(
            new Feedback().detailText("afterDueDate1").visibility(Visibility.AFTER_DUE_DATE),
            new Feedback().detailText("never1").visibility(Visibility.NEVER),
            new Feedback().detailText("always1").visibility(Visibility.ALWAYS)
        ));

        result.addFeedbacks(feedbacks);
        return resultRepo.save(result);
    }
    // @formatter:on

    public Result addFeedbackToResult(Feedback feedback, Result result) {
        feedbackRepo.save(feedback);
        result.addFeedback(feedback);
        return resultRepo.save(result);
    }

    public Result addFeedbackToResults(Result result) {
        List<Feedback> feedback = ModelFactory.generateStaticCodeAnalysisFeedbackList(5);
        feedback.addAll(ModelFactory.generateFeedback());
        feedback = feedbackRepo.saveAll(feedback);
        result.addFeedbacks(feedback);
        return resultRepo.save(result);
    }

    public Submission addResultToSubmission(final Submission submission, AssessmentType assessmentType, User user, Double score, boolean rated, ZonedDateTime completionDate) {
        Result result = new Result().participation(submission.getParticipation()).assessmentType(assessmentType).score(score).rated(rated).completionDate(completionDate);
        result.setAssessor(user);
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        var savedSubmission = submissionRepository.save(submission);
        return submissionRepository.findWithEagerResultsAndAssessorById(savedSubmission.getId()).orElseThrow();
    }

    public Submission addResultToSubmission(Submission submission, AssessmentType assessmentType) {
        return addResultToSubmission(submission, assessmentType, null, 100D, true, null);
    }

    public Submission addResultToSubmission(Submission submission, AssessmentType assessmentType, User user) {
        return addResultToSubmission(submission, assessmentType, user, 100D, true, ZonedDateTime.now());
    }

    public Submission addResultToSubmission(Submission submission, AssessmentType assessmentType, User user, Double score, boolean rated) {
        return addResultToSubmission(submission, assessmentType, user, score, rated, ZonedDateTime.now());
    }

    public void addRatingToResult(Result result, int score) {
        var rating = new Rating();
        rating.setResult(result);
        rating.setRating(score);
        ratingRepo.save(rating);
    }

    public Exercise addMaxScoreAndBonusPointsToExercise(Exercise exercise) {
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setMaxPoints(100.0);
        exercise.setBonusPoints(10.0);
        return exerciseRepo.save(exercise);
    }

    public List<GradingCriterion> addGradingInstructionsToExercise(Exercise exercise) {
        GradingCriterion emptyCriterion = ModelFactory.generateGradingCriterion(null);
        List<GradingInstruction> instructionWithNoCriteria = ModelFactory.generateGradingInstructions(emptyCriterion, 1, 0);
        instructionWithNoCriteria.get(0).setCredits(1);
        instructionWithNoCriteria.get(0).setUsageCount(0);
        emptyCriterion.setExercise(exercise);
        emptyCriterion.setStructuredGradingInstructions(instructionWithNoCriteria);

        GradingCriterion testCriterion = ModelFactory.generateGradingCriterion("test title");
        List<GradingInstruction> instructions = ModelFactory.generateGradingInstructions(testCriterion, 3, 1);
        testCriterion.setStructuredGradingInstructions(instructions);

        GradingCriterion testCriterion2 = ModelFactory.generateGradingCriterion("test title2");
        List<GradingInstruction> instructionsWithBigLimit = ModelFactory.generateGradingInstructions(testCriterion2, 1, 4);
        testCriterion2.setStructuredGradingInstructions(instructionsWithBigLimit);

        testCriterion.setExercise(exercise);
        var criteria = new ArrayList<GradingCriterion>();
        criteria.add(emptyCriterion);
        criteria.add(testCriterion);
        criteria.add(testCriterion2);
        exercise.setGradingCriteria(criteria);
        return exercise.getGradingCriteria();
    }

    /**
     * @param title The title of the to be added modeling exercise
     * @return A course with one specified modeling exercise
     */
    public Course addCourseWithOneModelingExercise(String title) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        modelingExercise.setTitle(title);
        course.addExercises(modelingExercise);
        course.setMaxComplaintTimeDays(14);
        course = courseRepo.save(course);
        modelingExercise = exerciseRepo.save(modelingExercise);
        assertThat(course.getExercises()).as("course contains the exercise").containsExactlyInAnyOrder(modelingExercise);
        assertThat(modelingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();
        return course;
    }

    public Course addCourseWithOneModelingExercise() {
        return addCourseWithOneModelingExercise("ClassDiagram");
    }

    /**
     * @param title The title of the to be added text exercise
     * @return A course with one specified text exercise
     */
    public Course addCourseWithOneReleasedTextExercise(String title) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setTitle(title);
        course.addExercises(textExercise);
        course = courseRepo.save(course);
        textExercise = exerciseRepo.save(textExercise);
        assertThat(courseRepo.findWithEagerExercisesById(course.getId()).getExercises()).as("course contains the exercise").contains(textExercise);
        assertThat(textExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();

        return course;
    }

    public Course addCourseWithOneReleasedModelExerciseWithKnowledge(String title) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        modelingExercise.setTitle(title);
        course.addExercises(modelingExercise);
        courseRepo.save(course);
        exerciseRepo.save(modelingExercise);
        return course;
    }

    public Course addCourseWithOneReleasedModelExerciseWithKnowledge() {
        return addCourseWithOneReleasedModelExerciseWithKnowledge("Text");
    }

    public Course addCourseWithOneReleasedTextExercise() {
        return addCourseWithOneReleasedTextExercise("Text");
    }

    public <T extends Exercise> T getFirstExerciseWithType(Course course, Class<T> clazz) {
        var exercise = course.getExercises().stream().filter(ex -> ex.getClass().equals(clazz)).findFirst().get();
        return (T) exercise;
    }

    public <T extends Exercise> T getFirstExerciseWithType(Exam exam, Class<T> clazz) {
        var exercise = exam.getExerciseGroups().stream().map(ExerciseGroup::getExercises).flatMap(Collection::stream).filter(ex -> ex.getClass().equals(clazz)).findFirst().get();
        return (T) exercise;
    }

    public ProgrammingExercise addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases() {
        ProgrammingExercise programmingExercise = addCourseExamExerciseGroupWithOneProgrammingExercise();
        addTestCasesToProgrammingExercise(programmingExercise);
        return programmingExercise;
    }

    public ProgrammingExercise addCourseExamExerciseGroupWithOneProgrammingExercise(String title, String shortName) {
        ExerciseGroup exerciseGroup = addExerciseGroupWithExamAndCourse(true);
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setExerciseGroup(exerciseGroup);
        populateProgrammingExercise(programmingExercise, shortName, title, false);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

        return programmingExercise;
    }

    public ProgrammingExercise addCourseExamExerciseGroupWithOneProgrammingExercise() {
        return addCourseExamExerciseGroupWithOneProgrammingExercise("Testtitle", "TESTEXFOREXAM");
    }

    public ProgrammingExercise addProgrammingExerciseToExam(Exam exam, int exerciseGroupNumber) {
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setExerciseGroup(exam.getExerciseGroups().get(exerciseGroupNumber));
        populateProgrammingExercise(programmingExercise, "TESTEXFOREXAM", "Testtitle", false);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

        exam.getExerciseGroups().get(exerciseGroupNumber).addExercise(programmingExercise);
        examRepository.save(exam);

        return programmingExercise;
    }

    public ModelingExercise addCourseExamExerciseGroupWithOneModelingExercise(String title) {
        ExerciseGroup exerciseGroup = addExerciseGroupWithExamAndCourse(true);
        ModelingExercise classExercise = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup);
        classExercise.setTitle(title);
        classExercise = modelingExerciseRepository.save(classExercise);
        return classExercise;
    }

    public ModelingExercise addCourseExamExerciseGroupWithOneModelingExercise() {
        return addCourseExamExerciseGroupWithOneModelingExercise("ClassDiagram");
    }

    public ProgrammingSubmission createProgrammingSubmission(Participation participation, boolean buildFailed, String commitHash) {
        ProgrammingSubmission programmingSubmission = ModelFactory.generateProgrammingSubmission(true);
        programmingSubmission.setBuildFailed(buildFailed);
        programmingSubmission.type(SubmissionType.MANUAL).submissionDate(ZonedDateTime.now());
        programmingSubmission.setCommitHash(commitHash);
        programmingSubmission.setParticipation(participation);
        return submissionRepository.save(programmingSubmission);
    }

    public ProgrammingSubmission createProgrammingSubmission(Participation participation, boolean buildFailed) {
        return createProgrammingSubmission(participation, buildFailed, TestConstants.COMMIT_HASH_STRING);
    }

    public TextExercise addCourseExamExerciseGroupWithOneTextExercise(String title) {
        ExerciseGroup exerciseGroup = addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        if (title != null) {
            textExercise.setTitle(title);
        }
        return exerciseRepo.save(textExercise);
    }

    public TextExercise addCourseExamExerciseGroupWithOneTextExercise() {
        return addCourseExamExerciseGroupWithOneTextExercise(null);
    }

    public TextExercise addCourseExamWithReviewDatesExerciseGroupWithOneTextExercise() {
        ExerciseGroup exerciseGroup = addExerciseGroupWithExamWithReviewDatesAndCourse(true);
        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        return exerciseRepo.save(textExercise);
    }

    public FileUploadExercise addCourseExamExerciseGroupWithOneFileUploadExercise() {
        ExerciseGroup exerciseGroup = addExerciseGroupWithExamAndCourse(true);
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExerciseForExam("pdf", exerciseGroup);
        return exerciseRepo.save(fileUploadExercise);
    }

    public ExerciseGroup addExerciseGroupWithExamAndCourse(boolean mandatory) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        Exam exam = ModelFactory.generateExam(course);
        ExerciseGroup exerciseGroup = ModelFactory.generateExerciseGroup(mandatory, exam);

        course = courseRepo.save(course);
        exam = examRepository.save(exam);

        Optional<Course> optionalCourse = courseRepo.findById(course.getId());
        assertThat(optionalCourse).as("course can be retrieved").isPresent();
        Course courseDB = optionalCourse.get();

        Optional<Exam> optionalExam = examRepository.findById(exam.getId());
        assertThat(optionalCourse).as("exam can be retrieved").isPresent();
        Exam examDB = optionalExam.get();

        Optional<ExerciseGroup> optionalExerciseGroup = exerciseGroupRepository.findById(exerciseGroup.getId());
        assertThat(optionalExerciseGroup).as("exerciseGroup can be retrieved").isPresent();
        ExerciseGroup exerciseGroupDB = optionalExerciseGroup.get();

        assertThat(examDB.getCourse().getId()).as("exam and course are linked correctly").isEqualTo(courseDB.getId());
        assertThat(exerciseGroupDB.getExam().getId()).as("exerciseGroup and exam are linked correctly").isEqualTo(examDB.getId());

        return exerciseGroup;
    }

    public ExerciseGroup addExerciseGroupWithExamWithReviewDatesAndCourse(boolean mandatory) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        Exam exam = ModelFactory.generateExamWithStudentReviewDates(course);
        ExerciseGroup exerciseGroup = ModelFactory.generateExerciseGroup(mandatory, exam);

        course = courseRepo.save(course);
        exam = examRepository.save(exam);

        Optional<Course> optionalCourse = courseRepo.findById(course.getId());
        assertThat(optionalCourse).as("course can be retrieved").isPresent();
        Course courseDB = optionalCourse.get();

        Optional<Exam> optionalExam = examRepository.findById(exam.getId());
        assertThat(optionalCourse).as("exam can be retrieved").isPresent();
        Exam examDB = optionalExam.get();

        Optional<ExerciseGroup> optionalExerciseGroup = exerciseGroupRepository.findById(exerciseGroup.getId());
        assertThat(optionalExerciseGroup).as("exerciseGroup can be retrieved").isPresent();
        ExerciseGroup exerciseGroupDB = optionalExerciseGroup.get();

        assertThat(examDB.getCourse().getId()).as("exam and course are linked correctly").isEqualTo(courseDB.getId());
        assertThat(exerciseGroupDB.getExam().getId()).as("exerciseGroup and exam are linked correctly").isEqualTo(examDB.getId());

        return exerciseGroup;
    }

    public Course addCourseWithOneFinishedTextExercise() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        TextExercise finishedTextExercise = ModelFactory.generateTextExercise(pastTimestamp, pastTimestamp.plusHours(12), pastTimestamp.plusHours(24), course);
        finishedTextExercise.setTitle("Finished");
        course.addExercises(finishedTextExercise);
        course = courseRepo.save(course);
        exerciseRepo.save(finishedTextExercise);
        return course;
    }

    public Course addCourseWithOneFinishedTextExerciseAndSimilarSubmissions(String userPrefix, String similarSubmissionText, int studentsAmount) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        addUsers(userPrefix, studentsAmount, 1, 1, 1);

        // Add text exercise to the course
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setTitle("Finished");
        textExercise.getCategories().add("Text");
        course.addExercises(textExercise);
        courseRepo.save(course);
        exerciseRepo.save(textExercise);

        Set<StudentParticipation> participations = new HashSet<>();

        for (int i = 0; i < studentsAmount; i++) {
            User participant = getUserByLogin(userPrefix + "student" + (i + 1));
            StudentParticipation participation = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise, participant);
            participation.setParticipant(participant);
            TextSubmission submission = ModelFactory.generateTextSubmission(similarSubmissionText, Language.ENGLISH, true);
            participation = studentParticipationRepo.save(participation);
            submission.setParticipation(participation);
            submissionRepository.save(submission);

            participation.setSubmissions(Set.of(submission));
            participations.add(participation);
        }

        textExercise.participations(participations);
        exerciseRepo.save(textExercise);

        return course;
    }

    public Course addOneFinishedModelingExerciseAndSimilarSubmissionsToTheCourse(String userPrefix, String similarSubmissionModel, int studentsAmount, Course course) {
        // Add text exercise to the course
        ModelingExercise exercise = ModelFactory.generateModelingExercise(pastTimestamp, pastTimestamp, futureTimestamp, DiagramType.ClassDiagram, course);
        exercise.setTitle("finished");
        exercise.getCategories().add("Model");
        course.addExercises(exercise);

        courseRepo.save(course);
        exerciseRepo.save(exercise);

        Set<StudentParticipation> participations = new HashSet<>();

        for (int i = 0; i < studentsAmount; i++) {
            User participant = getUserByLogin(userPrefix + "student" + (i + 1));
            StudentParticipation participation = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, exercise, participant);
            participation.setParticipant(participant);
            ModelingSubmission submission = ModelFactory.generateModelingSubmission(similarSubmissionModel, true);
            participation = studentParticipationRepo.save(participation);
            submission.setParticipation(participation);
            submissionRepository.save(submission);

            participation.setSubmissions(Set.of(submission));
            participations.add(participation);
        }

        exercise.participations(participations);
        exerciseRepo.save(exercise);

        return course;
    }

    public Course addCourseWithDifferentModelingExercises() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        ModelingExercise classExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        classExercise.setTitle("ClassDiagram");
        course.addExercises(classExercise);

        ModelingExercise activityExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ActivityDiagram, course);
        activityExercise.setTitle("ActivityDiagram");
        course.addExercises(activityExercise);

        ModelingExercise objectExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ObjectDiagram, course);
        objectExercise.setTitle("ObjectDiagram");
        course.addExercises(objectExercise);

        ModelingExercise useCaseExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.UseCaseDiagram, course);
        useCaseExercise.setTitle("UseCaseDiagram");
        course.addExercises(useCaseExercise);

        ModelingExercise communicationExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.CommunicationDiagram,
                course);
        communicationExercise.setTitle("CommunicationDiagram");
        course.addExercises(communicationExercise);

        ModelingExercise componentExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ComponentDiagram, course);
        componentExercise.setTitle("ComponentDiagram");
        course.addExercises(componentExercise);

        ModelingExercise deploymentExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.DeploymentDiagram, course);
        deploymentExercise.setTitle("DeploymentDiagram");
        course.addExercises(deploymentExercise);

        ModelingExercise petriNetExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.PetriNet, course);
        petriNetExercise.setTitle("PetriNet");
        course.addExercises(petriNetExercise);

        ModelingExercise syntaxTreeExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.SyntaxTree, course);
        syntaxTreeExercise.setTitle("SyntaxTree");
        course.addExercises(syntaxTreeExercise);

        ModelingExercise flowchartExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.Flowchart, course);
        flowchartExercise.setTitle("Flowchart");
        course.addExercises(flowchartExercise);

        ModelingExercise finishedExercise = ModelFactory.generateModelingExercise(pastTimestamp, pastTimestamp, futureTimestamp, DiagramType.ClassDiagram, course);
        finishedExercise.setTitle("finished");
        course.addExercises(finishedExercise);

        course = courseRepo.save(course);
        exerciseRepo.save(classExercise);
        exerciseRepo.save(activityExercise);
        exerciseRepo.save(objectExercise);
        exerciseRepo.save(useCaseExercise);
        exerciseRepo.save(communicationExercise);
        exerciseRepo.save(componentExercise);
        exerciseRepo.save(deploymentExercise);
        exerciseRepo.save(petriNetExercise);
        exerciseRepo.save(syntaxTreeExercise);
        exerciseRepo.save(flowchartExercise);
        exerciseRepo.save(finishedExercise);
        Course storedCourse = courseRepo.findByIdWithExercisesAndLecturesElseThrow(course.getId());
        Set<Exercise> exercises = storedCourse.getExercises();
        assertThat(exercises).as("eleven exercises got stored").hasSize(11);
        assertThat(exercises).as("Contains all exercises").containsExactlyInAnyOrder(course.getExercises().toArray(new Exercise[] {}));
        return course;
    }

    public Course addCourseWithOneQuizExercise() {
        return addCourseWithOneQuizExercise("Title");
    }

    public Course addCourseWithOneQuizExercise(String title) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        QuizExercise quizExercise = createQuiz(course, futureTimestamp, futureFutureTimestamp, QuizMode.SYNCHRONIZED);
        quizExercise.setTitle(title);
        quizExercise.setDuration(120);
        assertThat(quizExercise.getQuizQuestions()).isNotEmpty();
        assertThat(quizExercise.isValid()).isTrue();
        course.addExercises(quizExercise);
        course = courseRepo.save(course);
        quizExercise = exerciseRepo.save(quizExercise);
        assertThat(courseRepo.findWithEagerExercisesById(course.getId()).getExercises()).as("course contains the exercise").contains(quizExercise);
        return course;
    }

    public Course addCourseWithOneProgrammingExercise() {
        return addCourseWithOneProgrammingExercise(false);
    }

    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis) {
        return addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, false, ProgrammingLanguage.JAVA);
    }

    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis, String title, String shortName) {
        return addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, false, ProgrammingLanguage.JAVA, title, shortName);
    }

    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis, boolean enableTestwiseCoverageAnalysis, ProgrammingLanguage programmingLanguage) {
        return addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, enableTestwiseCoverageAnalysis, programmingLanguage, "Programming", "TSTEXC");
    }

    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis, boolean enableTestwiseCoverageAnalysis, ProgrammingLanguage programmingLanguage,
            String title, String shortName) {
        var course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        var programmingExercise = addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, enableTestwiseCoverageAnalysis, programmingLanguage, title, shortName);
        assertThat(programmingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();
        return courseRepo.findByIdWithExercisesAndLecturesElseThrow(course.getId());
    }

    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis) {
        return addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, false, ProgrammingLanguage.JAVA);
    }

    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis, boolean enableTestwiseCoverageAnalysis,
            ProgrammingLanguage programmingLanguage) {
        return addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, enableTestwiseCoverageAnalysis, programmingLanguage, "Programming", "TSTEXC");
    }

    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis, boolean enableTestwiseCoverageAnalysis,
            ProgrammingLanguage programmingLanguage, String title, String shortName) {
        var programmingExercise = (ProgrammingExercise) new ProgrammingExercise().course(course);
        populateProgrammingExercise(programmingExercise, shortName, title, enableStaticCodeAnalysis, enableTestwiseCoverageAnalysis, programmingLanguage);
        programmingExercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        course.addExercises(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

        assertThat(programmingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();

        return programmingExercise;
    }

    public OnlineCourseConfiguration addOnlineCourseConfigurationToCourse(Course course) {
        OnlineCourseConfiguration onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setLtiKey("artemis_lti_key");
        onlineCourseConfiguration.setLtiSecret("fake-secret");
        onlineCourseConfiguration.setUserPrefix("prefix");
        onlineCourseConfiguration.setRegistrationId(course.getId().toString());
        onlineCourseConfiguration.setCourse(course);
        course.setOnlineCourseConfiguration(onlineCourseConfiguration);
        courseRepo.save(course);
        return onlineCourseConfiguration;
    }

    public Course addCourseWithNamedProgrammingExercise(String programmingExerciseTitle, boolean scaActive) {
        var course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        var programmingExercise = (ProgrammingExercise) new ProgrammingExercise().course(course);
        populateProgrammingExercise(programmingExercise, "TSTEXC", programmingExerciseTitle, scaActive);
        programmingExercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        course.addExercises(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

        assertThat(programmingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();

        return courseRepo.findByIdWithExercisesAndLecturesElseThrow(course.getId());
    }

    private void populateProgrammingExercise(ProgrammingExercise programmingExercise, String shortName, String title, boolean enableStaticCodeAnalysis) {
        populateProgrammingExercise(programmingExercise, shortName, title, enableStaticCodeAnalysis, false, ProgrammingLanguage.JAVA);
    }

    private void populateProgrammingExercise(ProgrammingExercise programmingExercise, String shortName, String title, boolean enableStaticCodeAnalysis,
            boolean enableTestwiseCoverageAnalysis, ProgrammingLanguage programmingLanguage) {
        programmingExercise.setProgrammingLanguage(programmingLanguage);
        programmingExercise.setShortName(shortName);
        programmingExercise.generateAndSetProjectKey();
        programmingExercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
        programmingExercise.setDueDate(ZonedDateTime.now().plusDays(2));
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(3));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(5));
        programmingExercise.setBonusPoints(0D);
        programmingExercise.setPublishBuildPlanUrl(false);
        programmingExercise.setMaxPoints(42.0);
        programmingExercise.setDifficulty(DifficultyLevel.EASY);
        programmingExercise.setMode(ExerciseMode.INDIVIDUAL);
        programmingExercise.setProblemStatement("Lorem Ipsum");
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExercise.setGradingInstructions("Lorem Ipsum");
        programmingExercise.setTitle(title);
        if (programmingLanguage == ProgrammingLanguage.JAVA) {
            programmingExercise.setProjectType(ProjectType.PLAIN_MAVEN);
        }
        else if (programmingLanguage == ProgrammingLanguage.SWIFT) {
            programmingExercise.setProjectType(ProjectType.PLAIN);
        }
        else if (programmingLanguage == ProgrammingLanguage.C) {
            programmingExercise.setProjectType(ProjectType.GCC);
        }
        else {
            programmingExercise.setProjectType(null);
        }
        programmingExercise.setAllowOnlineEditor(true);
        programmingExercise.setStaticCodeAnalysisEnabled(enableStaticCodeAnalysis);
        if (enableStaticCodeAnalysis) {
            programmingExercise.setMaxStaticCodeAnalysisPenalty(40);
        }
        programmingExercise.setTestwiseCoverageEnabled(enableTestwiseCoverageAnalysis);
        // Note: no separators are allowed for Swift package names
        if (programmingLanguage == ProgrammingLanguage.SWIFT) {
            programmingExercise.setPackageName("swiftTest");
        }
        else {
            programmingExercise.setPackageName("de.test");
        }
        programmingExercise.setCategories(new HashSet<>(Set.of("cat1", "cat2")));
        programmingExercise.setTestRepositoryUrl("http://nadnasidni.tum/scm/" + programmingExercise.getProjectKey() + "/" + programmingExercise.getProjectKey() + "-tests.git");
        programmingExercise.setShowTestNamesToStudents(false);
        programmingExercise.setBranch(DEFAULT_BRANCH);
    }

    public Course addEmptyCourse(String studentGroupName, String taGroupName, String editorGroupName, String instructorGroupName) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), studentGroupName, taGroupName, editorGroupName,
                instructorGroupName);
        courseRepo.save(course);
        assertThat(courseRepo.findById(course.getId())).as("empty course is initialized").isPresent();
        return course;
    }

    /**
     * @return An empty course
     */
    public Course addEmptyCourse() {
        return addEmptyCourse("tumuser", "tutor", "editor", "instructor");
    }

    public void addTutorialCourse() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), tutorialGroupStudents.get(), tutorialGroupTutors.get(),
                tutorialGroupEditors.get(), tutorialGroupInstructors.get());
        courseRepo.save(course);
        assertThat(courseRepo.findById(course.getId())).as("tutorial course is initialized").isPresent();
    }

    /**
     * @param title The title reflect the genre of exercise that will be added to the course
     */
    public Course addCourseInOtherInstructionGroupAndExercise(String title) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "other-instructors");
        if ("Programming".equals(title)) {
            course = courseRepo.save(course);

            var programmingExercise = (ProgrammingExercise) new ProgrammingExercise().course(course);
            populateProgrammingExercise(programmingExercise, "TSTEXC", "Programming", false);
            programmingExercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);

            programmingExercise = programmingExerciseRepository.save(programmingExercise);
            course.addExercises(programmingExercise);
            programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
            programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

            assertThat(programmingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();
        }
        else if ("Text".equals(title)) {
            TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
            textExercise.setTitle("Text");
            course.addExercises(textExercise);
            courseRepo.save(course);
            exerciseRepo.save(textExercise);
        }
        else if (title.startsWith("ClassDiagram")) {
            ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
            modelingExercise.setTitle(title);
            course.addExercises(modelingExercise);
            courseRepo.save(course);
            exerciseRepo.save(modelingExercise);
        }

        return course;
    }

    public Course addCourseWithOneProgrammingExerciseAndSpecificTestCases() {
        Course course = addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = findProgrammingExerciseWithTitle(course.getExercises(), "Programming");

        List<ProgrammingExerciseTestCase> testCases = new ArrayList<>();
        testCases.add(new ProgrammingExerciseTestCase().testName("testClass[BubbleSort]").weight(1.0).active(true).exercise(programmingExercise).bonusMultiplier(1D).bonusPoints(0D)
                .visibility(Visibility.ALWAYS));
        testCases.add(new ProgrammingExerciseTestCase().testName("testMethods[Context]").weight(2.0).active(true).exercise(programmingExercise).bonusMultiplier(1D).bonusPoints(0D)
                .visibility(Visibility.ALWAYS));
        testCases.add(new ProgrammingExerciseTestCase().testName("testMethods[Policy]").weight(3.0).active(true).exercise(programmingExercise).bonusMultiplier(1D).bonusPoints(0D)
                .visibility(Visibility.ALWAYS));
        testCaseRepository.saveAll(testCases);

        List<ProgrammingExerciseTestCase> tests = new ArrayList<>(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        assertThat(tests).as("test case is initialized").hasSize(3);

        return courseRepo.findByIdWithEagerExercisesElseThrow(course.getId());
    }

    public ProgrammingExercise addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories() {
        return addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(ProgrammingLanguage.JAVA);
    }

    public ProgrammingExercise addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(ProgrammingLanguage programmingLanguage) {
        Course course = addCourseWithOneProgrammingExercise(true, false, programmingLanguage);
        ProgrammingExercise programmingExercise = findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        addStaticCodeAnalysisCategoriesToProgrammingExercise(programmingExercise);

        return programmingExercise;
    }

    public void addStaticCodeAnalysisCategoriesToProgrammingExercise(ProgrammingExercise programmingExercise) {
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExerciseRepository.save(programmingExercise);
        var category1 = ModelFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Bad Practice", CategoryState.GRADED, 3D, 10D);
        var category2 = ModelFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Code Style", CategoryState.GRADED, 5D, 10D);
        var category3 = ModelFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Miscellaneous", CategoryState.INACTIVE, 2D, 10D);
        var category4 = ModelFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Potential Bugs", CategoryState.FEEDBACK, 5D, 20D);
        var categories = staticCodeAnalysisCategoryRepository.saveAll(List.of(category1, category2, category3, category4));
        programmingExercise.setStaticCodeAnalysisCategories(new HashSet<>(categories));
    }

    public Course addCourseWithOneProgrammingExerciseAndTestCases() {
        Course course = addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        addTestCasesToProgrammingExercise(programmingExercise);
        return courseRepo.findByIdWithExercisesAndLecturesElseThrow(course.getId());
    }

    public void addCourseWithNamedProgrammingExerciseAndTestCases(String programmingExerciseTitle) {
        addCourseWithNamedProgrammingExerciseAndTestCases(programmingExerciseTitle, false);
    }

    /**
     * @param programmingExerciseTitle The title of the programming exercise
     */
    public void addCourseWithNamedProgrammingExerciseAndTestCases(String programmingExerciseTitle, boolean scaActive) {
        Course course = addCourseWithNamedProgrammingExercise(programmingExerciseTitle, scaActive);
        ProgrammingExercise programmingExercise = findProgrammingExerciseWithTitle(course.getExercises(), programmingExerciseTitle);

        addTestCasesToProgrammingExercise(programmingExercise);

        courseRepo.findById(course.getId()).get();
    }

    public void addTestCasesToProgrammingExercise(ProgrammingExercise programmingExercise) {
        // Clean up existing test cases
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));

        List<ProgrammingExerciseTestCase> testCases = new ArrayList<>();
        testCases.add(new ProgrammingExerciseTestCase().testName("test1").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS).bonusMultiplier(1D)
                .bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("test2").weight(2.0).active(false).exercise(programmingExercise).visibility(Visibility.ALWAYS).bonusMultiplier(1D)
                .bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("test3").weight(3.0).active(true).exercise(programmingExercise).visibility(Visibility.AFTER_DUE_DATE)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCaseRepository.saveAll(testCases);

        List<ProgrammingExerciseTestCase> tests = new ArrayList<>(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        assertThat(tests).as("test case is initialized").hasSize(3);
    }

    public void addBuildPlanAndSecretToProgrammingExercise(ProgrammingExercise programmingExercise, String buildPlan) {
        buildPlanRepository.setBuildPlanForExercise(buildPlan, programmingExercise);
        programmingExercise.generateAndSetBuildPlanAccessSecret();
        programmingExerciseRepository.save(programmingExercise);

        var buildPlanOptional = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(programmingExercise.getId());
        assertThat(buildPlanOptional).isPresent();
        assertThat(buildPlanOptional.get().getBuildPlan()).as("build plan is set").isNotNull();
        assertThat(programmingExercise.getBuildPlanAccessSecret()).as("build plan access secret is set").isNotNull();
    }

    public AuxiliaryRepository addAuxiliaryRepositoryToExercise(ProgrammingExercise programmingExercise) {
        AuxiliaryRepository repository = new AuxiliaryRepository();
        repository.setName("auxrepo");
        repository.setDescription("Description");
        repository.setCheckoutDirectory("assignment/src");
        repository = auxiliaryRepositoryRepository.save(repository);
        programmingExercise.setAuxiliaryRepositories(List.of(repository));
        repository.setExercise(programmingExercise);
        programmingExerciseRepository.save(programmingExercise);
        return repository;
    }

    public void addSubmissionPolicyToExercise(SubmissionPolicy policy, ProgrammingExercise programmingExercise) {
        policy = submissionPolicyRepository.save(policy);
        programmingExercise.setSubmissionPolicy(policy);
        programmingExerciseRepository.save(programmingExercise);
    }

    public Course addCourseWithModelingAndTextExercise() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        modelingExercise.setTitle("Modeling");
        course.addExercises(modelingExercise);
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setTitle("Text");
        course.addExercises(textExercise);
        course = courseRepo.save(course);
        exerciseRepo.save(modelingExercise);
        exerciseRepo.save(textExercise);
        return course;
    }

    public Course addCourseWithModelingAndTextAndFileUploadExercise() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");

        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        modelingExercise.setTitle("Modeling");
        course.addExercises(modelingExercise);

        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setTitle("Text");
        course.addExercises(textExercise);

        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, futureFutureTimestamp, "png,pdf", course);
        fileUploadExercise.setTitle("FileUpload");
        course.addExercises(fileUploadExercise);

        course = courseRepo.save(course);
        exerciseRepo.save(modelingExercise);
        exerciseRepo.save(textExercise);
        exerciseRepo.save(fileUploadExercise);
        return course;
    }

    public List<FileUploadExercise> createFileUploadExercisesWithCourse() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        int courseSizeBefore = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now()).size();
        courseRepo.save(course);
        List<Course> courseRepoContent = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now());
        assertThat(courseRepoContent).as("a course got stored").hasSize(courseSizeBefore + 1);

        FileUploadExercise releasedFileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, "png,pdf", course);
        releasedFileUploadExercise.setTitle("released");
        FileUploadExercise finishedFileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, futureFutureTimestamp, "png,pdf", course);
        finishedFileUploadExercise.setTitle("finished");
        FileUploadExercise assessedFileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, pastTimestamp, "png,pdf", course);
        assessedFileUploadExercise.setTitle("assessed");

        var fileUploadExercises = new ArrayList<FileUploadExercise>();
        fileUploadExercises.add(releasedFileUploadExercise);
        fileUploadExercises.add(finishedFileUploadExercise);
        fileUploadExercises.add(assessedFileUploadExercise);
        return fileUploadExercises;
    }

    public Course addCourseWithThreeFileUploadExercise() {
        var fileUploadExercises = createFileUploadExercisesWithCourse();
        assertThat(fileUploadExercises).as("created three exercises").hasSize(3);
        exerciseRepo.saveAll(fileUploadExercises);
        long courseId = fileUploadExercises.get(0).getCourseViaExerciseGroupOrCourseMember().getId();
        Course course = courseRepo.findByIdWithEagerExercisesElseThrow(courseId);
        List<Exercise> exercises = exerciseRepo.findAllExercisesByCourseId(courseId).stream().toList();
        assertThat(exercises).as("three exercises got stored").hasSize(3);
        assertThat(course.getExercises()).as("course contains the exercises").containsExactlyInAnyOrder(exercises.toArray(new Exercise[] {}));
        return course;
    }

    public List<FileUploadExercise> createFourFileUploadExercisesWithCourseWithCustomUserGroupAssignment(String studentGroupName, String teachingAssistantGroupName,
            String editorGroupName, String instructorGroupName) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), studentGroupName, teachingAssistantGroupName, editorGroupName,
                instructorGroupName);
        int courseSizeBefore = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now()).size();
        courseRepo.save(course);
        List<Course> courseRepoContent = courseRepo.findAllActiveWithEagerExercisesAndLectures(ZonedDateTime.now());
        assertThat(courseRepoContent).as("a course got stored").hasSize(courseSizeBefore + 1);

        FileUploadExercise releasedFileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, "png,pdf", course);
        releasedFileUploadExercise.setTitle("released");
        FileUploadExercise finishedFileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, futureFutureTimestamp, "png,pdf", course);
        finishedFileUploadExercise.setTitle("finished");
        FileUploadExercise assessedFileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, pastTimestamp, "png,pdf", course);
        assessedFileUploadExercise.setTitle("assessed");
        FileUploadExercise noDueDateFileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, null, pastTimestamp, "png,pdf", course);
        noDueDateFileUploadExercise.setTitle("noDueDate");

        var fileUploadExercises = new ArrayList<FileUploadExercise>();
        fileUploadExercises.add(releasedFileUploadExercise);
        fileUploadExercises.add(finishedFileUploadExercise);
        fileUploadExercises.add(assessedFileUploadExercise);
        fileUploadExercises.add(noDueDateFileUploadExercise);
        return fileUploadExercises;
    }

    public Course addCourseWithFourFileUploadExercise() {
        var fileUploadExercises = createFourFileUploadExercisesWithCourseWithCustomUserGroupAssignment("tumuser", "tutor", "editor", "instructor");
        return addFileUploadExercisesToCourse(fileUploadExercises);
    }

    public Course addCourseWithFourFileUploadExercisesAndCustomUserGroups(String studentGroupName, String teachingAssistantGroupName, String editorGroupName,
            String instructorGroupName) {
        var fileUploadExercises = createFourFileUploadExercisesWithCourseWithCustomUserGroupAssignment(studentGroupName, teachingAssistantGroupName, editorGroupName,
                instructorGroupName);
        return addFileUploadExercisesToCourse(fileUploadExercises);
    }

    private Course addFileUploadExercisesToCourse(List<FileUploadExercise> fileUploadExercises) {
        assertThat(fileUploadExercises).as("created four exercises").hasSize(4);
        exerciseRepo.saveAll(fileUploadExercises);
        long courseId = fileUploadExercises.get(0).getCourseViaExerciseGroupOrCourseMember().getId();
        Course course = courseRepo.findByIdWithEagerExercisesElseThrow(courseId);
        List<Exercise> exercises = exerciseRepo.findAllExercisesByCourseId(courseId).stream().toList();
        assertThat(exercises).as("four exercises got stored").hasSize(4);
        assertThat(course.getExercises()).as("course contains the exercises").containsExactlyInAnyOrder(exercises.toArray(new Exercise[] {}));
        return course;
    }

    public Course addCourseWithFileUploadExercise() {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        FileUploadExercise assessedFileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp, pastTimestamp, "png,pdf", course);
        assessedFileUploadExercise.setTitle("assessed");
        course.addExercises(assessedFileUploadExercise);
        courseRepo.save(course);
        exerciseRepo.save(assessedFileUploadExercise);
        return course;
    }

    /**
     * Generates a course with one specific exercise, and an arbitrare amount of submissions.
     *
     * @param exerciseType        - the type of exercise which should be generated: programming, file-pload or text
     * @param numberOfSubmissions - the amount of submissions which should be generated for an exercise
     * @return a course with an exercise with submissions
     */
    public Course addCourseWithOneExerciseAndSubmissions(String userPrefix, String exerciseType, int numberOfSubmissions) {
        return addCourseWithOneExerciseAndSubmissions(userPrefix, exerciseType, numberOfSubmissions, Optional.empty());
    }

    /**
     * Generates a course with one specific exercise, and an arbitrare amount of submissions.
     *
     * @param exerciseType             - the type of exercise which should be generated: modeling, programming, file-pload or text
     * @param numberOfSubmissions      - the amount of submissions which should be generated for an exercise
     * @param modelForModelingExercise - the model string for a modeling exercise
     * @return a course with an exercise with submissions
     */
    public Course addCourseWithOneExerciseAndSubmissions(String userPrefix, String exerciseType, int numberOfSubmissions, Optional<String> modelForModelingExercise) {
        Course course;
        Exercise exercise;
        switch (exerciseType) {
            case "modeling" -> {
                course = addCourseWithOneModelingExercise();
                exercise = exerciseRepo.findAllExercisesByCourseId(course.getId()).iterator().next();
                for (int j = 1; j <= numberOfSubmissions; j++) {
                    StudentParticipation participation = createAndSaveParticipationForExercise(exercise, userPrefix + "student" + j);
                    assertThat(modelForModelingExercise).isNotEmpty();
                    ModelingSubmission submission = ModelFactory.generateModelingSubmission(modelForModelingExercise.get(), true);
                    var user = getUserByLogin(userPrefix + "student" + j);
                    modelSubmissionService.handleModelingSubmission(submission, (ModelingExercise) exercise, user);
                    studentParticipationRepo.save(participation);
                }
                return course;
            }
            case "programming" -> {
                course = addCourseWithOneProgrammingExercise();
                exercise = exerciseRepo.findAllExercisesByCourseId(course.getId()).iterator().next();
                for (int j = 1; j <= numberOfSubmissions; j++) {
                    ProgrammingSubmission submission = new ProgrammingSubmission();
                    addProgrammingSubmission((ProgrammingExercise) exercise, submission, userPrefix + "student" + j);
                }
                return course;
            }
            case "text" -> {
                course = addCourseWithOneFinishedTextExercise();
                exercise = exerciseRepo.findAllExercisesByCourseId(course.getId()).iterator().next();
                for (int j = 1; j <= numberOfSubmissions; j++) {
                    TextSubmission textSubmission = ModelFactory.generateTextSubmission("Text" + j + j, null, true);
                    saveTextSubmission((TextExercise) exercise, textSubmission, userPrefix + "student" + j);
                }
                return course;
            }
            case "file-upload" -> {
                course = addCourseWithFileUploadExercise();
                exercise = exerciseRepo.findAllExercisesByCourseId(course.getId()).iterator().next();
                for (int j = 1; j <= numberOfSubmissions; j++) {
                    FileUploadSubmission submission = ModelFactory.generateFileUploadSubmissionWithFile(true, "path/to/file.pdf");
                    saveFileUploadSubmission((FileUploadExercise) exercise, submission, userPrefix + "student" + j);
                }
                return course;
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * Adds an automatic assessment to all submissions of an exercise
     *
     * @param exercise - the exercise of which the submissions are assessed
     */
    public void addAutomaticAssessmentToExercise(Exercise exercise) {
        var participations = studentParticipationRepo.findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessor(exercise.getId(), false);
        participations.forEach(participation -> {
            Submission submission = submissionRepository.findAllByParticipationId(participation.getId()).get(0);
            submission = submissionRepository.findOneWithEagerResultAndFeedback(submission.getId());
            participation = studentParticipationRepo.findWithEagerResultsById(participation.getId()).orElseThrow();
            Result result = generateResult(submission, null);
            result.setAssessmentType(AssessmentType.AUTOMATIC);
            submission.addResult(result);
            participation.addResult(result);
            studentParticipationRepo.save(participation);
            submissionRepository.save(submission);
        });
    }

    /**
     * Adds a result to all submissions of an exercise
     *
     * @param exercise - the exercise of which the submissions are assessed
     * @param assessor - the assessor which is set for the results of the submission
     */
    public void addAssessmentToExercise(Exercise exercise, User assessor) {
        var participations = studentParticipationRepo.findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessor(exercise.getId(), false);
        participations.forEach(participation -> {
            Submission submission = submissionRepository.findAllByParticipationId(participation.getId()).get(0);
            submission = submissionRepository.findOneWithEagerResultAndFeedback(submission.getId());
            participation = studentParticipationRepo.findWithEagerResultsById(participation.getId()).orElseThrow();
            Result result = generateResult(submission, assessor);
            submission.addResult(result);
            participation.addResult(result);
            studentParticipationRepo.save(participation);
            submissionRepository.save(submission);
        });
    }

    public List<Submission> getAllSubmissionsOfExercise(Exercise exercise) {
        var participations = studentParticipationRepo.findByExerciseId(exercise.getId());
        var allSubmissions = new ArrayList<Submission>();
        participations.forEach(participation -> {
            Submission submission = submissionRepository.findAllByParticipationId(participation.getId()).get(0);
            allSubmissions.add(submissionRepository.findWithEagerResultAndFeedbackById(submission.getId()).orElseThrow());
        });
        return allSubmissions;
    }

    /**
     * With this method we can generate a course. We can specify the number of exercises. To not only test one type, this method generates modeling, file-upload and text
     * exercises in a cyclic manner.
     *
     * @param numberOfExercises             number of generated exercises. E.g. if you set it to 4, 2 modeling exercises, one text and one file-upload exercise will be generated.
     *                                          (thats why there is the %3 check)
     * @param numberOfSubmissionPerExercise for each exercise this number of submissions will be generated. E.g. if you have 2 exercises, and set this to 4, in total 8
     *                                          submissions will be created.
     * @param numberOfAssessments           generates the assessments for a submission of an exercise. Example from abobe, 2 exrecises, 4 submissions each. If you set
     *                                          numberOfAssessments to 2, for each exercise 2 assessmetns will be created. In total there will be 4 assessments then. (by two
     *                                          different tutors, as each exercise is assessed by an individual tutor. There are 4 tutors that create assessments)
     * @param numberOfComplaints            generates the complaints for assessments, in the same way as results are created.
     * @param typeComplaint                 true: complaintType==COMPLAINT | false: complaintType==MORE_FEEDBACK
     * @param numberComplaintResponses      generates responses for the complaint/feedback request (as above)
     * @param validModel                    model for the modeling submission
     * @return - the generated course
     */
    public Course addCourseWithExercisesAndSubmissions(String userPrefix, String suffix, int numberOfExercises, int numberOfSubmissionPerExercise, int numberOfAssessments,
            int numberOfComplaints, boolean typeComplaint, int numberComplaintResponses, String validModel) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), userPrefix + "student" + suffix, userPrefix + "tutor" + suffix,
                userPrefix + "editor" + suffix, userPrefix + "instructor" + suffix);
        var tutors = userRepo.getTutors(course).stream().sorted(Comparator.comparing(User::getId)).toList();
        for (int i = 0; i < numberOfExercises; i++) {
            var currentUser = tutors.get(i % 4);

            if ((i % 3) == 0) {
                ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, pastTimestamp.plusHours(1), futureTimestamp, DiagramType.ClassDiagram,
                        course);
                modelingExercise.setTitle("Modeling" + i);
                course.addExercises(modelingExercise);
                course = courseRepo.save(course);
                exerciseRepo.save(modelingExercise);
                for (int j = 1; j <= numberOfSubmissionPerExercise; j++) {
                    StudentParticipation participation = createAndSaveParticipationForExercise(modelingExercise, userPrefix + "student" + j);
                    ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
                    var user = getUserByLogin(userPrefix + "student" + j);
                    modelSubmissionService.handleModelingSubmission(submission, modelingExercise, user);
                    studentParticipationRepo.save(participation);
                    if (numberOfAssessments >= j) {
                        Result result = generateResult(submission, currentUser);
                        submission.addResult(result);
                        participation.addResult(result);
                        studentParticipationRepo.save(participation);
                        modelingSubmissionRepo.save(submission);
                        generateComplaintAndResponses(userPrefix, j, numberOfComplaints, numberComplaintResponses, typeComplaint, result, currentUser);
                    }
                }

            }
            else if ((i % 3) == 1) {
                TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, pastTimestamp.plusHours(1), futureTimestamp, course);
                textExercise.setTitle("Text" + i);
                course.addExercises(textExercise);
                course = courseRepo.save(course);
                exerciseRepo.save(textExercise);
                for (int j = 1; j <= numberOfSubmissionPerExercise; j++) {
                    TextSubmission submission = ModelFactory.generateTextSubmission("submissionText", Language.ENGLISH, true);
                    submission = saveTextSubmission(textExercise, submission, userPrefix + "student" + j);
                    if (numberOfAssessments >= j) {
                        Result result = generateResult(submission, currentUser);
                        submission.addResult(result);
                        saveResultInParticipation(submission, result);
                        textSubmissionRepo.save(submission);
                        generateComplaintAndResponses(userPrefix, j, numberOfComplaints, numberComplaintResponses, typeComplaint, result, currentUser);
                    }
                }
            }
            else { // i.e. (i % 3) == 2
                FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, pastTimestamp.plusHours(1), futureTimestamp, "png,pdf", course);
                fileUploadExercise.setTitle("FileUpload" + i);
                course.addExercises(fileUploadExercise);
                course = courseRepo.save(course);
                exerciseRepo.save(fileUploadExercise);
                for (int j = 1; j <= numberOfSubmissionPerExercise; j++) {
                    FileUploadSubmission submission = ModelFactory.generateFileUploadSubmissionWithFile(true, "path/to/file.pdf");
                    saveFileUploadSubmission(fileUploadExercise, submission, userPrefix + "student" + j);
                    if (numberOfAssessments >= j) {
                        Result result = generateResult(submission, currentUser);
                        saveResultInParticipation(submission, result);
                        fileUploadSubmissionRepo.save(submission);
                        generateComplaintAndResponses(userPrefix, j, numberOfComplaints, numberComplaintResponses, typeComplaint, result, currentUser);
                    }
                }
            }
        }
        course = courseRepo.save(course);
        return course;
    }

    private void saveResultInParticipation(Submission submission, Result result) {
        submission.addResult(result);
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();
        participation.addResult(result);
        studentParticipationRepo.save(participation);
    }

    public Result generateResult(Submission submission, User assessor) {
        Result result = new Result();
        result = resultRepo.save(result);
        result.setSubmission(submission);
        result.completionDate(pastTimestamp);
        result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        result.setAssessor(assessor);
        result.setRated(true);
        return result;
    }

    private void generateComplaintAndResponses(String userPrefix, int j, int numberOfComplaints, int numberComplaintResponses, boolean typeComplaint, Result result,
            User currentUser) {
        result = resultRepo.save(result);
        if (numberOfComplaints >= j) {
            Complaint complaint = typeComplaint ? new Complaint().complaintType(ComplaintType.COMPLAINT) : new Complaint().complaintType(ComplaintType.MORE_FEEDBACK);
            complaint.setResult(result);
            complaint = complaintRepo.save(complaint);
            if (numberComplaintResponses >= j) {
                ComplaintResponse complaintResponse = createInitialEmptyResponse(typeComplaint ? userPrefix + "tutor5" : currentUser.getLogin(), complaint);
                complaintResponse.getComplaint().setAccepted(true);
                complaintResponse.setResponseText(typeComplaint ? "Accepted" : "SomeMoreFeedback");
                complaintResponseRepo.save(complaintResponse);
                complaint.setComplaintResponse(complaintResponse);
                complaintRepo.save(complaint);
            }
        }
    }

    /**
     * Stores for the given model a submission of the user and initiates the corresponding Result
     *
     * @param exercise exercise the submission belongs to
     * @param model    ModelingSubmission json as string contained in the submission
     * @param login    of the user the submission belongs to
     * @return submission stored in the modelingSubmissionRepository
     */
    public ModelingSubmission addModelingSubmissionWithEmptyResult(ModelingExercise exercise, String model, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(model, true);
        var user = getUserByLogin(login);
        submission = modelSubmissionService.handleModelingSubmission(submission, exercise, user);
        Result result = new Result();
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        participation.addResult(result);
        studentParticipationRepo.save(participation);
        modelingSubmissionRepo.save(submission);
        resultRepo.save(result);
        return submission;
    }

    public ModelingSubmission addModelingSubmission(ModelingExercise exercise, ModelingSubmission submission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    public ModelingSubmission addModelingTeamSubmission(ModelingExercise exercise, ModelingSubmission submission, Team team) {
        StudentParticipation participation = addTeamParticipationForExercise(exercise, team.getId());
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    public ProgrammingSubmission addProgrammingSubmission(ProgrammingExercise exercise, ProgrammingSubmission submission, String login) {
        StudentParticipation participation = addStudentParticipationForProgrammingExercise(exercise, login);
        submission.setParticipation(participation);
        submission = programmingSubmissionRepo.save(submission);
        return submission;
    }

    /**
     * Add a submission with a result to the given programming exercise. The submission will be assigned to the corresponding participation of the given login (if exists or
     * create a new participation).
     * The method will make sure that all necessary entities are connected.
     *
     * @param exercise   for which to create the submission/participation/result combination.
     * @param submission to use for adding to the exercise/participation/result.
     * @param login      of the user to identify the corresponding student participation.
     */
    public void addProgrammingSubmissionWithResult(ProgrammingExercise exercise, ProgrammingSubmission submission, String login) {
        StudentParticipation participation = addStudentParticipationForProgrammingExercise(exercise, login);
        submission = programmingSubmissionRepo.save(submission);
        Result result = resultRepo.save(new Result().participation(participation));
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submission.addResult(result);
        submission = programmingSubmissionRepo.save(submission);
        result.setSubmission(submission);
        result = resultRepo.save(result);
        participation.addResult(result);
        studentParticipationRepo.save(participation);
    }

    public ProgrammingSubmission addProgrammingSubmissionWithResultAndAssessor(ProgrammingExercise exercise, ProgrammingSubmission submission, String login, String assessorLogin,
            AssessmentType assessmentType, boolean hasCompletionDate) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        Result result = new Result();
        result.setAssessor(getUserByLogin(assessorLogin));
        result.setAssessmentType(assessmentType);
        result.setScore(50D);
        if (hasCompletionDate) {
            result.setCompletionDate(ZonedDateTime.now());
        }

        studentParticipationRepo.save(participation);
        programmingSubmissionRepo.save(submission);

        submission.setParticipation(participation);
        result.setParticipation(participation);

        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        // Manual results are always rated
        if (assessmentType == AssessmentType.SEMI_AUTOMATIC) {
            result.rated(true);
        }
        submission = programmingSubmissionRepo.save(submission);
        return submission;
    }

    public ProgrammingSubmission addProgrammingSubmissionToResultAndParticipation(Result result, StudentParticipation participation, String commitHash) {
        ProgrammingSubmission submission = createProgrammingSubmission(participation, false);
        submission.addResult(result);
        submission.setCommitHash(commitHash);
        resultRepo.save(result);
        result.setSubmission(submission);
        participation.addSubmission(submission);
        studentParticipationRepo.save(participation);
        return submissionRepository.save(submission);
    }

    public Submission addSubmission(Exercise exercise, Submission submission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submissionRepository.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    public Submission addSubmission(StudentParticipation participation, Submission submission) {
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submissionRepository.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    public ModelingSubmission addModelingSubmissionWithResultAndAssessor(ModelingExercise exercise, ModelingSubmission submission, String login, String assessorLogin) {

        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission = modelingSubmissionRepo.save(submission);

        Result result = new Result();

        result.setAssessor(getUserByLogin(assessorLogin));
        result.setAssessmentType(AssessmentType.MANUAL);
        result = resultRepo.save(result);
        submission = modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        result = resultRepo.save(result);

        result.setSubmission(submission);
        submission.setParticipation(participation);
        submission.addResult(result);
        submission.getParticipation().addResult(result);
        submission = modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    public Submission addModelingSubmissionWithFinishedResultAndAssessor(ModelingExercise exercise, ModelingSubmission submission, String login, String assessorLogin) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        return addSubmissionWithFinishedResultsWithAssessor(participation, submission, assessorLogin);
    }

    public Submission addSubmissionWithTwoFinishedResultsWithAssessor(Exercise exercise, Submission submission, String login, String assessorLogin) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        submission = addSubmissionWithFinishedResultsWithAssessor(participation, submission, assessorLogin);
        submission = addSubmissionWithFinishedResultsWithAssessor(participation, submission, assessorLogin);
        return submission;
    }

    public Submission addSubmissionWithFinishedResultsWithAssessor(StudentParticipation participation, Submission submission, String assessorLogin) {
        Result result = new Result();
        result.setAssessor(getUserByLogin(assessorLogin));
        result.setCompletionDate(ZonedDateTime.now());
        result.setSubmission(submission);
        submission.setParticipation(participation);
        submission.addResult(result);
        submission.getParticipation().addResult(result);
        submission = saveSubmissionToRepo(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    private Submission saveSubmissionToRepo(Submission submission) {
        if (submission instanceof ModelingSubmission) {
            return modelingSubmissionRepo.save((ModelingSubmission) submission);
        }
        else if (submission instanceof TextSubmission) {
            return textSubmissionRepo.save((TextSubmission) submission);
        }
        else if (submission instanceof ProgrammingSubmission) {
            return programmingSubmissionRepo.save((ProgrammingSubmission) submission);
        }
        return null;
    }

    public FileUploadSubmission addFileUploadSubmission(FileUploadExercise fileUploadExercise, FileUploadSubmission fileUploadSubmission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(fileUploadExercise, login);
        participation.addSubmission(fileUploadSubmission);
        fileUploadSubmission.setParticipation(participation);
        fileUploadSubmissionRepo.save(fileUploadSubmission);
        studentParticipationRepo.save(participation);
        return fileUploadSubmission;
    }

    public FileUploadSubmission saveFileUploadSubmissionWithResultAndAssessorFeedback(FileUploadExercise exercise, FileUploadSubmission fileUploadSubmission, String login,
            String assessorLogin, List<Feedback> feedbacks) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);

        submissionRepository.save(fileUploadSubmission);

        participation.addSubmission(fileUploadSubmission);
        Result result = new Result();
        result.setAssessor(getUserByLogin(assessorLogin));
        result.setScore(100D);
        result.setParticipation(participation);
        if (exercise.getReleaseDate() != null) {
            result.setCompletionDate(exercise.getReleaseDate());
        }
        else { // exam exercises do not have a release date
            result.setCompletionDate(ZonedDateTime.now());
        }
        result.setFeedbacks(feedbacks);
        result = resultRepo.save(result);
        for (Feedback feedback : feedbacks) {
            feedback.setResult(result);
        }
        result = resultRepo.save(result);
        result.setSubmission(fileUploadSubmission);
        fileUploadSubmission.setParticipation(participation);
        fileUploadSubmission.addResult(result);
        fileUploadSubmission.getParticipation().addResult(result);
        fileUploadSubmission = fileUploadSubmissionRepo.save(fileUploadSubmission);
        studentParticipationRepo.save(participation);
        return fileUploadSubmission;
    }

    public FileUploadSubmission saveFileUploadSubmissionWithResultAndAssessor(FileUploadExercise fileUploadExercise, FileUploadSubmission fileUploadSubmission, String login,
            String assessorLogin) {
        return saveFileUploadSubmissionWithResultAndAssessorFeedback(fileUploadExercise, fileUploadSubmission, login, assessorLogin, new ArrayList<>());
    }

    public FileUploadSubmission saveFileUploadSubmission(FileUploadExercise exercise, FileUploadSubmission submission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        fileUploadSubmissionRepo.save(submission);
        return submission;
    }

    public QuizSubmission saveQuizSubmission(QuizExercise exercise, QuizSubmission submission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submission = quizSubmissionRepository.save(submission);
        return submission;
    }

    public TextSubmission saveTextSubmission(TextExercise exercise, TextSubmission submission, String login) {
        StudentParticipation participation = createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submission = textSubmissionRepo.save(submission);
        return submission;
    }

    private TextSubmission saveTextSubmissionWithResultAndAssessor(TextExercise exercise, TextSubmission submission, String studentLogin, Long teamId, String assessorLogin) {
        StudentParticipation participation = Optional.ofNullable(studentLogin).map(login -> createAndSaveParticipationForExercise(exercise, login))
                .orElseGet(() -> addTeamParticipationForExercise(exercise, teamId));

        submissionRepository.save(submission);

        participation.addSubmission(submission);
        Result result = new Result();
        result.setAssessor(getUserByLogin(assessorLogin));
        result.setScore(100D);
        if (exercise.getReleaseDate() != null) {
            result.setCompletionDate(exercise.getReleaseDate());
        }
        else { // exam exercises do not have a release date
            result.setCompletionDate(ZonedDateTime.now());
        }
        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.setParticipation(participation);
        submission.addResult(result);
        submission.getParticipation().addResult(result);
        submission = textSubmissionRepo.save(submission);
        resultRepo.save(result);
        studentParticipationRepo.save(participation);

        submission = textSubmissionRepo.save(submission);
        return submission;
    }

    public TextSubmission saveTextSubmissionWithResultAndAssessor(TextExercise exercise, TextSubmission submission, String login, String assessorLogin) {
        return saveTextSubmissionWithResultAndAssessor(exercise, submission, login, null, assessorLogin);
    }

    public void saveTextSubmissionWithResultAndAssessor(TextExercise exercise, TextSubmission submission, long teamId, String assessorLogin) {
        saveTextSubmissionWithResultAndAssessor(exercise, submission, null, teamId, assessorLogin);
    }

    public TextSubmission addTextSubmissionWithResultAndAssessorAndFeedbacks(TextExercise exercise, TextSubmission submission, String studentLogin, String assessorLogin,
            List<Feedback> feedbacks) {
        submission = saveTextSubmissionWithResultAndAssessor(exercise, submission, studentLogin, null, assessorLogin);
        Result result = submission.getLatestResult();
        for (Feedback feedback : feedbacks) {
            // Important note to prevent 'JpaSystemException: null index column for collection':
            // 1) save the child entity (without connection to the parent entity) and make sure to re-assign the return value
            feedback = feedbackRepo.save(feedback);
            // this also invokes feedback.setResult(result)
            // Important note to prevent 'JpaSystemException: null index column for collection':
            // 2) connect child and parent entity
            result.addFeedback(feedback);
        }
        // this automatically saves the feedback because of the CascadeType.All annotation
        // Important note to prevent 'JpaSystemException: null index column for collection':
        // 3) save the parent entity and make sure to re-assign the return value
        resultRepo.save(result);

        return submission;
    }

    public TextSubmission addAndSaveTextBlocksToTextSubmission(Set<TextBlock> blocks, TextSubmission submission) {
        blocks.forEach(block -> {
            block.setSubmission(submission);
            block.setTextFromSubmission();
            block.computeId();
        });
        submission.setBlocks(blocks);
        textBlockRepo.saveAll(blocks);
        return textSubmissionRepo.save(submission);
    }

    public ModelingSubmission addModelingSubmissionFromResources(ModelingExercise exercise, String path, String login) throws Exception {
        String model = FileUtils.loadFileFromResources(path);
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(model, true);
        submission = addModelingSubmission(exercise, submission, login);
        checkModelingSubmissionCorrectlyStored(submission.getId(), model);
        return submission;
    }

    public void checkModelingSubmissionCorrectlyStored(Long submissionId, String sentModel) {
        Optional<ModelingSubmission> modelingSubmission = modelingSubmissionRepo.findById(submissionId);
        assertThat(modelingSubmission).as("submission correctly stored").isPresent();
        checkModelsAreEqual(modelingSubmission.get().getModel(), sentModel);
    }

    public void checkModelsAreEqual(String storedModel, String sentModel) {
        JsonObject sentModelObject = parseString(sentModel).getAsJsonObject();
        JsonObject storedModelObject = parseString(storedModel).getAsJsonObject();
        assertThat(storedModelObject).as("model correctly stored").isEqualTo(sentModelObject);
    }

    public Result addModelingAssessmentForSubmission(ModelingExercise exercise, ModelingSubmission submission, String path, String login, boolean submit) throws Exception {
        List<Feedback> feedbackList = loadAssessmentFomResources(path);
        Result result = assessmentService.saveManualAssessment(submission, feedbackList, null);
        result.setParticipation(submission.getParticipation().results(null));
        result.setAssessor(getUserByLogin(login));
        resultRepo.save(result);
        if (submit) {
            assessmentService.submitManualAssessment(result.getId(), exercise, submission.getSubmissionDate());
        }
        return resultRepo.findWithEagerSubmissionAndFeedbackAndAssessorById(result.getId()).get();
    }

    public Result addModelingAssessmentForSubmission(ModelingExercise exercise, ModelingSubmission submission, String login, boolean submit) {
        Feedback feedback1 = feedbackRepo.save(new Feedback().detailText("detail1"));
        Feedback feedback2 = feedbackRepo.save(new Feedback().detailText("detail2"));
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(feedback1);
        feedbacks.add(feedback2);

        Result result = assessmentService.saveManualAssessment(submission, feedbacks, null);
        result.setParticipation(submission.getParticipation().results(null));
        result.setAssessor(getUserByLogin(login));
        resultRepo.save(result);
        if (submit) {
            assessmentService.submitManualAssessment(result.getId(), exercise, submission.getSubmissionDate());
        }
        return resultRepo.findWithEagerSubmissionAndFeedbackAndAssessorById(result.getId()).get();
    }

    public ExampleSubmission addExampleSubmission(ExampleSubmission exampleSubmission) {
        Submission submission;
        if (exampleSubmission.getSubmission() instanceof ModelingSubmission) {
            submission = modelingSubmissionRepo.save((ModelingSubmission) exampleSubmission.getSubmission());
        }
        else {
            submission = textSubmissionRepo.save((TextSubmission) exampleSubmission.getSubmission());
        }
        exampleSubmission.setSubmission(submission);
        return exampleSubmissionRepo.save(exampleSubmission);
    }

    public ComplaintResponse createInitialEmptyResponse(String loginOfTutor, Complaint complaint) {
        ComplaintResponse complaintResponse = new ComplaintResponse();
        complaintResponse.setComplaint(complaint);
        User tutor = userRepo.findOneByLogin(loginOfTutor).get();
        complaintResponse.setReviewer(tutor);
        complaintResponse = complaintResponseRepo.saveAndFlush(complaintResponse);
        return complaintResponse;
    }

    public List<Feedback> loadAssessmentFomResources(String path) throws Exception {
        String fileContent = FileUtils.loadFileFromResources(path);
        return mapper.readValue(fileContent, mapper.getTypeFactory().constructCollectionType(List.class, Feedback.class));
    }

    /**
     * Gets a user from the database using the provided login but without the authorities.
     * <p>
     * Note: Jackson sometimes fails to deserialize the authorities leading to flaky server tests. The specific
     * circumstances when this happens in still unknown.
     *
     * @param login login to find user with
     * @return user with the provided logih
     */
    public User getUserByLoginWithoutAuthorities(String login) {
        return userRepo.findOneByLogin(login).orElseThrow(() -> new IllegalArgumentException("Provided login " + login + " does not exist in database"));
    }

    public User getUserByLogin(String login) {
        // we convert to lowercase for convenience, because logins have to be lower case
        return userRepo.findOneWithGroupsAndAuthoritiesByLogin(login.toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Provided login " + login + " does not exist in database"));
    }

    public boolean userExistsWithLogin(String login) {
        return userRepo.findOneByLogin(login).isPresent();
    }

    public void updateExerciseDueDate(long exerciseId, ZonedDateTime newDueDate) {
        Exercise exercise = exerciseRepo.findById(exerciseId).orElseThrow(() -> new IllegalArgumentException("Exercise with given ID " + exerciseId + " could not be found"));
        exercise.setDueDate(newDueDate);
        if (exercise instanceof ProgrammingExercise) {
            ((ProgrammingExercise) exercise).setBuildAndTestStudentSubmissionsAfterDueDate(newDueDate);
        }
        exerciseRepo.save(exercise);
    }

    public void updateAssessmentDueDate(long exerciseId, ZonedDateTime newDueDate) {
        Exercise exercise = exerciseRepo.findById(exerciseId).orElseThrow(() -> new IllegalArgumentException("Exercise with given ID " + exerciseId + " could not be found"));
        exercise.setAssessmentDueDate(newDueDate);
        exerciseRepo.save(exercise);
    }

    public void updateResultCompletionDate(long resultId, ZonedDateTime newCompletionDate) {
        Result result = resultRepo.findById(resultId).orElseThrow(() -> new IllegalArgumentException("Result with given ID " + resultId + " could not be found"));
        result.setCompletionDate(newCompletionDate);
        resultRepo.save(result);
    }

    public void addComplaints(String studentLogin, Participation participation, int numberOfComplaints, ComplaintType complaintType) {
        for (int i = 0; i < numberOfComplaints; i++) {
            Result dummyResult = new Result().participation(participation);
            dummyResult = resultRepo.save(dummyResult);
            Complaint complaint = new Complaint().participant(getUserByLogin(studentLogin)).result(dummyResult).complaintType(complaintType);
            complaintRepo.save(complaint);
        }
    }

    public void addComplaintToSubmission(Submission submission, String userLogin, ComplaintType type) {
        Result result = submission.getLatestResult();
        if (result != null) {
            result.hasComplaint(true);
            resultRepo.save(result);
        }
        Complaint complaint = new Complaint().participant(getUserByLogin(userLogin)).result(result).complaintType(type);
        complaintRepo.save(complaint);
    }

    public void addTeamComplaints(Team team, Participation participation, int numberOfComplaints, ComplaintType complaintType) {
        for (int i = 0; i < numberOfComplaints; i++) {
            Result dummyResult = new Result().participation(participation);
            dummyResult = resultRepo.save(dummyResult);
            Complaint complaint = new Complaint().participant(team).result(dummyResult).complaintType(complaintType);
            complaintRepo.save(complaint);
        }
    }

    public void addHintsToExercise(ProgrammingExercise exercise) {
        ExerciseHint exerciseHint1 = new ExerciseHint().content("content 1").exercise(exercise).title("title 1");
        ExerciseHint exerciseHint2 = new ExerciseHint().content("content 2").exercise(exercise).title("title 2");
        ExerciseHint exerciseHint3 = new ExerciseHint().content("content 3").exercise(exercise).title("title 3");
        exerciseHint1.setDisplayThreshold((short) 3);
        exerciseHint2.setDisplayThreshold((short) 3);
        exerciseHint3.setDisplayThreshold((short) 3);
        Set<ExerciseHint> hints = new HashSet<>();
        hints.add(exerciseHint1);
        hints.add(exerciseHint2);
        hints.add(exerciseHint3);
        exercise.setExerciseHints(hints);
        exerciseHintRepository.saveAll(hints);
        programmingExerciseRepository.save(exercise);
    }

    public void addTasksToProgrammingExercise(ProgrammingExercise programmingExercise) {
        StringBuilder problemStatement = new StringBuilder(programmingExercise.getProblemStatement());
        problemStatement.append('\n');

        var tasks = programmingExercise.getTestCases().stream().map(testCase -> {
            var task = new ProgrammingExerciseTask();
            task.setTaskName("Task for " + testCase.getTestName());
            task.setExercise(programmingExercise);
            task.setTestCases(Collections.singleton(testCase));
            testCase.setTasks(Collections.singleton(task));
            problemStatement.append("[task][").append(task.getTaskName()).append("](")
                    .append(task.getTestCases().stream().map(ProgrammingExerciseTestCase::getTestName).collect(Collectors.joining(","))).append(")\n");
            return task;
        }).toList();
        programmingExercise.setTasks(tasks);
        programmingExercise.setProblemStatement(problemStatement.toString());
        programmingExerciseTaskRepository.saveAll(tasks);
        programmingExerciseRepository.save(programmingExercise);
    }

    public void addSolutionEntriesToProgrammingExercise(ProgrammingExercise programmingExercise) {
        for (ProgrammingExerciseTestCase testCase : programmingExercise.getTestCases()) {
            var solutionEntry = new ProgrammingExerciseSolutionEntry();
            solutionEntry.setFilePath("test.txt");
            solutionEntry.setLine(1);
            solutionEntry.setCode("Line for " + testCase.getTestName());
            solutionEntry.setTestCase(testCase);

            testCase.setSolutionEntries(Collections.singleton(solutionEntry));
            solutionEntryRepository.save(solutionEntry);
        }
    }

    public void addCodeHintsToProgrammingExercise(ProgrammingExercise programmingExercise) {
        for (ProgrammingExerciseTask task : programmingExercise.getTasks()) {
            var solutionEntries = task.getTestCases().stream().flatMap(testCase -> testCase.getSolutionEntries().stream()).collect(Collectors.toSet());
            var codeHint = new CodeHint();
            codeHint.setTitle("Code Hint for " + task.getTaskName());
            codeHint.setContent("Content for " + task.getTaskName());
            codeHint.setExercise(programmingExercise);
            codeHint.setSolutionEntries(solutionEntries);
            codeHint.setProgrammingExerciseTask(task);

            programmingExercise.getExerciseHints().add(codeHint);
            codeHintRepository.save(codeHint);
            for (ProgrammingExerciseSolutionEntry solutionEntry : solutionEntries) {
                solutionEntry.setCodeHint(codeHint);
                solutionEntryRepository.save(solutionEntry);
            }
        }
    }

    public ProgrammingExercise loadProgrammingExerciseWithEagerReferences(ProgrammingExercise lazyExercise) {
        return programmingExerciseTestRepository.findOneWithEagerEverything(lazyExercise.getId());
    }

    /**
     * Generates an example submission for a given model and exercise
     *
     * @param modelOrText             given uml model for the example submission
     * @param exercise                exercise for which the example submission is created
     * @param flagAsExampleSubmission true if the submission is an example submission
     * @return created example submission
     */
    public ExampleSubmission generateExampleSubmission(String modelOrText, Exercise exercise, boolean flagAsExampleSubmission) {
        return generateExampleSubmission(modelOrText, exercise, flagAsExampleSubmission, false);
    }

    /**
     * Generates an example submission for a given model and exercise
     *
     * @param modelOrText             given uml model for the example submission
     * @param exercise                exercise for which the example submission is created
     * @param flagAsExampleSubmission true if the submission is an example submission
     * @param usedForTutorial         true if the example submission is used for tutorial
     * @return created example submission
     */
    public ExampleSubmission generateExampleSubmission(String modelOrText, Exercise exercise, boolean flagAsExampleSubmission, boolean usedForTutorial) {
        Submission submission;
        if (exercise instanceof ModelingExercise) {
            submission = ModelFactory.generateModelingSubmission(modelOrText, false);
        }
        else {
            submission = ModelFactory.generateTextSubmission(modelOrText, Language.ENGLISH, false);
            saveSubmissionToRepo(submission);
        }
        submission.setExampleSubmission(flagAsExampleSubmission);
        return ModelFactory.generateExampleSubmission(submission, exercise, usedForTutorial);
    }

    /**
     * Generates a submitted answer for a given question.
     *
     * @param question given question, the answer is for
     * @param correct  boolean whether the answer should be correct or not
     * @return created SubmittedAnswer
     */
    public SubmittedAnswer generateSubmittedAnswerFor(QuizQuestion question, boolean correct) {
        if (question instanceof MultipleChoiceQuestion) {
            var submittedAnswer = new MultipleChoiceSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                if (answerOption.isIsCorrect().equals(correct)) {
                    submittedAnswer.addSelectedOptions(answerOption);
                }
            }
            return submittedAnswer;
        }
        else if (question instanceof DragAndDropQuestion) {
            var submittedAnswer = new DragAndDropSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            DragItem dragItem1 = ((DragAndDropQuestion) question).getDragItems().get(0);
            dragItem1.setQuestion((DragAndDropQuestion) question);
            log.debug(dragItem1.toString());
            DragItem dragItem2 = ((DragAndDropQuestion) question).getDragItems().get(1);
            dragItem2.setQuestion((DragAndDropQuestion) question);
            log.debug(dragItem2.toString());
            DragItem dragItem3 = ((DragAndDropQuestion) question).getDragItems().get(2);
            dragItem3.setQuestion((DragAndDropQuestion) question);
            log.debug(dragItem3.toString());

            DropLocation dropLocation1 = ((DragAndDropQuestion) question).getDropLocations().get(0);
            dropLocation1.setQuestion((DragAndDropQuestion) question);
            log.debug(dropLocation1.toString());
            DropLocation dropLocation2 = ((DragAndDropQuestion) question).getDropLocations().get(1);
            dropLocation2.setQuestion((DragAndDropQuestion) question);
            log.debug(dropLocation2.toString());
            DropLocation dropLocation3 = ((DragAndDropQuestion) question).getDropLocations().get(2);
            dropLocation3.setQuestion((DragAndDropQuestion) question);
            log.debug(dropLocation3.toString());

            if (correct) {
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation2));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation3));
            }
            else {
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation3));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation2));
                submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation1));
            }

            return submittedAnswer;
        }
        else if (question instanceof ShortAnswerQuestion) {
            var submittedAnswer = new ShortAnswerSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var spot : ((ShortAnswerQuestion) question).getSpots()) {
                ShortAnswerSubmittedText submittedText = new ShortAnswerSubmittedText();
                submittedText.setSpot(spot);
                var correctText = ((ShortAnswerQuestion) question).getCorrectSolutionForSpot(spot).iterator().next().getText();
                if (correct) {
                    submittedText.setText(correctText);
                }
                else {
                    submittedText.setText(correctText.toUpperCase());
                }
                submittedAnswer.addSubmittedTexts(submittedText);
                // also invoke remove once
                submittedAnswer.removeSubmittedTexts(submittedText);
                submittedAnswer.addSubmittedTexts(submittedText);
            }
            return submittedAnswer;
        }
        return null;
    }

    public SubmittedAnswer generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(QuizQuestion question) {
        if (question instanceof MultipleChoiceQuestion) {
            var submittedAnswer = new MultipleChoiceSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                submittedAnswer.addSelectedOptions(answerOption);
            }
            return submittedAnswer;
        }
        else if (question instanceof DragAndDropQuestion) {
            var submittedAnswer = new DragAndDropSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            DragItem dragItem1 = ((DragAndDropQuestion) question).getDragItems().get(0);
            dragItem1.setQuestion((DragAndDropQuestion) question);
            log.debug(dragItem1.toString());
            DragItem dragItem2 = ((DragAndDropQuestion) question).getDragItems().get(1);
            dragItem2.setQuestion((DragAndDropQuestion) question);
            log.debug(dragItem2.toString());
            DragItem dragItem3 = ((DragAndDropQuestion) question).getDragItems().get(2);
            dragItem3.setQuestion((DragAndDropQuestion) question);
            log.debug(dragItem3.toString());

            DropLocation dropLocation1 = ((DragAndDropQuestion) question).getDropLocations().get(0);
            dropLocation1.setQuestion((DragAndDropQuestion) question);
            log.debug(dropLocation1.toString());
            DropLocation dropLocation2 = ((DragAndDropQuestion) question).getDropLocations().get(1);
            dropLocation2.setQuestion((DragAndDropQuestion) question);
            log.debug(dropLocation2.toString());
            DropLocation dropLocation3 = ((DragAndDropQuestion) question).getDropLocations().get(2);
            dropLocation3.setQuestion((DragAndDropQuestion) question);
            log.debug(dropLocation3.toString());

            submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1));
            submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation3));
            submittedAnswer.addMappings(new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation2));

            return submittedAnswer;
        }
        else if (question instanceof ShortAnswerQuestion) {
            var submittedAnswer = new ShortAnswerSubmittedAnswer();
            submittedAnswer.setQuizQuestion(question);

            for (var spot : ((ShortAnswerQuestion) question).getSpots()) {
                ShortAnswerSubmittedText submittedText = new ShortAnswerSubmittedText();
                submittedText.setSpot(spot);
                var correctText = ((ShortAnswerQuestion) question).getCorrectSolutionForSpot(spot).iterator().next().getText();
                if (spot.getSpotNr() == 2) {
                    submittedText.setText(correctText);
                }
                else {
                    submittedText.setText("wrong submitted text");
                }
                submittedAnswer.addSubmittedTexts(submittedText);
                // also invoke remove once
                submittedAnswer.removeSubmittedTexts(submittedText);
                submittedAnswer.addSubmittedTexts(submittedText);
            }
            return submittedAnswer;
        }
        return null;
    }

    @NotNull
    public QuizExercise createQuiz(Course course, ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode) {
        QuizExercise quizExercise = ModelFactory.generateQuizExercise(releaseDate, dueDate, quizMode, course);
        initializeQuizExercise(quizExercise);
        return quizExercise;
    }

    /**
     * Creates a new quiz that gets saved in the QuizExercise repository.
     *
     * @param releaseDate release date of the quiz, is also used to set the start date of the course
     * @param dueDate     due date of the quiz, is also used to set the end date of the course
     * @param quizMode    SYNCHRONIZED, BATCHED or INDIVIDUAL
     * @return quiz that was created
     */
    public QuizExercise createAndSaveQuiz(ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode) {
        Course course = createAndSaveCourse(null, releaseDate == null ? null : releaseDate.minusDays(1), dueDate == null ? null : dueDate.plusDays(1), Set.of());

        QuizExercise quizExercise = ModelFactory.generateQuizExercise(releaseDate, dueDate, quizMode, course);
        initializeQuizExercise(quizExercise);
        quizExerciseRepository.save(quizExercise);

        return quizExercise;
    }

    /**
     * Creates a new course that gets saved in the Course repository.
     *
     * @param id        the id of the course
     * @param startDate start date of the course
     * @param endDate   end date of the course
     * @param exercises exercises of the course
     * @return course that was created
     */
    public Course createAndSaveCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises) {
        Course course = ModelFactory.generateCourse(id, startDate, endDate, exercises, "tumuser", "tutor", "editor", "instructor");
        courseRepo.save(course);

        return course;
    }

    /**
     * Creates a new exam quiz that gets saved in the QuizExercise repository.
     *
     * @param startDate start date of the exam, is also used to set the end date of the course the exam is in
     * @param endDate   end date of the exam, is also used to set the end date of the course the exam is in
     * @return exam quiz that was created
     */
    @NotNull
    public QuizExercise createAndSaveExamQuiz(ZonedDateTime startDate, ZonedDateTime endDate) {
        Course course = createAndSaveCourse(null, startDate.minusDays(1), endDate.plusDays(1), new HashSet<>());

        Exam exam = ModelFactory.generateExam(course, startDate.minusMinutes(5), startDate, endDate, false);
        ExerciseGroup exerciseGroup = ModelFactory.generateExerciseGroup(true, exam);
        examRepository.save(exam);

        QuizExercise quizExercise = ModelFactory.generateQuizExerciseForExam(exerciseGroup);
        initializeQuizExercise(quizExercise);
        quizExerciseRepository.save(quizExercise);

        return quizExercise;
    }

    /**
     * Removes a user from all courses they are currently in
     *
     * @param login login to find user with
     */
    public void removeUserFromAllCourses(String login) {
        User user = getUserByLogin(login);
        user.setGroups(Set.of());
        userRepo.save(user);
    }

    @NotNull
    public QuizExercise createQuizWithQuizBatchedExercises(Course course, ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode) {
        QuizExercise quizExerciseWithQuizBatches = ModelFactory.generateQuizExerciseWithQuizBatches(releaseDate, dueDate, quizMode, course);
        initializeQuizExercise(quizExerciseWithQuizBatches);
        return quizExerciseWithQuizBatches;
    }

    @NotNull
    public QuizExercise createQuizForExam(ExerciseGroup exerciseGroup) {
        QuizExercise quizExercise = ModelFactory.generateQuizExerciseForExam(exerciseGroup);
        initializeQuizExercise(quizExercise);

        return quizExercise;
    }

    private void initializeQuizExercise(QuizExercise quizExercise) {
        quizExercise.addQuestions(createMultipleChoiceQuestion());
        quizExercise.addQuestions(createDragAndDropQuestion());
        quizExercise.addQuestions(createShortAnswerQuestion());
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());
        quizExercise.setGradingInstructions(null);
    }

    @NotNull
    public ShortAnswerQuestion createShortAnswerQuestion() {
        ShortAnswerQuestion sa = (ShortAnswerQuestion) new ShortAnswerQuestion().title("SA").score(2).text("This is a long answer text");
        sa.setScoringType(ScoringType.PROPORTIONAL_WITHOUT_PENALTY);
        // TODO: we should test different values here
        sa.setMatchLetterCase(true);
        sa.setSimilarityValue(100);

        var shortAnswerSpot1 = new ShortAnswerSpot().spotNr(0).width(1);
        shortAnswerSpot1.setTempID(generateTempId());
        var shortAnswerSpot2 = new ShortAnswerSpot().spotNr(2).width(2);
        shortAnswerSpot2.setTempID(generateTempId());
        sa.getSpots().add(shortAnswerSpot1);
        sa.getSpots().add(shortAnswerSpot2);

        var shortAnswerSolution1 = new ShortAnswerSolution().text("is");
        shortAnswerSolution1.setTempID(generateTempId());
        var shortAnswerSolution2 = new ShortAnswerSolution().text("long");
        shortAnswerSolution2.setTempID(generateTempId());
        sa.addSolution(shortAnswerSolution1);
        // also invoke remove once
        sa.removeSolution(shortAnswerSolution1);
        sa.addSolution(shortAnswerSolution1);
        sa.addSolution(shortAnswerSolution2);

        var mapping1 = new ShortAnswerMapping().spot(sa.getSpots().get(0)).solution(sa.getSolutions().get(0));
        shortAnswerSolution1.addMappings(mapping1);
        shortAnswerSpot1.addMappings(mapping1);
        // also invoke remove once
        shortAnswerSolution1.removeMappings(mapping1);
        shortAnswerSpot1.removeMappings(mapping1);
        shortAnswerSolution1.addMappings(mapping1);
        shortAnswerSpot1.addMappings(mapping1);
        assertThat(shortAnswerSolution1.getMappings()).isNotEmpty();
        assertThat(shortAnswerSpot1.getMappings()).isNotEmpty();
        log.debug(shortAnswerSolution1.toString());
        log.debug(shortAnswerSpot1.toString());

        var mapping2 = new ShortAnswerMapping().spot(sa.getSpots().get(1)).solution(sa.getSolutions().get(1));
        sa.addCorrectMapping(mapping1);
        assertThat(sa).isEqualTo(mapping1.getQuestion());
        sa.removeCorrectMapping(mapping1);
        sa.addCorrectMapping(mapping1);
        sa.addCorrectMapping(mapping2);
        sa.setExplanation("Explanation");
        sa.setRandomizeOrder(true);
        // invoke some util methods
        log.debug("ShortAnswer: {}", sa);
        log.debug("ShortAnswer.hashCode: {}", sa.hashCode());
        sa.copyQuestionId();
        return sa;
    }

    @NotNull
    public DragAndDropQuestion createDragAndDropQuestion() {
        DragAndDropQuestion dnd = (DragAndDropQuestion) new DragAndDropQuestion().title("DnD").score(3).text("Q2");
        dnd.setScoringType(ScoringType.PROPORTIONAL_WITH_PENALTY);

        var dropLocation1 = new DropLocation().posX(10d).posY(10d).height(10d).width(10d);
        dropLocation1.setTempID(generateTempId());
        var dropLocation2 = new DropLocation().posX(20d).posY(20d).height(10d).width(10d);
        dropLocation2.setTempID(generateTempId());
        var dropLocation3 = new DropLocation().posX(30d).posY(30d).height(10d).width(10d);
        dropLocation3.setTempID(generateTempId());
        dnd.addDropLocation(dropLocation1);
        // also invoke remove once
        dnd.removeDropLocation(dropLocation1);
        dnd.addDropLocation(dropLocation1);
        dnd.addDropLocation(dropLocation2);
        dnd.addDropLocation(dropLocation3);

        var dragItem1 = new DragItem().text("D1");
        dragItem1.setTempID(generateTempId());
        var dragItem2 = new DragItem().text("D2");
        dragItem2.setTempID(generateTempId());
        var dragItem3 = new DragItem().text("D3");
        dragItem3.setTempID(generateTempId());
        dnd.addDragItem(dragItem1);
        assertThat(dragItem1.getQuestion()).isEqualTo(dnd);
        // also invoke remove once
        dnd.removeDragItem(dragItem1);
        dnd.addDragItem(dragItem1);
        dnd.addDragItem(dragItem2);
        dnd.addDragItem(dragItem3);

        var mapping1 = new DragAndDropMapping().dragItem(dragItem1).dropLocation(dropLocation1);
        dragItem1.addMappings(mapping1);
        // also invoke remove
        dragItem1.removeMappings(mapping1);
        dragItem1.addMappings(mapping1);
        assertThat(dragItem1.getMappings()).isNotEmpty();

        dnd.addCorrectMapping(mapping1);
        dnd.removeCorrectMapping(mapping1);
        dnd.addCorrectMapping(mapping1);
        var mapping2 = new DragAndDropMapping().dragItem(dragItem2).dropLocation(dropLocation2);
        dnd.addCorrectMapping(mapping2);
        var mapping3 = new DragAndDropMapping().dragItem(dragItem3).dropLocation(dropLocation3);
        dnd.addCorrectMapping(mapping3);
        dnd.setExplanation("Explanation");
        // invoke some util methods
        log.debug("DnD: {}", dnd);
        log.debug("DnD.hashCode: {}", dnd.hashCode());
        dnd.copyQuestionId();
        return dnd;
    }

    public Long generateTempId() {
        return ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
    }

    @NotNull
    public MultipleChoiceQuestion createMultipleChoiceQuestion() {
        MultipleChoiceQuestion mc = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title("MC").score(4).text("Q1");
        mc.setScoringType(ScoringType.ALL_OR_NOTHING);
        mc.getAnswerOptions().add(new AnswerOption().text("A").hint("H1").explanation("E1").isCorrect(true));
        mc.getAnswerOptions().add(new AnswerOption().text("B").hint("H2").explanation("E2").isCorrect(false));
        mc.setExplanation("Explanation");
        // invoke some util methods
        log.debug("MC: {}", mc);
        log.debug("MC.hashCode: {}", mc.hashCode());
        mc.copyQuestionId();
        return mc;
    }

    /**
     * Generate submissions for a student for an exercise. Results depend on the studentID.
     *
     * @param quizExercise   QuizExercise the submissions are for (we assume 3 questions here)
     * @param studentID      ID of the student
     * @param submitted      Boolean if it is submitted or not
     * @param submissionDate Submission date
     */
    public QuizSubmission generateSubmissionForThreeQuestions(QuizExercise quizExercise, int studentID, boolean submitted, ZonedDateTime submissionDate) {
        QuizSubmission quizSubmission = new QuizSubmission();
        QuizQuestion quizQuestion1 = quizExercise.getQuizQuestions().get(0);
        QuizQuestion quizQuestion2 = quizExercise.getQuizQuestions().get(1);
        QuizQuestion quizQuestion3 = quizExercise.getQuizQuestions().get(2);
        quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(quizQuestion1, studentID % 2 == 0));
        quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(quizQuestion2, studentID % 3 == 0));
        quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(quizQuestion3, studentID % 4 == 0));
        quizSubmission.submitted(submitted);
        quizSubmission.submissionDate(submissionDate);

        return quizSubmission;
    }

    /**
     * Generate a submission with all or none options of a MultipleChoiceQuestion selected, if there is one in the exercise
     *
     * @param quizExercise     Exercise the submission is for
     * @param submitted        Boolean whether it is submitted or not
     * @param submissionDate   Submission date
     * @param selectEverything Boolean whether every answer option should be selected or none
     */
    public QuizSubmission generateSpecialSubmissionWithResult(QuizExercise quizExercise, boolean submitted, ZonedDateTime submissionDate, boolean selectEverything) {
        QuizSubmission quizSubmission = new QuizSubmission();

        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                var submittedAnswer = new MultipleChoiceSubmittedAnswer();
                submittedAnswer.setQuizQuestion(question);
                if (selectEverything) {
                    for (var answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                        submittedAnswer.addSelectedOptions(answerOption);
                    }
                }
                quizSubmission.addSubmittedAnswers(submittedAnswer);

            }
            else {
                quizSubmission.addSubmittedAnswers(generateSubmittedAnswerFor(question, false));
            }
        }
        quizSubmission.submitted(submitted);
        quizSubmission.submissionDate(submissionDate);

        return quizSubmission;
    }

    // TODO: find some generic solution for the following duplicated code

    @NotNull
    public FileUploadExercise findFileUploadExerciseWithTitle(Collection<Exercise> exercises, String title) {
        Optional<Exercise> exercise = exercises.stream().filter(e -> e.getTitle().equals(title)).findFirst();
        if (exercise.isEmpty()) {
            fail("Could not find file upload exercise with title " + title);
        }
        else {
            if (exercise.get() instanceof FileUploadExercise) {
                return (FileUploadExercise) exercise.get();
            }
        }
        fail("Could not find file upload exercise with title " + title);
        // just to prevent compiler warnings, we have failed anyway here
        return new FileUploadExercise();
    }

    @NotNull
    public ModelingExercise findModelingExerciseWithTitle(Collection<Exercise> exercises, String title) {
        Optional<Exercise> exercise = exercises.stream().filter(e -> e.getTitle().equals(title)).findFirst();
        if (exercise.isEmpty()) {
            fail("Could not find modeling exercise with title " + title);
        }
        else {
            if (exercise.get() instanceof ModelingExercise) {
                return (ModelingExercise) exercise.get();
            }
        }
        fail("Could not find modeling exercise with title " + title);
        // just to prevent compiler warnings, we have failed anyway here
        return new ModelingExercise();
    }

    @NotNull
    public TextExercise findTextExerciseWithTitle(Collection<Exercise> exercises, String title) {
        Optional<Exercise> exercise = exercises.stream().filter(e -> e.getTitle().equals(title)).findFirst();
        if (exercise.isEmpty()) {
            fail("Could not find text exercise with title " + title);
        }
        else {
            if (exercise.get() instanceof TextExercise) {
                return (TextExercise) exercise.get();
            }
        }
        fail("Could not find text exercise with title " + title);
        // just to prevent compiler warnings, we have failed anyway here
        return new TextExercise();
    }

    @NotNull
    public ProgrammingExercise findProgrammingExerciseWithTitle(Collection<Exercise> exercises, String title) {
        Optional<Exercise> exercise = exercises.stream().filter(e -> e.getTitle().equals(title)).findFirst();
        if (exercise.isEmpty()) {
            fail("Could not find programming exercise with title " + title);
        }
        else {
            if (exercise.get() instanceof ProgrammingExercise) {
                return (ProgrammingExercise) exercise.get();
            }
        }
        fail("Could not find programming exercise with title " + title);
        // just to prevent compiler warnings, we have failed anyway here
        return new ProgrammingExercise();
    }

    public PageableSearchDTO<String> configureSearch(String searchTerm) {
        final var search = new PageableSearchDTO<String>();
        search.setPage(1);
        search.setPageSize(10);
        search.setSearchTerm(searchTerm);
        search.setSortedColumn(Exercise.ExerciseSearchColumn.ID.name());
        if ("".equals(searchTerm)) {
            search.setSortingOrder(SortingOrder.ASCENDING);
        }
        else {
            search.setSortingOrder(SortingOrder.DESCENDING);
        }
        return search;
    }

    public PageableSearchDTO<String> configureStudentParticipationSearch(String searchTerm) {
        final var search = new PageableSearchDTO<String>();
        search.setPage(1);
        search.setPageSize(10);
        search.setSearchTerm(searchTerm);
        search.setSortedColumn(StudentParticipation.StudentParticipationSearchColumn.ID.name());
        if ("".equals(searchTerm)) {
            search.setSortingOrder(SortingOrder.ASCENDING);
        }
        else {
            search.setSortingOrder(SortingOrder.DESCENDING);
        }
        return search;
    }

    public PageableSearchDTO<String> configureLectureSearch(String searchTerm) {
        final var search = new PageableSearchDTO<String>();
        search.setPage(1);
        search.setPageSize(10);
        search.setSearchTerm(searchTerm);
        search.setSortedColumn(Lecture.LectureSearchColumn.COURSE_TITLE.name());
        search.setSortingOrder(SortingOrder.DESCENDING);
        return search;
    }

    public LinkedMultiValueMap<String, String> searchMapping(PageableSearchDTO<String> search) {
        final var mapType = new TypeToken<Map<String, String>>() {
        }.getType();
        final var gson = new Gson();
        final Map<String, String> params = new Gson().fromJson(gson.toJson(search), mapType);
        final var paramMap = new LinkedMultiValueMap<String, String>();
        params.forEach(paramMap::add);
        return paramMap;
    }

    public void checkFeedbackCorrectlyStored(List<Feedback> sentFeedback, List<Feedback> storedFeedback, FeedbackType feedbackType) {
        assertThat(sentFeedback).as("contains the same amount of feedback").hasSize(storedFeedback.size());
        Result storedFeedbackResult = new Result();
        Result sentFeedbackResult = new Result();
        storedFeedbackResult.setFeedbacks(storedFeedback);
        sentFeedbackResult.setFeedbacks(sentFeedback);

        Course course = new Course();
        course.setAccuracyOfScores(1);
        storedFeedbackResult.setParticipation(new StudentParticipation().exercise(new ProgrammingExercise().course(course)));
        sentFeedbackResult.setParticipation(new StudentParticipation().exercise(new ProgrammingExercise().course(course)));

        double calculatedTotalPoints = resultRepo.calculateTotalPoints(storedFeedback);
        double totalPoints = resultRepo.constrainToRange(calculatedTotalPoints, 20.0);
        storedFeedbackResult.setScore(100.0 * totalPoints / 20.0);

        double calculatedTotalPoints2 = resultRepo.calculateTotalPoints(sentFeedback);
        double totalPoints2 = resultRepo.constrainToRange(calculatedTotalPoints2, 20.0);
        sentFeedbackResult.setScore(100.0 * totalPoints2 / 20.0);

        assertThat(storedFeedbackResult.getScore()).as("stored feedback evaluates to the same score as sent feedback").isEqualTo(sentFeedbackResult.getScore());
        storedFeedback.forEach(feedback -> assertThat(feedback.getType()).as("type has been set correctly").isEqualTo(feedbackType));
    }

    public TextSubmission createSubmissionForTextExercise(TextExercise textExercise, Participant participant, String text) {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission(text, Language.ENGLISH, true);
        textSubmission = textSubmissionRepo.save(textSubmission);

        StudentParticipation studentParticipation;
        if (participant instanceof User user) {
            studentParticipation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, user);
        }
        else if (participant instanceof Team team) {
            studentParticipation = addTeamParticipationForExercise(textExercise, team.getId());
        }
        else {
            throw new RuntimeException("Unsupported participant!");
        }
        studentParticipation.addSubmission(textSubmission);

        studentParticipationRepo.save(studentParticipation);
        textSubmissionRepo.save(textSubmission);
        return textSubmission;
    }

    public TextPlagiarismResult createTextPlagiarismResultForExercise(Exercise exercise) {
        TextPlagiarismResult result = new TextPlagiarismResult();
        result.setExercise(exercise);
        result.setSimilarityDistribution(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
        result.setDuration(4);
        return plagiarismResultRepo.save(result);
    }

    public ModelingPlagiarismResult createModelingPlagiarismResultForExercise(Exercise exercise) {
        ModelingPlagiarismResult result = new ModelingPlagiarismResult();
        result.setExercise(exercise);
        result.setSimilarityDistribution(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
        result.setDuration(4);
        return plagiarismResultRepo.save(result);
    }

    @NotNull
    public LinkedMultiValueMap<String, String> getDefaultPlagiarismOptions() {
        return getPlagiarismOptions(50D, 0, 0);
    }

    @NotNull
    public LinkedMultiValueMap<String, String> getPlagiarismOptions(double similarityThreshold, int minimumScore, int minimumSize) {
        // Use default options for plagiarism detection
        var params = new LinkedMultiValueMap<String, String>();
        params.add("similarityThreshold", String.valueOf(similarityThreshold));
        params.add("minimumScore", String.valueOf(minimumScore));
        params.add("minimumSize", String.valueOf(minimumSize));
        return params;
    }

    @NotNull
    public Set<GradeStep> generateGradeStepSet(GradingScale gradingScale, boolean valid) {
        GradeStep gradeStep1 = new GradeStep();
        GradeStep gradeStep2 = new GradeStep();
        GradeStep gradeStep3 = new GradeStep();

        gradeStep1.setGradingScale(gradingScale);
        gradeStep2.setGradingScale(gradingScale);
        gradeStep3.setGradingScale(gradingScale);

        gradeStep1.setIsPassingGrade(false);
        gradeStep1.setGradeName("Fail");
        gradeStep1.setLowerBoundPercentage(0);
        gradeStep1.setUpperBoundPercentage(60);

        gradeStep2.setIsPassingGrade(true);
        gradeStep2.setGradeName("Pass");
        gradeStep2.setLowerBoundPercentage(60);
        if (valid) {
            gradeStep2.setUpperBoundPercentage(90);
        }
        else {
            gradeStep2.setUpperBoundPercentage(80);
        }

        gradeStep3.setIsPassingGrade(true);
        gradeStep3.setGradeName("Excellent");
        gradeStep3.setLowerBoundPercentage(90);
        gradeStep3.setUpperBoundPercentage(100);
        gradeStep3.setUpperBoundInclusive(true);

        return Set.of(gradeStep1, gradeStep2, gradeStep3);
    }

    public GradingScale generateGradingScale(int gradeStepCount, double[] intervals, boolean lowerBoundInclusivity, int firstPassingIndex, Optional<String[]> gradeNames,
            Course course, Integer presentationsNumber, Double presentationsWeight) {
        GradingScale gradingScale = generateGradingScale(gradeStepCount, intervals, lowerBoundInclusivity, firstPassingIndex, gradeNames);
        gradingScale.setCourse(course);
        gradingScale.setPresentationsNumber(presentationsNumber);
        gradingScale.setPresentationsWeight(presentationsWeight);
        return gradingScale;
    }

    public GradingScale generateGradingScale(int gradeStepCount, double[] intervals, boolean lowerBoundInclusivity, int firstPassingIndex, Optional<String[]> gradeNames) {
        if (gradeStepCount != intervals.length - 1 || firstPassingIndex >= gradeStepCount || firstPassingIndex < 0) {
            fail("Invalid grading scale parameters");
        }
        GradingScale gradingScale = new GradingScale();
        Set<GradeStep> gradeSteps = new HashSet<>();
        for (int i = 0; i < gradeStepCount; i++) {
            GradeStep gradeStep = new GradeStep();
            gradeStep.setLowerBoundPercentage(intervals[i]);
            gradeStep.setUpperBoundPercentage(intervals[i + 1]);
            gradeStep.setLowerBoundInclusive(i == 0 || lowerBoundInclusivity);
            gradeStep.setUpperBoundInclusive(i + 1 == gradeStepCount || !lowerBoundInclusivity);
            gradeStep.setIsPassingGrade(i >= firstPassingIndex);
            gradeStep.setGradeName(gradeNames.isPresent() ? gradeNames.get()[i] : "Step" + i);
            gradeStep.setGradingScale(gradingScale);
            gradeSteps.add(gradeStep);
        }
        gradingScale.setGradeSteps(gradeSteps);
        gradingScale.setGradeType(GradeType.GRADE);
        return gradingScale;
    }

    public GradingScale generateGradingScaleWithStickyStep(double[] intervalSizes, Optional<String[]> gradeNames, boolean lowerBoundInclusivity, int firstPassingIndex) {
        // This method has a different signature from the one above to define intervals from sizes to be consistent with
        // the instructor UI at interval-grading-system.component.ts and client tests at bonus.service.spec.ts.

        int gradeStepCount = intervalSizes.length;
        if (firstPassingIndex >= gradeStepCount || firstPassingIndex < 0) {
            fail("Invalid grading scale parameters");
        }
        GradingScale gradingScale = new GradingScale();
        Set<GradeStep> gradeSteps = new HashSet<>();
        double currentLowerBoundPercentage = 0.0;
        for (int i = 0; i < gradeStepCount; i++) {
            GradeStep gradeStep = new GradeStep();
            gradeStep.setLowerBoundPercentage(currentLowerBoundPercentage);
            currentLowerBoundPercentage += intervalSizes[i];
            gradeStep.setUpperBoundPercentage(currentLowerBoundPercentage);
            gradeStep.setLowerBoundInclusive(i == 0 || lowerBoundInclusivity);
            gradeStep.setUpperBoundInclusive(i + 1 == gradeStepCount || !lowerBoundInclusivity);

            // Ensure 100 percent is not a part of the sticky grade step.
            if (i == gradeStepCount - 2) {
                gradeStep.setUpperBoundInclusive(true);

            }
            else if (i == gradeStepCount - 1) {
                gradeStep.setLowerBoundInclusive(false);
                gradeStep.setUpperBoundInclusive(true);
            }

            gradeStep.setIsPassingGrade(i >= firstPassingIndex);
            gradeStep.setGradeName(gradeNames.isPresent() ? gradeNames.get()[i] : "Step" + i);
            gradeStep.setGradingScale(gradingScale);
            gradeSteps.add(gradeStep);
        }
        gradingScale.setGradeSteps(gradeSteps);
        gradingScale.setGradeType(GradeType.GRADE);
        return gradingScale;
    }

    public List<String[]> loadPercentagesAndGrades(String path) throws Exception {
        try (CSVReader reader = new CSVReader(new FileReader(ResourceUtils.getFile("classpath:" + path), StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            // delete first row with column headers
            rows.remove(0);
            List<String[]> percentagesAndGrades = new ArrayList<>();
            // copy only percentages, whether the student has submitted, and their grade
            rows.forEach(row -> percentagesAndGrades.add(new String[] { row[2], row[3], row[4] }));
            return percentagesAndGrades;
        }
    }

    public Course createCourseWithTestModelingAndFileUploadExercisesAndSubmissions(String loginPrefix) throws Exception {
        Course course = addCourseWithModelingAndTextAndFileUploadExercise();
        course.setEndDate(ZonedDateTime.now().minusMinutes(5));
        course = courseRepo.save(course);

        var fileUploadExercise = findFileUploadExerciseWithTitle(course.getExercises(), "FileUpload");
        createFileUploadSubmissionWithFile(loginPrefix, fileUploadExercise, "uploaded-file.png");

        var textExercise = findTextExerciseWithTitle(course.getExercises(), "Text");
        var textSubmission = ModelFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        saveTextSubmission(textExercise, textSubmission, loginPrefix + "student1");

        var modelingExercise = findModelingExerciseWithTitle(course.getExercises(), "Modeling");
        createAndSaveParticipationForExercise(modelingExercise, loginPrefix + "student1");
        String emptyActivityModel = FileUtils.loadFileFromResources("test-data/model-submission/empty-activity-diagram.json");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyActivityModel, true);
        addSubmission(modelingExercise, submission, loginPrefix + "student1");

        return course;
    }

    public void createFileUploadSubmissionWithFile(String loginPrefix, FileUploadExercise fileUploadExercise, String filename) throws IOException {
        var fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = addFileUploadSubmission(fileUploadExercise, fileUploadSubmission, loginPrefix + "student1");

        // Create a dummy file
        var uploadedFileDir = Path.of("./", FileUploadSubmission.buildFilePath(fileUploadExercise.getId(), fileUploadSubmission.getId()));
        var uploadedFilePath = Path.of(uploadedFileDir.toString(), filename);
        if (!Files.exists(uploadedFilePath)) {
            Files.createDirectories(uploadedFileDir);
            Files.createFile(uploadedFilePath);
        }
        fileUploadSubmission.setFilePath(uploadedFilePath.toString());
        fileUploadSubmissionRepo.save(fileUploadSubmission);
    }

    public Course createCourseWithExamAndExercises(String loginPrefix) throws IOException {
        var course = addEmptyCourse();

        // Create a file upload exercise with a dummy submission file
        var exerciseGroup1 = exerciseGroupRepository.save(new ExerciseGroup());
        var fileUploadExercise = ModelFactory.generateFileUploadExerciseForExam(".png", exerciseGroup1);
        fileUploadExercise = exerciseRepo.save(fileUploadExercise);
        createFileUploadSubmissionWithFile(loginPrefix, fileUploadExercise, "uploaded-file.png");
        exerciseGroup1.addExercise(fileUploadExercise);
        exerciseGroup1 = exerciseGroupRepository.save(exerciseGroup1);

        // Create a text exercise with a dummy submission file
        var exerciseGroup2 = exerciseGroupRepository.save(new ExerciseGroup());
        var textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup2);
        textExercise = exerciseRepo.save(textExercise);
        var textSubmission = ModelFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        saveTextSubmission(textExercise, textSubmission, loginPrefix + "student1");
        exerciseGroup2.addExercise(textExercise);
        exerciseGroup2 = exerciseGroupRepository.save(exerciseGroup2);

        // Create a modeling exercise with a dummy submission file
        var exerciseGroup3 = exerciseGroupRepository.save(new ExerciseGroup());
        var modelingExercise = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup2);
        modelingExercise = exerciseRepo.save(modelingExercise);
        String emptyActivityModel = FileUtils.loadFileFromResources("test-data/model-submission/empty-activity-diagram.json");
        var modelingSubmission = ModelFactory.generateModelingSubmission(emptyActivityModel, true);
        addSubmission(modelingExercise, modelingSubmission, loginPrefix + "student1");
        exerciseGroup3.addExercise(modelingExercise);
        exerciseGroupRepository.save(exerciseGroup3);

        Exam exam = addExam(course);
        exam.setEndDate(ZonedDateTime.now().minusMinutes(5));
        exam.addExerciseGroup(exerciseGroup1);
        exam.addExerciseGroup(exerciseGroup2);
        examRepository.save(exam);

        return course;
    }

    public StudentParticipation addAssessmentWithFeedbackWithGradingInstructionsForExercise(Exercise exercise, String login) {
        // add participation and submission for exercise
        StudentParticipation studentParticipation = createAndSaveParticipationForExercise(exercise, login);
        Submission submission = null;
        if (exercise instanceof TextExercise) {
            submission = ModelFactory.generateTextSubmission("test", Language.ENGLISH, true);
        }
        if (exercise instanceof FileUploadExercise) {
            submission = ModelFactory.generateFileUploadSubmission(true);
        }
        if (exercise instanceof ModelingExercise) {
            submission = ModelFactory.generateModelingSubmission(null, true);
        }
        if (exercise instanceof ProgrammingExercise) {
            submission = ModelFactory.generateProgrammingSubmission(true);
        }
        Submission submissionWithParticipation = addSubmission(studentParticipation, submission);
        Result result = addResultToParticipation(studentParticipation, submissionWithParticipation);
        resultRepo.save(result);

        assertThat(exercise.getGradingCriteria()).isNotNull();
        assertThat(exercise.getGradingCriteria().get(0).getStructuredGradingInstructions()).isNotNull();

        // add feedback which is associated with structured grading instructions
        Feedback feedback = new Feedback();
        feedback.setGradingInstruction(exercise.getGradingCriteria().get(0).getStructuredGradingInstructions().get(0));
        addFeedbackToResult(feedback, result);
        return studentParticipation;
    }

    public List<Result> getResultsForExercise(Exercise exercise) {
        return resultRepo.findWithEagerSubmissionAndFeedbackByParticipationExerciseId(exercise.getId());
    }

    public Course saveCourse(Course course) {
        return courseRepo.save(course);
    }

    public Course createCourseWithTextExerciseAndTutor(String login) {
        Course course = this.createCourse();
        TextExercise textExercise = createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        StudentParticipation participation = ModelFactory.generateStudentParticipationWithoutUser(InitializationState.INITIALIZED, textExercise);
        studentParticipationRepo.save(participation);
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("some text", Language.ENGLISH, true);
        textSubmission.setParticipation(participation);
        textSubmissionRepo.saveAndFlush(textSubmission);
        course.addExercises(textExercise);
        User user = createAndSaveUser(login);
        user.setGroups(Set.of(course.getTeachingAssistantGroupName()));
        userRepo.save(user);
        return course;
    }

    public Course createCourseWithInstructorAndTextExercise(String userPrefix) {
        Course course = this.createCourse();
        TextExercise textExercise = createIndividualTextExercise(course, pastTimestamp, pastTimestamp, pastTimestamp);
        StudentParticipation participation = ModelFactory.generateStudentParticipationWithoutUser(InitializationState.INITIALIZED, textExercise);
        studentParticipationRepo.save(participation);
        course.addExercises(textExercise);
        addUsers(userPrefix, 0, 0, 0, 1);
        return course;
    }

    public TextAssessmentEvent createSingleTextAssessmentEvent(Long courseId, Long userId, Long exerciseId, Long participationId, Long submissionId) {
        return ModelFactory.generateTextAssessmentEvent(TextAssessmentEventType.VIEW_AUTOMATIC_SUGGESTION_ORIGIN, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC, courseId, userId,
                exerciseId, participationId, submissionId);
    }

    /**
     * Update the max complaint text limit of the course.
     *
     * @param course             course which is updated
     * @param complaintTextLimit new complaint text limit
     * @return updated course
     */
    public Course updateCourseComplaintTextLimit(Course course, int complaintTextLimit) {
        course.setMaxComplaintTextLimit(complaintTextLimit);
        assertThat(course.getMaxComplaintTextLimit()).as("course contains the correct complaint text limit").isEqualTo(complaintTextLimit);
        return courseRepo.save(course);
    }

    /**
     * Update the max complaint response text limit of the course.
     *
     * @param course                     course which is updated
     * @param complaintResponseTextLimit new complaint response text limit
     * @return updated course
     */
    public Course updateCourseComplaintResponseTextLimit(Course course, int complaintResponseTextLimit) {
        course.setMaxComplaintResponseTextLimit(complaintResponseTextLimit);
        assertThat(course.getMaxComplaintResponseTextLimit()).as("course contains the correct complaint response text limit").isEqualTo(complaintResponseTextLimit);
        return courseRepo.save(course);
    }

    public <T extends Posting> void assertSensitiveInformationHidden(@NotNull List<T> postings) {
        for (Posting posting : postings) {
            assertSensitiveInformationHidden(posting);
        }
    }

    public void assertSensitiveInformationHidden(@NotNull Posting posting) {
        if (posting.getAuthor() != null) {
            assertThat(posting.getAuthor().getEmail()).isNull();
            assertThat(posting.getAuthor().getLogin()).isNull();
            assertThat(posting.getAuthor().getRegistrationNumber()).isNull();
        }
    }

    public void assertSensitiveInformationHidden(@NotNull Reaction reaction) {
        if (reaction.getUser() != null) {
            assertThat(reaction.getUser().getEmail()).isNull();
            assertThat(reaction.getUser().getLogin()).isNull();
            assertThat(reaction.getUser().getRegistrationNumber()).isNull();
        }
    }

    public TutorialGroupSession createIndividualTutorialGroupSession(Long tutorialGroupId, ZonedDateTime start, ZonedDateTime end, Integer attendanceCount) {
        var tutorialGroup = tutorialGroupRepository.findByIdElseThrow(tutorialGroupId);

        TutorialGroupSession tutorialGroupSession = new TutorialGroupSession();
        tutorialGroupSession.setStart(start);
        tutorialGroupSession.setEnd(end);
        tutorialGroupSession.setTutorialGroup(tutorialGroup);
        tutorialGroupSession.setLocation("LoremIpsum");
        tutorialGroupSession.setStatus(TutorialGroupSessionStatus.ACTIVE);
        tutorialGroupSession.setAttendanceCount(attendanceCount);
        tutorialGroupSession = tutorialGroupSessionRepository.save(tutorialGroupSession);
        return tutorialGroupSession;
    }

    public TutorialGroupFreePeriod addTutorialGroupFreeDay(Long tutorialGroupsConfigurationId, LocalDate date, String reason) {
        var tutorialGroupsConfiguration = tutorialGroupsConfigurationRepository.findByIdWithEagerTutorialGroupFreePeriodsElseThrow(tutorialGroupsConfigurationId);
        var course = tutorialGroupsConfiguration.getCourse();

        TutorialGroupFreePeriod newTutorialGroupFreePeriod = new TutorialGroupFreePeriod();
        newTutorialGroupFreePeriod.setTutorialGroupsConfiguration(tutorialGroupsConfiguration);
        newTutorialGroupFreePeriod.setReason(reason);

        newTutorialGroupFreePeriod.setStart(interpretInTimeZone(date, START_OF_DAY, course.getTimeZone()));
        newTutorialGroupFreePeriod.setEnd(interpretInTimeZone(date, END_OF_DAY, course.getTimeZone()));

        return tutorialGroupFreePeriodRepository.save(newTutorialGroupFreePeriod);
    }

    public TutorialGroup createTutorialGroup(Long courseId, String title, String additionalInformation, Integer capacity, Boolean isOnline, String campus, String language,
            User teachingAssistant, Set<User> registeredStudents) {
        var course = courseRepo.findByIdElseThrow(courseId);

        var tutorialGroup = ModelFactory.generateTutorialGroup(title, additionalInformation, capacity, isOnline, language, campus);
        tutorialGroup.setCourse(course);
        tutorialGroup.setTeachingAssistant(teachingAssistant);

        var persistedTutorialGroup = tutorialGroupRepository.saveAndFlush(tutorialGroup);

        var registrations = new HashSet<TutorialGroupRegistration>();
        for (var student : registeredStudents) {
            registrations.add(new TutorialGroupRegistration(student, persistedTutorialGroup, TutorialGroupRegistrationType.INSTRUCTOR_REGISTRATION));
        }
        tutorialGroupRegistrationRepository.saveAllAndFlush(registrations);
        return persistedTutorialGroup;
    }

    public TutorialGroupsConfiguration createTutorialGroupConfiguration(Long courseId, LocalDate start, LocalDate end) {
        var course = courseRepo.findByIdElseThrow(courseId);
        var tutorialGroupConfiguration = ModelFactory.generateTutorialGroupsConfiguration(start, end);
        tutorialGroupConfiguration.setCourse(course);
        var persistedConfiguration = tutorialGroupsConfigurationRepository.save(tutorialGroupConfiguration);
        course.setTutorialGroupsConfiguration(persistedConfiguration);
        course = courseRepo.save(course);
        persistedConfiguration.setCourse(course);
        return persistedConfiguration;
    }

    public Conversation createOneToOneChat(Course course, String userPrefix) {
        Conversation conversation = new OneToOneChat();
        conversation.setCourse(course);
        conversation = conversationRepository.save(conversation);

        List<ConversationParticipant> conversationParticipants = new ArrayList<>();
        conversationParticipants.add(createConversationParticipant(conversation, userPrefix + "tutor1"));
        conversationParticipants.add(createConversationParticipant(conversation, userPrefix + "tutor2"));

        conversation.setConversationParticipants(new HashSet<>(conversationParticipants));
        return conversationRepository.save(conversation);
    }

    private ConversationParticipant createConversationParticipant(Conversation conversation, String userName) {
        ConversationParticipant conversationParticipant = new ConversationParticipant();
        conversationParticipant.setConversation(conversation);
        conversationParticipant.setLastRead(conversation.getLastMessageDate());
        conversationParticipant.setUser(getUserByLogin(userName));

        return conversationParticipantRepository.save(conversationParticipant);
    }

    public void updateCourseGroups(String userPrefix, List<Course> courses, String suffix) {
        courses.forEach(course -> updateCourseGroups(userPrefix, course, suffix));
    }

    public void updateCourseGroups(String userPrefix, Course course, String suffix) {
        course.setStudentGroupName(userPrefix + "student" + suffix);
        course.setTeachingAssistantGroupName(userPrefix + "tutor" + suffix);
        course.setEditorGroupName(userPrefix + "editor" + suffix);
        course.setInstructorGroupName(userPrefix + "instructor" + suffix);
        courseRepo.save(course);
    }

    public void adjustUserGroupsToCustomGroups(String userPrefix, String userSuffix, int numberOfStudents, int numberOfTutors, int numberOfEditors, int numberOfInstructors) {
        for (int i = 1; i <= numberOfStudents; i++) {
            var user = getUserByLogin(userPrefix + "student" + i);
            user.setGroups(Set.of(userPrefix + "student" + userSuffix));
            userRepo.save(user);
        }
        for (int i = 1; i <= numberOfTutors; i++) {
            var user = getUserByLogin(userPrefix + "tutor" + i);
            user.setGroups(Set.of(userPrefix + "tutor" + userSuffix));
            userRepo.save(user);
        }
        for (int i = 1; i <= numberOfEditors; i++) {
            var user = getUserByLogin(userPrefix + "editor" + i);
            user.setGroups(Set.of(userPrefix + "editor" + userSuffix));
            userRepo.save(user);
        }
        for (int i = 1; i <= numberOfInstructors; i++) {
            var user = getUserByLogin(userPrefix + "instructor" + i);
            user.setGroups(Set.of(userPrefix + "instructor" + userSuffix));
            userRepo.save(user);
        }
    }
}
