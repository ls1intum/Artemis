import { Injectable } from '@angular/core';
import { BehaviorSubject, of, pipe } from 'rxjs';
import { switchMap, tap } from 'rxjs/operators';
import { Participation } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import dayjs from 'dayjs/esm';
import { cloneDeep } from 'lodash-es';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { SelfLearningFeedbackRequest } from 'app/entities/self-learning-feedback-request.model';
import { convertDateFromServer } from 'app/utils/date.utils';

const PERSONAL_PARTICIPATION_TOPIC_PREFIX = `/user/topic`;
const RESULTS_SUFFIX = `/newResults`;
const SELF_LEARNING_FEEDBACK_SUFFIX = `/newSelfLearningFeedback`;

const EXERCISE_PARTICIPATION_TOPIC_PREFIX = (exerciseId: number) => `/topic/exercise/${exerciseId}`;

export interface IParticipationWebsocketService {
    addParticipation: (participation: Participation, exercise?: Exercise) => void;
    getParticipationsForExercise: (exerciseId: number) => StudentParticipation[] | undefined;
    subscribeForParticipationChanges: () => BehaviorSubject<Participation | undefined>;
    subscribeForLatestResultsOfParticipation: (participationId: number, personal: boolean, exerciseId?: number) => BehaviorSubject<Result | undefined>;
    unsubscribeForLatestUpdatesOfParticipation: (participationId: number, exercise: Exercise) => void;
    notifyAllResultSubscribers: (result: Result) => void;
}

