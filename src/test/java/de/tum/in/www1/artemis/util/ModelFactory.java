package de.tum.in.www1.artemis.util;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
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
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildLogDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildPlanDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.connectors.jenkins.dto.*;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

public class ModelFactory {

    public static final String USER_PASSWORD = "0000";

    public static Lecture generateLecture(ZonedDateTime startDate, ZonedDateTime endDate, Course course) {
        Lecture lecture = new Lecture();
        lecture.setStartDate(startDate);
        lecture.setDescription("Description");
        lecture.setTitle("Lecture");
        lecture.setEndDate(endDate);
        lecture.setCourse(course);
        return lecture;
    }

    public static Attachment generateAttachment(ZonedDateTime startDate, Lecture lecture) {
        Attachment attachment = new Attachment();
        attachment.setAttachmentType(AttachmentType.FILE);
        attachment.setReleaseDate(startDate);
        attachment.setUploadDate(startDate);
        attachment.setName("TestAttachement");
        attachment.setLecture(lecture);
        return attachment;
    }

    public static QuizExercise generateQuizExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, Course course) {
        QuizExercise quizExercise = new QuizExercise();
        quizExercise = (QuizExercise) populateExercise(quizExercise, releaseDate, dueDate, null, course);
        quizExercise.setProblemStatement(null);
        quizExercise.setGradingInstructions(null);
        quizExercise.setPresentationScoreEnabled(false);
        quizExercise.setIsOpenForPractice(false);
        quizExercise.setIsPlannedToStart(true);
        quizExercise.setIsVisibleBeforeStart(true);
        quizExercise.setAllowedNumberOfAttempts(1);
        quizExercise.setDuration(10);
        quizExercise.setRandomizeQuestionOrder(true);
        return quizExercise;
    }

    public static QuizExercise generateQuizExerciseForExam(ExerciseGroup exerciseGroup) {
        QuizExercise quizExercise = new QuizExercise();
        quizExercise = (QuizExercise) populateExerciseForExam(quizExercise, exerciseGroup);
        quizExercise.setProblemStatement(null);
        quizExercise.setGradingInstructions(null);
        quizExercise.setPresentationScoreEnabled(false);
        quizExercise.setIsOpenForPractice(false);
        quizExercise.setIsPlannedToStart(false);
        quizExercise.setIsVisibleBeforeStart(true);
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
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise = (ProgrammingExercise) populateExercise(programmingExercise, releaseDate, dueDate, null, course);
        populateProgrammingExercise(programmingExercise, programmingLanguage);
        return programmingExercise;
    }

    public static ProgrammingExercise generateProgrammingExerciseForExam(ExerciseGroup exerciseGroup) {
        return generateProgrammingExerciseForExam(exerciseGroup, ProgrammingLanguage.JAVA);
    }

    public static ProgrammingExercise generateProgrammingExerciseForExam(ExerciseGroup exerciseGroup, ProgrammingLanguage programmingLanguage) {
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise = (ProgrammingExercise) populateExerciseForExam(programmingExercise, exerciseGroup);
        populateProgrammingExercise(programmingExercise, programmingLanguage);
        return programmingExercise;
    }

    private static void populateProgrammingExercise(ProgrammingExercise programmingExercise, ProgrammingLanguage programmingLanguage) {
        programmingExercise.generateAndSetProjectKey();
        programmingExercise.setAllowOfflineIde(true);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExercise.setProgrammingLanguage(programmingLanguage);
        if (programmingLanguage == ProgrammingLanguage.JAVA) {
            programmingExercise.setProjectType(ProjectType.ECLIPSE);
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
    }

    public static ModelingExercise generateModelingExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, DiagramType diagramType,
            Course course) {
        ModelingExercise modelingExercise = new ModelingExercise();
        modelingExercise = (ModelingExercise) populateExercise(modelingExercise, releaseDate, dueDate, assessmentDueDate, course);
        modelingExercise.setDiagramType(diagramType);
        return modelingExercise;
    }

    public static ModelingExercise generateModelingExerciseForExam(DiagramType diagramType, ExerciseGroup exerciseGroup) {
        ModelingExercise modelingExercise = new ModelingExercise();
        modelingExercise = (ModelingExercise) populateExerciseForExam(modelingExercise, exerciseGroup);
        modelingExercise.setDiagramType(diagramType);
        return modelingExercise;
    }

    public static TextExercise generateTextExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        TextExercise textExercise = new TextExercise();
        return (TextExercise) populateExercise(textExercise, releaseDate, dueDate, assessmentDueDate, course);
    }

    public static TextExercise generateTextExerciseForExam(ExerciseGroup exerciseGroup) {
        TextExercise textExercise = new TextExercise();
        return (TextExercise) populateExerciseForExam(textExercise, exerciseGroup);
    }

    public static FileUploadExercise generateFileUploadExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, String filePattern,
            Course course) {
        FileUploadExercise fileUploadExercise = new FileUploadExercise();
        fileUploadExercise.setFilePattern(filePattern);
        return (FileUploadExercise) populateExercise(fileUploadExercise, releaseDate, dueDate, assessmentDueDate, course);
    }

    public static FileUploadExercise generateFileUploadExerciseForExam(String filePattern, ExerciseGroup exerciseGroup) {
        FileUploadExercise fileUploadExercise = new FileUploadExercise();
        fileUploadExercise.setFilePattern(filePattern);
        return (FileUploadExercise) populateExerciseForExam(fileUploadExercise, exerciseGroup);
    }

    private static Exercise populateExercise(Exercise exercise, ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        exercise.setTitle(UUID.randomUUID().toString());
        exercise.setShortName("t" + UUID.randomUUID().toString().substring(0, 3));
        exercise.setProblemStatement("Problem Statement");
        exercise.setMaxPoints(5.0);
        exercise.setBonusPoints(0.0);
        exercise.setReleaseDate(releaseDate);
        exercise.setDueDate(dueDate);
        exercise.assessmentDueDate(assessmentDueDate);
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
        exercise.assessmentDueDate(null);
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

    public static List<User> generateActivatedUsers(String loginPrefix, String[] groups, Set<Authority> authorities, int amount) {
        List<User> generatedUsers = new ArrayList<>();
        for (int i = 1; i <= amount; i++) {
            User user = ModelFactory.generateActivatedUser(loginPrefix + i);
            if (groups != null) {
                user.setGroups(Set.of(groups));
                user.setAuthorities(authorities);
            }
            generatedUsers.add(user);
        }
        return generatedUsers;
    }

    /**
     * Generate users that has registration numbers
     *
     * @param loginPrefix prefix that will be added in front of every user's login
     * @param groups groups that the users will be added
     * @param authorities authorities that the users will have
     * @param amount amount of users to generate
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
     * @param exercise exercise of the team
     * @param name name of the team
     * @param shortName short name of the team
     * @param loginPrefix prefix that will be added in front of every user's login
     * @param numberOfStudents amount of users to generate for team as students
     * @param owner owner of the team generally a tutor
     * @param creatorLogin login of user that creates the teams
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
     * @param exercise exercise of the team
     * @param name name of the team
     * @param shortName short name of the team
     * @param numberOfStudents amount of users to generate for team as students
     * @param owner owner of the team generally a tutor
     * @return team that was generated
     */
    public static Team generateTeamForExercise(Exercise exercise, String name, String shortName, int numberOfStudents, User owner) {
        return generateTeamForExercise(exercise, name, shortName, "student", numberOfStudents, owner, null, "R");
    }

    /**
     * Generate teams
     *
     * @param exercise exercise of the teams
     * @param shortNamePrefix prefix that will be added in front of every team's short name
     * @param loginPrefix prefix that will be added in front of every student's login
     * @param numberOfTeams amount of teams to generate
     * @param owner owner of the teams generally a tutor
     * @param creatorLogin login of user that created the teams
     * @return teams that were generated
     */
    public static List<Team> generateTeamsForExercise(Exercise exercise, String shortNamePrefix, String loginPrefix, int numberOfTeams, User owner, String creatorLogin) {
        return generateTeamsForExercise(exercise, shortNamePrefix, loginPrefix, numberOfTeams, owner, creatorLogin, "R");
    }

    /**
     * Generate teams
     *
     * @param exercise exercise of the teams
     * @param shortNamePrefix prefix that will be added in front of every team's short name
     * @param loginPrefix prefix that will be added in front of every student's login
     * @param numberOfTeams amount of teams to generate
     * @param owner owner of the teams generally a tutor
     * @param creatorLogin login of user that created the teams
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
     * @param exercise exercise of the teams
     * @param shortNamePrefix prefix that will be added in front of every team's short name
     * @param loginPrefix prefix that will be added in front of every student's login
     * @param numberOfTeams amount of teams to generate
     * @param owner owner of the teams generally a tutor
     * @param creatorLogin login of user that created the teams
     * @param registrationPrefix prefix that will be added in front of every student's registration number
     * @param teamSize size of each individual team
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
            textSubmission.setSubmissionDate(ZonedDateTime.now().minusDays(1));
        }
        return textSubmission;
    }

    public static TextSubmission generateLateTextSubmission(String text, Language language) {
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.text(text);
        textSubmission.setLanguage(language);
        textSubmission.setSubmitted(true);
        textSubmission.setSubmissionDate(ZonedDateTime.now().plusDays(1));
        return textSubmission;
    }

    public static ProgrammingSubmission generateProgrammingSubmission(boolean submitted, String commitHash, SubmissionType type, @Nullable ZonedDateTime submissionDate) {
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setSubmitted(submitted);
        if (submitted) {
            programmingSubmission.setSubmissionDate(ZonedDateTime.now().minusDays(1));
        }
        programmingSubmission.setCommitHash(commitHash);
        programmingSubmission.setType(type);
        return programmingSubmission;
    }

    public static ProgrammingSubmission generateProgrammingSubmission(boolean submitted) {
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setSubmitted(submitted);
        if (submitted) {
            programmingSubmission.setSubmissionDate(ZonedDateTime.now().minusDays(1));
        }
        return programmingSubmission;
    }

    public static FileUploadSubmission generateFileUploadSubmission(boolean submitted) {
        FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();
        fileUploadSubmission.setSubmitted(submitted);
        if (submitted) {
            fileUploadSubmission.setSubmissionDate(ZonedDateTime.now().minusDays(1));
        }
        return fileUploadSubmission;
    }

    public static FileUploadSubmission generateFileUploadSubmissionWithFile(boolean submitted, String filePath) {
        FileUploadSubmission fileUploadSubmission = generateFileUploadSubmission(submitted);
        fileUploadSubmission.setFilePath(filePath);
        if (submitted) {
            fileUploadSubmission.setSubmissionDate(ZonedDateTime.now().minusDays(1));
        }
        return fileUploadSubmission;
    }

    public static FileUploadSubmission generateLateFileUploadSubmission() {
        FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();
        fileUploadSubmission.setSubmitted(true);
        fileUploadSubmission.setSubmissionDate(ZonedDateTime.now().plusDays(1));
        return fileUploadSubmission;
    }

    public static ModelingSubmission generateModelingSubmission(String model, boolean submitted) {
        ModelingSubmission submission = new ModelingSubmission();
        submission.setModel(model);
        submission.setSubmitted(submitted);
        if (submitted) {
            submission.setSubmissionDate(ZonedDateTime.now().minusDays(1));
        }
        return submission;
    }

    public static QuizSubmission generateQuizSubmission(boolean submitted) {
        QuizSubmission submission = new QuizSubmission();
        submission.setSubmitted(submitted);
        if (submitted) {
            submission.setSubmissionDate(ZonedDateTime.now().minusDays(1));
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
        return generateCourse(id, startDate, endDate, exercises, studentGroupName, teachingAssistantGroupName, editorGroupName, instructorGroupName, 3, 3, 7, true, 7);
    }

    public static Course generateCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises, String studentGroupName,
            String teachingAssistantGroupName, String editorGroupName, String instructorGroupName, Integer maxComplaints, Integer maxTeamComplaints, Integer maxComplaintTimeDays,
            boolean postsEnabled, int requestMoreFeedbackTimeDays) {
        Course course = new Course();
        course.setId(id);
        course.setTitle("Course title " + UUID.randomUUID().toString());

        // must start with a letter
        course.setShortName("short" + UUID.randomUUID().toString().replace("-", "0"));
        course.setMaxComplaints(maxComplaints);
        course.setMaxTeamComplaints(maxTeamComplaints);
        course.setMaxComplaintTimeDays(maxComplaintTimeDays);
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
        course.setPresentationScore(2);
        return course;
    }

    public static Exam generateExamWithStudentReviewDates(Course course) {
        ZonedDateTime currentTime = ZonedDateTime.now();
        Exam exam = new Exam();
        exam.setTitle("Test exam 1");
        exam.setVisibleDate(currentTime);
        exam.setStartDate(currentTime.plusMinutes(10));
        exam.setEndDate(currentTime.plusMinutes(60));
        exam.setStartText("Start Text");
        exam.setEndText("End Text");
        exam.setConfirmationStartText("Confirmation Start Text");
        exam.setConfirmationEndText("Confirmation End Text");
        exam.setMaxPoints(90);
        exam.setNumberOfExercisesInExam(1);
        exam.setRandomizeExerciseOrder(false);
        exam.setExamStudentReviewStart(currentTime);
        exam.setExamStudentReviewEnd(currentTime.plusMinutes(60));
        exam.setCourse(course);
        return exam;
    }

    public static Exam generateExam(Course course) {
        ZonedDateTime currentTime = ZonedDateTime.now();
        Exam exam = new Exam();
        exam.setTitle("Test exam 1");
        exam.setVisibleDate(currentTime);
        exam.setStartDate(currentTime.plusMinutes(10));
        exam.setEndDate(currentTime.plusMinutes(60));
        exam.setStartText("Start Text");
        exam.setEndText("End Text");
        exam.setConfirmationStartText("Confirmation Start Text");
        exam.setConfirmationEndText("Confirmation End Text");
        exam.setMaxPoints(90);
        exam.setNumberOfExercisesInExam(1);
        exam.setRandomizeExerciseOrder(false);
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
        var exampleInstruction1 = new GradingInstruction();
        while (numberOfTestInstructions > 0) {
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

    public static List<Feedback> applySGIonFeedback(Exercise receivedExercise) throws Exception {
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
        feedbackConflict.setCreatedAt(ZonedDateTime.now());
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
        toBeImported.setStaticCodeAnalysisEnabled(template.isStaticCodeAnalysisEnabled());
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
        toBeImported.setSequentialTestRuns(template.hasSequentialTestRuns());
        toBeImported.setBuildAndTestStudentSubmissionsAfterDueDate(template.getBuildAndTestStudentSubmissionsAfterDueDate());
        toBeImported.generateAndSetProjectKey();

        return toBeImported;
    }

    public static StudentParticipation generateStudentParticipation(InitializationState initializationState, Exercise exercise, User user) {
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setInitializationState(initializationState);
        studentParticipation.setInitializationDate(ZonedDateTime.now().minusDays(5));
        studentParticipation.setExercise(exercise);
        studentParticipation.setParticipant(user);
        return studentParticipation;
    }

    public static ProgrammingExerciseStudentParticipation generateProgrammingExerciseStudentParticipation(InitializationState initializationState, ProgrammingExercise exercise,
            User user) {
        ProgrammingExerciseStudentParticipation studentParticipation = new ProgrammingExerciseStudentParticipation();
        studentParticipation.setInitializationState(initializationState);
        studentParticipation.setInitializationDate(ZonedDateTime.now().minusDays(5));
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
     * @param repoName name of the repository
     * @param successfulTestNames names of successful tests
     * @param failedTestNames names of failed tests
     * @param programmingLanguage programming language to use
     * @param enableStaticAnalysisReports should the notification include static analysis reports
     * @return TestResultDTO with dummy data
     */
    public static TestResultsDTO generateTestResultDTO(String repoName, List<String> successfulTestNames, List<String> failedTestNames, ProgrammingLanguage programmingLanguage,
            boolean enableStaticAnalysisReports) {
        var notification = new TestResultsDTO();

        var testSuite = new TestsuiteDTO();
        testSuite.setName("TestSuiteName1");
        testSuite.setTime(ZonedDateTime.now().toEpochSecond());
        testSuite.setErrors(0);
        testSuite.setSkipped(0);
        testSuite.setFailures(failedTestNames.size());
        testSuite.setTests(successfulTestNames.size() + failedTestNames.size());
        testSuite.setTestCases(successfulTestNames.stream().map(name -> {
            var testcase = new TestCaseDTO();
            testcase.setName(name);
            testcase.setClassname("Class");
            return testcase;
        }).collect(Collectors.toList()));
        testSuite.getTestCases().addAll(failedTestNames.stream().map(name -> {
            var testcase = new TestCaseDTO();
            testcase.setName(name);
            testcase.setClassname("Class");
            var error = new TestCaseDetailMessageDTO();
            error.setMessage(name + " error message");
            testcase.setErrors(List.of(error));
            return testcase;
        }).collect(Collectors.toList()));

        var commitDTO = new CommitDTO();
        commitDTO.setHash(TestConstants.COMMIT_HASH_STRING);
        commitDTO.setRepositorySlug(repoName);

        if (enableStaticAnalysisReports) {
            var reports = generateStaticCodeAnalysisReports(programmingLanguage);
            notification.setStaticCodeAnalysisReports(reports);
        }

        notification.setCommits(List.of(commitDTO));
        notification.setResults(List.of(testSuite));
        notification.setSuccessful(successfulTestNames.size());
        notification.setFailures(failedTestNames.size());
        notification.setRunDate(ZonedDateTime.now());
        notification.setLogs(List.of());
        return notification;
    }

    /**
     * Creates a dummy DTO with custom feedbacks used by Jenkins, which notifies about new programming exercise results.
     *
     * Uses {@link #generateTestResultDTO(String, List, List, ProgrammingLanguage, boolean)} as basis.
     * Then adds a new {@link TestsuiteDTO} with name "CustomFeedbacks" to it.
     * This Testsuite has four {@link TestCaseDTO}s:
     * <ul>
     *     <li>CustomSuccessMessage: successful test with a message</li>
     *     <li>CustomSuccessNoMessage: successful test without message</li>
     *     <li>CustomFailedMessage: failed test with a message</li>
     * </ul>
     *
     * @param repoName name of the repository
     * @param successfulTestNames names of successful tests
     * @param failedTestNames names of failed tests
     * @param programmingLanguage programming language to use
     * @param enableStaticAnalysisReports should the notification include static analysis reports
     * @return TestResultDTO with dummy data
     */
    public static TestResultsDTO generateTestResultsDTOWithCustomFeedback(String repoName, List<String> successfulTestNames, List<String> failedTestNames,
            ProgrammingLanguage programmingLanguage, boolean enableStaticAnalysisReports) {
        var notification = generateTestResultDTO(repoName, successfulTestNames, failedTestNames, programmingLanguage, enableStaticAnalysisReports);

        var testSuite = new TestsuiteDTO();
        testSuite.setName("customFeedbacks");
        testSuite.setErrors(0);
        testSuite.setSkipped(0);
        testSuite.setFailures(failedTestNames.size());
        testSuite.setTests(successfulTestNames.size() + failedTestNames.size());

        final List<TestCaseDTO> testCases = new ArrayList<>();

        // successful with message
        {
            var testCase = new TestCaseDTO();
            testCase.setName("CustomSuccessMessage");
            var successInfo = new TestCaseDetailMessageDTO();
            successInfo.setMessage("Successful test with message");
            testCase.setSuccessInfos(List.of(successInfo));
            testCases.add(testCase);
        }

        // successful without message
        {
            var testCase = new TestCaseDTO();
            testCase.setName("CustomSuccessNoMessage");
            var successInfo = new TestCaseDetailMessageDTO();
            testCase.setSuccessInfos(List.of(successInfo));
            testCases.add(testCase);
        }

        // failed with message
        {
            var testCase = new TestCaseDTO();
            testCase.setName("CustomFailedMessage");
            var failedInfo = new TestCaseDetailMessageDTO();
            failedInfo.setMessage("Failed test with message");
            testCase.setFailures(List.of(failedInfo));
            testCases.add(testCase);
        }

        // failed without message
        {
            var testCase = new TestCaseDTO();
            testCase.setName("CustomFailedNoMessage");
            var failedInfo = new TestCaseDetailMessageDTO();
            testCase.setFailures(List.of(failedInfo));
            testCases.add(testCase);
        }

        testSuite.setTestCases(testCases);

        var results = new ArrayList<>(notification.getResults());
        results.add(testSuite);
        notification.setResults(results);

        return notification;
    }

    public static BambooBuildResultNotificationDTO generateBambooBuildResult(String repoName, List<String> successfulTestNames, List<String> failedTestNames) {
        final var notification = new BambooBuildResultNotificationDTO();
        final var build = new BambooBuildResultNotificationDTO.BambooBuildDTO();
        final var summary = new BambooBuildResultNotificationDTO.BambooTestSummaryDTO();
        final var job = new BambooBuildResultNotificationDTO.BambooJobDTO();
        final var successfulTests = successfulTestNames.stream().map(name -> generateBambooTestJob(name, true)).collect(Collectors.toList());
        final var failedTests = failedTestNames.stream().map(name -> generateBambooTestJob(name, false)).collect(Collectors.toList());
        final var vcs = new BambooBuildResultNotificationDTO.BambooVCSDTO();
        final var plan = new BambooBuildPlanDTO("TEST201904BPROGRAMMINGEXERCISE6-STUDENT1");

        vcs.setRepositoryName(repoName);
        vcs.setId(TestConstants.COMMIT_HASH_STRING);

        job.setId(42);
        job.setFailedTests(failedTests);
        job.setSuccessfulTests(successfulTests);
        job.setLogs(List.of());

        summary.setTotalCount(successfulTestNames.size() + failedTestNames.size());
        summary.setSuccessfulCount(successfulTestNames.size());
        summary.setSkippedCount(0);
        summary.setQuarantineCount(0);
        summary.setNewFailedCount(failedTestNames.size());
        summary.setIgnoreCount(0);
        summary.setFixedCount(0);
        summary.setFailedCount(failedTestNames.size());
        summary.setExistingFailedCount(failedTestNames.size());
        summary.setDuration(42);
        summary.setDescription("foobar");

        build.setNumber(42);
        build.setReason("foobar");
        build.setSuccessful(failedTestNames.isEmpty());
        build.setBuildCompletedDate(ZonedDateTime.now().minusSeconds(5));
        build.setArtifact(false);
        build.setTestSummary(summary);
        build.setJobs(List.of(job));
        build.setVcs(List.of(vcs));

        notification.setSecret("secret");
        notification.setNotificationType("TestNotification");
        notification.setBuild(build);
        notification.setPlan(plan);

        return notification;
    }

    /**
     * Generate a Bamboo notification with build logs of various sizes
     *
     * @param repoName repository name
     * @param successfulTestNames names of successful tests
     * @param failedTestNames names of failed tests
     * @return notification with build logs
     */
    public static BambooBuildResultNotificationDTO generateBambooBuildResultWithLogs(String repoName, List<String> successfulTestNames, List<String> failedTestNames) {
        var notification = generateBambooBuildResult(repoName, successfulTestNames, failedTestNames);
        notification.getBuild().getTestSummary().setDescription("No tests found");

        String logWith254Chars = "a".repeat(254);

        var buildLogDTO254Chars = new BambooBuildLogDTO();
        buildLogDTO254Chars.setDate(ZonedDateTime.now());
        buildLogDTO254Chars.setLog(logWith254Chars);

        var buildLogDTO255Chars = new BambooBuildLogDTO();
        buildLogDTO255Chars.setDate(ZonedDateTime.now());
        buildLogDTO255Chars.setLog(logWith254Chars + "a");

        var buildLogDTO256Chars = new BambooBuildLogDTO();
        buildLogDTO256Chars.setDate(ZonedDateTime.now());
        buildLogDTO256Chars.setLog(logWith254Chars + "aa");

        var largeBuildLogDTO = new BambooBuildLogDTO();
        largeBuildLogDTO.setDate(ZonedDateTime.now());
        largeBuildLogDTO.setLog(logWith254Chars + logWith254Chars);

        var logTypicalErrorLog = new BambooBuildLogDTO();
        logTypicalErrorLog.setDate(ZonedDateTime.now());
        logTypicalErrorLog.setLog("error: the java class ABC does not exist");

        var logTypicalDuplicatedErrorLog = new BambooBuildLogDTO();
        logTypicalDuplicatedErrorLog.setDate(ZonedDateTime.now());
        logTypicalDuplicatedErrorLog.setLog("error: the java class ABC does not exist");

        var logCompilationError = new BambooBuildLogDTO();
        logCompilationError.setDate(ZonedDateTime.now());
        logCompilationError.setLog("COMPILATION ERROR");

        var logBuildError = new BambooBuildLogDTO();
        logBuildError.setDate(ZonedDateTime.now());
        logBuildError.setLog("BUILD FAILURE");

        var logWarning = new BambooBuildLogDTO();
        logWarning.setDate(ZonedDateTime.now());
        logWarning.setLog("[WARNING]");

        var logWarningIllegalReflectiveAccess = new BambooBuildLogDTO();
        logWarningIllegalReflectiveAccess.setDate(ZonedDateTime.now());
        logWarningIllegalReflectiveAccess.setLog("WARNING: Illegal reflective access by");

        notification.getBuild().getJobs().iterator().next().setLogs(List.of(buildLogDTO254Chars, buildLogDTO255Chars, buildLogDTO256Chars, largeBuildLogDTO, logTypicalErrorLog,
                logTypicalDuplicatedErrorLog, logWarning, logWarningIllegalReflectiveAccess, logCompilationError, logBuildError));

        return notification;
    }

    public static Feedback createSCAFeedbackWithInactiveCategory(Result result) {
        return new Feedback().result(result).text(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER).reference("CHECKSTYLE").detailText("{\"category\": \"miscellaneous\"}")
                .type(FeedbackType.AUTOMATIC).positive(false);
    }

    public static BambooBuildResultNotificationDTO generateBambooBuildResultWithStaticCodeAnalysisReport(String repoName, List<String> successfulTestNames,
            List<String> failedTestNames, ProgrammingLanguage programmingLanguage) {
        var notification = generateBambooBuildResult(repoName, successfulTestNames, failedTestNames);
        var reports = generateStaticCodeAnalysisReports(programmingLanguage);
        notification.getBuild().getJobs().get(0).setStaticCodeAnalysisReports(reports);
        return notification;
    }

    public static List<StaticCodeAnalysisReportDTO> generateStaticCodeAnalysisReports(ProgrammingLanguage language) {
        return StaticCodeAnalysisTool.getToolsForProgrammingLanguage(language).stream().map(ModelFactory::generateStaticCodeAnalysisReport).collect(Collectors.toList());
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
        final var test = new BambooBuildResultNotificationDTO.BambooTestJobDTO();
        test.setErrors(successful ? List.of() : List.of("bad solution, did not work"));
        test.setMethodName(name);
        test.setClassName("SpringTestClass");
        test.setName(name);

        return test;
    }

    /**
     * Generates example TextSubmissions
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
     *
     * Generate an example organization entity
     * @param name of organization
     * @param shortName of organization
     * @param url of organization
     * @param description of organization
     * @param logoUrl of organization
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

    public static AttachmentUnit generateAttachmentUnit(ZonedDateTime startDate, Lecture lecture) {
        AttachmentUnit attachmentUnit = new AttachmentUnit();
        attachmentUnit.setReleaseDate(startDate);
        attachmentUnit.setName("TestAttachementUnit");
        attachmentUnit.setLecture(lecture);
        attachmentUnit.setDescription("Test description");
        return attachmentUnit;
    }
}
