import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { BonusComponent, BonusStrategyDiscreteness, BonusStrategyOption } from 'app/grading-system/bonus/bonus.component';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { BonusService, EntityResponseType } from 'app/grading-system/bonus/bonus.service';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { ModePickerComponent } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { PageableSearch, SearchResult, SortingOrder } from 'app/shared/table/pageable-table';
import { TableColumn } from 'app/exercises/modeling/manage/modeling-exercise-import.component';
import { of, throwError } from 'rxjs';
import { Bonus, BonusExample, BonusStrategy } from 'app/entities/bonus.model';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';
import { HttpResponse } from '@angular/common/http';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { NgModel } from '@angular/forms';

describe('BonusComponent', () => {
    let component: BonusComponent;
    let fixture: ComponentFixture<BonusComponent>;

    let bonusService: BonusService;
    let gradingSystemService: GradingSystemService;

    const courseId = 1;
    const examId = 2;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId, examId }) } } as any as ActivatedRoute;

    const sourceGradingScale = {
        id: 7,
        gradeType: GradeType.BONUS,
        exam: {
            id: 3,
            title: 'Grade Test Exam',
            maxPoints: 150,
            course: {
                id: courseId,
                title: 'Test Course',
                maxPoints: 200,
            },
        },
        gradeSteps: [
            {
                id: 354,
                lowerBoundPercentage: 0,
                lowerBoundInclusive: true,
                upperBoundPercentage: 10,
                upperBoundInclusive: true,
                gradeName: '0',
                isPassingGrade: false,
            },
            {
                id: 357,
                lowerBoundPercentage: 10,
                lowerBoundInclusive: false,
                upperBoundPercentage: 25,
                upperBoundInclusive: true,
                gradeName: '3',
                isPassingGrade: false,
            },
            {
                id: 359,
                lowerBoundPercentage: 55,
                lowerBoundInclusive: false,
                upperBoundPercentage: 70,
                upperBoundInclusive: true,
                gradeName: '12',
                isPassingGrade: false,
                numericValue: 12,
            },
            {
                id: 361,
                lowerBoundPercentage: 25,
                lowerBoundInclusive: false,
                upperBoundPercentage: 40,
                upperBoundInclusive: true,
                gradeName: '6',
                isPassingGrade: false,
            },
            {
                id: 364,
                lowerBoundPercentage: 70,
                lowerBoundInclusive: false,
                upperBoundPercentage: 170,
                upperBoundInclusive: true,
                gradeName: '20',
                isPassingGrade: false,
                numericValue: 20,
            },
            {
                id: 365,
                lowerBoundPercentage: 40,
                lowerBoundInclusive: false,
                upperBoundPercentage: 55,
                upperBoundInclusive: true,
                gradeName: '9',
                isPassingGrade: false,
            },
        ],
    };
    const bonus: Bonus = {
        id: 7,
        bonusStrategy: BonusStrategy.POINTS,
        weight: 1,
        sourceGradingScale,
    };

    const examGradeSteps: GradeStepsDTO = {
        title: 'Title',
        gradeType: GradeType.GRADE,
        maxPoints: 100,
        gradeSteps: [
            {
                id: 577,
                lowerBoundPercentage: 40,
                lowerBoundInclusive: true,
                upperBoundPercentage: 50,
                upperBoundInclusive: false,
                gradeName: '4.3',
                isPassingGrade: false,
            },
            {
                id: 578,
                lowerBoundPercentage: 0,
                lowerBoundInclusive: true,
                upperBoundPercentage: 40,
                upperBoundInclusive: false,
                gradeName: '5.0',
                isPassingGrade: false,
            },
            {
                id: 581,
                lowerBoundPercentage: 50,
                lowerBoundInclusive: true,
                upperBoundPercentage: 55,
                upperBoundInclusive: false,
                gradeName: '4.0',
                isPassingGrade: true,
            },
            {
                id: 584,
                lowerBoundPercentage: 100,
                lowerBoundInclusive: false,
                upperBoundPercentage: 200,
                upperBoundInclusive: true,
                gradeName: '1.0+',
                isPassingGrade: true,
            },
            {
                id: 585,
                lowerBoundPercentage: 85,
                lowerBoundInclusive: true,
                upperBoundPercentage: 100,
                upperBoundInclusive: true,
                gradeName: '1.0',
                isPassingGrade: true,
            },
            {
                id: 586,
                lowerBoundPercentage: 55,
                lowerBoundInclusive: true,
                upperBoundPercentage: 70,
                upperBoundInclusive: false,
                gradeName: '3.0',
                isPassingGrade: true,
            },
            {
                id: 589,
                lowerBoundPercentage: 70,
                lowerBoundInclusive: true,
                upperBoundPercentage: 85,
                upperBoundInclusive: false,
                gradeName: '2.0',
                isPassingGrade: true,
            },
        ],
    };

    const searchResult: SearchResult<GradingScale> = {
        resultsOnPage: [sourceGradingScale],
        numberOfPages: 1,
    };

    const bonusExamples: BonusExample[] = [
        {
            studentPointsOfBonusTo: 0,
            studentPointsOfBonusSource: undefined,
            exceedsMax: false,
            examGrade: '5.0',
            bonusGrade: 0,
            finalPoints: 0,
            finalGrade: '5.0',
        },
        {
            studentPointsOfBonusTo: 50,
            studentPointsOfBonusSource: 150,
            exceedsMax: false,
            examGrade: '4.0',
            bonusGrade: 13,
            finalPoints: 63,
            finalGrade: '3.3',
        },
        {
            studentPointsOfBonusTo: 55,
            studentPointsOfBonusSource: 105,
            exceedsMax: false,
            examGrade: '3.7',
            bonusGrade: 12,
            finalPoints: 67,
            finalGrade: '3.0',
        },
        {
            studentPointsOfBonusTo: 60,
            studentPointsOfBonusSource: 97.5,
            exceedsMax: false,
            examGrade: '3.3',
            bonusGrade: 11,
            finalPoints: 71,
            finalGrade: '2.7',
        },
        {
            studentPointsOfBonusTo: 100,
            studentPointsOfBonusSource: 90,
            exceedsMax: true,
            examGrade: '1.0',
            bonusGrade: 10,
            finalPoints: 100,
            finalGrade: '1.0',
        },
    ];

    const bonusStrategyToOptionAndDiscretenessMappings = [
        [BonusStrategy.GRADES_CONTINUOUS, BonusStrategyOption.GRADES, BonusStrategyDiscreteness.CONTINUOUS],
        [BonusStrategy.GRADES_DISCRETE, BonusStrategyOption.GRADES, BonusStrategyDiscreteness.DISCRETE],
        [BonusStrategy.POINTS, BonusStrategyOption.POINTS, undefined],
        [undefined, undefined, undefined],
        [undefined, BonusStrategyOption.GRADES, undefined],
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                BonusComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(SafeHtmlPipe),
                MockComponent(ModePickerComponent),
                MockPipe(GradeStepBoundsPipe),
                MockDirective(NgbTooltip),
                MockComponent(DeleteDialogComponent),
                MockDirective(NgModel),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                MockProvider(GradingSystemService),
                MockProvider(BonusService),
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(BonusComponent);
        component = fixture.componentInstance;
        bonusService = fixture.debugElement.injector.get(BonusService);
        gradingSystemService = fixture.debugElement.injector.get(GradingSystemService);

        jest.spyOn(gradingSystemService, 'findWithBonusGradeTypeForInstructor').mockReturnValue(of({ body: searchResult } as HttpResponse<SearchResult<GradingScale>>));
        jest.spyOn(gradingSystemService, 'findGradeSteps').mockReturnValue(of(examGradeSteps));
        jest.spyOn(bonusService, 'generateBonusExamples').mockReturnValue(bonusExamples);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
        const sortGradeStepsSpy = jest.spyOn(gradingSystemService, 'sortGradeSteps');
        const setGradePointsSpy = jest.spyOn(gradingSystemService, 'setGradePoints');

        const bonusSpy = jest.spyOn(bonusService, 'findBonusForExam').mockReturnValue(of({ body: bonus } as EntityResponseType));

        const gradingScaleSpy = jest.spyOn(gradingSystemService, 'findWithBonusGradeTypeForInstructor');

        const gradeStepsSpy = jest.spyOn(gradingSystemService, 'findGradeSteps');

        const state: PageableSearch = {
            page: 1,
            pageSize: 100,
            searchTerm: '',
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: TableColumn.ID,
        };

        fixture.detectChanges();

        expect(bonusSpy).toHaveBeenCalledOnce();
        expect(bonusSpy).toHaveBeenCalledWith(courseId, examId);

        expect(gradingScaleSpy).toHaveBeenCalledOnce();
        expect(gradingScaleSpy).toHaveBeenCalledWith(state);

        expect(gradeStepsSpy).toHaveBeenCalledOnce();
        expect(gradeStepsSpy).toHaveBeenCalledWith(courseId, examId);

        tick();

        expect(component.isLoading).toBeFalse();
        expect(component.bonus.sourceGradingScale).toEqual(sourceGradingScale);
        expect(component.sourceGradingScales).toEqual(searchResult.resultsOnPage);

        expect(sortGradeStepsSpy).toHaveBeenCalledTimes(2);
        expect(sortGradeStepsSpy).toHaveBeenCalledWith(examGradeSteps.gradeSteps);
        expect(sortGradeStepsSpy).toHaveBeenCalledWith(sourceGradingScale.gradeSteps);

        expect(setGradePointsSpy).toHaveBeenCalledTimes(2);
        expect(setGradePointsSpy).toHaveBeenCalledWith(examGradeSteps.gradeSteps, examGradeSteps.maxPoints);
        expect(setGradePointsSpy).toHaveBeenCalledWith(sourceGradingScale.gradeSteps, undefined);
    }));

    it('should get calculation sign', () => {
        expect(component.getCalculationSign(1)).toBe('+');
        expect(component.getCalculationSign(-1)).toBe('−');
    });

    it.each(bonusStrategyToOptionAndDiscretenessMappings)(
        'should set bonus strategy and discreteness for %p',
        fakeAsync((bonusStrategy: BonusStrategy, bonusStrategyOption: BonusStrategyOption, bonusStrategyDiscreteness: BonusStrategyDiscreteness) => {
            const bonusSpy = jest.spyOn(bonusService, 'findBonusForExam').mockReturnValue(of({ body: { bonusStrategy } } as EntityResponseType));
            component.ngOnInit();
            expect(bonusSpy).toHaveBeenCalledOnce();
            expect(component.currentBonusStrategyOption).toBe(bonusStrategyOption);
            expect(component.currentBonusStrategyDiscreteness).toBe(bonusStrategyDiscreteness);
        }),
    );

    it.each(bonusStrategyToOptionAndDiscretenessMappings)(
        'should convert from inputs to BonusStrategy for %p',
        (bonusStrategy: BonusStrategy, bonusStrategyOption: BonusStrategyOption, bonusStrategyDiscreteness: BonusStrategyDiscreteness) => {
            const actualBonusStrategy = component.convertFromInputsToBonusStrategy(bonusStrategyOption, bonusStrategyDiscreteness);
            expect(actualBonusStrategy).toBe(bonusStrategy);
        },
    );

    it('should generate examples on bonus strategy input change', () => {
        const [bonusStrategy, bonusStrategyOption, bonusStrategyDiscreteness] = bonusStrategyToOptionAndDiscretenessMappings[0];
        component.currentBonusStrategyOption = bonusStrategyOption as BonusStrategyOption;
        component.currentBonusStrategyDiscreteness = bonusStrategyDiscreteness as BonusStrategyDiscreteness;
        component.bonus = { ...bonus };
        component.bonusToGradeStepsDTO = examGradeSteps;

        const bonusSpy = jest.spyOn(bonusService, 'generateBonusExamples').mockReturnValue(bonusExamples);

        expect(component.examples).toBeEmpty();

        component.onBonusStrategyInputChange();

        expect(component.bonus.bonusStrategy).toBe(bonusStrategy);
        expect(bonusSpy).toHaveBeenCalledOnce();
        expect(bonusSpy).toHaveBeenCalledWith({ ...bonus, bonusStrategy }, examGradeSteps);
        expect(component.examples).toHaveLength(bonusExamples.length);
    });

    it('should check bonus strategy and weight mismatch', () => {
        jest.spyOn(gradingSystemService, 'getNumericValueForGradeName').mockImplementation((gradeName) => parseFloat(gradeName!));
        jest.spyOn(bonusService, 'doesBonusExceedMax').mockReturnValue(true);

        component.bonus = { ...bonus, bonusStrategy: BonusStrategy.GRADES_CONTINUOUS, weight: 1 };
        component.bonusToGradeStepsDTO = { gradeSteps: [] as GradeStep[] } as GradeStepsDTO;

        component.generateExamples();

        expect(component.examples).toBeEmpty();
        expect(component.hasBonusStrategyWeightMismatch).toBeTrue();
    });

    it('should remove examples when all required fields are not set', () => {
        component.bonus = { ...bonus, bonusStrategy: undefined };
        component.examples = bonusExamples;

        component.onBonusStrategyInputChange();

        expect(component.examples).toBeEmpty();
    });

    it('should create bonus', fakeAsync(() => {
        const createBonusSpy = jest.spyOn(bonusService, 'createBonusForExam').mockReturnValue(of({ body: bonus } as EntityResponseType));
        const findBonusSpy = jest.spyOn(bonusService, 'findBonusForExam').mockReturnValue(throwError(() => ({ status: 404 })));

        fixture.detectChanges();

        const newBonus = { ...bonus, id: undefined };
        component.bonus = newBonus;
        component.save();

        expect(createBonusSpy).toHaveBeenCalledOnce();
        expect(createBonusSpy).toHaveBeenCalledWith(courseId, examId, newBonus);

        expect(findBonusSpy).toHaveBeenCalledOnce();
        expect(findBonusSpy).toHaveBeenCalledWith(courseId, examId);

        tick();

        expect(component.bonus.id).toBe(bonus.id);
        expect(component.isLoading).toBeFalse();
    }));

    it('should update bonus', fakeAsync(() => {
        const bonusSpy = jest.spyOn(bonusService, 'updateBonus').mockReturnValue(of({ body: bonus } as EntityResponseType));

        component.bonus = bonus;
        component.save();

        expect(bonusSpy).toHaveBeenCalledOnce();
        expect(bonusSpy).toHaveBeenCalledWith(bonus);

        tick();

        expect(component.bonus.id).toBe(bonus.id);
        expect(component.isLoading).toBeFalse();
    }));

    it('should delete bonus', fakeAsync(() => {
        const bonusSpy = jest.spyOn(bonusService, 'deleteBonus').mockReturnValue(of({ body: undefined } as HttpResponse<any>));

        let dialogError: string | undefined = undefined;
        component.dialogError$.subscribe((err) => (dialogError = err));

        component.bonus = bonus;
        component.delete();

        expect(bonusSpy).toHaveBeenCalledOnce();
        expect(bonusSpy).toHaveBeenCalledWith(bonus.id);

        tick();

        expect(component.bonus.id).toBeUndefined();
        expect(component.bonus.bonusStrategy).toBeUndefined();
        expect(component.bonus.weight).toBeUndefined();
        expect(component.bonus.bonusToGradingScale).toBeUndefined();
        expect(component.bonus.sourceGradingScale).toBeUndefined();
        expect(dialogError).toBe('');

        expect(component.isLoading).toBeFalse();
    }));

    it('should show error on delete', fakeAsync(() => {
        const errorMessage = 'Error message';
        const bonusSpy = jest.spyOn(bonusService, 'deleteBonus').mockReturnValue(throwError(() => new Error(errorMessage)));

        let dialogError: string | undefined = undefined;
        component.dialogError$.subscribe((err) => (dialogError = err));

        component.bonus = bonus;
        component.delete();

        expect(bonusSpy).toHaveBeenCalledOnce();
        expect(bonusSpy).toHaveBeenCalledWith(bonus.id);

        tick();

        expect(component.bonus).toEqual(bonus);
        expect(dialogError).toBe(errorMessage);

        expect(component.isLoading).toBeFalse();
    }));

    it('should not delete if id is empty', () => {
        const bonusSpy = jest.spyOn(bonusService, 'deleteBonus');

        const unsavedBonus = { ...bonus, id: undefined };
        component.bonus = unsavedBonus;
        component.delete();

        expect(bonusSpy).not.toHaveBeenCalled();

        expect(component.bonus).toEqual(unsavedBonus);
        expect(component.isLoading).toBeFalse();
    });

    it('should handle find bonus response with error', fakeAsync(() => {
        const findBonusSpy = jest.spyOn(bonusService, 'findBonusForExam').mockReturnValue(throwError(() => ({ status: 500 })));

        component.ngOnInit();

        expect(findBonusSpy).toHaveBeenCalledOnce();
        expect(findBonusSpy).toHaveBeenCalledWith(courseId, examId);

        expect(() => tick()).toThrow();

        expect(component.bonus).toStrictEqual(new Bonus());
        expect(component.isLoading).toBeFalse();
    }));

    it('should handle empty find bonus response', fakeAsync(() => {
        const findBonusSpy = jest.spyOn(bonusService, 'findBonusForExam').mockReturnValue(of({ body: undefined } as any as EntityResponseType));

        component.ngOnInit();

        expect(findBonusSpy).toHaveBeenCalledOnce();
        expect(findBonusSpy).toHaveBeenCalledWith(courseId, examId);

        tick();

        expect(component.bonus).toStrictEqual(new Bonus());
        expect(component.isLoading).toBeFalse();
    }));

    it('should forward grading scale title call to service', () => {
        const gradingSystemSpy = jest.spyOn(gradingSystemService, 'getGradingScaleTitle');

        component.getGradingScaleTitle(bonus.sourceGradingScale!);

        expect(gradingSystemSpy).toHaveBeenCalledOnce();
        expect(gradingSystemSpy).toHaveBeenCalledWith(bonus.sourceGradingScale);
    });

    it('should forward grading scale max points call to service', () => {
        const gradingSystemSpy = jest.spyOn(gradingSystemService, 'getGradingScaleMaxPoints');

        component.getGradingScaleMaxPoints(bonus.sourceGradingScale!);

        expect(gradingSystemSpy).toHaveBeenCalledOnce();
        expect(gradingSystemSpy).toHaveBeenCalledWith(bonus.sourceGradingScale);
    });

    it('should forward has points set call to service', () => {
        const gradingSystemSpy = jest.spyOn(gradingSystemService, 'hasPointsSet');

        component.bonus = { ...bonus, sourceGradingScale: {} as GradingScale };
        component.hasPointsSet();

        // Should not call if sourceGradingScale has no gradeSteps.
        expect(gradingSystemSpy).not.toHaveBeenCalled();

        component.bonus = bonus;
        component.hasPointsSet();

        expect(gradingSystemSpy).toHaveBeenCalledOnce();
        expect(gradingSystemSpy).toHaveBeenCalledWith(bonus.sourceGradingScale!.gradeSteps!);
    });

    it('should forward calculate dynamic example call to service', () => {
        const bonusFinalGradeSpy = jest.spyOn(bonusService, 'calculateFinalGrade');

        const dynamicExample = new BonusExample(10, 50);

        component.bonus = bonus;
        component.bonusToGradeStepsDTO = examGradeSteps;
        component.dynamicExample = dynamicExample;

        component.calculateDynamicExample();

        expect(bonusFinalGradeSpy).toHaveBeenCalledOnce();
        expect(bonusFinalGradeSpy).toHaveBeenCalledWith(dynamicExample, bonus, examGradeSteps);
    });
});
