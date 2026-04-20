package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single action executed by the Atlas orchestrator during a run. The orchestrator appends one
 * entry per successful tool call so the REST response (and ultimately the UI) can show the
 * instructor exactly what was created, edited, assigned, unassigned, or deleted.
 * <p>
 * {@code weight} is populated only for {@link ActionType#ASSIGN} and records the strength of the
 * link as chosen by the orchestrator (1.0 primary, 0.5 partial, 0.3 incidental).
 * {@code justification} is the one-sentence reason the orchestrator commits to alongside the
 * mutation and is shown in the result dialog.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppliedActionDTO(ActionType type, Long competencyId, String competencyTitle, Long exerciseId, Double weight, String detail, String justification) {

    public enum ActionType {
        CREATE, EDIT, ASSIGN, UNASSIGN, DELETE
    }

    public static AppliedActionDTO create(Long competencyId, String competencyTitle, String detail, String justification) {
        return new AppliedActionDTO(ActionType.CREATE, competencyId, competencyTitle, null, null, detail, justification);
    }

    public static AppliedActionDTO edit(Long competencyId, String competencyTitle, String detail, String justification) {
        return new AppliedActionDTO(ActionType.EDIT, competencyId, competencyTitle, null, null, detail, justification);
    }

    public static AppliedActionDTO assign(Long competencyId, String competencyTitle, Long exerciseId, Double weight, String detail, String justification) {
        return new AppliedActionDTO(ActionType.ASSIGN, competencyId, competencyTitle, exerciseId, weight, detail, justification);
    }

    public static AppliedActionDTO unassign(Long competencyId, String competencyTitle, Long exerciseId, String detail, String justification) {
        return new AppliedActionDTO(ActionType.UNASSIGN, competencyId, competencyTitle, exerciseId, null, detail, justification);
    }

    public static AppliedActionDTO delete(Long competencyId, String competencyTitle, String detail, String justification) {
        return new AppliedActionDTO(ActionType.DELETE, competencyId, competencyTitle, null, null, detail, justification);
    }
}
