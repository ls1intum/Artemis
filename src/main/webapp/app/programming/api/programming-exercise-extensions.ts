import { HttpResponse } from '@angular/common/http';
import { InjectionToken } from '@angular/core';
import type { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Observable } from 'rxjs';

export type { ProgrammingExercise };

/** Resolved by an external import route before the programming exercise editor opens. */
export interface ProgrammingExerciseImport {
    exercise: ProgrammingExercise;
    save(exercise: ProgrammingExercise, courseId: number): Observable<HttpResponse<ProgrammingExercise>>;
}

export interface ProgrammingExerciseExportAction {
    label: string;
    run(exerciseId: number): void;
}

export const PROGRAMMING_EXERCISE_EXPORT_ACTION = new InjectionToken<Observable<ProgrammingExerciseExportAction | undefined>>('Programming exercise export action');
