package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.dto.UserStoryEffortDTO;
import de.tum.cit.aet.artemis.programming.dto.UserStoryEffortStatusDTO;
import de.tum.cit.aet.artemis.programming.service.UserStoryEffortService;

/**
 * REST controller for the effort a participant reports on a {@link UserStoryExercise}: what they estimated the story
 * would take, and what it actually took.
 * <p>
 * Both endpoints act on the requesting user's own participation only - there is no way to read or write someone else's
 * through here. A tutor reads the pair off the participation while assessing instead (it is serialized with it), and
 * therefore needs no endpoint of its own.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class UserStoryEffortResource {

    private static final Logger log = LoggerFactory.getLogger(UserStoryEffortResource.class);

    private final UserStoryEffortService userStoryEffortService;

    private final UserRepository userRepository;

    public UserStoryEffortResource(UserStoryEffortService userStoryEffortService, UserRepository userRepository) {
        this.userStoryEffortService = userStoryEffortService;
        this.userRepository = userRepository;
    }

    /**
     * GET /user-story-exercises/:exerciseId/effort : Get the effort the requesting user has reported for the story.
     *
     * @param exerciseId the id of the user story exercise
     * @return the ResponseEntity with status 200 (OK) and the reported pair in the body, with unset values omitted
     */
    @GetMapping("user-story-exercises/{exerciseId}/effort")
    @EnforceAtLeastStudent
    public ResponseEntity<UserStoryEffortDTO> getUserStoryEffort(@PathVariable long exerciseId) {
        log.debug("REST request to get the reported effort for user story exercise {}", exerciseId);
        User user = userRepository.getUser();
        return ResponseEntity.ok(userStoryEffortService.findForUser(exerciseId, user));
    }

    /**
     * GET /courses/:courseId/user-story-efforts : Every user story in the course the requesting user has started, with
     * whatever effort they have reported for it.
     * <p>
     * One request for the whole exercise overview, which marks the stories still missing an estimate. The pair is
     * deliberately not serialized with each participation: an inverse {@code @OneToOne} cannot be proxied, so that cost
     * a query per participation and broke the dashboard payload once the participation was detached.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and one entry per started story
     */
    @GetMapping("courses/{courseId}/user-story-efforts")
    @EnforceAtLeastStudent
    public ResponseEntity<List<UserStoryEffortStatusDTO>> getUserStoryEffortsForCourse(@PathVariable long courseId) {
        log.debug("REST request to get the reported user story efforts in course {}", courseId);
        User user = userRepository.getUser();
        return ResponseEntity.ok(userStoryEffortService.findAllForCourse(courseId, user));
    }

    /**
     * GET /participations/:participationId/user-story-effort : The effort reported on one participation.
     * <p>
     * For the tutor assessing that participation; the participant themself may read it too. Access is checked against
     * the participation itself, so this grants nothing the assessment view does not already have.
     *
     * @param participationId the id of the participation
     * @return the ResponseEntity with status 200 (OK) and the reported pair in the body
     */
    @GetMapping("participations/{participationId}/user-story-effort")
    @EnforceAtLeastStudent
    public ResponseEntity<UserStoryEffortDTO> getUserStoryEffortForParticipation(@PathVariable long participationId) {
        log.debug("REST request to get the reported effort on participation {}", participationId);
        return ResponseEntity.ok(userStoryEffortService.findForParticipation(participationId));
    }

    /**
     * PUT /user-story-exercises/:exerciseId/effort : Record the effort the requesting user reports for the story,
     * replacing anything they reported before.
     * <p>
     * Either value may be left unset, so a student can record the estimate up front and the actual effort later. Both
     * stop being writable once the story is due.
     *
     * @param exerciseId the id of the user story exercise
     * @param effortDTO  the reported pair, in hours
     * @return the ResponseEntity with status 200 (OK) and the stored pair in the body
     */
    @PutMapping("user-story-exercises/{exerciseId}/effort")
    @EnforceAtLeastStudent
    public ResponseEntity<UserStoryEffortDTO> updateUserStoryEffort(@PathVariable long exerciseId, @RequestBody UserStoryEffortDTO effortDTO) {
        log.debug("REST request to report the effort for user story exercise {} : {}", exerciseId, effortDTO);
        User user = userRepository.getUser();
        return ResponseEntity.ok(userStoryEffortService.save(exerciseId, effortDTO, user));
    }
}
