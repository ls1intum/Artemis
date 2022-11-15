package de.tum.in.www1.artemis.util;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.springframework.util.ResourceUtils;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ApollonDiagram;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.domain.notification.SystemNotification;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildLogDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildPlanDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.*;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

public class ModelFactory {

    public static final String USER_PASSWORD = "00000000";

    public static final String DEFAULT_BRANCH = "main";

    public static Lecture generateLecture(ZonedDateTime startDate, ZonedDateTime endDate, Course course) {
        Lecture lecture = new Lecture();
        lecture.setStartDate(startDate);
        lecture.setDescription("Description");
        lecture.setTitle("Lecture");
        lecture.setEndDate(endDate);
        lecture.setCourse(course);
        return lecture;
    }

    /**
     * Create a dummy attachment for testing
     *
     * @param date The optional upload and release date to set on the attachment
     * @return Attachment that was created
     */
    public static Attachment generateAttachment(ZonedDateTime date) {
        Attachment attachment = new Attachment();
        attachment.setAttachmentType(AttachmentType.FILE);
        if (date != null) {
            attachment.setReleaseDate(date);
            attachment.setUploadDate(date);
        }
        attachment.setName("TestAttachment");
        return attachment;
    }

    /**
     * Create a dummy attachment for testing with a placeholder image file on disk
     *
     * @param startDate The release date to set on the attachment
     * @return Attachment that was created with its link set to a testing file on disk
     */
    public static Attachment generateAttachmentWithFile(ZonedDateTime startDate) {
        Attachment attachment = generateAttachment(startDate);
        String testFileName = "test_" + UUID.randomUUID().toString().substring(0, 8) + ".jpg";
        try {
            FileUtils.copyFile(ResourceUtils.getFile("classpath:test-data/attachment/placeholder.jpg"), new File(FilePathService.getTempFilePath(), testFileName));
        }
        catch (IOException ex) {
            fail("Failed while copying test attachment files", ex);
        }
        attachment.setLink(Path.of(FileService.DEFAULT_FILE_SUBPATH, testFileName).toString());
        return attachment;
    }

    public static QuizBatch generateQuizBatch(QuizExercise quizExercise, ZonedDateTime startTime) {
        var quizBatch = new QuizBatch();
        quizBatch.setQuizExercise(quizExercise);
        quizBatch.setStartTime(startTime);
        return quizBatch;
    }

