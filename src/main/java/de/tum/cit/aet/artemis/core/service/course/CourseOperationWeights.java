package de.tum.cit.aet.artemis.core.service.course;

import de.tum.cit.aet.artemis.core.dto.CourseSummaryDTO;

/**
 * Utility class for calculating weighted progress for course operations (delete, reset).
 * <p>
 * Different entities have different "costs" based on the time they take to process:
 * <ul>
 * <li>Programming exercises are expensive due to repository operations</li>
 * <li>Exercises with many participations/submissions take longer</li>
 * <li>Exams with many student exams take longer</li>
 * <li>Large numbers of posts take longer than simple metadata</li>
 * </ul>
 * <p>
 * Weight units are approximate relative costs, not actual time measurements.
 */
public final class CourseOperationWeights {

    // Base weights for exercises (per exercise)
    private static final double WEIGHT_PROGRAMMING_EXERCISE = 20.0;

    private static final double WEIGHT_OTHER_EXERCISE = 5.0;

    // Per-item weights for student data
    private static final double WEIGHT_PER_PARTICIPATION = 1.0;

    // Programming exercise participations are more expensive due to Git repository deletion
    private static final double WEIGHT_PER_PROGRAMMING_PARTICIPATION = 5.0;

    private static final double WEIGHT_PER_SUBMISSION = 0.5;

    // Exam weights
    private static final double WEIGHT_EXAM_BASE = 10.0;

    // Communication weights (per item)
    private static final double WEIGHT_PER_POST = 0.1;

    private static final double WEIGHT_PER_ANSWER = 0.1;

    private static final double WEIGHT_PER_CONVERSATION = 1.0;

    // Course structure weights (per item)
    private static final double WEIGHT_PER_LECTURE = 5.0;

    private static final double WEIGHT_PER_COMPETENCY = 2.0;

    private static final double WEIGHT_PER_TUTORIAL_GROUP = 3.0;

    private static final double WEIGHT_PER_FAQ = 1.0;

    // Learning/AI data weights (per item)
    private static final double WEIGHT_PER_COMPETENCY_PROGRESS = 0.1;

    private static final double WEIGHT_PER_LEARNER_PROFILE = 1.0;

    private static final double WEIGHT_PER_IRIS_SESSION = 1.0;

    private static final double WEIGHT_PER_LLM_TRACE = 0.1;

    private static final double WEIGHT_PER_BUILD = 0.1;

    // Simple operations (minimal weight)
    private static final double WEIGHT_GRADING_SCALE = 1.0;

    private static final double WEIGHT_NOTIFICATIONS = 2.0;

    private static final double WEIGHT_NOTIFICATION_SETTINGS = 1.0;

    private static final double WEIGHT_USER_GROUPS = 2.0;

    private static final double WEIGHT_COURSE_REQUESTS = 1.0;

    private static final double WEIGHT_COURSE_ENTITY = 1.0;

    private static final double WEIGHT_PER_USER_UNENROLL = 0.5;

    private static final double WEIGHT_TUTORIAL_REGISTRATIONS = 1.0;

    private CourseOperationWeights() {
        // Utility class - no instantiation
    }

