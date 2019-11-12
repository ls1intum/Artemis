import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { tap, distinctUntilChanged, filter, map } from 'rxjs/operators';

export enum FeatureToggle {
    PROGRAMMING_EXERCISES = 'PROGRAMMING_EXERCISES',
}
export type ActiveFeatures = Array<FeatureToggle>;

const defaultActiveFeatureState: ActiveFeatures = Object.values(FeatureToggle);

@Injectable({ providedIn: 'root' })
export class FeatureToggleService {
    private readonly topic = `/topic/management/features`;
    private subject: BehaviorSubject<ActiveFeatures>;

    constructor(private websocketService: JhiWebsocketService) {
        this.subject = new BehaviorSubject<ActiveFeatures>(defaultActiveFeatureState);
    }

    private subscribeFeatureToggleUpdates() {
        this.websocketService.subscribe(this.topic);
        this.websocketService
            .receive(this.topic)
            .pipe(tap(activeFeatures => this.notifySubscribers(activeFeatures)))
            .subscribe();
    }

    private notifySubscribers(activeFeatures: ActiveFeatures) {
        this.subject.next(activeFeatures);
    }

    setFeatureToggles(activeFeatures: ActiveFeatures) {
        this.notifySubscribers(activeFeatures);
    }

    getFeatureToggles() {
        return this.subject.asObservable().pipe(distinctUntilChanged());
    }

    getFeatureToggleActive(feature: FeatureToggle) {
        return this.subject.asObservable().pipe(
            map(activeFeatures => activeFeatures.includes(feature)),
            distinctUntilChanged(),
        );
    }
}
