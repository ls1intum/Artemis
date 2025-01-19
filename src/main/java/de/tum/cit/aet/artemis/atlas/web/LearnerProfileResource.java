package de.tum.cit.aet.artemis.atlas.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.dto.CourseLearnerProfileDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseLearnerProfileRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
@Transactional
public class LearnerProfileResource {

    private static final Logger log = LoggerFactory.getLogger(LearnerProfileResource.class);

    @Autowired
    private final UserRepository userRepository;

    @Autowired
    private final CourseLearnerProfileRepository courseLearnerProfileRepository;

    public LearnerProfileResource(UserRepository userRepository, CourseLearnerProfileRepository courseLearnerProfileRepository) {
        this.userRepository = userRepository;
        this.courseLearnerProfileRepository = courseLearnerProfileRepository;
    }

    /**
     * GET /learner-profiles/course-learner-profiles : get all CourseLearnerProfile for a user
     *
     * @return The ResponseEntity with status 200 (OK) and with body containing a map of DTOs, wich contain per course profile data.
     */
    @GetMapping("learner-profiles/course-learner-profiles")
    @EnforceAtLeastStudent
    public ResponseEntity<Map<Long, CourseLearnerProfileDTO>> getCourseLearnerProfiles() {
        User user = userRepository.getUser();
        log.debug("REST request to get all CourseLearnerProfiles of user {}", user.getLogin());
        Map<Long, CourseLearnerProfileDTO> result = courseLearnerProfileRepository.findAllByLogin(user.getLogin()).stream()
                .collect(Collectors.toMap(profile -> profile.getCourse().getId(), CourseLearnerProfileDTO::of));
        return ResponseEntity.ok(result);
    }

    /**
     * PUT /learner-profiles/course-learner-profiles/{courseLearnerProfileId} : update a CourseLearnerProfile
     *
     * @param courseLearnerProfileId ID of the CourseLearnerProfile
     * @return A ResponseEntity with a status matching the validity of the request containing the updated profile.
     */
    @PutMapping(value = "learner-profiles/course-learner-profiles/{courseLearnerProfileId}")
    @EnforceAtLeastStudent
    public ResponseEntity<CourseLearnerProfileDTO> updateCourseLearnerProfile(@PathVariable long courseLearnerProfileId,
            @RequestBody CourseLearnerProfileDTO courseLearnerProfileDTO) {
        User user = userRepository.getUser();
        log.debug("REST request to update CourseLearnerProfile {} of user {}", courseLearnerProfileId, user);

        if (courseLearnerProfileDTO.id() != courseLearnerProfileId) {
            throw new BadRequestAlertException("Provided courseLEarnerProfileId does not match CourseLearnerProfile.", CourseLearnerProfile.ENTITY_NAME, "objectDoesNotMatchId",
                    true);
        }

        Set<CourseLearnerProfile> clps = courseLearnerProfileRepository.findAllByLogin(user.getLogin());
        Optional<CourseLearnerProfile> optionalCourseLearnerProfile = clps.stream()
                .filter(clp -> clp.getId() == courseLearnerProfileId && clp.getCourse().getId() == courseLearnerProfileDTO.courseId()).findFirst();

        if (optionalCourseLearnerProfile.isEmpty()) {
            throw new BadRequestAlertException("CourseLearnerProfile not found.", CourseLearnerProfile.ENTITY_NAME, "courseLearnerProfileNotFound", true);
        }

        if (courseLearnerProfileDTO.aimForGradeOrBonus() < 0 || courseLearnerProfileDTO.aimForGradeOrBonus() > 4) {
            throw new BadRequestAlertException("AimForGradeOrBonus field is outside valid bounds", CourseLearnerProfile.ENTITY_NAME, "aimForGradeOrBonusOutOfBounds", true);
        }
        if (courseLearnerProfileDTO.timeInvestment() < 0 || courseLearnerProfileDTO.timeInvestment() > 4) {
            throw new BadRequestAlertException("TimeInvestment field is outside valid bounds", CourseLearnerProfile.ENTITY_NAME, "timeInvestmentOutOfBounds", true);
        }
        if (courseLearnerProfileDTO.repetitionIntensity() < 0 || courseLearnerProfileDTO.repetitionIntensity() > 4) {
            throw new BadRequestAlertException("RepetitionIntensity field is outside valid bounds", CourseLearnerProfile.ENTITY_NAME, "repetitionIntensityOutOfBounds", true);
        }

        CourseLearnerProfile updateProfile = optionalCourseLearnerProfile.get();
        updateProfile.setAimForGradeOrBonus(courseLearnerProfileDTO.aimForGradeOrBonus());
        updateProfile.setTimeInvestment(courseLearnerProfileDTO.timeInvestment());
        updateProfile.setRepetitionIntensity(courseLearnerProfileDTO.repetitionIntensity());

        CourseLearnerProfile result = courseLearnerProfileRepository.save(updateProfile);
        return ResponseEntity.ok(CourseLearnerProfileDTO.of(result));
    }
}
