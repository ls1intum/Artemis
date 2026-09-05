package de.tum.cit.aet.artemis.course.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.config.DataCleanupProperties;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;

/**
 * Drives the two-phase, data-privacy (GDPR) retention workflow for old courses:
 * <ol>
 * <li><b>Warn:</b> once a course's retention period has elapsed (see {@link #retentionYears(Course)}), archive it (so
 * instructors keep a downloadable backup) and email the instructors that the student data will be deleted after a grace
 * period.</li>
 * <li><b>Reset:</b> once the grace period after the retention deadline has also elapsed, delete all student data via
 * {@link CourseResetService#resetStudentData(long)} while keeping the course material intact.</li>
 * </ol>
 * The retention deadline is derived from the course end date and the {@link DataCleanupProperties} cutoffs. The warning
 * event and the reset are recorded per course on the {@link CourseConfiguration} ({@code resetWarningSentDate} /
 * {@code studentDataResetDate}), giving a one-shot lifecycle: not warned → warned → reset. The grace period is measured
 * from the actual warning ({@code resetWarningSentDate}), so it is always honored regardless of scheduling/backlog, and a
 * course is never reset before its instructors were warned and given a backup.
 * <p>
 * Test courses, courses without an end date, and courses under a data-retention hold (a pending objection or legal
 * proceeding, see {@link CourseConfiguration#isDataRetentionHold()}) are always skipped. Eligibility is re-evaluated at
 * reset time rather than trusted from the warning, so a course that leaves the scope during the grace period has its
 * warning withdrawn instead of being reset before its current retention deadline. Courses are never deleted, only reset.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseDataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(CourseDataRetentionService.class);

    private static final String RESET_WARNING_EMAIL_TEMPLATE = "mail/courseStudentDataResetWarningEmail";

    private static final String RESET_WARNING_EMAIL_SUBJECT_KEY = "email.courseStudentDataResetWarning.title";

    private final CourseRepository courseRepository;

    private final CourseArchiveService courseArchiveService;

    private final CourseResetService courseResetService;

    private final UserRepository userRepository;

    private final MailSendingService mailSendingService;

    private final DataCleanupProperties dataCleanupProperties;

    public CourseDataRetentionService(CourseRepository courseRepository, CourseArchiveService courseArchiveService, CourseResetService courseResetService,
            UserRepository userRepository, MailSendingService mailSendingService, DataCleanupProperties dataCleanupProperties) {
        this.courseRepository = courseRepository;
        this.courseArchiveService = courseArchiveService;
        this.courseResetService = courseResetService;
        this.userRepository = userRepository;
        this.mailSendingService = mailSendingService;
        this.dataCleanupProperties = dataCleanupProperties;
    }

    /**
     * @return the courses whose retention period has elapsed and that have not yet been warned or reset
     */
    public List<Course> findCoursesDueForWarning() {
        ZonedDateTime now = ZonedDateTime.now();
        // Broadest candidate cutoff: a course can be due at the earliest after the shorter of the two retention periods.
        // Using the minimum (rather than assuming non-grade-relevant is shorter) stays correct even if the config is inverted.
        int shortestRetentionYears = Math.min(dataCleanupProperties.gradeRelevantRetentionYears(), dataCleanupProperties.nonGradeRelevantRetentionYears());
        ZonedDateTime candidateCutoff = now.minusYears(shortestRetentionYears);
        // A title is required to build the warning email; skip the (anomalous) title-less course rather than fail it forever.
        return courseRepository.findAllWithCourseConfigurationByEndDateBefore(candidateCutoff).stream().filter(course -> course.getTitle() != null)
                .filter(this::notYetWarnedOrReset).filter(course -> isEligibleForReset(course, now)).toList();
    }

    /**
     * @return the courses that were warned (and archived), whose grace period has elapsed, that are still eligible, and
     *         that have not yet been reset. The grace is measured from the actual warning event
     *         ({@code resetWarningSentDate}), not the course end date, so it is always honored and a course can never be
     *         reset before its instructors were warned. Eligibility is re-evaluated here rather than trusted from the
     *         warning, because a course can be moved out of scope after it was warned, see
     *         {@link #withdrawStaleResetWarnings()}.
     */
    public List<Course> findCoursesDueForReset() {
        ZonedDateTime now = ZonedDateTime.now();
        long graceDays = dataCleanupProperties.resetWarningGracePeriodDays();
        return warnedCoursesAwaitingReset().filter(course -> isEligibleForReset(course, now))
                .filter(course -> course.getCourseConfiguration().getResetWarningSentDate().plusDays(graceDays).isBefore(now)).toList();
    }

    /**
     * Phase 1: for every course due for warning, ensure an archive exists (instructor backup), email the instructors a
     * download link plus a notice that the student data will be deleted after the grace period, and stamp the warning
     * timestamp on the course configuration. Each course is handled independently: an archive failure or per-course error
     * is logged and skipped so the rest of the batch still runs, and the course is retried on the next run.
     *
     * @return the number of courses whose instructors were warned
     */
    public int warnAndArchiveDueCourses() {
        withdrawStaleResetWarnings();
        List<Course> dueCourses = findCoursesDueForWarning();
        log.info("Found {} old course(s) due for a student-data reset warning", dueCourses.size());
        int warned = 0;
        for (Course dueCourse : dueCourses) {
            try {
                // Archive on a course loaded with exercises/lectures (required by the export); the archive path is stored
                // on that instance.
                Course courseWithExercises = courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(dueCourse.getId());
                if (!courseWithExercises.hasCourseArchive() && !courseArchiveService.archiveCourseSynchronously(courseWithExercises)) {
                    log.warn("Could not archive course {} for the data-privacy warning; it will be retried on the next run", dueCourse.getId());
                    continue;
                }
                // Only advance the lifecycle once at least one instructor was actually warned. If mail is disabled or the
                // course has no eligible instructor (activated, with an email), leave it retryable and do NOT schedule a
                // reset, so student data is never deleted without its instructors having been warned.
                int notifiedInstructors = notifyInstructorsAboutUpcomingReset(dueCourse);
                if (notifiedInstructors == 0) {
                    log.warn("No instructor of course {} could be warned (mail disabled or no eligible recipient); leaving it retryable and not scheduling a reset",
                            dueCourse.getId());
                    continue;
                }
                // Persist the warning timestamp on the config-bearing instance (dueCourse has the configuration fetched),
                // syncing the archive path set during archiving so saving this instance does not clobber it.
                CourseConfiguration configuration = dueCourse.getCourseConfiguration();
                if (configuration == null) {
                    configuration = new CourseConfiguration();
                    configuration.setCourse(dueCourse);
                    dueCourse.setCourseConfiguration(configuration);
                }
                configuration.setResetWarningSentDate(ZonedDateTime.now());
                dueCourse.setCourseArchivePath(courseWithExercises.getCourseArchivePath());
                courseRepository.save(dueCourse);
                warned++;
            }
            catch (Exception e) {
                log.error("Failed to warn and archive course {} for the data-privacy reset", dueCourse.getId(), e);
            }
        }
        return warned;
    }

    /**
     * Phase 2: reset the student data of every course whose grace period after the warning has elapsed, keeping the
     * course material intact, and stamp the reset timestamp so the course is not reset again. Each course is handled
     * independently so a per-course failure does not abort the batch.
     *
     * @return the number of courses whose student data was reset
     */
    public int resetDueCourses() {
        withdrawStaleResetWarnings();
        List<Course> dueCourses = findCoursesDueForReset();
        log.info("Found {} old course(s) due for a student-data reset", dueCourses.size());
        int reset = 0;
        for (Course course : dueCourses) {
            try {
                log.info("Resetting student data of old course {} for data-privacy reasons", course.getId());
                courseResetService.resetStudentData(course.getId());
                CourseConfiguration configuration = course.getCourseConfiguration();
                configuration.setStudentDataResetDate(ZonedDateTime.now());
                courseRepository.save(course);
                reset++;
            }
            catch (Exception e) {
                log.error("Failed to reset the student data of course {}", course.getId(), e);
            }
        }
        return reset;
    }

    /**
     * Withdraws the warning of every course that was warned but has since left the scope of the cleanup, because an
     * instructor moved its end date into the future, marked it grade-relevant (which lengthens the retention period),
     * turned it into a test course, or placed it under a retention hold. Without this, such a course would keep its
     * warning and be reset at the end of the original grace period, before its current retention deadline.
     * <p>
     * Clearing the warning returns the course to the start of the lifecycle: if it becomes eligible again later, it has
     * to be warned again and gets a full grace period, rather than inheriting a warning its instructors received for a
     * deadline that no longer applies.
     *
     * @return the number of courses whose warning was withdrawn
     */
    private int withdrawStaleResetWarnings() {
        ZonedDateTime now = ZonedDateTime.now();
        List<Course> staleCourses = warnedCoursesAwaitingReset().filter(course -> !isEligibleForReset(course, now)).toList();
        for (Course staleCourse : staleCourses) {
            log.info("Withdrawing the student-data reset warning of course {}: it is no longer due for a reset", staleCourse.getId());
            staleCourse.getCourseConfiguration().setResetWarningSentDate(null);
            courseRepository.save(staleCourse);
        }
        return staleCourses.size();
    }

    /**
     * @return the warned courses that have not been reset yet, with their configuration guaranteed to be present
     */
    private Stream<Course> warnedCoursesAwaitingReset() {
        return courseRepository.findAllWithResetWarningSent().stream().filter(course -> {
            CourseConfiguration configuration = course.getCourseConfiguration();
            return configuration != null && configuration.getResetWarningSentDate() != null && configuration.getStudentDataResetDate() == null;
        });
    }

    private boolean notYetWarnedOrReset(Course course) {
        CourseConfiguration configuration = course.getCourseConfiguration();
        return configuration == null || (configuration.getResetWarningSentDate() == null && configuration.getStudentDataResetDate() == null);
    }

    /**
     * Whether the course is currently in scope for the data-privacy reset. Evaluated from the course's present state, so
     * it is equally valid before the warning and at reset time.
     *
     * @param course the course to check
     * @param now    the point in time to evaluate the retention deadline against
     * @return {@code true} if the course may be warned and, after the grace period, reset
     */
    private boolean isEligibleForReset(Course course, ZonedDateTime now) {
        return !course.isTestCourse() && !course.isDataRetentionHold() && isPastRetentionDeadline(course, now);
    }

    private boolean isPastRetentionDeadline(Course course, ZonedDateTime now) {
        return course.getEndDate().isBefore(now.minusYears(retentionYears(course)));
    }

    private int retentionYears(Course course) {
        return course.isGradeRelevant() ? dataCleanupProperties.gradeRelevantRetentionYears() : dataCleanupProperties.nonGradeRelevantRetentionYears();
    }

    /**
     * Emails every eligible instructor (activated, with an email address) of the course the upcoming-reset warning with a
     * download link to the archived backup.
     *
     * @param course the course whose instructors should be warned
     * @return the number of instructors a warning was dispatched to; {@code 0} if mail is disabled globally or the course
     *         has no eligible instructor (in which case the caller must not advance the reset lifecycle)
     */
    private int notifyInstructorsAboutUpcomingReset(Course course) {
        // Cannot warn anyone if mail is not configured, so the reset lifecycle must not advance.
        if (!mailSendingService.isMailConfigured()) {
            return 0;
        }
        Set<User> instructors = userRepository.getInstructors(course);
        Map<String, Object> contextVariables = Map.of("courseTitle", course.getTitle(), "courseId", course.getId(), "gracePeriodDays",
                dataCleanupProperties.resetWarningGracePeriodDays());
        int notified = 0;
        for (User instructor : instructors) {
            if (!instructor.getActivated() || instructor.getEmail() == null) {
                continue;
            }
            try {
                // Send synchronously and only count instructors who were actually warned, so the reset lifecycle
                // advances only once at least one warning was really delivered (an SMTP outage warns nobody -> the
                // caller does not advance the lifecycle -> no student data is reset without a delivered warning).
                boolean sent = mailSendingService.buildAndSendSyncReporting(MailRecipientDTO.from(instructor), RESET_WARNING_EMAIL_SUBJECT_KEY, List.of(course.getTitle()),
                        RESET_WARNING_EMAIL_TEMPLATE, contextVariables);
                if (sent) {
                    notified++;
                }
            }
            catch (Exception ex) {
                log.error("Failed to send student-data reset warning email to instructor {} for course {}", instructor.getLogin(), course.getId(), ex);
            }
        }
        return notified;
    }
}
