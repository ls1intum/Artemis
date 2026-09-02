import { ElementRef, Signal } from '@angular/core';

/**
 * Contract shared by every directive that addresses one of the assessment canvas' chrome regions.
 *
 * `getRegionElement` registers its host as a control with `inset: "auto"`, so a region that mounts
 * while empty still reserves canvas room that `fitView({ respectInsets: true })` frames around.
 * {@link occupied} is what the host acquires and releases the region against.
 *
 * Hosts must project the content unconditionally and toggle {@link occupied}, never wrap it in
 * `@if`: a control-flow block only reaches a named `ng-content` slot when the compiler can infer
 * the slot from the block's single root node, and otherwise falls back to a default slot this
 * component does not have — the directive still instantiates, so the region mounts around nodes
 * that were never attached.
 */
export interface ModelingAssessmentRegion {
    readonly elementRef: ElementRef<HTMLElement>;
    /** Bind it (`[modelingAssessmentTopLeft]="hasNotice()"`) for content that comes and goes; a bare attribute means "always shown". */
    readonly occupied: Signal<boolean>;
}

export function isOccupied(region: ModelingAssessmentRegion | undefined): boolean {
    return !!region?.occupied();
}
