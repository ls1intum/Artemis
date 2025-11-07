import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { QuizReEvaluateService } from 'app/quiz/manage/re-evaluate/services/quiz-re-evaluate.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { provideHttpClient } from '@angular/common/http';
import { ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import * as blobUtil from 'app/shared/util/blob-util';

describe('QuizReEvaluateService', () => {
    let service: QuizReEvaluateService;
    let httpMock: HttpTestingController;
    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), QuizReEvaluateService],
        });
        service = TestBed.inject(QuizReEvaluateService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should send reevaluate request correctly', fakeAsync(() => {
        const quizExercise = { id: 1 } as QuizExercise;
        const files = new Map<string, Blob>();
        files.set('test1', new Blob());
        files.set('test2', new Blob());
        service.reevaluate(quizExercise, files).subscribe((res) => {
            expect(res.body).toBeNull();
            expect(res.ok).toBeTrue();
        });

        const req = httpMock.expectOne({ method: 'PUT', url: 'api/quiz/quiz-exercises/1/re-evaluate' });
        expect(req.request.body).toBeInstanceOf(FormData);
        expect(req.request.body.getAll('exercise')).toBeArrayOfSize(1);
        expect(req.request.body.get('exercise')).toBeInstanceOf(Blob);
        const formDataFiles = req.request.body.getAll('files');
        expect(formDataFiles).toBeArrayOfSize(2);
        expect(formDataFiles[0]).toBeInstanceOf(Blob);
        expect(formDataFiles[1]).toBeInstanceOf(Blob);
        req.flush(null, { status: 200, statusText: 'OK' });
        tick();
    }));

    it('should convert QuizExercise with all question types into correct DTO and send it as FormData', () => {
        // Spy on objectToJsonBlob to capture the DTO passed to it
        const blobSpy = jest.spyOn(blobUtil, 'objectToJsonBlob');

        // Build a QuizExercise with MC, DnD, and SA questions to cover all conversions
        const mcQuestion = {
            type: 'multiple-choice',
            id: 5,
            title: 'MCQ',
            scoringType: ScoringType.ALL_OR_NOTHING,
            randomizeOrder: true,
            invalid: true,
            text: 'Which?',
            hint: 'Think.',
            explanation: 'Because.',
            answerOptions: [
                { id: 1, text: 'A', hint: 'h1', explanation: 'e1', isCorrect: true, invalid: false },
                { id: 2, text: 'B', hint: 'h2', explanation: 'e2', isCorrect: false, invalid: true },
            ],
        };

        const dndDropLocation = { id: 11, invalid: false };
        const dndDragItem = { id: 21, invalid: true, text: 'Item 1', pictureFilePath: 'img.png' };
        const dndMapping = { dragItem: dndDragItem, dropLocation: dndDropLocation };
        const dndQuestion = {
            type: 'drag-and-drop',
            id: 6,
            title: 'DnD',
            text: 'Drag it',
            hint: 'h',
            explanation: 'e',
            scoringType: ScoringType.PROPORTIONAL_WITH_PENALTY,
            randomizeOrder: false,
            invalid: true,
            dropLocations: [dndDropLocation],
            dragItems: [dndDragItem],
            correctMappings: [dndMapping],
        };

        const saSpot = { id: 31, invalid: false };
        const saSolutionWithId = { id: 41, text: 'answer1', invalid: false };
        const saSolutionWithoutId = { tempID: 999, text: 'answer2', invalid: true };
        const saMappingWithId = { solution: saSolutionWithId, spot: saSpot };
        const saMappingWithTempId = { solution: saSolutionWithoutId, spot: saSpot };
        const saQuestion = {
            type: 'short-answer',
            id: 7,
            title: 'SAQ',
            text: 'Fill in',
            scoringType: ScoringType.PROPORTIONAL_WITHOUT_PENALTY,
            randomizeOrder: true,
            invalid: true,
            similarityValue: 80,
            matchLetterCase: true,
            spots: [saSpot],
            solutions: [saSolutionWithId, saSolutionWithoutId],
            correctMappings: [saMappingWithId, saMappingWithTempId],
        };

        const quizExercise = {
            id: 42,
            title: 'Quiz Title',
            includedInOverallScore: IncludedInOverallScore.INCLUDED_AS_BONUS,
            randomizeQuestionOrder: false,
            quizQuestions: [mcQuestion, dndQuestion, saQuestion],
        } as unknown as QuizExercise;

        const files = new Map<string, Blob>();
        files.set('fileA', new Blob());

        // Call service
        const sub = service.reevaluate(quizExercise, files).subscribe();

        const req = httpMock.expectOne({ method: 'PUT', url: 'api/quiz/quiz-exercises/42/re-evaluate' });
        expect(req.request.body).toBeInstanceOf(FormData);
        const exerciseBlob = req.request.body.get('exercise') as Blob;
        expect(exerciseBlob).toBeInstanceOf(Blob);

        // The first call to objectToJsonBlob contains the DTO we want to assert
        const dto = blobSpy.mock.calls[0][0] as any;

        // Top-level DTO expectations
        expect(dto.title).toBe('Quiz Title');
        expect(dto.includedInOverallScore).toBe('INCLUDED_AS_BONUS');
        expect(dto.randomizeQuestionOrder).toBeFalse();
        expect(dto.quizQuestions).toBeArrayOfSize(3);

        // Multiple-choice DTO
        const dtoMc = dto.quizQuestions.find((q: any) => q.type === 'multiple-choice');
        expect(dtoMc).toBeDefined();
        expect(dtoMc.id).toBe(5);
        expect(dtoMc.title).toBe('MCQ');
        expect(dtoMc.scoringType).toBe('ALL_OR_NOTHING');
        expect(dtoMc.randomizeOrder).toBeTrue();
        expect(dtoMc.invalid).toBeTrue();
        expect(dtoMc.text).toBe('Which?');
        expect(dtoMc.hint).toBe('Think.');
        expect(dtoMc.explanation).toBe('Because.');
        expect(dtoMc.answerOptions).toBeArrayOfSize(2);
        expect(dtoMc.answerOptions[0]).toEqual(expect.objectContaining({ id: 1, text: 'A', hint: 'h1', explanation: 'e1', isCorrect: true, invalid: false }));
        expect(dtoMc.answerOptions[1]).toEqual(expect.objectContaining({ id: 2, text: 'B', hint: 'h2', explanation: 'e2', isCorrect: false, invalid: true }));

        // Drag-and-drop DTO
        const dtoDnd = dto.quizQuestions.find((q: any) => q.type === 'drag-and-drop');
        expect(dtoDnd).toBeDefined();
        expect(dtoDnd.id).toBe(6);
        expect(dtoDnd.title).toBe('DnD');
        expect(dtoDnd.scoringType).toBe('PROPORTIONAL_WITH_PENALTY');
        expect(dtoDnd.randomizeOrder).toBeFalse();
        expect(dtoDnd.invalid).toBeTrue();
        expect(dtoDnd.text).toBe('Drag it');
        expect(dtoDnd.hint).toBe('h');
        expect(dtoDnd.explanation).toBe('e');
        expect(dtoDnd.dropLocations).toBeArrayOfSize(1);
        expect(dtoDnd.dropLocations[0]).toEqual(expect.objectContaining({ id: 11, invalid: false }));
        expect(dtoDnd.dragItems).toBeArrayOfSize(1);
        expect(dtoDnd.dragItems[0]).toEqual(expect.objectContaining({ id: 21, invalid: true, text: 'Item 1', pictureFilePath: 'img.png' }));
        expect(dtoDnd.correctMappings).toBeArrayOfSize(1);
        expect(dtoDnd.correctMappings[0]).toEqual(expect.objectContaining({ dragItemId: 21, dropLocationId: 11 }));

        // Short-answer DTO
        const dtoSa = dto.quizQuestions.find((q: any) => q.type === 'short-answer');
        expect(dtoSa).toBeDefined();
        expect(dtoSa.id).toBe(7);
        expect(dtoSa.title).toBe('SAQ');
        expect(dtoSa.scoringType).toBe('PROPORTIONAL_WITHOUT_PENALTY');
        expect(dtoSa.randomizeOrder).toBeTrue();
        expect(dtoSa.invalid).toBeTrue();
        expect(dtoSa.text).toBe('Fill in');
        expect(dtoSa.similarityValue).toBe(80);
        expect(dtoSa.matchLetterCase).toBeTrue();
        expect(dtoSa.spots).toBeArrayOfSize(1);
        expect(dtoSa.spots[0]).toEqual(expect.objectContaining({ id: 31, invalid: false }));
        expect(dtoSa.solutions).toBeArrayOfSize(2);
        // One solution by id, one by tempID
        expect(dtoSa.solutions).toEqual(
            expect.arrayContaining([
                expect.objectContaining({ id: 41, text: 'answer1', invalid: false }),
                expect.objectContaining({ tempID: 999, text: 'answer2', invalid: true }),
            ]),
        );
        expect(dtoSa.correctMappings).toBeArrayOfSize(2);
        expect(dtoSa.correctMappings).toEqual(
            expect.arrayContaining([expect.objectContaining({ solutionId: 41, spotId: 31 }), expect.objectContaining({ solutionTempID: 999, spotId: 31 })]),
        );

        // Also ensure the file was appended
        const formDataFiles = req.request.body.getAll('files');
        expect(formDataFiles).toBeArrayOfSize(1);
        expect(formDataFiles[0]).toBeInstanceOf(Blob);

        // Finish request
        req.flush(null, { status: 200, statusText: 'OK' });
        sub.unsubscribe();
        blobSpy.mockRestore();
    });
});
