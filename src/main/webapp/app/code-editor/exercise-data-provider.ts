import { Injectable } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

@Injectable({ providedIn: 'root' })
export class ExerciseDataProvider {
    public exerciseDataStorage: ProgrammingExercise;

    public constructor() {}
}
