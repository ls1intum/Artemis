import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { AssessmentCriteriaGenerationService } from 'app/exercise/structured-grading-criterion/assessment-criteria-generation.service';
import { HyperionAssessmentCriteriaGenerationApi } from 'app/openapi/api/hyperion-assessment-criteria-generation-api';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { UMLDiagramType } from '@tumaet/apollon';

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

        expect(service.buildGenerationCall(regularExercise)).toMatchObject({ courseId: 11, request: { exerciseType: 'TEXT', bonusPoints: 0 } });
        expect(service.buildGenerationCall(examExercise)).toMatchObject({ courseId: 22, request: { exerciseType: 'TEXT' } });
    });

    it('should include the current modeling context', () => {
        const exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, { id: 33 }, undefined);
        exercise.problemStatement = 'Draw a model';
        exercise.maxPoints = 8;
        exercise.exampleSolutionModel = '{"nodes":[{"id":"unsaved"}]}';

        const call = service.buildGenerationCall(exercise);

        expect(call.request).toMatchObject({
            exerciseType: 'MODELING',
            modelingContext: { diagramType: String(UMLDiagramType.ClassDiagram), exampleSolutionModel: exercise.exampleSolutionModel },
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

        service.generate(exercise).subscribe((criteria) => {
            expect(criteria[0].title).toBe('Correctness');
            expect(criteria[0].id).toBeUndefined();
            expect(criteria[0].structuredGradingInstructions[0]).toMatchObject({ credits: 2, usageCount: 1 });
            expect(criteria[0].structuredGradingInstructions[0].id).toBeUndefined();
        });
        expect(api.generateAssessmentCriteria).toHaveBeenCalledWith(44, expect.objectContaining({ exerciseType: 'TEXT' }));
    });
});
