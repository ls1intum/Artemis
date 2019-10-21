import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/tutor-participation';
import { Router } from '@angular/router';
import * as _ from 'lodash';

@Component({
    selector: 'jhi-tutor-participation-graph',
    templateUrl: './tutor-participation-graph.component.html',
    styleUrls: ['./tutor-participation-graph.component.scss'],
})
export class TutorParticipationGraphComponent implements OnInit, OnChanges {
    @Input() public tutorParticipation: TutorParticipation;
    @Input() public numberOfParticipations: number;
    @Input() public numberOfAssessments: number;

    tutorParticipationStatus: TutorParticipationStatus = TutorParticipationStatus.NOT_PARTICIPATED;
    NOT_PARTICIPATED = TutorParticipationStatus.NOT_PARTICIPATED;
    REVIEWED_INSTRUCTIONS = TutorParticipationStatus.REVIEWED_INSTRUCTIONS;
    TRAINED = TutorParticipationStatus.TRAINED;
    COMPLETED = TutorParticipationStatus.COMPLETED;

    percentageAssessmentProgress = 0;

    routerLink: string;

    constructor(private router: Router) {}

    ngOnInit() {
        this.tutorParticipationStatus = this.tutorParticipation.status;
        const exerciseId = _.get(this.tutorParticipation, 'trainedExampleSubmissions[0].exercise.id');
        const courseId = _.get(this.tutorParticipation, 'trainedExampleSubmissions[0].exercise.course.id');

        if (courseId && exerciseId) {
            this.routerLink = `/course/${courseId}/exercise/${exerciseId}/tutor-dashboard`;
        }

        if (this.numberOfParticipations !== 0) {
            this.percentageAssessmentProgress = Math.round((this.numberOfAssessments / this.numberOfParticipations) * 100);
        }
    }

    navigate() {
        if (this.routerLink && this.routerLink.length > 0) {
            this.router.navigate([this.routerLink]);
        }
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.tutorParticipation) {
            this.tutorParticipation = changes.tutorParticipation.currentValue;
            this.tutorParticipationStatus = this.tutorParticipation.status;
        }

        if (this.numberOfParticipations !== 0) {
            this.percentageAssessmentProgress = Math.round((this.numberOfAssessments / this.numberOfParticipations) * 100);
        }
    }

    calculateClasses(step: TutorParticipationStatus): string {
        if (step === this.tutorParticipationStatus && step !== this.TRAINED) {
            return 'active';
        }

        if (step === this.COMPLETED && this.tutorParticipationStatus !== this.TRAINED) {
            return 'opaque';
        }

        if (step === this.TRAINED && ![this.REVIEWED_INSTRUCTIONS, this.TRAINED, this.COMPLETED].includes(this.tutorParticipationStatus)) {
            return 'opaque';
        }

        if (step === this.TRAINED && this.tutorParticipation.trainedExampleSubmissions && this.tutorParticipation.trainedExampleSubmissions.length > 0) {
            return 'orange';
        }

        return '';
    }

    calculateProgressBarClass(): string {
        const percentage = this.percentageAssessmentProgress;

        if (percentage < 50) {
            return 'bg-danger';
        } else if (percentage < 100) {
            return 'bg-warning';
        }

        return 'bg-success';
    }

    chooseProgressBarTextColor() {
        if (this.percentageAssessmentProgress < 100) {
            return 'text-dark';
        }

        return 'text-white';
    }

    calculateClassProgressBar() {
        if (this.tutorParticipationStatus !== this.TRAINED && this.tutorParticipationStatus !== this.COMPLETED) {
            return 'opaque';
        }

        if (this.tutorParticipationStatus === this.COMPLETED || this.numberOfParticipations === this.numberOfAssessments) {
            return 'active';
        }

        return 'orange';
    }
}
