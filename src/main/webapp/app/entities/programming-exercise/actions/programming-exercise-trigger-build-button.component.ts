import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { tap } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise/services';
import { ProgrammingSubmissionWebsocketService } from 'app/submission/programming-submission-websocket.service';
import { hasParticipationChanged } from 'app/entities/participation';

@Component({
    selector: 'jhi-programming-exercise-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
})
export class ProgrammingExerciseTriggerBuildButtonComponent implements OnChanges {
    @Input() participationId: number;

    isBuilding: boolean;

    private submissionSubscription: Subscription;

    constructor(private participationService: ProgrammingExerciseParticipationService, private submissionService: ProgrammingSubmissionWebsocketService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (this.participationId && this.participationId !== changes.participationId.previousValue) {
            this.setupSubmissionSubscription();
        }
    }

    setupSubmissionSubscription() {
        if (this.submissionSubscription) {
            this.submissionSubscription.unsubscribe();
        }

        this.submissionSubscription = this.submissionService
            .getLatestPendingSubmission(this.participationId)
            .pipe(tap(submission => (this.isBuilding = !!submission)))
            .subscribe();
    }

    triggerBuild() {
        this.participationService.triggerBuild(this.participationId).subscribe();
    }
}
