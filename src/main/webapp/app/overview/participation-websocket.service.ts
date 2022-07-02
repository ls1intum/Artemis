import { Injectable } from '@angular/core';
import { BehaviorSubject, of, pipe } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';
import { Participation } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { Exercise } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

const PERSONAL_PARTICIPATION_TOPIC = `/user/topic/newResults`;
const EXERCISE_PARTICIPATION_TOPIC = (exerciseId: number) => `/topic/exercise/${exerciseId}/newResults`;

export interface IParticipationWebsocketService {
    addParticipation: (participation: Participation, exercise?: Exercise) => void;
    getParticipationForExercise: (exerciseId: number) => StudentParticipation | undefined;
    subscribeForParticipationChanges: () => BehaviorSubject<Participation | undefined>;
    subscribeForLatestResultOfParticipation: (participationId: number, personal: boolean, exerciseId?: number) => BehaviorSubject<Result | undefined>;
    unsubscribeForLatestResultOfParticipation: (participationId: number, exercise: Exercise) => void;
    notifyAllResultSubscribers: (result: Result) => void;
}

@Injectable({ providedIn: 'root' })
export class ParticipationWebsocketService implements IParticipationWebsocketService {
    cachedParticipations: Map<number /* ID of participation */, StudentParticipation> = new Map<number, StudentParticipation>();
    openResultWebsocketSubscriptions: Map<number /*ID of participation */, string /* url of websocket connection */> = new Map<number, string>();
    openPersonalWebsocketSubscription?: string; /* url of websocket connection */
    resultObservables: Map<number /* ID of participation */, BehaviorSubject<Result | undefined>> = new Map<number, BehaviorSubject<Result>>();
    participationObservable?: BehaviorSubject<Participation | undefined>;
    subscribedExercises: Map<number /* ID of exercise */, Set<number> /* IDs of the participations of this exercise */> = new Map<number, Set<number>>();
    participationSubscriptionTypes: Map<number /* ID of participation */, boolean /* Whether the participation was subscribed in personal mode */> = new Map<number, boolean>();

    constructor(private jhiWebsocketService: JhiWebsocketService, private participationService: ParticipationService) {}

    private getNotifyAllSubscribersPipe = () => {
        return pipe(tap(this.notifyResultSubscribers), switchMap(this.addResultToParticipation), tap(this.notifyParticipationSubscribers));
    };

    /**
     * remove all local participations
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
     * @param participation
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
     * @param result
     */
    private notifyResultSubscribers = (result: Result) => {
        const resultObservable = this.resultObservables.get(result.participation!.id!);
        // TODO: We never convert the date strings of the result (e.g. completionDate) to a Dayjs object
        //  this could be an issue in some parts of app when a formatted date is needed.
        if (!resultObservable) {
            this.resultObservables.set(result.participation!.id!, new BehaviorSubject(result));
        } else {
            resultObservable.next(result);
        }
    };

    /**
     * Update a cachedParticipation with the given result, meaning that the new result will be added to it.
     * @param result
     */
    private addResultToParticipation = (result: Result) => {
        const cachedParticipation = this.cachedParticipations.get(result.participation!.id!);
        if (cachedParticipation) {
            // update the results with the new received one by filtering the old result
            const updatedResults = [...(cachedParticipation.results || [])].filter((r) => r.id !== result.id);
            updatedResults.push(result);
            // create a clone
            this.cachedParticipations.set(result.participation!.id!, { ...cachedParticipation, results: updatedResults } as StudentParticipation);
            return of(this.cachedParticipations.get(result.participation!.id!));
        }
        return of();
    };

    /**
     * This adds a participation to the cached data maps. The exercise information is required to find the correct
     * participations for a given exercise. Please note: we explicitly do not want to use websockets here!
     *
     * @param newParticipation The new participation for the cached data maps
     * @param exercise (optional) The exercise that the participation belongs to. Only needed if exercise is missing in participation.
     */
    public addParticipation = (newParticipation: StudentParticipation, exercise?: Exercise) => {
        // The participation needs to be cloned so that the original object is not modified
        const participation = { ...newParticipation } as StudentParticipation;
        if (!participation.exercise && !exercise) {
            throw new Error('a link from the participation to the exercise is required. Please attach it manually or add exercise as function input');
        }
        participation.exercise = participation.exercise || exercise;
        this.cachedParticipations.set(participation.id!, participation);
        this.notifyParticipationSubscribers(participation);
    };

