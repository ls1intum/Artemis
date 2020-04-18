import { Component, Input, Output, OnInit, EventEmitter } from '@angular/core';
import { TextSubmission } from 'app/entities/text-submission.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Observable } from 'rxjs/Observable';
import { auditTime, distinctUntilChanged, filter } from 'rxjs/internal/operators';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { AlertService } from 'app/core/alert/alert.service';
import { TextSubmissionSyncPayload } from 'app/entities/submission-sync-payload.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-text-submission-team-sync',
    template: '',
})
export class TextSubmissionTeamSyncComponent implements OnInit {
    // Sync settings
    readonly throttleTime = 2000; // ms

    @Input() submission: TextSubmission;
    @Input() participation: StudentParticipation;
    @Input() answerStream$: Observable<string>;

    @Output() receiveSubmission = new EventEmitter<TextSubmission>();

    currentUser: User;
    websocketTopic: string;

    constructor(
        private accountService: AccountService,
        private textService: TextEditorService,
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
            .pipe(filter(({ sender }: TextSubmissionSyncPayload) => !this.isSelf(sender)))
            .subscribe(
                ({ submission, sender }: TextSubmissionSyncPayload) => {
                    console.log(`received answer "${submission.text}" from ${sender.login}`);
                    this.receiveSubmission.emit(submission);
                },
                (error) => this.onError(error),
            );
    }

    private setupSender() {
        this.answerStream$.pipe(auditTime(this.throttleTime), distinctUntilChanged()).subscribe(
            (answer) => {
                console.log('sending answer', answer);
                const submission = this.submissionForAnswer(answer);
                this.receiveSubmission.emit(submission);
                this.teamSubmissionWebsocketService.send(this.buildWebsocketTopic('/update'), submission);
            },
            (error) => this.onError(error),
        );
    }

    private submissionForAnswer(answer: string): TextSubmission {
        return { ...this.submission, text: answer, language: this.textService.predictLanguage(answer) };
    }

    private isSelf(user: User) {
        return this.currentUser.login === user.login;
    }

    private buildWebsocketTopic(path = ''): string {
        return `/topic/participations/${this.participation.id}/team/text-submissions${path}`;
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, undefined);
    }
}
