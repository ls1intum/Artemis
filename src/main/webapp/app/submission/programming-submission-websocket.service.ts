import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, merge, Observable, of, from, Subject, Subscription, timer, pipe, UnaryFunction } from 'rxjs';
import { catchError, distinctUntilChanged, filter, last, concatAll, switchMap, map, tap } from 'rxjs/operators';
import { JhiWebsocketService } from 'app/core';
import { Submission } from 'app/entities/submission/submission.model';
import { SERVER_API_URL } from 'app/app.constants';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { Result } from 'app/entities/result';
import { ProgrammingSubmission } from 'app/entities/programming-submission';

export enum ProgrammingSubmissionState {
    // The last submission of participation has a result.
    HAS_NO_PENDING_SUBMISSION = 'HAS_NO_PENDING_SUBMISSION',
    // The submission was created on the server, we assume that the build is running within an expected time frame.
    IS_BUILDING_PENDING_SUBMISSION = 'IS_BUILDING_PENDING_SUBMISSION',
    // A failed submission is a pending submission that has not received a result within an expected time frame.
    HAS_FAILED_SUBMISSION = 'HAS_PENDING_SUBMISSION_WITHOUT_RESULT',
}

export type ProgrammingSubmissionStateObj = [ProgrammingSubmissionState, Submission | null];

