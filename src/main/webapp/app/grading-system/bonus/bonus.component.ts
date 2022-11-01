import { Component, OnInit } from '@angular/core';
import { BonusService } from 'app/grading-system/bonus/bonus.service';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradingScale } from 'app/entities/grading-scale.model';
import { ActivatedRoute, Router } from '@angular/router';
import { Bonus, BonusExample, BonusStrategy } from 'app/entities/bonus.model';
import { catchError, finalize, tap } from 'rxjs/operators';
import { faExclamationTriangle, faPlus, faQuestionCircle, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';
import { ButtonSize } from 'app/shared/components/button.component';
import { Subject, forkJoin, of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { PageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { TableColumn } from 'app/exercises/modeling/manage/modeling-exercise-import.component';
import { GradeEditMode } from 'app/grading-system/base-grading-system/base-grading-system.component';
import { AlertService } from 'app/core/util/alert.service';

export enum BonusStrategyOption {
    GRADES,
    POINTS,
}

export enum BonusStrategyDiscreteness {
    DISCRETE,
    CONTINUOUS,
}

@Component({
    selector: 'jhi-bonus',
    templateUrl: './bonus.component.html',
    styleUrls: ['./bonus.component.scss'],
})
export class BonusComponent implements OnInit {
    readonly CALCULATION_PLUS = 1;
    readonly CALCULATION_MINUS = -1;

    // Icons
    readonly faSave = faSave;
    readonly faPlus = faPlus;
    readonly faTimes = faTimes;
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faQuestionCircle = faQuestionCircle;

    readonly ButtonSize = ButtonSize;
    readonly GradeEditMode = GradeEditMode;
    readonly BonusStrategyOption = BonusStrategyOption;
    readonly BonusStrategy = BonusStrategy;

    readonly bonusStrategyOptions = [BonusStrategyOption.GRADES, BonusStrategyOption.POINTS].map((bonusStrategyOption) => ({
        value: bonusStrategyOption,
        labelKey: 'artemisApp.bonus.bonusStrategy.' + BonusStrategyOption[bonusStrategyOption].toLowerCase(),
        btnClass: 'btn-secondary',
    }));

    readonly bonusStrategyDiscreteness = [BonusStrategyDiscreteness.CONTINUOUS].map((bonusStrategyDiscreteness) => ({
        value: bonusStrategyDiscreteness,
        labelKey: 'artemisApp.bonus.discreteness.' + BonusStrategyDiscreteness[bonusStrategyDiscreteness].toLowerCase(),
        btnClass: 'btn-secondary',
    }));

    readonly calculationSigns = [
        {
            value: this.CALCULATION_MINUS,
            label: '−',
            btnClass: 'btn-secondary',
        },
        {
            value: this.CALCULATION_PLUS,
            label: '+',
            btnClass: 'btn-secondary',
        },
    ];

    sourceGradingScales: GradingScale[] = [];

    bonusToGradeStepsDTO: GradeStepsDTO;

    isLoading = false;
    private courseId: number;
    private examId: number;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    currentBonusStrategyOption?: BonusStrategyOption;
    currentBonusStrategyDiscreteness?: BonusStrategyDiscreteness;

    examples: BonusExample[] = [];
    dynamicExample = new BonusExample(0, 0);

    bonus = new Bonus();
    hasBonusStrategyWeightMismatch = false;

    private state: PageableSearch = {
        page: 1,
        pageSize: 100,
        searchTerm: '',
        sortingOrder: SortingOrder.DESCENDING,
        sortedColumn: TableColumn.ID,
    };

    constructor(
        private bonusService: BonusService,
        private gradingSystemService: GradingSystemService,
        private route: ActivatedRoute,
        private router: Router,
        private translateService: TranslateService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;

        const paramMap = this.route.snapshot.paramMap;
        this.courseId = Number(paramMap.get('courseId'));
        this.examId = Number(paramMap.get('examId'));

        forkJoin([
            this.bonusService.findBonusForExam(this.courseId, this.examId).pipe(
                tap((bonusResponse) => {
                    this.setBonus(bonusResponse.body || new Bonus());
                }),
                catchError((error) => {
                    if (error?.status === 404) {
                        return of(undefined);
                    }
                    throw error;
                }),
            ),
            this.gradingSystemService.findWithBonusGradeTypeForInstructor(this.state).pipe(
                tap((gradingScales) => {
                    this.sourceGradingScales = gradingScales.body?.resultsOnPage || [];
                }),
            ),
            this.gradingSystemService.findGradeSteps(this.courseId, this.examId).pipe(
                tap((gradeSteps) => {
                    if (gradeSteps) {
                        this.bonusToGradeStepsDTO = gradeSteps;
                        this.gradingSystemService.sortGradeSteps(gradeSteps.gradeSteps);
                        this.gradingSystemService.setGradePoints(gradeSteps.gradeSteps, gradeSteps.maxPoints!);
                    } else {
                        throw new Error(`No grade steps found for bonus calculation. Bonus to course id "${this.courseId}", exam id "${this.examId}"`);
                    }
                }),
                catchError((error) => {
                    this.alertService.error(error.message);
                    this.navigateToExam();
                    throw new Error(`No grade scale found for bonus calculation. Bonus to course id "${this.courseId}", exam id "${this.examId}"`);
                }),
            ),
        ])
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(() => {
                this.setSourceGradingScale();
            });
    }

    private navigateToExam() {
        this.router.navigate(['course-management', this.courseId, 'exams', this.examId]);
    }

    private setSourceGradingScale() {
        if (this.bonus.sourceGradingScale) {
            const sourceGradingScale = this.sourceGradingScales.find((gradingScale) => gradingScale.id === this.bonus.sourceGradingScale!.id);
            if (!sourceGradingScale) {
                throw new Error(`sourceGradingScale not found for id: ${this.bonus.sourceGradingScale.id}`);
            }
            this.bonus.sourceGradingScale = sourceGradingScale;
            this.onBonusSourceChange(sourceGradingScale);
        }
    }

    generateExamples() {
        if (this.bonus.sourceGradingScale && this.bonus.bonusStrategy) {
            this.hasBonusStrategyWeightMismatch = this.checkBonusStrategyWeightMismatch(this.bonus.bonusStrategy, this.bonus.weight!, this.bonusToGradeStepsDTO.gradeSteps);
            this.examples = !this.hasBonusStrategyWeightMismatch ? this.bonusService.generateBonusExamples(this.bonus, this.bonusToGradeStepsDTO) : [];
        } else {
            this.hasBonusStrategyWeightMismatch = false;
            this.examples = [];
        }
    }

    /**
     * Checks if given bonus strategy and weight combination would result in a worse grade for student, which is counter-intuitive for a bonus.
     * Warning: Assumes bonusToGradeSteps are sorted.
     *
     * @param bonusStrategy current bonus strategy
     * @param weight current weight
     * @param bonusToGradeSteps grade steps belonging to bonusTo grading scale, assumed to be sorted.
     * @private
     */
    private checkBonusStrategyWeightMismatch(bonusStrategy: BonusStrategy, weight: number, bonusToGradeSteps: GradeStep[]) {
        switch (bonusStrategy) {
            case BonusStrategy.POINTS:
                return weight < 0;
            case BonusStrategy.GRADES_CONTINUOUS:
            case BonusStrategy.GRADES_DISCRETE:
                const maxGradeValue = this.gradingSystemService.getNumericValueForGradeName(this.gradingSystemService.maxGrade(bonusToGradeSteps));
                const firstGradeValue = this.gradingSystemService.getNumericValueForGradeName(bonusToGradeSteps[0]?.gradeName);
                return this.bonusService.doesBonusExceedMax(firstGradeValue!, maxGradeValue!, weight);
            default:
                return false;
        }
    }

    private setBonus(bonus: Bonus) {
        this.bonus = bonus;
        switch (bonus.bonusStrategy) {
            case BonusStrategy.POINTS:
                this.currentBonusStrategyOption = BonusStrategyOption.POINTS;
                this.currentBonusStrategyDiscreteness = undefined;
                break;
            case BonusStrategy.GRADES_CONTINUOUS:
                this.currentBonusStrategyOption = BonusStrategyOption.GRADES;
                this.currentBonusStrategyDiscreteness = BonusStrategyDiscreteness.CONTINUOUS;
                break;
            case BonusStrategy.GRADES_DISCRETE:
                this.currentBonusStrategyOption = BonusStrategyOption.GRADES;
                this.currentBonusStrategyDiscreteness = BonusStrategyDiscreteness.DISCRETE;
                break;
            default:
                this.currentBonusStrategyOption = undefined;
                this.currentBonusStrategyDiscreteness = undefined;
        }
    }

    onBonusStrategyInputChange() {
        this.bonus.bonusStrategy = this.convertFromInputsToBonusStrategy(this.currentBonusStrategyOption, this.currentBonusStrategyDiscreteness);
        this.generateExamples();
        this.refreshDynamicExample();
    }

    onWeightChange() {
        this.generateExamples();
        this.refreshDynamicExample();
    }

    convertFromInputsToBonusStrategy(
        bonusStrategyOption: BonusStrategyOption | undefined,
        bonusStrategyDiscreteness: BonusStrategyDiscreteness | undefined,
    ): BonusStrategy | undefined {
        if (bonusStrategyOption === BonusStrategyOption.POINTS) {
            return BonusStrategy.POINTS;
        } else if (bonusStrategyOption === BonusStrategyOption.GRADES) {
            switch (bonusStrategyDiscreteness) {
                case BonusStrategyDiscreteness.CONTINUOUS:
                    return BonusStrategy.GRADES_CONTINUOUS;
                case BonusStrategyDiscreteness.DISCRETE:
                    return BonusStrategy.GRADES_DISCRETE;
                default:
                    return undefined;
            }
        }
        return undefined;
    }

    save(): void {
        this.isLoading = true;
        const saveObservable = this.bonus.id
            ? this.bonusService.updateBonus(this.courseId, this.examId, this.bonus)
            : this.bonusService.createBonusForExam(this.courseId, this.examId, this.bonus);

        saveObservable
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe((bonusResponse) => (this.bonus.id = bonusResponse.body?.id));
    }

    delete() {
        if (!this.bonus.id) {
            return;
        }
        this.isLoading = true;
        this.bonusService
            .deleteBonus(this.courseId, this.examId, this.bonus.id)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (bonusResponse) => {
                    this.setBonus(bonusResponse.body || new Bonus());
                    this.dialogErrorSource.next('');
                },
                error: (error) => this.dialogErrorSource.next(error.message),
            });
    }

    /**
     * @see GradingSystemService.getGradingScaleTitle
     * @param gradingScale
     */
    getGradingScaleTitle(gradingScale: GradingScale): string | undefined {
        return this.gradingSystemService.getGradingScaleTitle(gradingScale);
    }

    /**
     * @see GradingSystemService.getGradingScaleMaxPoints
     * @param gradingScale
     */
    getGradingScaleMaxPoints(gradingScale?: GradingScale): number {
        return this.gradingSystemService.getGradingScaleMaxPoints(gradingScale);
    }

    /**
     * @see GradingSystemService.hasPointsSet
     */
    hasPointsSet(): boolean {
        return !!this.bonus.sourceGradingScale?.gradeSteps && this.gradingSystemService.hasPointsSet(this.bonus.sourceGradingScale.gradeSteps);
    }

    /**
     * Calculate points for each grade step of the selected gradingScale if they are not already calculated before
     * to display in the table and sort the grading steps for matching.
     * @param gradingScale the selected bonus source grading scale
     */
    private setBonusSourcePoints(gradingScale: GradingScale) {
        if (!this.gradingSystemService.hasPointsSet(gradingScale.gradeSteps)) {
            this.gradingSystemService.setGradePoints(gradingScale.gradeSteps, this.getGradingScaleMaxPoints(gradingScale));
        }
        this.gradingSystemService.sortGradeSteps(gradingScale.gradeSteps);
    }

    calculateDynamicExample() {
        this.bonusService.calculateFinalGrade(this.dynamicExample, this.bonus, this.bonusToGradeStepsDTO);
    }

    private refreshDynamicExample() {
        if (this.dynamicExample.finalGrade == undefined) {
            // Do not recalculate if it has not been calculated already.
            return;
        }
        this.calculateDynamicExample();
    }

    onBonusSourceChange(gradingScale: GradingScale) {
        this.setBonusSourcePoints(gradingScale);
        this.generateExamples();
        this.refreshDynamicExample();
    }

    /**
     * Gets the sign of the bonus calculation to display inside the formula.
     * @param weight a positive or negative number
     */
    getCalculationSign(weight: number): string {
        return weight > 0 ? '+' : '−';
    }

    maxPossibleGrade() {
        return this.gradingSystemService.maxGrade(this.bonusToGradeStepsDTO.gradeSteps);
    }
}
