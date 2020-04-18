import { Component, Input, Output, OnInit, EventEmitter } from '@angular/core';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Observable } from 'rxjs';
import { throttleTime, filter } from 'rxjs/internal/operators';
import { AlertService } from 'app/core/alert/alert.service';
import { ModelingSubmissionSyncPayload } from 'app/entities/submission-sync-payload.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';

@Component({
    selector: 'jhi-modeling-submission-team-sync',
    template: '',
})
export class ModelingSubmissionTeamSyncComponent implements OnInit {
    // Sync settings
    readonly throttleTime = 2000; // ms

    @Input() submissionStream$: Observable<ModelingSubmission>;
    @Input() participation: StudentParticipation;

    @Output() receiveSubmission = new EventEmitter<ModelingSubmission>();

    currentUser: User;
    websocketTopic: string;

    constructor(
        private accountService: AccountService,
        private modelingSubmissionService: ModelingSubmissionService,
        private teamSubmissionWebsocketService: JhiWebsocketService,
        private jhiAlertService: AlertService,
    ) {
        this.accountService.identity().then((user: User) => (this.currentUser = user));
    }

    ngOnInit(): void {
        this.websocketTopic = this.buildWebsocketTopic();
        this.teamSubmissionWebsocketService.subscribe(this.websocketTopic);
        this.setupReceiver();
        this.setupSender();
    }

    private setupReceiver() {
        this.teamSubmissionWebsocketService
            .receive(this.websocketTopic)
            .pipe(filter(({ sender }: ModelingSubmissionSyncPayload) => !this.isSelf(sender)))
            .subscribe(
                ({ submission, sender }: ModelingSubmissionSyncPayload) => {
                    console.log(`received model from ${sender.login}`);
                    this.receiveSubmission.emit(submission);
                },
                (error) => this.onError(error),
            );
    }

    private setupSender() {
        this.submissionStream$.pipe(throttleTime(this.throttleTime)).subscribe(
            (modelingSubmission) => {
                console.log('sending model', modelingSubmission.model);
                delete modelingSubmission.participation;
                // this.receiveSubmission.emit(modelingSubmission);
                this.teamSubmissionWebsocketService.send(this.buildWebsocketTopic('/update'), modelingSubmission);
            },
            (error) => this.onError(error),
        );
    }

    private isSelf(user: User) {
        return this.currentUser.login === user.login;
    }

    private buildWebsocketTopic(path = ''): string {
        return `/topic/participations/${this.participation.id}/team/modeling-submissions${path}`;
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, undefined);
    }
}
