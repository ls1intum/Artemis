import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, from, merge, Observable, of, Subject, Subscription, throwError, timer } from 'rxjs';
import { catchError, distinctUntilChanged, filter, map, reduce, switchMap, tap, mergeMap, concatAll, combineAll, mergeAll } from 'rxjs/operators';
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
    HAS_FAILED_SUBMISSION = 'HAS_FAILED_SUBMISSION',
}

export type ProgrammingSubmissionStateObj = [ProgrammingSubmissionState, ProgrammingSubmission | null];

export type ExerciseBuildState = { [participationId: number]: [ProgrammingSubmissionState, ProgrammingSubmission | null] };

export interface IProgrammingSubmissionService {
    getLatestPendingSubmissionByParticipationId: (participationId: number, exerciseId: number) => Observable<ProgrammingSubmissionStateObj>;
    getSubmissionStateOfExercise: (exerciseId: number) => Observable<ExerciseBuildState>;
    triggerBuild: (participationId: number) => Observable<Object>;
    triggerInstructorBuild: (participationId: number) => Observable<Object>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingSubmissionService implements IProgrammingSubmissionService, OnDestroy {
    public SUBMISSION_RESOURCE_URL = SERVER_API_URL + 'api/programming-submissions/';
    public PROGRAMMING_EXERCISE_RESOURCE_URL = SERVER_API_URL + 'api/programming-exercises/';
    // Current value: 2 minutes.
    private EXPECTED_RESULT_CREATION_TIME_MS = 2 * 60 * 1000;
    private SUBMISSION_TEMPLATE_TOPIC = '/topic/participation/%participationId%/newSubmission';

    private resultSubscriptions: { [participationId: number]: Subscription } = {};
    private submissionTopicsSubscribed: { [participationId: number]: string } = {};
    // Null describes the case where no pending submission exists, undefined is used for the setup process and will not be emitted to subscribers.
    private submissionSubjects: { [participationId: number]: BehaviorSubject<[ProgrammingSubmissionState, ProgrammingSubmission | null | undefined]> } = {};
    private exerciseBuildStateSubjects: { [exerciseId: number]: BehaviorSubject<ExerciseBuildState | undefined> } = {};
    private resultTimerSubjects: { [participationId: number]: Subject<null> } = {};
    private resultTimerSubscriptions: { [participationId: number]: Subscription } = {};

    private exerciseBuildState: { [exerciseId: number]: { [participationId: number]: [ProgrammingSubmissionState, ProgrammingSubmission | null] } } = {};

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
     * This method is private on purpose as subscribers should not try to load initial data!
     * A separate initial fetch is not necessary as this service takes care of it and provides a BehaviorSubject.
     *
     * @param participationId
     */
    private fetchLatestPendingSubmissionByParticipationId = (participationId: number): Observable<ProgrammingSubmission | null> => {
        return this.http
            .get<ProgrammingSubmission>(SERVER_API_URL + 'api/programming-exercise-participations/' + participationId + '/latest-pending-submission')
            .pipe(catchError(() => of(null)));
    };

    /**
     * Fetch the latest pending submission for all participations of a given exercise.
     * Returns an empty array if the api request fails.
     *
     * This method is private on purpose as subscribers should not try to load initial data!
     * A separate initial fetch is not necessary as this service takes care of it and provides a BehaviorSubject.
     *
     * @param exerciseId of programming exercise.
     */
    private fetchLatestPendingSubmissionByExerciseId = (exerciseId: number): Observable<{ [participationId: number]: ProgrammingSubmission | null }> => {
        return this.http
            .get<{ [participationId: number]: ProgrammingSubmission | null }>(SERVER_API_URL + `api/programming-exercises/${exerciseId}/latest-pending-submissions`)
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

    private onError = (participationId: number, exerciseId: number) => {
        this.emitFailedSubmission(participationId, exerciseId);
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
    private setupWebsocketSubscription = (participationId: number, exerciseId: number): void => {
        if (!this.submissionTopicsSubscribed[participationId]) {
            const newSubmissionTopic = this.SUBMISSION_TEMPLATE_TOPIC.replace('%participationId%', participationId.toString());
            this.submissionTopicsSubscribed[participationId] = newSubmissionTopic;
            this.websocketService.subscribe(newSubmissionTopic);
            this.resultTimerSubjects[participationId] = new Subject<null>();
            this.websocketService
                .receive(newSubmissionTopic)
                .pipe(
                    tap((submission: ProgrammingSubmission) => {
                        this.emitBuildingSubmission(participationId, exerciseId, submission);
                        // Now we start a timer, if there is no result when the timer runs out, it will notify the subscribers that no result was received and show an error.
                        this.startResultWaitingTimer(participationId);
                    }),
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
    private subscribeForNewResult = (participationId: number, exerciseId: number) => {
        if (this.resultSubscriptions[participationId]) {
            return;
        }
        const resultObservable = this.participationWebsocketService.subscribeForLatestResultOfParticipation(participationId).pipe(
            // Make sure that the incoming result belongs the latest submission!
            filter((result: Result | null) => {
                if (!result || !result.submission) {
                    return false;
                }
                return result.submission.id === this.exerciseBuildState[exerciseId][participationId][1]!.id;
            }),
            distinctUntilChanged(),
            tap(() => {
                // This is the normal case - the last pending submission received a result, so we emit null as the message that there is no pending submission anymore.
                this.emitNoPendingSubmission(participationId, exerciseId);
            }),
        );

        // If the timer runs out, we will emit an error as we assume the result is lost.
        const timerObservable = this.resultTimerSubjects[participationId].pipe(
            tap(() => {
                this.onError(participationId, exerciseId);
            }),
        );

        this.resultSubscriptions[participationId] = merge(timerObservable, resultObservable)
            .pipe(
                filter(() => !!this.exerciseBuildState[exerciseId][participationId]),
                tap(() => {
                    // We reset the timer when a new result came through OR the timer ran out. The stream will then be inactive until the next submission comes in.
                    this.resetResultWaitingTimer(participationId);
                }),
            )
            .subscribe();
    };

    private emitNoPendingSubmission = (participationId: number, exerciseId: number) => {
        const newSubmissionState = [ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, null] as ProgrammingSubmissionStateObj;
        this.submissionSubjects[participationId].next(newSubmissionState);
        if (!this.exerciseBuildState[exerciseId]) {
            this.exerciseBuildState[exerciseId] = {};
        }
        this.exerciseBuildState[exerciseId][participationId] = newSubmissionState;
        // TODO: This could be refactored into a setter.
        const exerciseBuildStateSubject = this.exerciseBuildStateSubjects[exerciseId];
        if (exerciseBuildStateSubject) {
            exerciseBuildStateSubject.next(this.exerciseBuildState[exerciseId]);
        }
    };

    private emitBuildingSubmission = (participationId: number, exerciseId: number, submission: ProgrammingSubmission) => {
        const newSubmissionState = [ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission] as ProgrammingSubmissionStateObj;
        this.submissionSubjects[participationId].next(newSubmissionState);
        if (!this.exerciseBuildState[exerciseId]) {
            this.exerciseBuildState[exerciseId] = {};
        }
        this.exerciseBuildState[exerciseId][participationId] = newSubmissionState;
        // TODO: This could be refactored into a setter.
        const exerciseBuildStateSubject = this.exerciseBuildStateSubjects[exerciseId];
        if (exerciseBuildStateSubject) {
            exerciseBuildStateSubject.next(this.exerciseBuildState[exerciseId]);
        }
    };

    private emitFailedSubmission = (participationId: number, exerciseId: number) => {
        const newSubmissionState = [ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, null] as ProgrammingSubmissionStateObj;
        this.submissionSubjects[participationId].next(newSubmissionState);
        if (!this.exerciseBuildState[exerciseId]) {
            this.exerciseBuildState[exerciseId] = {};
        }
        this.exerciseBuildState[exerciseId][participationId] = newSubmissionState;
        // TODO: This could be refactored into a setter.
        const exerciseBuildStateSubject = this.exerciseBuildStateSubjects[exerciseId];
        if (exerciseBuildStateSubject) {
            exerciseBuildStateSubject.next(this.exerciseBuildState[exerciseId]);
        }
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
     * This method will execute a REST call to the server so that the subscriber will always receive the latest information from the server.
     *
     * @param participationId
     */
    public getLatestPendingSubmissionByParticipationId = (participationId: number, exerciseId: number) => {
        const subject = this.submissionSubjects[participationId];
        if (subject) {
            return subject.asObservable().pipe(filter(([, s]) => s !== undefined)) as Observable<ProgrammingSubmissionStateObj>;
        }
        if (!this.exerciseBuildState[exerciseId]) {
            this.exerciseBuildState[exerciseId] = {};
        }
        // The setup process is difficult, because it should not happen that multiple subscribers trigger the setup process at the same time.
        // There the subject is returned before the REST call is made, but will emit its result as soon as it returns.
        this.submissionSubjects[participationId] = new BehaviorSubject<[ProgrammingSubmissionState, ProgrammingSubmission | null | undefined]>([
            ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
            undefined,
        ]);
        this.fetchLatestPendingSubmissionByParticipationId(participationId)
            .pipe(switchMap(submission => this.processPendingSubmission(submission, participationId, exerciseId)))
            .subscribe();
        // We just remove the initial undefined from the pipe as it is only used to make the setup process easier.
        return this.submissionSubjects[participationId].asObservable().pipe(filter(([, s]) => s !== undefined)) as Observable<ProgrammingSubmissionStateObj>;
    };

    /**
     * Will retrieve and cache all pending submissions for all student participations of given exercise.
     * After calling this method, subscribers for single pending submissions will be able to use the cached submissions so that we don't execute a GET request to the server for every participation.
     *
     * Will emit once at the end so the subscriber knows that the loading & setup process is done.
     * If the user is not an instructor, this method will not be able to retrieve any pending submission.
     *
     * This method will execute a REST call to the server so that the subscriber will always receive the latest information from the server.
     *
     * @param exerciseId id of programming exercise for which to retrieve all pending submissions.
     */
    public getSubmissionStateOfExercise = (exerciseId: number): Observable<ExerciseBuildState> => {
        // We need to check if the submissions for the given exercise are already being fetched, otherwise the call would be done multiple done.
        const preloadingSubject = this.exerciseBuildStateSubjects[exerciseId];
        if (preloadingSubject) {
            return preloadingSubject.asObservable().filter(val => val !== undefined) as Observable<ExerciseBuildState>;
        }
        this.exerciseBuildStateSubjects[exerciseId] = new BehaviorSubject<ExerciseBuildState | undefined>(undefined);
        this.fetchLatestPendingSubmissionByExerciseId(exerciseId)
            .pipe(
                map(submissions => {
                    return Object.entries(submissions).map(([participationId, submission]) => [parseInt(participationId, 10), submission]);
                }),
                switchMap((submissions: Array<[number, ProgrammingSubmission | null]>) => {
                    if (!submissions.length) {
                        throwError('submission information object is empty!');
                    }
                    return from(submissions).pipe(
                        switchMap(
                            ([participationId, submission]): Observable<[number, ProgrammingSubmission | null, ProgrammingSubmissionState]> => {
                                this.submissionSubjects[participationId] = new BehaviorSubject<[ProgrammingSubmissionState, ProgrammingSubmission | null | undefined]>([
                                    ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION,
                                    undefined,
                                ]);
                                return this.processPendingSubmission(submission, participationId, exerciseId);
                            },
                        ),
                    );
                }),
                reduce((acc, val: [number, ProgrammingSubmission | null, ProgrammingSubmissionState]) => {
                    const [participationId, submission, submissionState] = val;
                    return { ...acc, [participationId]: [submissionState, submission] };
                }, {}),
                catchError(() => of({})),
            )
            .subscribe((exerciseBuildState: ExerciseBuildState) => {
                this.exerciseBuildState[exerciseId] = exerciseBuildState;
                this.exerciseBuildStateSubjects[exerciseId].next(exerciseBuildState);
            });
        return this.exerciseBuildStateSubjects[exerciseId].asObservable().pipe(filter(val => val !== undefined)) as Observable<ExerciseBuildState>;
    };

    public triggerBuild(participationId: number) {
        return this.http.post(this.SUBMISSION_RESOURCE_URL + participationId + '/trigger-build', {});
    }

    public triggerInstructorBuild(participationId: number) {
        return this.http.post(this.SUBMISSION_RESOURCE_URL + participationId + '/trigger-instructor-build', {});
    }

    public triggerInstructorBuildForAllParticipationsOfExercise(exerciseId: number) {
        return this.http.post(this.PROGRAMMING_EXERCISE_RESOURCE_URL + exerciseId + '/trigger-instructor-build-all', {});
    }

    public triggerInstructorBuildForParticipationsOfExercise(exerciseId: number, participationIds: number[]) {
        return this.http.post(this.PROGRAMMING_EXERCISE_RESOURCE_URL + exerciseId + '/trigger-instructor-build', { participationIds });
    }

    public getFailedSubmissionParticipationsForExercise(exerciseId: number) {
        const exerciseBuildState = this.exerciseBuildState[exerciseId];
        return Object.entries(exerciseBuildState)
            .filter(([, buildState]) => {
                const [submissionState] = buildState;
                return submissionState === ProgrammingSubmissionState.HAS_FAILED_SUBMISSION;
            })
            .map(([participationId]) => parseInt(participationId, 10));
    }

    /**
     * Cache a retrieved pending submission and setup the websocket connections and timer.
     *
     * @param submissionToBeProcessed to cache and use for the websocket subscriptions
     * @param participationId that serves as an identifier for caching the submission.
     */
    private processPendingSubmission = (
        submissionToBeProcessed: ProgrammingSubmission | null,
        participationId: number,
        exerciseId: number,
    ): Observable<[number, ProgrammingSubmission | null, ProgrammingSubmissionState]> => {
        if (!this.exerciseBuildState[exerciseId]) {
            this.exerciseBuildState[exerciseId] = {};
        }
        return of(submissionToBeProcessed).pipe(
            tap(() => {
                this.setupWebsocketSubscription(participationId, exerciseId);
                this.subscribeForNewResult(participationId, exerciseId);
            }),
            map((submission: ProgrammingSubmission | null) => {
                if (submission) {
                    const remainingTime = this.getExpectedRemainingTimeForBuild(submission);
                    if (remainingTime > 0) {
                        this.emitBuildingSubmission(participationId, exerciseId, submission);
                        this.startResultWaitingTimer(participationId, remainingTime);
                        return [participationId, submissionToBeProcessed, ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION];
                    }
                    // The server sends the latest submission without a result - so it could be that the result is too old. In this case the error is shown directly.
                    this.onError(participationId, exerciseId);
                    return [participationId, submissionToBeProcessed, ProgrammingSubmissionState.HAS_FAILED_SUBMISSION];
                }
                this.emitNoPendingSubmission(participationId, exerciseId);
                return [participationId, null, ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION];
            }),
            tap(([, submission, submissionState]: [number, ProgrammingSubmission | null, ProgrammingSubmissionState]) => {
                this.exerciseBuildState[exerciseId][participationId] = [submissionState, submission];
            }),
        );
    };
}
