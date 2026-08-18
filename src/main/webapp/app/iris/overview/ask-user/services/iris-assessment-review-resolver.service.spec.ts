import { HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, convertToParamMap } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { firstValueFrom, of } from 'rxjs';

import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisAssessmentReviewHttpService } from 'app/iris/overview/ask-user/services/iris-assessment-review-http.service';
import { IrisAssessmentReviewResolver } from 'app/iris/overview/ask-user/services/iris-assessment-review-resolver.service';
import { IrisAssessment } from 'app/iris/shared/entities/iris-assessment.model';
import { QAExchangeDTO } from 'app/iris/shared/entities/iris-qa-exchange-dto.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

describe('IrisAssessmentReviewResolver', () => {
    let resolver: IrisAssessmentReviewResolver;
    let courseManagementService: { find: ReturnType<typeof vi.fn> };
    let assessmentReviewService: {
        findWithStudent: ReturnType<typeof vi.fn>;
        getAssessmentChat: ReturnType<typeof vi.fn>;
    };

    const course = { id: 7, title: 'Course' } as Course;
    const exercise = { id: 11, type: ExerciseType.PROGRAMMING, title: 'Programming Exercise' } as ProgrammingExercise;
    const assessment = { id: 3, exercise } as IrisAssessment;
    const rows: QAExchangeDTO[] = [{ id: 1, question: 'Question', answer: 'Answer', reasoning: 'Reasoning' }];

    const route = (parameters: Record<string, string>, inClass = false) =>
        ({
            paramMap: convertToParamMap(parameters),
            data: { inClass },
        }) as unknown as ActivatedRouteSnapshot;

    beforeEach(() => {
        courseManagementService = {
            find: vi.fn(() => of(new HttpResponse({ body: course }))),
        };
        assessmentReviewService = {
            findWithStudent: vi.fn(() => of(new HttpResponse({ body: assessment }))),
            getAssessmentChat: vi.fn(() => of(new HttpResponse({ body: rows }))),
        };

        TestBed.configureTestingModule({
            providers: [
                IrisAssessmentReviewResolver,
                { provide: CourseManagementService, useValue: courseManagementService },
                { provide: IrisAssessmentReviewHttpService, useValue: assessmentReviewService },
            ],
        });

        resolver = TestBed.inject(IrisAssessmentReviewResolver);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should resolve course, programming exercise, assessment and chat rows', async () => {
        const resolvedData = await firstValueFrom(resolver.resolve(route({ courseId: '7', assessmentId: '3' }, true)));

        expect(resolvedData).toEqual({ course, exercise, assessment, rows });
        expect(courseManagementService.find).toHaveBeenCalledExactlyOnceWith(7);
        expect(assessmentReviewService.findWithStudent).toHaveBeenCalledExactlyOnceWith(3);
        expect(assessmentReviewService.getAssessmentChat).toHaveBeenCalledExactlyOnceWith(3, true);
    });

    it('should reject missing route ids', () => {
        expect(() => resolver.resolve(route({ courseId: '7' }))).toThrow('Missing or invalid route parameter: assessmentId');
    });

    it('should reject missing course response body', async () => {
        courseManagementService.find.mockReturnValue(of(new HttpResponse({ body: null })));

        await expect(firstValueFrom(resolver.resolve(route({ courseId: '7', assessmentId: '3' })))).rejects.toThrow('Course could not be loaded');
    });

    it('should reject missing assessment response body', async () => {
        assessmentReviewService.findWithStudent.mockReturnValue(of(new HttpResponse({ body: null })));

        await expect(firstValueFrom(resolver.resolve(route({ courseId: '7', assessmentId: '3' })))).rejects.toThrow('Iris assessment could not be loaded');
    });

    it('should reject missing assessment chat response body', async () => {
        assessmentReviewService.getAssessmentChat.mockReturnValue(of(new HttpResponse({ body: null })));

        await expect(firstValueFrom(resolver.resolve(route({ courseId: '7', assessmentId: '3' })))).rejects.toThrow('Assessment chat could not be loaded');
    });

    it('should reject assessments without a programming exercise', async () => {
        assessmentReviewService.findWithStudent.mockReturnValue(of(new HttpResponse({ body: { id: 3, exercise: { id: 12, type: ExerciseType.QUIZ } } })));

        await expect(firstValueFrom(resolver.resolve(route({ courseId: '7', assessmentId: '3' })))).rejects.toThrow('Iris assessment 3 is not linked to a programming exercise');
    });
});
