import { Directive, ElementRef, booleanAttribute, inject, input } from '@angular/core';
import { ModelingAssessmentRegion } from 'app/modeling/manage/assess/modeling-assessment-projection';

/** Marks content for the canvas' top-left chrome corner. See {@link ModelingAssessmentRegion}. */
@Directive({ selector: '[modelingAssessmentTopLeft]' })
export class ModelingAssessmentTopLeftDirective implements ModelingAssessmentRegion {
    readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    readonly occupied = input(true, { alias: 'modelingAssessmentTopLeft', transform: booleanAttribute });
}
