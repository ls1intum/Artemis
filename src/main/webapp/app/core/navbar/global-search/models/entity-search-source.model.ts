/**
 * One entity source used by the Iris global-search answer: course information (an exercise, exam,
 * channel, FAQ, lecture or course entry) the answer drew on, alongside the lecture-content sources.
 * The `snippet` carries the entity card Pyris rendered and reranked.
 */
export interface EntitySearchSource {
    entityType: string;
    entityId?: number;
    course?: { id: number; name: string };
    title?: string;
    snippet?: string;
    /** Artemis-relative deep link (may contain a query string, so it is opened via `navigateByUrl`). */
    link?: string;
    /** The exercise type (exercises only), used to pick the same icon as the palette. */
    exerciseType?: string;
}
