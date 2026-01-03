import { MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { BonusComponent, BonusStrategyDiscreteness, BonusStrategyOption } from 'app/assessment/manage/grading/bonus/bonus.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { BonusService, EntityResponseType } from 'app/assessment/manage/grading/bonus/bonus.service';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { ModePickerComponent } from 'app/exercise/mode-picker/mode-picker.component';
import { SearchResult, SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { of, throwError } from 'rxjs';
import { Bonus, BonusExample, BonusStrategy } from 'app/assessment/shared/entities/bonus.model';
import { GradeType, GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { GradeStep, GradeStepsDTO } from 'app/assessment/shared/entities/grade-step.model';
import { HttpResponse } from '@angular/common/http';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/component/delete-dialog.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('BonusComponent', () => {
    setupTestBed({ zoneless: true });
    let component: BonusComponent;
    let fixture: ComponentFixture<BonusComponent>;

    let bonusService: BonusService;
    let gradingSystemService: GradingService;

    let findGradeStepsSpy: MockInstance;
    let findWithBonusSpy: MockInstance;
    let findBonusForExamSpy: MockInstance;

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
        plagiarismGrade: GradingScale.DEFAULT_PLAGIARISM_GRADE,
        noParticipationGrade: GradingScale.DEFAULT_NO_PARTICIPATION_GRADE,
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
                gradeName: '1.0',
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
        [BonusStrategy.GRADES_CONTINUOUS, BonusStrategyOption.GRADES, undefined],
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                FormsModule,
                ReactiveFormsModule,
                MockModule(NgbTooltipModule),
                BonusComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(SafeHtmlPipe),
                MockComponent(ModePickerComponent),
                MockPipe(GradeStepBoundsPipe),
                MockComponent(DeleteDialogComponent),
                MockDirective(DeleteButtonDirective),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                MockProvider(GradingService),
                MockProvider(BonusService),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(BonusComponent);
        component = fixture.componentInstance;
        bonusService = TestBed.inject(BonusService);
        gradingSystemService = TestBed.inject(GradingService);

        findBonusForExamSpy = vi.spyOn(bonusService, 'findBonusForExam').mockReturnValue(of({ body: bonus } as EntityResponseType));
        findWithBonusSpy = vi
            .spyOn(gradingSystemService, 'findWithBonusGradeTypeForInstructor')
            .mockReturnValue(of({ body: searchResult } as HttpResponse<SearchResult<GradingScale>>));
        findGradeStepsSpy = vi.spyOn(gradingSystemService, 'findGradeSteps').mockReturnValue(of(examGradeSteps));
        vi.spyOn(bonusService, 'generateBonusExamples').mockReturnValue(bonusExamples);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        // Note: without this line the test does not work for some reason due to a weird error. We let the test still run by setting the bonus object with its grading scale
        // below (i.e. we inject it directly into the component)
        findBonusForExamSpy.mockReturnValue(throwError(() => ({ status: 404 })));

        const sortGradeStepsSpy = vi.spyOn(gradingSystemService, 'sortGradeSteps');
        const setGradePointsSpy = vi.spyOn(gradingSystemService, 'setGradePoints');

        fixture.changeDetectorRef.detectChanges();
        component.setBonus(bonus);

        expect(findBonusForExamSpy).toHaveBeenCalledTimes(1);
        expect(findBonusForExamSpy).toHaveBeenCalledWith(courseId, examId);

        const state: SearchTermPageableSearch = {
            page: 1,
            pageSize: 100,
            searchTerm: '',
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: 'ID',
        };

        expect(findWithBonusSpy).toHaveBeenCalledTimes(1);
        expect(findWithBonusSpy).toHaveBeenCalledWith(state);

        expect(findGradeStepsSpy).toHaveBeenCalledTimes(1);
        expect(findGradeStepsSpy).toHaveBeenCalledWith(courseId, examId);

        expect(component.isLoading).toBe(false);
        expect(component.bonus.sourceGradingScale).toEqual(sourceGradingScale);
        expect(component.sourceGradingScales).toEqual(searchResult.resultsOnPage);

        expect(sortGradeStepsSpy).toHaveBeenCalledTimes(1);
        expect(sortGradeStepsSpy).toHaveBeenCalledWith(examGradeSteps.gradeSteps);

        expect(setGradePointsSpy).toHaveBeenCalledTimes(1);
        expect(setGradePointsSpy).toHaveBeenCalledWith(examGradeSteps.gradeSteps, examGradeSteps.maxPoints);
    });

    it('should get calculation sign', () => {
        expect(component.getCalculationSign(1)).toBe('+');
        expect(component.getCalculationSign(-1)).toBe('−');
    });

    it.each(bonusStrategyToOptionAndDiscretenessMappings.slice(0, -1))(
        'should set bonus strategy and discreteness for [%p, %p, %p]',
        (bonusStrategy: BonusStrategy, bonusStrategyOption: BonusStrategyOption, bonusStrategyDiscreteness: BonusStrategyDiscreteness) => {
            const bonusSpy = findBonusForExamSpy.mockReturnValue(of({ body: { bonusStrategy } } as EntityResponseType));
            component.ngOnInit();
            expect(bonusSpy).toHaveBeenCalledTimes(1);
            expect(component.currentBonusStrategyOption).toBe(bonusStrategyOption);
            expect(component.currentBonusStrategyDiscreteness).toBe(bonusStrategyDiscreteness);
        },
    );

    it.each(bonusStrategyToOptionAndDiscretenessMappings)(
        'should convert from inputs to BonusStrategy for [%p, %p, %p]',
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

        const bonusSpy = vi.spyOn(bonusService, 'generateBonusExamples').mockReturnValue(bonusExamples);

        expect(component.examples).toHaveLength(0);

        component.onBonusStrategyInputChange();

        expect(component.bonus.bonusStrategy).toBe(bonusStrategy);
        expect(bonusSpy).toHaveBeenCalledTimes(1);
        expect(bonusSpy).toHaveBeenCalledWith({ ...bonus, bonusStrategy }, examGradeSteps);
        expect(component.examples).toHaveLength(bonusExamples.length);
    });

    it('should check bonus strategy and weight mismatch', () => {
        vi.spyOn(gradingSystemService, 'getNumericValueForGradeName').mockImplementation((gradeName) => parseFloat(gradeName!));
        vi.spyOn(bonusService, 'doesBonusExceedMax').mockReturnValue(true);

        component.bonus = { ...bonus, bonusStrategy: BonusStrategy.GRADES_CONTINUOUS, weight: 1 };
        component.bonusToGradeStepsDTO = { gradeSteps: [] as GradeStep[] } as GradeStepsDTO;

        component.generateExamples();

        expect(component.examples).toHaveLength(0);
        expect(component.hasBonusStrategyWeightMismatch).toBe(true);
    });

    it('should remove examples when all required fields are not set', () => {
        component.bonus = { ...bonus, bonusStrategy: undefined };
        component.examples = bonusExamples;

        component.onBonusStrategyInputChange();

        expect(component.examples).toHaveLength(0);
    });

    it('should create bonus', () => {
        const createBonusSpy = vi.spyOn(bonusService, 'createBonusForExam').mockReturnValue(of({ body: bonus } as EntityResponseType));
        const findBonusSpy = findBonusForExamSpy.mockReturnValue(throwError(() => ({ status: 404 })));

        fixture.changeDetectorRef.detectChanges();

        const newBonus = { ...bonus, id: undefined };
        component.bonus = newBonus;
        component.save();

        expect(createBonusSpy).toHaveBeenCalledTimes(1);
        expect(createBonusSpy).toHaveBeenCalledWith(courseId, examId, newBonus);

        expect(findBonusSpy).toHaveBeenCalledTimes(1);
        expect(findBonusSpy).toHaveBeenCalledWith(courseId, examId);

        expect(component.bonus.id).toBe(bonus.id);
        expect(component.isLoading).toBe(false);
    });

    it('should update bonus', () => {
        const bonusSpy = vi.spyOn(bonusService, 'updateBonus').mockReturnValue(of({ body: bonus } as EntityResponseType));

        component.bonus = bonus;
        component.ngOnInit();

        component.save();

        expect(bonusSpy).toHaveBeenCalledTimes(1);
        expect(bonusSpy).toHaveBeenCalledWith(courseId, examId, bonus);

        expect(component.bonus.id).toBe(bonus.id);
        expect(component.isLoading).toBe(false);
    });

    it('should delete bonus', () => {
        const bonusSpy = vi.spyOn(bonusService, 'deleteBonus').mockReturnValue(of({} as HttpResponse<void>));

        let dialogError: string | undefined = undefined;
        component.dialogError$.subscribe((err) => (dialogError = err));

        component.bonus = bonus;
        component.ngOnInit();

        component.delete();

        expect(bonusSpy).toHaveBeenCalledTimes(1);
        expect(bonusSpy).toHaveBeenCalledWith(courseId, examId, bonus.id);

        expect(component.bonus.id).toBeUndefined();
        expect(component.bonus.bonusStrategy).toBeUndefined();
        expect(component.bonus.weight).toBeUndefined();
        expect(component.bonus.bonusToGradingScale).toBeUndefined();
        expect(component.bonus.sourceGradingScale).toBeUndefined();
        expect(dialogError).toBe('');

        expect(component.isLoading).toBe(false);
    });

    it('should show error on delete', () => {
        const errorMessage = 'Error message';
        const bonusSpy = vi.spyOn(bonusService, 'deleteBonus').mockReturnValue(throwError(() => new Error(errorMessage)));

        let dialogError: string | undefined = undefined;
        component.dialogError$.subscribe((err) => (dialogError = err));

        component.bonus = bonus;
        component.ngOnInit();

        component.delete();

        expect(bonusSpy).toHaveBeenCalledTimes(1);
        expect(bonusSpy).toHaveBeenCalledWith(courseId, examId, bonus.id);

        expect(component.bonus).toEqual(bonus);
        expect(dialogError).toBe(errorMessage);

        expect(component.isLoading).toBe(false);
    });

    it('should not delete if id is empty', () => {
        const bonusSpy = vi.spyOn(bonusService, 'deleteBonus');

        const unsavedBonus = { ...bonus, id: undefined };
        component.bonus = unsavedBonus;
        component.delete();

        expect(bonusSpy).not.toHaveBeenCalled();

        expect(component.bonus).toEqual(unsavedBonus);
        expect(component.isLoading).toBe(false);
    });

    it('should handle find bonus response with error', () => {
        const findBonusSpy = findBonusForExamSpy.mockReturnValue(throwError(() => ({ status: 500 })));

        component.ngOnInit();

        expect(findBonusSpy).toHaveBeenCalledTimes(1);
        expect(findBonusSpy).toHaveBeenCalledWith(courseId, examId);

        expect(component.bonus).toStrictEqual(new Bonus());
        expect(component.isLoading).toBe(false);
    });

    it('should handle empty find bonus response', () => {
        const findBonusSpy = findBonusForExamSpy.mockReturnValue(of({ body: undefined } as any as EntityResponseType));

        component.ngOnInit();

        expect(findBonusSpy).toHaveBeenCalledTimes(1);
        expect(findBonusSpy).toHaveBeenCalledWith(courseId, examId);

        expect(component.bonus).toStrictEqual(new Bonus());
        expect(component.isLoading).toBe(false);
    });

    it('should forward grading scale title call to service', () => {
        const gradingSystemSpy = vi.spyOn(gradingSystemService, 'getGradingScaleTitle');

        component.getGradingScaleTitle(bonus.sourceGradingScale!);

        expect(gradingSystemSpy).toHaveBeenCalledTimes(1);
        expect(gradingSystemSpy).toHaveBeenCalledWith(bonus.sourceGradingScale);
    });

    it('should forward grading scale max points call to service', () => {
        const gradingSystemSpy = vi.spyOn(gradingSystemService, 'getGradingScaleMaxPoints');

        component.getGradingScaleMaxPoints(bonus.sourceGradingScale!);

        expect(gradingSystemSpy).toHaveBeenCalledTimes(1);
        expect(gradingSystemSpy).toHaveBeenCalledWith(bonus.sourceGradingScale);
    });

    it('should forward has points set call to service', () => {
        const gradingSystemSpy = vi.spyOn(gradingSystemService, 'hasPointsSet');

        component.bonus = { ...bonus, sourceGradingScale: {} as GradingScale };
        component.hasPointsSet();

        // Should not call if sourceGradingScale has no gradeSteps.
        expect(gradingSystemSpy).not.toHaveBeenCalled();

        component.bonus = bonus;
        component.hasPointsSet();

        expect(gradingSystemSpy).toHaveBeenCalledTimes(1);
        expect(gradingSystemSpy).toHaveBeenCalledWith(bonus.sourceGradingScale!.gradeSteps!);
    });

    it('should forward calculate dynamic example call to service', () => {
        const bonusFinalGradeSpy = vi.spyOn(bonusService, 'calculateFinalGrade');

        const dynamicExample = new BonusExample(10, 50);

        component.bonus = bonus;
        component.bonusToGradeStepsDTO = examGradeSteps;
        component.dynamicExample = dynamicExample;

        component.calculateDynamicExample();

        expect(bonusFinalGradeSpy).toHaveBeenCalledTimes(1);
        expect(bonusFinalGradeSpy).toHaveBeenCalledWith(dynamicExample, bonus, examGradeSteps);
    });

    it('should refresh dynamic example on weight change only if previously calculated', () => {
        const bonusFinalGradeSpy = vi.spyOn(bonusService, 'calculateFinalGrade');

        const dynamicExample = new BonusExample(10, 50);

        component.bonus = bonus;
        component.bonusToGradeStepsDTO = examGradeSteps;
        component.dynamicExample = dynamicExample;

        component.onWeightChange();

        expect(bonusFinalGradeSpy).not.toHaveBeenCalled();

        component.dynamicExample.finalGrade = '1.0';

        component.onWeightChange();

        expect(bonusFinalGradeSpy).toHaveBeenCalledTimes(1);
        expect(bonusFinalGradeSpy).toHaveBeenCalledWith(dynamicExample, bonus, examGradeSteps);
    });
});
