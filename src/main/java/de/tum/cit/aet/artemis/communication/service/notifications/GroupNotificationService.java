package de.tum.cit.aet.artemis.communication.service.notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.course_notifications.AttachmentChangedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.DuplicateTestCaseNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.ExerciseOpenForPracticeNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.ExerciseUpdatedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewExerciseNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewManualFeedbackRequestNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.ProgrammingBuildRunUpdateNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.ProgrammingTestCasesChangedNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.QuizExerciseStartedNotification;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationService;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class GroupNotificationService {

    private static final Logger log = LoggerFactory.getLogger(GroupNotificationService.class);

    private final UserRepository userRepository;

    private final CourseNotificationService courseNotificationService;

    public GroupNotificationService(UserRepository userRepository, CourseNotificationService courseNotificationService) {
        this.userRepository = userRepository;
        this.courseNotificationService = courseNotificationService;
    }

    /**
     * Checks if a notification has to be created for this exercise update and creates one if the situation is appropriate
     *
     * @param exercise         that is updated
     * @param notificationText that is used for the notification process
     */
    public void notifyAboutExerciseUpdate(Exercise exercise, String notificationText) {
        if (exercise.isExamExercise()) {
            // Do not send an exercise-update notification if it's an exam exercise.
            // Exam exercise updates are handled using exam live events.
            return;
        }

        if (exercise.getReleaseDate() != null && exercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            // Do not send an exercise-update notification before the release date of the exercise.
            return;
        }

        if (notificationText != null) {
            // sends an exercise-update notification
            notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise);
        }
    }

    /**
     * Notify student groups about an attachment change.
     *
     * @param attachment that has been changed
     */
    public void notifyStudentGroupAboutAttachmentChange(Attachment attachment) {
        // Do not send a notification before the release date of the attachment.
        if (attachment.getReleaseDate() != null && attachment.getReleaseDate().isAfter(ZonedDateTime.now())) {
            return;
        }

        var course = attachment.getExercise() != null ? attachment.getExercise().getCourseViaExerciseGroupOrCourseMember() : attachment.getLecture().getCourse();
        var recipients = userRepository.getStudents(course);

        var attachmentChangedNotification = new AttachmentChangedNotification(course.getId(), course.getTitle(), course.getCourseIcon(), attachment.getName(),
                attachment.getExercise() == null ? attachment.getLecture().getTitle() : attachment.getExercise().getTitle(),
                attachment.getExercise() == null ? null : attachment.getExercise().getId(), attachment.getLecture() == null ? null : attachment.getLecture().getId());

        courseNotificationService.sendCourseNotification(attachmentChangedNotification, recipients.stream().toList());
    }

    /**
     * Notify students groups about an exercise opened for practice.
     *
     * @param exercise that has been opened for practice
     */
    public void notifyStudentGroupAboutExercisePractice(Exercise exercise) {
        if (exercise.isExamExercise()) {
            // Exam exercises have their own participation flow; do not send practice notifications to students.
            return;
        }

        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var recipients = userRepository.getStudents(course);

        var exerciseOpenForPracticeNotification = new ExerciseOpenForPracticeNotification(course.getId(), course.getTitle(), course.getCourseIcon(), exercise.getId(),
                exercise.getExerciseNotificationTitle());

        courseNotificationService.sendCourseNotification(exerciseOpenForPracticeNotification, recipients.stream().toList());
    }

    /**
     * Notify student groups about a started quiz exercise, asynchronously on the shared {@code taskExecutor}.
     *
     * <p>
     * The caller must supply pre-resolved primitives rather than the {@link QuizExercise} entity itself so this
     * method does not rely on the caller's Hibernate session (which is closed by the time the async task runs) and
     * does not need to re-fetch the quiz on the async thread. Fan-out to a large student cohort (observed &gt;8 s for
     * 2000 students on staging1) therefore no longer blocks the REST response.
     * </p>
     *
     * <p>
     * Exceptions are caught and logged to the structured log rather than propagated, since the caller is already
     * gone by the time this runs. The WebSocket broadcast, which happens separately from the caller, is unaffected.
     * </p>
     *
     * @param courseId          the id of the course the quiz belongs to
     * @param courseTitle       the human-readable course title used in the notification body
     * @param courseIcon        the course icon URL used in the notification body (may be {@code null})
     * @param studentGroupName  the group name used to resolve the recipients via {@link UserRepository}
     * @param quizExerciseId    the id of the quiz that was just started
     * @param notificationTitle the exercise-specific title used in the notification body
     */
    @Async
    public void notifyStudentGroupAboutQuizExerciseStartAsync(long courseId, String courseTitle, String courseIcon, String studentGroupName, long quizExerciseId,
            String notificationTitle) {
        try {
            var recipients = userRepository.findAllWithGroupsAndAuthoritiesByDeletedIsFalseAndGroupsContains(studentGroupName);
            var quizExerciseStartedNotification = new QuizExerciseStartedNotification(courseId, courseTitle, courseIcon, quizExerciseId, notificationTitle);
            courseNotificationService.sendCourseNotification(quizExerciseStartedNotification, recipients.stream().toList());
        }
        catch (Exception e) {
            log.warn("Failed to send quiz-started notification for quiz {}: {}", quizExerciseId, e.getMessage(), e);
        }
    }

    /**
     * Notify all groups but tutors about an exercise update.
     * Tutors will only work on the exercise during the assessment therefore it is not urgent to inform them about changes beforehand.
     * Students, instructors, and editors should be notified about changed as quickly as possible.
     *
     * @param exercise that has been updated
     */
    public void notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(Exercise exercise) {
        if (exercise.isExamExercise()) {
            // Do not send exercise update notifications to students for exam exercises.
            // The notification URL points to the course-management page which students cannot access.
            // Use notifyEditorAndInstructorGroupAboutExerciseUpdate instead for exam exercises.
            return;
        }

        // Do not send a notification before the release date of the exercise.
        if (exercise.getReleaseDate() != null && exercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            return;
        }

        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var recipients = userRepository.findAllWithGroupsAndAuthoritiesByDeletedIsFalseAndGroupsContains(
                Set.of(course.getEditorGroupName(), course.getInstructorGroupName(), course.getStudentGroupName()));

        var exerciseUpdatedNotification = new ExerciseUpdatedNotification(course.getId(), course.getTitle(), course.getCourseIcon(), exercise.getId(),
                exercise.getExerciseNotificationTitle(), null, null, exercise.getType());

        courseNotificationService.sendCourseNotification(exerciseUpdatedNotification, recipients.stream().toList());
    }

    /**
     * Notify all groups about a newly released exercise at the moment of its release date.
     * This notification can be deactivated in the notification settings
     *
     * @param exercise that has been created
     */
    public void notifyAllGroupsAboutReleasedExercise(Exercise exercise) {
        if (exercise.isExamExercise()) {
            // Exam exercises are not individually released; they are part of the exam flow.
            return;
        }

        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var recipients = userRepository.getUsersInCourse(course);

        var newExerciseNotification = new NewExerciseNotification(course.getId(), course.getTitle(), course.getCourseIcon(), exercise.getId(),
                exercise.getExerciseNotificationTitle(), exercise.getDifficulty() == null ? null : exercise.getDifficulty().toString(),
                exercise.getReleaseDate() == null ? null : exercise.getReleaseDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                exercise.getDueDate() == null ? null : exercise.getDueDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), exercise.getMaxPoints().longValue());

        courseNotificationService.sendCourseNotification(newExerciseNotification, recipients.stream().toList());
    }

    /**
     * Notify editor and instructor groups about an exercise update.
     *
     * @param exercise that has been updated
     */
    public void notifyEditorAndInstructorGroupAboutExerciseUpdate(Exercise exercise) {
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var recipients = userRepository.findAllWithGroupsAndAuthoritiesByDeletedIsFalseAndGroupsContains(Set.of(course.getEditorGroupName(), course.getInstructorGroupName()));

        ExerciseGroup exerciseGroup = exercise.isExamExercise() ? exercise.getExerciseGroup() : null;
        var exerciseUpdatedNotification = new ExerciseUpdatedNotification(course.getId(), course.getTitle(), course.getCourseIcon(), exercise.getId(),
                exercise.getExerciseNotificationTitle(), exerciseGroup != null ? exerciseGroup.getExam().getId() : null, exerciseGroup != null ? exerciseGroup.getId() : null,
                exercise.getType());

        courseNotificationService.sendCourseNotification(exerciseUpdatedNotification, recipients.stream().toList());
    }

    /**
     * Notify editor and instructor groups about changed test cases for a programming exercise.
     *
     * @param exercise that has been updated
     */
    public void notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise(ProgrammingExercise exercise) {
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var recipients = userRepository.findAllWithGroupsAndAuthoritiesByDeletedIsFalseAndGroupsContains(Set.of(course.getEditorGroupName(), course.getInstructorGroupName()));

        ExerciseGroup exerciseGroup = exercise.isExamExercise() ? exercise.getExerciseGroup() : null;
        var programmingTestCasesChangedNotification = new ProgrammingTestCasesChangedNotification(course.getId(), course.getTitle(), course.getCourseIcon(), exercise.getId(),
                exercise.getExerciseNotificationTitle(), exerciseGroup != null ? exerciseGroup.getExam().getId() : null, exerciseGroup != null ? exerciseGroup.getId() : null);

        courseNotificationService.sendCourseNotification(programmingTestCasesChangedNotification, recipients.stream().toList());
    }

    /**
     * Notify editor and instructor groups about started or completed build runs for all participants of an exercise.
     *
     * @param exercise the exercise where the builds status changed
     */
    public void notifyEditorAndInstructorGroupsAboutBuildRunUpdate(ProgrammingExercise exercise) {
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var recipients = userRepository.findAllWithGroupsAndAuthoritiesByDeletedIsFalseAndGroupsContains(Set.of(course.getEditorGroupName(), course.getInstructorGroupName()));

        ExerciseGroup exerciseGroup = exercise.isExamExercise() ? exercise.getExerciseGroup() : null;
        var programmingBuildRunUpdateNotification = new ProgrammingBuildRunUpdateNotification(course.getId(), course.getTitle(), course.getCourseIcon(), exercise.getId(),
                exercise.getExerciseNotificationTitle(), exerciseGroup != null ? exerciseGroup.getExam().getId() : null, exerciseGroup != null ? exerciseGroup.getId() : null);

        courseNotificationService.sendCourseNotification(programmingBuildRunUpdateNotification, recipients.stream().toList());
    }

    /**
     * Notify editor and instructor groups about duplicate test cases.
     *
     * @param exercise that has been updated
     */
    public void notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise(Exercise exercise) {
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var recipients = userRepository.findAllWithGroupsAndAuthoritiesByDeletedIsFalseAndGroupsContains(Set.of(course.getEditorGroupName(), course.getInstructorGroupName()));
        var formattedReleaseDate = exercise.getReleaseDate() != null ? exercise.getReleaseDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "-";
        var formattedDueDate = exercise.getDueDate() != null ? exercise.getDueDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "-";

        ExerciseGroup exerciseGroup = exercise.isExamExercise() ? exercise.getExerciseGroup() : null;
        var duplicateTestCaseNotification = new DuplicateTestCaseNotification(course.getId(), course.getTitle(), course.getCourseIcon(), exercise.getId(),
                exercise.getExerciseNotificationTitle(), formattedReleaseDate, formattedDueDate, exerciseGroup != null ? exerciseGroup.getExam().getId() : null,
                exerciseGroup != null ? exerciseGroup.getId() : null);

        courseNotificationService.sendCourseNotification(duplicateTestCaseNotification, recipients.stream().toList());
    }

    /**
     * Notifies a tutor that their feedback was requested.
     *
     * @param exercise that has been affected
     */
    public void notifyTutorGroupAboutNewFeedbackRequest(Exercise exercise) {
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var recipients = userRepository.getTutors(course);

        Long examId = exercise.isExamExercise() ? exercise.getExerciseGroup().getExam().getId() : null;
        var manualFeedbackRequestNotification = new NewManualFeedbackRequestNotification(course.getId(), course.getTitle(), course.getCourseIcon(), exercise.getId(),
                exercise.getExerciseNotificationTitle(), examId);

        courseNotificationService.sendCourseNotification(manualFeedbackRequestNotification, recipients.stream().toList());
    }
}
