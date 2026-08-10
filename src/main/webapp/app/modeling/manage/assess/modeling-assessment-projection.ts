import { ElementRef, Signal } from '@angular/core';

/**
 * The contract every directive shares that addresses one of the assessment
 * canvas' chrome regions.
 *
 * Two things about it are deliberate.
 *
 * **The content must be projected unconditionally.** Wrapping it in `@if` looks
 * equivalent, but a control-flow block only reaches a named `ng-content` slot
 * when the compiler can infer that slot from the block's single root node. When
 * it cannot, the block falls back to the default slot — and this component has
 * none, so the view is still created (the directive instantiates, content
 * queries resolve) while its nodes are never attached to the DOM. The symptom is
 * a mounted-but-empty region: the worst of both outcomes. Host pages therefore
 * keep the element in the template and toggle {@link occupied} instead.
 *
 * **An unoccupied region must not mount.** `getRegionElement` registers the host
 * as a control with `inset: "auto"`, so even an empty region reserves canvas
 * room that `fitView({ respectInsets: true })` frames around. Occupancy is the
 * host's answer to "is there anything in here?", and the region is acquired and
 * released to match.
 */
export interface ModelingAssessmentRegion {
    readonly elementRef: ElementRef<HTMLElement>;
    /**
     * Whether the slot currently holds something worth reserving space for.
     * Bind it (`[modelingAssessmentTopLeft]="hasNotice()"`) for content that
     * comes and goes; a bare attribute keeps meaning "always shown".
     */
    readonly occupied: Signal<boolean>;
}

/** An absent slot and an unoccupied one are the same thing to the region. */
export function isOccupied(region: ModelingAssessmentRegion | undefined): boolean {
    return !!region?.occupied();
}
