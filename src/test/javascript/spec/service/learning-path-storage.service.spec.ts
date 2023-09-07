import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../test.module';
import { ExerciseEntry, LearningPathStorageService, LectureUnitEntry } from 'app/course/learning-paths/participate/learning-path-storage.service';
import { NgxLearningPathDTO, NgxLearningPathEdge, NgxLearningPathNode, NodeType } from 'app/entities/competency/learning-path.model';

describe('LearningPathStorageService', () => {
    let storageService: LearningPathStorageService;
    let learningPathId: number;
    let lectureId: number;
    let lectureUnitId: number;
    let exerciseId: number;
    let ngxPath: NgxLearningPathDTO;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        })
            .compileComponents()
            .then(() => {
                storageService = new LearningPathStorageService();
                learningPathId = 1;
                lectureId = 2;
                lectureUnitId = 3;
                exerciseId = 4;

                ngxPath = {
                    nodes: [
                        { id: '1', type: NodeType.COMPETENCY_START } as NgxLearningPathNode,
                        { id: '2', type: NodeType.LECTURE_UNIT, linkedResource: 5, linkedResourceParent: 6 } as NgxLearningPathNode,
                        { id: '3', type: NodeType.EXERCISE, linkedResource: 7 } as NgxLearningPathNode,
                        { id: '4', type: NodeType.COMPETENCY_END } as NgxLearningPathNode,
                    ],
                    edges: [
                        { id: '8', source: '1', target: '2' } as NgxLearningPathEdge,
                        { id: '9', source: '2', target: '3' } as NgxLearningPathEdge,
                        { id: '10', source: '3', target: '4' } as NgxLearningPathEdge,
                    ],
                } as NgxLearningPathDTO;
            });
    });

    it('should return undefined if no previous is present', () => {
        expect(storageService.hasPrevious(learningPathId)).toBeFalsy();
        const entry = storageService.getPrevious(learningPathId);
        expect(entry).toBeUndefined();
    });

    it('should handle single lecture unit', () => {
        expect(storageService.hasPrevious(learningPathId)).toBeFalsy();
        const stored = storageService.storeLectureUnit(learningPathId, lectureId, lectureUnitId);
        expect(stored).toBeInstanceOf(LectureUnitEntry);
        expect(stored.lectureUnitId).toBe(lectureUnitId);
        expect(stored.lectureId).toBe(lectureId);
        expect(storageService.hasPrevious(learningPathId)).toBeTruthy();
        const entry = storageService.getPrevious(learningPathId);
        expect(entry).not.toBeNull();
        expect(entry).toBeInstanceOf(LectureUnitEntry);
        const lectureUnitEntry = <LectureUnitEntry>entry;
        expect(lectureUnitEntry.lectureId).toBe(lectureId);
        expect(lectureUnitEntry.lectureUnitId).toBe(lectureUnitId);
        expect(storageService.hasPrevious(learningPathId)).toBeFalsy();
    });

    it('should handle single exercise', () => {
        expect(storageService.hasPrevious(learningPathId)).toBeFalsy();
        const stored = storageService.storeExercise(learningPathId, exerciseId);
        expect(stored).toBeInstanceOf(ExerciseEntry);
        expect(stored.exerciseId).toBe(exerciseId);
        expect(storageService.hasPrevious(learningPathId)).toBeTruthy();
        const entry = storageService.getPrevious(learningPathId);
        expect(entry).not.toBeNull();
        expect(entry).toBeInstanceOf(ExerciseEntry);
        const exerciseEntry = <ExerciseEntry>entry;
        expect(exerciseEntry.exerciseId).toBe(exerciseId);
        expect(storageService.hasPrevious(learningPathId)).toBeFalsy();
    });

    it('should handle mixed sequence', () => {
        expect(storageService.hasPrevious(learningPathId)).toBeFalsy();
        storageService.storeExercise(learningPathId, exerciseId);
        expect(storageService.hasPrevious(learningPathId)).toBeTruthy();
        storageService.storeLectureUnit(learningPathId, lectureId, lectureUnitId);
        expect(storageService.hasPrevious(learningPathId)).toBeTruthy();
        storageService.storeExercise(learningPathId, 11);
        expect(storageService.hasPrevious(learningPathId)).toBeTruthy();

        // exercise 2
        let entry = storageService.getPrevious(learningPathId);
        expect(entry).not.toBeNull();
        expect(entry).toBeInstanceOf(ExerciseEntry);
        const exercise2Entry = <ExerciseEntry>entry;
        expect(exercise2Entry.exerciseId).toBe(11);
        expect(storageService.hasPrevious(learningPathId)).toBeTruthy();

        // lecture unit
        entry = storageService.getPrevious(learningPathId);
        expect(entry).not.toBeNull();
        expect(entry).toBeInstanceOf(LectureUnitEntry);
        const lectureUnitEntry = <LectureUnitEntry>entry;
        expect(lectureUnitEntry.lectureId).toBe(lectureId);
        expect(lectureUnitEntry.lectureUnitId).toBe(lectureUnitId);
        expect(storageService.hasPrevious(learningPathId)).toBeTruthy();

        // exercise 1
        entry = storageService.getPrevious(learningPathId);
        expect(entry).not.toBeNull();
        expect(entry).toBeInstanceOf(ExerciseEntry);
        const exerciseEntry = <ExerciseEntry>entry;
        expect(exerciseEntry.exerciseId).toBe(exerciseId);
        expect(storageService.hasPrevious(learningPathId)).toBeFalsy();
    });

    it('should handle multiple learning paths', () => {
        const learningPath2Id = 11;
        expect(storageService.hasPrevious(learningPathId)).toBeFalsy();
        expect(storageService.hasPrevious(learningPath2Id)).toBeFalsy();

        // lecture unit in learningPath(1)
        storageService.storeLectureUnit(learningPathId, lectureId, lectureUnitId);

        expect(storageService.hasPrevious(learningPathId)).toBeTruthy();
        expect(storageService.hasPrevious(learningPath2Id)).toBeFalsy();

        // exercise in learningPath(2)
        storageService.storeExercise(learningPath2Id, exerciseId);

        expect(storageService.hasPrevious(learningPathId)).toBeTruthy();
        expect(storageService.hasPrevious(learningPath2Id)).toBeTruthy();

        let entry = storageService.getPrevious(learningPathId);
        expect(entry).not.toBeNull();
        expect(entry).toBeInstanceOf(LectureUnitEntry);
        const lectureUnitEntry = <LectureUnitEntry>entry;
        expect(lectureUnitEntry.lectureId).toBe(lectureId);
        expect(lectureUnitEntry.lectureUnitId).toBe(lectureUnitId);

        expect(storageService.hasPrevious(learningPathId)).toBeFalsy();
        expect(storageService.hasPrevious(learningPath2Id)).toBeTruthy();

        entry = storageService.getPrevious(learningPath2Id);
        expect(entry).not.toBeNull();
        expect(entry).toBeInstanceOf(ExerciseEntry);
        const exerciseEntry = <ExerciseEntry>entry;
        expect(exerciseEntry.exerciseId).toBe(exerciseId);

        expect(storageService.hasPrevious(learningPathId)).toBeFalsy();
        expect(storageService.hasPrevious(learningPath2Id)).toBeFalsy();
    });

    it('should return first uncompleted if entry not existing', () => {
        storageService.storeRecommendations(learningPathId, ngxPath);
        let expectedEntry = new LectureUnitEntry(6, 5);
        expectedEntry.interacted = true;
        expect(storageService.getNextRecommendation(learningPathId, new LectureUnitEntry(10, 10))).toStrictEqual(expectedEntry);

        storageService.setInteraction(learningPathId, new LectureUnitEntry(6, 5));
        expectedEntry = new ExerciseEntry(7);
        expectedEntry.interacted = true;
        expect(storageService.getNextRecommendation(learningPathId, new LectureUnitEntry(10, 10))).toStrictEqual(expectedEntry);
    });

    it('should return undefined if all recommendations completed', () => {
        storageService.storeRecommendations(learningPathId, ngxPath);
        storageService.setInteraction(learningPathId, new LectureUnitEntry(6, 5));
        storageService.setInteraction(learningPathId, new ExerciseEntry(7));
        expect(storageService.getNextRecommendation(learningPathId, new LectureUnitEntry(10, 10))).toBeFalsy();
    });
});
