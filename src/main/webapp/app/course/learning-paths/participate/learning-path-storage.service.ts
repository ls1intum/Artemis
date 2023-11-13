import { Injectable } from '@angular/core';
import { NgxLearningPathDTO, NodeType } from 'app/entities/competency/learning-path.model';

/**
 * This service is used to store the recommendations of learning path participation for the currently logged-in user.
 */
@Injectable({ providedIn: 'root' })
export class LearningPathStorageService {
    private readonly learningPathRecommendations: Map<number, StorageEntry[]> = new Map();

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
     * Gets all recommendations of the learning path in recommended order
     *
     * @param learningPathId the id of the learning path
     */
    getRecommendations(learningPathId: number) {
        return this.learningPathRecommendations.get(learningPathId);
    }

    /**
     * Gets if the given learning object has a successor.
     *
     * @param learningPathId the id of the learning path
     * @param entry the entry for which the successor should be checked
     */
    hasNextRecommendation(learningPathId: number, entry?: StorageEntry): boolean {
        if (!this.learningPathRecommendations.has(learningPathId)) {
            return false;
        }
        if (!entry) {
            return !!this.learningPathRecommendations.get(learningPathId)?.length;
        }
        const index = this.getIndexOf(learningPathId, entry);
        return 0 <= index && index + 1 < this.learningPathRecommendations.get(learningPathId)!.length;
    }

    /**
     * Gets the next recommended entry for a learning object.
     * <p>
     * First entry, if given entry undefined.
     * Undefined if the current entry has no successor.
     * @param learningPathId the id of the learning path
     * @param entry the entry for which the successor should be returned
     */
    getNextRecommendation(learningPathId: number, entry?: StorageEntry): StorageEntry | undefined {
        if (!this.hasNextRecommendation(learningPathId, entry)) {
            return undefined;
        }
        if (!entry) {
            return this.learningPathRecommendations.get(learningPathId)![0];
        }
        const nextIndex = this.getIndexOf(learningPathId, entry) + 1;
        return this.learningPathRecommendations.get(learningPathId)![nextIndex];
    }

    /**
     * Gets if the given learning object has a predecessor.
     *
     * @param learningPathId the id of the learning path
     * @param entry the entry for which the predecessor should be checked
     */
    hasPrevRecommendation(learningPathId: number, entry?: StorageEntry): boolean {
        if (!this.learningPathRecommendations.has(learningPathId) || !entry) {
            return false;
        }
        return 0 < this.getIndexOf(learningPathId, entry);
    }

    /**
     * Gets the prior recommended entry for a learning object.
     * <p>
     * Undefined if the current entry has no predecessor.
     * @param learningPathId the id of the learning path
     * @param entry the entry for which the predecessor should be returned
     */
    getPrevRecommendation(learningPathId: number, entry?: StorageEntry): StorageEntry | undefined {
        if (!this.hasPrevRecommendation(learningPathId, entry)) {
            return undefined;
        }
        const prevIndex = this.getIndexOf(learningPathId, entry!) - 1;
        return this.learningPathRecommendations.get(learningPathId)![prevIndex];
    }

    private getIndexOf(learningPathId: number, entry: StorageEntry) {
        if (!this.learningPathRecommendations.has(learningPathId)) {
            return -1;
        }
        return this.learningPathRecommendations.get(learningPathId)!.findIndex((e: StorageEntry) => {
            return entry.equals(e);
        });
    }
}

export abstract class StorageEntry {
    abstract equals(other: StorageEntry): boolean;
}

export class LectureUnitEntry extends StorageEntry {
    readonly lectureUnitId: number;
    readonly lectureId: number;

    constructor(lectureId: number, lectureUnitId: number) {
        super();
        this.lectureId = lectureId;
        this.lectureUnitId = lectureUnitId;
    }

    equals(other: StorageEntry): boolean {
        if (other instanceof LectureUnitEntry) {
            return this.lectureId === other.lectureId && this.lectureUnitId === other.lectureUnitId;
        }
        return false;
    }
}

export class ExerciseEntry extends StorageEntry {
    readonly exerciseId: number;

    constructor(exerciseId: number) {
        super();
        this.exerciseId = exerciseId;
    }

    equals(other: StorageEntry): boolean {
        if (other instanceof ExerciseEntry) {
            return this.exerciseId === other.exerciseId;
        }
        return false;
    }
}
