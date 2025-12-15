import { AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { PresentationScoreComponent } from 'app/exercise/presentation-score/presentation-score.component';
import { GradingInstructionsDetailsComponent } from 'app/exercise/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { FileUploadExerciseService } from '../services/file-upload-exercise.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Exercise, ExerciseMode, IncludedInOverallScore, getCourseId, resetForImport } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { cloneDeep } from 'lodash-es';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercise/exercise-update-warning/exercise-update-warning.service';
import { onError } from 'app/shared/util/global.utils';
import { EditType, SaveExerciseCommand } from 'app/exercise/util/exercise.utils';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';

import { scrollToTopOfPage } from 'app/shared/util/utils';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ExerciseTitleChannelNameComponent } from 'app/exercise/exercise-title-channel-name/exercise-title-channel-name.component';
import { TeamConfigFormGroupComponent } from 'app/exercise/team-config-form-group/team-config-form-group.component';
import { FormsModule, NgModel } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DifficultyPickerComponent } from 'app/exercise/difficulty-picker/difficulty-picker.component';
import { FormSectionStatus, FormStatusBarComponent } from 'app/shared/form/form-status-bar/form-status-bar.component';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { FormFooterComponent } from 'app/shared/form/form-footer/form-footer.component';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';

@Component({
    selector: 'jhi-file-upload-exercise-update',
    templateUrl: './file-upload-exercise-update.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        TranslateDirective,
        DocumentationButtonComponent,
        FormStatusBarComponent,
        ExerciseTitleChannelNameComponent,
        HelpIconComponent,
        CategorySelectorComponent,
        DifficultyPickerComponent,
        TeamConfigFormGroupComponent,
        MarkdownEditorMonacoComponent,
        CompetencySelectionComponent,
        FormDateTimePickerComponent,
        IncludedInOverallScorePickerComponent,
        FaIconComponent,
        NgbTooltip,
        PresentationScoreComponent,
        GradingInstructionsDetailsComponent,
        FormFooterComponent,
        ArtemisTranslatePipe,
    ],
})
export class FileUploadExerciseUpdateComponent implements AfterViewInit, OnInit {
    private readonly fileUploadExerciseService = inject(FileUploadExerciseService);
    private readonly modalService = inject(NgbModal);
    private readonly popupService = inject(ExerciseUpdateWarningService);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly courseService = inject(CourseManagementService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly alertService = inject(AlertService);
    private readonly navigationUtilService = inject(ArtemisNavigationUtilService);
    private readonly exerciseGroupService = inject(ExerciseGroupService);
    private readonly calendarService = inject(CalendarService);
    private readonly destroyRef = inject(DestroyRef);

    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly documentationType: DocumentationType = 'FileUpload';

    bonusPoints = viewChild<NgModel>('bonusPoints');
    points = viewChild<NgModel>('points');
    solutionPublicationDateField = viewChild<FormDateTimePickerComponent>('solutionPublicationDate');
    releaseDateField = viewChild<FormDateTimePickerComponent>('releaseDate');
    startDateField = viewChild<FormDateTimePickerComponent>('startDate');
    dueDateField = viewChild<FormDateTimePickerComponent>('dueDate');
    assessmentDateField = viewChild<FormDateTimePickerComponent>('assessmentDueDate');
    exerciseTitleChannelNameComponent = viewChild(ExerciseTitleChannelNameComponent);
    teamConfigFormGroupComponent = viewChild(TeamConfigFormGroupComponent);

    // Signals for state
    fileUploadExercise = signal<FileUploadExercise>(new FileUploadExercise(undefined, undefined));
    backupExercise = signal<FileUploadExercise>(new FileUploadExercise(undefined, undefined));
    isSaving = signal(false);
    isExamMode = signal(false);
    isImport = signal(false);
    notificationText = signal<string | undefined>(undefined);
    exerciseCategories = signal<ExerciseCategory[]>([]);
    existingCategories = signal<ExerciseCategory[]>([]);

    examCourseId = signal<number | undefined>(undefined);
    formStatusSections = signal<FormSectionStatus[]>([]);

    domainActionsProblemStatement = [new FormulaAction()];
    domainActionsExampleSolution = [new FormulaAction()];

    editType = computed(() => {
        if (this.isImport()) {
            return EditType.IMPORT;
        }
        return this.fileUploadExercise().id == undefined ? EditType.CREATE : EditType.UPDATE;
    });

    // Route signals
    private routeData = toSignal(this.activatedRoute.data);
    private routeUrl = toSignal(this.activatedRoute.url);
    private routeParams = toSignal(this.activatedRoute.params);

    constructor() {
        effect(() => {
            this.updateFormSectionsOnIsValidChange();
        });

        // Effect to handle route data loading
        effect(() => {
            const data = this.routeData();
            if (data?.fileUploadExercise) {
                this.fileUploadExercise.set(data.fileUploadExercise);
                this.backupExercise.set(cloneDeep(data.fileUploadExercise));
                this.examCourseId.set(getCourseId(data.fileUploadExercise));
            }
        });

        // Effect to handle URL segments
        effect(() => {
            const segments = this.routeUrl();
            if (segments) {
                this.isImport.set(segments.some((segment) => segment.path === 'import'));
                this.isExamMode.set(segments.some((segment) => segment.path === 'exercise-groups'));
            }
        });

        // Effect to handle params (import/config)
        effect(() => {
            const params = this.routeParams();
            if (params) {
                // We depend on isImport/isExamMode being set first.
                // Since signals update effectively, we can check them.
                this.handleExerciseSettings();
                this.handleImport(params);
            }
        });
    }

    /**
     * Triggers {@link calculateFormSectionStatus} whenever a relevant signal changes
     */
    private updateFormSectionsOnIsValidChange() {
        // Guard against viewChild not being available yet (before view init)
        const titleComponent = this.exerciseTitleChannelNameComponent?.();
        if (titleComponent?.titleChannelNameComponent) {
            titleComponent.titleChannelNameComponent().isValid(); // trigger the effect
        }
        this.calculateFormSectionStatus();
    }

    /**
     * Initializes information relevant to file upload exercise
     */
    ngOnInit() {
        scrollToTopOfPage();
        this.isSaving.set(false);
    }

    ngAfterViewInit() {
        this.points()
            ?.valueChanges?.pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.calculateFormSectionStatus());
        this.bonusPoints()
            ?.valueChanges?.pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.calculateFormSectionStatus());
        this.teamConfigFormGroupComponent()
            ?.formValidChanges?.pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.calculateFormSectionStatus());
    }

    calculateFormSectionStatus() {
        const exercise = this.fileUploadExercise();
        const titleComponent = this.exerciseTitleChannelNameComponent?.();
        const teamConfig = this.teamConfigFormGroupComponent?.();
        this.formStatusSections.set([
            {
                title: 'artemisApp.exercise.sections.general',
                valid: titleComponent?.titleChannelNameComponent()?.isValid() ?? true,
            },
            { title: 'artemisApp.exercise.sections.mode', valid: teamConfig?.formValid ?? true },
            { title: 'artemisApp.exercise.sections.problem', valid: true, empty: !exercise.problemStatement },
            {
                title: 'artemisApp.exercise.sections.solution',
                valid: Boolean(this.isExamMode() || (!exercise.exampleSolutionPublicationDateError && (this.solutionPublicationDateField()?.dateInput?.valid ?? true))),
                empty: !exercise.exampleSolution || (!this.isExamMode() && !exercise.exampleSolutionPublicationDate),
            },
            {
                title: 'artemisApp.exercise.sections.grading',
                valid: Boolean(
                    (this.points()?.valid ?? true) &&
                    (this.bonusPoints()?.valid ?? true) &&
                    (this.isExamMode() ||
                        (!exercise.startDateError &&
                            !exercise.dueDateError &&
                            !exercise.assessmentDueDateError &&
                            (this.releaseDateField()?.dateInput?.valid ?? true) &&
                            (this.startDateField()?.dateInput?.valid ?? true) &&
                            (this.dueDateField()?.dateInput?.valid ?? true) &&
                            (this.assessmentDateField()?.dateInput?.valid ?? true))),
                ),
                empty:
                    !this.isExamMode() &&
                    // if a dayjs object contains an empty date, it is considered "invalid"
                    (!exercise.startDate?.isValid() || !exercise.dueDate?.isValid() || !exercise.assessmentDueDate?.isValid() || !exercise.releaseDate?.isValid()),
            },
        ]);
    }

    /**
     * Return to the exercise overview page
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.fileUploadExercise());
    }

    private handleImport(params: Params) {
        if (this.isImport()) {
            const exercise = this.fileUploadExercise();
            // Mutating the object in the signal - careful, but effective for this logic
            if (this.isExamMode()) {
                const exerciseGroupId = params['exerciseGroupId'];
                const courseId = params['courseId'];
                const examId = params['examId'];

                this.exerciseGroupService.find(courseId, examId, exerciseGroupId).subscribe((res) => (exercise.exerciseGroup = res.body ?? undefined));
                exercise.course = undefined;
            } else {
                const targetCourseId = params['courseId'];
                this.courseService.find(targetCourseId).subscribe((res) => (exercise.course = res.body ?? undefined));
                exercise.exerciseGroup = undefined;
            }
            resetForImport(exercise);
        }
    }

    private handleExerciseSettings() {
        const exercise = this.fileUploadExercise();
        if (!this.isExamMode()) {
            this.exerciseCategories.set(exercise.categories || []);
            const courseId = this.examCourseId();
            if (courseId) {
                this.courseService.findAllCategoriesOfCourse(courseId).subscribe({
                    next: (categoryRes: HttpResponse<string[]>) => {
                        this.existingCategories.set(this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body ?? []));
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
            }
        } else {
            // Lock individual mode for exam exercises
            exercise.mode = ExerciseMode.INDIVIDUAL;
            exercise.teamAssignmentConfig = undefined;
            exercise.teamMode = false;
            if (exercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
                exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
            }
        }
    }

    async save() {
        this.isSaving.set(true);

        const command = new SaveExerciseCommand(this.modalService, this.popupService, this.fileUploadExerciseService, this.backupExercise(), this.editType(), this.alertService);

        try {
            // save() returns Observable. Convert to Promise.
            const exercise = await firstValueFrom(command.save(this.fileUploadExercise(), this.isExamMode(), this.notificationText()));
            this.onSaveSuccess(exercise);
        } catch (error: unknown) {
            this.onSaveError(error as HttpErrorResponse);
        } finally {
            // complete logic handled? no, finally handles cleanup
            // this.isSaving.set(false) done in success/error
        }
    }

    /**
     * Validates if the date is correct
     */
    validateDate() {
        this.exerciseService.validateDate(this.fileUploadExercise());
        this.calculateFormSectionStatus();
    }

    /**
     * Updates categories for file upload exercise
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.fileUploadExercise().categories = categories;
        this.exerciseCategories.set(categories);
    }

    private onSaveSuccess(exercise: Exercise) {
        this.isSaving.set(false);
        this.calendarService.reloadEvents();
        this.navigationUtilService.navigateForwardFromExerciseUpdateOrCreation(exercise);
    }

    private onSaveError(error: HttpErrorResponse) {
        if (error.error && error.error.title) {
            this.alertService.addErrorAlert(error.error.title, error.error.message, error.error.params);
        }
        const errorMessage = error.headers?.get('X-artemisApp-alert') ?? 'error.unexpectedError';
        this.alertService.addAlert({
            type: AlertType.DANGER,
            message: errorMessage,
            disableTranslation: true,
        });
        this.isSaving.set(false);
    }
}
