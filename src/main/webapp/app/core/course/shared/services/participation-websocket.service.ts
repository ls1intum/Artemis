import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, of, pipe } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import dayjs from 'dayjs/esm';
import { cloneDeep } from 'lodash-es';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

/**
 * Websocket destination for user-specific participation results.
 */
const PERSONAL_PARTICIPATION_TOPIC = `/user/topic/newResults`;

/**
 * Constructs the websocket destination for exercise-wide participation results.
 *
 * @param exerciseId ID of the exercise
 * @returns topic URL for new results of the given exercise
 */
const EXERCISE_PARTICIPATION_TOPIC = (exerciseId: number) => `/topic/exercise/${exerciseId}/newResults`;

/**
 * Public API of the ParticipationWebsocketService.
 * Exposed for dependency inversion and easier mocking in tests.
 */
export interface IParticipationWebsocketService {
    /**
     * Adds a participation to the local cache and notifies subscribers.
     *
     * @param participation The participation to be cached
     * @param exercise Optional exercise object if it is not contained in the participation
     */
    addParticipation: (participation: Participation, exercise?: Exercise) => void;

    /**
     * Returns all cached student participations for a given exercise.
     *
     * @param exerciseId ID of the exercise
     * @returns Array of student participations for the exercise or undefined if nothing is cached
     */
    getParticipationsForExercise: (exerciseId: number) => StudentParticipation[] | undefined;

    /**
     * Subscribes to general changes of participations.
     * The emitted values are full participation objects including results and exercise.
     *
     * @returns A BehaviorSubject that emits participation updates
     */
    subscribeForParticipationChanges: () => BehaviorSubject<Participation | undefined>;

    /**
     * Subscribes to the latest result for a specific participation.
     * Opens websocket subscriptions as needed.
     *
     * @param participationId ID of the participation
     * @param personal Whether this subscription is personal (user-specific) or exercise-wide
     * @param exerciseId Optional ID of the exercise (required when personal is false)
     * @returns A BehaviorSubject that emits the latest result or undefined initially
     */
    subscribeForLatestResultOfParticipation: (participationId: number, personal: boolean, exerciseId?: number) => BehaviorSubject<Result | undefined>;

    /**
     * Unsubscribes from tracking the latest result of a participation.
     * Potentially closes websocket connections if no longer needed.
     *
     * @param participationId ID of the participation to unsubscribe
     * @param exercise Exercise that the participation belongs to
     */
    unsubscribeForLatestResultOfParticipation: (participationId: number, exercise: Exercise) => void;

    /**
     * Notifies all result subscribers with the given result and updates the cached participation.
     *
     * @param result The new result
     */
    notifyAllResultSubscribers: (result: Result) => void;
}

@Injectable({ providedIn: 'root' })
export class ParticipationWebsocketService implements IParticipationWebsocketService {
    private websocketService = inject(WebsocketService);
    private participationService = inject(ParticipationService);

    /**
     * Cache of student participations by participation ID.
     */
    cachedParticipations: Map<number /* ID of participation */, StudentParticipation> = new Map<number, StudentParticipation>();

    /**
     * Tracks open non-personal result websocket subscriptions by exercise ID.
     * Value is the subscribed topic URL.
     */
    openResultWebsocketSubscriptions: Map<number /*ID of participation */, string /* url of websocket connection */> = new Map<number, string>();

    /**
     * Topic URL of an open personal websocket subscription if any.
     */
    openPersonalWebsocketSubscription?: string; /* url of websocket connection */

    /**
     * BehaviorSubjects that emit the latest result per participation ID.
     */
    resultObservables: Map<number /* ID of participation */, BehaviorSubject<Result | undefined>> = new Map<number, BehaviorSubject<Result>>();

    /**
     * BehaviorSubject that emits the latest updated participation.
     * Created lazily when a first subscription is requested.
     */
    participationObservable?: BehaviorSubject<Participation | undefined>;

    /**
     * Mapping of exercise IDs to the set of participation IDs that are currently subscribed for that exercise.
     */
    subscribedExercises: Map<number /* ID of exercise */, Set<number> /* IDs of the participations of this exercise */> = new Map<number, Set<number>>();

    /**
     * Tracks subscription type per participation ID.
     * Value is true for personal subscriptions and false for exercise-wide subscriptions.
     */
    participationSubscriptionTypes: Map<number /* ID of participation */, boolean /* Whether the participation was subscribed in personal mode */> = new Map<number, boolean>();

    /**
     * Creates an RxJS pipe that:
     * 1. Notifies result subscribers,
     * 2. Adds the result to the cached participation,
     * 3. Notifies participation subscribers.
     *
     * @returns A pipeable operator for handling incoming results
     */
    private getNotifyAllSubscribersPipe = () => {
        return pipe(tap(this.notifyResultSubscribers), switchMap(this.addResultToParticipation), tap(this.notifyParticipationSubscribers));
    };

