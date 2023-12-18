import { Component, Input } from '@angular/core';
import { Competency } from 'app/entities/competency.model';
@Component({
    selector: 'jhi-competency-recommendation',
    templateUrl: './competency-recommendation-detail.component.html',
})
export class CompetencyRecommendationDetailComponent {
    @Input({ required: true }) recommendation: Competency;

    constructor() {}
}
