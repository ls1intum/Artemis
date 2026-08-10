import { Directive, ElementRef, booleanAttribute, inject, input } from '@angular/core';
import { ModelingAssessmentRegion } from 'app/modeling/manage/assess/modeling-assessment-projection';

/**
 * Marks content for the editor's side panel — a disclosure that opens beside the
 * canvas, inside the editor's own chrome, so it travels into fullscreen with it.
 * See {@link ModelingAssessmentRegion}.
 */
@Directive({ selector: '[modelingAssessmentPanel]' })
export class ModelingAssessmentPanelDirective implements ModelingAssessmentRegion {
    readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    readonly occupied = input(true, { alias: 'modelingAssessmentPanel', transform: booleanAttribute });
}
