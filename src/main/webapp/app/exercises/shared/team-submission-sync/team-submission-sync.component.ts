import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { throttleTime } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';
import { SubmissionSyncPayload, isSubmissionSyncPayload } from 'app/entities/submission-sync-payload.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { Submission } from 'app/entities/submission.model';
import { Observable } from 'rxjs';
import { ExerciseType } from 'app/entities/exercise.model';
import { SubmissionPatch } from 'app/entities/submission-patch.model';
import { SubmissionPatchPayload, isSubmissionPatchPayload } from 'app/entities/submission-patch-payload.model';

@Component({
    selector: 'jhi-team-submission-sync',
    template: '',
})
export class TeamSubmissionSyncComponent implements OnInit {
    private accountService = inject(AccountService);
    private teamSubmissionWebsocketService = inject(JhiWebsocketService);
    private alertService = inject(AlertService);

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
        this.teamSubmissionWebsocketService.subscribe(this.websocketTopic);
        this.setupReceiver();
        this.setupSender();
    }

    /**
     * Receives updated submissions or submission patches from other team members and emits them
     */
    private setupReceiver() {
        this.teamSubmissionWebsocketService.receive(this.websocketTopic).subscribe({
            next: (payload: SubmissionSyncPayload | SubmissionPatchPayload) => {
                if (isSubmissionSyncPayload(payload) && !this.isSelf(payload.sender)) {
                    this.receiveSubmission.emit(payload.submission);
                } else if (isSubmissionPatchPayload(payload)) {
                    this.receiveSubmissionPatch.emit(payload.submissionPatch);
                }
            },
            error: (error) => this.onError(error),
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
                this.teamSubmissionWebsocketService.send(this.buildWebsocketTopic('/update'), submission);
            },
            error: (error) => this.onError(error),
        });

        this.submissionPatchObservable?.subscribe({
            next: (submissionPatch: SubmissionPatch) => {
                this.teamSubmissionWebsocketService.send(this.buildWebsocketTopic('/patch'), submissionPatch);
            },
            error: (error) => this.onError(error),
        });
    }

    private isSelf(user: User) {
        return this.currentUser.login === user.login;
    }

    private buildWebsocketTopic(path = ''): string {
        return `/topic/participations/${this.participation.id}/team/${this.exerciseType}-submissions${path}`;
    }

    private onError(error: string) {
        this.alertService.error(error);
    }
}
