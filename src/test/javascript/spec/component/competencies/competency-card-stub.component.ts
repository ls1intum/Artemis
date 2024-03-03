import { Component, Input } from '@angular/core';
import { Competency } from 'app/entities/competency.model';

@Component({ selector: 'jhi-competency-card', template: '<div><ng-content /></div>' })
export class CompetencyCardStubComponent {
    @Input() courseId?: number;
    @Input() competency: Competency;
    @Input() isPrerequisite: boolean;
    @Input() displayOnly: boolean;
}
