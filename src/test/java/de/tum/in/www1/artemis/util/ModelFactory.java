package de.tum.in.www1.artemis.util;

import java.time.ZonedDateTime;
import java.util.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;

public class ModelFactory {

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
        quizExercise.setIsOpenForPractice(false);
        quizExercise.setIsPlannedToStart(true);
        quizExercise.setIsVisibleBeforeStart(true);
        quizExercise.setAllowedNumberOfAttempts(1);
        quizExercise.setDuration(10);
        quizExercise.setRandomizeQuestionOrder(true);
        return quizExercise;
    }

    public static ProgrammingExercise generateProgrammingExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, Course course) {
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise = (ProgrammingExercise) populateExercise(programmingExercise, releaseDate, dueDate, null, course);
        programmingExercise.generateAndSetProjectKey();
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setPackageName("de.test");
        return programmingExercise;
    }

    public static ModelingExercise generateModelingExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, DiagramType diagramType,
            Course course) {
        ModelingExercise modelingExercise = new ModelingExercise();
        modelingExercise = (ModelingExercise) populateExercise(modelingExercise, releaseDate, dueDate, assessmentDueDate, course);
        modelingExercise.setDiagramType(diagramType);
        return modelingExercise;
    }

    public static TextExercise generateTextExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        TextExercise textExercise = new TextExercise();
        return (TextExercise) populateExercise(textExercise, releaseDate, dueDate, assessmentDueDate, course);
    }

    public static FileUploadExercise generateFileUploadExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, String filePattern,
            Course course) {
        FileUploadExercise fileUploadExercise = new FileUploadExercise();
        fileUploadExercise = (FileUploadExercise) populateExercise(fileUploadExercise, releaseDate, dueDate, assessmentDueDate, course);
        fileUploadExercise.setFilePattern(filePattern);
        return (FileUploadExercise) populateExercise(fileUploadExercise, releaseDate, dueDate, assessmentDueDate, course);
    }

    public static Exercise populateExercise(Exercise exercise, ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        exercise.setTitle(UUID.randomUUID().toString());
        exercise.setShortName("t" + UUID.randomUUID().toString().substring(0, 3));
        exercise.setProblemStatement("Problem Statement");
        exercise.setMaxScore(5.0);
        exercise.setReleaseDate(releaseDate);
        exercise.setDueDate(dueDate);
        exercise.assessmentDueDate(assessmentDueDate);
        exercise.setCourse(course);
        exercise.setDifficulty(DifficultyLevel.MEDIUM);
        exercise.getCategories().add("Category");
        exercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);
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

    public static User generateActivatedUser(String login) {
        User user = new User();
        user.setLogin(login);
        user.setPassword("0000");
        user.setFirstName(login + "First");
        user.setLastName(login + "Last");
        user.setEmail(login + "@test.de");
        user.setActivated(true);
        user.setLangKey("en");
        user.setGroups(new HashSet<>());
        user.setAuthorities(new HashSet<>());
        return user;
    }

    public static Course generateCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises) {
        return generateCourse(id, startDate, endDate, exercises, UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
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

    public static ModelingSubmission generateModelingSubmission(String model, boolean submitted) {
        ModelingSubmission submission = new ModelingSubmission();
        submission.setModel(model);
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
            String teachingAssistantGroupName, String instructorGroupName) {
        Course course = new Course();
        course.setId(id);
        course.setTitle(UUID.randomUUID().toString());
        course.setDescription(UUID.randomUUID().toString());
        course.setShortName("t" + UUID.randomUUID().toString().substring(0, 3));
        course.setStudentGroupName(studentGroupName);
        course.setTeachingAssistantGroupName(teachingAssistantGroupName);
        course.setInstructorGroupName(instructorGroupName);
        course.setStartDate(startDate);
        course.setEndDate(endDate);
        course.setMaxComplaints(5);
        course.setExercises(exercises);
        course.setOnlineCourse(false);
        course.setPresentationScore(2);
        return course;
    }

    public static List<Feedback> generateFeedback() {
        List<Feedback> feedbacks = new ArrayList<>();
        Feedback positiveFeedback = new Feedback();
        positiveFeedback.setCredits(2D);
        positiveFeedback.setReference("theory");
        feedbacks.add(positiveFeedback);
        Feedback negativeFeedback = new Feedback();
        negativeFeedback.setCredits(-1D);
        negativeFeedback.setDetailText("Bad solution");
        negativeFeedback.setReference("practice");
        feedbacks.add(negativeFeedback);
        return feedbacks;
    }

    public static ProgrammingExercise generateToBeImportedProgrammingExercise(String title, String shortName, ProgrammingExercise template, Course targetCourse) {
        ProgrammingExercise toBeImported = new ProgrammingExercise();
        toBeImported.setCourse(targetCourse);
        toBeImported.setTitle(title);
        toBeImported.setShortName(shortName);
        toBeImported.setId(template.getId());
        toBeImported.setTestCases(null);
        toBeImported.setNumberOfAssessments(template.getNumberOfAssessments());
        toBeImported.setNumberOfComplaints(template.getNumberOfComplaints());
        toBeImported.setNumberOfMoreFeedbackRequests(template.getNumberOfMoreFeedbackRequests());
        toBeImported.setExerciseHints(null);
        toBeImported.setSolutionParticipation(null);
        toBeImported.setTemplateParticipation(null);
        toBeImported.setPublishBuildPlanUrl(template.isPublishBuildPlanUrl());
        toBeImported.setSequentialTestRuns(template.hasSequentialTestRuns());
        toBeImported.setProblemStatement(template.getProblemStatement());
        toBeImported.setMaxScore(template.getMaxScore());
        toBeImported.setGradingInstructions(template.getGradingInstructions());
        toBeImported.setDifficulty(template.getDifficulty());
        toBeImported.setAssessmentType(template.getAssessmentType());
        toBeImported.setCategories(template.getCategories());
        toBeImported.setPackageName(template.getPackageName());
        toBeImported.setAllowOnlineEditor(template.isAllowOnlineEditor());
        toBeImported.setTutorParticipations(null);
        toBeImported.setStudentQuestions(null);
        toBeImported.setStudentParticipations(null);
        toBeImported.setNumberOfParticipations(template.getNumberOfParticipations());
        toBeImported.setExampleSubmissions(null);
        toBeImported.setTestRepositoryUrl(template.getTestRepositoryUrl());
        toBeImported.setProgrammingLanguage(template.getProgrammingLanguage());
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
        studentParticipation.setStudent(user);
        return studentParticipation;
    }

    public static ProgrammingExerciseStudentParticipation generateProgrammingExerciseStudentParticipation(InitializationState initializationState, ProgrammingExercise exercise,
            User user) {
        ProgrammingExerciseStudentParticipation studentParticipation = new ProgrammingExerciseStudentParticipation();
        studentParticipation.setInitializationState(initializationState);
        studentParticipation.setInitializationDate(ZonedDateTime.now().minusDays(5));
        studentParticipation.setExercise(exercise);
        studentParticipation.setStudent(user);
        return studentParticipation;
    }

    public static Result generateResult(boolean rated, long score) {
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

    public static SystemNotification generateSystemNotification(ZonedDateTime expireDate, ZonedDateTime notificationDate) {
        SystemNotification systemNotification = new SystemNotification();
        systemNotification.setExpireDate(expireDate);
        systemNotification.setNotificationDate(notificationDate);
        return systemNotification;
    }
}