    public static QuizExercise generateQuizExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode, Course course) {
        var quizExercise = (QuizExercise) populateExercise(new QuizExercise(), releaseDate, dueDate, null, course);
        quizExercise.setProblemStatement(null);
        quizExercise.setGradingInstructions(null);
        quizExercise.setPresentationScoreEnabled(false);
        quizExercise.setIsOpenForPractice(false);
        quizExercise.setAllowedNumberOfAttempts(1);
        quizExercise.setDuration(10);
        quizExercise.setRandomizeQuestionOrder(true);
        quizExercise.setQuizMode(quizMode);
        if (quizMode == QuizMode.SYNCHRONIZED) {
            quizExercise.setQuizBatches(Set.of(generateQuizBatch(quizExercise, releaseDate)));
        }
        return quizExercise;
    }

    public static QuizExercise generateQuizExerciseWithQuizBatches(ZonedDateTime releaseDate, ZonedDateTime dueDate, QuizMode quizMode, Course course) {
        var quizExercise = generateQuizExercise(releaseDate, dueDate, quizMode, course);
        quizExercise.setQuizBatches(Set.of(generateQuizBatch(quizExercise, releaseDate)));
        return quizExercise;
    }

    public static QuizExercise generateQuizExerciseForExam(ExerciseGroup exerciseGroup) {
        var quizExercise = (QuizExercise) populateExerciseForExam(new QuizExercise(), exerciseGroup);
        quizExercise.setProblemStatement(null);
        quizExercise.setGradingInstructions(null);
        quizExercise.setPresentationScoreEnabled(false);
        quizExercise.setIsOpenForPractice(false);
        quizExercise.setAllowedNumberOfAttempts(1);
        quizExercise.setDuration(10);
        quizExercise.setQuizPointStatistic(new QuizPointStatistic());
        for (var question : quizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion) {
                question.setQuizQuestionStatistic(new DragAndDropQuestionStatistic());
            }
            else if (question instanceof MultipleChoiceQuestion) {
                question.setQuizQuestionStatistic(new MultipleChoiceQuestionStatistic());
            }
            else {
                question.setQuizQuestionStatistic(new ShortAnswerQuestionStatistic());
            }
        }
        quizExercise.setRandomizeQuestionOrder(true);
        return quizExercise;
    }

    public static ProgrammingExercise generateProgrammingExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, Course course) {
        return generateProgrammingExercise(releaseDate, dueDate, course, ProgrammingLanguage.JAVA);
    }

    public static ProgrammingExercise generateProgrammingExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, Course course, ProgrammingLanguage programmingLanguage) {
        var programmingExercise = (ProgrammingExercise) populateExercise(new ProgrammingExercise(), releaseDate, dueDate, null, course);
        populateProgrammingExercise(programmingExercise, programmingLanguage);
        return programmingExercise;
    }

    public static ProgrammingExercise generateProgrammingExerciseForExam(ExerciseGroup exerciseGroup) {
        return generateProgrammingExerciseForExam(exerciseGroup, ProgrammingLanguage.JAVA);
    }

    public static ProgrammingExercise generateProgrammingExerciseForExam(ExerciseGroup exerciseGroup, ProgrammingLanguage programmingLanguage) {
        var programmingExercise = (ProgrammingExercise) populateExerciseForExam(new ProgrammingExercise(), exerciseGroup);
        populateProgrammingExercise(programmingExercise, programmingLanguage);
        return programmingExercise;
    }

    private static void populateProgrammingExercise(ProgrammingExercise programmingExercise, ProgrammingLanguage programmingLanguage) {
        programmingExercise.generateAndSetProjectKey();
        programmingExercise.setAllowOfflineIde(true);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.setTestwiseCoverageEnabled(false);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExercise.setProgrammingLanguage(programmingLanguage);
        if (programmingLanguage == ProgrammingLanguage.JAVA) {
            programmingExercise.setProjectType(ProjectType.PLAIN_MAVEN);
        }
        else if (programmingLanguage == ProgrammingLanguage.SWIFT) {
            programmingExercise.setProjectType(ProjectType.PLAIN);
        }
        else {
            programmingExercise.setProjectType(null);
        }
        programmingExercise.setPackageName(programmingLanguage == ProgrammingLanguage.SWIFT ? "swiftTest" : "de.test");
        final var repoName = programmingExercise.generateRepositoryName(RepositoryType.TESTS);
        String testRepoUrl = String.format("http://some.test.url/scm/%s/%s.git", programmingExercise.getProjectKey(), repoName);
        programmingExercise.setTestRepositoryUrl(testRepoUrl);
        programmingExercise.setBranch(DEFAULT_BRANCH);
    }

    public static ModelingExercise generateModelingExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, DiagramType diagramType,
            Course course) {
        var modelingExercise = (ModelingExercise) populateExercise(new ModelingExercise(), releaseDate, dueDate, assessmentDueDate, course);
        modelingExercise.setDiagramType(diagramType);
        modelingExercise.setExampleSolutionModel("This is my example solution model");
        modelingExercise.setExampleSolutionExplanation("This is my example solution model");
        return modelingExercise;
    }

    public static ModelingExercise generateModelingExerciseForExam(DiagramType diagramType, ExerciseGroup exerciseGroup) {
        var modelingExercise = (ModelingExercise) populateExerciseForExam(new ModelingExercise(), exerciseGroup);
        modelingExercise.setDiagramType(diagramType);
        modelingExercise.setExampleSolutionModel("This is my example solution model");
        modelingExercise.setExampleSolutionExplanation("This is my example solution model");
        return modelingExercise;
    }

    public static TextExercise generateTextExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        var textExercise = (TextExercise) populateExercise(new TextExercise(), releaseDate, dueDate, assessmentDueDate, course);
        textExercise.setExampleSolution("This is my example solution");
        return textExercise;
    }

    public static TextExercise generateTextExerciseForExam(ExerciseGroup exerciseGroup) {
        var textExercise = (TextExercise) populateExerciseForExam(new TextExercise(), exerciseGroup);
        textExercise.setExampleSolution("This is my example solution");
        return textExercise;
    }

    public static FileUploadExercise generateFileUploadExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, String filePattern,
            Course course) {
        var fileUploadExercise = (FileUploadExercise) populateExercise(new FileUploadExercise(), releaseDate, dueDate, assessmentDueDate, course);
        fileUploadExercise.setFilePattern(filePattern);
        fileUploadExercise.setExampleSolution("This is my example solution");
        return fileUploadExercise;
    }

    public static FileUploadExercise generateFileUploadExerciseForExam(String filePattern, ExerciseGroup exerciseGroup) {
        FileUploadExercise fileUploadExercise = new FileUploadExercise();
        fileUploadExercise.setFilePattern(filePattern);
        return (FileUploadExercise) populateExerciseForExam(fileUploadExercise, exerciseGroup);
    }

    public static GitUtilService.MockFileRepositoryUrl getMockFileRepositoryUrl(LocalRepository repository) {
        return new GitUtilService.MockFileRepositoryUrl(repository.originRepoFile);
    }

    private static Exercise populateExercise(Exercise exercise, ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        exercise.setTitle(UUID.randomUUID().toString());
        exercise.setShortName("t" + UUID.randomUUID().toString().substring(0, 3));
        exercise.setProblemStatement("Problem Statement");
        exercise.setMaxPoints(5.0);
        exercise.setBonusPoints(0.0);
        exercise.setReleaseDate(releaseDate);
        exercise.setDueDate(dueDate);
        exercise.setAssessmentDueDate(assessmentDueDate);
        exercise.setDifficulty(DifficultyLevel.MEDIUM);
        exercise.setMode(ExerciseMode.INDIVIDUAL);
        exercise.getCategories().add("Category");
        exercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);
        exercise.setCourse(course);
        exercise.setExerciseGroup(null);
        return exercise;
    }

    private static Exercise populateExerciseForExam(Exercise exercise, ExerciseGroup exerciseGroup) {
        exercise.setTitle(UUID.randomUUID().toString());
        exercise.setShortName("t" + UUID.randomUUID().toString().substring(0, 3));
        exercise.setProblemStatement("Exam Problem Statement");
        exercise.setMaxPoints(5.0);
        exercise.setBonusPoints(0.0);
        // these values are set to null explicitly
        exercise.setReleaseDate(null);
        exercise.setDueDate(null);
        exercise.setAssessmentDueDate(null);
        exercise.setDifficulty(DifficultyLevel.MEDIUM);
        exercise.setMode(ExerciseMode.INDIVIDUAL);
        exercise.getCategories().add("Category");
        exercise.setExerciseGroup(exerciseGroup);
        exercise.setCourse(null);
        if (!(exercise instanceof QuizExercise)) {
            exercise.setGradingInstructions("Grading instructions");
            exercise.setGradingCriteria(List.of(new GradingCriterion()));
        }
        return exercise;
    }

    public static List<User> generateActivatedUsers(String loginPrefix, String commonPasswordHash, String[] groups, Set<Authority> authorities, int amount) {
        List<User> generatedUsers = new ArrayList<>();
        for (int i = 1; i <= amount; i++) {
            User user = ModelFactory.generateActivatedUser(loginPrefix + i, commonPasswordHash);
            if (groups != null) {
                user.setGroups(Set.of(groups));
                user.setAuthorities(authorities);
            }
            generatedUsers.add(user);
        }
        return generatedUsers;
    }

    public static List<User> generateActivatedUsers(String loginPrefix, String[] groups, Set<Authority> authorities, int amount) {
        return generateActivatedUsers(loginPrefix, USER_PASSWORD, groups, authorities, amount);
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
    public static List<User> generateActivatedUsersWithRegistrationNumber(String loginPrefix, String[] groups, Set<Authority> authorities, int amount,
            String registrationNumberPrefix) {
        List<User> generatedUsers = ModelFactory.generateActivatedUsers(loginPrefix, groups, authorities, amount);
        for (int i = 0; i < amount; i++) {
            generatedUsers.get(i).setRegistrationNumber(registrationNumberPrefix + "R" + i);
        }
        return generatedUsers;
    }

    public static User generateActivatedUser(String login, String password) {
        User user = new User();
        user.setLogin(login);
        user.setPassword(password);
        user.setFirstName(login + "First");
        user.setLastName(login + "Last");
        user.setEmail(login + "@test.de");
        user.setActivated(true);
        user.setLangKey("en");
        user.setGroups(new HashSet<>());
        user.setAuthorities(new HashSet<>());
        return user;
    }

    public static User generateActivatedUser(String login) {
        return generateActivatedUser(login, USER_PASSWORD);
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
    public static Team generateTeamForExercise(Exercise exercise, String name, String shortName, String loginPrefix, int numberOfStudents, User owner, String creatorLogin,
            String registrationPrefix) {
        List<User> students = generateActivatedUsersWithRegistrationNumber(shortName + loginPrefix, new String[] { "tumuser", "testgroup" },
                Set.of(new Authority(Role.STUDENT.getAuthority())), numberOfStudents, shortName + registrationPrefix);

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
    public static Team generateTeamForExercise(Exercise exercise, String name, String shortName, int numberOfStudents, User owner) {
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
    public static List<Team> generateTeamsForExercise(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner, String creatorLogin) {
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
    public static List<Team> generateTeamsForExercise(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner, String creatorLogin,
            String registrationPrefix) {
        List<Team> teams = new ArrayList<>();
        for (int i = 1; i <= numberOfTeams; i++) {
            int numberOfStudents = new Random().nextInt(4) + 1; // range: 1-4 students
            teams.add(generateTeamForExercise(exercise, "Team " + i, shortNamePrefix + i, loginPrefix, numberOfStudents, owner, creatorLogin, registrationPrefix));
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
    public static List<Team> generateTeamsForExerciseFixedTeamSize(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner,
            String creatorLogin, String registrationPrefix, int teamSize) {
        List<Team> teams = new ArrayList<>();
        for (int i = 1; i <= numberOfTeams; i++) {
            teams.add(generateTeamForExercise(exercise, "Team " + i, shortNamePrefix + i, loginPrefix, teamSize, owner, creatorLogin, registrationPrefix));
        }
        return teams;
    }

    public static Course generateCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises) {
        return generateCourse(id, startDate, endDate, exercises, null, null, null, null);
    }

    public static TextSubmission generateTextSubmission(String text, Language language, boolean submitted) {
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.text(text);
        textSubmission.setLanguage(language);
        textSubmission.setSubmitted(submitted);
        if (submitted) {
            textSubmission.setSubmissionDate(now().minusDays(1));
        }
        return textSubmission;
    }

    public static TextSubmission generateLateTextSubmission(String text, Language language) {
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.text(text);
        textSubmission.setLanguage(language);
        textSubmission.setSubmitted(true);
        textSubmission.setSubmissionDate(now().plusDays(1));
        return textSubmission;
    }

    public static ProgrammingSubmission generateProgrammingSubmission(boolean submitted, String commitHash, SubmissionType type) {
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setSubmitted(submitted);
        if (submitted) {
            programmingSubmission.setSubmissionDate(now().minusDays(1));
        }
        programmingSubmission.setCommitHash(commitHash);
        programmingSubmission.setType(type);
        return programmingSubmission;
    }

    public static ProgrammingSubmission generateProgrammingSubmission(boolean submitted) {
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setSubmitted(submitted);
        if (submitted) {
            programmingSubmission.setSubmissionDate(now().minusDays(1));
        }
        return programmingSubmission;
    }

    public static FileUploadSubmission generateFileUploadSubmission(boolean submitted) {
        FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();
        fileUploadSubmission.setSubmitted(submitted);
        if (submitted) {
            fileUploadSubmission.setSubmissionDate(now().minusDays(1));
        }
        return fileUploadSubmission;
    }

    public static FileUploadSubmission generateFileUploadSubmissionWithFile(boolean submitted, String filePath) {
        FileUploadSubmission fileUploadSubmission = generateFileUploadSubmission(submitted);
        fileUploadSubmission.setFilePath(filePath);
        if (submitted) {
            fileUploadSubmission.setSubmissionDate(now().minusDays(1));
        }
        return fileUploadSubmission;
    }

    public static FileUploadSubmission generateLateFileUploadSubmission() {
        FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();
        fileUploadSubmission.setSubmitted(true);
        fileUploadSubmission.setSubmissionDate(now().plusDays(1));
        return fileUploadSubmission;
    }

    public static ModelingSubmission generateModelingSubmission(String model, boolean submitted) {
        ModelingSubmission submission = new ModelingSubmission();
        submission.setModel(model);
        submission.setSubmitted(submitted);
        if (submitted) {
            submission.setSubmissionDate(now().minusDays(1));
        }
        return submission;
    }

    public static QuizSubmission generateQuizSubmission(boolean submitted) {
        QuizSubmission submission = new QuizSubmission();
        submission.setSubmitted(submitted);
        if (submitted) {
            submission.setSubmissionDate(now().minusDays(1));
        }
        return submission;
    }

    public static ExampleSubmission generateExampleSubmission(Submission submission, Exercise exercise, boolean usedForTutorial) {
        ExampleSubmission exampleSubmission = new ExampleSubmission();
        exampleSubmission.setSubmission(submission);
        exampleSubmission.setExercise(exercise);
        exampleSubmission.setUsedForTutorial(usedForTutorial);
        return exampleSubmission;
    }

    public static Course generateCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises, String studentGroupName,
            String teachingAssistantGroupName, String editorGroupName, String instructorGroupName) {
        return generateCourse(id, startDate, endDate, exercises, studentGroupName, teachingAssistantGroupName, editorGroupName, instructorGroupName, 3, 3, 7, 2000, 2000, true, 7);
    }

    public static Course generateCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises, String studentGroupName,
            String teachingAssistantGroupName, String editorGroupName, String instructorGroupName, Integer maxComplaints, Integer maxTeamComplaints, Integer maxComplaintTimeDays,
            int maxComplaintTextLimit, int maxComplaintResponseTextLimit, boolean postsEnabled, int requestMoreFeedbackTimeDays) {
        Course course = new Course();
        course.setId(id);
        course.setTitle("Course title " + UUID.randomUUID());

        // must start with a letter
        course.setShortName("short" + UUID.randomUUID().toString().replace("-", "0"));
        course.setMaxComplaints(maxComplaints);
        course.setMaxTeamComplaints(maxTeamComplaints);
        course.setMaxComplaintTimeDays(maxComplaintTimeDays);
        course.setMaxComplaintTextLimit(maxComplaintTextLimit);
        course.setMaxComplaintResponseTextLimit(maxComplaintResponseTextLimit);
        course.setPostsEnabled(postsEnabled);
        course.setMaxRequestMoreFeedbackTimeDays(requestMoreFeedbackTimeDays);
        course.setStudentGroupName(studentGroupName);
        course.setTeachingAssistantGroupName(teachingAssistantGroupName);
        course.setEditorGroupName(editorGroupName);
        course.setInstructorGroupName(instructorGroupName);
        course.setStartDate(startDate);
        course.setEndDate(endDate);
        course.setExercises(exercises);
        course.setOnlineCourse(false);
        course.setRegistrationEnabled(false);
        course.setPresentationScore(2);
        course.setAccuracyOfScores(1);
        return course;
    }

    public static OnlineCourseConfiguration generateOnlineCourseConfiguration(Course course, String key, String secret, String userPrefix, String originalUrl) {
        OnlineCourseConfiguration onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setCourse(course);
        onlineCourseConfiguration.setLtiKey(key);
        onlineCourseConfiguration.setLtiSecret(secret);
        onlineCourseConfiguration.setUserPrefix(userPrefix);
        onlineCourseConfiguration.setOriginalUrl(originalUrl);
        course.setOnlineCourseConfiguration(onlineCourseConfiguration);
        return onlineCourseConfiguration;
    }

    /**
     * Generates a TextAssessment event with the given parameters
     *
     * @param eventType       the type of the event
     * @param feedbackType    the type of the feedback
     * @param segmentType     the segment type of the event
     * @param courseId        the course id of the event
     * @param userId          the userid of the event
     * @param exerciseId      the exercise id of the event
     * @param participationId the participation id of the event
     * @param submissionId    the submission id of the event
     * @return the TextAssessment event with all the properties applied
     */
    public static TextAssessmentEvent generateTextAssessmentEvent(TextAssessmentEventType eventType, FeedbackType feedbackType, TextBlockType segmentType, Long courseId,
            Long userId, Long exerciseId, Long participationId, Long submissionId) {
        TextAssessmentEvent event = new TextAssessmentEvent();
        event.setId(null);
        event.setEventType(eventType);
        event.setFeedbackType(feedbackType);
        event.setSegmentType(segmentType);
        event.setCourseId(courseId);
        event.setTextExerciseId(exerciseId);
        event.setParticipationId(participationId);
        event.setSubmissionId(submissionId);
        event.setUserId(userId);
        return event;
    }

    /**
     * Generates a list of different combinations of assessment events based on the given parameters
     *
     * @param courseId        the course id of the event
     * @param userId          the userid of the event
     * @param exerciseId      the exercise id of the event
     * @param participationId the participation id of the event
     * @param submissionId    the submission id of the event
     * @return a list of TextAssessment events that are generated
     */
    public static List<TextAssessmentEvent> generateMultipleTextAssessmentEvents(Long courseId, Long userId, Long exerciseId, Long participationId, Long submissionId) {
        List<TextAssessmentEvent> events = new ArrayList<>();
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.VIEW_AUTOMATIC_SUGGESTION_ORIGIN, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC, courseId, userId,
                exerciseId, participationId, submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, FeedbackType.AUTOMATIC, TextBlockType.AUTOMATIC, courseId, userId, exerciseId,
                participationId, submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.HOVER_OVER_IMPACT_WARNING, FeedbackType.MANUAL, TextBlockType.AUTOMATIC, courseId, userId, exerciseId,
                participationId, submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.DELETE_FEEDBACK, FeedbackType.MANUAL, TextBlockType.AUTOMATIC, courseId, userId, exerciseId, participationId,
                submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.ADD_FEEDBACK_AUTOMATICALLY_SELECTED_BLOCK, FeedbackType.MANUAL, TextBlockType.AUTOMATIC, courseId, userId,
                exerciseId, participationId, submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.DELETE_FEEDBACK, FeedbackType.MANUAL, TextBlockType.AUTOMATIC, courseId, userId, exerciseId, participationId,
                submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.ADD_FEEDBACK_MANUALLY_SELECTED_BLOCK, FeedbackType.MANUAL, TextBlockType.MANUAL, courseId, userId,
                exerciseId, participationId, submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.SUBMIT_ASSESSMENT, FeedbackType.MANUAL, TextBlockType.MANUAL, courseId, userId, exerciseId, participationId,
                submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.CLICK_TO_RESOLVE_CONFLICT, FeedbackType.MANUAL, TextBlockType.MANUAL, courseId, userId, exerciseId,
                participationId, submissionId));
        events.add(generateTextAssessmentEvent(TextAssessmentEventType.ASSESS_NEXT_SUBMISSION, FeedbackType.MANUAL, TextBlockType.MANUAL, courseId, userId, exerciseId,
                participationId, submissionId));
        return events;
    }

    /**
     * Generates a real exam with student review dates set
     *
     * @param course the associated course
     * @return the created exam
     */
    public static Exam generateExamWithStudentReviewDates(Course course) {
        Exam exam = generateExamHelper(course, false);
        ZonedDateTime currentTime = now();
        exam.setNumberOfExercisesInExam(1);
        exam.setRandomizeExerciseOrder(false);
        exam.setExamStudentReviewStart(currentTime);
        exam.setExamStudentReviewEnd(currentTime.plusMinutes(60));
        return exam;
    }

    /**
     * Generates a real exam without student review dates set
     *
     * @param course the associated course
     * @return the created exam
     */
    public static Exam generateExam(Course course) {
        return generateExamHelper(course, false);
    }

    /**
     * Generates a test eam (test exams have no student review dates)
     *
     * @param course the associated course
     * @return the created exam
     */
    public static Exam generateTestExam(Course course) {
        return generateExamHelper(course, true);
    }

    /**
     * Helper method to create an exam
     *
     * @param course   the associated course
     * @param testExam Boolean flag to determine whether it is a test exam
     * @return the created Exam
     */
    private static Exam generateExamHelper(Course course, boolean testExam) {
        ZonedDateTime currentTime = now();
        Exam exam = new Exam();
        exam.setTitle((testExam ? "Test " : "Real ") + "exam 1");
        exam.setTestExam(testExam);
        exam.setVisibleDate(currentTime);
        exam.setStartDate(currentTime.plusMinutes(10));
        exam.setEndDate(currentTime.plusMinutes(testExam ? 80 : 60));
        exam.setWorkingTime(3000);
        exam.setStartText("Start Text");
        exam.setEndText("End Text");
        exam.setConfirmationStartText("Confirmation Start Text");
        exam.setConfirmationEndText("Confirmation End Text");
        exam.setMaxPoints(90);
        exam.setNumberOfExercisesInExam(1);
        exam.setRandomizeExerciseOrder(false);
        exam.setNumberOfCorrectionRoundsInExam(testExam ? 0 : 1);
        exam.setCourse(course);
        return exam;
    }

    public static ExerciseGroup generateExerciseGroup(boolean mandatory, Exam exam) {
        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setTitle("Exercise group title");
        exerciseGroup.setIsMandatory(mandatory);
        exam.addExerciseGroup(exerciseGroup);
        return exerciseGroup;
    }

    public static StudentExam generateStudentExam(Exam exam) {
        StudentExam studentExam = new StudentExam();
        studentExam.setExam(exam);
        studentExam.setTestRun(false);
        return studentExam;
    }

    /**
     * Helper Method to generate a studentExam for a test exam
     *
     * @param exam the exam to be linked to the studentExam
     * @return a StudentExam for a test exam
     */
    public static StudentExam generateStudentExamForTestExam(Exam exam) {
        StudentExam studentExam = new StudentExam();
        studentExam.setExam(exam);
        studentExam.setWorkingTime(exam.getWorkingTime());
        studentExam.setTestRun(false);
        return studentExam;
    }

    public static StudentExam generateExamTestRun(Exam exam) {
        StudentExam studentExam = new StudentExam();
        studentExam.setExam(exam);
        studentExam.setTestRun(true);
        return studentExam;
    }

    public static GradingCriterion generateGradingCriterion(String title) {
        var criterion = new GradingCriterion();
        criterion.setTitle(title);
        return criterion;
    }

    public static List<GradingInstruction> generateGradingInstructions(GradingCriterion criterion, int numberOfTestInstructions, int usageCount) {
        var instructions = new ArrayList<GradingInstruction>();
        while (numberOfTestInstructions > 0) {
            var exampleInstruction1 = new GradingInstruction();
            exampleInstruction1.setGradingCriterion(criterion);
            exampleInstruction1.setCredits(1);
            exampleInstruction1.setGradingScale("good test");
            exampleInstruction1.setInstructionDescription("created first instruction with empty criteria for testing");
            exampleInstruction1.setFeedback("test feedback");
            exampleInstruction1.setUsageCount(usageCount);
            instructions.add(exampleInstruction1);
            numberOfTestInstructions--;
        }
        return instructions;
    }

    public static List<Feedback> generateFeedback() {
        List<Feedback> feedbacks = new ArrayList<>();
        Feedback positiveFeedback = createPositiveFeedback(FeedbackType.AUTOMATIC);
        positiveFeedback.setReference("theory");
        feedbacks.add(positiveFeedback);
        Feedback positiveFeedback2 = new Feedback();
        positiveFeedback2.setCredits(1D);
        positiveFeedback2.setReference("theory2");
        positiveFeedback2.setType(FeedbackType.AUTOMATIC);
        feedbacks.add(positiveFeedback2);
        Feedback negativeFeedback = createNegativeFeedback(FeedbackType.AUTOMATIC);
        negativeFeedback.setReference("practice");
        negativeFeedback.setType(FeedbackType.AUTOMATIC);
        feedbacks.add(negativeFeedback);
        return feedbacks;
    }

    public static List<Feedback> generateManualFeedback() {
        List<Feedback> feedbacks = new ArrayList<>();
        Feedback positiveFeedback = createPositiveFeedback(FeedbackType.MANUAL);
        feedbacks.add(positiveFeedback);
        Feedback negativeFeedback = createNegativeFeedback(FeedbackType.MANUAL);
        feedbacks.add(negativeFeedback);
        Feedback unrefFeedback = new Feedback();
        unrefFeedback.setCredits(-1D);
        unrefFeedback.setText("no reference");
        unrefFeedback.setType(FeedbackType.MANUAL_UNREFERENCED);
        feedbacks.add(unrefFeedback);
        return feedbacks;
    }

    @NotNull
    public static Feedback createPositiveFeedback(FeedbackType type) {
        Feedback positiveFeedback = new Feedback();
        positiveFeedback.setCredits(2D);
        positiveFeedback.setText("good");
        positiveFeedback.setType(type);
        return positiveFeedback;
    }

    @NotNull
    public static Feedback createNegativeFeedback(FeedbackType type) {
        Feedback negativeFeedback = new Feedback();
        negativeFeedback.setCredits(-1D);
        negativeFeedback.setText("bad");
        negativeFeedback.setType(type);
        return negativeFeedback;
    }

    @NotNull
    public static Feedback createManualTextFeedback(Double credits, String textBlockReference) {
        Feedback feedback = new Feedback();
        feedback.setCredits(credits);
        feedback.setText("bad");
        feedback.setType(FeedbackType.MANUAL);
        feedback.setReference(textBlockReference);
        return feedback;
    }

    public static List<Feedback> generateStaticCodeAnalysisFeedbackList(int numOfFeedback) {
        List<Feedback> feedbackList = new ArrayList<>();
        for (int i = 0; i < numOfFeedback; i++) {
            feedbackList.add(generateStaticCodeAnalysisFeedback());
        }
        return feedbackList;
    }

    private static Feedback generateStaticCodeAnalysisFeedback() {
        Feedback feedback = new Feedback();
        feedback.setPositive(false);
        feedback.setType(FeedbackType.AUTOMATIC);
        feedback.setText(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER);
        feedback.setReference("Tool");
        feedback.setDetailText("{\"filePath\":\"" + Constants.STUDENT_WORKING_DIRECTORY
                + "/www/withSCA/MergeSort.java\",\"startLine\":9,\"endLine\":9,\"startColumn\":11,\"endColumn\":11,\"rule\":\"rule\",\"category\":\"category\",\"message\":\"message\"}");
        return feedback;
    }

    public static List<Feedback> applySGIonFeedback(Exercise receivedExercise) {
        List<Feedback> feedbacks = ModelFactory.generateFeedback();

        var gradingInstructionWithNoLimit = receivedExercise.getGradingCriteria().get(0).getStructuredGradingInstructions().get(0);
        var gradingInstructionWithLimit = receivedExercise.getGradingCriteria().get(1).getStructuredGradingInstructions().get(0);

        feedbacks.get(0).setGradingInstruction(gradingInstructionWithLimit);
        feedbacks.get(0).setCredits(gradingInstructionWithLimit.getCredits()); // score +1P
        feedbacks.get(1).setGradingInstruction(gradingInstructionWithLimit);
        feedbacks.get(1).setCredits(gradingInstructionWithLimit.getCredits()); // score +1P
        feedbacks.get(2).setGradingInstruction(gradingInstructionWithNoLimit);
        feedbacks.get(2).setCredits(gradingInstructionWithNoLimit.getCredits()); // score +1P
        var moreFeedback = new Feedback();
        moreFeedback.setGradingInstruction(gradingInstructionWithNoLimit);
        moreFeedback.setCredits(gradingInstructionWithNoLimit.getCredits()); // score +1P
        feedbacks.add(moreFeedback);

        return feedbacks; // total score should be 3P
    }

    public static FeedbackConflict generateFeedbackConflictBetweenFeedbacks(Feedback firstFeedback, Feedback secondFeedback) {
        FeedbackConflict feedbackConflict = new FeedbackConflict();
        feedbackConflict.setConflict(true);
        feedbackConflict.setCreatedAt(now());
        feedbackConflict.setFirstFeedback(firstFeedback);
        feedbackConflict.setSecondFeedback(secondFeedback);
        feedbackConflict.setType(FeedbackConflictType.INCONSISTENT_SCORE);
        feedbackConflict.setDiscard(false);
        return feedbackConflict;
    }

    public static ProgrammingExercise generateToBeImportedProgrammingExercise(String title, String shortName, ProgrammingExercise template, Course targetCourse) {
        ProgrammingExercise toBeImported = new ProgrammingExercise();
        toBeImported.setCourse(targetCourse);
        toBeImported.setTitle(title);
        toBeImported.setShortName(shortName);
        toBeImported.setId(template.getId());
        toBeImported.setTestCases(null);
        toBeImported.setStaticCodeAnalysisCategories(null);
        toBeImported.setTotalNumberOfAssessments(template.getTotalNumberOfAssessments());
        toBeImported.setNumberOfComplaints(template.getNumberOfComplaints());
        toBeImported.setNumberOfMoreFeedbackRequests(template.getNumberOfMoreFeedbackRequests());
        toBeImported.setExerciseHints(null);
        toBeImported.setSolutionParticipation(null);
        toBeImported.setTemplateParticipation(null);
        toBeImported.setPublishBuildPlanUrl(template.isPublishBuildPlanUrl());
        toBeImported.setSequentialTestRuns(template.hasSequentialTestRuns());
        toBeImported.setProblemStatement(template.getProblemStatement());
        toBeImported.setMaxPoints(template.getMaxPoints());
        toBeImported.setBonusPoints(template.getBonusPoints());
        toBeImported.setGradingInstructions(template.getGradingInstructions());
        toBeImported.setDifficulty(template.getDifficulty());
        toBeImported.setMode(template.getMode());
        toBeImported.setAssessmentType(template.getAssessmentType());
        toBeImported.setCategories(template.getCategories());
        toBeImported.setPackageName(template.getPackageName());
        toBeImported.setAllowOnlineEditor(template.isAllowOnlineEditor());
        toBeImported.setAllowOfflineIde(template.isAllowOfflineIde());
        toBeImported.setStaticCodeAnalysisEnabled(template.isStaticCodeAnalysisEnabled());
        toBeImported.setTestwiseCoverageEnabled(template.isTestwiseCoverageEnabled());
        toBeImported.setTutorParticipations(null);
        toBeImported.setPosts(null);
        toBeImported.setStudentParticipations(null);
        toBeImported.setNumberOfSubmissions(template.getNumberOfSubmissions());
        toBeImported.setExampleSubmissions(null);
        toBeImported.setTestRepositoryUrl(template.getTestRepositoryUrl());
        toBeImported.setProgrammingLanguage(template.getProgrammingLanguage());
        toBeImported.setProjectType(template.getProjectType());
        toBeImported.setAssessmentDueDate(template.getAssessmentDueDate());
        toBeImported.setAttachments(null);
        toBeImported.setDueDate(template.getDueDate());
        toBeImported.setReleaseDate(template.getReleaseDate());
        toBeImported.setExampleSolutionPublicationDate(null);
        toBeImported.setSequentialTestRuns(template.hasSequentialTestRuns());
        toBeImported.setBuildAndTestStudentSubmissionsAfterDueDate(template.getBuildAndTestStudentSubmissionsAfterDueDate());
        toBeImported.generateAndSetProjectKey();

        return toBeImported;
    }

    public static StudentParticipation generateStudentParticipation(InitializationState initializationState, Exercise exercise, User user) {
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setInitializationState(initializationState);
        studentParticipation.setInitializationDate(now().minusDays(5));
        studentParticipation.setExercise(exercise);
        studentParticipation.setParticipant(user);
        return studentParticipation;
    }

    /**
     * Generates a minimal student participation without a specific user attached.
     *
     * @param initializationState the state of the participation
     * @param exercise            the referenced exercise of the participation
     * @return the StudentParticipation created
     */
    public static StudentParticipation generateStudentParticipationWithoutUser(InitializationState initializationState, Exercise exercise) {
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setInitializationState(initializationState);
        studentParticipation.setInitializationDate(now().minusDays(5));
        studentParticipation.setExercise(exercise);
        return studentParticipation;
    }

    public static ProgrammingExerciseStudentParticipation generateProgrammingExerciseStudentParticipation(InitializationState initializationState, ProgrammingExercise exercise,
            User user) {
        ProgrammingExerciseStudentParticipation studentParticipation = new ProgrammingExerciseStudentParticipation();
        studentParticipation.setInitializationState(initializationState);
        studentParticipation.setInitializationDate(now().minusDays(5));
        studentParticipation.setExercise(exercise);
        studentParticipation.setParticipant(user);
        return studentParticipation;
    }

    public static Result generateResult(boolean rated, double score) {
        Result result = new Result();
        result.setRated(rated);
        result.setScore(score);
        return result;
    }

    public static TextBlock generateTextBlock(int startIndex, int endIndex, String text) {
        final TextBlock textBlock = new TextBlock();
        textBlock.setStartIndex(startIndex);
        textBlock.setEndIndex(endIndex);
        textBlock.setText(text);
        textBlock.computeId();
        return textBlock;
    }

    public static TextBlock generateTextBlock(int startIndex, int endIndex) {
        return generateTextBlock(startIndex, endIndex, "");
    }

    public static SingleUserNotification generateSingleUserNotification(ZonedDateTime notificationDate, User recipient) {
        SingleUserNotification singleUserNotification = new SingleUserNotification();
        singleUserNotification.setNotificationDate(notificationDate);
        singleUserNotification.setRecipient(recipient);
        return singleUserNotification;
    }

    public static GroupNotification generateGroupNotification(ZonedDateTime notificationDate, Course course, GroupNotificationType type) {
        GroupNotification groupNotification = new GroupNotification();
        groupNotification.setNotificationDate(notificationDate);
        groupNotification.setCourse(course);
        groupNotification.setType(type);
        return groupNotification;
    }

    public static SystemNotification generateSystemNotification(ZonedDateTime notificationDate, ZonedDateTime expireDate) {
        SystemNotification systemNotification = new SystemNotification();
        systemNotification.setNotificationDate(notificationDate);
        systemNotification.setExpireDate(expireDate);
        return systemNotification;
    }

    public static ApollonDiagram generateApollonDiagram(DiagramType diagramType, String title) {
        ApollonDiagram apollonDiagram = new ApollonDiagram();
        apollonDiagram.setDiagramType(diagramType);
        apollonDiagram.setTitle(title);
        return apollonDiagram;
    }

    /**
     * Creates a dummy DTO used by Jenkins, which notifies about new programming exercise results.
     *
     * @param fullName                    full name of the build (includes Folder, Job and Build number)
     * @param repoName                    name of the repository
     * @param buildRunDate                the date of the build run, can be null
     * @param programmingLanguage         programming language to use
     * @param enableStaticAnalysisReports should the notification include static analysis reports
     * @param successfulTestNames         names of successful tests
     * @param failedTestNames             names of failed tests
     * @param logs                        the logs produced by the test result
     * @param commits                     the involved commits, can be null or empty
     * @param testSuiteDto                the test suite
     * @return TestResultDTO with dummy data
     */
    public static TestResultsDTO generateTestResultDTO(String fullName, String repoName, ZonedDateTime buildRunDate, ProgrammingLanguage programmingLanguage,
            boolean enableStaticAnalysisReports, List<String> successfulTestNames, List<String> failedTestNames, List<String> logs, List<CommitDTO> commits,
            TestSuiteDTO testSuiteDto) {

        final var testSuite = new TestSuiteDTO("TestSuiteName1", now().toEpochSecond(), 0, 0, failedTestNames.size(), successfulTestNames.size() + failedTestNames.size(),
                new ArrayList<>());
        testSuite.testCases().addAll(successfulTestNames.stream().map(name -> new TestCaseDTO(name, "Class", 0d)).toList());
        testSuite.testCases().addAll(failedTestNames.stream()
                .map(name -> new TestCaseDTO(name, "Class", 0d, new ArrayList<>(), List.of(new TestCaseDetailMessageDTO(name + " error message")), new ArrayList<>())).toList());

        final var commitDTO = new CommitDTO(TestConstants.COMMIT_HASH_STRING, repoName, DEFAULT_BRANCH);
        final var staticCodeAnalysisReports = enableStaticAnalysisReports ? generateStaticCodeAnalysisReports(programmingLanguage) : new ArrayList<StaticCodeAnalysisReportDTO>();

        return new TestResultsDTO(successfulTestNames.size(), 0, 0, failedTestNames.size(), fullName, commits != null && commits.size() > 0 ? commits : List.of(commitDTO),
                List.of(testSuiteDto != null ? testSuiteDto : testSuite), staticCodeAnalysisReports, List.of(), buildRunDate != null ? buildRunDate : now(), false, logs);
    }

    /**
     * Creates a dummy DTO with custom feedbacks used by Jenkins, which notifies about new programming exercise results.
     * Uses {@link #generateTestResultDTO(String, String, ZonedDateTime, ProgrammingLanguage, boolean, List, List, List, List, TestSuiteDTO)} as basis.
     * Then adds a new {@link TestSuiteDTO} with name "CustomFeedbacks" to it.
     * This Testsuite has four {@link TestCaseDTO}s:
     * <ul>
     *     <li>CustomSuccessMessage: successful test with a message</li>
     *     <li>CustomSuccessNoMessage: successful test without message</li>
     *     <li>CustomFailedMessage: failed test with a message</li>
     * </ul>
     *
     * @param repoName                    name of the repository
     * @param successfulTestNames         names of successful tests
     * @param failedTestNames             names of failed tests
     * @param programmingLanguage         programming language to use
     * @param enableStaticAnalysisReports should the notification include static analysis reports
     * @return TestResultDTO with dummy data
     */
    public static TestResultsDTO generateTestResultsDTOWithCustomFeedback(String repoName, List<String> successfulTestNames, List<String> failedTestNames,
            ProgrammingLanguage programmingLanguage, boolean enableStaticAnalysisReports) {

        final List<TestCaseDTO> testCases = new ArrayList<>();

        // successful with message
        {
            var testCase = new TestCaseDTO("CustomSuccessMessage", null, 0d);
            testCase.successInfos().add(new TestCaseDetailMessageDTO("Successful test with message"));
            testCases.add(testCase);
        }

        // successful without message
        {
            var testCase = new TestCaseDTO("CustomSuccessNoMessage", null, 0d);
            testCase.successInfos().add(new TestCaseDetailMessageDTO(null));
            testCases.add(testCase);
        }

        // failed with message
        {
            var testCase = new TestCaseDTO("CustomFailedMessage", null, 0d);
            testCase.failures().add(new TestCaseDetailMessageDTO("Failed test with message"));
            testCases.add(testCase);
        }

        // failed without message
        {
            var testCase = new TestCaseDTO("CustomFailedNoMessage", null, 0d);
            testCase.failures().add(new TestCaseDetailMessageDTO(null));
            testCases.add(testCase);
        }
        var testSuite = new TestSuiteDTO("customFeedbacks", 0d, 0, 0, failedTestNames.size(), successfulTestNames.size() + failedTestNames.size(), testCases);
        return generateTestResultDTO(null, repoName, null, programmingLanguage, enableStaticAnalysisReports, successfulTestNames, failedTestNames, new ArrayList<>(),
                new ArrayList<>(), testSuite);
    }

    public static BambooBuildResultNotificationDTO generateBambooBuildResult(String repoName, String planKey, String testSummaryDescription, ZonedDateTime buildCompletionDate,
            List<String> successfulTestNames, List<String> failedTestNames, List<BambooBuildResultNotificationDTO.BambooVCSDTO> vcsDtos) {
        return generateBambooBuildResult(repoName, planKey, testSummaryDescription, buildCompletionDate, successfulTestNames, failedTestNames, vcsDtos, failedTestNames.isEmpty());
    }

    public static BambooBuildResultNotificationDTO generateBambooBuildResult(String repoName, String planKey, String testSummaryDescription, ZonedDateTime buildCompletionDate,
            List<String> successfulTestNames, List<String> failedTestNames, List<BambooBuildResultNotificationDTO.BambooVCSDTO> vcsDtos, boolean successful) {

        final var summary = new BambooBuildResultNotificationDTO.BambooTestSummaryDTO(42, 0, failedTestNames.size(), failedTestNames.size(), 0, successfulTestNames.size(),
                testSummaryDescription, 0, 0, successfulTestNames.size() + failedTestNames.size(), failedTestNames.size());

        final var successfulTests = successfulTestNames.stream().map(name -> generateBambooTestJob(name, true)).toList();
        final var failedTests = failedTestNames.stream().map(name -> generateBambooTestJob(name, false)).toList();
        final var job = new BambooBuildResultNotificationDTO.BambooJobDTO(42, failedTests, successfulTests, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final var vcs = new BambooBuildResultNotificationDTO.BambooVCSDTO(TestConstants.COMMIT_HASH_STRING, repoName, DEFAULT_BRANCH, new ArrayList<>());
        final var plan = new BambooBuildPlanDTO(planKey != null ? planKey : "TEST201904BPROGRAMMINGEXERCISE6-STUDENT1");

        final var build = new BambooBuildResultNotificationDTO.BambooBuildDTO(false, 42, "foobar", buildCompletionDate != null ? buildCompletionDate : now().minusSeconds(5),
                successful, summary, vcsDtos != null && vcsDtos.size() > 0 ? vcsDtos : List.of(vcs), List.of(job));

        return new BambooBuildResultNotificationDTO("secret", "TestNotification", plan, build);
    }

    /**
     * Generate a Bamboo notification with build logs of various sizes
     *
     * @param buildPlanKey        the key of the build plan
     * @param repoName            repository name
     * @param successfulTestNames names of successful tests
     * @param failedTestNames     names of failed tests
     * @param buildCompletionDate the completion date of the build
     * @param vcsDtos             the vcs objects containing commit information
     * @return notification with build logs
     */
    public static BambooBuildResultNotificationDTO generateBambooBuildResultWithLogs(String buildPlanKey, String repoName, List<String> successfulTestNames,
            List<String> failedTestNames, ZonedDateTime buildCompletionDate, List<BambooBuildResultNotificationDTO.BambooVCSDTO> vcsDtos) {
        return generateBambooBuildResultWithLogs(buildPlanKey, repoName, successfulTestNames, failedTestNames, buildCompletionDate, vcsDtos, failedTestNames.isEmpty());
    }

    public static BambooBuildResultNotificationDTO generateBambooBuildResultWithLogs(String buildPlanKey, String repoName, List<String> successfulTestNames,
            List<String> failedTestNames, ZonedDateTime buildCompletionDate, List<BambooBuildResultNotificationDTO.BambooVCSDTO> vcsDtos, boolean successful) {
        var notification = generateBambooBuildResult(repoName, buildPlanKey, "No tests found", buildCompletionDate, successfulTestNames, failedTestNames, vcsDtos, successful);

        String logWith254Chars = "a".repeat(254);

        var buildLogDTO254Chars = new BambooBuildLogDTO(now(), logWith254Chars, null);
        var buildLogDTO255Chars = new BambooBuildLogDTO(now(), logWith254Chars + "a", null);
        var buildLogDTO256Chars = new BambooBuildLogDTO(now(), logWith254Chars + "aa", null);
        var largeBuildLogDTO = new BambooBuildLogDTO(now(), logWith254Chars + logWith254Chars, null);
        var logTypicalErrorLog = new BambooBuildLogDTO(now(), "error: the java class ABC does not exist", null);
        var logTypicalDuplicatedErrorLog = new BambooBuildLogDTO(now(), "error: the java class ABC does not exist", null);
        var logCompilationError = new BambooBuildLogDTO(now(), "COMPILATION ERROR", null);
        var logBuildError = new BambooBuildLogDTO(now(), "BUILD FAILURE", null);
        var logWarning = new BambooBuildLogDTO(now(), "[WARNING]", null);
        var logWarningIllegalReflectiveAccess = new BambooBuildLogDTO(now(), "WARNING: Illegal reflective access by", null);

        notification.getBuild().jobs().get(0).logs().addAll(List.of(buildLogDTO254Chars, buildLogDTO255Chars, buildLogDTO256Chars, largeBuildLogDTO, logTypicalErrorLog,
                logTypicalDuplicatedErrorLog, logWarning, logWarningIllegalReflectiveAccess, logCompilationError, logBuildError));

        return notification;
    }

    public static BambooBuildResultNotificationDTO generateBambooBuildResultWithAnalyticsLogs(String buildPlanKey, String repoName, List<String> successfulTestNames,
            List<String> failedTestNames, ZonedDateTime buildCompletionDate, List<BambooBuildResultNotificationDTO.BambooVCSDTO> vcsDtos, boolean sca) {
        var notification = generateBambooBuildResult(repoName, buildPlanKey, "Test executed", buildCompletionDate, successfulTestNames, failedTestNames, vcsDtos, true);

        var jobStarted = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 14, 58, 30, 0, ZoneId.systemDefault()), "started building on agent 1", null);

        var executingBuild = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 0, 0, ZoneId.systemDefault()), "Executing build", null);

        var testingStarted = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 5, 0, ZoneId.systemDefault()), "Starting task 'Tests'", null);

        var dependency1Downloaded = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 10, 0, ZoneId.systemDefault()), "Dependency 1 Downloaded from", null);

        var testingFinished = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 15, 0, ZoneId.systemDefault()), "Finished task 'Tests' with result", null);

        var scaStarted = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 16, 0, ZoneId.systemDefault()), "Starting task 'Static Code Analysis'", null);

        var dependency2Downloaded = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 20, 0, ZoneId.systemDefault()), "Dependency 2 Downloaded from", null);

        var scaFinished = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 27, 0, ZoneId.systemDefault()), "Finished task 'Static Code Analysis'", null);

        var jobFinished = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 30, 0, ZoneId.systemDefault()), "Finished building", null);

        notification.getBuild().jobs().get(0).logs().clear();
        if (sca) {
            notification.getBuild().jobs().get(0).logs().addAll(
                    List.of(jobStarted, executingBuild, testingStarted, dependency1Downloaded, testingFinished, scaStarted, dependency2Downloaded, scaFinished, jobFinished));
        }
        else {
            notification.getBuild().jobs().get(0).logs().addAll(List.of(jobStarted, executingBuild, testingStarted, dependency1Downloaded, testingFinished, jobFinished));
        }

        return notification;
    }

    public static Feedback createSCAFeedbackWithInactiveCategory(Result result) {
        return new Feedback().result(result).text(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER).reference("CHECKSTYLE").detailText("{\"category\": \"miscellaneous\"}")
                .type(FeedbackType.AUTOMATIC).positive(false);
    }

    public static BambooBuildResultNotificationDTO generateBambooBuildResultWithStaticCodeAnalysisReport(String repoName, List<String> successfulTestNames,
            List<String> failedTestNames, ProgrammingLanguage programmingLanguage) {
        var notification = generateBambooBuildResult(repoName, null, null, null, successfulTestNames, failedTestNames, new ArrayList<>(), true);
        var reports = generateStaticCodeAnalysisReports(programmingLanguage);
        notification.getBuild().jobs().get(0).staticCodeAnalysisReports().addAll(reports);
        return notification;
    }

    public static List<StaticCodeAnalysisReportDTO> generateStaticCodeAnalysisReports(ProgrammingLanguage language) {
        return StaticCodeAnalysisTool.getToolsForProgrammingLanguage(language).stream().map(ModelFactory::generateStaticCodeAnalysisReport).toList();
    }

    private static StaticCodeAnalysisReportDTO generateStaticCodeAnalysisReport(StaticCodeAnalysisTool tool) {
        var report = new StaticCodeAnalysisReportDTO();
        report.setTool(tool);
        report.setIssues(List.of(generateStaticCodeAnalysisIssue(tool)));
        return report;
    }

    private static StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue generateStaticCodeAnalysisIssue(StaticCodeAnalysisTool tool) {
        // Use a category which is not invisible in the default configuration
        String category = switch (tool) {
            case SPOTBUGS -> "BAD_PRACTICE";
            case PMD -> "Best Practices";
            case CHECKSTYLE -> "coding";
            case PMD_CPD -> "Copy/Paste Detection";
            case SWIFTLINT -> "swiftLint"; // TODO: rene: set better value after categories are better defined
            case GCC -> "Memory";
        };

        var issue = new StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue();
        issue.setFilePath(Constants.STUDENT_WORKING_DIRECTORY + "/www/packagename/Class1.java");
        issue.setStartLine(1);
        issue.setEndLine(2);
        issue.setStartColumn(1);
        issue.setEndColumn(10);
        issue.setRule("Rule");
        issue.setCategory(category);
        issue.setMessage("Message");
        issue.setPriority("Priority");

        return issue;
    }

    public static StaticCodeAnalysisCategory generateStaticCodeAnalysisCategory(ProgrammingExercise programmingExercise, String name, CategoryState state, Double penalty,
            Double maxPenalty) {
        var category = new StaticCodeAnalysisCategory();
        category.setName(name);
        category.setPenalty(penalty);
        category.setMaxPenalty(maxPenalty);
        category.setState(state);
        category.setProgrammingExercise(programmingExercise);
        return category;
    }

    private static BambooBuildResultNotificationDTO.BambooTestJobDTO generateBambooTestJob(String name, boolean successful) {
        return new BambooBuildResultNotificationDTO.BambooTestJobDTO(name, name, "SpringTestClass", successful ? List.of() : List.of("bad solution, did not work"));
    }

    /**
     * Generates example TextSubmissions
     *
     * @param count How many submissions should be generated (max. 10)
     * @return A list containing the generated TextSubmissions
     */
    public static List<TextSubmission> generateTextSubmissions(int count) {
        if (count > 10) {
            throw new IllegalArgumentException();
        }

        // Example texts for submissions
        String[] submissionTexts = {
                "Differences: \nAntipatterns: \n-Have one problem and two solutions(one problematic and one refactored)\n-Antipatterns are a sign of bad architecture and bad coding \nPattern:\n-Have one problem and one solution\n-Patterns are a sign of elaborated architecutre and coding",
                "The main difference between patterns and antipatterns is, that patterns show you a good way to do something and antipatterns show a bad way to do something. Nevertheless patterns may become antipatterns in the course of changing understanding of how good software engineering looks like. One example for that is functional decomposition, which used to be a pattern and \"good practice\". Over the time it turned out that it is not a goog way to solve problems, so it became a antipattern.\n\nA pattern itsself is a proposed solution to a problem that occurs often and in different situations.\nIn contrast to that a antipattern shows commonly made mistakes when dealing with a certain problem. Nevertheless a refactored solution is aswell proposed.",
                "1.Patterns can evolve into Antipatterns when change occurs\\n2. Pattern has one solution, whereas anti pattern can have subtypes of solution\\n3. Antipattern has negative consequences and symptom, where as patterns looks only into benefits and consequences",
                "Patterns: A way to Model code in differents ways \nAntipattern: A way of how Not to Model code",
                "Antipatterns are used when there are common mistakes in software management and development to find these, while patterns by themselves are used to build software systems in the context of frequent change by reducing complexity and isolating the change.\nAnother difference is that the antipatterns have problematic solution and then refactored solution, while patterns only have a solution.",
                "- In patterns we have a problem and a solution, in antipatterns we have a problematic solution and a refactored solution instead\n- patterns represent best practices from the industry etc. so proven concepts, whereas antipatterns shed a light on common mistakes during software development etc.",
                "1) Patterns have one solution, antipatterns have to solutions (one problematic and one refactored).\n2) for the coice of patterns code has to be written; for antipatterns, the bad smell code already exists",
                "Design Patterns:\n\nSolutions which are productive and efficient and are developed by Software Engineers over the years of practice and solving problems.\n\nAnti Patterns:\n\nKnown solutions which are actually bad or defective to certain kind of problems.",
                "Patterns has one problem and one solution.\nAntipatterns have one problematic solution and a solution for that. The antipattern happens when  a solution that is been used for a long time can not apply anymore. ",
                "Patterns identify problems and present solutions.\nAntipatterns identify problems but two kinds of solutions. One problematic solution and a better \"refactored\" version of the solution. Problematic solutions are suggested not to be used because they results in smells or hinder future work." };

        // Create Submissions with id's 0 - count
        List<TextSubmission> textSubmissions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TextSubmission textSubmission = new TextSubmission((long) i).text(submissionTexts[i]);
            textSubmission.setLanguage(Language.ENGLISH);
            textSubmissions.add(textSubmission);
        }

        return textSubmissions;
    }

    /**
     * Generate an example organization entity
     *
     * @param name         of organization
     * @param shortName    of organization
     * @param url          of organization
     * @param description  of organization
     * @param logoUrl      of organization
     * @param emailPattern of organization
     * @return An organization entity
     */
    public static Organization generateOrganization(String name, String shortName, String url, String description, String logoUrl, String emailPattern) {
        Organization organization = new Organization();
        organization.setName(name);
        organization.setShortName(shortName);
        organization.setUrl(url);
        organization.setDescription(description);
        organization.setLogoUrl(logoUrl);
        organization.setEmailPattern(emailPattern);
        return organization;
    }

    /**
     * Generates a Bonus instance with given arguments.
     *
     * @param bonusStrategy       of bonus
     * @param weight              of bonus
     * @param sourceGradingScaleId  of sourceGradingScale of bonus
     * @param bonusToGradingScaleId of bonusToGradingScale bonus
     * @return a new Bonus instance associated with the gradins scales corresonding to ids bonusToGradingScaleId and bonusToGradingScaleId.
     */
    public static Bonus generateBonus(BonusStrategy bonusStrategy, Double weight, long sourceGradingScaleId, long bonusToGradingScaleId) {
        Bonus bonus = new Bonus();
        bonus.setBonusStrategy(bonusStrategy);
        bonus.setWeight(weight);
        // New object is created to avoid circular dependency on json serialization.
        var sourceGradingScale = new GradingScale();
        sourceGradingScale.setId(sourceGradingScaleId);
        bonus.setSourceGradingScale(sourceGradingScale);

        // New object is created to avoid circular dependency on json serialization.
        var bonusToGradingScale = new GradingScale();
        bonusToGradingScale.setId(bonusToGradingScaleId);
        bonus.setBonusToGradingScale(bonusToGradingScale);

        return bonus;

    }
}
