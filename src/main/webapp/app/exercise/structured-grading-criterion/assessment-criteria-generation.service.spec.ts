import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { AssessmentCriteriaGenerationService } from 'app/exercise/structured-grading-criterion/assessment-criteria-generation.service';
import { HyperionAssessmentCriteriaGenerationApi } from 'app/openapi/api/hyperion-assessment-criteria-generation-api';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';

describe('AssessmentCriteriaGenerationService', () => {
    let service: AssessmentCriteriaGenerationService;
    const api = { generateAssessmentCriteria: vi.fn() };

    beforeEach(() => {
        vi.clearAllMocks();
        TestBed.configureTestingModule({ providers: [{ provide: HyperionAssessmentCriteriaGenerationApi, useValue: api }] });
        service = TestBed.inject(AssessmentCriteriaGenerationService);
    });

    it('should resolve regular and exam course IDs', () => {
        const regularExercise = new TextExercise({ id: 11 }, undefined);
        regularExercise.problemStatement = 'Problem';
        regularExercise.maxPoints = 5;
        regularExercise.bonusPoints = undefined;
        const examExercise = new TextExercise(undefined, { exam: { course: { id: 22 } } });
        examExercise.problemStatement = 'Exam problem';
        examExercise.maxPoints = 10;

        expect(service.buildGenerationCall(regularExercise)).toEqual({ courseId: 11, request: { problemStatement: 'Problem', maxPoints: 5, bonusPoints: 0 } });
        expect(service.buildGenerationCall(examExercise)).toEqual({ courseId: 22, request: { problemStatement: 'Exam problem', maxPoints: 10, bonusPoints: 0 } });
    });

    it('should include optional example solution and additional context for any exercise type', () => {
        const exercise = new FileUploadExercise({ id: 33 }, undefined);
        exercise.problemStatement = ' Upload a report ';
        exercise.maxPoints = 8;
        exercise.bonusPoints = 2;
        exercise.gradingInstructions = ' Accept only valid PDF files. ';

        const call = service.buildGenerationCall(exercise, {
            exampleSolution: ' Example report ',
            additionalContext: ' Accepted format: PDF ',
        });

        expect(call).toEqual({
            courseId: 33,
            request: {
                problemStatement: 'Upload a report',
                maxPoints: 8,
                bonusPoints: 2,
                gradingInstructions: 'Accept only valid PDF files.',
                exampleSolution: 'Example report',
                additionalContext: 'Accepted format: PDF',
            },
        });
    });

    it('should map generated DTOs to unsaved grading models', () => {
        api.generateAssessmentCriteria.mockReturnValue(
            of({
                criteria: [
                    {
                        title: 'Correctness',
                        structuredGradingInstructions: [{ credits: 2, gradingScale: 'Full', instructionDescription: 'Correct', feedback: 'Well done', usageCount: 1 }],
                    },
                ],
            }),
        );
        const exercise = new TextExercise({ id: 44 }, undefined);
        exercise.problemStatement = 'Problem';
        exercise.maxPoints = 2;

        service.generate(exercise, { exampleSolution: 'Example' }).subscribe((criteria) => {
            expect(criteria[0].title).toBe('Correctness');
            expect(criteria[0].id).toBeUndefined();
            expect(criteria[0].structuredGradingInstructions[0]).toMatchObject({ credits: 2, usageCount: 1 });
            expect(criteria[0].structuredGradingInstructions[0].id).toBeUndefined();
        });
        expect(api.generateAssessmentCriteria).toHaveBeenCalledWith(44, {
            problemStatement: 'Problem',
            maxPoints: 2,
            bonusPoints: 0,
            exampleSolution: 'Example',
        });
    });
});
