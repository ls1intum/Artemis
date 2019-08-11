import { Component, Input } from '@angular/core';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services';
import { ProgrammingSubmissionWebsocketService } from 'app/submission/programming-submission-websocket.service';

@Component({
    selector: 'jhi-programming-exercise-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
})
export class ProgrammingExerciseTriggerBuildButtonComponent {
    @Input() participationId: number;

    constructor(private participationService: ProgrammingExerciseParticipationService, private submissionService: ProgrammingSubmissionWebsocketService) {}

    triggerBuild() {
        this.participationService.triggerBuild(this.participationId).subscribe();
    }
}
