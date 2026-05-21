package de.tum.cit.aet.artemis.atlas.api;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.CourseLearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.service.profile.CourseLearnerProfileService;
import de.tum.cit.aet.artemis.atlas.service.profile.LearnerProfileService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.export.LearnerProfileExportDTO;
import de.tum.cit.aet.artemis.core.dto.export.UserLearnerProfileExportDTO;

@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class LearnerProfileApi extends AbstractAtlasApi {

    private final LearnerProfileService learnerProfileService;

    private final CourseLearnerProfileService courseLearnerProfileService;

    private final LearnerProfileRepository learnerProfileRepository;

    private final CourseLearnerProfileRepository courseLearnerProfileRepository;

    public LearnerProfileApi(LearnerProfileService learnerProfileService, CourseLearnerProfileService courseLearnerProfileService,
            LearnerProfileRepository learnerProfileRepository, CourseLearnerProfileRepository courseLearnerProfileRepository) {
        this.learnerProfileService = learnerProfileService;
        this.courseLearnerProfileService = courseLearnerProfileService;
        this.learnerProfileRepository = learnerProfileRepository;
        this.courseLearnerProfileRepository = courseLearnerProfileRepository;
    }

    public void deleteAllForCourse(Course course) {
        courseLearnerProfileService.deleteAllForCourse(course);
    }

    /**
     * Delete all course learner profiles for a course by its ID.
     *
     * @param courseId the ID of the course
     */
    public void deleteAllForCourseId(long courseId) {
        courseLearnerProfileRepository.deleteAllByCourseId(courseId);
    }

    /**
     * Count the number of course learner profiles for a course.
     *
     * @param courseId the ID of the course
     * @return the number of course learner profiles
     */
    public long countByCourseId(long courseId) {
        return courseLearnerProfileRepository.countByCourseId(courseId);
    }

    public void createCourseLearnerProfile(Course course, User user) {
        courseLearnerProfileService.createCourseLearnerProfile(course, user);
    }

    public void createCourseLearnerProfiles(Course course, Set<User> students) {
        courseLearnerProfileService.createCourseLearnerProfiles(course, students);
    }

    public void deleteCourseLearnerProfile(Course course, User user) {
        courseLearnerProfileService.deleteCourseLearnerProfile(course, user);
    }

    public void createProfile(User user) {
        learnerProfileService.createProfile(user);
    }

    /**
     * Get or create a learner profile for a user
     *
     * @param user the user for which the profile is retrieved or created
     * @return Saved LearnerProfile
     */
    public LearnerProfile getOrCreateLearnerProfile(User user) {
        return learnerProfileService.getOrCreateLearnerProfile(user);
    }

    /**
     * Delete a learner profile by its user
     *
     * @param user the user for which the profile is deleted
     */
    public void deleteProfile(User user) {
        learnerProfileRepository.deleteByUser(user);
    }

    /**
     * Find all course learner profiles for a course for export.
     *
     * @param courseId the ID of the course
     * @return list of learner profile export DTOs
     */
    public List<LearnerProfileExportDTO> findAllForExportByCourseId(long courseId) {
        return courseLearnerProfileRepository.findAllForExportByCourseId(courseId);
    }

    /**
     * Find all course learner profiles for a user for GDPR data export.
     *
     * @param userId the ID of the user
     * @return list of user learner profile export DTOs with course information
     */
    public List<UserLearnerProfileExportDTO> findAllForExportByUserId(long userId) {
        return courseLearnerProfileRepository.findAllForExportByUserId(userId);
    }
}
