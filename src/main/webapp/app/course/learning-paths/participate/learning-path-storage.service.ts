import { Injectable } from '@angular/core';
import { NgxLearningPathDTO, NodeType } from 'app/entities/competency/learning-path.model';

/**
 * This service is used to store the histories and recommendations of learning path participation for the currently logged-in user.
 */
@Injectable({ providedIn: 'root' })
export class LearningPathStorageService {
    private readonly learningPathHistories: Map<number, StorageEntry[]> = new Map();
    private readonly learningPathRecommendations: Map<number, StorageEntry[]> = new Map();

    /**
     * Stores the lecture unit in the learning path's history.
     *
     * @param learningPathId the id of the learning path to which the new entry should be added
     * @param lectureId the id of the lecture, the lecture unit belongs to
     * @param lectureUnitId the id of the lecture unit
     * @return the entry that is stored
     */
    storeLectureUnit(learningPathId: number, lectureId: number, lectureUnitId: number) {
        const entry = new LectureUnitEntry(lectureId, lectureUnitId);
        this.store(learningPathId, entry);
        return entry;
    }

    /**
     * Stores the exercise in the learning path's history.
     *
     * @param learningPathId the id of the learning path to which the new entry should be added
     * @param exerciseId the id of the exercise
     * @return the entry that is stored
     */
    storeExercise(learningPathId: number, exerciseId: number) {
        const entry = new ExerciseEntry(exerciseId);
        this.store(learningPathId, entry);
        return entry;
    }
    private store(learningPathId: number, entry: StorageEntry) {
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

    /**
     * Simplifies and stores the recommended order of learning objects for the given learning path
     *
     * @param learningPathId the id of the learning path
     * @param learningPath the learning path dto that should be stored
     */
    storeRecommendations(learningPathId: number, learningPath: NgxLearningPathDTO) {
        this.learningPathRecommendations.set(learningPathId, []);
        let currentId = learningPath.nodes.map((node) => node.id).find((id) => !learningPath.edges.find((edge) => edge.target == id));
        while (currentId) {
            const currentNode = learningPath.nodes.find((node) => node.id == currentId)!;
            if (currentNode.type === NodeType.LECTURE_UNIT) {
                this.learningPathRecommendations.get(learningPathId)!.push(new LectureUnitEntry(currentNode.linkedResourceParent!, currentNode.linkedResource!));
            } else if (currentNode.type === NodeType.EXERCISE) {
                this.learningPathRecommendations.get(learningPathId)!.push(new ExerciseEntry(currentNode.linkedResource!));
            }
            const edge = learningPath.edges.find((edge) => edge.source == currentId);
            if (edge) {
                currentId = edge.target;
            } else {
                currentId = undefined;
            }
        }
    }

    /**
     * Returns if there is a recommendation left that has not been interacted with yet.
     *
     * @param learningPathId the id of the learning path
     */
    hasRecommendation(learningPathId: number) {
        if (this.learningPathRecommendations.has(learningPathId)) {
            return this.learningPathRecommendations.get(learningPathId)!.find((entry) => !entry.interacted) !== undefined;
        }
        return false;
    }

    /**
     * Sets the given entry as interacted with.
     *
     * @param learningPathId the id of the learning path the entry belongs to
     * @param entry the entry that should be set to interacted with
     */
    setInteraction(learningPathId: number, entry: StorageEntry) {
        if (!this.learningPathRecommendations.has(learningPathId)) {
            return;
        }
        const storedEntry = this.getStoredEntry(learningPathId, entry);
        if (storedEntry) {
            storedEntry.interacted = true;
        }
    }

    /**
     * Gets the next recommended entry for a learning object.
     * <p>
     * If the given entry has no successor, the first entry that has not been interacted with will be returned.
     * @param learningPathId the id of the learning path
     * @param entry the entry for which the successor should be returned
     */
    getNextRecommendation(learningPathId: number, entry: StorageEntry | undefined) {
        if (!this.learningPathRecommendations.has(learningPathId)) {
            return undefined;
        }
        if (!entry) {
            return this.learningPathRecommendations.get(learningPathId)!.find((e) => !e.interacted);
        }
        const storedEntry = this.getStoredEntry(learningPathId, entry);
        const nextIndex = this.learningPathRecommendations.get(learningPathId)!.indexOf(storedEntry!) + 1;
        const nextEntry = this.learningPathRecommendations.get(learningPathId)!.find((e, idx) => idx >= nextIndex && !e.interacted);
        if (nextEntry) {
            nextEntry.interacted = true;
        }
        return nextEntry;
    }

    private getStoredEntry(learningPathId: number, entry: StorageEntry) {
        return this.learningPathRecommendations.get(learningPathId)!.find((e) => {
            if (e instanceof LectureUnitEntry && entry instanceof LectureUnitEntry) {
                return e.lectureId === entry.lectureId && e.lectureUnitId === e.lectureUnitId;
            } else if (e instanceof ExerciseEntry && entry instanceof ExerciseEntry) {
                return e.exerciseId === entry.exerciseId;
            }
            return false;
        });
    }
}

export abstract class StorageEntry {
    interacted?: boolean;
}

export class LectureUnitEntry extends StorageEntry {
    lectureUnitId: number;
    lectureId: number;

    constructor(lectureId: number, lectureUnitId: number) {
        super();
        this.lectureId = lectureId;
        this.lectureUnitId = lectureUnitId;
    }
}

export class ExerciseEntry extends StorageEntry {
    readonly exerciseId: number;

    constructor(exerciseId: number) {
        super();
        this.exerciseId = exerciseId;
    }
}
