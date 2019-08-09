import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { BehaviorSubject, merge, Observable, of, Subject, Subscription, timer } from 'rxjs';
import { catchError, distinctUntilChanged, filter, map, tap } from 'rxjs/operators';
import { JhiWebsocketService } from 'app/core';
import { Submission } from 'app/entities/submission/submission.model';
import { SERVER_API_URL } from 'app/app.constants';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { Result } from 'app/entities/result';

export interface ISubmissionWebsocketService {
    getLatestPendingSubmission: (participationId: number) => Observable<Submission | null>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingSubmissionWebsocketService implements ISubmissionWebsocketService, OnDestroy {
    // Current value: 2 minutes.
    private EXPECTED_RESULT_CREATION_TIME_MS = 2 * 60 * 1000;
    private SUBMISSION_TEMPLATE_TOPIC = '/topic/participation/%participationId%/newSubmission';

    private resultSubscriptions: { [participationId: number]: Subscription } = {};
    private submissionTopicsSubscribed: { [participationId: number]: string } = {};
    // Null describes the case where no pending submission exists, undefined is used for the setup process and will not be emitted to subscribers.
    private submissionSubjects: { [participationId: number]: BehaviorSubject<Submission | null | undefined> } = {};
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
     * - Submission is not older than EXPECTED_RESULT_CREATION_TIME_MS (in this case it could be that never a result will come due to an error)
     *
     * @param participationId
     */
    private fetchLatestPendingSubmission = (participationId: number): Observable<Submission | null> => {
        return this.http.get<Submission>(SERVER_API_URL + 'api/programming-exercise-participation/' + participationId + '/latest-pending-submission').pipe(
            catchError(() => of(null)),
            map((submission: Submission | null) => {
                if (submission) {
                    const remainingTime = this.EXPECTED_RESULT_CREATION_TIME_MS - (Date.now() - Date.parse(submission.submissionDate as any));
                    if (remainingTime > 0) {
                        this.startResultWaitingTimer(participationId, remainingTime);
                        return submission;
                    } else {
                        // The server sends the latest submission without a result - so it could be that the result is too old. In this case the error is shown directly.
                        this.onError();
                        return null;
                    }
                }
                return null;
            }),
        );
    };

    /**
     * Start a timer after which the timer subject will notify the corresponding subject.
     * Side effect: Timer will also emit an alert when the time runs out as it means here that no result came for a submission.
     *
     * @param participationId
     * @param time
     */
    private startResultWaitingTimer = (participationId: number, time = this.EXPECTED_RESULT_CREATION_TIME_MS) => {
        this.resetResultWaitingTimer(participationId);
        this.resultTimerSubscriptions[participationId] = timer(time)
            .pipe(
                tap(() => {
                    this.resultTimerSubjects[participationId].next(null);
                    this.onError();
                }),
            )
            .subscribe();
    };

    private onError = () => {
        this.alertService.error('artemisApp.submission.resultTimeout');
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
        const resultObservable = this.participationWebsocketService.subscribeForLatestResultOfParticipation(participationId).pipe(
            // Make sure that the incoming result belongs the latest submission!
            filter((result: Result | null) => {
                if (!result || !result.submission) {
                    return false;
                }
                return result.submission.id === this.latestValue[participationId]!.id;
            }),
            distinctUntilChanged(),
        );

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
     * - Submission is not older than EXPECTED_RESULT_CREATION_TIME_MS (in this case it could be that never a result will come due to an error)
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
            return subject.asObservable().pipe(filter(s => s !== undefined)) as Observable<Submission | null>;
        }
        // The setup process is difficult, because it should not happen that multiple subscribers trigger the setup process at the same time.
        // There the subject is returned before the REST call is made, but will emit its result as soon as it returns.
        this.submissionSubjects[participationId] = new BehaviorSubject<Submission | null | undefined>(undefined);
        this.fetchLatestPendingSubmission(participationId)
            .pipe(
                tap((submission: Submission | null) => {
                    this.latestValue[participationId] = submission;
                }),
                tap(() => {
                    this.setupWebsocketSubscription(participationId);
                    this.subscribeForNewResult(participationId);
                }),
                tap((submission: Submission | null) => {
                    this.submissionSubjects[participationId].next(submission);
                }),
            )
            .subscribe();
        // We just remove the initial undefined from the pipe as it is only used to make the setup process easier.
        return this.submissionSubjects[participationId].asObservable().pipe(filter(s => s !== undefined)) as Observable<Submission | null>;
    };
}
