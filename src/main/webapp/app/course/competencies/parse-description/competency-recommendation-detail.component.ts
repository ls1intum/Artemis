import { Component, Input } from '@angular/core';
import { Competency } from 'app/entities/competency.model';
import { faChevronRight, faEdit, faTrash } from '@fortawesome/free-solid-svg-icons';
@Component({
    selector: 'jhi-competency-recommendation',
    templateUrl: './competency-recommendation-detail.component.html',
    styleUrls: ['competency-recommendation-detail.component.scss'],
})
export class CompetencyRecommendationDetailComponent {
    @Input({ required: true }) recommendation: Competency;
    @Input() isCollapsed: boolean = true;

    //Icons
    faChevronRight = faChevronRight;
    faTrash = faTrash;
    faEdit = faEdit;

    toggle() {
        this.isCollapsed = !this.isCollapsed;
    }
    constructor() {}
}
