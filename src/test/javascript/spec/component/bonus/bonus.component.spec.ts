import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { BonusComponent, BonusStrategyDiscreteness, BonusStrategyOption } from 'app/grading-system/bonus/bonus.component';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { BonusService, EntityResponseType } from 'app/grading-system/bonus/bonus.service';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { ModePickerComponent } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { PageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { TableColumn } from 'app/exercises/modeling/manage/modeling-exercise-import.component';
import { of, throwError } from 'rxjs';
import { Bonus, BonusExample, BonusStrategy } from 'app/entities/bonus.model';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { GradeStepsDTO } from 'app/entities/grade-step.model';
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

    const bonus: Bonus = {
        id: 7,
        weight: 1,
        sourceGradingScale: {
            id: 7,
            gradeType: GradeType.BONUS,
            exam: {
                id: 3,
                title: 'Grade Exam',
                maxPoints: 150,
                course: {
                    id: courseId,
                    title: 'Ata Test 1',
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
                    id: 355,
                    lowerBoundPercentage: 10,
                    lowerBoundInclusive: false,
                    upperBoundPercentage: 15,
                    upperBoundInclusive: true,
                    gradeName: '1',
                    isPassingGrade: false,
                },
                {
                    id: 356,
                    lowerBoundPercentage: 15,
                    lowerBoundInclusive: false,
                    upperBoundPercentage: 20,
                    upperBoundInclusive: true,
                    gradeName: '2',
                    isPassingGrade: false,
                },
                {
                    id: 357,
                    lowerBoundPercentage: 20,
                    lowerBoundInclusive: false,
                    upperBoundPercentage: 25,
                    upperBoundInclusive: true,
                    gradeName: '3',
                    isPassingGrade: false,
                },
                {
                    id: 358,
                    lowerBoundPercentage: 25,
                    lowerBoundInclusive: false,
                    upperBoundPercentage: 30,
                    upperBoundInclusive: true,
                    gradeName: '4',
                    isPassingGrade: false,
                },
                {
                    id: 359,
                    lowerBoundPercentage: 65,
                    lowerBoundInclusive: false,
                    upperBoundPercentage: 70,
                    upperBoundInclusive: true,
                    gradeName: '12',
                    isPassingGrade: false,
                    numericValue: 12,
                },
                {
                    id: 360,
                    lowerBoundPercentage: 30,
                    lowerBoundInclusive: false,
                    upperBoundPercentage: 35,
                    upperBoundInclusive: true,
                    gradeName: '5',
                    isPassingGrade: false,
                },
                {
                    id: 361,
                    lowerBoundPercentage: 35,
                    lowerBoundInclusive: false,
                    upperBoundPercentage: 40,
                    upperBoundInclusive: true,
                    gradeName: '6',
                    isPassingGrade: false,
                },
                {
                    id: 362,
                    lowerBoundPercentage: 40,
                    lowerBoundInclusive: false,
                    upperBoundPercentage: 45,
                    upperBoundInclusive: true,
                    gradeName: '7',
                    isPassingGrade: false,
                },
                {
                    id: 363,
                    lowerBoundPercentage: 45,
                    lowerBoundInclusive: false,
                    upperBoundPercentage: 50,
                    upperBoundInclusive: true,
                    gradeName: '8',
                    isPassingGrade: false,
                },
                {
                    id: 364,
                    lowerBoundPercentage: 70,
                    lowerBoundInclusive: false,
                    upperBoundPercentage: 170,
                    upperBoundInclusive: true,
                    gradeName: '13',
                    isPassingGrade: false,
                    numericValue: 13,
                },
                {
                    id: 365,
                    lowerBoundPercentage: 50,
                    lowerBoundInclusive: false,
                    upperBoundPercentage: 55,
                    upperBoundInclusive: true,
                    gradeName: '9',
                    isPassingGrade: false,
                },
                {
                    id: 366,
                    lowerBoundPercentage: 60,
                    lowerBoundInclusive: false,
                    upperBoundPercentage: 65,
                    upperBoundInclusive: true,
                    gradeName: '11',
                    isPassingGrade: false,
                    numericValue: 11,
                },
                {
                    id: 367,
                    lowerBoundPercentage: 55,
                    lowerBoundInclusive: false,
                    upperBoundPercentage: 60,
                    upperBoundInclusive: true,
                    gradeName: '10',
                    isPassingGrade: false,
                    numericValue: 10,
                },
            ],
        },
        bonusStrategy: BonusStrategy.POINTS,
    };

    const examGradeSteps: GradeStepsDTO = {
        title: 'Title',
        gradeType: GradeType.GRADE,
        maxPoints: 100,
        gradeSteps: [
            {
                id: 577,
                lowerBoundPercentage: 45,
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
                id: 579,
                lowerBoundPercentage: 40,
                lowerBoundInclusive: true,
                upperBoundPercentage: 45,
                upperBoundInclusive: false,
                gradeName: '4.7',
                isPassingGrade: false,
            },
            {
                id: 580,
                lowerBoundPercentage: 90,
                lowerBoundInclusive: true,
                upperBoundPercentage: 95,
                upperBoundInclusive: false,
                gradeName: '1.3',
                isPassingGrade: true,
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
                id: 582,
                lowerBoundPercentage: 55,
                lowerBoundInclusive: true,
                upperBoundPercentage: 60,
                upperBoundInclusive: false,
                gradeName: '3.7',
                isPassingGrade: true,
            },
            {
                id: 583,
                lowerBoundPercentage: 60,
                lowerBoundInclusive: true,
                upperBoundPercentage: 65,
                upperBoundInclusive: false,
                gradeName: '3.3',
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
                lowerBoundPercentage: 95,
                lowerBoundInclusive: true,
                upperBoundPercentage: 100,
                upperBoundInclusive: true,
                gradeName: '1.0',
                isPassingGrade: true,
            },
            {
                id: 586,
                lowerBoundPercentage: 65,
                lowerBoundInclusive: true,
                upperBoundPercentage: 70,
                upperBoundInclusive: false,
                gradeName: '3.0',
                isPassingGrade: true,
            },
            {
                id: 587,
                lowerBoundPercentage: 70,
                lowerBoundInclusive: true,
                upperBoundPercentage: 75,
                upperBoundInclusive: false,
                gradeName: '2.7',
                isPassingGrade: true,
            },
            {
                id: 588,
                lowerBoundPercentage: 75,
                lowerBoundInclusive: true,
                upperBoundPercentage: 80,
                upperBoundInclusive: false,
                gradeName: '2.3',
                isPassingGrade: true,
            },
            {
                id: 589,
                lowerBoundPercentage: 80,
                lowerBoundInclusive: true,
                upperBoundPercentage: 85,
                upperBoundInclusive: false,
                gradeName: '2.0',
                isPassingGrade: true,
            },
            {
                id: 590,
                lowerBoundPercentage: 85,
                lowerBoundInclusive: true,
                upperBoundPercentage: 90,
                upperBoundInclusive: false,
                gradeName: '1.7',
                isPassingGrade: true,
            },
        ],
    };

    // TODO: Ata Fill examples array
    const bonusExamples: BonusExample[] = [];

    const bonusStrategyToOptionAndDiscretenessMappings = [
        [BonusStrategy.GRADES_CONTINUOUS, BonusStrategyOption.GRADES, BonusStrategyDiscreteness.CONTINUOUS],
        [BonusStrategy.GRADES_DISCRETE, BonusStrategyOption.GRADES, BonusStrategyDiscreteness.DISCRETE],
        [BonusStrategy.POINTS, BonusStrategyOption.POINTS, undefined],
        [undefined, undefined, undefined],
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
            providers: [{ provide: ActivatedRoute, useValue: route }],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(BonusComponent);
        component = fixture.componentInstance;
        bonusService = fixture.debugElement.injector.get(BonusService);
        gradingSystemService = fixture.debugElement.injector.get(GradingSystemService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', fakeAsync(() => {
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

        expect(component.isLoading).toBeTrue();

        expect(bonusSpy).toHaveBeenCalledOnce();
        expect(bonusSpy).toHaveBeenCalledWith(courseId, examId);

        expect(gradingScaleSpy).toHaveBeenCalledOnce();
        expect(gradingScaleSpy).toHaveBeenCalledWith(state);

        expect(gradeStepsSpy).toHaveBeenCalledOnce();
        expect(gradeStepsSpy).toHaveBeenCalledWith(courseId, examId);

        tick();
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
        component.examGradeStepsDTO = examGradeSteps;

        const bonusSpy = jest.spyOn(bonusService, 'generateBonusExamples').mockReturnValue(bonusExamples);

        expect(component.examples).toHaveLength(0);

        component.onBonusStrategyInputChange();

        expect(component.bonus.bonusStrategy).toBe(bonusStrategy);
        expect(bonusSpy).toHaveBeenCalledOnce();
        expect(bonusSpy).toHaveBeenCalledWith({ ...bonus, bonusStrategy }, examGradeSteps);
        expect(component.examples).toHaveLength(bonusExamples.length);
    });

    it('should remove examples when all required fields are not set', () => {
        component.bonus = { ...bonus, bonusStrategy: undefined };
        component.examples = bonusExamples;

        component.onBonusStrategyInputChange();

        expect(component.examples).toHaveLength(0);
    });

    it('should create bonus', fakeAsync(() => {
        const bonusSpy = jest.spyOn(bonusService, 'createBonusForExam').mockReturnValue(of({ body: bonus } as EntityResponseType));

        fixture.detectChanges();

        const newBonus = { ...bonus, id: undefined };
        component.bonus = newBonus;
        component.save();

        expect(bonusSpy).toHaveBeenCalledOnce();
        expect(bonusSpy).toHaveBeenCalledWith(courseId, examId, newBonus);

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
});
