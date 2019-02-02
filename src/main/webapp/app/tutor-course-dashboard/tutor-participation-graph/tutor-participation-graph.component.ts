import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/tutor-participation';
import {Router} from '@angular/router';

@Component({
    selector: 'jhi-tutor-participation-graph',
    templateUrl: './tutor-participation-graph.component.html',
    styles: []
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
        if (this.tutorParticipation.assessedExercise && this.tutorParticipation.assessedExercise.course) {
            this.routerLink = `/course/${this.tutorParticipation.assessedExercise.course.id}/exercise/${this.tutorParticipation.assessedExercise.id}/tutor-dashboard`;
        }
    }

    navigate() {
        this.router.navigate([this.routerLink]);
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

       if (step === this.TRAINED && this.tutorParticipation.trainedExampleSubmissions.length > 0) {
           return 'orange';
       }
    }
}