    /**
     * Removes all locally cached participations and resets all related state.
     * Also cleans up all websocket subscription tracking data.
     */
    public resetLocalCache() {
        const participations = this.getAllParticipations();
        participations.forEach((participation) => {
            this.cachedParticipations.delete(participation.id!);
            this.removeParticipation(participation.id!, participation.exercise?.id);
        });
        this.cachedParticipations = new Map<number, StudentParticipation>();
        this.resultObservables = new Map<number, BehaviorSubject<Result>>();
        this.participationObservable = undefined;
        this.subscribedExercises = new Map<number, Set<number>>();
        this.participationSubscriptionTypes = new Map<number, boolean>();
    }

    /**
     * Notify all participation subscribers with the newest participation value (e.g. if the result has changed).
     *
     * @param participation Updated participation object
     */
    private notifyParticipationSubscribers = (participation: Participation) => {
        if (!this.participationObservable) {
            this.participationObservable = new BehaviorSubject(participation);
        } else {
            this.participationObservable.next(participation);
        }
    };

    /**
     * Notify all result subscribers with the newest result provided.
     *
     * @param result Newly received result
     */
    private notifyResultSubscribers = (result: Result) => {
        const resultObservable = this.resultObservables.get(result.submission!.participation!.id!);
        // TODO: We never convert the date strings of the result (e.g. completionDate) to a Dayjs object
        //  this could be an issue in some parts of app when a formatted date is needed.
        if (!resultObservable) {
            this.resultObservables.set(result.submission!.participation!.id!, new BehaviorSubject(result));
        } else {
            resultObservable.next(result);
        }
    };

    /**
     * Emits the participation associated with the given result from the cache, if present.
     * This method does not mutate the cached participation, it only returns it as an observable.
     *
     * @param result Result whose participation should be retrieved from cache
     * @returns Observable emitting the cached participation or an empty observable if none is found
     */
    private addResultToParticipation = (result: Result) => {
        const cachedParticipation = this.cachedParticipations.get(result.submission!.participation!.id!);
        if (cachedParticipation) {
            return of(this.cachedParticipations.get(result.submission!.participation!.id!));
        }
        return of();
    };

    /**
     * This adds a participation to the cached data maps. The exercise information is required to find the correct
     * participations for a given exercise. Please note: we explicitly do not want to use websockets here!
     *
     * @param newParticipation The new participation for the cached data maps
     * @param exercise (optional) The exercise that the participation belongs to. Only needed if exercise is missing in participation.
     * @throws Error if neither the participation nor the parameter provide a link to the exercise
     */
    public addParticipation = (newParticipation: StudentParticipation, exercise?: Exercise) => {
        // The participation needs to be cloned so that the original object is not modified
        const participation = cloneDeep(newParticipation);
        if (!participation.exercise && !exercise) {
            throw new Error('a link from the participation to the exercise is required. Please attach it manually or add exercise as function input');
        }
        participation.exercise = participation.exercise || exercise;
        this.cachedParticipations.set(participation.id!, participation);
        this.notifyParticipationSubscribers(participation);
    };

    /**
     * Returns all participations for all exercises.
     * The participation objects include the exercise data and all results.
     *
     * @returns Array of all cached student participations
     */
    private getAllParticipations(): StudentParticipation[] {
        return [...this.cachedParticipations.values()];
    }

    /**
     * Returns the student participation for the given exercise.
     * The participation objects include the exercise data and all results.
     *
     * @param exerciseId ID of the exercise that the participations belong to.
     * @returns The cached student participations (merged by test run and normal participation)
     *          for the exercise or an empty array
     */
    public getParticipationsForExercise(exerciseId: number): StudentParticipation[] {
        const participationsForExercise = [...this.cachedParticipations.values()].filter((participation) => {
            return participation.exercise?.id === exerciseId;
        });
        if (participationsForExercise?.length) {
            return this.participationService.mergeStudentParticipations(participationsForExercise);
        } else {
            return [];
        }
    }

