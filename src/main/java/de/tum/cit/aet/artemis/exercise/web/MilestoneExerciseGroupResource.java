package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.validation.Valid;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.exercise.dto.CreateMilestoneExerciseGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.CreateUserStoryExerciseDTO;
import de.tum.cit.aet.artemis.exercise.dto.MilestoneExerciseGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.MilestoneStatusDTO;
import de.tum.cit.aet.artemis.exercise.dto.UpdateMilestoneExerciseGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.UserStoryExerciseDTO;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.service.MilestoneExerciseService;

/**
 * REST controller for managing {@link MilestoneExerciseGroup}s — the Scrum-style groups whose members share one set of
 * repositories, anchored by a {@link MilestoneExercise}.
 * <p>
 * Deliberately a separate resource from {@link ExerciseVariantGroupResource}, which serves the ExerciseVariantGroups: a
 * milestone group carries a whole programming exercise as its anchor, so it is created, read, updated and deleted through
 * its own routes, its own {@link MilestoneExerciseService} and its own repository. Clients fetch the two group types
 * separately; nothing here goes through the variant-group endpoints.
 * <p>
 * Authorization mirrors the ExerciseVariantGroups: editors create, read and update, only instructors delete. Every
 * endpoint verifies that the group belongs to the course in the request path.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/exercise/")
public class MilestoneExerciseGroupResource {

    private static final Logger log = LoggerFactory.getLogger(MilestoneExerciseGroupResource.class);

    private static final String ENTITY_NAME = "milestoneExerciseGroup";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final MilestoneExerciseService milestoneExerciseService;

    private final UserRepository userRepository;

    public MilestoneExerciseGroupResource(MilestoneExerciseService milestoneExerciseService, UserRepository userRepository) {
        this.milestoneExerciseService = milestoneExerciseService;
        this.userRepository = userRepository;
    }

    /**
     * POST /courses/:courseId/milestone-exercise-groups : Create a new milestone exercise group in the given course.
     * <p>
     * Unlike an ExerciseVariantGroup, a milestone group is never created empty: this provisions a real
     * {@link MilestoneExercise} (repositories, build plan, the works) and wires it as the new group's anchor in one
     * request.
     *
     * @param createDTO the settings of the milestone exercise to set up
     * @param courseId  the id of the course that will own the group
     * @return the ResponseEntity with status 201 (Created) and the created group in the body
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/milestone-exercise-groups")
    @EnforceAtLeastEditorInCourse
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<MilestoneExerciseGroupDTO> createMilestoneExerciseGroup(@Valid @RequestBody CreateMilestoneExerciseGroupDTO createDTO, @PathVariable Long courseId)
            throws URISyntaxException {
        log.debug("REST request to create MilestoneExerciseGroup in course {}", courseId);
        MilestoneExerciseGroup group;
        try {
            group = milestoneExerciseService.createMilestoneGroup(createDTO, courseId);
        }
        catch (IOException | GitAPIException | ContinuousIntegrationException e) {
            log.error("Error while setting up milestone exercise", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(HeaderUtil.createAlert(applicationName, "An error occurred while setting up the milestone: " + e.getMessage(), "errorProgrammingExercise")).body(null);
        }
        return ResponseEntity.created(new URI("/api/exercise/courses/" + courseId + "/milestone-exercise-groups/" + group.getId())).body(new MilestoneExerciseGroupDTO(group));
    }

    /**
     * GET /courses/:courseId/milestone-exercise-groups : Get all milestone exercise groups of a course.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with status 200 (OK) and the list of milestone groups in the body
     */
    @GetMapping("courses/{courseId}/milestone-exercise-groups")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<List<MilestoneExerciseGroupDTO>> getMilestoneExerciseGroupsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all MilestoneExerciseGroups for course {}", courseId);
        List<MilestoneExerciseGroupDTO> groups = milestoneExerciseService.findAllByCourseId(courseId).stream().map(MilestoneExerciseGroupDTO::new).toList();
        return ResponseEntity.ok(groups);
    }

    /**
     * GET /courses/:courseId/milestone-exercise-groups/:groupId : Get a single milestone exercise group.
     *
     * @param groupId  the id of the group to retrieve
     * @param courseId the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK) and the group in the body
     */
    @GetMapping("courses/{courseId}/milestone-exercise-groups/{groupId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<MilestoneExerciseGroupDTO> getMilestoneExerciseGroup(@PathVariable Long groupId, @PathVariable Long courseId) {
        log.debug("REST request to get MilestoneExerciseGroup {} in course {}", groupId, courseId);
        MilestoneExerciseGroup group = milestoneExerciseService.findByIdAndCourseIdElseThrow(groupId, courseId);
        return ResponseEntity.ok(new MilestoneExerciseGroupDTO(group));
    }

    /**
     * PUT /courses/:courseId/milestone-exercise-groups/:groupId : Update a milestone exercise group's title and shared
     * timeline. The owning course cannot be changed. The timeline is stored on the group's anchor milestone exercise and
     * pushed onto every member.
     *
     * @param updateDTO the new settings of the group
     * @param groupId   the id of the group to update
     * @param courseId  the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK) and the updated group in the body
     */
    @PutMapping("courses/{courseId}/milestone-exercise-groups/{groupId}")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<MilestoneExerciseGroupDTO> updateMilestoneExerciseGroup(@Valid @RequestBody UpdateMilestoneExerciseGroupDTO updateDTO, @PathVariable Long groupId,
            @PathVariable Long courseId) {
        log.debug("REST request to update MilestoneExerciseGroup {} in course {} : {}", groupId, courseId, updateDTO);
        MilestoneExerciseGroup group = milestoneExerciseService.updateMilestoneGroup(updateDTO, groupId, courseId);
        // Respond from the loaded entity (its members were fetched); the re-merged save() result has a lazy exercises
        // collection that cannot initialize once the session closes (open-in-view is off).
        return ResponseEntity.ok(new MilestoneExerciseGroupDTO(group));
    }

    /**
     * DELETE /courses/:courseId/milestone-exercise-groups/:groupId : Delete a milestone exercise group together with its
     * anchor milestone exercise. Only an empty group can be deleted — its members share its repositories, so they cannot
     * simply be ungrouped the way an ExerciseVariantGroup's members are.
     *
     * @param groupId  the id of the group to delete
     * @param courseId the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("courses/{courseId}/milestone-exercise-groups/{groupId}")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> deleteMilestoneExerciseGroup(@PathVariable Long groupId, @PathVariable Long courseId) {
        log.debug("REST request to delete MilestoneExerciseGroup {} in course {}", groupId, courseId);
        String title = milestoneExerciseService.deleteMilestoneGroup(groupId, courseId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, title)).build();
    }

    /**
     * POST /courses/:courseId/milestone-exercise-groups/:groupId/user-story-exercises : Create a new user story exercise
     * in the given milestone exercise group.
     * <p>
     * Its Language/Version-Control settings, repositories and timeline are the group's {@link MilestoneExercise}'s — a
     * user story is never independently configured on any of these — so {@link CreateUserStoryExerciseDTO} carries none
     * of them: only what a user story owns for itself (title, short name, problem statement and grading settings) is
     * taken from the request, and the target group from the path.
     *
     * @param createDTO the settings of the user story exercise to create
     * @param groupId   the id of the milestone exercise group that will own the exercise
     * @param courseId  the id of the course the group belongs to
     * @return the ResponseEntity with status 201 (Created) and the created exercise in the body
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/milestone-exercise-groups/{groupId}/user-story-exercises")
    @EnforceAtLeastEditorInCourse
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<UserStoryExerciseDTO> createUserStoryExercise(@Valid @RequestBody CreateUserStoryExerciseDTO createDTO, @PathVariable Long groupId,
            @PathVariable Long courseId) throws URISyntaxException {
        log.debug("REST request to create UserStoryExercise in milestone exercise group {} of course {}", groupId, courseId);
        UserStoryExercise created = milestoneExerciseService.createUserStoryExercise(createDTO, groupId, courseId);
        return ResponseEntity.created(new URI("/api/programming/programming-exercises/" + created.getId())).body(new UserStoryExerciseDTO(created));
    }

    /**
     * GET /courses/:courseId/milestone-exercise-groups/:groupId/milestone-status : Whether the requesting student has
     * started the group's anchor milestone exercise.
     * <p>
     * The milestone exercise is never itself shown to students ({@code MilestoneExercise.isVisibleToStudents} is always
     * {@code false}), so the milestone group view can't just fetch its details like any other exercise to find this out.
     * All the group's {@link UserStoryExercise}s share the milestone's repository once it's started, so this is what the
     * view uses to decide whether to offer a "Start exercise" action for the milestone itself.
     *
     * @param groupId  the id of the milestone exercise group to check
     * @param courseId the id of the course the group belongs to
     * @return the ResponseEntity with status 200 (OK) and the milestone's id, whether the student has started it, and
     *         the milestone's problem statement (which doubles as the group's description in the student group view)
     */
    @GetMapping("courses/{courseId}/milestone-exercise-groups/{groupId}/milestone-status")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<MilestoneStatusDTO> getMilestoneStatus(@PathVariable Long groupId, @PathVariable Long courseId) {
        log.debug("REST request to get milestone status of MilestoneExerciseGroup {} in course {}", groupId, courseId);
        User user = userRepository.getUserWithAuthorities();
        return ResponseEntity.ok(milestoneExerciseService.getMilestoneStatus(groupId, courseId, user));
    }
}
