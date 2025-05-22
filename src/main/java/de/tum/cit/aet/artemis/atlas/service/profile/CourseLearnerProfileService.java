package de.tum.cit.aet.artemis.atlas.service.profile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.CourseLearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.ProfileService;

@Conditional(AtlasEnabled.class)
@Service
public class CourseLearnerProfileService {

    private static final Logger log = LoggerFactory.getLogger(CourseLearnerProfileService.class);

    private final CourseLearnerProfileRepository courseLearnerProfileRepository;

    private final LearnerProfileRepository learnerProfileRepository;

    private final LearnerProfileService learnerProfileService;

    private final UserRepository userRepository;

    private final ProfileService profileService;

    public CourseLearnerProfileService(CourseLearnerProfileRepository courseLearnerProfileRepository, LearnerProfileRepository learnerProfileRepository,
            LearnerProfileService learnerProfileService, UserRepository userRepository, ProfileService profileService) {
        this.courseLearnerProfileRepository = courseLearnerProfileRepository;
        this.learnerProfileRepository = learnerProfileRepository;
        this.learnerProfileService = learnerProfileService;
        this.userRepository = userRepository;
        this.profileService = profileService;
    }

    /**
     * Create a course learner profile for a user and saves it in the database
     *
     * @param course the course for which the profile is created
     * @param user   the user for which the profile is created
     * @return Saved CourseLearnerProfile
     */
    public CourseLearnerProfile createCourseLearnerProfile(Course course, User user) {

        if (user.getLearnerProfile() == null) {
            learnerProfileService.createProfile(user);
        }

        var courseProfile = new CourseLearnerProfile();
        courseProfile.setCourse(course);

        // Initialize values in the middle of Likert scale
        courseProfile.setAimForGradeOrBonus(3);
        courseProfile.setRepetitionIntensity(3);
        courseProfile.setTimeInvestment(3);

        var learnerProfile = learnerProfileRepository.findByUserElseThrow(user);
        courseProfile.setLearnerProfile(learnerProfile);

        return courseLearnerProfileRepository.save(courseProfile);
    }

    /**
     * Create course learner profiles for a set of users and saves them in the database.
     *
     * @param course the course for which the profiles are created
     * @param users  the users for which the profiles are created with eagerly loaded learner profiles
     * @return A List of saved CourseLearnerProfiles
     */
    public List<CourseLearnerProfile> createCourseLearnerProfiles(Course course, Set<User> users) {

        users.stream().filter(user -> user.getLearnerProfile() == null).forEach(learnerProfileService::createProfile);

        Set<CourseLearnerProfile> courseProfiles = users.stream().map(user -> courseLearnerProfileRepository.findByLoginAndCourse(user.getLogin(), course).orElseGet(() -> {

            var courseProfile = new CourseLearnerProfile();
            courseProfile.setCourse(course);
            courseProfile.setLearnerProfile(learnerProfileRepository.findByUserElseThrow(user));

            // Initialize values in the middle of Likert scale
            courseProfile.setAimForGradeOrBonus(3);
            courseProfile.setRepetitionIntensity(3);
            courseProfile.setTimeInvestment(3);

            return courseProfile;
        })).collect(Collectors.toSet());

        return courseLearnerProfileRepository.saveAll(courseProfiles);
    }

    /**
     * Delete a course learner profile for a user
     *
     * @param course the course for which the profile is deleted
     * @param user   the user for which the profile is deleted
     */
    public void deleteCourseLearnerProfile(Course course, User user) {
        courseLearnerProfileRepository.deleteByCourseAndUser(course, user);
    }

    /**
     * Delete all course learner profiles for a course
     *
     * @param course the course for which the profiles are deleted
     */
    public void deleteAllForCourse(Course course) {
        courseLearnerProfileRepository.deleteAllByCourse(course);
    }

    /**
     * Calculates the estimated proficiency of a student utilizing lines changed by a student in a submission,
     * the score increase achieved and the lines changed between template and sample solution.
     * The values are cumulated and mapped to a likert scale using a sigmoid curve.
     *
     * @param linesChanged           Lines changed by the student.
     * @param linesChangedInTemplate Lines changed between template and sample solution
     * @param score                  Score increase by student.
     * @param currentProficiency     The current proficiency of the student.
     * @return The estimated proficiency of the student.
     */
    private double estimateProficiency(int linesChanged, int linesChangedInTemplate, double score, double currentProficiency) {

        if (score == 0) {
            // The student submitted a submission without changes.
            // No assumptions can be made about their proficiency.
            if (linesChanged == 0) {
                return currentProficiency;
            }
            // The student has changed some code without achieving a better score.
            // We assume a proficiency of 1.
            else {
                return 1;
            }
        }

        // weighing factor. Can be tuned to achieve better accuracy.
        final double WEIGHT_FACTOR = 0.05;
        // Factor to account for the disparity in line changes needed by students vs. the sample solution.
        final double LINE_TOLERANCE = 15;

        /*
         * Calculates exponent for sigmoid curve.
         * General formula of sigmoid f(t) = 1/(1+e^(-t))
         * Maps -Inf -> Inf to 0 -> 1.
         * We map the lines changed by the student, lines changed in the template and the achieved score to -Inf -> Inf.
         * We tune this with WEIGHT_FACTOR and LINE_TOLERANCE to statistically approach the expected proficiency distribution.
         */
        double exp = -(WEIGHT_FACTOR / score * (linesChanged - LINE_TOLERANCE * linesChangedInTemplate * Math.abs(score)));

        // Calculates point on sigmoid.
        // Flips and scales curve to match to Likert scale.
        return -4 * (1 / (Math.exp(exp))) + 5;
    }

    /**
     * Updates proficiency in the CourseLearnerProfile.
     * Uses the lines changed by the student, lines changed in the template and the achieved score of a submission to estimate a proficiency.
     * Moves the current proficiency towards the estimated proficiency.
     *
     * @param course                 The course the submission is from.
     * @param linesChanged           The lines changed by the student.
     * @param linesChangedInTemplate the lines changed in the template.
     * @param score                  The achieved score increase between this and the last submission.
     */
    public void updateProficiency(Set<User> users, Course course, int linesChanged, int linesChangedInTemplate, double score) {

        Set<CourseLearnerProfile> courseLearnerProfiles = users.stream().map(user -> courseLearnerProfileRepository.findByLoginAndCourseElseThrow(user.getLogin(), course))
                .collect(Collectors.toSet());

        courseLearnerProfiles.forEach(courseLearnerProfile -> {
            double currentProficiency = courseLearnerProfile.getProficiency();
            double estimatedProficiency = estimateProficiency(linesChanged, linesChangedInTemplate, score, currentProficiency);

            // Correct current proficiency by 10% of difference between estimated and current proficiency.
            final double CORRECTION_PERCENTAGE = 0.1;
            courseLearnerProfile.setProficiency(currentProficiency + CORRECTION_PERCENTAGE * (estimatedProficiency - currentProficiency));
            courseLearnerProfileRepository.save(courseLearnerProfile);

            log.debug("Update proficiency: LC {}, LCTemplate {}, Score {}. Previous proficiency: {}; Current proficiency: {}", linesChanged, linesChangedInTemplate, score,
                    currentProficiency, courseLearnerProfile.getProficiency());
        });

    }
}
