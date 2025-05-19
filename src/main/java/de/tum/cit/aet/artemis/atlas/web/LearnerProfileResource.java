package de.tum.cit.aet.artemis.atlas.web;

import static de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile.MAX_PROFILE_VALUE;
import static de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile.MIN_PROFILE_VALUE;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.dto.CourseLearnerProfileDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseLearnerProfileRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;

@Conditional(AtlasEnabled.class)
@RestController
@RequestMapping("api/atlas/")
public class LearnerProfileResource {

    private static final Logger log = LoggerFactory.getLogger(LearnerProfileResource.class);

    private final UserRepository userRepository;

    private final CourseLearnerProfileRepository courseLearnerProfileRepository;

    public LearnerProfileResource(UserRepository userRepository, CourseLearnerProfileRepository courseLearnerProfileRepository) {
        this.userRepository = userRepository;
        this.courseLearnerProfileRepository = courseLearnerProfileRepository;
    }

    /**
     * GET course-learner-profiles : get a Map of a {@link de.tum.cit.aet.artemis.core.domain.Course} id
     * to the corresponding {@link CourseLearnerProfile} of the logged-in user.
     *
     * @return The ResponseEntity with status 200 (OK) and with the body containing a map of DTOs, which contains per course profile data.
     */
    @GetMapping("course-learner-profiles")
    @EnforceAtLeastStudent
public ResponseEntity<Set<CourseLearnerProfileDTO>> getCourseLearnerProfiles() {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get all CourseLearnerProfiles of user {}", user.getLogin());
        var now = ZonedDateTime.now();
        Set<Long, CourseLearnerProfileDTO> courseLearnerProfiles = courseLearnerProfileRepository.findAllByLoginWithCourse(user.getLogin()).stream()
            // Check if course is active
            .filter(profile -> profile.getCourse().getEndDate() == null || profile.getCourse().getEndDate().isAfter(now))
            .filter(profile -> profile.getCourse().getStartDate() == null || profile.getCourse().getStartDate().isBefore(now))
            // Check if user is student in course
            .filter(profile -> user.getGroups().contains(profile.getCourse().getStudentGroupName()))
            .map(CourseLearnerProfileDTO::of)
            .collect(Collectors.toSet());
        return ResponseEntity.ok(courseLearnerProfiles);
    }

    /**
     * Validates that fields are within {@link #MIN_PROFILE_VALUE} and {@link #MAX_PROFILE_VALUE}.
     *
     * @param value     Value of the field
     * @param fieldName Field name
     */
    private void validateProfileField(int value, String fieldName) {
        if (value < MIN_PROFILE_VALUE || value > MAX_PROFILE_VALUE) {
            String message = String.format("%s (%d) is outside valid bounds [%d, %d]", fieldName, value, MIN_PROFILE_VALUE, MAX_PROFILE_VALUE);
            throw new BadRequestAlertException(message, CourseLearnerProfile.ENTITY_NAME, fieldName.toLowerCase() + "OutOfBounds", true);
        }
    }

    /**
     * PUT course-learner-profiles/{courseLearnerProfileId} : update fields in a {@link CourseLearnerProfile}.
     *
     * @param courseLearnerProfileId  ID of the CourseLearnerProfile
     * @param courseLearnerProfileDTO {@link CourseLearnerProfileDTO} object from the request body.
     * @return A ResponseEntity with a status matching the validity of the request containing the updated profile.
     */
    @PutMapping(value = "course-learner-profiles/{courseLearnerProfileId}")
    @EnforceAtLeastStudent
    public ResponseEntity<CourseLearnerProfileDTO> updateCourseLearnerProfile(@PathVariable long courseLearnerProfileId,
            @RequestBody CourseLearnerProfileDTO courseLearnerProfileDTO) {
        User user = userRepository.getUser();
        log.debug("REST request to update CourseLearnerProfile {} of user {}", courseLearnerProfileId, user);

        if (courseLearnerProfileDTO.id() != courseLearnerProfileId) {
            throw new BadRequestAlertException("Provided courseLEarnerProfileId does not match CourseLearnerProfile.", CourseLearnerProfile.ENTITY_NAME, "objectDoesNotMatchId",
                    true);
        }

        Optional<CourseLearnerProfile> optionalCourseLearnerProfile = courseLearnerProfileRepository.findByLoginAndId(user.getLogin(), courseLearnerProfileId);

        if (optionalCourseLearnerProfile.isEmpty()) {
            throw new BadRequestAlertException("CourseLearnerProfile not found.", CourseLearnerProfile.ENTITY_NAME, "courseLearnerProfileNotFound", true);
        }

        validateProfileField(courseLearnerProfileDTO.aimForGradeOrBonus(), "AimForGradeOrBonus");
        validateProfileField(courseLearnerProfileDTO.timeInvestment(), "TimeInvestment");
        validateProfileField(courseLearnerProfileDTO.repetitionIntensity(), "RepetitionIntensity");

        CourseLearnerProfile updateProfile = optionalCourseLearnerProfile.get();
        updateProfile.setAimForGradeOrBonus(courseLearnerProfileDTO.aimForGradeOrBonus());
        updateProfile.setTimeInvestment(courseLearnerProfileDTO.timeInvestment());
        updateProfile.setRepetitionIntensity(courseLearnerProfileDTO.repetitionIntensity());

        courseLearnerProfileRepository.save(updateProfile);
        return ResponseEntity.ok(CourseLearnerProfileDTO.of(updateProfile));
    }
}
