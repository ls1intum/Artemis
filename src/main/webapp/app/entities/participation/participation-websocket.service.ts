import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

import { Participation } from './participation.model';
import { JhiWebsocketService } from 'app/core';
import { Result } from 'app/entities/result';
import { Exercise } from 'app/entities/exercise';

const RESULTS_WEBSOCKET = 'results_';
const PARTICIPATION_WEBSOCKET = 'participation_';

@Injectable({ providedIn: 'root' })
export class ParticipationWebsocketService {
    cachedParticipations: Map<number, Participation> = new Map<number, Participation>();
    openWebsocketConnections: Map<string, string> = new Map<string, string>();
    resultObservables: Map<number, BehaviorSubject<Result>> = new Map<number, BehaviorSubject<Result>>();
    participationObservable: BehaviorSubject<Participation>;

    constructor(private jhiWebsocketService: JhiWebsocketService) {}

    setCachedParticipation(participations: Participation[], exercise?: Exercise) {
        participations.forEach(participation => {
            this.addParticipationToList(participation, exercise);
        });
    }

    updateParticipation(participation: Participation, exercise?: Exercise) {
        this.addParticipationToList(participation, exercise);
    }

    addParticipation(participation: Participation, exercise?: Exercise) {
        this.addParticipationToList(participation, exercise);
    }

    /**
     * This adds a participation to the cached data maps. The exercise information is required to find the correct
     * participations for a given exercise.
     *
     * @param participation The new participation for the cached data maps
     * @param exercise (optional) The exercise that the participation belongs to. Only needed if exercise is missing in participation.
     * @private
     */
    private addParticipationToList(participation: Participation, exercise?: Exercise) {
        if (!participation.exercise && !exercise) {
            throw new Error('a link from the participation to the exercise is required. Please attach it manually or add exercise as function input');
        }
        participation.exercise = participation.exercise || exercise;
        this.cachedParticipations.set(participation.id, participation);
        this.checkWebsocketConnection(participation);
    }

    /**
     * Returns all participations for all exercises. The participation objects include the exercise data and all results.
     * @return array of Participations
     */
    getAllParticipations(): Participation[] {
        return [...this.cachedParticipations.values()];
    }

    /**
     * Returns all participation for the given exercise. The participation objects include the exercise data and all results.
     *
     * @param exerciseId ID of the exercise that the participations belong to.
     * @return array of Participations
     */
    getAllParticipationsForExercise(exerciseId: number): Participation[] {
        return [...this.cachedParticipations.values()].filter(participation => {
            return participation.exercise.id === exerciseId;
        });
    }

    /**
     * Removes all participation information locally from all cached data maps.
     *
     * @param id ID of the participation that should not be tracked anymore
     */
    removeParticipation(id: number) {
        this.cachedParticipations.delete(id);
        // removing results observable
        const participationResultTopic = this.openWebsocketConnections.get(`${RESULTS_WEBSOCKET}${id}`);
        this.jhiWebsocketService.unsubscribe(participationResultTopic);
        this.openWebsocketConnections.delete(`${RESULTS_WEBSOCKET}${id}`);
        // removing participation observable
        const participationTopic = this.openWebsocketConnections.get(`${PARTICIPATION_WEBSOCKET}${id}`);
        this.jhiWebsocketService.unsubscribe(participationTopic);
        this.openWebsocketConnections.delete(`${PARTICIPATION_WEBSOCKET}${id}`);
    }

    /**
     * Checks for the given participation all necessary websocket connections to the server already exists.
     *
     * @param participation Participation object that has to be checked
     * @private
     */
    private checkWebsocketConnection(participation: Participation) {
        this.checkWebsocketConnectionForNewResults(participation);
        this.checkWebsocketConnectionForNewParticipations(participation);
    }

    /**
     * Checks for the given participation if a websocket connection for new results to the server already exists.
     * If not a new one will be opened.
     *
     * @param participation Participation object that has to be checked
     * @private
     */
    private checkWebsocketConnectionForNewResults(participation: Participation) {
        if (!this.openWebsocketConnections.get(`${RESULTS_WEBSOCKET}${participation.id}`)) {
            const participationResultTopic = `/topic/participation/${participation.id}/newResults`;
            this.jhiWebsocketService.subscribe(participationResultTopic);
            const participationResultObservable = this.jhiWebsocketService.receive(participationResultTopic);
            participationResultObservable.subscribe((result: Result) => {
                this.addResultToParticipation(result);
            });
            this.openWebsocketConnections.set(`${RESULTS_WEBSOCKET}${participation.id}`, participationResultTopic);
        }
    }

    /**
     * Checks for the given participation if a websocket connection for new participations to the server already exists.
     * If not a new one will be opened.
     *
     * @param participation Participation object that has to be checked
     * @private
     */
    private checkWebsocketConnectionForNewParticipations(participation: Participation) {
        if (!this.openWebsocketConnections.get(`${PARTICIPATION_WEBSOCKET}${participation.id}`)) {
            const participationResultTopic = `/user/topic/quizExercise/${participation.exercise.id}/participation`;
            this.jhiWebsocketService.subscribe(participationResultTopic);
            const participationObservable = this.jhiWebsocketService.receive(participationResultTopic);
            participationObservable.subscribe((participationMessage: Participation) => {
                this.cachedParticipations.set(participationMessage.id, participationMessage);
                this.participationObservable.next(participationMessage);
            });
            this.openWebsocketConnections.set(`${PARTICIPATION_WEBSOCKET}${participation.id}`, participationResultTopic);
        }
    }

    getParticipation(id: number): Participation {
        return this.cachedParticipations.get(id);
    }

    /**
     * This adds newly received results to the corresponding participation. Then all listeners for the
     * participationObservable will be notified with the complete participation object.
     *
     * @param result Newly received result object from the websocket message
     * @private
     */
    private addResultToParticipation(result: Result) {
        const correspondingParticipation = this.cachedParticipations.get(result.participation.id);
        correspondingParticipation.results.push(result);
        this.cachedParticipations.set(correspondingParticipation.id, correspondingParticipation);
        this.participationObservable.next(correspondingParticipation);
    }

    /**
     * Subscribing for general changes in a participation object. This will triggered if a new result is received by the service.
     * A received object will be the full participation object including all results and the exercise.
     *
     * If no observable exists a new one will be created.
     */
    subscribeForParticipationChanges(): BehaviorSubject<Participation> {
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
     * @param participationId ID of the participation that the new results should belong to
     */
    subscribeForLatestResultOfParticipation(participationId: number): BehaviorSubject<Result> {
        let resultObservable = this.resultObservables.get(participationId);
        if (!resultObservable) {
            resultObservable = new BehaviorSubject<Result>(null);
            this.resultObservables.set(participationId, resultObservable);
        }
        return resultObservable;
    }
}
