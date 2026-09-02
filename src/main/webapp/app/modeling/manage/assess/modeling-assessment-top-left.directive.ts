import { Directive, ElementRef, booleanAttribute, inject, input } from '@angular/core';
import { ModelingAssessmentRegion } from 'app/modeling/manage/assess/modeling-assessment-projection';

@Directive({ selector: '[modelingAssessmentTopLeft]' })
export class ModelingAssessmentTopLeftDirective implements ModelingAssessmentRegion {
    readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    readonly occupied = input(true, { alias: 'modelingAssessmentTopLeft', transform: booleanAttribute });
}
