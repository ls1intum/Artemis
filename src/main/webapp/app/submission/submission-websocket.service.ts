import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { BehaviorSubject, merge, Observable, of, Subject, Subscription, timer } from 'rxjs';
import { catchError, distinctUntilChanged, filter, switchMap, tap } from 'rxjs/operators';
import { JhiWebsocketService } from 'app/core';
import { Submission } from 'app/entities/submission/submission.model';
import { SERVER_API_URL } from 'app/app.constants';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';

export interface ISubmissionWebsocketService {
    getLatestPendingSubmission: (participationId: number) => Observable<Submission | null>;
}

@Injectable({ providedIn: 'root' })
export class SubmissionWebsocketService implements ISubmissionWebsocketService, OnDestroy {
    // Current value: 1 minute.
    private EXPECTED_RESULT_CREATION_TIME_MS = 60 * 1000;
    private SUBMISSION_TEMPLATE_TOPIC = '/topic/participation/%participationId%/newSubmission';

    private resultSubscriptions: { [participationId: number]: Subscription } = {};
    private submissionTopicsSubscribed: { [participationId: number]: string } = {};
    private submissionSubjects: { [participationId: number]: BehaviorSubject<Submission | null> } = {};
    private resultTimerSubjects: { [participationId: number]: Subject<null> } = {};
    private resultTimerSubscriptions: { [participationId: number]: Subscription } = {};

    private latestValue: { [participationId: number]: Submission | null } = {};

    constructor(
        private websocketService: JhiWebsocketService,
        private http: HttpClient,
        private participationWebsocketService: ParticipationWebsocketService,
        private alertService: JhiAlertService,
    ) {}

    ngOnDestroy(): void {
        Object.values(this.resultSubscriptions).forEach(sub => sub.unsubscribe());
        Object.values(this.resultTimerSubscriptions).forEach(sub => sub.unsubscribe());
        Object.values(this.submissionTopicsSubscribed).forEach(topic => this.websocketService.unsubscribe(topic));
    }

    /**
     * Fetch the latest pending submission for a participation, which means:
     * - Submission is the newest one (by submissionDate)
     * - Submission does not have a result (yet)
     * - Submission is not older than 2 minutes (in this case it could be that never a result will come due to an error)
     *
     * @param participationId
     */
    private fetchLatestPendingSubmission = (participationId: number): Observable<Submission> => {
        return this.http.get<Submission>(SERVER_API_URL + 'api/participations/' + participationId + '/latest-submission');
    };

    /**
     * Start a timer after which the timer subject will notify the corresponding subject.
     * Side effect: Timer will also emit an alert when the time runs out as it means here that no result came for a submission.
     *
     * @param participationId
     */
    private startResultWaitingTimer = (participationId: number) => {
        timer(this.EXPECTED_RESULT_CREATION_TIME_MS)
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

    /**
     * Set up a submission subscription for the latest pending submission if not yet existing.
     *
     * @param participationId
     */
    private setupWebsocketSubscription = (participationId: number): void => {
        if (!this.submissionTopicsSubscribed[participationId]) {
            const newSubmissionTopic = this.SUBMISSION_TEMPLATE_TOPIC.replace('%participationId%', participationId.toString());
            this.submissionTopicsSubscribed[participationId] = newSubmissionTopic;
            this.websocketService.subscribe(newSubmissionTopic);
            this.resultTimerSubjects[participationId] = new Subject<null>();
            this.websocketService
                .receive(newSubmissionTopic)
                .pipe(
                    tap((submission: Submission) => {
                        const subject = this.submissionSubjects[participationId];
                        subject.next(submission);
                        // Now we start a timer, if there is no result when the timer runs out, it will notify the subscribers that no result was received and show an error.
                        this.startResultWaitingTimer(participationId);
                    }),
                    tap((submission: Submission) => (this.latestValue[participationId] = submission)),
                )
                .subscribe();
        }
    };

    /**
     * Waits for a new result to come in while a pending submission exists.
     * Will stop waiting after the timer subject has emited a value.
     *
     * @param participationId
     */
    private subscribeForNewResult = (participationId: number) => {
        if (this.resultSubscriptions[participationId]) {
            return;
        }
        const resultObservable = this.participationWebsocketService.subscribeForLatestResultOfParticipation(participationId).pipe(distinctUntilChanged());

        this.resultSubscriptions[participationId] = merge(this.resultTimerSubjects[participationId], resultObservable)
            .pipe(
                filter(() => !!this.latestValue[participationId]),
                tap(() => {
                    this.resetResultWaitingTimer(participationId);
                    this.submissionSubjects[participationId].next(null);
                }),
            )
            .subscribe();
    };

    /**
     * Subscribe for the latest pending submission for the given participation.
     * A latest pending submission is characterized by the following properties:
     * - Submission is the newest one (by submissionDate)
     * - Submission does not have a result (yet)
     * - Submission is not older than 2 minutes (in this case it could be that never a result will come due to an error)
     *
     * Will emit:
     * - A submission if a last pending submission exists.
     * - A null value when there is no pending submission.
     * - A null value when no result arrived in time for the submission.
     *
     * @param participationId
     */
    public getLatestPendingSubmission = (participationId: number) => {
        const subject = this.submissionSubjects[participationId];
        if (subject) {
            return subject.asObservable();
        }
        return this.fetchLatestPendingSubmission(participationId).pipe(
            catchError(() => of(null)),
            tap(() => this.setupWebsocketSubscription(participationId)),
            switchMap((submission: Submission | null) => {
                const newSubject = new BehaviorSubject(submission);
                this.submissionSubjects[participationId] = newSubject;
                return newSubject.asObservable();
            }),
            tap(() => this.subscribeForNewResult(participationId)),
        );
    };
}
