package de.tum.cit.aet.artemis.core.service.connectors.campusonline;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.CampusOnlineEnabled;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineSyncResultDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlinePersonDTO;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineStudentListResponseDTO;
import de.tum.cit.aet.artemis.core.service.user.UserService;

/**
 * Service for synchronizing student enrollments from CAMPUSOnline into Artemis courses.
 * <p>
 * This service periodically fetches the student list from the CAMPUSOnline API for each
 * course that has a {@link de.tum.cit.aet.artemis.core.domain.CampusOnlineConfiguration}
 * and adds confirmed students to the corresponding Artemis course student group.
 * <p>
 * The sync can run:
 * <ul>
 * <li>Automatically via a scheduled cron job (default: every 6 hours)</li>
 * <li>Manually for all courses via the admin API</li>
 * <li>Manually for a single course via the admin API</li>
 * </ul>
 * Only students with a confirmed attendance status ("J") in CAMPUSOnline are synced.
 */
@Service
@Conditional(CampusOnlineEnabled.class)
@Lazy
@Profile(PROFILE_CORE)
public class CampusOnlineEnrollmentSyncService {

    private static final Logger log = LoggerFactory.getLogger(CampusOnlineEnrollmentSyncService.class);

    private final CampusOnlineClientService campusOnlineClient;

    private final CourseRepository courseRepository;

    private final UserService userService;

    private final ProfileService profileService;

    /**
     * Constructs the enrollment sync service with the required dependencies.
     *
     * @param campusOnlineClient the client service for CAMPUSOnline API calls
     * @param courseRepository   the repository for Artemis course persistence
     * @param userService        the user service for finding and enrolling users
     * @param profileService     the profile service for checking scheduling and dev mode
     */
    public CampusOnlineEnrollmentSyncService(CampusOnlineClientService campusOnlineClient, CourseRepository courseRepository, UserService userService,
            ProfileService profileService) {
        this.campusOnlineClient = campusOnlineClient;
        this.courseRepository = courseRepository;
        this.userService = userService;
        this.profileService = profileService;
    }

    /**
     * Scheduled task that syncs enrollments from CAMPUSOnline for all configured courses.
     * Runs according to the configured cron expression.
     */
    @Scheduled(cron = "${artemis.scheduling.campus-online-enrollment-sync-time:0 0 */6 * * *}")
    public void syncEnrollments() {
        if (!profileService.isSchedulingActive()) {
            return;
        }
        if (profileService.isDevActive()) {
            return;
        }
        log.info("Starting scheduled CAMPUSOnline enrollment sync");
        CampusOnlineSyncResultDTO result = performEnrollmentSync();
        log.info("CAMPUSOnline enrollment sync completed: {} courses synced, {} failed, {} users added, {} users not found", result.coursesSynced(), result.coursesFailed(),
                result.usersAdded(), result.usersNotFound());
    }

    /**
     * Performs enrollment sync for all courses with a CAMPUSOnline configuration.
     *
     * @return the sync result summary
     */
    public CampusOnlineSyncResultDTO performEnrollmentSync() {
        Set<Course> courses = courseRepository.findAllWithCampusOnlineConfiguration();
        int coursesSynced = 0;
        int coursesFailed = 0;
        int totalUsersAdded = 0;
        int totalUsersNotFound = 0;

        for (Course course : courses) {
            try {
                SyncCounts counts = syncCourseEnrollment(course);
                totalUsersAdded += counts.usersAdded;
                totalUsersNotFound += counts.usersNotFound;
                coursesSynced++;
            }
            catch (Exception e) {
                log.error("Failed to sync enrollment for course {} (ID: {}): {}", course.getTitle(), course.getId(), e.getMessage());
                coursesFailed++;
            }
        }

        return new CampusOnlineSyncResultDTO(coursesSynced, coursesFailed, totalUsersAdded, totalUsersNotFound);
    }

    /**
     * Performs enrollment sync for a single course.
     * Unlike batch sync, this method propagates errors so the admin gets actionable feedback.
     *
     * @param courseId the Artemis course ID
     * @return the sync result for this single course
     */
    public CampusOnlineSyncResultDTO performSingleCourseSync(long courseId) {
        Course course = courseRepository.findWithEagerCampusOnlineConfigurationById(courseId).orElseThrow(() -> new EntityNotFoundException("Course", courseId));
        if (course.getCampusOnlineConfiguration() == null) {
            throw new CampusOnlineApiException("Course " + courseId + " has no CAMPUSOnline configuration");
        }
        SyncCounts counts = syncCourseEnrollment(course);
        return new CampusOnlineSyncResultDTO(1, 0, counts.usersAdded, counts.usersNotFound);
    }

    /**
     * Syncs enrollment for a single course by fetching the student list from CAMPUSOnline
     * and adding confirmed students to the course's student group in Artemis.
     * <p>
     * Only students with attendance status "J" (confirmed/Ja) are considered.
     * Students are matched by registration number or email address.
     *
     * @param course the Artemis course with an associated CAMPUSOnline configuration
     * @return the sync counts (users added, users not found)
     */
    SyncCounts syncCourseEnrollment(Course course) {
        String campusOnlineCourseId = course.getCampusOnlineConfiguration().getCampusOnlineCourseId();
        log.info("Syncing enrollment for course '{}' (CAMPUSOnline ID: {})", course.getTitle(), campusOnlineCourseId);

        CampusOnlineStudentListResponseDTO response = campusOnlineClient.fetchStudents(campusOnlineCourseId);

        // Filter for students with confirmed attendance ("J" = Ja/Yes in German)
        List<CampusOnlinePersonDTO> confirmedStudents = response.persons() != null
                ? response.persons().stream().filter(p -> p.attendance() != null && "J".equals(p.attendance().confirmed())).toList()
                : List.of();

        int usersAdded = 0;
        int usersNotFound = 0;

        for (CampusOnlinePersonDTO person : confirmedStudents) {
            // Extract registration number and email from nested DTOs (may be null if not provided by API)
            String registrationNumber = person.extension() != null ? person.extension().registrationNumber() : null;
            String email = person.contactData() != null ? person.contactData().email() : null;

            // Try to find the user in Artemis by registration number or email and add to course group
            var optionalUser = userService.findUserAndAddToCourse(registrationNumber, null, email, course.getStudentGroupName());
            if (optionalUser.isPresent()) {
                usersAdded++;
            }
            else {
                log.debug("Could not find user for course '{}' (student not registered in Artemis)", course.getTitle());
                usersNotFound++;
            }
        }

        log.info("Synced course '{}': {} users added, {} users not found out of {} confirmed students", course.getTitle(), usersAdded, usersNotFound, confirmedStudents.size());
        return new SyncCounts(usersAdded, usersNotFound);
    }

    /**
     * Internal record to hold the sync result counts for a single course synchronization.
     *
     * @param usersAdded    the number of users successfully added to the course group
     * @param usersNotFound the number of confirmed students that could not be matched to Artemis users
     */
    private record SyncCounts(int usersAdded, int usersNotFound) {
    }
}
