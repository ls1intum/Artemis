import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { BehaviorSubject, Observable, merge, Subject, Subscription, timer, of } from 'rxjs';
import { catchError, distinctUntilChanged, filter, first, switchMap, tap } from 'rxjs/operators';
import { JhiWebsocketService } from 'app/core';
import { Submission } from 'app/entities/submission/submission.model';
import { SERVER_API_URL } from 'app/app.constants';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';

// Current value: 2 minutes.
const EXPECTED_RESULT_CREATION_TIME_MS = 2 * 120 * 1000;

@Injectable({ providedIn: 'root' })
export class SubmissionWebsocketService implements OnDestroy {
    private newSubmissionRouteTopic = '/topic/participation/%participationId%/newSubmission';
    private subscriptions: { [participationId: number]: string } = {};
    private resultSubscriptions: { [participationId: number]: Subscription } = {};
    private subjects: { [participationId: number]: BehaviorSubject<Submission | null> } = {};
    private latestValue: { [participationId: number]: Submission | null } = {};
    private resultTimerSubjects: { [participationId: number]: Subject<null> } = {};
    private resultTimerSubscriptions: { [participationId: number]: Subscription } = {};

    constructor(
        private websocketService: JhiWebsocketService,
        private http: HttpClient,
        private participationWebsocketService: ParticipationWebsocketService,
        private alertService: JhiAlertService,
    ) {}

    ngOnDestroy(): void {
        Object.values(this.resultSubscriptions).forEach(sub => sub.unsubscribe());
        Object.values(this.resultTimerSubscriptions).forEach(sub => sub.unsubscribe());
    }

    private fetchLatestPendingSubmission = (participationId: number): Observable<Submission> => {
        return this.http.get<Submission>(SERVER_API_URL + 'api/participations/' + participationId + '/latest-submission');
    };

    private startResultWaitingTimer = (participationId: number) => {
        timer(EXPECTED_RESULT_CREATION_TIME_MS)
            .pipe(
                tap(() => {
                    this.resultTimerSubjects[participationId].next(null);
                    const currentSubmission = this.latestValue[participationId] || { id: undefined, submissionDate: undefined };
                    this.alertService.error('artemisApp.submission.resultTimeout', { id: currentSubmission.id, submissionDate: currentSubmission.submissionDate });
                }),
            )
            .subscribe();
    };

    private resetResultWaitingTimer = (participationId: number) => {
        if (this.resultTimerSubscriptions[participationId]) {
            this.resultTimerSubscriptions[participationId].unsubscribe();
        }
    };

    private setupWebsocketSubscription = (participationId: number): void => {
        if (!this.subscriptions[participationId]) {
            const newSubmissionTopic = this.newSubmissionRouteTopic.replace('%participationId%', participationId.toString());
            this.websocketService.subscribe(newSubmissionTopic);
            this.resultTimerSubjects[participationId] = new Subject<null>();
            this.websocketService
                .receive(newSubmissionTopic)
                .pipe(
                    tap((submission: Submission) => {
                        const subject = this.subjects[participationId];
                        subject.next(submission);
                        this.startResultWaitingTimer(participationId);
                    }),
                    tap((submission: Submission) => (this.latestValue[participationId] = submission)),
                )
                .subscribe();
        }
    };

    private subscribeForLatestResult = (participationId: number) => {
        if (this.resultSubscriptions[participationId]) {
            return;
        }
        const resultObservable = this.participationWebsocketService.subscribeForLatestResultOfParticipation(participationId).pipe(distinctUntilChanged());

        this.resultSubscriptions[participationId] = merge(this.resultTimerSubjects[participationId], resultObservable)
            .pipe(
                filter(() => !!this.latestValue[participationId]),
                tap(() => {
                    this.resetResultWaitingTimer(participationId);
                    this.subjects[participationId].next(null);
                }),
            )
            .subscribe();
    };

    public getLatestPendingSubmission = (participationId: number) => {
        const subject = this.subjects[participationId];
        if (subject) {
            return subject.asObservable();
        }
        return this.fetchLatestPendingSubmission(participationId).pipe(
            catchError(() => of(null)),
            tap(() => this.setupWebsocketSubscription(participationId)),
            switchMap((submission: Submission | null) => {
                const newSubject = new BehaviorSubject(submission);
                this.subjects[participationId] = newSubject;
                return newSubject.asObservable();
            }),
            tap(() => this.subscribeForLatestResult(participationId)),
        );
    };
}