@Injectable({ providedIn: 'root' })
export class ParticipationWebsocketService implements IParticipationWebsocketService {
    cachedParticipations: Map<number /* ID of participation */, StudentParticipation> = new Map<number, StudentParticipation>();
    openParticipationWebsocketSubscriptionsPrefix: Map<number /*ID of participation */, string /* prefix of url of websocket connections */> = new Map<number, string>();
    openPersonalWebsocketSubscription?: string; /* prefix of url of websocket connections */
    resultObservables: Map<number /* ID of participation */, BehaviorSubject<Result | undefined>> = new Map<number, BehaviorSubject<Result>>();
    selfLearningFeedbackObservables: Map<number /* ID of participation */, BehaviorSubject<SelfLearningFeedbackRequest | undefined>> = new Map<
        number,
        BehaviorSubject<SelfLearningFeedbackRequest>
    >();
    participationObservable?: BehaviorSubject<Participation | undefined>;
    subscribedExercises: Map<number /* ID of exercise */, Set<number> /* IDs of the participations of this exercise */> = new Map<number, Set<number>>();
    participationSubscriptionTypes: Map<number /* ID of participation */, boolean /* Whether the participation was subscribed in personal mode */> = new Map<number, boolean>();

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private participationService: ParticipationService,
    ) {}

    private getNotifyAllResultSubscribersPipe = () => {
        return pipe(tap(this.notifyResultSubscribers), switchMap(this.addResultToParticipation), tap(this.notifyParticipationSubscribers));
    };

    private getNotifyAllSelfLearningFeedbackSubscribersPipe = () => {
        return pipe(tap(this.notifySelfLearningFeedbackSubscribers), switchMap(this.addSelfLearningFeedbackToParticipation), tap(this.notifyParticipationSubscribers));
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
        this.selfLearningFeedbackObservables = new Map<number, BehaviorSubject<SelfLearningFeedbackRequest>>();
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
        result.completionDate = convertDateFromServer(result.completionDate);
        const resultObservable = this.resultObservables.get(result.participation!.id!);
        if (!resultObservable) {
            this.resultObservables.set(result.participation!.id!, new BehaviorSubject(result));
        } else {
            resultObservable.next(result);
        }
    };

    /**
     * Notify all self-learning-feedback subscribers with the newest information provided.
     * @param selfLearnigFeedback
     */
    private notifySelfLearningFeedbackSubscribers = (selfLearnigFeedback: SelfLearningFeedbackRequest) => {
        selfLearnigFeedback.requestDateTime = convertDateFromServer(selfLearnigFeedback.requestDateTime);
        selfLearnigFeedback.responseDateTime = convertDateFromServer(selfLearnigFeedback.responseDateTime);
        const selfLearnigFeedbackObservable = this.selfLearningFeedbackObservables.get(selfLearnigFeedback.participation!.id!);
        if (!selfLearnigFeedbackObservable) {
            this.selfLearningFeedbackObservables.set(selfLearnigFeedback.participation!.id!, new BehaviorSubject(selfLearnigFeedback));
        } else {
            selfLearnigFeedbackObservable.next(selfLearnigFeedback);
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
     * Update a cachedParticipation with the given self-learning-feedback, meaning that the new information will be added to it.
     * @param selfLearnigFeedback
     */
    private addSelfLearningFeedbackToParticipation = (selfLearnigFeedback: SelfLearningFeedbackRequest) => {
        const cachedParticipation = this.cachedParticipations.get(selfLearnigFeedback.participation!.id!);
        if (cachedParticipation) {
            // update the results with the new received one by filtering the old result
            const updatedselfLearnigFeedbacks = [...(cachedParticipation.results || [])].filter((r) => r.id !== selfLearnigFeedback.id);
            updatedselfLearnigFeedbacks.push(selfLearnigFeedback);
            // create a clone
            this.cachedParticipations.set(selfLearnigFeedback.participation!.id!, {
                ...cachedParticipation,
                selfLearningFeedbackRequests: updatedselfLearnigFeedbacks,
            } as StudentParticipation);
            return of(this.cachedParticipations.get(selfLearnigFeedback.participation!.id!));
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
        const participation = cloneDeep(newParticipation);
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
     * @return the cached student participations separated between testRun and normal participation for the exercise or an empty array
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
                    this.jhiWebsocketService.unsubscribe(PERSONAL_PARTICIPATION_TOPIC_PREFIX + SELF_LEARNING_FEEDBACK_SUFFIX);
                    this.jhiWebsocketService.unsubscribe(PERSONAL_PARTICIPATION_TOPIC_PREFIX + RESULTS_SUFFIX);
                    this.openPersonalWebsocketSubscription = undefined;
                }
            } else {
                // The subscriptions are non-personal subscriptions, so it should only be removed if it was the last for this exercise
                const openSubscriptionsForExercise = this.subscribedExercises.get(exerciseId!);
                if (openSubscriptionsForExercise) {
                    openSubscriptionsForExercise.delete(participationId);
                    if (openSubscriptionsForExercise.size === 0) {
                        this.subscribedExercises.delete(exerciseId!);
                        const subscribedTopicPrefix = this.openParticipationWebsocketSubscriptionsPrefix.get(exerciseId!);
                        if (subscribedTopicPrefix) {
                            this.jhiWebsocketService.unsubscribe(subscribedTopicPrefix + RESULTS_SUFFIX);
                            this.jhiWebsocketService.unsubscribe(subscribedTopicPrefix + SELF_LEARNING_FEEDBACK_SUFFIX);
                            this.openParticipationWebsocketSubscriptionsPrefix.delete(exerciseId!);
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
        if ((personal && !this.openPersonalWebsocketSubscription) || (!personal && !this.openParticipationWebsocketSubscriptionsPrefix.has(exerciseId!))) {
            let participationTopicPrefix: string;
            if (personal) {
                participationTopicPrefix = PERSONAL_PARTICIPATION_TOPIC_PREFIX;
                this.openPersonalWebsocketSubscription = participationTopicPrefix;
            } else {
                participationTopicPrefix = EXERCISE_PARTICIPATION_TOPIC_PREFIX(exerciseId!);
                this.openParticipationWebsocketSubscriptionsPrefix.set(exerciseId!, participationTopicPrefix);
            }
            this.participationSubscriptionTypes.set(participationId, personal);
            if (!this.subscribedExercises.has(exerciseId!)) {
                this.subscribedExercises.set(exerciseId!, new Set<number>());
            }
            const subscribedParticipations = this.subscribedExercises.get(exerciseId!);
            subscribedParticipations!.add(participationId);

            this.jhiWebsocketService.subscribe(participationTopicPrefix + RESULTS_SUFFIX);
            this.jhiWebsocketService.subscribe(participationTopicPrefix + SELF_LEARNING_FEEDBACK_SUFFIX);
            this.jhiWebsocketService
                .receive(participationTopicPrefix + RESULTS_SUFFIX)
                .pipe(this.getNotifyAllResultSubscribersPipe())
                .subscribe();
            this.jhiWebsocketService
                .receive(participationTopicPrefix + SELF_LEARNING_FEEDBACK_SUFFIX)
                .pipe(this.getNotifyAllSelfLearningFeedbackSubscribersPipe())
                .subscribe();
        }
    }

    /**
     * Notifies the result and participation subscribers with the newest result.
     * Note: the result must contain the participation id
     *
     * @param result The result with which the subscribers get notified
     */
    public notifyAllResultSubscribers = (result: Result) => {
        of(result).pipe(this.getNotifyAllResultSubscribersPipe()).subscribe();
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
    public subscribeForLatestResultsOfParticipation(participationId: number, personal: boolean, exerciseId?: number): BehaviorSubject<Result | undefined> {
        this.openResultWebsocketSubscriptionIfNotExisting(participationId, personal, exerciseId);
        let resultObservable = this.resultObservables.get(participationId)!;
        if (!resultObservable) {
            resultObservable = new BehaviorSubject<Result | undefined>(undefined);
            this.resultObservables.set(participationId, resultObservable);
        }
        return resultObservable;
    }

    /**
     * Unsubscribe from the updates of the participation. This will cancel results and self-learning-feedback subscriptions.
     * @param participationId
     * @param exercise The exercise to which the participationId belongs to. Needed for deciding whether to unsubscribe from the websocket
     */
    public unsubscribeForLatestUpdatesOfParticipation(participationId: number, exercise: Exercise): void {
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
