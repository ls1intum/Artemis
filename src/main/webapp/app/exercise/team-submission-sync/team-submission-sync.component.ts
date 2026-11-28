import { Component, OnDestroy, OnInit, inject, input, output } from '@angular/core';
import { ConnectionState, WebsocketService } from 'app/foundation/service/websocket.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { distinctUntilChanged, filter, throttleTime } from 'rxjs/operators';
import { AlertService } from 'app/foundation/service/alert.service';
import { SubmissionSyncPayload, isSubmissionSyncPayload } from 'app/exercise/shared/entities/submission/submission-sync-payload.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
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
    private destroyed = false;
    private websocketSubscription?: Subscription;
    private connectionStateSubscription?: Subscription;

    // Sync settings
    readonly THROTTLE_TIME = 2000; // ms

    readonly exerciseType = input.required<ExerciseType>();
    readonly submissionObservable = input<Observable<Submission> | undefined>();
    readonly submissionPatchObservable = input<Observable<SubmissionPatch> | undefined>();
    readonly participation = input.required<StudentParticipation>();

    readonly receiveSubmission = output<Submission>();
    readonly receiveSubmissionPatch = output<SubmissionPatch>();
    // Fires on every transition into the connected STOMP state (initial connect + each reconnect).
    // Consumers should re-announce their full local state in response.
    readonly reconnected = output<void>();

    currentUser?: User;
    websocketTopic!: string; // set in ngOnInit() before any read

    constructor() {
        void this.accountService.identity().then((user: User | undefined) => (this.currentUser = user));
    }

    /**
     * Life cycle hook to indicate component creation is done
     */
    ngOnInit(): void {
        this.websocketTopic = this.buildWebsocketTopic();
        this.setupReceiver();
        this.setupSender();
        this.setupReconnectSync();
    }

    ngOnDestroy(): void {
        this.destroyed = true;
        this.websocketSubscription?.unsubscribe();
        this.connectionStateSubscription?.unsubscribe();
    }

    /** Loads Apollon. A method of its own, so that a test can hold the download back. */
    protected loadApollon() {
        return import('@tumaet/apollon');
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
        const submissionObservable = this.submissionObservable();
        if (submissionObservable) {
            submissionObservable.pipe(throttleTime(this.THROTTLE_TIME, undefined, { leading: true, trailing: true })).subscribe({
                next: (submission: Submission) => {
                    if (submission.participation) {
                        submission.participation.exercise = undefined;
                        submission.participation.submissions = [];
                    }
                    this.teamSubmissionWebsocketService.send<Submission>(this.buildSendDestination('/update'), submission);
                },
                error: (error: unknown) => this.onError(error),
            });
        }

        this.submissionPatchObservable()?.subscribe({
            next: (submissionPatch: SubmissionPatch) => {
                this.teamSubmissionWebsocketService.send<SubmissionPatch>(this.buildSendDestination('/patch'), submissionPatch);
            },
            error: (error: unknown) => this.onError(error),
        });
    }

    // Re-runs the Yjs handshake of a modeling exercise on every (re)connect. Apollon broadcasts only incremental updates,
    // so a peer who missed any edits during a disconnect window would otherwise stay out of sync.
    private setupReconnectSync() {
        this.connectionStateSubscription = this.teamSubmissionWebsocketService.connectionState
            .pipe(
                distinctUntilChanged((a: ConnectionState, b: ConnectionState) => a.connected === b.connected),
                filter((state: ConnectionState) => state.connected),
            )
            .subscribe({
                next: async () => {
                    try {
                        if (this.exerciseType() === ExerciseType.MODELING) {
                            // Only the modeling team sync has a patch endpoint on the server (ParticipationTeamWebsocketService), and the Yjs
                            // handshake is built by Apollon. The package is loaded here and not at the top of the file: this component is part
                            // of the text editor route, and a text exercise has nothing to re-announce, so it never downloads the editor.
                            const { ApollonEditor } = await this.loadApollon();
                            if (this.destroyed) {
                                // The student left the exercise while the editor downloaded: unsubscribing does not cancel this callback.
                                return;
                            }
                            const initialSync = new SubmissionPatch(ApollonEditor.generateInitialSyncMessage());
                            this.teamSubmissionWebsocketService.send<SubmissionPatch>(this.buildSendDestination('/patch'), initialSync);

                            const initialAwarenessSync = new SubmissionPatch(ApollonEditor.generateInitialAwarenessSyncMessage());
                            this.teamSubmissionWebsocketService.send<SubmissionPatch>(this.buildSendDestination('/patch'), initialAwarenessSync);
                        }
                        this.reconnected.emit();
                    } catch (error: unknown) {
                        this.onError(error);
                    }
                },
                error: (error: unknown) => this.onError(error),
            });
    }

    private isSelf(user: User) {
        return this.currentUser?.login === user.login;
    }

    /**
     * Topic on which the server distributes the team's submission changes (needs to match ExerciseWebsocketTopics.TEAM_TEXT_SUBMISSIONS and TEAM_MODELING_SUBMISSIONS)
     */
    private buildWebsocketTopic(): string {
        return `/topic/participations/${this.participation().id}/team/${this.exerciseType()}-submissions`;
    }

    /**
     * Destination for the own submission changes (needs to match the routes in ParticipationTeamWebsocketService.java)
     */
    private buildSendDestination(path: string): string {
        return `/app/participations/${this.participation().id}/team/${this.exerciseType()}-submissions${path}`;
    }

    private onError(error: unknown) {
        const message = error instanceof Error ? error.message : String(error);
        this.alertService.error(message);
    }
}
