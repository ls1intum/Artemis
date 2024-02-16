import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../test.module';
import { ExerciseEntry, LearningPathStorageService, LectureUnitEntry } from 'app/course/learning-paths/participate/learning-path-storage.service';
import { NgxLearningPathDTO, NgxLearningPathEdge, NgxLearningPathNode, NodeType } from 'app/entities/competency/learning-path.model';

describe('LearningPathStorageService', () => {
    let storageService: LearningPathStorageService;
    let learningPathId1: number;
    let learningPathId2: number;
    let ngxPathEmpty: NgxLearningPathDTO;
    let ngxPath1: NgxLearningPathDTO;
    let ngxPath2: NgxLearningPathDTO;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        })
            .compileComponents()
            .then(() => {
                storageService = new LearningPathStorageService();
                learningPathId1 = 1;
                learningPathId2 = 2;

                ngxPath1 = {
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

                ngxPath2 = {
                    nodes: [
                        { id: '11', type: NodeType.COMPETENCY_START } as NgxLearningPathNode,
                        { id: '12', type: NodeType.LECTURE_UNIT, linkedResource: 15, linkedResourceParent: 16 } as NgxLearningPathNode,
                        { id: '13', type: NodeType.EXERCISE, linkedResource: 17 } as NgxLearningPathNode,
                        { id: '14', type: NodeType.COMPETENCY_END } as NgxLearningPathNode,
                    ],
                    edges: [
                        { id: '18', source: '11', target: '12' } as NgxLearningPathEdge,
                        { id: '19', source: '12', target: '13' } as NgxLearningPathEdge,
                        { id: '20', source: '13', target: '14' } as NgxLearningPathEdge,
                    ],
                } as NgxLearningPathDTO;

                ngxPathEmpty = { nodes: [], edges: [] } as NgxLearningPathDTO;
            });
    });

    it('should have no recommendation for empty learning path', () => {
        storageService.storeRecommendations(learningPathId1, ngxPathEmpty);
        expect(storageService.hasPrevRecommendation(learningPathId1)).toBeFalsy();
        expect(storageService.getPrevRecommendation(learningPathId1)).toBeUndefined();

        const arbitraryEntry = new ExerciseEntry(1337);
        expect(storageService.hasPrevRecommendation(learningPathId1, arbitraryEntry)).toBeFalsy();
        expect(storageService.getPrevRecommendation(learningPathId1, arbitraryEntry)).toBeUndefined();

        expect(storageService.hasNextRecommendation(learningPathId1)).toBeFalsy();
        expect(storageService.getNextRecommendation(learningPathId1)).toBeUndefined();

        expect(storageService.hasNextRecommendation(learningPathId1, arbitraryEntry)).toBeFalsy();
        expect(storageService.getNextRecommendation(learningPathId1, arbitraryEntry)).toBeUndefined();
    });

    it('should retrieve first entry as next recommendation for undefined entry', () => {
        storageService.storeRecommendations(learningPathId1, ngxPath1);
        expect(storageService.hasNextRecommendation(learningPathId1)).toBeTruthy();
        const recommendation = storageService.getNextRecommendation(learningPathId1);
        expect(recommendation).toBeInstanceOf(LectureUnitEntry);
        const lectureUnitEntry = recommendation as LectureUnitEntry;
        expect(lectureUnitEntry.lectureUnitId).toBe(5);
        expect(lectureUnitEntry.lectureId).toBe(6);
    });

    it('should retrieve successor entry as next recommendation', () => {
        storageService.storeRecommendations(learningPathId1, ngxPath1);
        const lectureUnitEntry = new LectureUnitEntry(6, 5);
        expect(storageService.hasNextRecommendation(learningPathId1, lectureUnitEntry)).toBeTruthy();
        const recommendation = storageService.getNextRecommendation(learningPathId1, lectureUnitEntry);
        expect(recommendation).toBeInstanceOf(ExerciseEntry);
        const exerciseEntry = recommendation as ExerciseEntry;
        expect(exerciseEntry.exerciseId).toBe(7);
    });

    it('should retrieve no entry as next recommendation for last entry', () => {
        storageService.storeRecommendations(learningPathId1, ngxPath1);
        const exerciseEntry = new ExerciseEntry(7);
        expect(storageService.hasNextRecommendation(learningPathId1, exerciseEntry)).toBeFalsy();
        expect(storageService.getNextRecommendation(learningPathId1, exerciseEntry)).toBeUndefined();
    });

    it('should not retrieve entry as previous recommendation for undefined entry', () => {
        storageService.storeRecommendations(learningPathId1, ngxPath1);
        expect(storageService.hasPrevRecommendation(learningPathId1)).toBeFalsy();
        expect(storageService.getPrevRecommendation(learningPathId1)).toBeUndefined();
    });

    it('should not retrieve entry as previous recommendation for first entry', () => {
        storageService.storeRecommendations(learningPathId1, ngxPath1);
        const lectureUnitEntry = new LectureUnitEntry(6, 5);
        expect(storageService.hasPrevRecommendation(learningPathId1, lectureUnitEntry)).toBeFalsy();
        expect(storageService.getPrevRecommendation(learningPathId1, lectureUnitEntry)).toBeUndefined();
    });

    it('should retrieve predecessor entry as next recommendation', () => {
        storageService.storeRecommendations(learningPathId1, ngxPath1);
        const exerciseEntry = new ExerciseEntry(7);
        expect(storageService.hasPrevRecommendation(learningPathId1, exerciseEntry)).toBeTruthy();
        const recommendation = storageService.getPrevRecommendation(learningPathId1, exerciseEntry);
        expect(recommendation).toBeInstanceOf(LectureUnitEntry);
        const lectureUnitEntry = recommendation as LectureUnitEntry;
        expect(lectureUnitEntry.lectureUnitId).toBe(5);
        expect(lectureUnitEntry.lectureId).toBe(6);
    });

    it('should handle multiple learning paths - next, entry undefined', () => {
        storageService.storeRecommendations(learningPathId1, ngxPath1);
        storageService.storeRecommendations(learningPathId2, ngxPath2);

        expect(storageService.hasNextRecommendation(learningPathId1)).toBeTruthy();
        const recommendation1 = storageService.getNextRecommendation(learningPathId1);
        expect(recommendation1).toBeInstanceOf(LectureUnitEntry);
        const recommendedLectureUnitEntry1 = recommendation1 as LectureUnitEntry;
        expect(recommendedLectureUnitEntry1.lectureUnitId).toBe(5);
        expect(recommendedLectureUnitEntry1.lectureId).toBe(6);
        expect(storageService.hasNextRecommendation(learningPathId2)).toBeTruthy();
        const recommendation2 = storageService.getNextRecommendation(learningPathId2);
        expect(recommendation2).toBeInstanceOf(LectureUnitEntry);
        const recommendedLectureUnitEntry2 = recommendation2 as LectureUnitEntry;
        expect(recommendedLectureUnitEntry2.lectureUnitId).toBe(15);
        expect(recommendedLectureUnitEntry2.lectureId).toBe(16);
    });

    it('should handle multiple learning paths - next, entry defined', () => {
        storageService.storeRecommendations(learningPathId1, ngxPath1);
        storageService.storeRecommendations(learningPathId2, ngxPath2);

        const lectureUnitEntry1 = new LectureUnitEntry(6, 5);
        const lectureUnitEntry2 = new LectureUnitEntry(16, 15);
        expect(storageService.hasNextRecommendation(learningPathId1, lectureUnitEntry1)).toBeTruthy();
        const recommendation1 = storageService.getNextRecommendation(learningPathId1, lectureUnitEntry1);
        expect(recommendation1).toBeInstanceOf(ExerciseEntry);
        const exerciseEntry1 = recommendation1 as ExerciseEntry;
        expect(exerciseEntry1.exerciseId).toBe(7);
        expect(storageService.hasNextRecommendation(learningPathId1, lectureUnitEntry2)).toBeFalsy();
        expect(storageService.hasNextRecommendation(learningPathId2, lectureUnitEntry1)).toBeFalsy();
        expect(storageService.hasNextRecommendation(learningPathId2, lectureUnitEntry2)).toBeTruthy();
        const recommendation2 = storageService.getNextRecommendation(learningPathId2, lectureUnitEntry2);
        expect(recommendation2).toBeInstanceOf(ExerciseEntry);
        const exerciseEntry2 = recommendation2 as ExerciseEntry;
        expect(exerciseEntry2.exerciseId).toBe(17);
    });

    it('should handle multiple learning paths - prev, entry undefined', () => {
        storageService.storeRecommendations(learningPathId1, ngxPath1);
        storageService.storeRecommendations(learningPathId2, ngxPath2);

        expect(storageService.hasPrevRecommendation(learningPathId1)).toBeFalsy();
        expect(storageService.hasPrevRecommendation(learningPathId2)).toBeFalsy();
    });

    it('should handle multiple learning paths - prev, entry defined', () => {
        storageService.storeRecommendations(learningPathId1, ngxPath1);
        storageService.storeRecommendations(learningPathId2, ngxPath2);

        const exerciseEntry1 = new ExerciseEntry(7);
        const exerciseEntry2 = new ExerciseEntry(17);
        expect(storageService.hasPrevRecommendation(learningPathId1, exerciseEntry1)).toBeTruthy();
        const recommendation1 = storageService.getPrevRecommendation(learningPathId1, exerciseEntry1);
        expect(recommendation1).toBeInstanceOf(LectureUnitEntry);
        const lectureUnitEntry1 = recommendation1 as LectureUnitEntry;
        expect(lectureUnitEntry1.lectureUnitId).toBe(5);
        expect(lectureUnitEntry1.lectureId).toBe(6);
        expect(storageService.hasPrevRecommendation(learningPathId1, exerciseEntry2)).toBeFalsy();
        expect(storageService.hasPrevRecommendation(learningPathId2, exerciseEntry1)).toBeFalsy();
        expect(storageService.hasPrevRecommendation(learningPathId2, exerciseEntry2)).toBeTruthy();
        const recommendation2 = storageService.getPrevRecommendation(learningPathId2, exerciseEntry2);
        expect(recommendation2).toBeInstanceOf(LectureUnitEntry);
        const lectureUnitEntry2 = recommendation2 as LectureUnitEntry;
        expect(lectureUnitEntry2.lectureUnitId).toBe(15);
        expect(lectureUnitEntry2.lectureId).toBe(16);
    });
});
