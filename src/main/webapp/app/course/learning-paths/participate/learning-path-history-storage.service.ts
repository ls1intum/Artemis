import { Injectable } from '@angular/core';

/**
 * This service is used to store the histories of learning path participation for the currently logged-in user.
 */
@Injectable({ providedIn: 'root' })
export class LearningPathHistoryStorageService {
    private readonly learningPathHistories: Map<number, HistoryEntry[]> = new Map();

    /**
     * Stores the lecture unit in the learning path's history.
     *
     * @param learningPathId the id of the learning path to which the new entry should be added
     * @param lectureId the id of the lecture, the lecture unit belongs to
     * @param lectureUnitId the id of the lecture unit
     */
    storeLectureUnit(learningPathId: number, lectureId: number, lectureUnitId: number) {
        this.store(learningPathId, new LectureUnitEntry(lectureId, lectureUnitId));
    }

    /**
     * Stores the exercise in the learning path's history.
     *
     * @param learningPathId the id of the learning path to which the new entry should be added
     * @param exerciseId the id of the exercise
     */
    storeExercise(learningPathId: number, exerciseId: number) {
        this.store(learningPathId, new ExerciseEntry(exerciseId));
    }

    private store(learningPathId: number, entry: HistoryEntry) {
        if (!entry) {
            return;
        }
        if (!this.learningPathHistories.has(learningPathId)) {
            this.learningPathHistories.set(learningPathId, []);
        }
        this.learningPathHistories.get(learningPathId)!.push(entry);
    }

    /**
     * Returns if the learning path's history stores at least one entry.
     *
     * @param learningPathId the id of the learning path for which the history should be checked
     */
    hasPrevious(learningPathId: number): boolean {
        if (this.learningPathHistories.has(learningPathId)) {
            return this.learningPathHistories.get(learningPathId)!.length !== 0;
        }
        return false;
    }

    /**
     * Gets and removes the latest stored entry from the learning path's history.
     *
     * @param learningPathId
     */
    getPrevious(learningPathId: number) {
        if (!this.hasPrevious(learningPathId)) {
            return undefined;
        }
        return this.learningPathHistories.get(learningPathId)!.pop();
    }
}

export abstract class HistoryEntry {}

export class LectureUnitEntry extends HistoryEntry {
    lectureUnitId: number;
    lectureId: number;

    constructor(lectureId: number, lectureUnitId: number) {
        super();
        this.lectureId = lectureId;
        this.lectureUnitId = lectureUnitId;
    }
}

export class ExerciseEntry extends HistoryEntry {
    readonly exerciseId: number;

    constructor(exerciseId: number) {
        super();
        this.exerciseId = exerciseId;
    }
}
