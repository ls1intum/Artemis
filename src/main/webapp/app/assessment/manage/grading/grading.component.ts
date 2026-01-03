import { Component, OnInit, inject, signal } from '@angular/core';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { GradeType, GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { GradeStep } from 'app/assessment/shared/entities/grade-step.model';
import { ActivatedRoute } from '@angular/router';
import { EntityResponseType, GradingService } from 'app/assessment/manage/grading/grading-service';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { Observable, Subject, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { catchError, finalize } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { download, generateCsv, mkConfig } from 'export-to-csv';
import { faExclamationTriangle, faInfo, faPlus, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { GradingPresentationsComponent, PresentationType, PresentationsConfig } from 'app/assessment/manage/grading/grading-presentations/grading-presentations.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { GradingInfoModalComponent } from 'app/assessment/manage/grading/grading-info-modal/grading-info-modal.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { ModePickerComponent, ModePickerOption } from 'app/exercise/mode-picker/mode-picker.component';
import { parse } from 'papaparse';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { GradeStepBoundsPipe } from 'app/shared/pipes/grade-step-bounds.pipe';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';

const csvColumnsGrade = Object.freeze({
    gradeName: 'gradeName',
    lowerBoundPercentage: 'lowerBoundPercentage',
    upperBoundPercentage: 'upperBoundPercentage',
    isPassingGrade: 'isPassingGrade',
});

const csvColumnsBonus = Object.freeze({
    bonusPoints: 'bonusPoints',
    lowerBoundPercentage: 'lowerBoundPercentage',
    upperBoundPercentage: 'upperBoundPercentage',
});

// needed to map from csv object to grade step
export type CsvGradeStep = object;

export enum GradeEditMode {
    POINTS,
    PERCENTAGE,
}

export enum GradingViewMode {
    INTERVAL = 'interval',
    DETAILED = 'detailed',
}

@Component({
    selector: 'jhi-grading',
    templateUrl: './grading.component.html',
    styleUrls: ['./grading.component.scss'],
    imports: [
        TranslateDirective,
        GradingInfoModalComponent,
        FaIconComponent,
        NgbTooltip,
        FormsModule,
        GradingPresentationsComponent,
        ArtemisTranslatePipe,
        HelpIconComponent,
        DocumentationButtonComponent,
        ModePickerComponent,
        SafeHtmlPipe,
        GradeStepBoundsPipe,
        DeleteButtonDirective,
    ],
})
export class GradingComponent implements OnInit {
    private readonly gradingService = inject(GradingService);
    private readonly route = inject(ActivatedRoute);
    private readonly translateService = inject(TranslateService);
    private readonly courseService = inject(CourseManagementService);
    private readonly examService = inject(ExamManagementService);

    // Template constants
    readonly GradeType = GradeType;
    readonly GradeEditMode = GradeEditMode;
    readonly GradingViewMode = GradingViewMode;
    readonly ButtonSize = ButtonSize;
    readonly GradingScale = GradingScale;
    readonly documentationType: DocumentationType = 'Grading';

    // State
    gradingScale = new GradingScale();
    lowerBoundInclusivity = true;
    existingGradingScale = false;
    firstPassingGrade?: string;
    courseId?: number;
    examId?: number;
    isExam = false;
    dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    isLoading = false;
    invalidGradeStepsMessage?: string;

    course?: Course;
    exam?: Exam;
    maxPoints?: number;

    /**
     * The current view mode for the grading system.
     * INTERVAL shows a simplified view with percentage intervals.
     * DETAILED shows all fields for each grade step.
     */
    readonly viewMode = signal<GradingViewMode>(GradingViewMode.INTERVAL);

    /**
     * Edit mode for interval view (percentage or points).
     */
    gradeEditMode = GradeEditMode.PERCENTAGE;

    /**
     * Mode picker options for switching between percentage and points in interval view.
     */
    readonly intervalModePickerOptions: ModePickerOption<GradeEditMode>[] = [
        {
            value: GradeEditMode.PERCENTAGE,
            labelKey: 'artemisApp.gradingSystem.intervalTab.percentageMode',
            btnClass: 'btn-secondary',
        },
        {
            value: GradeEditMode.POINTS,
            labelKey: 'artemisApp.gradingSystem.intervalTab.pointsMode',
            btnClass: 'btn-info',
        },
    ];

    /**
     * Configuration for presentation settings in the grading system.
     */
    presentationsConfig: PresentationsConfig = { presentationType: PresentationType.NONE };

    // Icons
    readonly faSave = faSave;
    readonly faPlus = faPlus;
    readonly faTimes = faTimes;
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faInfo = faInfo;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.isLoading = true;
            this.courseId = Number(params['courseId']);
            if (params['examId']) {
                this.examId = Number(params['examId']);
                this.isExam = true;
            }
            if (this.isExam) {
                this.handleFindObservable(this.gradingService.findGradingScaleForExam(this.courseId!, this.examId!));
            } else {
                this.handleFindObservable(this.gradingService.findGradingScaleForCourse(this.courseId!));
            }
        });
    }

    // =========================================================================
    // View Mode
    // =========================================================================

    /**
     * Switches the view mode between interval and detailed views.
     */
    setViewMode(mode: GradingViewMode): void {
        this.viewMode.set(mode);
    }

    // =========================================================================
    // Presentations Config
    // =========================================================================

    /**
     * Handles updates to the presentations configuration emitted by the child component.
     */
    onPresentationsConfigChange(config: PresentationsConfig): void {
        this.presentationsConfig = config;
    }

    // =========================================================================
    // Data Loading
    // =========================================================================

    private handleFindObservable(findObservable: Observable<EntityResponseType>) {
        findObservable
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe((gradingSystemResponse) => {
                if (gradingSystemResponse.body) {
                    this.handleFindResponse(gradingSystemResponse.body);
                }
                if (this.isExam) {
                    this.examService.find(this.courseId!, this.examId!).subscribe((examResponse) => {
                        this.exam = examResponse.body!;
                        this.maxPoints = this.exam?.examMaxPoints;
                        this.onChangeMaxPoints(this.exam?.examMaxPoints);
                    });
                } else {
                    this.courseService.find(this.courseId!).subscribe((courseResponse) => {
                        this.course = courseResponse.body!;
                        this.gradingScale.course = this.course;
                        this.maxPoints = this.course?.maxPoints;
                        this.onChangeMaxPoints(this.course?.maxPoints);
                    });
                }
            });
    }

    /**
     * If the grading scale exists, sorts its grade steps,
     * and sets the inclusivity and first passing grade properties
     */
    handleFindResponse(gradingScale?: GradingScale): void {
        if (gradingScale) {
            gradingScale.gradeSteps = this.gradingService.sortGradeSteps(gradingScale.gradeSteps);
            this.gradingScale = gradingScale;
            this.existingGradingScale = true;
            this.setBoundInclusivity();
            this.determineFirstPassingGrade();
        }
    }

    // =========================================================================
    // Save
    // =========================================================================

    /**
     * Sorts the grade steps by lower bound percentage, sets their inclusivity
     * and passing grade properties and saves the grading scale via the service
     */
    save(): void {
        this.isLoading = true;
        this.gradingScale.gradeSteps = this.gradingService.sortGradeSteps(this.gradingScale.gradeSteps);
        this.setInclusivity();
        this.gradingScale.gradeSteps = this.setPassingGrades(this.gradingScale.gradeSteps);
        // new grade steps shouldn't have ids set
        this.gradingScale.gradeSteps.forEach((gradeStep) => {
            gradeStep.id = undefined;
        });
        if (this.isExam) {
            this.gradingScale.exam = this.exam;
            this.gradingScale.exam!.examMaxPoints = this.maxPoints;
        } else {
            this.gradingScale.course = this.course;
            this.gradingScale.course!.maxPoints = this.maxPoints;
            this.gradingScale.course!.presentationScore = this.presentationsConfig.presentationScore;
        }
        if (this.existingGradingScale) {
            if (this.isExam) {
                this.handleSaveObservable(this.gradingService.updateGradingScaleForExam(this.courseId!, this.examId!, this.gradingScale));
            } else {
                this.handleSaveObservable(this.gradingService.updateGradingScaleForCourse(this.courseId!, this.gradingScale));
            }
        } else {
            if (this.isExam) {
                this.handleSaveObservable(this.gradingService.createGradingScaleForExam(this.courseId!, this.examId!, this.gradingScale));
            } else {
                this.handleSaveObservable(this.gradingService.createGradingScaleForCourse(this.courseId!, this.gradingScale));
            }
        }
    }

    private handleSaveObservable(saveObservable: Observable<EntityResponseType>) {
        saveObservable
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
                catchError(() => of(new HttpResponse<GradingScale>({ status: 400 }))),
            )
            .subscribe((gradingSystemResponse) => {
                this.handleSaveResponse(gradingSystemResponse.body!);
            });
    }

    private handleSaveResponse(newGradingScale?: GradingScale): void {
        if (newGradingScale) {
            newGradingScale.gradeSteps = this.gradingService.sortGradeSteps(newGradingScale.gradeSteps);
            this.existingGradingScale = true;
        }
    }

    // =========================================================================
    // Delete
    // =========================================================================

    /**
     * Deletes a grading scale for the given course/exam via the service
     */
    delete(): void {
        if (!this.existingGradingScale) {
            return;
        }
        this.isLoading = true;
        if (this.isExam) {
            this.handleDeleteObservable(this.gradingService.deleteGradingScaleForExam(this.courseId!, this.examId!));
        } else {
            this.handleDeleteObservable(this.gradingService.deleteGradingScaleForCourse(this.courseId!));
        }
        this.gradingScale = new GradingScale();
        this.gradingScale.course = this.course;
    }

    handleDeleteObservable(deleteObservable: Observable<HttpResponse<void>>) {
        deleteObservable
            .pipe(
                catchError(() => of(new HttpResponse<void>({ status: 400 }))),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(() => {
                this.existingGradingScale = false;
                this.dialogErrorSource.next('');
            });
    }

    // =========================================================================
    // Validation
    // =========================================================================

    /**
     * Checks if the currently entered grade steps are valid based on multiple criteria
     */
    validGradeSteps(): boolean {
        if (!this.gradingScale || this.gradingScale.gradeSteps.length === 0) {
            this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.empty');
            return false;
        }
        // check if max points are at least 0, if they are defined
        if (this.maxPoints != undefined && this.maxPoints! < 0) {
            this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.negativeMaxPoints');
            return false;
        }
        // check if any of the fields are empty
        for (const gradeStep of this.gradingScale.gradeSteps) {
            if (gradeStep.gradeName === '' || gradeStep.gradeName === null || gradeStep.lowerBoundPercentage === null || gradeStep.upperBoundPercentage === null) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.emptyFields');
                return false;
            }
            if (this.maxPointsValid() && (gradeStep.lowerBoundPoints == undefined || gradeStep.upperBoundPoints == undefined)) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.emptyFields');
                return false;
            }
        }
        // check if any of the fields have invalid percentages
        for (const gradeStep of this.gradingScale.gradeSteps) {
            if (gradeStep.lowerBoundPercentage! < 0 || gradeStep.lowerBoundPercentage! >= gradeStep.upperBoundPercentage!) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.invalidMinMaxPercentages');
                return false;
            }
        }
        // check if any of the fields have invalid points
        if (this.maxPointsValid()) {
            for (const gradeStep of this.gradingScale.gradeSteps) {
                if (gradeStep.lowerBoundPoints! < 0 || gradeStep.lowerBoundPoints! >= gradeStep.upperBoundPoints!) {
                    this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.invalidMinMaxPoints');
                    return false;
                }
            }
        } else {
            // ensures that all updated have taken place before the grading key can be saved
            for (const gradeStep of this.gradingScale.gradeSteps) {
                if (gradeStep.lowerBoundPoints != undefined || gradeStep.upperBoundPoints != undefined) {
                    return false;
                }
            }
        }
        if (this.gradingScale.gradeType === GradeType.GRADE) {
            // check if all grade names are unique
            if (!this.gradingScale.gradeSteps.map((gradeStep) => gradeStep.gradeName).every((gradeName, index, gradeNames) => gradeNames.indexOf(gradeName) === index)) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.nonUniqueGradeNames');
                return false;
            }
            // check if the first passing grade is set
            if (this.firstPassingGrade === undefined || this.firstPassingGrade === '') {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.unsetFirstPassingGrade');
                return false;
            }
        }
        // copy the grade steps in a separate array, so they don't get dynamically updated when sorting
        let sortedGradeSteps: GradeStep[] = [];
        this.gradingScale.gradeSteps.forEach((gradeStep) => sortedGradeSteps.push(Object.assign({}, gradeStep)));
        sortedGradeSteps = this.gradingService.sortGradeSteps(sortedGradeSteps);
        if (this.gradingScale.gradeType === GradeType.BONUS) {
            // check if when the grade type is BONUS the bonus points are at least 0
            for (const gradeStep of sortedGradeSteps) {
                if (isNaN(Number(gradeStep.gradeName)) || Number(gradeStep.gradeName) < 0) {
                    this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.invalidBonusPoints');
                    return false;
                }
            }
            // check if when the grade type is BONUS the bonus points have strictly ascending values
            if (
                !sortedGradeSteps
                    .map((gradeStep) => Number(gradeStep.gradeName))
                    .every((bonusPoints, index, bonusPointsArray) => index === 0 || bonusPoints > bonusPointsArray[index - 1])
            ) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.nonStrictlyIncreasingBonusPoints');
                return false;
            }
        }

        // check if grade steps have valid adjacency
        for (let i = 0; i < sortedGradeSteps.length - 1; i++) {
            if (sortedGradeSteps[i].upperBoundPercentage !== sortedGradeSteps[i + 1].lowerBoundPercentage) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.invalidAdjacency');
                return false;
            }
        }
        // check if first and last grade step are valid
        if (sortedGradeSteps[0].lowerBoundPercentage !== 0 || sortedGradeSteps.last()!.upperBoundPercentage < 100) {
            this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.invalidFirstAndLastStep');
            return false;
        }
        this.invalidGradeStepsMessage = undefined;
        return true;
    }

    /**
     * Checks if the currently entered presentation settings are valid
     */
    validPresentationsConfig(): boolean {
        if (this.presentationsConfig.presentationType === PresentationType.NONE) {
            if (this.presentationsConfig.presentationsNumber !== undefined || this.presentationsConfig.presentationsWeight !== undefined) {
                return false;
            }
            if (this.presentationsConfig.presentationScore !== undefined) {
                return false;
            }
        }
        if (this.presentationsConfig.presentationType === PresentationType.BASIC) {
            if (this.presentationsConfig.presentationsNumber !== undefined || this.presentationsConfig.presentationsWeight !== undefined) {
                return false;
            }
            if ((this.course?.presentationScore ?? 0) <= 0) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.invalidPresentationsNumber');
                return false;
            }
        }
        if (this.presentationsConfig.presentationType === PresentationType.GRADED) {
            if (
                this.presentationsConfig.presentationsNumber === undefined ||
                !Number.isInteger(this.presentationsConfig.presentationsNumber) ||
                this.presentationsConfig.presentationsNumber < 1
            ) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.invalidPresentationsNumber');
                return false;
            }
            if (
                this.presentationsConfig.presentationsWeight === undefined ||
                this.presentationsConfig.presentationsWeight < 0 ||
                this.presentationsConfig.presentationsWeight > 99
            ) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.invalidPresentationsWeight');
                return false;
            }
            if ((this.course?.presentationScore ?? 0) > 0) {
                this.invalidGradeStepsMessage = this.translateService.instant('artemisApp.gradingSystem.error.invalidBasicPresentationIsEnabled');
                return false;
            }
        }
        this.invalidGradeStepsMessage = undefined;
        return true;
    }

    maxPointsValid(): boolean {
        return this.maxPoints != undefined && this.maxPoints! > 0;
    }

    // =========================================================================
    // Points/Percentage Conversion
    // =========================================================================

    setPercentage(gradeStep: GradeStep, lowerBound: boolean) {
        if (lowerBound) {
            gradeStep.lowerBoundPercentage = (gradeStep.lowerBoundPoints! / this.maxPoints!) * 100;
        } else {
            gradeStep.upperBoundPercentage = (gradeStep.upperBoundPoints! / this.maxPoints!) * 100;
        }
    }

    setPoints(gradeStep: GradeStep, lowerBound: boolean): void {
        if (!this.maxPoints) {
            return;
        } else {
            if (lowerBound) {
                gradeStep.lowerBoundPoints = (this.maxPoints! * gradeStep.lowerBoundPercentage) / 100;
            } else {
                gradeStep.upperBoundPoints = (this.maxPoints! * gradeStep.upperBoundPercentage) / 100;
            }
        }
    }

    onChangeMaxPoints(maxPoints?: number): void {
        if (maxPoints == undefined || maxPoints < 0) {
            for (const gradeStep of this.gradingScale.gradeSteps) {
                gradeStep.lowerBoundPoints = undefined;
                gradeStep.upperBoundPoints = undefined;
            }
        } else {
            for (const gradeStep of this.gradingScale.gradeSteps) {
                this.setPoints(gradeStep, true);
                this.setPoints(gradeStep, false);
            }
        }
    }

    // =========================================================================
    // Inclusivity
    // =========================================================================

    setBoundInclusivity(): void {
        const lastStepId = this.gradingScale.gradeSteps.last()?.id;
        this.lowerBoundInclusivity = this.gradingScale.gradeSteps.every((gradeStep) => {
            if (gradeStep.id === lastStepId) {
                return true;
            }
            return gradeStep.lowerBoundInclusive || gradeStep.lowerBoundPercentage === 0;
        });
    }

    /**
     * Sets the inclusivity for all grade steps based on the lowerBoundInclusivity property.
     * Implementation differs between interval and detailed modes.
     */
    setInclusivity(): void {
        if (this.viewMode() === GradingViewMode.DETAILED) {
            this.setInclusivityDetailed();
        } else {
            this.setInclusivityInterval();
        }
    }

    private setInclusivityInterval(): void {
        const gradeSteps = this.gradingScale?.gradeSteps;
        if (!(gradeSteps?.length > 0)) {
            return;
        }

        gradeSteps.forEach((gradeStep) => {
            gradeStep.lowerBoundInclusive = this.lowerBoundInclusivity;
            gradeStep.upperBoundInclusive = !this.lowerBoundInclusivity;
        });

        // Always true for first and last
        gradeSteps.first()!.lowerBoundInclusive = true;
        gradeSteps.last()!.upperBoundInclusive = true;
    }

    private setInclusivityDetailed(): void {
        const gradeSteps = this.gradingScale.gradeSteps;
        let sortedGradeSteps = gradeSteps.slice();
        sortedGradeSteps = this.gradingService.sortGradeSteps(sortedGradeSteps);

        gradeSteps.forEach((gradeStep) => {
            if (this.lowerBoundInclusivity) {
                gradeStep.lowerBoundInclusive = true;
                gradeStep.upperBoundInclusive = sortedGradeSteps.last()!.gradeName === gradeStep.gradeName;
            } else {
                gradeStep.lowerBoundInclusive = sortedGradeSteps.first()!.gradeName === gradeStep.gradeName;
                gradeStep.upperBoundInclusive = true;
            }
        });
    }

    // =========================================================================
    // Passing Grades
    // =========================================================================

    determineFirstPassingGrade(): void {
        this.firstPassingGrade = this.gradingScale.gradeSteps.find((gradeStep) => {
            return gradeStep.isPassingGrade;
        })?.gradeName;
    }

    setPassingGrades(gradeSteps: GradeStep[]): GradeStep[] {
        let passingGrade = false;
        gradeSteps.forEach((gradeStep) => {
            if (gradeStep.gradeName === this.firstPassingGrade) {
                passingGrade = true;
            }
            gradeStep.isPassingGrade = passingGrade;
        });
        return gradeSteps;
    }

    deleteGradeNames(): void {
        this.gradingScale.gradeSteps.forEach((gradeStep) => {
            gradeStep.gradeName = '';
        });
    }

    gradeStepsWithNonemptyNames(): GradeStep[] {
        if (this.gradingScale.gradeSteps) {
            return this.gradingScale.gradeSteps.filter((gradeStep) => {
                return gradeStep.gradeName !== '';
            });
        } else {
            return [];
        }
    }

    // =========================================================================
    // Grade Step CRUD
    // =========================================================================

    /**
     * Creates a new grade step. In interval mode, handles the sticky grade step at the end.
     */
    createGradeStep(): void {
        if (this.viewMode() === GradingViewMode.DETAILED) {
            this.createGradeStepBasic();
            return;
        }

        // Interval mode: handle sticky grade step
        if (this.gradingScale?.gradeSteps?.length === 0) {
            this.createGradeStepBasic();
        }

        const stickyGradeStep = this.gradingScale.gradeSteps.pop()!;
        this.createGradeStepBasic();
        this.gradingScale.gradeSteps.push(stickyGradeStep);

        const selectedIndex = this.gradingScale.gradeSteps.length - 2;
        this.setPercentageInterval(selectedIndex);
    }

    private createGradeStepBasic(): void {
        if (!this.gradingScale) {
            this.gradingScale = new GradingScale();
        }
        if (!this.gradingScale.gradeSteps) {
            this.gradingScale.gradeSteps = [];
        }
        const gradeStepsArrayLength = this.gradingScale.gradeSteps.length;
        const lowerBound = gradeStepsArrayLength === 0 ? 0 : this.gradingScale.gradeSteps.last()!.upperBoundPercentage;
        const gradeStep: GradeStep = {
            gradeName: '',
            lowerBoundPercentage: lowerBound,
            upperBoundPercentage: 100,
            isPassingGrade: true,
            lowerBoundInclusive: this.lowerBoundInclusivity,
            upperBoundInclusive: !this.lowerBoundInclusivity,
        };
        this.setPoints(gradeStep, true);
        this.setPoints(gradeStep, false);
        this.gradingScale.gradeSteps.push(gradeStep);
    }

    /**
     * Deletes a grade step. In interval mode, handles percentage recalculation.
     */
    deleteGradeStep(index: number): void {
        if (this.viewMode() === GradingViewMode.DETAILED) {
            this.gradingScale.gradeSteps.splice(index, 1);
            return;
        }

        // Interval mode: handle percentage recalculation
        this.setPercentageInterval(index, 0);
        this.gradingScale.gradeSteps.splice(index, 1);
        const gradeSteps = this.gradingScale.gradeSteps;

        if (gradeSteps.length > 0) {
            if (gradeSteps.last()!.upperBoundPercentage < 100) {
                gradeSteps.last()!.upperBoundPercentage = 100;
            }
            gradeSteps.first()!.lowerBoundInclusive = true;
            gradeSteps.last()!.upperBoundInclusive = true;
        }
    }

    // =========================================================================
    // Interval-specific Methods
    // =========================================================================

    setPercentageInterval(selectedIndex: number, newPercentageInterval?: number): void {
        const gradeSteps = this.gradingScale.gradeSteps;
        let previousGradeStep: GradeStep | undefined = undefined;

        for (let i = selectedIndex; i < gradeSteps.length; i++) {
            const currentGradeStep = gradeSteps[i];
            let currentInterval: number;

            if (previousGradeStep) {
                currentInterval = this.getPercentageInterval(currentGradeStep);
                currentGradeStep.lowerBoundPercentage = previousGradeStep.upperBoundPercentage;
            } else {
                currentInterval = newPercentageInterval ?? this.getPercentageInterval(currentGradeStep);
            }

            currentGradeStep.upperBoundPercentage = currentGradeStep.lowerBoundPercentage + currentInterval;

            this.setPoints(currentGradeStep, true);
            this.setPoints(currentGradeStep, false);

            previousGradeStep = currentGradeStep;
        }
    }

    setPointsInterval(selectedIndex: number, newPointsInterval: number): void {
        const gradeStep = this.gradingScale.gradeSteps[selectedIndex];
        if (gradeStep.lowerBoundPoints == undefined) {
            throw new Error(`lowerBoundPoints are not set yet for selectedIndex: '${selectedIndex}'`);
        }
        gradeStep.upperBoundPoints = gradeStep.lowerBoundPoints + newPointsInterval;
        this.setPercentage(gradeStep, false);
        this.setPercentageInterval(selectedIndex);
    }

    getPercentageInterval(gradeStep: GradeStep): number {
        return gradeStep.upperBoundPercentage - gradeStep.lowerBoundPercentage;
    }

    getPointsInterval(gradeStep: GradeStep): number | undefined {
        if (gradeStep.lowerBoundPoints == undefined || gradeStep.upperBoundPoints == undefined) {
            return undefined;
        }
        const raw = gradeStep.upperBoundPoints - gradeStep.lowerBoundPoints;
        const floored = raw < 0.5 ? 0.5 : raw;
        return parseFloat(floored.toFixed(6));
    }

    // =========================================================================
    // Default Grading Scale
    // =========================================================================

    generateDefaultGradingScale(): void {
        this.gradingScale = this.getDefaultGradingScale();
        this.firstPassingGrade = this.gradingScale.gradeSteps[3].gradeName;
        this.lowerBoundInclusivity = true;
    }

    getDefaultGradingScale(): GradingScale {
        const gradeStep1: GradeStep = {
            gradeName: '5.0',
            lowerBoundPercentage: 0,
            upperBoundPercentage: 40,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: false,
        };
        const gradeStep2: GradeStep = {
            gradeName: '4.7',
            lowerBoundPercentage: 40,
            upperBoundPercentage: 45,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: false,
        };
        const gradeStep3: GradeStep = {
            gradeName: '4.3',
            lowerBoundPercentage: 45,
            upperBoundPercentage: 50,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: false,
        };
        const gradeStep4: GradeStep = {
            gradeName: '4.0',
            lowerBoundPercentage: 50,
            upperBoundPercentage: 55,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep5: GradeStep = {
            gradeName: '3.7',
            lowerBoundPercentage: 55,
            upperBoundPercentage: 60,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep6: GradeStep = {
            gradeName: '3.3',
            lowerBoundPercentage: 60,
            upperBoundPercentage: 65,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep7: GradeStep = {
            gradeName: '3.0',
            lowerBoundPercentage: 65,
            upperBoundPercentage: 70,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep8: GradeStep = {
            gradeName: '2.7',
            lowerBoundPercentage: 70,
            upperBoundPercentage: 75,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep9: GradeStep = {
            gradeName: '2.3',
            lowerBoundPercentage: 75,
            upperBoundPercentage: 80,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep10: GradeStep = {
            gradeName: '2.0',
            lowerBoundPercentage: 80,
            upperBoundPercentage: 85,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep11: GradeStep = {
            gradeName: '1.7',
            lowerBoundPercentage: 85,
            upperBoundPercentage: 90,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep12: GradeStep = {
            gradeName: '1.3',
            lowerBoundPercentage: 90,
            upperBoundPercentage: 95,
            lowerBoundInclusive: true,
            upperBoundInclusive: false,
            isPassingGrade: true,
        };
        const gradeStep13: GradeStep = {
            gradeName: '1.0',
            lowerBoundPercentage: 95,
            upperBoundPercentage: 100,
            lowerBoundInclusive: true,
            upperBoundInclusive: true,
            isPassingGrade: true,
        };
        const gradeSteps = [
            gradeStep1,
            gradeStep2,
            gradeStep3,
            gradeStep4,
            gradeStep5,
            gradeStep6,
            gradeStep7,
            gradeStep8,
            gradeStep9,
            gradeStep10,
            gradeStep11,
            gradeStep12,
            gradeStep13,
        ];
        for (const gradeStep of gradeSteps) {
            this.setPoints(gradeStep, true);
            this.setPoints(gradeStep, false);
        }
        return {
            gradeSteps,
            gradeType: GradeType.GRADE,
            course: this.course,
        };
    }

    // =========================================================================
    // CSV Import/Export
    // =========================================================================

    async onCSVFileSelect(event: any) {
        if (event.target.files.length > 0) {
            await this.readGradingStepsFromCSVFile(event.target.files[0]);
            this.lowerBoundInclusivity = true;
            this.setInclusivity();
            this.maxPoints = 100;
            this.onChangeMaxPoints(this.maxPoints);
            this.determineFirstPassingGrade();
            this.gradingScale.gradeSteps.sort((a, b) => a.lowerBoundPercentage - b.lowerBoundPercentage);
        }
    }

    private async readGradingStepsFromCSVFile(csvFile: File) {
        let csvGradeSteps: CsvGradeStep[] = [];
        try {
            csvGradeSteps = await this.parseCSVFile(csvFile);
        } catch (error) {
            return [];
        }

        if (csvGradeSteps.length === 0 || csvGradeSteps.length > 100) {
            this.gradingScale.gradeSteps = [];
            return;
        }

        const gradeType = csvGradeSteps[0]['bonusPoints' as keyof CsvGradeStep] === undefined ? GradeType.GRADE : GradeType.BONUS;
        if (gradeType === GradeType.BONUS) {
            this.gradingScale.gradeType = GradeType.BONUS;
        } else {
            this.gradingScale.gradeType = GradeType.GRADE;
        }

        this.gradingScale.gradeSteps = this.mapCsvGradeStepsToGradeSteps(csvGradeSteps, gradeType);
    }

    parseCSVFile(csvFile: File): Promise<CsvGradeStep[]> {
        return new Promise((resolve, reject) => {
            parse(csvFile, {
                header: true,
                skipEmptyLines: true,
                dynamicTyping: false,
                complete: (results) => resolve(results.data as CsvGradeStep[]),
                error: (error) => reject(error),
            });
        });
    }

    mapCsvGradeStepsToGradeSteps(csvGradeSteps: CsvGradeStep[], gradeType: GradeType): GradeStep[] {
        return csvGradeSteps.map(
            (csvGradeStep: CsvGradeStep) =>
                ({
                    gradeName:
                        gradeType === GradeType.GRADE
                            ? String(csvGradeStep[csvColumnsGrade.gradeName as keyof CsvGradeStep] ?? '')
                            : String(csvGradeStep[csvColumnsBonus.bonusPoints as keyof CsvGradeStep] ?? ''),
                    lowerBoundPercentage: csvGradeStep[csvColumnsGrade.lowerBoundPercentage as keyof CsvGradeStep]
                        ? Number(csvGradeStep[csvColumnsGrade.lowerBoundPercentage as keyof CsvGradeStep])
                        : undefined,
                    upperBoundPercentage: csvGradeStep[csvColumnsGrade.upperBoundPercentage as keyof CsvGradeStep]
                        ? Number(csvGradeStep[csvColumnsGrade.upperBoundPercentage as keyof CsvGradeStep])
                        : undefined,
                    ...(gradeType === GradeType.GRADE && { isPassingGrade: csvGradeStep[csvColumnsGrade.isPassingGrade as keyof CsvGradeStep] === 'TRUE' }),
                }) as GradeStep,
        );
    }

    exportGradingStepsToCsv(): void {
        const headers = this.gradingScale.gradeType === GradeType.GRADE ? Object.keys(csvColumnsGrade) : Object.keys(csvColumnsBonus);
        const rows = this.gradingScale.gradeSteps.map((gradeStep) => this.convertToCsvRow(gradeStep));
        this.exportAsCSV(rows, headers);
    }

    convertToCsvRow(gradeStep: GradeStep): any {
        return {
            ...(this.gradingScale.gradeType === GradeType.GRADE && { gradeName: gradeStep.gradeName ?? '' }),
            ...(this.gradingScale.gradeType === GradeType.BONUS && { bonusPoints: gradeStep.gradeName ?? '' }),
            lowerBoundPercentage: gradeStep.lowerBoundPercentage ?? '',
            upperBoundPercentage: gradeStep.upperBoundPercentage ?? '',
            ...(this.gradingScale.gradeType === GradeType.GRADE && { isPassingGrade: gradeStep.isPassingGrade }),
        };
    }

    exportAsCSV(rows: any[], headers: string[]): void {
        const options = {
            fieldSeparator: ',',
            quoteStrings: false,
            decimalSeparator: 'locale',
            showLabels: true,
            filename: 'grading_key' + (this.gradingScale.course?.shortName ? '_' + this.gradingScale.course?.shortName : ''),
            useTextFile: false,
            useBom: true,
            columnHeaders: headers,
        };

        const csvExportConfig = mkConfig(options);
        const csvData = generateCsv(csvExportConfig)(rows);
        download(csvExportConfig)(csvData);
    }

    // =========================================================================
    // Warnings
    // =========================================================================

    shouldShowGradingStepsAboveMaxPointsWarning(): boolean {
        if (this.viewMode() === GradingViewMode.DETAILED) {
            return this.isAnyGradingStepAboveMaxPoints(this.gradingScale.gradeSteps);
        } else {
            const steps = [...this.gradingScale.gradeSteps].slice(0, this.gradingScale.gradeSteps.length - 1);
            return this.isAnyGradingStepAboveMaxPoints(steps);
        }
    }

    private isAnyGradingStepAboveMaxPoints(steps: GradeStep[]): boolean {
        for (const step of steps) {
            if (step.upperBoundInclusive && step.upperBoundPercentage > 100) {
                return true;
            }
            if (!step.upperBoundInclusive && step.upperBoundPercentage >= 100) {
                return true;
            }
        }
        return false;
    }
}
