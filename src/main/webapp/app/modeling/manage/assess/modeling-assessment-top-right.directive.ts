import { Directive, ElementRef, booleanAttribute, inject, input } from '@angular/core';
import { ModelingAssessmentRegion } from 'app/modeling/manage/assess/modeling-assessment-projection';

/** Marks content for the canvas' top-right chrome corner. See {@link ModelingAssessmentRegion}. */
@Directive({ selector: '[modelingAssessmentTopRight]' })
export class ModelingAssessmentTopRightDirective implements ModelingAssessmentRegion {
    readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    readonly occupied = input(true, { alias: 'modelingAssessmentTopRight', transform: booleanAttribute });
}