    /**
     * Returns all participations for all exercises. The participation objects include the exercise data and all results.
     * @return array of Participations
     */
    private getAllParticipations(): StudentParticipation[] {
        return [...this.cachedParticipations.values()];
    }

    /**
     * Returns the student participation for the given exercise. The participation objects include the exercise data and all results.
     *
     * @param exerciseId ID of the exercise that the participations belong to.
     * @return the cached student participation for the exercise or undefined
     */
    public getParticipationForExercise(exerciseId: number) {
        const participationsForExercise = [...this.cachedParticipations.values()].filter((participation) => {
            return participation.exercise?.id === exerciseId;
        });
        if (participationsForExercise && participationsForExercise.length === 1) {
            return participationsForExercise[0];
        }
        if (participationsForExercise && participationsForExercise.length > 1) {
            return this.participationService.mergeStudentParticipations(participationsForExercise);
        }
        return undefined;
    }

    /**
     * Unsubscribes from the topics used by the participationId, if possible
     *
     * @param participationId ID of the participation that should not be tracked anymore
     * @param exerciseId optional the participationId an exercise that should not be tracked anymore
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
                    this.jhiWebsocketService.unsubscribe(PERSONAL_PARTICIPATION_TOPIC);
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
                            this.jhiWebsocketService.unsubscribe(subscribedTopic);
                            this.openResultWebsocketSubscriptions.delete(exerciseId!);
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if a websocket connection for new results to the server already exists.
     * If not a new one will be opened.
     *
     * @param participationId the id of the participation for which the subscription should be opened
     * @param personal whether the current user is a participant in the participation.
     * @param exerciseId optional exerciseId of the exercise where the participation is part of, only needed if personal == false
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

            this.jhiWebsocketService.subscribe(participationResultTopic);
            this.jhiWebsocketService.receive(participationResultTopic).pipe(this.getNotifyAllSubscribersPipe()).subscribe();
        }
    }

    /**
     * Notifies the result and participation subscribers with the newest result.
     * Note: the result must contain the participation id
     *
     * @param result The result with which the subscribers get notified
     */
    public notifyAllResultSubscribers = (result: Result) => {
        of(result).pipe(this.getNotifyAllSubscribersPipe()).subscribe();
    };

    /**
     * Subscribing for general changes in a participation object. This will triggered if a new result is received by the service.
     * A received object will be the full participation object including all results and the exercise.
     *
     * If no observable exists a new one will be created.
     */
    public subscribeForParticipationChanges(): BehaviorSubject<Participation | undefined> {
        if (!this.participationObservable) {
            this.participationObservable = new BehaviorSubject<Participation | undefined>(undefined);
        }
        return this.participationObservable;
    }

    /**
     * Subscribing to new results of a certain participation. This will be triggered if a new result is received by the service.
     * A received Object will be a result object.
     *
     * If there is no observable for the participation a new one will be created.
     *
     * @param participationId Id of Participation of which result to subscribe to
     * @param personal whether the current user is a participant in the participation.
     * @param exerciseId optional exerciseId of the exercise where the participation is part of, only needed if personal == false
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
     * Unsubscribe from the result
     * @param participationId
     * @param exercise The exercise to which the participationId belongs to. Needed for deciding whether to unsubscribe from the websocket
     */
    public unsubscribeForLatestResultOfParticipation(participationId: number, exercise: Exercise): void {
        // Only unsubscribe from websocket, if the exercise is not active any more
        let isInactiveProgrammingExercise = false;
        if (exercise instanceof ProgrammingExercise) {
            const programmingExercise = exercise as ProgrammingExercise;
            isInactiveProgrammingExercise =
                !!programmingExercise.buildAndTestStudentSubmissionsAfterDueDate && dayjs(programmingExercise.buildAndTestStudentSubmissionsAfterDueDate).isBefore(dayjs());
        }
        if (isInactiveProgrammingExercise || (exercise.dueDate && dayjs(exercise.dueDate).isBefore(dayjs()))) {
            this.removeParticipation(participationId, exercise.id);
        }
    }
}
