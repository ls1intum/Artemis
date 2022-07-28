import { Component, OnInit } from '@angular/core';
import { BonusService } from 'app/grading-system/bonus/bonus.service';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradingScale } from 'app/entities/grading-scale.model';
import { ActivatedRoute } from '@angular/router';
import { Bonus, BonusStrategy } from 'app/entities/bonus.model';
import { finalize } from 'rxjs/operators';
import { faExclamationTriangle, faPlus, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { GradeStepsDTO } from 'app/entities/grade-step.model';
import { ButtonSize } from 'app/shared/components/button.component';
import { Subject } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { PageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { TableColumn } from 'app/exercises/modeling/manage/modeling-exercise-import.component';
import { GradeEditMode } from 'app/grading-system/base-grading-system/base-grading-system.component';

enum BonusStrategyOptions {
    GRADES,
    POINTS,
}

enum BonusStrategyDiscreteness {
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

    readonly ButtonSize = ButtonSize;
    readonly GradeEditMode = GradeEditMode;
    readonly BonusStrategyOptions = BonusStrategyOptions;
    // readonly BonusStrategyDiscreteness = BonusStrategyDiscreteness;

    readonly bonusStrategyOptions = [BonusStrategyOptions.GRADES, BonusStrategyOptions.POINTS].map((bonusStrategyOption) => ({
        value: bonusStrategyOption,
        labelKey: 'artemisApp.TODO: Ata.' + BonusStrategyOptions[bonusStrategyOption].toLowerCase(),
        btnClass: 'btn-secondary',
    }));

    readonly bonusStrategyDiscreteness = [BonusStrategyDiscreteness.DISCRETE, BonusStrategyDiscreteness.CONTINUOUS].map((bonusStrategyDiscreteness) => ({
        value: bonusStrategyDiscreteness,
        labelKey: 'artemisApp.TODO: Ata.' + BonusStrategyDiscreteness[bonusStrategyDiscreteness].toLowerCase(),
        btnClass: 'btn-secondary',
    }));

    readonly calculationSigns = [
        {
            value: this.CALCULATION_PLUS,
            labelKey: '+',
            btnClass: 'btn-secondary',
        },
        {
            value: this.CALCULATION_MINUS,
            labelKey: '−',
            btnClass: 'btn-secondary',
        },
    ];

    sourceGradingScales: GradingScale[] = [];

    examGradeStepsDTO: GradeStepsDTO;

    isLoading = false;
    private courseId: number;
    private examId: number;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    currentBonusStrategyOption?: BonusStrategyOptions;
    currentBonusStrategyDiscreteness?: BonusStrategyDiscreteness;
    currentCalculationSign?: number;

    bonus = new Bonus();
    invalidBonusMessage?: string;

    state: PageableSearch = {
        page: 1,
        pageSize: 10,
        searchTerm: '',
        sortingOrder: SortingOrder.DESCENDING,
        sortedColumn: TableColumn.ID,
    };

    constructor(
        private bonusService: BonusService,
        private gradingSystemService: GradingSystemService,
        private route: ActivatedRoute,
        private translateService: TranslateService,
    ) {}

    ngOnInit(): void {
        window['comp'] = this; // TODO: Ata Delete this.
        this.route.params.subscribe((params) => {
            this.isLoading = true;
            this.courseId = Number(params['courseId']);
            this.examId = Number(params['examId']);

            this.bonusService
                .findBonusWithTargetExam(this.courseId, this.examId)
                .pipe(
                    finalize(() => {
                        this.isLoading = false;
                    }),
                )
                .subscribe((bonusResponse) => {
                    this.setBonus(bonusResponse.body || new Bonus());
                });
        });

        this.gradingSystemService.findWithBonusGradeTypeForInstructor(this.state).subscribe((gradingScales) => {
            this.sourceGradingScales = gradingScales.body?.resultsOnPage || [];
        });

        this.gradingSystemService.findGradeSteps(this.courseId, this.examId).subscribe((gradeSteps) => {
            if (gradeSteps) {
                this.examGradeStepsDTO = gradeSteps;
                this.gradingSystemService.sortGradeSteps(gradeSteps.gradeSteps);
                this.gradingSystemService.setGradePoints(gradeSteps.gradeSteps, gradeSteps.maxPoints!);
                // if (gradeSteps.maxPoints !== undefined) {
                //     if (!this.isExam) {
                //         // calculate course max points based on exercises
                //         const course = this.courseCalculationService.getCourse(this.courseId!);
                //         const maxPoints = this.courseCalculationService.calculateTotalScores(course!.exercises!, course!).get(ScoreType.REACHABLE_POINTS);
                //         this.gradingSystemService.setGradePoints(this.gradeSteps, maxPoints!);
                //     } else {
                //         // for exams the max points filed should equal the total max points (otherwise exams can't be started)
                //         this.gradingSystemService.setGradePoints(this.gradeSteps, gradeSteps.maxPoints!);
                //     }
                // }
            } else {
                // TODO: Ata Return to exam detail page and alert error.
            }
        });
    }

    private setBonus(bonus: Bonus) {
        this.bonus = bonus;
        this.currentCalculationSign = Math.sign(bonus.calculationSign!) || undefined;
        switch (bonus.bonusStrategy) {
            case BonusStrategy.POINTS:
                this.currentBonusStrategyOption = BonusStrategyOptions.POINTS;
                this.currentBonusStrategyDiscreteness = undefined;
                break;
            case BonusStrategy.GRADES_CONTINUOUS:
                this.currentBonusStrategyOption = BonusStrategyOptions.GRADES;
                this.currentBonusStrategyDiscreteness = BonusStrategyDiscreteness.CONTINUOUS;
                break;
            case BonusStrategy.GRADES_DISCRETE:
                this.currentBonusStrategyOption = BonusStrategyOptions.GRADES;
                this.currentBonusStrategyDiscreteness = BonusStrategyDiscreteness.DISCRETE;
                break;
            default:
                this.currentBonusStrategyOption = undefined;
                this.currentBonusStrategyDiscreteness = undefined;
        }
    }

    private prepareBonusToSave(bonus: Bonus) {
        bonus.calculationSign = this.currentCalculationSign;
        if (this.currentBonusStrategyOption === BonusStrategyOptions.POINTS) {
            bonus.bonusStrategy = BonusStrategy.POINTS;
        } else if (this.currentBonusStrategyOption === BonusStrategyOptions.GRADES) {
            bonus.bonusStrategy = this.currentBonusStrategyDiscreteness === BonusStrategyDiscreteness.DISCRETE ? BonusStrategy.GRADES_DISCRETE : BonusStrategy.GRADES_CONTINUOUS;
        }
    }

    save(): void {
        this.prepareBonusToSave(this.bonus);
        this.isLoading = true;
        const saveObservable = this.bonus.id ? this.bonusService.updateBonus(this.bonus) : this.bonusService.createBonusForExam(this.courseId, this.examId, this.bonus);

        saveObservable
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe((bonusResponse) => {
                this.setBonus(bonusResponse.body!);
            });

        // this.gradingScale.gradeSteps = this.gradingSystemService.sortGradeSteps(this.gradingScale.gradeSteps);
        // this.setInclusivity();
        // this.gradingScale.gradeSteps = this.setPassingGrades(this.gradingScale.gradeSteps);
        // // new grade steps shouldn't have ids set
        // this.gradingScale.gradeSteps.forEach((gradeStep) => {
        //     gradeStep.id = undefined;
        // });
        // if (this.isExam) {
        //     this.gradingScale.exam = this.exam;
        //     this.gradingScale.exam!.maxPoints = this.maxPoints;
        // } else {
        //     this.gradingScale.course = this.course;
        //     this.gradingScale.course!.maxPoints = this.maxPoints;
        // }
        // if (this.existingGradingScale) {
        //     if (this.isExam) {
        //         this.handleSaveObservable(this.gradingSystemService.updateGradingScaleForExam(this.courseId!, this.examId!, this.gradingScale));
        //     } else {
        //         this.handleSaveObservable(this.gradingSystemService.updateGradingScaleForCourse(this.courseId!, this.gradingScale));
        //     }
        // } else {
        //     if (this.isExam) {
        //         this.handleSaveObservable(this.gradingSystemService.createGradingScaleForExam(this.courseId!, this.examId!, this.gradingScale));
        //     } else {
        //         this.handleSaveObservable(this.gradingSystemService.createGradingScaleForCourse(this.courseId!, this.gradingScale));
        //     }
        // }
    }

    delete() {
        if (!this.bonus.id) {
            return;
        }
        this.isLoading = true;
        this.bonusService
            .deleteBonus(this.bonus.id)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe((bonusResponse) => {
                this.setBonus(bonusResponse.body || new Bonus());
            });
    }

    isFormValid() {
        // TODO: Ata
        if (!this.bonus) {
            this.invalidBonusMessage = this.translateService.instant('artemisApp.gradingSystem.error.empty');
            return false;
        }
        return true;
    }

    getGradingScaleTitle(gradingScale: GradingScale): string | undefined {
        return gradingScale?.exam?.title ?? gradingScale?.course?.title;
    }

    getGradingScaleMaxPoints(gradingScale: GradingScale): number {
        return (gradingScale?.exam?.maxPoints ?? gradingScale?.course?.maxPoints) || 0;
    }

    /**
     * @see GradingSystemService.hasPointsSet
     */
    hasPointsSet(): boolean {
        return !!this.bonus.source?.gradeSteps && this.gradingSystemService.hasPointsSet(this.bonus.source.gradeSteps);
    }

    /**
     * Calculate points for each grade step of the selected gradingScale if they are not already calculated before
     * to display in the table.
     * @param gradingScale the selected bonus source grading scale
     */
    setBonusSourcePoints(gradingScale: GradingScale) {
        if (!this.gradingSystemService.hasPointsSet(gradingScale.gradeSteps)) {
            this.gradingSystemService.setGradePoints(gradingScale.gradeSteps, this.getGradingScaleMaxPoints(gradingScale));
        }
    }
}
