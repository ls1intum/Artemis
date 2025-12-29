import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { GradingSystemService } from 'app/assessment/manage/grading-system/grading-system.service';
import { GradeType, GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { GradeStep } from 'app/assessment/shared/entities/grade-step.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { lastValueFrom } from 'rxjs';
import { SortingOrder } from 'app/shared/table/pageable-table';

describe('GradingSystemService', () => {
    setupTestBed({ zoneless: true });
    let service: GradingSystemService;
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

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [GradingSystemService, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(GradingSystemService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('HTTP operations for course', () => {
        it('should create grading scale for course', async () => {
            const responsePromise = lastValueFrom(service.createGradingScaleForCourse(courseId, gradingScale));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/grading-scale`);
            expect(req.request.method).toBe('POST');
            req.flush(gradingScale);

            const response = await responsePromise;
            expect(response.body).toEqual(gradingScale);
        });

        it('should update grading scale for course', async () => {
            const responsePromise = lastValueFrom(service.updateGradingScaleForCourse(courseId, gradingScale));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/grading-scale`);
            expect(req.request.method).toBe('PUT');
            req.flush(gradingScale);

            const response = await responsePromise;
            expect(response.body).toEqual(gradingScale);
        });

        it('should find grading scale for course', async () => {
            const responsePromise = lastValueFrom(service.findGradingScaleForCourse(courseId));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/grading-scale`);
            expect(req.request.method).toBe('GET');
            req.flush(gradingScale);

            const response = await responsePromise;
            expect(response.body).toEqual(gradingScale);
        });

        it('should delete grading scale for course', async () => {
            const responsePromise = lastValueFrom(service.deleteGradingScaleForCourse(courseId));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/grading-scale`);
            expect(req.request.method).toBe('DELETE');
            req.flush({});

            const response = await responsePromise;
            expect(response.ok).toBe(true);
        });

        it('should find grade steps for course', async () => {
            const gradeStepsDTO = { title: 'Course', gradeType: GradeType.GRADE, gradeSteps: [gradeStep1, gradeStep2], maxPoints: 100 };
            const responsePromise = lastValueFrom(service.findGradeStepsForCourse(courseId));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/grading-scale/grade-steps`);
            expect(req.request.method).toBe('GET');
            req.flush(gradeStepsDTO);

            const response = await responsePromise;
            expect(response.body).toEqual(gradeStepsDTO);
        });

        it('should match percentage to grade step for course', async () => {
            const gradeDTO = { gradeName: 'Pass', isPassingGrade: true };
            const responsePromise = lastValueFrom(service.matchPercentageToGradeStepForCourse(courseId, 75));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/grading-scale/match-grade-step?gradePercentage=75`);
            expect(req.request.method).toBe('GET');
            req.flush(gradeDTO);

            const response = await responsePromise;
            expect(response.body).toEqual(gradeDTO);
        });
    });

    describe('HTTP operations for exam', () => {
        it('should create grading scale for exam', async () => {
            const responsePromise = lastValueFrom(service.createGradingScaleForExam(courseId, examId, gradingScale));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/exams/${examId}/grading-scale`);
            expect(req.request.method).toBe('POST');
            req.flush(gradingScale);

            const response = await responsePromise;
            expect(response.body).toEqual(gradingScale);
        });

        it('should update grading scale for exam', async () => {
            const responsePromise = lastValueFrom(service.updateGradingScaleForExam(courseId, examId, gradingScale));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/exams/${examId}/grading-scale`);
            expect(req.request.method).toBe('PUT');
            req.flush(gradingScale);

            const response = await responsePromise;
            expect(response.body).toEqual(gradingScale);
        });

        it('should find grading scale for exam', async () => {
            const responsePromise = lastValueFrom(service.findGradingScaleForExam(courseId, examId));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/exams/${examId}/grading-scale`);
            expect(req.request.method).toBe('GET');
            req.flush(gradingScale);

            const response = await responsePromise;
            expect(response.body).toEqual(gradingScale);
        });

        it('should delete grading scale for exam', async () => {
            const responsePromise = lastValueFrom(service.deleteGradingScaleForExam(courseId, examId));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/exams/${examId}/grading-scale`);
            expect(req.request.method).toBe('DELETE');
            req.flush({});

            const response = await responsePromise;
            expect(response.ok).toBe(true);
        });

        it('should find grade steps for exam', async () => {
            const gradeStepsDTO = { title: 'Exam', gradeType: GradeType.GRADE, gradeSteps: [gradeStep1, gradeStep2], maxPoints: 100 };
            const responsePromise = lastValueFrom(service.findGradeStepsForExam(courseId, examId));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/exams/${examId}/grading-scale/grade-steps`);
            expect(req.request.method).toBe('GET');
            req.flush(gradeStepsDTO);

            const response = await responsePromise;
            expect(response.body).toEqual(gradeStepsDTO);
        });

        it('should match percentage to grade step for exam', async () => {
            const gradeDTO = { gradeName: 'Pass', isPassingGrade: true };
            const responsePromise = lastValueFrom(service.matchPercentageToGradeStepForExam(courseId, examId, 75));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/exams/${examId}/grading-scale/match-grade-step?gradePercentage=75`);
            expect(req.request.method).toBe('GET');
            req.flush(gradeDTO);

            const response = await responsePromise;
            expect(response.body).toEqual(gradeDTO);
        });
    });

    describe('findGradeSteps', () => {
        it('should find grade steps for course when no examId provided', async () => {
            const gradeStepsDTO = { title: 'Course', gradeType: GradeType.GRADE, gradeSteps: [gradeStep1, gradeStep2], maxPoints: 100 };
            const resultPromise = lastValueFrom(service.findGradeSteps(courseId));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/grading-scale/grade-steps`);
            req.flush(gradeStepsDTO);

            const result = await resultPromise;
            expect(result).toEqual(gradeStepsDTO);
        });

        it('should find grade steps for exam when examId provided', async () => {
            const gradeStepsDTO = { title: 'Exam', gradeType: GradeType.GRADE, gradeSteps: [gradeStep1, gradeStep2], maxPoints: 100 };
            const resultPromise = lastValueFrom(service.findGradeSteps(courseId, examId));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/exams/${examId}/grading-scale/grade-steps`);
            req.flush(gradeStepsDTO);

            const result = await resultPromise;
            expect(result).toEqual(gradeStepsDTO);
        });

        it('should return undefined when response body is null', async () => {
            const resultPromise = lastValueFrom(service.findGradeSteps(courseId));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/grading-scale/grade-steps`);
            req.flush(null);

            const result = await resultPromise;
            expect(result).toBeUndefined();
        });
    });

    describe('matchPercentageToGradeStep', () => {
        it('should match percentage for course when no examId provided', async () => {
            const gradeDTO = { gradeName: 'Pass', isPassingGrade: true };
            const resultPromise = lastValueFrom(service.matchPercentageToGradeStep(75, courseId));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/grading-scale/match-grade-step?gradePercentage=75`);
            req.flush(gradeDTO);

            const result = await resultPromise;
            expect(result).toEqual(gradeDTO);
        });

        it('should match percentage for exam when examId provided', async () => {
            const gradeDTO = { gradeName: 'Pass', isPassingGrade: true };
            const resultPromise = lastValueFrom(service.matchPercentageToGradeStep(75, courseId, examId));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/exams/${examId}/grading-scale/match-grade-step?gradePercentage=75`);
            req.flush(gradeDTO);

            const result = await resultPromise;
            expect(result).toEqual(gradeDTO);
        });

        it('should return undefined when response body is null', async () => {
            const resultPromise = lastValueFrom(service.matchPercentageToGradeStep(75, courseId));
            const req = httpMock.expectOne(`api/assessment/courses/${courseId}/grading-scale/match-grade-step?gradePercentage=75`);
            req.flush(null);

            const result = await resultPromise;
            expect(result).toBeUndefined();
        });
    });

    describe('findWithBonusGradeTypeForInstructor', () => {
        it('should search for grading scales with bonus grade type', async () => {
            const pageable = { pageSize: 10, page: 0, sortingOrder: SortingOrder.ASCENDING, searchTerm: 'test', sortedColumn: 'title' };
            const searchResult = { resultsOnPage: [gradingScale], numberOfPages: 1 };
            const responsePromise = lastValueFrom(service.findWithBonusGradeTypeForInstructor(pageable));
            const req = httpMock.expectOne((r) => r.url === 'api/assessment/grading-scales');
            expect(req.request.method).toBe('GET');
            expect(req.request.params.get('pageSize')).toBe('10');
            expect(req.request.params.get('page')).toBe('0');
            expect(req.request.params.get('sortingOrder')).toBe('ASCENDING');
            expect(req.request.params.get('searchTerm')).toBe('test');
            expect(req.request.params.get('sortedColumn')).toBe('title');
            req.flush(searchResult);

            const response = await responsePromise;
            expect(response.body).toEqual(searchResult);
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

        it('should return undefined and capture exception for non-numeric grade name', () => {
            expect(service.getNumericValueForGradeName('Pass')).toBeUndefined();
        });
    });
});
