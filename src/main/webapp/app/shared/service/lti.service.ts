import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Exercise } from 'app/entities/exercise.model';

@Injectable({
    providedIn: 'root',
})
export class LtiService {
    private shownViaLtiSubject = new BehaviorSubject<boolean>(false);
    isShownViaLti$ = this.shownViaLtiSubject.asObservable();

    private multiLaunchSubject = new BehaviorSubject<boolean>(false);
    isMultiLaunch$ = this.multiLaunchSubject.asObservable();

    private multiLaunchExercisesSubject = new BehaviorSubject<Exercise[]>([]);
    multiLaunchExercises$ = this.multiLaunchExercisesSubject.asObservable();

    setShownViaLti(shownViaLti: boolean) {
        this.shownViaLtiSubject.next(shownViaLti);
    }

    setMultiLaunch(isMultiLaunch: boolean) {
        this.multiLaunchSubject.next(isMultiLaunch);
    }

    setMultiLaunchExercises(exercises: Exercise[]) {
        this.multiLaunchExercisesSubject.next(exercises);
    }

    getMultiLaunchExercises(): Exercise[] {
        return this.multiLaunchExercisesSubject.value;
    }
}
