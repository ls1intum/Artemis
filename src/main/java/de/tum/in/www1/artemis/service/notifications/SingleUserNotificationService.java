package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.SingleUserNotificationFactory.createNotification;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsCommunicationChannel.*;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.repository.SingleUserNotificationRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.MailService;

@Service
public class SingleUserNotificationService {

    private final SingleUserNotificationRepository singleUserNotificationRepository;

    private final UserRepository userRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private final MailService mailService;

    private final NotificationSettingsService notificationSettingsService;

    private final StudentParticipationRepository studentParticipationRepository;

    public SingleUserNotificationService(SingleUserNotificationRepository singleUserNotificationRepository, UserRepository userRepository,
            SimpMessageSendingOperations messagingTemplate, MailService mailService, NotificationSettingsService notificationSettingsService,
            StudentParticipationRepository studentParticipationRepository) {
        this.singleUserNotificationRepository = singleUserNotificationRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.mailService = mailService;
        this.notificationSettingsService = notificationSettingsService;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Auxiliary method to call the correct factory method and start the process to save & sent the notification
     * @param notificationSubject is the subject of the notification (e.g. exercise, attachment)
     * @param notificationType is the discriminator for the factory
     * @param typeSpecificInformation is based on the current use case (e.g. POST -> course, Exercise -> user)
     */
    private void notifyRecipientWithNotificationType(Object notificationSubject, NotificationType notificationType, Object typeSpecificInformation, User author) {
        var singleUserNotification = switch (notificationType) {
            // Post Types
            case NEW_REPLY_FOR_EXERCISE_POST, NEW_REPLY_FOR_LECTURE_POST, NEW_REPLY_FOR_COURSE_POST -> createNotification((Post) notificationSubject, notificationType,
                    (Course) typeSpecificInformation);
            // Exercise related
            case EXERCISE_SUBMISSION_ASSESSED, FILE_SUBMISSION_SUCCESSFUL -> createNotification((Exercise) notificationSubject, notificationType, (User) typeSpecificInformation);
            // Plagiarism related
            case NEW_PLAGIARISM_CASE_STUDENT, PLAGIARISM_CASE_VERDICT_STUDENT -> createNotification((PlagiarismCase) notificationSubject, notificationType,
                    (User) typeSpecificInformation, author);

            default -> throw new UnsupportedOperationException("Can not create notification for type : " + notificationType);
        };
        saveAndSend(singleUserNotification, notificationSubject);
    }

    /**
     * Notify all users with available assessments about the finished assessment for an exercise submission.
     * This is an auxiliary method that finds all relevant users and initiates the process for sending SingleUserNotifications and emails
     *
     * @param exercise which assessmentDueDate is the trigger for the notification process
     */
    public void notifyUsersAboutAssessedExerciseSubmission(Exercise exercise) {
        // This process can not be replaces via a GroupNotification (can only notify ALL students of the course)
        // because we want to notify only the students that have a valid assessed submission.

        // Find student participations with eager legal submissions and latest results that have a completion date
        Set<StudentParticipation> filteredStudentParticipations = Set
                .copyOf(studentParticipationRepository.findByExerciseIdWithEagerLegalSubmissionsAndLatestResultWithCompletionDate(exercise.getId()));

        // Load and assign all studentParticipations with results (this information is needed for the emails later)
        exercise.setStudentParticipations(filteredStudentParticipations);

        // Extract all users that should be notified from the previously loaded student participations
        Set<User> relevantStudents = filteredStudentParticipations.stream().map(participation -> participation.getStudent().orElseThrow()).collect(Collectors.toSet());

        // notify all relevant users
        relevantStudents.forEach(student -> notifyUserAboutAssessedExerciseSubmission(exercise, student));
    }

    /**
     * Notify author of a post for an exercise that there is a new reply.
     *
     * @param post that is replied
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewReplyForExercise(Post post, Course course) {
        notifyRecipientWithNotificationType(post, NEW_REPLY_FOR_EXERCISE_POST, course, post.getAuthor());
    }

    /**
     * Notify author of a post for a lecture that there is a new reply.
     *
     * @param post that is replied
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewReplyForLecture(Post post, Course course) {
        notifyRecipientWithNotificationType(post, NEW_REPLY_FOR_LECTURE_POST, course, post.getAuthor());
    }

    /**
     * Notify author of a course-wide that there is a new reply.
     * Also creates and sends an email.
     *
     * @param post that is replied
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewReplyForCoursePost(Post post, Course course) {
        notifyRecipientWithNotificationType(post, NEW_REPLY_FOR_COURSE_POST, course, post.getAuthor());
    }

    /**
     * Notify student about the finished assessment for an exercise submission.
     * Also creates and sends an email.
     *
     * private because it is called by other methods that check e.g. if the time or results are correct
     *
     * @param exercise that was assessed
     * @param recipient who should be notified
     *
     */
    private void notifyUserAboutAssessedExerciseSubmission(Exercise exercise, User recipient) {
        notifyRecipientWithNotificationType(exercise, EXERCISE_SUBMISSION_ASSESSED, recipient, null);
    }

    /**
     * Checks if a new assessed-exercise-submission notification has to be created now
     *
     * @param exercise which the submission is based on
     * @param recipient of the notification (i.e. the student)
     * @param result containing information needed for the email
     */
    public void checkNotificationForAssessmentExerciseSubmission(Exercise exercise, User recipient, Result result) {
        // only send the notification now if no assessment due date was set or if it is in the past
        if (exercise.isCourseExercise() && (exercise.getAssessmentDueDate() == null || !exercise.getAssessmentDueDate().isAfter(ZonedDateTime.now()))) {
            saturateExerciseWithResultAndStudentParticipationForGivenUserForEmail(exercise, recipient, result);
            notifyUserAboutAssessedExerciseSubmission(exercise, recipient);
        }
        // no scheduling needed because it is already part of updating/creating exercises
    }

    /**
     * Auxiliary method needed to create an email based on assessed exercises.
     * We saturate the wanted result information (e.g. score) in the exercise
     * This method is only called in those cases where no assessmentDueDate is set, i.e. individual/dynamic processes.
     *
     * @param exercise that should contain information that is needed for emails
     * @param recipient who should be notified
     * @param result that should be loaded as part of the exercise
     * @return the input exercise with information about a result
     */
    public Exercise saturateExerciseWithResultAndStudentParticipationForGivenUserForEmail(Exercise exercise, User recipient, Result result) {
        StudentParticipation studentParticipationForEmail = new StudentParticipation();
        studentParticipationForEmail.setResults(Set.of(result));
        studentParticipationForEmail.setParticipant(recipient);
        exercise.setStudentParticipations(Set.of(studentParticipationForEmail));
        return exercise;
    }

    /**
     * Notify student about successful submission of file upload exercise.
     * Also creates and sends an email.
     *
     * @param exercise that was submitted
     * @param recipient that should be notified
     */
    public void notifyUserAboutSuccessfulFileUploadSubmission(FileUploadExercise exercise, User recipient) {
        notifyRecipientWithNotificationType(exercise, FILE_SUBMISSION_SUCCESSFUL, recipient, null);
    }

    /**
     * Notify student about possible plagiarism case.
     *  @param plagiarismCase that hold the major information for the plagiarism case
     * @param student who should be notified
     */
    public void notifyUserAboutNewPlagiarismCase(PlagiarismCase plagiarismCase, User student) {
        notifyRecipientWithNotificationType(plagiarismCase, NEW_PLAGIARISM_CASE_STUDENT, student, userRepository.getUser());
    }

    /**
     * Notify student about plagiarism case verdict.
     *  @param plagiarismCase that hold the major information for the plagiarism case
     * @param student who should be notified
     */
    public void notifyUserAboutPlagiarismCaseVerdict(PlagiarismCase plagiarismCase, User student) {
        notifyRecipientWithNotificationType(plagiarismCase, PLAGIARISM_CASE_VERDICT_STUDENT, student, userRepository.getUser());
    }

    /**
     * Saves the given notification in database and sends it to the client via websocket.
     * Also creates and sends an email.
     *
     * @param notification that should be saved and sent
     * @param notificationSubject which information will be extracted to create the email
     */
    private void saveAndSend(SingleUserNotification notification, Object notificationSubject) {
        singleUserNotificationRepository.save(notification);
        // we only want to notify one individual user therefore we can check the settings and filter preemptively
        boolean isAllowedBySettings = notificationSettingsService.checkIfNotificationOrEmailIsAllowedBySettingsForGivenUser(notification, notification.getRecipient(), WEBAPP);
        if (isAllowedBySettings) {
            messagingTemplate.convertAndSend(notification.getTopic(), notification);
            prepareSingleUserNotificationEmail(notification, notificationSubject);
        }
    }

    /**
     * Checks if an email should be created based on the provided notification, user, notification settings and type for SingleUserNotifications
     * If the checks are successful creates and sends a corresponding email
     * @param notification that should be checked
     * @param notificationSubject which information will be extracted to create the email
     */
    private void prepareSingleUserNotificationEmail(SingleUserNotification notification, Object notificationSubject) {
        NotificationType type = NotificationTitleTypeConstants.findCorrespondingNotificationType(notification.getTitle());
        // checks if this notification type has email support
        if (notificationSettingsService.checkNotificationTypeForEmailSupport(type)) {
            boolean isAllowedBySettingsForEmail = notificationSettingsService.checkIfNotificationOrEmailIsAllowedBySettingsForGivenUser(notification, notification.getRecipient(),
                    EMAIL);
            if (isAllowedBySettingsForEmail) {
                mailService.sendNotificationEmail(notification, notification.getRecipient(), notificationSubject);
            }
        }
    }
}
