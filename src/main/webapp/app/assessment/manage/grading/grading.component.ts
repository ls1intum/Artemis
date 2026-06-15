import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormField, applyEach, form, required, validate, validateTree } from '@angular/forms/signals';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { GradeType, GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { GradeStep } from 'app/assessment/shared/entities/grade-step.model';
import { ActivatedRoute } from '@angular/router';
import { EntityResponseType, GradingService } from 'app/assessment/manage/grading/grading-service';
import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
import { Observable, Subject, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { catchError, finalize } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { download, generateCsv, mkConfig } from 'export-to-csv';
import { faExclamationTriangle, faInfo, faPlus, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { GradingPresentationsComponent, PresentationType, PresentationsConfig } from 'app/assessment/manage/grading/grading-presentations/grading-presentations.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { GradingInfoModalComponent } from 'app/assessment/manage/grading/grading-info-modal/grading-info-modal.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { ModePickerComponent, ModePickerOption } from 'app/exercise/mode-picker/mode-picker.component';
import { parse } from 'papaparse';
import { SafeHtmlPipe } from 'app/foundation/pipes/safe-html.pipe';
import { GradeStepBoundsPipe } from 'app/foundation/pipes/grade-step-bounds.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { GradingScaleDTO, toEntity } from 'app/assessment/shared/entities/grading-scale-dto.model';

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

// needed to map from csv object to the grade step
export type CsvGradeStep = object;

export enum GradeEditMode {
    POINTS,
    PERCENTAGE,
}

export enum GradingViewMode {
    INTERVAL = 'interval',
    DETAILED = 'detailed',
}

/**
 * Narrow, plain-object model that backs the {@link https://angular.dev/guide/forms/signals signal form} for the
 * grade-step editor. It deliberately holds only the editable parts of a {@link GradingScale} (grade type, the special
 * grade names, and the grade steps) so the form does not build field trees for the heavy nested associations of a full
 * {@link GradingScale} (course, exam, bonuses). The remaining {@link GradingScale} fields are kept in {@link GradingComponent.gradingScaleMeta}
 * and recombined via the {@link GradingComponent.gradingScale} getter/setter.
 */
export interface GradeStepsFormModel {
    gradeType: GradeType;
    // Non-optional (default '') so the signal-form `[formField]` directive can bind them (it requires a definite field).
    // The empty string is coalesced back to `undefined` in the {@link GradingComponent.gradingScale} getter.
    plagiarismGrade: string;
    noParticipationGrade: string;
    gradeSteps: GradeStep[];
}

/** The non-editable {@link GradingScale} fields kept aside while the editor works on {@link GradeStepsFormModel}. */
type GradingScaleMeta = Pick<GradingScale, 'id' | 'bonusStrategy' | 'GradeStep' | 'course' | 'exam' | 'presentationsNumber' | 'presentationsWeight' | 'bonusFrom'>;

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
        FormField,
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
    /**
     * Source of truth for the editable grade-step data, backing the signal {@link gradingForm}.
     * Mutations go through {@link updateGradeSteps}; reads of the full {@link GradingScale} use the {@link gradingScale} accessor.
     */
    readonly gradeStepsModel = signal<GradeStepsFormModel>({ gradeType: GradeType.GRADE, plagiarismGrade: '', noParticipationGrade: '', gradeSteps: [] });

    /** The non-editable {@link GradingScale} fields, recombined with {@link gradeStepsModel} in the {@link gradingScale} accessor. */
    private gradingScaleMeta: GradingScaleMeta = {};

    /**
     * Signal form over {@link gradeStepsModel}. Drives the grade-step rows reactively (so they render under zoneless) and
     * provides {@link gradingForm}().valid()/invalid() for the Save button instead of a method called from the template.
     */
    readonly gradingForm = form(this.gradeStepsModel, (path) => {
        applyEach(path.gradeSteps, (step) => {
            required(step.gradeName, { message: this.translateService.instant('artemisApp.gradingSystem.error.emptyFields') });
            validate(step, ({ value }) => this.gradeStepFieldErrors(value()));
        });
        validateTree(path.gradeSteps, ({ value, valueOf }) => this.gradeStepStructureErrors(value(), valueOf(path.gradeType)));
    });

    /**
     * First validation error message (if any) reported by {@link gradingForm}, shown in the warning banner.
     * Uses {@link FieldState.errorSummary} (own + descendant errors) because the validators live on the grade-step
     * sub-fields, so the root field's own {@link FieldState.errors} would be empty.
     */
    readonly invalidGradeStepsMessage = computed(() => this.gradingForm().errorSummary()[0]?.message);

    lowerBoundInclusivity = true;
    readonly existingGradingScale = signal(false);
    firstPassingGrade = signal<string | undefined>(undefined);
    courseId?: number;
    examId?: number;
    readonly isExam = signal(false);
    dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    readonly isLoading = signal(false);

    readonly course = signal<Course | undefined>(undefined);
    readonly exam = signal<Exam | undefined>(undefined);
    maxPoints = signal<number | undefined>(undefined);

    /**
     * Recombines the editable {@link gradeStepsModel} with {@link gradingScaleMeta} into a full {@link GradingScale}.
     * Reading this in a reactive context (template, computed, validator) tracks {@link gradeStepsModel}.
     */
    get gradingScale(): GradingScale {
        const model = this.gradeStepsModel();
        return Object.assign(new GradingScale(), this.gradingScaleMeta, {
            gradeType: model.gradeType,
            plagiarismGrade: model.plagiarismGrade || undefined,
            noParticipationGrade: model.noParticipationGrade || undefined,
            gradeSteps: model.gradeSteps,
        });
    }

    set gradingScale(value: GradingScale) {
        this.gradingScaleMeta = {
            id: value.id,
            bonusStrategy: value.bonusStrategy,
            GradeStep: value.GradeStep,
            course: value.course,
            exam: value.exam,
            presentationsNumber: value.presentationsNumber,
            presentationsWeight: value.presentationsWeight,
            bonusFrom: value.bonusFrom,
        };
        this.gradeStepsModel.set({
            gradeType: value.gradeType,
            plagiarismGrade: value.plagiarismGrade ?? '',
            noParticipationGrade: value.noParticipationGrade ?? '',
            gradeSteps: value.gradeSteps ?? [],
        });
    }

    /**
     * Applies an in-place mutation of the grade-step array, then commits a fresh copy to {@link gradeStepsModel}
     * so the signal form and the rendered rows react. Existing imperative logic can keep mutating the passed array.
     */
    private updateGradeSteps(mutate: (gradeSteps: GradeStep[]) => void): void {
        this.gradeStepsModel.update((model) => {
            const gradeSteps = model.gradeSteps.map((gradeStep) => ({ ...gradeStep }));
            mutate(gradeSteps);
            return { ...model, gradeSteps };
        });
    }

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
     * Mode picker options for switching between percentage and points in the interval view.
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
    readonly presentationsConfig = signal<PresentationsConfig>({ presentationType: PresentationType.NONE });

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
            this.isLoading.set(true);
            this.courseId = Number(params['courseId']);
            if (params['examId']) {
                this.examId = Number(params['examId']);
                this.isExam.set(true);
            }
            if (this.isExam()) {
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
     * Switches the view mode between the interval and detailed views.
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
        this.presentationsConfig.set(config);
    }

    // =========================================================================
    // Data Loading
    // =========================================================================

    private handleFindObservable(findObservable: Observable<EntityResponseType>) {
        findObservable
            .pipe(
                finalize(() => {
                    this.isLoading.set(false);
                }),
            )
            .subscribe((gradingSystemResponse) => {
                if (gradingSystemResponse.body) {
                    this.handleFindResponse(gradingSystemResponse.body);
                }
                if (this.isExam()) {
                    this.examService.find(this.courseId!, this.examId!).subscribe((examResponse) => {
                        this.exam.set(examResponse.body!);
                        this.maxPoints.set(this.exam()?.examMaxPoints);
                        this.onChangeMaxPoints(this.exam()?.examMaxPoints);
                    });
                } else {
                    this.courseService.find(this.courseId!).subscribe((courseResponse) => {
                        this.course.set(courseResponse.body!);
                        this.gradingScaleMeta.course = this.course();
                        this.maxPoints.set(this.course()?.maxPoints);
                        this.onChangeMaxPoints(this.course()?.maxPoints);
                    });
                }
            });
    }

    /**
     * If the grading scale exists, sorts its grade steps
     * and sets the inclusivity and first passing grade properties
     */
    handleFindResponse(gradingScaleDTO?: GradingScaleDTO): void {
        if (gradingScaleDTO) {
            gradingScaleDTO.gradeSteps.gradeSteps = this.gradingService.sortGradeSteps(gradingScaleDTO.gradeSteps.gradeSteps);
            this.gradingScale = toEntity(gradingScaleDTO, this.course(), this.exam());
            this.existingGradingScale.set(true);
            this.setBoundInclusivity();
            this.determineFirstPassingGrade();
        }
    }

    // =========================================================================
    // Save
    // =========================================================================

    /**
     * Sorts the grade steps by lower bound percentage, sets their inclusivity
     * and passing grade properties, and saves the grading scale via the service
     */
    save(): void {
        this.isLoading.set(true);
        // Capture the recombined scale once (the getter rebuilds it from the signal model on each access) and
        // operate on this local copy before sending it to the server.
        const gradingScale = this.gradingScale;
        gradingScale.gradeSteps = this.gradingService.sortGradeSteps(gradingScale.gradeSteps);
        this.applyInclusivity(gradingScale.gradeSteps);
        gradingScale.gradeSteps = this.setPassingGrades(gradingScale.gradeSteps);
        // new grade steps shouldn't have ids set
        gradingScale.gradeSteps.forEach((gradeStep) => {
            gradeStep.id = undefined;
        });
        if (this.isExam()) {
            gradingScale.exam = this.exam();
            gradingScale.exam!.examMaxPoints = this.maxPoints();
        } else {
            gradingScale.course = this.course();
            gradingScale.course!.maxPoints = this.maxPoints();
            gradingScale.course!.presentationScore = this.presentationsConfig().presentationScore;
        }
        // Reflect the sorted/inclusivity-adjusted scale that is being sent back into the editor's model.
        this.gradingScale = gradingScale;
        if (this.existingGradingScale()) {
            if (this.isExam()) {
                this.handleSaveObservable(this.gradingService.updateGradingScaleForExam(this.courseId!, this.examId!, gradingScale));
            } else {
                this.handleSaveObservable(this.gradingService.updateGradingScaleForCourse(this.courseId!, gradingScale));
            }
        } else {
            if (this.isExam()) {
                this.handleSaveObservable(this.gradingService.createGradingScaleForExam(this.courseId!, this.examId!, gradingScale));
            } else {
                this.handleSaveObservable(this.gradingService.createGradingScaleForCourse(this.courseId!, gradingScale));
            }
        }
    }

    private handleSaveObservable(saveObservable: Observable<EntityResponseType>) {
        saveObservable
            .pipe(
                finalize(() => {
                    this.isLoading.set(false);
                }),
                catchError(() => of(new HttpResponse<GradingScaleDTO>({ status: 400 }))),
            )
            .subscribe((gradingSystemResponse) => {
                this.handleSaveResponse(gradingSystemResponse.body!);
            });
    }

    private handleSaveResponse(newGradingScaleDTO?: GradingScaleDTO): void {
        if (newGradingScaleDTO) {
            newGradingScaleDTO.gradeSteps.gradeSteps = this.gradingService.sortGradeSteps(newGradingScaleDTO.gradeSteps.gradeSteps);
            this.existingGradingScale.set(true);
        }
    }

    // =========================================================================
    // Delete
    // =========================================================================

    /**
     * Deletes a grading scale for the given course/exam via the service
     */
    delete(): void {
        if (!this.existingGradingScale()) {
            return;
        }
        this.isLoading.set(true);
        if (this.isExam()) {
            this.handleDeleteObservable(this.gradingService.deleteGradingScaleForExam(this.courseId!, this.examId!));
        } else {
            this.handleDeleteObservable(this.gradingService.deleteGradingScaleForCourse(this.courseId!));
        }
    }

    handleDeleteObservable(deleteObservable: Observable<HttpResponse<void>>) {
        deleteObservable.subscribe({
            next: () => {
                // Reset state only on successful delete
                this.existingGradingScale.set(false);
                const emptyGradingScale = new GradingScale();
                emptyGradingScale.course = this.course();
                this.gradingScale = emptyGradingScale;
                this.dialogErrorSource.next('');
                this.isLoading.set(false);
            },
            error: () => {
                // Keep the current state unchanged on error so the UI remains consistent with the server
                this.isLoading.set(false);
            },
        });
    }

    // =========================================================================
    // Validation
    // =========================================================================

    /**
     * Per-grade-step validator (registered via {@link applyEach}). Checks a single step's bounds/points fields.
     * The empty grade name is covered separately by the {@link required} rule on `gradeName`.
     */
    private gradeStepFieldErrors(gradeStep: GradeStep): { kind: string; message: string } | undefined {
        if (gradeStep.lowerBoundPercentage == undefined || gradeStep.upperBoundPercentage == undefined) {
            return { kind: 'emptyFields', message: this.translateService.instant('artemisApp.gradingSystem.error.emptyFields') };
        }
        if (this.maxPointsValid() && (gradeStep.lowerBoundPoints == undefined || gradeStep.upperBoundPoints == undefined)) {
            return { kind: 'emptyFields', message: this.translateService.instant('artemisApp.gradingSystem.error.emptyFields') };
        }
        if (gradeStep.lowerBoundPercentage < 0 || gradeStep.lowerBoundPercentage >= gradeStep.upperBoundPercentage) {
            return { kind: 'invalidMinMaxPercentages', message: this.translateService.instant('artemisApp.gradingSystem.error.invalidMinMaxPercentages') };
        }
        if (this.maxPointsValid() && (gradeStep.lowerBoundPoints! < 0 || gradeStep.lowerBoundPoints! >= gradeStep.upperBoundPoints!)) {
            return { kind: 'invalidMinMaxPoints', message: this.translateService.instant('artemisApp.gradingSystem.error.invalidMinMaxPoints') };
        }
        return undefined;
    }

    /**
     * Whole-grade-step-list validator (registered via {@link validateTree}). Checks cross-step structure: max points,
     * uniqueness, the first passing grade, bonus point ordering, adjacency, and full 0–100 coverage.
     * Reads {@link maxPoints} / {@link firstPassingGrade} (signals) so it re-runs reactively when they change.
     */
    private gradeStepStructureErrors(gradeSteps: GradeStep[], gradeType: GradeType): { kind: string; message: string } | undefined {
        if (gradeSteps.length === 0) {
            return { kind: 'empty', message: this.translateService.instant('artemisApp.gradingSystem.error.empty') };
        }
        const maxPoints = this.maxPoints();
        if (maxPoints != undefined && maxPoints < 0) {
            return { kind: 'negativeMaxPoints', message: this.translateService.instant('artemisApp.gradingSystem.error.negativeMaxPoints') };
        }
        if (!this.maxPointsValid()) {
            // ensures that all point updates have taken place before the grading key can be saved
            for (const gradeStep of gradeSteps) {
                if (gradeStep.lowerBoundPoints != undefined || gradeStep.upperBoundPoints != undefined) {
                    return { kind: 'pendingPoints', message: this.translateService.instant('artemisApp.gradingSystem.error.emptyFields') };
                }
            }
        }
        if (gradeType === GradeType.GRADE) {
            // check if all grade names are unique
            if (!gradeSteps.map((gradeStep) => gradeStep.gradeName).every((gradeName, index, gradeNames) => gradeNames.indexOf(gradeName) === index)) {
                return { kind: 'nonUniqueGradeNames', message: this.translateService.instant('artemisApp.gradingSystem.error.nonUniqueGradeNames') };
            }
            // check if the first passing grade is set
            const firstPassingGrade = this.firstPassingGrade();
            if (firstPassingGrade === undefined || firstPassingGrade === '') {
                return { kind: 'unsetFirstPassingGrade', message: this.translateService.instant('artemisApp.gradingSystem.error.unsetFirstPassingGrade') };
            }
        }
        // copy the grade steps in a separate array, so they don't get dynamically updated when sorting
        const sortedGradeSteps = this.gradingService.sortGradeSteps(gradeSteps.map((gradeStep) => Object.assign({}, gradeStep)));
        if (gradeType === GradeType.BONUS) {
            // check if when the grade type is BONUS, the bonus points are at least 0
            for (const gradeStep of sortedGradeSteps) {
                if (isNaN(Number(gradeStep.gradeName)) || Number(gradeStep.gradeName) < 0) {
                    return { kind: 'invalidBonusPoints', message: this.translateService.instant('artemisApp.gradingSystem.error.invalidBonusPoints') };
                }
            }
            // check if when the grade type is BONUS, the bonus points have strictly ascending values
            if (
                !sortedGradeSteps
                    .map((gradeStep) => Number(gradeStep.gradeName))
                    .every((bonusPoints, index, bonusPointsArray) => index === 0 || bonusPoints > bonusPointsArray[index - 1])
            ) {
                return { kind: 'nonStrictlyIncreasingBonusPoints', message: this.translateService.instant('artemisApp.gradingSystem.error.nonStrictlyIncreasingBonusPoints') };
            }
        }
        // check if grade steps have valid adjacency
        for (let i = 0; i < sortedGradeSteps.length - 1; i++) {
            if (sortedGradeSteps[i].upperBoundPercentage !== sortedGradeSteps[i + 1].lowerBoundPercentage) {
                return { kind: 'invalidAdjacency', message: this.translateService.instant('artemisApp.gradingSystem.error.invalidAdjacency') };
            }
        }
        // check if the first and last grade steps are valid
        if (sortedGradeSteps[0].lowerBoundPercentage !== 0 || sortedGradeSteps.last()!.upperBoundPercentage < 100) {
            return { kind: 'invalidFirstAndLastStep', message: this.translateService.instant('artemisApp.gradingSystem.error.invalidFirstAndLastStep') };
        }
        return undefined;
    }

    /**
     * Pure derivation of the presentations-config error message for the warning banner (replaces the
     * previous side-effecting writes inside validPresentationsConfig, which would throw NG0600 under
     * zoneless when invoked from template bindings). Re-evaluated on every change-detection pass.
     */
    presentationsConfigErrorMessage(): string | undefined {
        const presentationsConfig = this.presentationsConfig();
        if (presentationsConfig.presentationType === PresentationType.BASIC && (this.course()?.presentationScore ?? 0) <= 0) {
            return this.translateService.instant('artemisApp.gradingSystem.error.invalidPresentationsNumber');
        }
        if (presentationsConfig.presentationType === PresentationType.GRADED) {
            const presentationsNumber = presentationsConfig.presentationsNumber;
            if (presentationsNumber === undefined || !Number.isInteger(presentationsNumber) || presentationsNumber < 1) {
                return this.translateService.instant('artemisApp.gradingSystem.error.invalidPresentationsNumber');
            }
            const presentationsWeight = presentationsConfig.presentationsWeight;
            if (presentationsWeight === undefined || presentationsWeight < 0 || presentationsWeight > 99) {
                return this.translateService.instant('artemisApp.gradingSystem.error.invalidPresentationsWeight');
            }
            if ((this.course()?.presentationScore ?? 0) > 0) {
                return this.translateService.instant('artemisApp.gradingSystem.error.invalidBasicPresentationIsEnabled');
            }
        }
        return undefined;
    }

    /**
     * Checks if the currently entered presentation settings are valid
     */
    validPresentationsConfig(): boolean {
        const presentationsConfig = this.presentationsConfig();
        if (presentationsConfig.presentationType === PresentationType.NONE) {
            if (presentationsConfig.presentationsNumber !== undefined || presentationsConfig.presentationsWeight !== undefined) {
                return false;
            }
            if (presentationsConfig.presentationScore !== undefined) {
                return false;
            }
        }
        if (presentationsConfig.presentationType === PresentationType.BASIC) {
            if (presentationsConfig.presentationsNumber !== undefined || presentationsConfig.presentationsWeight !== undefined) {
                return false;
            }
            if ((this.course()?.presentationScore ?? 0) <= 0) {
                return false;
            }
        }
        if (presentationsConfig.presentationType === PresentationType.GRADED) {
            if (
                presentationsConfig.presentationsNumber === undefined ||
                !Number.isInteger(presentationsConfig.presentationsNumber) ||
                presentationsConfig.presentationsNumber < 1
            ) {
                return false;
            }
            if (presentationsConfig.presentationsWeight === undefined || presentationsConfig.presentationsWeight < 0 || presentationsConfig.presentationsWeight > 99) {
                return false;
            }
            if ((this.course()?.presentationScore ?? 0) > 0) {
                return false;
            }
        }
        return true;
    }

    maxPointsValid(): boolean {
        const maxPoints = this.maxPoints();
        return maxPoints != undefined && maxPoints > 0;
    }

    // =========================================================================
    // Points/Percentage Conversion
    // =========================================================================

    setPercentage(gradeStep: GradeStep, lowerBound: boolean) {
        const maxPoints = this.maxPoints()!;
        if (lowerBound) {
            gradeStep.lowerBoundPercentage = (gradeStep.lowerBoundPoints! / maxPoints) * 100;
        } else {
            gradeStep.upperBoundPercentage = (gradeStep.upperBoundPoints! / maxPoints) * 100;
        }
    }

    setPoints(gradeStep: GradeStep, lowerBound: boolean): void {
        const maxPoints = this.maxPoints();
        if (!maxPoints) {
            return;
        } else {
            if (lowerBound) {
                gradeStep.lowerBoundPoints = (maxPoints * gradeStep.lowerBoundPercentage) / 100;
            } else {
                gradeStep.upperBoundPoints = (maxPoints * gradeStep.upperBoundPercentage) / 100;
            }
        }
    }

    /** Detailed view: a percentage bound was edited; recompute the matching points and commit. */
    onDetailedPercentageChanged(index: number, lowerBound: boolean): void {
        this.updateGradeSteps((gradeSteps) => this.setPoints(gradeSteps[index], lowerBound));
    }

    /**
     * Detailed view: a points bound was edited. Points are optional (undefined when no max points are set), so they are
     * not bound via the signal-form `[formField]` directive (which requires a definite field) but written here directly.
     */
    onDetailedPointsInput(index: number, lowerBound: boolean, value: number): void {
        // valueAsNumber is NaN for an emptied input; coerce to undefined like the previous ngModel number accessor did,
        // so validation treats the field as missing instead of letting NaN slip through the bound checks.
        const points = Number.isNaN(value) ? undefined : value;
        this.updateGradeSteps((gradeSteps) => {
            const gradeStep = gradeSteps[index];
            if (lowerBound) {
                gradeStep.lowerBoundPoints = points;
            } else {
                gradeStep.upperBoundPoints = points;
            }
            this.setPercentage(gradeStep, lowerBound);
        });
    }

    onChangeMaxPoints(maxPoints?: number): void {
        this.updateGradeSteps((gradeSteps) => {
            if (maxPoints == undefined || maxPoints < 0) {
                for (const gradeStep of gradeSteps) {
                    gradeStep.lowerBoundPoints = undefined;
                    gradeStep.upperBoundPoints = undefined;
                }
            } else {
                for (const gradeStep of gradeSteps) {
                    this.setPoints(gradeStep, true);
                    this.setPoints(gradeStep, false);
                }
            }
        });
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
     * Sets the inclusivity for all grade steps based on the lowerBoundInclusivity property and commits the change to the model.
     */
    setInclusivity(): void {
        this.updateGradeSteps((gradeSteps) => this.applyInclusivity(gradeSteps));
    }

    /**
     * Mutates the inclusivity flags of the given grade steps in place. Implementation differs between interval and detailed modes.
     */
    private applyInclusivity(gradeSteps: GradeStep[]): void {
        if (this.viewMode() === GradingViewMode.DETAILED) {
            this.applyInclusivityDetailed(gradeSteps);
        } else {
            this.applyInclusivityInterval(gradeSteps);
        }
    }

    private applyInclusivityInterval(gradeSteps: GradeStep[]): void {
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

    private applyInclusivityDetailed(gradeSteps: GradeStep[]): void {
        const sortedGradeSteps = this.gradingService.sortGradeSteps(gradeSteps.slice());

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
        this.firstPassingGrade.set(
            this.gradingScale.gradeSteps.find((gradeStep) => {
                return gradeStep.isPassingGrade;
            })?.gradeName,
        );
    }

    setPassingGrades(gradeSteps: GradeStep[]): GradeStep[] {
        let passingGrade = false;
        const firstPassingGrade = this.firstPassingGrade();
        gradeSteps.forEach((gradeStep) => {
            if (gradeStep.gradeName === firstPassingGrade) {
                passingGrade = true;
            }
            gradeStep.isPassingGrade = passingGrade;
        });
        return gradeSteps;
    }

    deleteGradeNames(): void {
        this.updateGradeSteps((gradeSteps) => {
            gradeSteps.forEach((gradeStep) => {
                gradeStep.gradeName = '';
            });
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
        this.updateGradeSteps((gradeSteps) => {
            if (this.viewMode() === GradingViewMode.DETAILED) {
                this.createGradeStepInto(gradeSteps);
                return;
            }

            // Interval mode: handle sticky grade step
            // If no grade steps exist, create an initial step first (which will become the sticky step)
            if (gradeSteps.length === 0) {
                this.createGradeStepInto(gradeSteps);
            }

            // Pop the existing sticky grade step, add a new step, then re-append the sticky step.
            // Because the array is empty after popping, the new step gets lowerBound=0 and upperBound=100,
            // giving it a proper interval of 100. This ensures the percentage cascade works correctly.
            const stickyGradeStep = gradeSteps.pop()!;
            this.createGradeStepInto(gradeSteps);
            gradeSteps.push(stickyGradeStep);

            const selectedIndex = gradeSteps.length - 2;
            this.cascadePercentageInterval(gradeSteps, selectedIndex);
        });
    }

    private createGradeStepInto(gradeSteps: GradeStep[]): void {
        const gradeStepsArrayLength = gradeSteps.length;
        const lowerBound = gradeStepsArrayLength === 0 ? 0 : gradeSteps.last()!.upperBoundPercentage;
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
        gradeSteps.push(gradeStep);
    }

    /**
     * Deletes a grade step. In interval mode, handles percentage recalculation.
     */
    deleteGradeStep(index: number): void {
        this.updateGradeSteps((gradeSteps) => {
            if (this.viewMode() === GradingViewMode.DETAILED) {
                gradeSteps.splice(index, 1);
                return;
            }

            // Interval mode: handle percentage recalculation
            this.cascadePercentageInterval(gradeSteps, index, 0);
            gradeSteps.splice(index, 1);

            if (gradeSteps.length > 0) {
                if (gradeSteps.last()!.upperBoundPercentage < 100) {
                    gradeSteps.last()!.upperBoundPercentage = 100;
                }
                gradeSteps.first()!.lowerBoundInclusive = true;
                gradeSteps.last()!.upperBoundInclusive = true;
            }
        });
    }

    // =========================================================================
    // Interval-specific Methods
    // =========================================================================

    setPercentageInterval(selectedIndex: number, newPercentageInterval?: number): void {
        this.updateGradeSteps((gradeSteps) => this.cascadePercentageInterval(gradeSteps, selectedIndex, newPercentageInterval));
    }

    /**
     * Cascades a percentage-interval change from {@code selectedIndex} through all following grade steps, mutating the
     * given array in place. Kept separate from {@link setPercentageInterval} so it can be composed inside a single
     * {@link updateGradeSteps} call (e.g. from {@link createGradeStep} / {@link deleteGradeStep}) without nested commits.
     */
    private cascadePercentageInterval(gradeSteps: GradeStep[], selectedIndex: number, newPercentageInterval?: number): void {
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
        this.updateGradeSteps((gradeSteps) => {
            const gradeStep = gradeSteps[selectedIndex];
            if (gradeStep.lowerBoundPoints == undefined) {
                throw new Error(`lowerBoundPoints are not set yet for selectedIndex: '${selectedIndex}'`);
            }
            gradeStep.upperBoundPoints = gradeStep.lowerBoundPoints + newPointsInterval;
            this.setPercentage(gradeStep, false);
            this.cascadePercentageInterval(gradeSteps, selectedIndex);
        });
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
        this.firstPassingGrade.set(this.gradingScale.gradeSteps[3].gradeName);
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
            course: this.course(),
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
            this.maxPoints.set(100);
            this.onChangeMaxPoints(this.maxPoints());
            this.determineFirstPassingGrade();
            this.updateGradeSteps((gradeSteps) => gradeSteps.sort((a, b) => a.lowerBoundPercentage - b.lowerBoundPercentage));
        }
    }

    private async readGradingStepsFromCSVFile(csvFile: File) {
        let csvGradeSteps: CsvGradeStep[];
        try {
            csvGradeSteps = await this.parseCSVFile(csvFile);
        } catch (error) {
            return [];
        }

        if (csvGradeSteps.length === 0 || csvGradeSteps.length > 100) {
            this.gradeStepsModel.update((model) => ({ ...model, gradeSteps: [] }));
            return;
        }

        const gradeType = csvGradeSteps[0]['bonusPoints' as keyof CsvGradeStep] === undefined ? GradeType.GRADE : GradeType.BONUS;
        this.gradeStepsModel.update((model) => ({ ...model, gradeType, gradeSteps: this.mapCsvGradeStepsToGradeSteps(csvGradeSteps, gradeType) }));
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