    /**
     * Calculates total work weight for course deletion.
     * Includes all data that will be deleted.
     *
     * @param summary          the course summary with entity counts
     * @param actualExamWeight the pre-calculated total exam weight (from actual exam data)
     * @return the total weight for deletion
     */
    public static double calculateDeletionTotalWeight(CourseSummaryDTO summary, double actualExamWeight) {
        double weight = 0;

        // Exercises
        weight += summary.numberOfProgrammingExercises() * WEIGHT_PROGRAMMING_EXERCISE;
        long otherExerciseCount = summary.numberOfTextExercises() + summary.numberOfModelingExercises() + summary.numberOfQuizExercises() + summary.numberOfFileUploadExercises();
        weight += otherExerciseCount * WEIGHT_OTHER_EXERCISE;

        // Student work (participations, submissions)
        // Estimate programming vs other participations based on exercise distribution
        long totalExercises = summary.numberOfProgrammingExercises() + otherExerciseCount;
        if (totalExercises > 0) {
            double programmingRatio = (double) summary.numberOfProgrammingExercises() / totalExercises;
            long estimatedProgrammingParticipations = (long) (summary.numberOfParticipations() * programmingRatio);
            long estimatedOtherParticipations = summary.numberOfParticipations() - estimatedProgrammingParticipations;
            weight += estimatedProgrammingParticipations * WEIGHT_PER_PROGRAMMING_PARTICIPATION;
            weight += estimatedOtherParticipations * WEIGHT_PER_PARTICIPATION;
        }
        else {
            weight += summary.numberOfParticipations() * WEIGHT_PER_PARTICIPATION;
        }
        weight += summary.numberOfSubmissions() * WEIGHT_PER_SUBMISSION;

        // Exams - use actual calculated weight instead of estimate
        weight += actualExamWeight;

        // Lectures
        weight += summary.numberOfLectures() * WEIGHT_PER_LECTURE;

        // Competencies
        weight += summary.numberOfCompetencies() * WEIGHT_PER_COMPETENCY;
        weight += summary.numberOfCompetencyProgress() * WEIGHT_PER_COMPETENCY_PROGRESS;

        // Tutorial groups
        weight += summary.numberOfTutorialGroups() * WEIGHT_PER_TUTORIAL_GROUP;

        // Communication
        weight += summary.numberOfConversations() * WEIGHT_PER_CONVERSATION;
        weight += summary.numberOfPosts() * WEIGHT_PER_POST;
        weight += summary.numberOfAnswerPosts() * WEIGHT_PER_ANSWER;

        // FAQs
        weight += summary.numberOfFaqs() * WEIGHT_PER_FAQ;

        // Learning/AI data
        weight += summary.numberOfLearnerProfiles() * WEIGHT_PER_LEARNER_PROFILE;
        weight += summary.numberOfIrisChatSessions() * WEIGHT_PER_IRIS_SESSION;
        weight += summary.numberOfLLMTraces() * WEIGHT_PER_LLM_TRACE;
        weight += summary.numberOfBuilds() * WEIGHT_PER_BUILD;

        // Fixed cost operations
        weight += WEIGHT_NOTIFICATIONS;
        weight += WEIGHT_NOTIFICATION_SETTINGS;
        weight += WEIGHT_USER_GROUPS;
        weight += WEIGHT_GRADING_SCALE;
        weight += WEIGHT_COURSE_REQUESTS;
        weight += WEIGHT_COURSE_ENTITY;

        return Math.max(weight, 1.0); // Ensure non-zero
    }

    /**
     * Calculates total work weight for course reset.
     * Includes only student data that will be deleted (course structure preserved).
     * <p>
     * Note: Reset operations are lighter than delete operations because structure is preserved.
     * Programming exercise reset uses factor 0.7, other exercises use 0.5.
     * Programming participations have higher weight due to repository deletion.
     *
     * @param summary          the course summary with entity counts
     * @param actualExamWeight the pre-calculated total exam weight (from actual exam data, already scaled for reset)
     * @return the total weight for reset
     */
    public static double calculateResetTotalWeight(CourseSummaryDTO summary, double actualExamWeight) {
        double weight = 0;

        // Calculate exercise distribution
        long otherExerciseCount = summary.numberOfTextExercises() + summary.numberOfModelingExercises() + summary.numberOfQuizExercises() + summary.numberOfFileUploadExercises();
        long totalExercises = summary.numberOfProgrammingExercises() + otherExerciseCount;

        // Exercise reset weights with proportional factor
        weight += summary.numberOfProgrammingExercises() * WEIGHT_PROGRAMMING_EXERCISE * 0.7;
        weight += otherExerciseCount * WEIGHT_OTHER_EXERCISE * 0.5;

        // Student work (participations/submissions)
        // Estimate programming vs other participations based on exercise distribution
        // Programming participations need higher weight due to repository deletion
        if (totalExercises > 0) {
            double programmingRatio = (double) summary.numberOfProgrammingExercises() / totalExercises;
            long estimatedProgrammingParticipations = (long) (summary.numberOfParticipations() * programmingRatio);
            long estimatedOtherParticipations = summary.numberOfParticipations() - estimatedProgrammingParticipations;
            // Apply reset factors: 0.7 for programming, 0.5 for others
            weight += estimatedProgrammingParticipations * WEIGHT_PER_PROGRAMMING_PARTICIPATION * 0.7;
            weight += estimatedOtherParticipations * WEIGHT_PER_PARTICIPATION * 0.5;
            // Submissions use average reset factor
            double avgResetFactor = (summary.numberOfProgrammingExercises() * 0.7 + otherExerciseCount * 0.5) / totalExercises;
            weight += summary.numberOfSubmissions() * WEIGHT_PER_SUBMISSION * avgResetFactor;
        }
        else {
            weight += summary.numberOfParticipations() * WEIGHT_PER_PARTICIPATION * 0.6;
            weight += summary.numberOfSubmissions() * WEIGHT_PER_SUBMISSION * 0.6;
        }

        // Exam reset - use actual calculated weight instead of estimate
        weight += actualExamWeight;

        // Communication (posts only, not structure)
        weight += summary.numberOfPosts() * WEIGHT_PER_POST;
        weight += summary.numberOfAnswerPosts() * WEIGHT_PER_ANSWER;

        // Learning data
        weight += summary.numberOfCompetencyProgress() * WEIGHT_PER_COMPETENCY_PROGRESS;
        weight += summary.numberOfLearnerProfiles() * WEIGHT_PER_LEARNER_PROFILE;

        // AI data
        weight += summary.numberOfIrisChatSessions() * WEIGHT_PER_IRIS_SESSION;
        weight += summary.numberOfLLMTraces() * WEIGHT_PER_LLM_TRACE;

        // User unenrollment
        weight += (summary.numberOfStudents() + summary.numberOfTutors() + summary.numberOfEditors()) * WEIGHT_PER_USER_UNENROLL;

        // Fixed cost operations
        weight += WEIGHT_NOTIFICATIONS;
        weight += WEIGHT_NOTIFICATION_SETTINGS;
        weight += WEIGHT_TUTORIAL_REGISTRATIONS;

        return Math.max(weight, 1.0); // Ensure non-zero
    }

