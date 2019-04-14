import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

import { Participation } from './participation.model';
import { JhiWebsocketService } from 'app/core';
import { Result } from 'app/entities/result';
import { Exercise } from 'app/entities/exercise';

@Injectable({ providedIn: 'root' })
export class ParticipationWebsocketService {
    cachedParticipations: Map<number, Participation> = new Map<number, Participation>();
    openWebsocketConnections: Map<number, string> = new Map<number, string>();
    resultObservables: Map<number, BehaviorSubject<Result>> = new Map<number, BehaviorSubject<Result>>();
    participationObserver: BehaviorSubject<Participation>;

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

    addParticipationToList(participation: Participation, exercise?: Exercise) {
        if (!participation.exercise && !exercise) {
            throw new Error('a link from the participation to the exercise is required. Please attach it manually or add exercise as function input');
        }
        participation.exercise = participation.exercise || exercise;
        this.cachedParticipations.set(participation.id, participation);
        this.checkWebsocketConnection(participation);
    }

    getAllParticipations(): Participation[] {
        return [...this.cachedParticipations.values()];
    }

    getAllParticipationsForExercise(exerciseId: number): Participation[] {
        return [...this.cachedParticipations.values()].filter(participation => {
            return participation.exercise.id === exerciseId;
        });
    }

    removeParticipation(id: number) {
        this.cachedParticipations.delete(id);
        const participationResultTopic = this.openWebsocketConnections.get(id);
        this.jhiWebsocketService.unsubscribe(participationResultTopic);
        this.openWebsocketConnections.delete(id);
    }

    checkWebsocketConnection(participation: Participation) {
        if (!this.openWebsocketConnections.get(participation.id)) {
            const participationResultTopic = `/topic/participation/${participation.id}/newResults`;
            this.jhiWebsocketService.subscribe(participationResultTopic);
            const participationResultObservable = this.jhiWebsocketService.receive(participationResultTopic);
            participationResultObservable.subscribe((result: Result) => {
                this.addResultToParticipation(result);
            });
            this.openWebsocketConnections.set(participation.id, participationResultTopic);
        }
    }

    getParticipation(id: number): Participation {
        return this.cachedParticipations.get(id);
    }

    addResultToParticipation(result: Result) {
        const correspondingParticipation = this.cachedParticipations.get(result.participation.id);
        correspondingParticipation.results.push(result);
        this.cachedParticipations.set(correspondingParticipation.id, correspondingParticipation);
        this.participationObserver.next(correspondingParticipation);
    }

    subscribeForParticipationChanges(): BehaviorSubject<Participation> {
        if (!this.participationObserver) {
            this.participationObserver = new BehaviorSubject<Participation>(null);
        }
        return this.participationObserver;
    }

    subscribeForLatestResultOfParticipation(participationId: number): BehaviorSubject<Result> {
        let resultObservable = this.resultObservables.get(participationId);
        if (!resultObservable) {
            resultObservable = new BehaviorSubject<Result>(null);
            this.resultObservables.set(participationId, resultObservable);
        }
        return resultObservable;
    }
}
