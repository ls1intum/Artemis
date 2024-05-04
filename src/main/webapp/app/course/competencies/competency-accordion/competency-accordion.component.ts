import { Component, Input } from '@angular/core';
import { Competency, getConfidence, getIcon, getMastery, getProgress } from 'app/entities/competency.model';

@Component({
    selector: 'jhi-competency-accordion',
    templateUrl: './competency-accordion.component.html',
    styleUrl: './competency-accordion.component.scss',
})
export class CompetencyAccordionComponent {
    @Input()
    courseId?: number;
    @Input()
    competency: Competency;

    open = false;
    rated = false;
    ratingAvailable = false;

    getIcon = getIcon;
    getProgress = getProgress;
    getConfidence = getConfidence;
    getMastery = getMastery;

    constructor() {}

    toggle() {
        this.open = !this.open;
    }
}
