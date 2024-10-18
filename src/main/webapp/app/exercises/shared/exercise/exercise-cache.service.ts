import { Injectable, inject } from '@angular/core';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import dayjs from 'dayjs/esm';
import { Observable, of, tap } from 'rxjs';

@Injectable()
export class ExerciseCacheService {
    private exerciseService = inject(ExerciseService);

    latestDueDateByExerciseId: Map<number, dayjs.Dayjs> = new Map();

    public getLatestDueDate(exerciseId: number): Observable<dayjs.Dayjs | undefined> {
        const cachedLatestDueDate = this.latestDueDateByExerciseId.get(exerciseId);
        if (cachedLatestDueDate) {
            return of(cachedLatestDueDate);
        }

        return this.exerciseService.getLatestDueDate(exerciseId).pipe(
            tap((latestDueDate) => {
                if (latestDueDate) {
                    this.latestDueDateByExerciseId.set(exerciseId, latestDueDate);
                }
            }),
        );
    }
}
