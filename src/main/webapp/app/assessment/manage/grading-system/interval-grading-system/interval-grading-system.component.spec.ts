import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { IntervalGradingSystemComponent } from 'app/assessment/manage/grading-system/interval-grading-system/interval-grading-system.component';
import { GradeType, GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { MockProvider } from 'ng-mocks';
import { GradeStep } from 'app/assessment/shared/entities/grade-step.model';
import { cloneDeep } from 'lodash-es';
import { of } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpResponse, provideHttpClient } from '@angular/common/http';

describe('Interval Grading System Component', () => {
    setupTestBed({ zoneless: true });
    let comp: IntervalGradingSystemComponent;
    let fixture: ComponentFixture<IntervalGradingSystemComponent>;

    const route = { parent: { params: of({ courseId: 1, examId: 1 }) } } as any as ActivatedRoute;

    const gradeStep1: GradeStep = {
        gradeName: 'Fail',
        lowerBoundPercentage: 0,
        upperBoundPercentage: 40,
        lowerBoundInclusive: true,
        upperBoundInclusive: false,
        isPassingGrade: false,
    };
    const gradeStep2: GradeStep = {
        gradeName: 'Pass',
        lowerBoundPercentage: 40,
        upperBoundPercentage: 65,
        lowerBoundInclusive: true,
        upperBoundInclusive: false,
        isPassingGrade: true,
    };
    const gradeStep3: GradeStep = {
        gradeName: 'Excellent',
        lowerBoundPercentage: 65,
        upperBoundPercentage: 100,
        lowerBoundInclusive: true,
        upperBoundInclusive: true,
        isPassingGrade: true,
    };

    const gradeStep4: GradeStep = {
        gradeName: 'Sticky',
        lowerBoundPercentage: 100,
        upperBoundPercentage: 200,
        lowerBoundInclusive: false,
        upperBoundInclusive: true,
        isPassingGrade: true,
    };
    const gradeSteps = [gradeStep1, gradeStep2, gradeStep3, gradeStep4];

    const exam = new Exam();
    exam.examMaxPoints = 100;
    const course = new Course();
    course.maxPoints = 100;

    function validateGradeStepBounds(actualGradeStepRow: any, percentageLowerBound: number, percentageUpperBound: number, maxPoints: number) {
        expect(actualGradeStepRow.lowerBoundPercentage).toBe(percentageLowerBound);
        expect(actualGradeStepRow.upperBoundPercentage).toBe(percentageUpperBound);

        const multiplier = maxPoints / 100;
        expect(actualGradeStepRow.lowerBoundPoints).toBe(percentageLowerBound * multiplier);
        expect(actualGradeStepRow.upperBoundPoints).toBe(percentageUpperBound * multiplier);
    }

    let examService: ExamManagementService;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                MockProvider(ExamManagementService),
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
            ],
        })
            .compileComponents()
            .then(() => {
                examService = TestBed.inject(ExamManagementService);
                // Mock examService.find by default BEFORE creating component to prevent undefined subscribe errors
                vi.spyOn(examService, 'find').mockReturnValue(of(new HttpResponse<Exam>({ body: exam })));

                fixture = TestBed.createComponent(IntervalGradingSystemComponent);
                comp = fixture.componentInstance;

                comp.gradingScale = new GradingScale();
                comp.gradingScale.gradeSteps = cloneDeep(gradeSteps);
                comp.courseId = 123;
                comp.examId = 456;
                comp.firstPassingGrade = 'Pass';
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should generate default grading scale', () => {
        comp.generateDefaultGradingScale();

        expect(comp.gradingScale.gradeType).toStrictEqual(GradeType.GRADE);
        expect(comp.firstPassingGrade).toBe('4.0');
        expect(comp.lowerBoundInclusivity).toBe(true);
        expect(comp.gradingScale.gradeSteps).toHaveLength(13);
        comp.gradingScale.gradeSteps.forEach((gradeStep) => {
            expect(gradeStep.id).toBeUndefined();
            expect(gradeStep.gradeName).toBeDefined();

            // No need to test parts that are intrinsic to DetailedGradingSystemComponent.

            expect(comp.getPercentageInterval(gradeStep)).toBe(gradeStep.upperBoundPercentage - gradeStep.lowerBoundPercentage);
        });
    });

    it('should generate default grading scale with max points set', () => {
        const maxPoints = 200;
        comp.maxPoints = maxPoints;
        comp.onChangeMaxPoints(maxPoints);

        comp.generateDefaultGradingScale();

        comp.gradingScale.gradeSteps.forEach((gradeStep) => {
            expect(comp.getPointsInterval(gradeStep)).toBe(gradeStep.upperBoundPoints! - gradeStep.lowerBoundPoints!);
        });
    });

    it('should delete grade step', () => {
        const maxPoints = 200;
        comp.maxPoints = maxPoints;
        comp.onChangeMaxPoints(maxPoints);

        comp.deleteGradeStep(1);

        expect(comp.gradingScale.gradeSteps).toHaveLength(3);
        expect(comp.gradingScale.gradeSteps).not.toContain(gradeStep2);

        validateGradeStepBounds(comp.gradingScale.gradeSteps[0], 0, 40, maxPoints);
        validateGradeStepBounds(comp.gradingScale.gradeSteps[1], 40, 75, maxPoints);
        validateGradeStepBounds(comp.gradingScale.gradeSteps[2], 75, 175, maxPoints);
    });

    it('should create grade step', () => {
        comp.lowerBoundInclusivity = true;

        comp.createGradeStep();

        expect(comp.gradingScale.gradeSteps).toHaveLength(5);

        const newGradeStep = comp.gradingScale.gradeSteps[3];
        expect(newGradeStep.id).toBeUndefined();
        expect(newGradeStep.gradeName).toBe('');
        expect(newGradeStep.lowerBoundPercentage).toBe(100);
        expect(newGradeStep.upperBoundPercentage).toBe(100);
        expect(newGradeStep.isPassingGrade).toBe(true);
        expect(newGradeStep.lowerBoundInclusive).toBe(true);
        expect(newGradeStep.upperBoundInclusive).toBe(false);

        // Previous gradeStep.upperBoundPercentage is already 100.
        expect(comp.getPercentageInterval(newGradeStep)).toBe(0);
    });

    it('should delete grade names correctly', () => {
        comp.deleteGradeNames();

        comp.gradingScale.gradeSteps.forEach((gradeStep) => {
            expect(gradeStep.gradeName).toBe('');
        });
    });

    it('should set all grade step percentage intervals correctly', () => {
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[0])).toBe(40);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[1])).toBe(25);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[2])).toBe(35);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[3])).toBe(100);
    });

    it('should set all grade step point intervals correctly', () => {
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[0])).toBeUndefined();
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[1])).toBeUndefined();
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[2])).toBeUndefined();
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[3])).toBeUndefined();

        const multiplier = 2;
        const maxPoints = multiplier * 100;
        comp.maxPoints = maxPoints;
        comp.onChangeMaxPoints(maxPoints);

        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[0])).toBe(40 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[1])).toBe(25 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[2])).toBe(35 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[3])).toBe(100 * multiplier);

        const negativeMaxPoints = -10;
        comp.maxPoints = negativeMaxPoints;
        comp.onChangeMaxPoints(negativeMaxPoints);

        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[0])).toBeUndefined();
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[1])).toBeUndefined();
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[2])).toBeUndefined();
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[3])).toBeUndefined();
    });

    it('should cascade percentage interval increase', () => {
        const multiplier = 2;
        const maxPoints = multiplier * 100;
        comp.maxPoints = maxPoints;
        comp.onChangeMaxPoints(maxPoints);

        comp.setPercentageInterval(1, 50);

        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[0])).toBe(40);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[1])).toBe(50);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[2])).toBe(35);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[3])).toBe(100);

        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[0])).toBe(40 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[1])).toBe(50 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[2])).toBe(35 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[3])).toBe(100 * multiplier);

        validateGradeStepBounds(comp.gradingScale.gradeSteps[0], 0, 40, maxPoints);
        validateGradeStepBounds(comp.gradingScale.gradeSteps[1], 40, 90, maxPoints);
        validateGradeStepBounds(comp.gradingScale.gradeSteps[2], 90, 125, maxPoints);
        validateGradeStepBounds(comp.gradingScale.gradeSteps[3], 125, 225, maxPoints);
    });

    it('should cascade percentage interval decrease', () => {
        const multiplier = 2;
        const maxPoints = multiplier * 100;
        comp.maxPoints = maxPoints;
        comp.onChangeMaxPoints(maxPoints);

        comp.setPercentageInterval(1, 10);

        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[0])).toBe(40);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[1])).toBe(10);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[2])).toBe(35);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[3])).toBe(100);

        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[0])).toBe(40 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[1])).toBe(10 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[2])).toBe(35 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[3])).toBe(100 * multiplier);

        validateGradeStepBounds(comp.gradingScale.gradeSteps[0], 0, 40, maxPoints);
        validateGradeStepBounds(comp.gradingScale.gradeSteps[1], 40, 50, maxPoints);
        validateGradeStepBounds(comp.gradingScale.gradeSteps[2], 50, 85, maxPoints);
        validateGradeStepBounds(comp.gradingScale.gradeSteps[3], 85, 185, maxPoints);
    });

    it('should cascade points interval increase', () => {
        const multiplier = 2;
        const maxPoints = multiplier * 100;
        comp.maxPoints = maxPoints;
        comp.onChangeMaxPoints(maxPoints);

        comp.setPointsInterval(1, 50 * multiplier);

        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[0])).toBe(40);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[1])).toBe(50);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[2])).toBe(35);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[3])).toBe(100);

        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[0])).toBe(40 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[1])).toBe(50 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[2])).toBe(35 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[3])).toBe(100 * multiplier);

        validateGradeStepBounds(comp.gradingScale.gradeSteps[0], 0, 40, maxPoints);
        validateGradeStepBounds(comp.gradingScale.gradeSteps[1], 40, 90, maxPoints);
        validateGradeStepBounds(comp.gradingScale.gradeSteps[2], 90, 125, maxPoints);
        validateGradeStepBounds(comp.gradingScale.gradeSteps[3], 125, 225, maxPoints);
    });

    it('should cascade points interval decrease', () => {
        const multiplier = 2;
        const maxPoints = multiplier * 100;
        comp.maxPoints = maxPoints;
        comp.onChangeMaxPoints(maxPoints);

        comp.setPointsInterval(1, 10 * multiplier);

        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[0])).toBe(40);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[1])).toBe(10);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[2])).toBe(35);
        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[3])).toBe(100);

        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[0])).toBe(40 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[1])).toBe(10 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[2])).toBe(35 * multiplier);
        expect(comp.getPointsInterval(comp.gradingScale.gradeSteps[3])).toBe(100 * multiplier);

        validateGradeStepBounds(comp.gradingScale.gradeSteps[0], 0, 40, maxPoints);
        validateGradeStepBounds(comp.gradingScale.gradeSteps[1], 40, 50, maxPoints);
        validateGradeStepBounds(comp.gradingScale.gradeSteps[2], 50, 85, maxPoints);
        validateGradeStepBounds(comp.gradingScale.gradeSteps[3], 85, 185, maxPoints);
    });

    it('should throw on points interval change when max points are not defined', () => {
        expect(comp.maxPoints).toBeUndefined();
        expect(() => {
            comp.setPointsInterval(0, 10);
        }).toThrow();
    });

    it('should prevent total percentage is less than 100 when only sticky step remains', () => {
        comp.deleteGradeStep(0);
        comp.deleteGradeStep(0);
        comp.deleteGradeStep(0);

        expect(comp.getPercentageInterval(comp.gradingScale.gradeSteps[0])).toBe(100);
    });

    it('should create the initial step when grading scale is empty', () => {
        comp.gradingScale = new GradingScale();
        comp.lowerBoundInclusivity = true;

        comp.createGradeStep();

        expect(comp.gradingScale.gradeSteps[0].lowerBoundInclusive).toBe(true);
        expect(comp.gradingScale.gradeSteps[0].upperBoundInclusive).toBe(false);

        expect(comp.gradingScale.gradeSteps[0].lowerBoundPercentage).toBe(0);
        expect(comp.gradingScale.gradeSteps[0].upperBoundPercentage).toBe(100);

        expect(comp.gradingScale.gradeSteps[1].lowerBoundInclusive).toBe(true);
        expect(comp.gradingScale.gradeSteps[1].upperBoundInclusive).toBe(false);

        expect(comp.gradingScale.gradeSteps[1].lowerBoundPercentage).toBe(100);

        expect(comp.gradingScale.gradeSteps).toHaveLength(2);
    });

    it('should handle inclusivity setting when there are no grade steps', () => {
        comp.gradingScale = new GradingScale();

        comp.setInclusivity();

        expect(comp.gradingScale.gradeSteps).toHaveLength(0);
    });

    it('should set inclusivity to lower bound inclusive', () => {
        comp.lowerBoundInclusivity = true;
        comp.setInclusivity();

        expect(comp.gradingScale.gradeSteps[0].lowerBoundInclusive).toBe(true);
        expect(comp.gradingScale.gradeSteps[0].upperBoundInclusive).toBe(false);

        expect(comp.gradingScale.gradeSteps[1].lowerBoundInclusive).toBe(true);
        expect(comp.gradingScale.gradeSteps[1].upperBoundInclusive).toBe(false);

        expect(comp.gradingScale.gradeSteps[2].lowerBoundInclusive).toBe(true);
        expect(comp.gradingScale.gradeSteps[2].upperBoundInclusive).toBe(false);

        expect(comp.gradingScale.gradeSteps[3].lowerBoundInclusive).toBe(true);
        expect(comp.gradingScale.gradeSteps[3].upperBoundInclusive).toBe(true);
    });

    it('should set inclusivity to upper bound inclusive', () => {
        comp.lowerBoundInclusivity = false;
        comp.setInclusivity();

        expect(comp.gradingScale.gradeSteps[0].lowerBoundInclusive).toBe(true);
        expect(comp.gradingScale.gradeSteps[0].upperBoundInclusive).toBe(true);

        expect(comp.gradingScale.gradeSteps[1].lowerBoundInclusive).toBe(false);
        expect(comp.gradingScale.gradeSteps[1].upperBoundInclusive).toBe(true);

        expect(comp.gradingScale.gradeSteps[2].lowerBoundInclusive).toBe(false);
        expect(comp.gradingScale.gradeSteps[2].upperBoundInclusive).toBe(true);

        expect(comp.gradingScale.gradeSteps[3].lowerBoundInclusive).toBe(false);
        expect(comp.gradingScale.gradeSteps[3].upperBoundInclusive).toBe(true);
    });

    it('should not show grading steps above max points warning', () => {
        const result = comp.shouldShowGradingStepsAboveMaxPointsWarning();
        expect(result).toBe(false);
    });

    it('should show grading steps above max points warning for inclusive bound', () => {
        const gradeStep: GradeStep = {
            gradeName: 'Step',
            lowerBoundPercentage: 100,
            upperBoundPercentage: 101,
            lowerBoundInclusive: true,
            upperBoundInclusive: true,
            isPassingGrade: true,
        };
        comp.gradingScale.gradeSteps = [gradeStep1, gradeStep2, gradeStep3, gradeStep, gradeStep4];

        const result = comp.shouldShowGradingStepsAboveMaxPointsWarning();
        expect(result).toBe(true);
    });

    it('should show grading steps above max points warning for exclusive bound', () => {
        const gradeStep: GradeStep = {
            gradeName: 'Step',
            lowerBoundPercentage: 100,
            upperBoundPercentage: 100,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        comp.gradingScale.gradeSteps = [gradeStep1, gradeStep2, gradeStep3, gradeStep, gradeStep4];

        const result = comp.shouldShowGradingStepsAboveMaxPointsWarning();
        expect(result).toBe(true);
    });
});
