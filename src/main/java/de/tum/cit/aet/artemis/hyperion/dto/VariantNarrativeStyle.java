package de.tum.cit.aet.artemis.hyperion.dto;

/**
 * Narrative strength ("storytelling") of a generated exercise variant — a scale from plain technical wording to
 * fully creative storytelling. Absent (null) on the request means "stay consistent with the source exercise".
 *
 * When a narrative beyond {@link #TECHNICAL} is requested but neither the source exercise nor the request
 * provides a domain to build on, the planner defaults the story's theme to Greek mythology — a nod to the
 * research group's services (Artemis, Athena, Iris, Hyperion, ...).
 */
public enum VariantNarrativeStyle {

    /** No story: plain, concise focus on the technical concepts. */
    TECHNICAL,

    /** A realistic narrative frame: a short real-world scenario introduces the task, the rest stays technical. */
    REALISTIC,

    /** A creative story carries the task: themed setting, named actors, story-driven examples. */
    CREATIVE,

    /** Fully imaginative storytelling: the entire exercise is told inside a rich narrative world. */
    IMAGINATIVE
}
