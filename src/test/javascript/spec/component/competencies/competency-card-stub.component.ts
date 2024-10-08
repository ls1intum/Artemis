import { Component, input } from '@angular/core';
import { CourseCompetency } from 'app/entities/competency.model';

@Component({ selector: 'jhi-competency-card', template: '<div><ng-content /></div>' })
export class CompetencyCardStubComponent {
    courseId = input<number | undefined>();
    competency = input<CourseCompetency>();
    isPrerequisite = input<boolean>();
    hideProgress = input<boolean>(false);
    noProgressRings = input<boolean>(false);
}
