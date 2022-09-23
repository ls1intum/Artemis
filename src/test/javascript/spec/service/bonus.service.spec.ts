import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { take } from 'rxjs/operators';
import { RouterTestingModule } from '@angular/router/testing';
import { GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';
import { BonusService } from 'app/grading-system/bonus/bonus.service';
import { Bonus, BonusExample, BonusStrategy } from 'app/entities/bonus.model';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { cloneDeep } from 'lodash-es';

describe('Bonus Service', () => {
    type GradeStepBuilder = {
        interval: number;
        gradeName: string;
        isPassingGrade?: boolean;
    };

    /**
     * A test helper to generate grade steps from as few arguments as possible.
     *
     * @param gradeStepBuiilders grade step name and range defined by interval
     * @param lowerBoundInclusive whether the lower or upper bound is included in a given grade step
     */
    function generateGradeSteps(gradeStepBuiilders: GradeStepBuilder[], lowerBoundInclusive: boolean): GradeStep[] {
        let isPassingGrade = false;
        let currentLowerBound = 0;
        const gradeSteps = gradeStepBuiilders.map((gradeStepBuiilder) => {
            isPassingGrade ||= !!gradeStepBuiilder.isPassingGrade;
            const gradeStep: GradeStep = {
                lowerBoundPercentage: currentLowerBound,
                upperBoundPercentage: currentLowerBound + gradeStepBuiilder.interval,
                lowerBoundInclusive,
                upperBoundInclusive: !lowerBoundInclusive,
                isPassingGrade,
                gradeName: gradeStepBuiilder.gradeName,
            };
            currentLowerBound = gradeStep.upperBoundPercentage;
            return gradeStep;
        });
        if (gradeSteps.length > 1) {
            // Ensure 100 percent is not a part of the sticky grade step.
            gradeSteps[gradeSteps.length - 2].upperBoundInclusive = true;
            const stickyGradeStep = gradeSteps.last()!;
            stickyGradeStep.lowerBoundInclusive = false;
            stickyGradeStep.upperBoundInclusive = true;
        }
        return gradeSteps;
    }

    let service: BonusService;
    let httpMock: HttpTestingController;
    let elemDefault: GradingScale;

    const bonusToGradeStepsDTO: GradeStepsDTO = {
        title: 'Exam Title',
        gradeType: GradeType.GRADE,
        maxPoints: 200,
        gradeSteps: generateGradeSteps(
            [
                { interval: 40, gradeName: '5.0' },
                { interval: 20, gradeName: '4.0', isPassingGrade: true },
                { interval: 15, gradeName: '3.0' },
                { interval: 15, gradeName: '2.0' },
                { interval: 10, gradeName: '1.0' },
                { interval: 100, gradeName: '1.0+' }, // sticky grade step
            ],
            true,
        ),
    };

    const sourceGradingScale = {
        id: 7,
        gradeType: GradeType.BONUS,
        course: {
            id: 123,
            title: 'Test Course',
            maxPoints: 200,
        },
        gradeSteps: generateGradeSteps(
            [
                { interval: 30, gradeName: '0.0' },
                { interval: 40, gradeName: '0.1' },
                { interval: 100, gradeName: '0.2' }, // sticky grade step
            ],
            true,
        ),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, RouterTestingModule],
        });
        service = TestBed.inject(BonusService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new GradingScale();
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should filter bonus before serializing into request', fakeAsync(() => {
        const filteredBonus: Bonus = {
            id: 3,
            weight: 1,
            bonusStrategy: BonusStrategy.POINTS,
            sourceGradingScale: { id: 5 } as GradingScale,
        };

        const bonusToSend: Bonus = {
            ...filteredBonus,
            sourceGradingScale: {
                id: filteredBonus.sourceGradingScale!.id,
                gradeSteps: [{} as GradeStep],
            } as GradingScale,
            bonusToGradingScale: { id: 6 } as GradingScale,
        };
        service.updateBonus(1, 6, bonusToSend).pipe(take(1)).subscribe();

        const httpRequest = httpMock.expectOne({ method: 'PUT' });
        expect(httpRequest.request.body).toEqual(filteredBonus);
        tick();
    }));

    it.each([
        [5, 10, 1, false],
        [5, 10, -1, true],
        [10, 10, 1, false],
        [10, 10, -1, false],
        [20, 10, 1, true],
        [20, 10, -1, false],
        [1.4, 1.3, 1, true],
        [1.4, 1.3, -1, false],
        [5, 10, 2, false],
        [10, 10, -2, false],
    ])('should check does bonus exceed max [%p, %p, %p, %p]', (valueWithBonus: number, maxValue: number, calculationSign: number, expected: boolean) => {
        const result = service.doesBonusExceedMax(valueWithBonus, maxValue, calculationSign);
        expect(result).toBe(expected);
    });

    it.each([
        [
            {
                lowerBoundPoints: 40,
                upperBoundPoints: 80,
                lowerBoundInclusive: true,
                upperBoundInclusive: false,
            } as GradeStep,
            100,
            40,
        ],
        [
            {
                lowerBoundPoints: 40,
                upperBoundPoints: 80,
                lowerBoundInclusive: false,
                upperBoundInclusive: true,
            } as GradeStep,
            100,
            80,
        ],
        [
            {
                lowerBoundPoints: 40,
                upperBoundPoints: 120,
                lowerBoundInclusive: false,
                upperBoundInclusive: true,
            } as GradeStep,
            100,
            100,
        ],
        [
            {
                lowerBoundPoints: 40,
                upperBoundPoints: 80,
                lowerBoundInclusive: true,
                upperBoundInclusive: true,
            } as GradeStep,
            100,
            40,
        ],
        [
            {
                lowerBoundPoints: 40,
                upperBoundPoints: 80,
                lowerBoundInclusive: false,
                upperBoundInclusive: false,
            } as GradeStep,
            100,
            undefined,
        ],
    ])('should get included boundary points [%p, %p, %p, %p]', (gradeStep: GradeStep, maxPoints: number, expected: number) => {
        const result = service.getIncludedBoundaryPoints(gradeStep, maxPoints);
        expect(result).toBe(expected);
    });

    it.each([
        {
            studentPointsOfBonusTo: 50,
            studentPointsOfBonusSource: 100,
            examGrade: '5.0',
            bonusGrade: 0,
            finalPoints: 50,
            finalGrade: '5.0',
            exceedsMax: false,
        },
        {
            studentPointsOfBonusTo: 120,
            studentPointsOfBonusSource: 75,
            examGrade: '3.0',
            bonusGrade: 0.1,
            finalPoints: undefined,
            finalGrade: 2.9,
            exceedsMax: false,
        },
        {
            studentPointsOfBonusTo: 200,
            studentPointsOfBonusSource: 200,
            examGrade: '1.0',
            bonusGrade: 0.2,
            finalPoints: undefined,
            finalGrade: '1.0',
            exceedsMax: true,
        },
    ] as BonusExample[])('should check final grade is calculated correctly with BonusStrategy.GRADES_CONTINUOUS %j', (expectedBonusExample: BonusExample) => {
        // Calculation results should be consistent with BonusIntegrationTest.java

        const bonusExample = new BonusExample(expectedBonusExample.studentPointsOfBonusTo, expectedBonusExample.studentPointsOfBonusSource);
        const bonus: Bonus = {
            id: 77,
            bonusStrategy: BonusStrategy.GRADES_CONTINUOUS,
            weight: -1,
            sourceGradingScale,
        };

        service.calculateFinalGrade(bonusExample, bonus, bonusToGradeStepsDTO);

        expect(bonusExample).toEqual(expectedBonusExample);
    });

    it.each([
        {
            studentPointsOfBonusTo: 50,
            studentPointsOfBonusSource: 100,
            examGrade: '5.0',
            bonusGrade: 0,
            finalPoints: 50,
            finalGrade: '5.0',
            exceedsMax: false,
        },
        {
            studentPointsOfBonusTo: 120,
            studentPointsOfBonusSource: 75,
            examGrade: '3.0',
            bonusGrade: 10,
            finalPoints: 130,
            finalGrade: '3.0',
            exceedsMax: false,
        },
        {
            studentPointsOfBonusTo: 200,
            studentPointsOfBonusSource: 200,
            examGrade: '1.0',
            bonusGrade: 20,
            finalPoints: 200,
            finalGrade: '1.0',
            exceedsMax: true,
        },
    ] as BonusExample[])('should check final grade is calculated correctly with BonusStrategy.POINTS %j', (expectedBonusExample: BonusExample) => {
        // Calculation results should be consistent with BonusIntegrationTest.java

        const bonusExample = new BonusExample(expectedBonusExample.studentPointsOfBonusTo, expectedBonusExample.studentPointsOfBonusSource);
        const sourceGradingScaleForPoints = {
            id: 7,
            gradeType: GradeType.BONUS,
            course: {
                id: 123,
                title: 'Test Course',
                maxPoints: 200,
            },
            gradeSteps: generateGradeSteps(
                [
                    { interval: 30, gradeName: '0' },
                    { interval: 40, gradeName: '10' },
                    { interval: 100, gradeName: '20' },
                ],
                true,
            ),
        };

        const bonus: Bonus = {
            id: 77,
            weight: 1,
            sourceGradingScale: sourceGradingScaleForPoints,
            bonusStrategy: BonusStrategy.POINTS,
        };

        service.calculateFinalGrade(bonusExample, bonus, bonusToGradeStepsDTO);

        expect(bonusExample).toEqual(expectedBonusExample);
    });

    it('should generate bonus examples', () => {
        const expectedBonusExamples = [
            {
                studentPointsOfBonusTo: 0,
                exceedsMax: false,
                examGrade: '5.0',
                bonusGrade: 0,
                finalPoints: 0,
                finalGrade: '5.0',
            },
            {
                studentPointsOfBonusTo: 80,
                studentPointsOfBonusSource: 200,
                exceedsMax: false,
                examGrade: '4.0',
                bonusGrade: 0.2,
                finalGrade: 3.8,
            },
            {
                studentPointsOfBonusTo: 120,
                studentPointsOfBonusSource: 60,
                exceedsMax: false,
                examGrade: '3.0',
                bonusGrade: 0.1,
                finalGrade: 2.9,
            },
            {
                studentPointsOfBonusTo: 150,
                studentPointsOfBonusSource: 0,
                exceedsMax: false,
                examGrade: '2.0',
                bonusGrade: 0,
                finalGrade: 2,
            },
            {
                studentPointsOfBonusTo: 200,
                studentPointsOfBonusSource: 200,
                exceedsMax: true,
                examGrade: '1.0',
                bonusGrade: 0.2,
                finalGrade: '1.0',
            },
        ];

        const bonus: Bonus = {
            id: 77,
            bonusStrategy: BonusStrategy.GRADES_CONTINUOUS,
            weight: -1,
            sourceGradingScale,
        };

        const gradingSystemService = TestBed.inject(GradingSystemService);
        gradingSystemService.setGradePoints(sourceGradingScale.gradeSteps, sourceGradingScale.course.maxPoints);
        gradingSystemService.setGradePoints(bonusToGradeStepsDTO.gradeSteps, bonusToGradeStepsDTO.maxPoints!);

        const bonusExamples = service.generateBonusExamples(bonus, bonusToGradeStepsDTO);
        expect(bonusExamples).toEqual(expectedBonusExamples);
    });

    it('should throw when generating example without sourceGradingScale', () => {
        const bonus: Bonus = {
            id: 77,
            weight: -1,
            bonusStrategy: BonusStrategy.GRADES_CONTINUOUS,
        };

        expect(() => service.generateBonusExamples(bonus, bonusToGradeStepsDTO)).toThrow(Error);
    });

    it('should generate examples with default bound points if no boundary points are included', () => {
        const expectedBonusExamples = [
            {
                studentPointsOfBonusTo: 0,
                exceedsMax: false,
                bonusGrade: 0,
                finalPoints: 0,
            },
            {
                studentPointsOfBonusTo: 100,
                studentPointsOfBonusSource: 200,
                exceedsMax: false,
                examGrade: '4.0',
                bonusGrade: 0.2,
                finalGrade: 3.8,
            },
            {
                studentPointsOfBonusTo: 135,
                studentPointsOfBonusSource: 60,
                exceedsMax: false,
                examGrade: '3.0',
                bonusGrade: 0.1,
                finalGrade: 2.9,
            },
            {
                studentPointsOfBonusTo: 165,
                studentPointsOfBonusSource: 0,
                exceedsMax: false,
                examGrade: '2.0',
                bonusGrade: 0,
                finalGrade: 2,
            },
            {
                studentPointsOfBonusTo: 300,
                studentPointsOfBonusSource: 200,
                exceedsMax: false,
                examGrade: '1.0+',
                bonusGrade: 0.2,
                finalGrade: 0.8,
            },
        ];
        const bonus: Bonus = {
            id: 77,
            bonusStrategy: BonusStrategy.GRADES_CONTINUOUS,
            weight: -1,
            sourceGradingScale,
        };

        const bonusToGradeStepsDTOWithoutIncludedBounds = cloneDeep(bonusToGradeStepsDTO);
        bonusToGradeStepsDTOWithoutIncludedBounds.gradeSteps.forEach((gradeStep) => {
            gradeStep.lowerBoundInclusive = false;
            gradeStep.upperBoundInclusive = false;
        });

        const gradingSystemService = TestBed.inject(GradingSystemService);
        gradingSystemService.setGradePoints(sourceGradingScale.gradeSteps, sourceGradingScale.course.maxPoints);
        gradingSystemService.setGradePoints(bonusToGradeStepsDTOWithoutIncludedBounds.gradeSteps, bonusToGradeStepsDTOWithoutIncludedBounds.maxPoints!);

        const bonusExamples = service.generateBonusExamples(bonus, bonusToGradeStepsDTOWithoutIncludedBounds);
        expect(bonusExamples).toEqual(expectedBonusExamples);
    });

    it('should not generate examples if there are no passing grades in bonusToGradingScale', () => {
        const bonus: Bonus = {
            id: 77,
            bonusStrategy: BonusStrategy.GRADES_CONTINUOUS,
            weight: -1,
            sourceGradingScale,
        };

        const bonusToGradeStepsDTOWithoutPassingGrade = {
            ...bonusToGradeStepsDTO,
            gradeSteps: generateGradeSteps([{ interval: 40, gradeName: '5.0' }], true),
        };

        expect(() => service.generateBonusExamples(bonus, bonusToGradeStepsDTOWithoutPassingGrade)).toThrow(Error);
    });
});
