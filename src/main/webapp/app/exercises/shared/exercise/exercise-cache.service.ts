import { Injectable } from '@angular/core';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import dayjs from 'dayjs/esm';
import { Observable, mergeMap, of, tap } from 'rxjs';

@Injectable()
export class ExerciseCacheService {
    constructor(private exerciseService: ExerciseService) {}

    latestDueDateByExerciseId: Map<number, dayjs.Dayjs> = new Map();

    public getLatestDueDate(exerciseId: number): Observable<dayjs.Dayjs | undefined> {
        return of(this.latestDueDateByExerciseId.get(exerciseId)).pipe(
            mergeMap(
                // Use cached value if it exists and call server otherwise
                (cachedLatestDueDate) => (cachedLatestDueDate ? of(cachedLatestDueDate) : this.exerciseService.getLatestDueDate(exerciseId)),
            ),
            tap((latestDueDate) => {
                if (latestDueDate) {
                    this.latestDueDateByExerciseId.set(exerciseId, latestDueDate);
                }
            }),
        );
    }
}
