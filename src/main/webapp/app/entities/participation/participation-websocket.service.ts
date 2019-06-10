import { Injectable } from '@angular/core';
import { BehaviorSubject, of } from 'rxjs';
import { filter, switchMap, tap } from 'rxjs/operators';

import { Participation } from './participation.model';
import { JhiWebsocketService } from 'app/core';
import { Result } from 'app/entities/result';
import { Exercise } from 'app/entities/exercise';

const RESULTS_WEBSOCKET = 'results_';
const PARTICIPATION_WEBSOCKET = 'participation_';

@Injectable({ providedIn: 'root' })
export class ParticipationWebsocketService {
    cachedParticipations: Map<number /* ID of participation */, Participation> = new Map<number, Participation>();
    openWebsocketConnections: Map<string /* results_{participationId} OR participation_{exerciseId} */, string /* url of websocket connection */> = new Map<string, string>();
    resultObservables: Map<number /* ID of participation */, BehaviorSubject<Result>> = new Map<number, BehaviorSubject<Result>>();
    participationObservable: BehaviorSubject<Participation>;

    constructor(private jhiWebsocketService: JhiWebsocketService) {}

    public resetLocalCache() {
        const participations = this.getAllParticipations();
        participations.forEach(participation => {
            this.removeParticipation(participation.id, participation.exercise.id);
        });
        this.cachedParticipations = new Map<number, Participation>();
        this.resultObservables = new Map<number, BehaviorSubject<Result>>();
        this.participationObservable = null;
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
        const resultObservable = this.resultObservables.get(result.participation.id);
        if (!resultObservable) {
            this.resultObservables.set(result.participation.id, new BehaviorSubject(result));
        } else {
            resultObservable.next(result);
        }
    };

    /**
     * Update a cachedParticipation with the given result, meaning that the new result will be added to it.
     * @param result
     */
    private addResultToParticipation = (result: Result) => {
        const cachedParticipation = this.cachedParticipations.get(result.participation.id);
        if (cachedParticipation) {
            this.cachedParticipations.set(result.participation.id, { ...cachedParticipation, results: [...(cachedParticipation.results || []), result] });
            return of(this.cachedParticipations.get(result.participation.id));
        }
        return of();
    };

    /**
     * This adds a participation to the cached data maps. The exercise information is required to find the correct
     * participations for a given exercise.
     *
     * @param newParticipation The new participation for the cached data maps
     * @param exercise (optional) The exercise that the participation belongs to. Only needed if exercise is missing in participation.
     */
    public addParticipation = (newParticipation: Participation, exercise?: Exercise) => {
        // The participation needs to be cloned so that the original object is not modified
        const participation = { ...newParticipation };
        if (!participation.exercise && !exercise) {
            throw new Error('a link from the participation to the exercise is required. Please attach it manually or add exercise as function input');
        }
        participation.exercise = participation.exercise || exercise;
        this.cachedParticipations.set(participation.id, participation);
        this.createResultWSConnectionIfNotExisting(participation.id);
        this.createParticipationWSConnectionIfNotExisting(participation.exercise.id);
    };

    public addExerciseForNewParticipation(exerciseId: number) {
        this.createParticipationWSConnectionIfNotExisting(exerciseId);
    }

    /**
     * Returns all participations for all exercises. The participation objects include the exercise data and all results.
     * @return array of Participations
     */
    private getAllParticipations(): Participation[] {
        return [...this.cachedParticipations.values()];
    }

    /**
     * Returns all participation for the given exercise. The participation objects include the exercise data and all results.
     *
     * @param exerciseId ID of the exercise that the participations belong to.
     * @return array of Participations
     */
    public getAllParticipationsForExercise(exerciseId: number): Participation[] {
        return [...this.cachedParticipations.values()].filter(participation => {
            return participation.exercise.id === exerciseId;
        });
    }

    /**
     * Removes all participation information locally from all cached data maps.
     *
     * @param id ID of the participation that should not be tracked anymore
     * @param exerciseId optional the id an exercise that should not be tracked anymore
     */
    private removeParticipation(id: number, exerciseId?: number) {
        this.cachedParticipations.delete(id);
        // removing results observable
        const participationResultTopic = this.openWebsocketConnections.get(`${RESULTS_WEBSOCKET}${id}`);
        this.jhiWebsocketService.unsubscribe(participationResultTopic);
        this.openWebsocketConnections.delete(`${RESULTS_WEBSOCKET}${id}`);
        // removing exercise observable
        if (exerciseId) {
            const participationTopic = this.openWebsocketConnections.get(`${PARTICIPATION_WEBSOCKET}${exerciseId}`);
            this.jhiWebsocketService.unsubscribe(participationTopic);
            this.openWebsocketConnections.delete(`${PARTICIPATION_WEBSOCKET}${exerciseId}`);
        }
    }

    /**
     * Checks for the given participation if a websocket connection for new results to the server already exists.
     * If not a new one will be opened.
     *
     * @param participationId
     * @private
     */
    private createResultWSConnectionIfNotExisting(participationId: number) {
        if (!this.openWebsocketConnections.get(`${RESULTS_WEBSOCKET}${participationId}`)) {
            const participationResultTopic = `/topic/participation/${participationId}/newResults`;
            this.jhiWebsocketService.subscribe(participationResultTopic);
            this.openWebsocketConnections.set(`${RESULTS_WEBSOCKET}${participationId}`, participationResultTopic);
            this.jhiWebsocketService
                .receive(participationResultTopic)
                .pipe(
                    tap(this.notifyResultSubscribers),
                    switchMap(this.addResultToParticipation),
                    tap(this.notifyParticipationSubscribers),
                )
                .subscribe();
        }
    }

    /**
     * Checks for the given exercise if a websocket connection for new participations to the server already exists.
     * If not a new one will be opened.
     *
     * @param exerciseId
     * @private
     */
    private createParticipationWSConnectionIfNotExisting(exerciseId: number) {
        if (!this.openWebsocketConnections.get(`${PARTICIPATION_WEBSOCKET}${exerciseId}`)) {
            const participationTopic = `/user/topic/exercise/${exerciseId}/participation`;
            this.jhiWebsocketService.subscribe(participationTopic);
            this.openWebsocketConnections.set(`${PARTICIPATION_WEBSOCKET}${exerciseId}`, participationTopic);
            this.jhiWebsocketService
                .receive(participationTopic)
                .pipe(
                    tap(this.addParticipation),
                    tap(this.notifyParticipationSubscribers),
                )
                .subscribe();
        }
    }

    /**
     * Subscribing for general changes in a participation object. This will triggered if a new result is received by the service.
     * A received object will be the full participation object including all results and the exercise.
     *
     * If no observable exists a new one will be created.
     */
    public subscribeForParticipationChanges(): BehaviorSubject<Participation> {
        if (!this.participationObservable) {
            this.participationObservable = new BehaviorSubject<Participation>(null);
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
     * @param exercise Exercise to which the Participation belongs
     */
    public subscribeForLatestResultOfParticipation(participationId: number): BehaviorSubject<Result> {
        this.createResultWSConnectionIfNotExisting(participationId);
        let resultObservable = this.resultObservables.get(participationId);
        if (!resultObservable) {
            resultObservable = new BehaviorSubject<Result>(null);
            this.resultObservables.set(participationId, resultObservable);
        }
        return resultObservable;
    }
}
