import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/tutor-participation';
import { Router } from '@angular/router';
import * as _ from 'lodash';

@Component({
    selector: 'jhi-tutor-participation-graph',
    templateUrl: './tutor-participation-graph.component.html',
    styleUrls: ['./tutor-participation-graph.component.scss']
})
export class TutorParticipationGraphComponent implements OnInit, OnChanges {
    @Input() public tutorParticipation: TutorParticipation;

    tutorParticipationStatus: TutorParticipationStatus = TutorParticipationStatus.NOT_PARTICIPATED;
    NOT_PARTICIPATED = TutorParticipationStatus.NOT_PARTICIPATED;
    REVIEWED_INSTRUCTIONS = TutorParticipationStatus.REVIEWED_INSTRUCTIONS;
    TRAINED = TutorParticipationStatus.TRAINED;
    COMPLETED = TutorParticipationStatus.COMPLETED;

    routerLink: string;

    constructor(private router: Router) {}

    ngOnInit() {
        this.tutorParticipationStatus = this.tutorParticipation.status;
        const exerciseId = _.get(this.tutorParticipation, 'trainedExampleSubmissions[0].exercise.id');
        const courseId = _.get(this.tutorParticipation, 'trainedExampleSubmissions[0].exercise.course.id');

        if (courseId && exerciseId) {
            this.routerLink = `/course/${courseId}/exercise/${exerciseId}/tutor-dashboard`;
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
    }

    calculateClasses(step: TutorParticipationStatus): string {
       if (step === this.tutorParticipationStatus) {
           return 'active';
       }

       if (step === this.COMPLETED && this.tutorParticipationStatus !== this.TRAINED) {
           return 'opaque';
       }

       if (step === this.TRAINED && ![this.REVIEWED_INSTRUCTIONS, this.COMPLETED].includes(this.tutorParticipationStatus)) {
           return 'opaque';
       }

       if (step === this.TRAINED && this.tutorParticipation.trainedExampleSubmissions && this.tutorParticipation.trainedExampleSubmissions.length > 0) {
           return 'orange';
       }
    }
}