export interface ISubmissionWebsocketService {
    getLatestPendingSubmissionByParticipationId: (participationId: number) => Observable<ProgrammingSubmissionStateObj>;
    triggerBuild: (participationId: number) => Observable<Object>;
    triggerInstructorBuild: (participationId: number) => Observable<Object>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingSubmissionWebsocketService implements ISubmissionWebsocketService, OnDestroy {
    public RESOURCE_URL = SERVER_API_URL + 'api/programming-submissions/';
    // Current value: 2 minutes.
    private EXPECTED_RESULT_CREATION_TIME_MS = 2 * 60 * 1000;
    private SUBMISSION_TEMPLATE_TOPIC = '/topic/participation/%participationId%/newSubmission';

    private resultSubscriptions: { [participationId: number]: Subscription } = {};
    private submissionTopicsSubscribed: { [participationId: number]: string } = {};
    // Null describes the case where no pending submission exists, undefined is used for the setup process and will not be emitted to subscribers.
    private submissionSubjects: { [participationId: number]: BehaviorSubject<[ProgrammingSubmissionState, Submission | null | undefined]> } = {};
    private resultTimerSubjects: { [participationId: number]: Subject<null> } = {};
    private resultTimerSubscriptions: { [participationId: number]: Subscription } = {};

    private latestSubmission: { [participationId: number]: Submission | null } = {};

    constructor(private websocketService: JhiWebsocketService, private http: HttpClient, private participationWebsocketService: ParticipationWebsocketService) {}

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
    private fetchLatestPendingSubmissionByParticipationId = (participationId: number): Observable<ProgrammingSubmission | null> => {
        return this.http
            .get<ProgrammingSubmission>(SERVER_API_URL + 'api/programming-exercise-participations/' + participationId + '/latest-pending-submission')
            .pipe(catchError(() => of(null)));
    };

    private fetchLatestPendingSubmissionByExerciseId = (exerciseId: number): Observable<{ [participationId: number]: ProgrammingSubmission | null }> => {
        return this.http
            .get<{ [participationId: number]: ProgrammingSubmission | null }>(SERVER_API_URL + `api/programming-exercises/${exerciseId}/latest-pending-submission`)
            .pipe(catchError(() => of([])));
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
                }),
            )
            .subscribe();
    };

    private onError = (participationId: number) => {
        this.emitFailedSubmission(participationId);
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
                        subject.next([ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission]);
                        // Now we start a timer, if there is no result when the timer runs out, it will notify the subscribers that no result was received and show an error.
                        this.startResultWaitingTimer(participationId);
                    }),
                    tap((submission: Submission) => (this.latestSubmission[participationId] = submission)),
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
                return result.submission.id === this.latestSubmission[participationId]!.id;
            }),
            distinctUntilChanged(),
            tap(() => {
                // This is the normal case - the last pending submission received a result, so we emit null as the message that there is no pending submission anymore.
                this.submissionSubjects[participationId].next([ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, null]);
            }),
        );

        // If the timer runs out, we will emit an error as we assume the result is lost.
        const timerObservable = this.resultTimerSubjects[participationId].pipe(
            tap(() => {
                this.onError(participationId);
            }),
        );

        this.resultSubscriptions[participationId] = merge(timerObservable, resultObservable)
            .pipe(
                filter(() => !!this.latestSubmission[participationId]),
                tap(() => {
                    // We reset the timer when a new result came through OR the timer ran out. The stream will then be inactive until the next submission comes in.
                    this.resetResultWaitingTimer(participationId);
                }),
            )
            .subscribe();
    };

    private emitNoPendingSubmission = (participationId: number) => {
        this.submissionSubjects[participationId].next([ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, null]);
    };

    private emitBuildingSubmission = (participationId: number, submission: ProgrammingSubmission) => {
        this.submissionSubjects[participationId].next([ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission]);
    };

    private emitFailedSubmission = (participationId: number) => {
        this.submissionSubjects[participationId].next([ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, null]);
    };

    /**
     * Check how much time is still left for the build.
     *
     * @param submission for which to check the passed build time.
     * @return the expected rest time to wait for the build.
     */
    private getExpectedRemainingTimeForBuild = (submission: ProgrammingSubmission): number => {
        return this.EXPECTED_RESULT_CREATION_TIME_MS - (Date.now() - Date.parse(submission.submissionDate as any));
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
    public getLatestPendingSubmissionByParticipationId = (participationId: number) => {
        const subject = this.submissionSubjects[participationId];
        if (subject) {
            return subject.asObservable().pipe(filter(([, s]) => s !== undefined)) as Observable<ProgrammingSubmissionStateObj>;
        }
        // The setup process is difficult, because it should not happen that multiple subscribers trigger the setup process at the same time.
        // There the subject is returned before the REST call is made, but will emit its result as soon as it returns.
        this.submissionSubjects[participationId] = new BehaviorSubject<[ProgrammingSubmissionState, Submission | null | undefined]>([
            ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
            undefined,
        ]);
        this.fetchLatestPendingSubmissionByParticipationId(participationId)
            .pipe(switchMap(submission => this.processPendingSubmission(submission, participationId)))
            .subscribe();
        // We just remove the initial undefined from the pipe as it is only used to make the setup process easier.
        return this.submissionSubjects[participationId].asObservable().pipe(filter(([, s]) => s !== undefined)) as Observable<ProgrammingSubmissionStateObj>;
    };

    public preloadLatestPendingSubmissionsForExercise = (exerciseId: number) => {
        // TODO: Add security mechanism for case of multiple subscribers.
        return this.fetchLatestPendingSubmissionByExerciseId(exerciseId).pipe(
            map(submissions => {
                return Object.entries(submissions).map(([participationId, submission]) => [parseInt(participationId, 10), submission]);
            }),
            switchMap((submissions: Array<[number, ProgrammingSubmission | null]>) => {
                if (!submissions.length) {
                    return of([]);
                }
                return from(submissions).pipe(
                    switchMap(([participationId, submission]) => {
                        this.submissionSubjects[participationId] = new BehaviorSubject<[ProgrammingSubmissionState, Submission | null | undefined]>([
                            ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
                            undefined,
                        ]);
                        return this.processPendingSubmission(submission, participationId);
                    }),
                );
            }),
        );
    };

    private processPendingSubmission = (submission: ProgrammingSubmission | null, participationId: number) => {
        // TODO: Fix shadowed variable name.
        return of(submission).pipe(
            tap((submission: ProgrammingSubmission | null) => {
                this.latestSubmission[participationId] = submission;
            }),
            tap(() => {
                this.setupWebsocketSubscription(participationId);
                this.subscribeForNewResult(participationId);
            }),
            tap((submission: ProgrammingSubmission | null) => {
                if (submission) {
                    const remainingTime = this.getExpectedRemainingTimeForBuild(submission);
                    if (remainingTime > 0) {
                        this.emitBuildingSubmission(participationId, submission);
                        this.startResultWaitingTimer(participationId, remainingTime);
                        return;
                    }
                    // The server sends the latest submission without a result - so it could be that the result is too old. In this case the error is shown directly.
                    this.onError(participationId);
                    return;
                }
                this.emitNoPendingSubmission(participationId);
            }),
        );
    };

    public triggerBuild(participationId: number) {
        return this.http.post(this.RESOURCE_URL + participationId + '/trigger-build', {});
    }

    public triggerInstructorBuild(participationId: number) {
        return this.http.post(this.RESOURCE_URL + participationId + '/trigger-instructor-build', {});
    }
}
