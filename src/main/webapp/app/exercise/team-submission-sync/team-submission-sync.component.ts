import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { throttleTime } from 'rxjs/operators';
import { AlertService } from 'app/shared/service/alert.service';
import { SubmissionSyncPayload, isSubmissionSyncPayload } from 'app/exercise/shared/entities/submission/submission-sync-payload.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Observable, Subscription } from 'rxjs';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SubmissionPatch } from 'app/exercise/shared/entities/submission/submission-patch.model';
import { SubmissionPatchPayload, isSubmissionPatchPayload } from 'app/exercise/shared/entities/submission/submission-patch-payload.model';

@Component({
    selector: 'jhi-team-submission-sync',
    template: '',
})
export class TeamSubmissionSyncComponent implements OnInit, OnDestroy {
    private accountService = inject(AccountService);
    private teamSubmissionWebsocketService = inject(WebsocketService);
    private alertService = inject(AlertService);
    private websocketSubscription?: Subscription;

    // Sync settings
    readonly THROTTLE_TIME = 2000; // ms

    @Input() exerciseType: ExerciseType;
    @Input() submissionObservable?: Observable<Submission>;
    @Input() submissionPatchObservable?: Observable<SubmissionPatch>;
    @Input() participation: StudentParticipation;

    @Output() receiveSubmission = new EventEmitter<Submission>();
    @Output() receiveSubmissionPatch = new EventEmitter<SubmissionPatch>();

    currentUser: User;
    websocketTopic: string;

    constructor() {
        this.accountService.identity().then((user: User) => (this.currentUser = user));
    }

    /**
     * Life cycle hook to indicate component creation is done
     */
    ngOnInit(): void {
        this.websocketTopic = this.buildWebsocketTopic('');
        this.setupReceiver();
        this.setupSender();
    }

    ngOnDestroy(): void {
        this.websocketSubscription?.unsubscribe();
    }

    /**
     * Receives updated submissions or submission patches from other team members and emits them
     */
    private setupReceiver() {
        this.websocketSubscription = this.teamSubmissionWebsocketService.subscribe<SubmissionSyncPayload | SubmissionPatchPayload>(this.websocketTopic).subscribe({
            next: (payload: SubmissionSyncPayload | SubmissionPatchPayload) => {
                if (isSubmissionSyncPayload(payload) && !this.isSelf(payload.sender)) {
                    this.receiveSubmission.emit(payload.submission);
                } else if (isSubmissionPatchPayload(payload)) {
                    this.receiveSubmissionPatch.emit(payload.submissionPatch);
                }
            },
            error: (error: unknown) => this.onError(error),
        });
    }

    /**
     * Subscribes to the submission and submission patch streams and sends out
     * updated submissions or submission patches based on those own changes via websockets
     */
    private setupSender() {
        this.submissionObservable?.pipe(throttleTime(this.THROTTLE_TIME, undefined, { leading: true, trailing: true })).subscribe({
            next: (submission: Submission) => {
                if (submission.participation) {
                    submission.participation.exercise = undefined;
                    submission.participation.submissions = [];
                }
                this.teamSubmissionWebsocketService.send<Submission>(this.buildWebsocketTopic('/update'), submission);
            },
            error: (error: unknown) => this.onError(error),
        });

        this.submissionPatchObservable?.subscribe({
            next: (submissionPatch: SubmissionPatch) => {
                this.teamSubmissionWebsocketService.send<SubmissionPatch>(this.buildWebsocketTopic('/patch'), submissionPatch);
            },
            error: (error: unknown) => this.onError(error),
        });
    }

    private isSelf(user: User) {
        return this.currentUser.login === user.login;
    }

    private buildWebsocketTopic(path = ''): string {
        return `/topic/participations/${this.participation.id}/team/${this.exerciseType}-submissions${path}`;
    }

    private onError(error: unknown) {
        const message = error instanceof Error ? error.message : String(error);
        this.alertService.error(message);
    }
}
