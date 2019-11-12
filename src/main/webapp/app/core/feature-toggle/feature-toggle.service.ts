import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';
import { JhiWebsocketService } from 'app/core';
import { tap } from 'rxjs/operators';

export enum FeatureToggleState {
    PROGRAMMING_EXERCISES = 'PROGRAMMING_EXERCISES',
}
export type ActiveFeatures = Array<FeatureToggleState>;

const defaultActiveFeatureState: ActiveFeatures = Object.values(FeatureToggleState);

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

    getFeatureToggles() {
        return this.subject.asObservable();
    }
}
