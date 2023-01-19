import { Injectable } from '@angular/core';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import dayjs from 'dayjs/esm';

@Injectable({ providedIn: 'root' })
export class ExerciseCacheService {
    constructor(private exerciseService: ExerciseService) {}

    latestDueDateByExerciseId: Map<number, dayjs.Dayjs> = new Map();

    public getLatestDueDate(exerciseId: number): dayjs.Dayjs | undefined {
        if (!this.latestDueDateByExerciseId.has(exerciseId)) {
            this.exerciseService.getLatestDueDate(exerciseId).subscribe((latestDueDate) => {
                if (latestDueDate) {
                    this.latestDueDateByExerciseId.set(exerciseId, latestDueDate);
                }
            });
        }

        return this.latestDueDateByExerciseId.get(exerciseId);
    }
}
