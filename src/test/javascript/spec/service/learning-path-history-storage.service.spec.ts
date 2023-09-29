import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../test.module';
import { ExerciseEntry, LearningPathHistoryStorageService, LectureUnitEntry } from 'app/course/learning-paths/participate/learning-path-history-storage.service';

describe('LearningPathHistoryStorageService', () => {
    let historyStorageService: LearningPathHistoryStorageService;
    let learningPathId: number;
    let lectureId: number;
    let lectureUnitId: number;
    let exerciseId: number;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        })
            .compileComponents()
            .then(() => {
                historyStorageService = new LearningPathHistoryStorageService();
                learningPathId = 1;
                lectureId = 2;
                lectureUnitId = 3;
                exerciseId = 4;
            });
    });

    it('should return undefined if no previous is present', () => {
        expect(historyStorageService.hasPrevious(learningPathId)).toBeFalsy();
        const entry = historyStorageService.getPrevious(learningPathId);
        expect(entry).toBeUndefined();
    });

    it('should handle single lecture unit', () => {
        expect(historyStorageService.hasPrevious(learningPathId)).toBeFalsy();
        historyStorageService.storeLectureUnit(learningPathId, lectureId, lectureUnitId);
        expect(historyStorageService.hasPrevious(learningPathId)).toBeTruthy();
        const entry = historyStorageService.getPrevious(learningPathId);
        expect(entry).not.toBeNull();
        expect(entry).toBeInstanceOf(LectureUnitEntry);
        const lectureUnitEntry = <LectureUnitEntry>entry;
        expect(lectureUnitEntry.lectureId).toBe(lectureId);
        expect(lectureUnitEntry.lectureUnitId).toBe(lectureUnitId);
        expect(historyStorageService.hasPrevious(learningPathId)).toBeFalsy();
    });

    it('should handle single exercise', () => {
        expect(historyStorageService.hasPrevious(learningPathId)).toBeFalsy();
        historyStorageService.storeExercise(learningPathId, exerciseId);
        expect(historyStorageService.hasPrevious(learningPathId)).toBeTruthy();
        const entry = historyStorageService.getPrevious(learningPathId);
        expect(entry).not.toBeNull();
        expect(entry).toBeInstanceOf(ExerciseEntry);
        const exerciseEntry = <ExerciseEntry>entry;
        expect(exerciseEntry.exerciseId).toBe(exerciseId);
        expect(historyStorageService.hasPrevious(learningPathId)).toBeFalsy();
    });

    it('should handle mixed sequence', () => {
        expect(historyStorageService.hasPrevious(learningPathId)).toBeFalsy();
        historyStorageService.storeExercise(learningPathId, exerciseId);
        expect(historyStorageService.hasPrevious(learningPathId)).toBeTruthy();
        historyStorageService.storeLectureUnit(learningPathId, lectureId, lectureUnitId);
        expect(historyStorageService.hasPrevious(learningPathId)).toBeTruthy();
        historyStorageService.storeExercise(learningPathId, 11);
        expect(historyStorageService.hasPrevious(learningPathId)).toBeTruthy();

        // exercise 2
        let entry = historyStorageService.getPrevious(learningPathId);
        expect(entry).not.toBeNull();
        expect(entry).toBeInstanceOf(ExerciseEntry);
        const exercise2Entry = <ExerciseEntry>entry;
        expect(exercise2Entry.exerciseId).toBe(11);
        expect(historyStorageService.hasPrevious(learningPathId)).toBeTruthy();

        // lecture unit
        entry = historyStorageService.getPrevious(learningPathId);
        expect(entry).not.toBeNull();
        expect(entry).toBeInstanceOf(LectureUnitEntry);
        const lectureUnitEntry = <LectureUnitEntry>entry;
        expect(lectureUnitEntry.lectureId).toBe(lectureId);
        expect(lectureUnitEntry.lectureUnitId).toBe(lectureUnitId);
        expect(historyStorageService.hasPrevious(learningPathId)).toBeTruthy();

        // exercise 1
        entry = historyStorageService.getPrevious(learningPathId);
        expect(entry).not.toBeNull();
        expect(entry).toBeInstanceOf(ExerciseEntry);
        const exerciseEntry = <ExerciseEntry>entry;
        expect(exerciseEntry.exerciseId).toBe(exerciseId);
        expect(historyStorageService.hasPrevious(learningPathId)).toBeFalsy();
    });

    it('should handle multiple learning paths', () => {
        const learningPath2Id = 11;
        expect(historyStorageService.hasPrevious(learningPathId)).toBeFalsy();
        expect(historyStorageService.hasPrevious(learningPath2Id)).toBeFalsy();

        // lecture unit in learningPath(1)
        historyStorageService.storeLectureUnit(learningPathId, lectureId, lectureUnitId);

        expect(historyStorageService.hasPrevious(learningPathId)).toBeTruthy();
        expect(historyStorageService.hasPrevious(learningPath2Id)).toBeFalsy();

        // exercise in learningPath(2)
        historyStorageService.storeExercise(learningPath2Id, exerciseId);

        expect(historyStorageService.hasPrevious(learningPathId)).toBeTruthy();
        expect(historyStorageService.hasPrevious(learningPath2Id)).toBeTruthy();

        let entry = historyStorageService.getPrevious(learningPathId);
        expect(entry).not.toBeNull();
        expect(entry).toBeInstanceOf(LectureUnitEntry);
        const lectureUnitEntry = <LectureUnitEntry>entry;
        expect(lectureUnitEntry.lectureId).toBe(lectureId);
        expect(lectureUnitEntry.lectureUnitId).toBe(lectureUnitId);

        expect(historyStorageService.hasPrevious(learningPathId)).toBeFalsy();
        expect(historyStorageService.hasPrevious(learningPath2Id)).toBeTruthy();

        entry = historyStorageService.getPrevious(learningPath2Id);
        expect(entry).not.toBeNull();
        expect(entry).toBeInstanceOf(ExerciseEntry);
        const exerciseEntry = <ExerciseEntry>entry;
        expect(exerciseEntry.exerciseId).toBe(exerciseId);

        expect(historyStorageService.hasPrevious(learningPathId)).toBeFalsy();
        expect(historyStorageService.hasPrevious(learningPath2Id)).toBeFalsy();
    });
});
