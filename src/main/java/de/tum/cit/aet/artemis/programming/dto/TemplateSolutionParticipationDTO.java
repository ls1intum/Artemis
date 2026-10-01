package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.programming.domain.AbstractBaseProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;

/**
 * The template and solution participation of a programming exercise. Both slots have an identical shape today; only
 * the {@code type} discriminator differs ({@code "template"} vs {@code "solution"}), which the client switches on.
 * <p>
 * Readers: the detail page and the course exercise list read {@code id}, {@code buildPlanId} and
 * {@code submissions[*].results}; the trigger-build button gates on {@code initializationState}; the code-editor
 * container reads {@code id} and {@code repositoryUri}; the repository view reads {@code repositoryUri};
 * participation-submission reads {@code id} and {@code submissions[*].results[*].id}.
 *
 * @param id                  the participation id
 * @param type                the constant discriminator, {@link #TYPE_TEMPLATE} or {@link #TYPE_SOLUTION}
 * @param repositoryUri       the URI of the participation's repository
 * @param buildPlanId         the id of the participation's build plan
 * @param initializationState the participation's lifecycle state; the client only offers manual build triggers on
 *                                INITIALIZED, INACTIVE or FINISHED participations
 * @param submissions         the participation's submissions with their results; {@code null} when not loaded
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TemplateSolutionParticipationDTO(Long id, String type, String repositoryUri, String buildPlanId, InitializationState initializationState,
        List<ProgrammingSubmissionWithResultsDTO> submissions) implements Serializable {

    /**
     * The constant Jackson subtype id of {@link TemplateProgrammingExerciseParticipation}.
     */
    public static final String TYPE_TEMPLATE = "template";

    /**
     * The constant Jackson subtype id of {@link SolutionProgrammingExerciseParticipation}.
     */
    public static final String TYPE_SOLUTION = "solution";

    /**
     * Converts a template participation, emitting {@code type = "template"}.
     *
     * @param participation the template participation (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static TemplateSolutionParticipationDTO ofTemplate(TemplateProgrammingExerciseParticipation participation) {
        return of(participation, TYPE_TEMPLATE);
    }

    /**
     * Converts a solution participation, emitting {@code type = "solution"}.
     *
     * @param participation the solution participation (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static TemplateSolutionParticipationDTO ofSolution(SolutionProgrammingExerciseParticipation participation) {
        return of(participation, TYPE_SOLUTION);
    }

    /**
     * Returns the participation as an export carries it: no row id, which an import would copy onto the participation
     * it creates, and no submissions, which are build runs of this instance that nothing on the receiving side reads
     * ({@code ImportProgrammingExerciseRequestDTO} binds id, repository URI and build plan id only). The repository
     * URI stays: the import from file reads it to rewrite legacy project names.
     *
     * @return a copy of this DTO without the id and without the submissions
     */
    public TemplateSolutionParticipationDTO forExport() {
        return new TemplateSolutionParticipationDTO(null, type, repositoryUri, buildPlanId, initializationState, null);
    }

    private static TemplateSolutionParticipationDTO of(AbstractBaseProgrammingExerciseParticipation participation, String type) {
        if (participation == null || !Hibernate.isInitialized(participation)) {
            return null;
        }
        return new TemplateSolutionParticipationDTO(participation.getId(), type, participation.getRepositoryUri(), participation.getBuildPlanId(),
                participation.getInitializationState(), mapSubmissions(participation));
    }

    /**
     * Maps the participation's submissions when they are loaded. Never triggers a lazy load and never mutates the
     * loaded collection.
     */
    private static List<ProgrammingSubmissionWithResultsDTO> mapSubmissions(Participation participation) {
        if (participation.getSubmissions() == null || !Hibernate.isInitialized(participation.getSubmissions())) {
            return null;
        }
        return participation.getSubmissions().stream().filter(ProgrammingSubmission.class::isInstance).map(ProgrammingSubmission.class::cast)
                .map(ProgrammingSubmissionWithResultsDTO::of).toList();
    }
}
