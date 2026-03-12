package de.tum.cit.aet.artemis.communication.service.notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.ExerciseAssessedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewCpcPlagiarismCaseNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewPlagiarismCaseNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.PlagiarismCaseVerdictNotification;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationService;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class SingleUserNotificationService {

    private final StudentParticipationRepository studentParticipationRepository;

    private final ConversationService conversationService;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseNotificationService courseNotificationService;

    public SingleUserNotificationService(StudentParticipationRepository studentParticipationRepository, ConversationService conversationService,
            AuthorizationCheckService authorizationCheckService, CourseNotificationService courseNotificationService) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.conversationService = conversationService;
        this.authorizationCheckService = authorizationCheckService;
        this.courseNotificationService = courseNotificationService;
    }

    /**
     * Notify all users with available assessments about the finished assessment for an exercise submission.
     * This is an auxiliary method that finds all relevant users and initiates the process for sending SingleUserNotifications and emails
     *
     * @param exercise which assessmentDueDate is the trigger for the notification process
     */
    // TODO: Should by a general method and not be in the single user service
    public void notifyUsersAboutAssessedExerciseSubmission(Exercise exercise) {
        // This process can not be replaces via a GroupNotification (can only notify ALL students of the course)
        // because we want to notify only the students that have a valid assessed submission.

        // Find student participations with eager legal submissions and latest results that have a completion date
        Set<StudentParticipation> filteredStudentParticipations = Set
                .copyOf(studentParticipationRepository.findByExerciseIdAndTestRunWithEagerSubmissionsAndLatestResultWithCompletionDate(exercise.getId(), false));

        // Load and assign all studentParticipations with results (this information is needed for the emails later)
        exercise.setStudentParticipations(filteredStudentParticipations);

        // Extract all users that should be notified from the previously loaded student participations
        Set<User> relevantStudents = filteredStudentParticipations.stream().flatMap(participation -> {
            if (participation.getParticipant() instanceof Team team) {
                return team.getStudents().stream();
            }

            return Stream.of(participation.getStudent().orElseThrow());
        }).collect(Collectors.toSet());

        // notify all relevant users
        relevantStudents.forEach(student -> notifyUserAboutAssessedExerciseSubmission(exercise, student));
    }

    /**
     * Notify student about the finished assessment for an exercise submission.
     * Also creates and sends an email.
     * <p>
     * private because it is called by other methods that check e.g. if the time or results are correct
     *
     * @param exercise  that was assessed
     * @param recipient who should be notified
     */
    public void notifyUserAboutAssessedExerciseSubmission(Exercise exercise, User recipient) {
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();

        var studentParticipation = exercise.getStudentParticipations().stream().filter(participation -> participation.getStudent().orElseThrow().equals(recipient)).findFirst();

        if (studentParticipation.isEmpty() || studentParticipation.get().findLatestResult() == null) {
            return;
        }

        Double score = Objects.requireNonNull(studentParticipation.get().findLatestResult()).getScore();

        Long examId = exercise.isExamExercise() ? exercise.getExerciseGroup().getExam().getId() : null;
        var exerciseAssessedNotification = new ExerciseAssessedNotification(course.getId(), course.getTitle(), course.getCourseIcon(), exercise.getId(),
                exercise.getSanitizedExerciseTitle(), exercise.getType(), exercise.getMaxPoints().longValue(), score.longValue(), examId);

        courseNotificationService.sendCourseNotification(exerciseAssessedNotification, List.of(recipient));
    }

    /**
     * Checks if a new assessed-exercise-submission notification has to be created now
     *
     * @param exercise  which the submission is based on
     * @param recipient of the notification (i.e. the student)
     * @param result    containing information needed for the email
     */
    public void checkNotificationForAssessmentExerciseSubmission(Exercise exercise, User recipient, Result result) {
        // Only send the notification now if no assessment due date was set or if it is in the past.
        // For exam exercises, this fires only when exam results are published (resultsPublished() returns true).
        if (ExerciseDateService.isAfterAssessmentDueDate(exercise)) {
            saturateExerciseWithResultAndStudentParticipationForGivenUserForEmail(exercise, recipient, result);
            notifyUserAboutAssessedExerciseSubmission(exercise, recipient);
        }
    }

    /**
     * Auxiliary method needed to create an email based on assessed exercises.
     * We saturate the wanted result information (e.g. score) in the exercise
     * This method is only called in those cases where no assessmentDueDate is set, i.e. individual/dynamic processes.
     *
     * @param exercise  that should contain information that is needed for emails
     * @param recipient who should be notified
     * @param result    that should be loaded as part of the exercise
     */
    public void saturateExerciseWithResultAndStudentParticipationForGivenUserForEmail(Exercise exercise, User recipient, Result result) {
        StudentParticipation studentParticipationForEmail = new StudentParticipation();
        studentParticipationForEmail.setParticipant(recipient);
        exercise.setStudentParticipations(Set.of(studentParticipationForEmail));
    }

    /**
     * Notify student about possible plagiarism case.
     *
     * @param plagiarismCase that hold the major information for the plagiarism case
     * @param student        who should be notified
     */
    public void notifyUserAboutNewPlagiarismCase(PlagiarismCase plagiarismCase, User student) {
        var plagiarismCaseExercise = plagiarismCase.getExercise();
        var course = plagiarismCaseExercise.getCourseViaExerciseGroupOrCourseMember();

        Long examId = plagiarismCaseExercise.isExamExercise() ? plagiarismCaseExercise.getExerciseGroup().getExam().getId() : null;
        var newPlagiarismCaseNotification = new NewPlagiarismCaseNotification(course.getId(), course.getTitle(), course.getCourseIcon(), plagiarismCaseExercise.getId(),
                plagiarismCaseExercise.getSanitizedExerciseTitle(), plagiarismCaseExercise.getType(), plagiarismCase.getPost().getContent(), examId);

        courseNotificationService.sendCourseNotification(newPlagiarismCaseNotification, List.of(student));
    }

    /**
     * Notify student about possible plagiarism case opened by the continuous plagiarism control.
     * The notification is created without explicit notification author.
     *
     * @param plagiarismCase that hold the major information for the plagiarism case
     * @param student        who should be notified
     */
    public void notifyUserAboutNewContinuousPlagiarismControlPlagiarismCase(PlagiarismCase plagiarismCase, User student) {
        var plagiarismCaseExercise = plagiarismCase.getExercise();
        var course = plagiarismCaseExercise.getCourseViaExerciseGroupOrCourseMember();

        Long examId = plagiarismCaseExercise.isExamExercise() ? plagiarismCaseExercise.getExerciseGroup().getExam().getId() : null;
        var newCpcPlagiarismCaseNotification = new NewCpcPlagiarismCaseNotification(course.getId(), course.getTitle(), course.getCourseIcon(), plagiarismCaseExercise.getId(),
                plagiarismCaseExercise.getSanitizedExerciseTitle(), plagiarismCaseExercise.getType(), plagiarismCase.getPost().getContent(), examId);

        courseNotificationService.sendCourseNotification(newCpcPlagiarismCaseNotification, List.of(student));
    }

    /**
     * Notify student about plagiarism case verdict.
     *
     * @param plagiarismCase that hold the major information for the plagiarism case
     * @param student        who should be notified
     */
    public void notifyUserAboutPlagiarismCaseVerdict(PlagiarismCase plagiarismCase, User student) {
        var plagiarismCaseExercise = plagiarismCase.getExercise();
        var course = plagiarismCaseExercise.getCourseViaExerciseGroupOrCourseMember();

        Long examId = plagiarismCaseExercise.isExamExercise() ? plagiarismCaseExercise.getExerciseGroup().getExam().getId() : null;
        var plagiarismCaseVerdictNotification = new PlagiarismCaseVerdictNotification(course.getId(), course.getTitle(), course.getCourseIcon(), plagiarismCaseExercise.getId(),
                plagiarismCaseExercise.getSanitizedExerciseTitle(), plagiarismCaseExercise.getType(), plagiarismCase.getVerdict().toString(), examId);

        courseNotificationService.sendCourseNotification(plagiarismCaseVerdictNotification, List.of(student));
    }

    /**
     * Filters which of the mentioned users are permitted to receive a notification
     *
     * @param mentionedUsers users mentioned in the message
     * @param conversation   the conversation of the created post/notification, used for filtering
     * @return the stream of mentioned users which are permitted to receive the notification for the given conversation
     */
    public Stream<User> filterAllowedRecipientsInMentionedUsers(Set<User> mentionedUsers, Conversation conversation) {
        return mentionedUsers.stream().filter(user -> {
            boolean isChannelAndCourseWide = conversation instanceof Channel channel && channel.getIsCourseWide();
            boolean isChannelVisibleToStudents = !(conversation instanceof Channel channel) || conversationService.isChannelVisibleToStudents(channel);
            boolean isChannelVisibleToMentionedUser = isChannelVisibleToStudents || authorizationCheckService.isAtLeastTeachingAssistantInCourse(conversation.getCourse(), user);

            // Only send a notification to the mentioned user if...
            // (for course-wide channels) ...the course-wide channel is visible
            // (for all other cases) ...the user is a member of the conversation
            return (isChannelAndCourseWide && isChannelVisibleToMentionedUser) || conversationService.isMember(conversation.getId(), user.getId());
        });
    }
}
