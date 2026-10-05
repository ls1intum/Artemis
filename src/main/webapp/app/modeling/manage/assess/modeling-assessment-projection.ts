import { ElementRef, Signal } from '@angular/core';

/** Keeps an empty projected region from reserving Apollon canvas space. */
export interface ModelingAssessmentRegion {
    readonly elementRef: ElementRef<HTMLElement>;
    readonly occupied: Signal<boolean>;
}

export function isOccupied(region: ModelingAssessmentRegion | undefined): boolean {
    return !!region?.occupied();
}
