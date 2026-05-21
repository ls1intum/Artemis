package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyRelationApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyRepositoryApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;

/**
 * Service for managing competency-exercise links during exercise creation, update, and import.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class CompetencyExerciseLinkService {

    private final Optional<CompetencyRepositoryApi> competencyRepositoryApi;

    private final Optional<CompetencyRelationApi> competencyRelationApi;

    public CompetencyExerciseLinkService(Optional<CompetencyRepositoryApi> competencyRepositoryApi, Optional<CompetencyRelationApi> competencyRelationApi) {
        this.competencyRepositoryApi = competencyRepositoryApi;
        this.competencyRelationApi = competencyRelationApi;
    }

    /**
     * Saves the given competency exercise links directly via the API layer,
     * without re-saving the parent exercise entity.
     *
     * @param links the competency exercise links to save
     */
    public void saveAll(Collection<CompetencyExerciseLink> links) {
        competencyRelationApi.ifPresent(api -> api.saveAllExerciseLinks(links));
    }

    /**
     * Updates the competency links of an exercise based on the DTO values.
     * Reuses existing managed links where possible, creates new ones for new competencies,
     * and removes links that are no longer present.
     *
     * @param dto    the DTO containing the new competency link state
     * @param entity the exercise entity to update
     */
    public void updateCompetencyLinks(CompetencyLinksHolderDTO dto, Exercise entity) {
        if (competencyRepositoryApi.isEmpty()) {
            return;
        }
        if (dto.competencyLinks() == null) {
            // null means "not provided" — do not change existing links (PATCH semantics)
            return;
        }
        if (dto.competencyLinks().isEmpty()) {
            // empty set means "remove all" — clear existing links
            entity.getCompetencyLinks().clear();
        }
        else {
            final var existingLinksByCompetencyId = entity.getCompetencyLinks().stream().collect(Collectors.toMap(link -> link.getCompetency().getId(), Function.identity()));

            Set<CompetencyExerciseLink> updatedLinks = new HashSet<>();

            for (var dtoLink : dto.competencyLinks()) {
                if (dtoLink == null || dtoLink.competency() == null) {
                    throw new BadRequestAlertException("Competency link and its competency must not be null", "exercise", "competencyLinkNull");
                }
                long competencyId = dtoLink.competency().id();
                double weight = dtoLink.weight();

                var existingLink = existingLinksByCompetencyId.get(competencyId);
                if (existingLink != null) {
                    existingLink.setWeight(weight);
                    updatedLinks.add(existingLink);
                }
                else {
                    var competency = competencyRepositoryApi.get().findCompetencyOrPrerequisiteByIdElseThrow(competencyId);
                    // Validate that the competency belongs to the same course as the exercise
                    Long exerciseCourseId = entity.getCourseViaExerciseGroupOrCourseMember() != null ? entity.getCourseViaExerciseGroupOrCourseMember().getId() : null;
                    Long competencyCourseId = competency.getCourse() != null ? competency.getCourse().getId() : null;
                    if (exerciseCourseId != null && competencyCourseId != null && !Objects.equals(exerciseCourseId, competencyCourseId)) {
                        throw new BadRequestAlertException("The competency does not belong to the exercise's course.", "exercise", "wrongCourse");
                    }
                    var newLink = new CompetencyExerciseLink(competency, entity, weight);
                    updatedLinks.add(newLink);
                }
            }

            entity.getCompetencyLinks().clear();
            entity.getCompetencyLinks().addAll(updatedLinks);
        }
    }

    /**
     * Extracts competency links from a new exercise before its first save.
     * The links are cleared from the exercise so it can be saved without them
     * (since they need the exercise ID which doesn't exist yet).
     *
     * @param exercise the new exercise (not yet saved) from which to extract competency links
     * @return the extracted competency links (may be empty)
     */
    public Set<CompetencyExerciseLink> extractCompetencyLinksForCreation(Exercise exercise) {
        Set<CompetencyExerciseLink> competencyLinks = new HashSet<>(exercise.getCompetencyLinks());
        exercise.getCompetencyLinks().clear();
        return competencyLinks;
    }

    /**
     * Restores competency links to a saved exercise and persists them.
     * <p>
     * This method must be called AFTER the exercise has been saved and has an ID.
     * It sets the proper exercise reference on each link (required for @MapsId) and
     * adds them to the exercise. The caller must save the exercise again after this call
     * to persist the links via cascade.
     *
     * @param exercise        the saved exercise (must have an ID)
     * @param competencyLinks the links previously extracted via extractCompetencyLinksForCreation
     */
    public void addCompetencyLinksForCreation(Exercise exercise, Set<CompetencyExerciseLink> competencyLinks) {
        if (competencyLinks == null || competencyLinks.isEmpty()) {
            return;
        }
        if (competencyRepositoryApi.isEmpty()) {
            return;
        }
        // Load each competency individually as a managed entity. Using findAllById on the
        // abstract CourseCompetency (SINGLE_TABLE inheritance) can return empty results
        // under Hibernate 6.6+ due to polymorphic query issues.
        Set<CompetencyExerciseLink> resolvedLinks = new HashSet<>();
        Long exerciseCourseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        for (CompetencyExerciseLink link : competencyLinks) {
            long competencyId = link.getCompetency().getId();
            CourseCompetency managedCompetency = competencyRepositoryApi.get().findCompetencyOrPrerequisiteByIdElseThrow(competencyId);
            // Skip competencies that belong to a different course (can happen during cross-course import)
            Long competencyCourseId = managedCompetency.getCourse() != null ? managedCompetency.getCourse().getId() : null;
            if (exerciseCourseId != null && competencyCourseId != null && !Objects.equals(exerciseCourseId, competencyCourseId)) {
                continue;
            }
            resolvedLinks.add(new CompetencyExerciseLink(managedCompetency, exercise, link.getWeight()));
        }
        exercise.setCompetencyLinks(resolvedLinks);
    }
}
