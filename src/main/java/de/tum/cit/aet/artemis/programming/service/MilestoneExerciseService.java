package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;
import de.tum.cit.aet.artemis.exercise.dto.CreateMilestoneExerciseGroupDTO;
import de.tum.cit.aet.artemis.exercise.dto.CreateUserStoryExerciseDTO;
import de.tum.cit.aet.artemis.exercise.dto.MilestoneStatusDTO;
import de.tum.cit.aet.artemis.exercise.dto.UpdateMilestoneExerciseGroupDTO;
import de.tum.cit.aet.artemis.exercise.repository.MilestoneExerciseGroupRepository;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVariantGroupService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfigHelper;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * Owns everything specific to a {@link MilestoneExerciseGroup}: creating one (which provisions its anchor
 * {@link MilestoneExercise} through the regular programming-exercise pipeline), reading it, updating its shared timeline,
 * and deleting it together with that anchor.
 * <p>
 * Deliberately separate from {@link ExerciseVariantGroupService}, which handles what all variant groups share (pushing a
 * group's timeline onto its members, and group membership) and is reused unchanged from here.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class MilestoneExerciseService {

    private static final Logger log = LoggerFactory.getLogger(MilestoneExerciseService.class);

    private static final String ENTITY_NAME = "milestoneExerciseGroup";

    /**
     * The two endpoints lifted from the variant-group resource keep its entity name, so the client's existing
     * artemisApp.exerciseVariantGroup.* translation keys still resolve for their errors.
     */
    private static final String VARIANT_GROUP_ENTITY_NAME = "exerciseVariantGroup";

    private final CourseRepository courseRepository;

    private final MilestoneExerciseGroupRepository milestoneExerciseGroupRepository;

    private final ExerciseVariantGroupService exerciseVariantGroupService;

    private final ProgrammingExerciseValidationService programmingExerciseValidationService;

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    private final ExerciseVersionService exerciseVersionService;

    private final ProgrammingExerciseDeletionService programmingExerciseDeletionService;

    private final UserStoryExerciseService userStoryExerciseService;

    private final ChannelService channelService;

    private final ParticipationService participationService;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ResultRepository resultRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final MilestoneExercisePointsService milestoneExercisePointsService;

    private final CompetencyExerciseLinkService competencyExerciseLinkService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public MilestoneExerciseService(CourseRepository courseRepository, MilestoneExerciseGroupRepository milestoneExerciseGroupRepository,
            ExerciseVariantGroupService exerciseVariantGroupService, ProgrammingExerciseValidationService programmingExerciseValidationService,
            ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService, StaticCodeAnalysisService staticCodeAnalysisService,
            ExerciseVersionService exerciseVersionService, ProgrammingExerciseDeletionService programmingExerciseDeletionService, UserStoryExerciseService userStoryExerciseService,
            ChannelService channelService, ParticipationService participationService, ProgrammingExerciseGradingService programmingExerciseGradingService,
            ResultRepository resultRepository, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            MilestoneExercisePointsService milestoneExercisePointsService, CompetencyExerciseLinkService competencyExerciseLinkService,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.courseRepository = courseRepository;
        this.milestoneExerciseGroupRepository = milestoneExerciseGroupRepository;
        this.exerciseVariantGroupService = exerciseVariantGroupService;
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
        this.exerciseVersionService = exerciseVersionService;
        this.programmingExerciseDeletionService = programmingExerciseDeletionService;
        this.userStoryExerciseService = userStoryExerciseService;
        this.channelService = channelService;
        this.participationService = participationService;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.resultRepository = resultRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.milestoneExercisePointsService = milestoneExercisePointsService;
        this.competencyExerciseLinkService = competencyExerciseLinkService;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Loads all milestone groups of a course, each with its members and anchor milestone exercise.
     *
     * @param courseId the id of the course
     * @return the course's milestone groups
     */
    public List<MilestoneExerciseGroup> findAllByCourseId(Long courseId) {
        return milestoneExerciseGroupRepository.findAllByCourseId(courseId);
    }

    /**
     * Loads one milestone group of a course, with its members and anchor milestone exercise.
     *
     * @param groupId  the id of the milestone group
     * @param courseId the id of the course the group must belong to
     * @return the milestone group
     */
    public MilestoneExerciseGroup findByIdAndCourseIdElseThrow(Long groupId, Long courseId) {
        return milestoneExerciseGroupRepository.findByIdAndCourseIdElseThrow(groupId, courseId);
    }

    /**
     * Creates a milestone group in the course, provisioning its anchor {@link MilestoneExercise} (repositories, build
     * plan, the works — the same pipeline as {@code POST programming-exercises/setup}) and wiring the two together in one
     * call. That matches the "auto-created together with the group" UX: there is no separate "create milestone exercise"
     * endpoint.
     *
     * @param createDTO the settings of the milestone exercise to set up
     * @param courseId  the id of the course that will own the group
     * @return the created group, with its milestone exercise wired
     * @throws IOException                    if the repository setup fails
     * @throws GitAPIException                if the repository setup fails
     * @throws ContinuousIntegrationException if the build plan setup fails
     */
    public MilestoneExerciseGroup createMilestoneGroup(CreateMilestoneExerciseGroupDTO createDTO, Long courseId)
            throws IOException, GitAPIException, ContinuousIntegrationException {
        Course course = courseRepository.findByIdElseThrow(courseId);
        MilestoneExercise milestoneExercise = createDTO.toMilestoneExercise();
        milestoneExercise.setCourse(course);
        // Milestones aren't Athena-assessed - they are never included in an overall score to begin with.
        milestoneExercise.setFeedbackSuggestionModule(null);
        programmingExerciseValidationService.validateNewProgrammingExerciseSettings(milestoneExercise, course);
        PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(milestoneExercise, ENTITY_NAME);

        MilestoneExercise createdMilestoneExercise = (MilestoneExercise) programmingExerciseCreationUpdateService.createProgrammingExercise(milestoneExercise, false);
        if (Boolean.TRUE.equals(createdMilestoneExercise.isStaticCodeAnalysisEnabled())) {
            staticCodeAnalysisService.createDefaultCategories(createdMilestoneExercise);
        }
        exerciseVersionService.createExerciseVersion(createdMilestoneExercise);

        MilestoneExerciseGroup group = new MilestoneExerciseGroup();
        group.setTitle(createdMilestoneExercise.getTitle());
        group.setMilestoneExercise(createdMilestoneExercise);
        // The course owns the unidirectional collection, so save the group first to get an id, then attach it to write
        // the course_id FK. Not transactional (this codebase avoids service-level @Transactional); a failure between the
        // two saves leaves an orphan, course-less group - the milestone exercise itself is already fully created.
        group = milestoneExerciseGroupRepository.save(group);
        Course courseForGroup = courseRepository.findWithEagerExerciseVariantGroupsByIdElseThrow(courseId);
        courseForGroup.addExerciseVariantGroup(group);
        courseRepository.save(courseForGroup);
        return group;
    }

    /**
     * Applies the update to the milestone group and pushes the resulting timeline onto its members. The group's timeline
     * setters delegate to its anchor milestone exercise, which is why the group is loaded with that exercise fetched.
     *
     * @param updateDTO the new settings of the group
     * @param groupId   the id of the milestone group to update
     * @param courseId  the id of the course the group belongs to
     * @return the updated group, with its members still initialized for the response
     */
    public MilestoneExerciseGroup updateMilestoneGroup(UpdateMilestoneExerciseGroupDTO updateDTO, Long groupId, Long courseId) {
        if (!Objects.equals(groupId, updateDTO.id())) {
            throw new BadRequestAlertException("The id in the path and the body must match", ENTITY_NAME, "idMismatch");
        }
        MilestoneExerciseGroup group = findByIdAndCourseIdElseThrow(groupId, courseId);
        updateDTO.applyTo(group);
        group.validateDates();
        // Reused unchanged from the variant groups: a milestone group is still an ExerciseVariantGroup, so its members are
        // kept on the shared timeline the same way. The anchor exercise's own new dates ride along through the group's
        // CascadeType.MERGE.
        exerciseVariantGroupService.saveWithTimelineAppliedToMembers(group);
        return group;
    }

    /**
     * Creates a user story exercise in the given milestone group.
     * <p>
     * Its Language/Version-Control settings, repositories and timeline come from the group's {@link MilestoneExercise} - a
     * user story is never independently configured on any of these, which is why {@link CreateUserStoryExerciseDTO}
     * carries none of them; only its title/short name/problem statement/grading settings are taken from the request.
     *
     * @param createDTO the settings of the user story exercise to create
     * @param groupId   the id of the milestone group that will own the exercise
     * @param courseId  the id of the course the group belongs to
     * @return the created user story exercise
     */
    public UserStoryExercise createUserStoryExercise(CreateUserStoryExerciseDTO createDTO, Long groupId, Long courseId) {
        MilestoneExerciseGroup milestoneGroup = milestoneExerciseGroupRepository.findByIdAndCourseIdWithDetailsElseThrow(groupId, courseId);
        if (milestoneGroup.getMilestoneExercise() == null) {
            throw new BadRequestAlertException("A user story exercise can only be created in a milestone exercise group", VARIANT_GROUP_ENTITY_NAME, "milestoneGroupRequired");
        }
        UserStoryExercise userStoryExercise = createDTO.toUserStoryExercise();
        Course course = courseRepository.findByIdElseThrow(courseId);
        userStoryExercise.setCourse(course);
        userStoryExercise.setExerciseGroup(null);
        userStoryExercise.setExerciseVariantGroup(milestoneGroup);
        userStoryExercise.setFeedbackSuggestionModule(null);
        // A user story's points count through its group, so it stays INCLUDED_COMPLETELY and the field is not offered in
        // the form (USER_STORY_HIDDEN_FIELDS). Double counting is prevented by the score calculation skipping milestone
        // group members - see CourseScoreCalculator.includeIntoScoreCalculation - not by lying about this value.
        userStoryExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        userStoryExerciseService.applyMilestoneConfig(userStoryExercise, milestoneGroup.getMilestoneExercise());
        userStoryExercise.setReleaseDate(milestoneGroup.getReleaseDate());
        userStoryExercise.setStartDate(milestoneGroup.getStartDate());
        userStoryExercise.setDueDate(milestoneGroup.getDueDate());
        userStoryExercise.setAssessmentDueDate(milestoneGroup.getAssessmentDueDate());
        userStoryExercise.setExampleSolutionPublicationDate(milestoneGroup.getExampleSolutionPublicationDate());

        // Needs the course, which the competency's own course is checked against; the links themselves are only persistable
        // once the exercise has an id, so they are taken back off before the save below and restored after it.
        competencyExerciseLinkService.updateCompetencyLinks(createDTO, userStoryExercise);
        var competencyLinks = competencyExerciseLinkService.extractCompetencyLinksForCreation(userStoryExercise);

        programmingExerciseValidationService.validateNewProgrammingExerciseSettings(userStoryExercise, course);
        PlagiarismDetectionConfigHelper.validatePlagiarismDetectionConfigOrThrow(userStoryExercise, VARIANT_GROUP_ENTITY_NAME);

        // applyMilestoneConfig above attaches a fresh, still-transient buildConfig (copied from the milestone exercise)
        // and template/solution participations - none of which cascade PERSIST, so they need their own save dance.
        UserStoryExercise created = programmingExerciseCreationUpdateService.saveNewExerciseWithOwnAssociations(userStoryExercise);
        competencyExerciseLinkService.addCompetencyLinksForCreation(created, competencyLinks);
        if (!created.getCompetencyLinks().isEmpty()) {
            // The links cascade from the exercise, and nothing else on this path saves it again.
            programmingExerciseRepository.save(created);
        }
        // Unlike the generic programming-exercise creation flow (ProgrammingExerciseCreationUpdateService.createProgrammingExercise),
        // saveNewExerciseWithOwnAssociations above is a narrow "persist this exercise plus its own build config/participations"
        // helper and does not create a communication channel - without this, students saw no channel at all under the
        // exercise's Communication tab.
        channelService.createExerciseChannel(created, Optional.ofNullable(createDTO.channelName()));
        // Test cases can only be duplicated onto the new exercise once it has an id (applyMilestoneConfig above
        // skipped this for the same reason); the initial relevance derivation runs against a still-empty problem
        // statement, but re-runs on every later update - see the corresponding TODO on the (not yet implemented)
        // update endpoint.
        userStoryExerciseService.syncTestCasesFromMilestone(created, milestoneGroup.getMilestoneExercise());
        userStoryExerciseService.updateRelevantTestCases(created);
        // The group just became worth more, and the milestone carries that total - both for the students' scores and for
        // the static code analysis budget derived from it. Sync before backfilling, so the results written below are
        // aggregated against the new total rather than the old one.
        milestoneExercisePointsService.syncMaxPoints(milestoneGroup.getMilestoneExercise().getId());
        backfillExistingParticipantsForNewUserStoryExercise(created, milestoneGroup);
        exerciseVersionService.createExerciseVersion(created);
        return created;
    }

    /**
     * Retroactively provisions a participation - and an initial score derived from their latest existing result - for
     * every student who already shares {@code milestoneGroup}'s repository, so they don't have to start {@code created}
     * themselves for it to show up with a correct score. Only the latest already-graded result per student is backfilled
     * (not the full submission history); the score simply won't reflect this new exercise before that point in time.
     *
     * @param created        the newly created user story exercise
     * @param milestoneGroup the group it was created in
     */
    private void backfillExistingParticipantsForNewUserStoryExercise(UserStoryExercise created, MilestoneExerciseGroup milestoneGroup) {
        long milestoneExerciseId = milestoneGroup.getMilestoneExercise().getId();
        for (ProgrammingExerciseStudentParticipation newParticipation : participationService.provisionParticipationsForNewUserStoryExercise(created)) {
            newParticipation.getStudent()
                    .flatMap(student -> programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(milestoneExerciseId, student.getLogin()))
                    .flatMap(milestoneParticipation -> resultRepository.findLatestResultWithFeedbacksForParticipation(milestoneParticipation.getId(), true))
                    .ifPresent(latestMilestoneResult -> programmingExerciseGradingService.fanOutResultToUserStoryExercise(latestMilestoneResult, created, newParticipation));
        }
    }

    /**
     * Reports whether the student has started the group's anchor milestone exercise, along with the milestone's problem
     * statement (which doubles as the group's description in the student group view).
     *
     * @param groupId  the id of the milestone group to check
     * @param courseId the id of the course the group belongs to
     * @param user     the requesting student
     * @return the milestone's id, whether the student has started it, and its problem statement
     */
    public MilestoneStatusDTO getMilestoneStatus(Long groupId, Long courseId, User user) {
        // Deliberately the members-free lookup: this endpoint reads nothing but the anchor exercise, and it is the
        // hottest of the milestone endpoints (every student opening a milestone group hits it).
        // The dedicated repository only resolves milestone groups, so a variant group id is already a 404 here.
        milestoneExerciseGroupRepository.findByIdAndCourseIdWithoutExercisesElseThrow(groupId, courseId);
        long milestoneExerciseId = milestoneExerciseGroupRepository.findMilestoneExerciseIdByGroupId(groupId)
                .orElseThrow(() -> new BadRequestAlertException("The milestone group has no anchor milestone exercise", VARIANT_GROUP_ENTITY_NAME, "milestoneExerciseMissing"));
        // The milestone's problem statement doubles as the group's description in the student group view - the milestone
        // itself is never rendered, so this endpoint is the only path that can hand it to the group view.
        String problemStatement = milestoneExerciseGroupRepository.findMilestoneProblemStatementByGroupId(groupId).orElse(null);
        var participation = programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(milestoneExerciseId, user.getLogin());
        return participation.map(p -> new MilestoneStatusDTO(milestoneExerciseId, true, p.getId(), p.getRepositoryUri(), problemStatement))
                .orElseGet(() -> new MilestoneStatusDTO(milestoneExerciseId, false, null, null, problemStatement));
    }

    /**
     * Deletes the milestone group together with its anchor milestone exercise. Only an empty group can be deleted: unlike
     * an ordinary variant group, a milestone's members share its repositories, so ungrouping them on delete would leave them
     * pointing at a milestone that is about to disappear.
     *
     * @param groupId  the id of the milestone group to delete
     * @param courseId the id of the course the group belongs to
     * @return the deleted group's title, for the deletion alert
     */
    public String deleteMilestoneGroup(Long groupId, Long courseId) {
        // Loaded without its members so the ON DELETE SET NULL FK on exercise.exercise_variant_group_id ungroups them;
        // loading them would fail Hibernate's flush because managed exercises would still reference the removed group.
        MilestoneExerciseGroup group = milestoneExerciseGroupRepository.findByIdAndCourseIdWithoutExercisesElseThrow(groupId, courseId);
        if (milestoneExerciseGroupRepository.countExercisesByGroupId(groupId) > 0) {
            throw new BadRequestAlertException("A milestone exercise group can only be deleted while it has no exercises", ENTITY_NAME, "milestoneGroupNotEmpty");
        }
        Long milestoneExerciseId = milestoneExerciseGroupRepository.findMilestoneExerciseIdByGroupId(groupId).orElse(null);
        // The group row references the milestone exercise through an ON DELETE RESTRICT foreign key, so the group must go
        // first; the association carries no CascadeType.REMOVE / orphanRemoval (see MilestoneExerciseGroup), so deleting
        // the group here does not also delete the exercise. That happens right after, through the deletion service's
        // ordered, VCS/CI-aware cleanup.
        String title = group.getTitle();
        milestoneExerciseGroupRepository.delete(group);
        if (milestoneExerciseId != null) {
            programmingExerciseDeletionService.delete(milestoneExerciseId, true);
        }
        else {
            log.warn("Deleted milestone exercise group {} that had no anchor milestone exercise", groupId);
        }
        return title;
    }
}
