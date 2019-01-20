import { Component, Input, OnInit } from '@angular/core';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/tutor-participation';

@Component({
    selector: 'jhi-tutor-participation-graph',
    templateUrl: './tutor-participation-graph.component.html',
    styles: []
})
export class TutorParticipationGraphComponent implements OnInit {
    @Input() public tutorParticipation: TutorParticipation;

    tutorParticipationStatus: TutorParticipationStatus = TutorParticipationStatus.NOT_PARTICIPATED;
    NOT_PARTICIPATED = TutorParticipationStatus.NOT_PARTICIPATED;
    REVIEWED_INSTRUCTIONS = TutorParticipationStatus.REVIEWED_INSTRUCTIONS;
    TRAINED = TutorParticipationStatus.TRAINED;
    COMPLETED = TutorParticipationStatus.COMPLETED;

    constructor() {}

    ngOnInit() {
        this.tutorParticipationStatus = this.tutorParticipation.status;
    }
}
