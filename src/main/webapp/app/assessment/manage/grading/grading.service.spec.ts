import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { GradeType, GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { GradeStep } from 'app/assessment/shared/entities/grade-step.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { lastValueFrom } from 'rxjs';

describe('GradingService', () => {
    let service: GradingService;
    let httpMock: HttpTestingController;

    const courseId = 123;
    const examId = 456;

    const gradeStep1: GradeStep = {
        gradeName: 'Fail',
        lowerBoundPercentage: 0,
        upperBoundPercentage: 50,
        lowerBoundInclusive: true,
        upperBoundInclusive: false,
        isPassingGrade: false,
    };

    const gradeStep2: GradeStep = {
        gradeName: 'Pass',
        lowerBoundPercentage: 50,
        upperBoundPercentage: 100,
        lowerBoundInclusive: true,
        upperBoundInclusive: true,
        isPassingGrade: true,
    };

    const gradingScale: GradingScale = {
        id: 1,
        gradeType: GradeType.GRADE,
        gradeSteps: [gradeStep1, gradeStep2],
        plagiarismGrade: '5.0',
        noParticipationGrade: '5.0',
    };

    const gradeStepsResponse = (title: string) => ({ title, gradeType: GradeType.GRADE, maxPoints: 100, plagiarismGrade: '5.0', noParticipationGrade: '5.0' });

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [GradingService, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(GradingService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('findGradeSteps', () => {
        it('should reuse the full instructor grading scale for management steps and keep the grade step ids', async () => {
            const resultPromise = lastValueFrom(service.findGradeSteps(courseId, examId, 'management'));
            const req = httpMock.expectOne({ method: 'GET' });
            expect(req.request.url).toBe(`/api/assessment/courses/${courseId}/exams/${examId}/grading-scale`);
            req.flush({
                id: 42,
                gradeSteps: {
                    ...gradeStepsResponse('Exam'),
                    gradeSteps: [
                        { id: 7, ...gradeStep1 },
                        { id: 8, ...gradeStep2 },
                    ],
                },
            });

            const result = await resultPromise;
            expect(result?.gradeSteps.map((step) => step.id)).toEqual([7, 8]);
            expect(result?.title).toBe('Exam');
        });

        it('should find grade steps for course when no examId provided', async () => {
            const resultPromise = lastValueFrom(service.findGradeSteps(courseId));
            const req = httpMock.expectOne(`/api/assessment/courses/${courseId}/grading-scale/grade-steps`);
            expect(req.request.method).toBe('GET');
            req.flush({
                ...gradeStepsResponse('Course'),
                gradeSteps: [
                    { id: 1, ...gradeStep1 },
                    { id: 2, ...gradeStep2 },
                ],
            });

            const result = await resultPromise;
            expect(result).toEqual({
                ...gradeStepsResponse('Course'),
                gradeType: GradeType.GRADE,
                gradeSteps: [
                    { id: 1, ...gradeStep1 },
                    { id: 2, ...gradeStep2 },
                ],
            });
        });

        it('should find grade steps for exam when examId provided', async () => {
            const resultPromise = lastValueFrom(service.findGradeSteps(courseId, examId));
            const req = httpMock.expectOne(`/api/assessment/courses/${courseId}/exams/${examId}/grading-scale/grade-steps`);
            expect(req.request.method).toBe('GET');
            req.flush({ ...gradeStepsResponse('Exam'), gradeSteps: [gradeStep1, gradeStep2] });

            const result = await resultPromise;
            expect(result?.title).toBe('Exam');
            expect(result?.gradeSteps).toEqual([gradeStep1, gradeStep2]);
        });

        it('should return undefined when response body is null', async () => {
            const resultPromise = lastValueFrom(service.findGradeSteps(courseId));
            const req = httpMock.expectOne(`/api/assessment/courses/${courseId}/grading-scale/grade-steps`);
            req.flush(null);

            const result = await resultPromise;
            expect(result).toBeUndefined();
        });
    });

    describe('sortGradeSteps', () => {
        it('should sort grade steps by lower bound percentage', () => {
            const unsorted = [gradeStep2, gradeStep1];
            const result = service.sortGradeSteps(unsorted);

            expect(result[0]).toBe(gradeStep1);
            expect(result[1]).toBe(gradeStep2);
        });
    });

    describe('matchGradePercentage', () => {
        it('should match when percentage equals lower bound with inclusive lower bound', () => {
            expect(service.matchGradePercentage(gradeStep1, 0)).toBe(true);
        });

        it('should not match when percentage equals lower bound with exclusive lower bound', () => {
            const step: GradeStep = { ...gradeStep1, lowerBoundInclusive: false };
            expect(service.matchGradePercentage(step, 0)).toBe(false);
        });

        it('should match when percentage equals upper bound with inclusive upper bound', () => {
            expect(service.matchGradePercentage(gradeStep2, 100)).toBe(true);
        });

        it('should not match when percentage equals upper bound with exclusive upper bound', () => {
            expect(service.matchGradePercentage(gradeStep1, 50)).toBe(false);
        });

        it('should match when percentage is between bounds', () => {
            expect(service.matchGradePercentage(gradeStep1, 25)).toBe(true);
            expect(service.matchGradePercentage(gradeStep2, 75)).toBe(true);
        });

        it('should not match when percentage is outside bounds', () => {
            expect(service.matchGradePercentage(gradeStep1, 60)).toBe(false);
            expect(service.matchGradePercentage(gradeStep2, 40)).toBe(false);
        });
    });

    describe('findMatchingGradeStep', () => {
        it('should find matching grade step', () => {
            const gradeSteps = [gradeStep1, gradeStep2];
            expect(service.findMatchingGradeStep(gradeSteps, 25)).toBe(gradeStep1);
            expect(service.findMatchingGradeStep(gradeSteps, 75)).toBe(gradeStep2);
        });

        it('should return highest step when percentage exceeds all steps', () => {
            const gradeSteps = [gradeStep1, gradeStep2];
            const result = service.findMatchingGradeStep(gradeSteps, 150);
            expect(result?.gradeName).toBe('Pass');
        });

        it('should return undefined when percentage is below all steps', () => {
            const step: GradeStep = { ...gradeStep1, lowerBoundPercentage: 10, lowerBoundInclusive: false };
            const result = service.findMatchingGradeStep([step, gradeStep2], 5);
            expect(result).toBeUndefined();
        });
    });

    describe('findMatchingGradeStepByPoints', () => {
        it('should find matching grade step by points', () => {
            const gradeSteps = [gradeStep1, gradeStep2];
            expect(service.findMatchingGradeStepByPoints(gradeSteps, 25, 100)).toBe(gradeStep1);
            expect(service.findMatchingGradeStepByPoints(gradeSteps, 75, 100)).toBe(gradeStep2);
        });
    });

    describe('maxGrade', () => {
        it('should return max grade name', () => {
            const gradeSteps = [gradeStep1, gradeStep2];
            expect(service.maxGrade(gradeSteps)).toBe('Pass');
        });

        it('should return empty string when no max grade found', () => {
            const step: GradeStep = { ...gradeStep1, upperBoundInclusive: false };
            expect(service.maxGrade([step])).toBe('');
        });
    });

    describe('setGradePoints', () => {
        it('should set grade points based on max points', () => {
            const steps = [{ ...gradeStep1 }, { ...gradeStep2 }];
            service.setGradePoints(steps, 200);

            expect(steps[0].lowerBoundPoints).toBe(0);
            expect(steps[0].upperBoundPoints).toBe(100);
            expect(steps[1].lowerBoundPoints).toBe(100);
            expect(steps[1].upperBoundPoints).toBe(200);
        });
    });

    describe('hasPointsSet', () => {
        it('should return true when all grade steps have points set', () => {
            const steps = [
                { ...gradeStep1, lowerBoundPoints: 0, upperBoundPoints: 50 },
                { ...gradeStep2, lowerBoundPoints: 50, upperBoundPoints: 100 },
            ];
            expect(service.hasPointsSet(steps)).toBe(true);
        });

        it('should return false when points are not set', () => {
            expect(service.hasPointsSet([gradeStep1, gradeStep2])).toBe(false);
        });

        it('should return false when upper bound points is 0', () => {
            const steps = [{ ...gradeStep1, lowerBoundPoints: 0, upperBoundPoints: 0 }];
            expect(service.hasPointsSet(steps)).toBe(false);
        });

        it('should return false for empty array', () => {
            expect(service.hasPointsSet([])).toBe(false);
        });
    });

    describe('getGradingScaleCourse', () => {
        it('should return course from exam', () => {
            const course = new Course();
            course.id = 1;
            const exam = new Exam();
            exam.course = course;
            const scale: GradingScale = { ...gradingScale, exam };

            expect(service.getGradingScaleCourse(scale)).toBe(course);
        });

        it('should return course directly', () => {
            const course = new Course();
            course.id = 1;
            const scale: GradingScale = { ...gradingScale, course };

            expect(service.getGradingScaleCourse(scale)).toBe(course);
        });
    });

    describe('getGradingScaleTitle', () => {
        it('should return exam title', () => {
            const exam = new Exam();
            exam.title = 'Test Exam';
            const scale: GradingScale = { ...gradingScale, exam };

            expect(service.getGradingScaleTitle(scale)).toBe('Test Exam');
        });

        it('should return course title when no exam', () => {
            const course = new Course();
            course.title = 'Test Course';
            const scale: GradingScale = { ...gradingScale, course };

            expect(service.getGradingScaleTitle(scale)).toBe('Test Course');
        });
    });

    describe('getGradingScaleMaxPoints', () => {
        it('should return exam max points', () => {
            const exam = new Exam();
            exam.examMaxPoints = 150;
            const scale: GradingScale = { ...gradingScale, exam };

            expect(service.getGradingScaleMaxPoints(scale)).toBe(150);
        });

        it('should return course max points when no exam', () => {
            const course = new Course();
            course.maxPoints = 200;
            const scale: GradingScale = { ...gradingScale, course };

            expect(service.getGradingScaleMaxPoints(scale)).toBe(200);
        });

        it('should return 0 when no max points set', () => {
            expect(service.getGradingScaleMaxPoints(gradingScale)).toBe(0);
        });
    });

    describe('getNumericValueForGradeName', () => {
        it('should parse grade name with period as decimal separator', () => {
            expect(service.getNumericValueForGradeName('2.0')).toBe(2.0);
        });

        it('should parse grade name with comma as decimal separator', () => {
            expect(service.getNumericValueForGradeName('2,5')).toBe(2.5);
        });

        it('should return undefined for undefined input', () => {
            expect(service.getNumericValueForGradeName(undefined)).toBeUndefined();
        });

        it('should return undefined for non-numeric grade name', () => {
            expect(service.getNumericValueForGradeName('Pass')).toBeUndefined();
        });
    });
});