    /**
     * Calculates weight for a single programming exercise.
     * Includes base exercise weight plus weight for participations/submissions.
     * Programming participations have higher weight due to Git repository deletion.
     *
     * @param participationCount the number of participations for this exercise
     * @param submissionCount    the number of submissions for this exercise
     * @return the weight for this exercise
     */
    public static double calculateProgrammingExerciseWeight(long participationCount, long submissionCount) {
        return WEIGHT_PROGRAMMING_EXERCISE + (participationCount * WEIGHT_PER_PROGRAMMING_PARTICIPATION) + (submissionCount * WEIGHT_PER_SUBMISSION);
    }

    /**
     * Calculates weight for a single non-programming exercise.
     *
     * @param participationCount the number of participations for this exercise
     * @param submissionCount    the number of submissions for this exercise
     * @return the weight for this exercise
     */
    public static double calculateOtherExerciseWeight(long participationCount, long submissionCount) {
        return WEIGHT_OTHER_EXERCISE + (participationCount * WEIGHT_PER_PARTICIPATION) + (submissionCount * WEIGHT_PER_SUBMISSION);
    }

    /**
     * Calculates weight for a single exam.
     * Takes into account the number of programming exercises, as each student's
     * repository must be deleted for each programming exercise.
     *
     * @param studentExamCount         the number of student exams
     * @param programmingExerciseCount the number of programming exercises in the exam
     * @return the weight for this exam
     */
    public static double calculateExamWeight(long studentExamCount, long programmingExerciseCount) {
        // Base exam overhead + per-student overhead + repository deletions for programming exercises
        double baseWeight = WEIGHT_EXAM_BASE + (studentExamCount * WEIGHT_PER_PARTICIPATION);
        double repositoryWeight = programmingExerciseCount * studentExamCount * WEIGHT_PER_PROGRAMMING_PARTICIPATION;
        return baseWeight + repositoryWeight;
    }

    // Getters for individual weights (for granular progress tracking)

    public static double getWeightPerPost() {
        return WEIGHT_PER_POST;
    }

    public static double getWeightPerAnswer() {
        return WEIGHT_PER_ANSWER;
    }

    public static double getWeightPerConversation() {
        return WEIGHT_PER_CONVERSATION;
    }

    public static double getWeightPerLecture() {
        return WEIGHT_PER_LECTURE;
    }

    public static double getWeightPerCompetency() {
        return WEIGHT_PER_COMPETENCY;
    }

    public static double getWeightPerCompetencyProgress() {
        return WEIGHT_PER_COMPETENCY_PROGRESS;
    }

    public static double getWeightPerTutorialGroup() {
        return WEIGHT_PER_TUTORIAL_GROUP;
    }

    public static double getWeightPerFaq() {
        return WEIGHT_PER_FAQ;
    }

    public static double getWeightPerLearnerProfile() {
        return WEIGHT_PER_LEARNER_PROFILE;
    }

    public static double getWeightPerIrisSession() {
        return WEIGHT_PER_IRIS_SESSION;
    }

    public static double getWeightPerLlmTrace() {
        return WEIGHT_PER_LLM_TRACE;
    }

    public static double getWeightNotifications() {
        return WEIGHT_NOTIFICATIONS;
    }

    public static double getWeightNotificationSettings() {
        return WEIGHT_NOTIFICATION_SETTINGS;
    }

    public static double getWeightUserGroups() {
        return WEIGHT_USER_GROUPS;
    }

    public static double getWeightGradingScale() {
        return WEIGHT_GRADING_SCALE;
    }

    public static double getWeightCourseRequests() {
        return WEIGHT_COURSE_REQUESTS;
    }

    public static double getWeightCourseEntity() {
        return WEIGHT_COURSE_ENTITY;
    }

    public static double getWeightPerUserUnenroll() {
        return WEIGHT_PER_USER_UNENROLL;
    }

    public static double getWeightTutorialRegistrations() {
        return WEIGHT_TUTORIAL_REGISTRATIONS;
    }
}
