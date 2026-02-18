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

    SyncCounts syncCourseEnrollment(Course course) {
        String campusOnlineCourseId = course.getCampusOnlineConfiguration().getCampusOnlineCourseId();
        log.info("Syncing enrollment for course '{}' (CAMPUSOnline ID: {})", course.getTitle(), campusOnlineCourseId);

        CampusOnlineStudentListResponseDTO response = campusOnlineClient.fetchStudents(campusOnlineCourseId);
        List<CampusOnlinePersonDTO> confirmedStudents = response.persons() != null
                ? response.persons().stream().filter(p -> p.attendance() != null && "J".equals(p.attendance().confirmed())).toList()
                : List.of();

        int usersAdded = 0;
        int usersNotFound = 0;

        for (CampusOnlinePersonDTO person : confirmedStudents) {
            String registrationNumber = person.extension() != null ? person.extension().registrationNumber() : null;
            String email = person.contactData() != null ? person.contactData().email() : null;

            var optionalUser = userService.findUserAndAddToCourse(registrationNumber, null, email, course.getStudentGroupName());
            if (optionalUser.isPresent()) {
                usersAdded++;
            }
            else {
                log.debug("Could not find user with registration number '{}' or email '{}' for course '{}'", registrationNumber, email, course.getTitle());
                usersNotFound++;
            }
        }

        log.info("Synced course '{}': {} users added, {} users not found out of {} confirmed students", course.getTitle(), usersAdded, usersNotFound, confirmedStudents.size());
        return new SyncCounts(usersAdded, usersNotFound);
    }

    private record SyncCounts(int usersAdded, int usersNotFound) {
    }
}