    /**
     * Unsubscribes from the topics used by the participationId, if possible.
     * This may close personal or exercise-wide websocket connections if they are no longer needed.
     *
     * @param participationId ID of the participation that should not be tracked anymore
     * @param exerciseId Optional ID of the exercise whose subscriptions may be cleaned up
     */
    private removeParticipation(participationId: number, exerciseId?: number) {
        const subscriptionTypePersonal = this.participationSubscriptionTypes.get(participationId);
        this.participationSubscriptionTypes.delete(participationId);

        // We are only interested if there is a value
        if (subscriptionTypePersonal != undefined) {
            if (subscriptionTypePersonal) {
                // The subscription was a personal subscription, so it should only be removed if it was the last of it kind
                const openPersonalSubscriptions = [...this.participationSubscriptionTypes.values()].filter((personal: boolean) => personal).length;
                if (openPersonalSubscriptions === 0) {
                    this.websocketService.unsubscribe(PERSONAL_PARTICIPATION_TOPIC);
                    this.openPersonalWebsocketSubscription = undefined;
                }
            } else {
                // The subscriptions are non-personal subscriptions, so it should only be removed if it was the last for this exercise
                const openSubscriptionsForExercise = this.subscribedExercises.get(exerciseId!);
                if (openSubscriptionsForExercise) {
                    openSubscriptionsForExercise.delete(participationId);
                    if (openSubscriptionsForExercise.size === 0) {
                        this.subscribedExercises.delete(exerciseId!);
                        const subscribedTopic = this.openResultWebsocketSubscriptions.get(exerciseId!);
                        if (subscribedTopic) {
                            this.websocketService.unsubscribe(subscribedTopic);
                            this.openResultWebsocketSubscriptions.delete(exerciseId!);
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if a websocket connection for new results to the server already exists.
     * If not, a new one will be opened and the participation will be registered as subscribed.
     *
     * @param participationId The ID of the participation for which the subscription should be opened
     * @param personal Whether the current user is a participant in the participation
     * @param exerciseId Optional exerciseId of the exercise where the participation is part of,
     *                   only needed if personal == false
     */
    private openResultWebsocketSubscriptionIfNotExisting(participationId: number, personal: boolean, exerciseId?: number) {
        if ((personal && !this.openPersonalWebsocketSubscription) || (!personal && !this.openResultWebsocketSubscriptions.has(exerciseId!))) {
            let participationResultTopic: string;
            if (personal) {
                participationResultTopic = PERSONAL_PARTICIPATION_TOPIC;
                this.openPersonalWebsocketSubscription = participationResultTopic;
            } else {
                participationResultTopic = EXERCISE_PARTICIPATION_TOPIC(exerciseId!);
                this.openResultWebsocketSubscriptions.set(exerciseId!, participationResultTopic);
            }
            this.participationSubscriptionTypes.set(participationId, personal);
            if (!this.subscribedExercises.has(exerciseId!)) {
                this.subscribedExercises.set(exerciseId!, new Set<number>());
            }
            const subscribedParticipations = this.subscribedExercises.get(exerciseId!);
            subscribedParticipations!.add(participationId);

            this.websocketService.subscribe(participationResultTopic);
            this.websocketService.receive(participationResultTopic).pipe(this.getNotifyAllSubscribersPipe()).subscribe();
        }
    }

    /**
     * Notifies the result and participation subscribers with the newest result.
     * Note: the result must contain the participation id.
     *
     * @param result The result with which the subscribers get notified
     */
    public notifyAllResultSubscribers = (result: Result) => {
        of(result).pipe(this.getNotifyAllSubscribersPipe()).subscribe();
    };

    /**
     * Subscribes for general changes in a participation object.
     * This will be triggered if a new result is received by the service.
     * A received object will be the full participation object including all results and the exercise.
     *
     * If no observable exists a new one will be created.
     *
     * @returns A BehaviorSubject emitting participation updates, starting with undefined
     */
    public subscribeForParticipationChanges(): BehaviorSubject<Participation | undefined> {
        if (!this.participationObservable) {
            this.participationObservable = new BehaviorSubject<Participation | undefined>(undefined);
        }
        return this.participationObservable;
    }

    /**
     * Subscribes to new results of a certain participation.
     * This will be triggered if a new result is received by the service.
     * A received object will be a result object.
     *
     * If there is no observable for the participation, a new one will be created.
     *
     * @param participationId Id of Participation of which result to subscribe to
     * @param personal Whether the current user is a participant in the participation
     * @param exerciseId Optional exerciseId of the exercise where the participation is part of,
     *                   only needed if personal == false
     * @returns BehaviorSubject that emits the latest result or undefined initially
     */
    public subscribeForLatestResultOfParticipation(participationId: number, personal: boolean, exerciseId?: number): BehaviorSubject<Result | undefined> {
        this.openResultWebsocketSubscriptionIfNotExisting(participationId, personal, exerciseId);
        let resultObservable = this.resultObservables.get(participationId)!;
        if (!resultObservable) {
            resultObservable = new BehaviorSubject<Result | undefined>(undefined);
            this.resultObservables.set(participationId, resultObservable);
        }
        return resultObservable;
    }

    /**
     * Unsubscribe from tracking the latest result of a participation.
     * Might also remove the underlying websocket subscription if the exercise is no longer active.
     *
     * @param participationId ID of the participation
     * @param exercise The exercise to which the participationId belongs.
     *                 Needed for deciding whether to unsubscribe from the websocket.
     */
    public unsubscribeForLatestResultOfParticipation(participationId: number, exercise: Exercise): void {
        // Only unsubscribe from websocket, if the exercise is not active any more
        let isInactiveProgrammingExercise = false;
        if (exercise.type === ExerciseType.PROGRAMMING) {
            const programmingExercise = exercise as ProgrammingExercise;
            isInactiveProgrammingExercise =
                !!programmingExercise.buildAndTestStudentSubmissionsAfterDueDate && dayjs(programmingExercise.buildAndTestStudentSubmissionsAfterDueDate).isBefore(dayjs());
        }
        if (isInactiveProgrammingExercise || (exercise.dueDate && dayjs(exercise.dueDate).isBefore(dayjs()))) {
            this.removeParticipation(participationId, exercise.id);
        }
    }
}
