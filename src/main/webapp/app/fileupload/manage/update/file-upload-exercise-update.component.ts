import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { PresentationScoreComponent } from 'app/exercise/presentation-score/presentation-score.component';
import { GradingInstructionsDetailsComponent } from 'app/exercise/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { FileUploadExerciseService } from '../services/file-upload-exercise.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Exercise, ExerciseMode, IncludedInOverallScore, ValidationReason, getCourseId, resetForImport } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercise/exercise-update-warning/exercise-update-warning.service';
import { onError } from 'app/foundation/util/global.utils';
import { EditType, SaveExerciseCommand } from 'app/exercise/util/exercise.utils';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';

import { scrollToTopOfPage } from 'app/foundation/util/utils';
import { ExerciseGroupTimelineLockComponent } from 'app/course/manage/exercises/group-timeline-lock/exercise-group-timeline-lock.component';
import { ExerciseTitleChannelNameComponent } from 'app/exercise/exercise-title-channel-name/exercise-title-channel-name.component';
import { TeamConfigFormGroupComponent } from 'app/exercise/team-config-form-group/team-config-form-group.component';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { FormulaAction } from 'app/editor/monaco-editor/model/actions/formula.action';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { CategorySelectorPrimengComponent } from 'app/exercise/category-selector-primeng/category-selector-primeng.component';
import { MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DifficultyPickerComponent } from 'app/exercise/difficulty-picker/difficulty-picker.component';
import { FormSectionStatus, FormStatusBarComponent } from 'app/shared-ui/form/form-status-bar/form-status-bar.component';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { FormFooterComponent } from 'app/shared-ui/form/form-footer/form-footer.component';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { TimelineStatus } from 'app/shared-ui/timeline/timeline.component';
import { ExerciseTimelineComponent } from 'app/exercise/exercise-timeline/exercise-timeline.component';
import { ExerciseGroupDateNoticeComponent } from 'app/exercise/exercise-group-date-notice/exercise-group-date-notice.component';
import {
    ExerciseValidationViewState,
    getCommonExerciseInvalidReasons,
    getGeneralSectionInvalidReasons,
    getGradingSectionInvalidReasons,
    getModeSectionInvalidReasons,
} from 'app/exercise/util/exercise-validation.util';
import { ExerciseFormState } from 'app/exercise/util/exercise-form-state';
import { deepClone } from 'app/foundation/util/deep-clone.util';

const MIN_FILE_PATTERN_LENGTH = 2;

@Component({
    selector: 'jhi-file-upload-exercise-update',
    templateUrl: './file-upload-exercise-update.component.html',
    styleUrl: './file-upload-exercise-update.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        TranslateDirective,
        DocumentationButtonComponent,
        FormStatusBarComponent,
        ExerciseTitleChannelNameComponent,
        HelpIconComponent,
        CategorySelectorPrimengComponent,
        DifficultyPickerComponent,
        TeamConfigFormGroupComponent,
        MarkdownEditorMonacoComponent,
        CompetencySelectionComponent,
        ExerciseGroupTimelineLockComponent,
        IncludedInOverallScorePickerComponent,
        FaIconComponent,
        NgbTooltip,
        PresentationScoreComponent,
        GradingInstructionsDetailsComponent,
        FormFooterComponent,
        ArtemisTranslatePipe,
        ExerciseTimelineComponent,
        ExerciseGroupDateNoticeComponent,
    ],
})
export class FileUploadExerciseUpdateComponent implements OnInit {
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

    exerciseTitleChannelNameComponent = viewChild(ExerciseTitleChannelNameComponent);
    gradingInstructionsDetails = viewChild(GradingInstructionsDetailsComponent);

    /** Every write to the exercise goes through this, so that {@link invalidReasons} and {@link formStatusSections} follow it. */
    readonly exerciseState = new ExerciseFormState<FileUploadExercise>(new FileUploadExercise(undefined, undefined));
    readonly fileUploadExercise = this.exerciseState.exercise;
    backupExercise = signal<FileUploadExercise>(new FileUploadExercise(undefined, undefined));
    isSaving = signal(false);
    isExamMode = signal(false);
    isImport = signal(false);
    notificationText = signal<string | undefined>(undefined);
    exerciseCategories = signal<ExerciseCategory[]>([]);
    existingCategories = signal<ExerciseCategory[]>([]);
    timelineStatus = signal<TimelineStatus>({ valid: true, empty: false, invalidItems: [] });

    examCourseId = signal<number | undefined>(undefined);

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

    /** What the shared checks need to know beyond the exercise itself. */
    private readonly validationViewState = computed<ExerciseValidationViewState>(() => {
        const titleChannelName = this.exerciseTitleChannelNameComponent()?.titleChannelNameComponent();
        return {
            isExamMode: this.isExamMode(),
            minTitleLength: 3,
            isTitleDisallowed: !!titleChannelName?.isTitleDisallowed(),
            isChannelNameRequired: !!titleChannelName?.isChannelFieldDisplayed(),
            timelineStatus: this.timelineStatus(),
        };
    });

    private readonly filePatternInvalidReasons = computed<ValidationReason[]>(() => {
        const filePattern = this.fileUploadExercise().filePattern;
        if (!filePattern) {
            return [{ translateKey: 'artemisApp.fileUploadExercise.form.filePattern.undefined', translateValues: {} }];
        }
        if (filePattern.length < MIN_FILE_PATTERN_LENGTH) {
            return [{ translateKey: 'artemisApp.fileUploadExercise.form.filePattern.minlength', translateValues: { min: MIN_FILE_PATTERN_LENGTH } }];
        }
        return [];
    });

    /** Every reason the exercise cannot be saved; drives the footer's disabled state and its tooltip. */
    readonly invalidReasons = computed<ValidationReason[]>(() => [
        ...getCommonExerciseInvalidReasons(this.fileUploadExercise(), this.validationViewState()),
        ...this.filePatternInvalidReasons(),
    ]);

    readonly formStatusSections = computed<FormSectionStatus[]>(() => {
        const exercise = this.fileUploadExercise();
        const viewState = this.validationViewState();
        return [
            { title: 'artemisApp.exercise.sections.general', valid: getGeneralSectionInvalidReasons(exercise, viewState).length === 0 },
            { title: 'artemisApp.exercise.sections.mode', valid: getModeSectionInvalidReasons(exercise).length === 0 },
            { title: 'artemisApp.exercise.sections.problem', valid: true, empty: !exercise.problemStatement },
            { title: 'artemisApp.exercise.sections.solution', valid: true, empty: !exercise.exampleSolution },
            {
                // The example solution publication date lives in the timeline (as for programming exercises), and the file
                // pattern is entered next to the points, so both count for the grading section.
                title: 'artemisApp.exercise.sections.grading',
                valid: getGradingSectionInvalidReasons(exercise, viewState).length === 0 && this.filePatternInvalidReasons().length === 0,
                empty: !viewState.isExamMode && viewState.timelineStatus.empty,
            },
        ];
    });

    constructor() {
        // Effect to handle route data loading
        effect(() => {
            const data = this.routeData();
            if (data?.fileUploadExercise) {
                this.exerciseState.set(data.fileUploadExercise);
                this.backupExercise.set(deepClone(data.fileUploadExercise));
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
            // Runs again for a new exercise or a new mode. The exercise itself is read untracked below: both handlers write
            // to it, every write notifies, and tracking it would run this again on every edit.
            this.routeData();
            this.isImport();
            this.isExamMode();
            if (params) {
                untracked(() => {
                    this.handleExerciseSettings();
                    this.handleImport(params);
                });
            }
        });

        // Effect to load existing categories when courseId becomes available
        effect(() => {
            const courseId = this.examCourseId();
            const isExamMode = this.isExamMode();
            if (!isExamMode && courseId) {
                this.loadExistingCategories(courseId);
            }
        });
    }

    /**
     * Initializes information relevant to file upload exercise
     */
    ngOnInit() {
        scrollToTopOfPage();
        this.isSaving.set(false);
    }

    /**
     * Return to the exercise overview page
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.fileUploadExercise());
    }

    private handleImport(params: Params) {
        if (this.isImport()) {
            if (this.isExamMode()) {
                const exerciseGroupId = params['exerciseGroupId'];
                const courseId = params['courseId'];
                const examId = params['examId'];

                this.exerciseGroupService.find(courseId, examId, exerciseGroupId).subscribe((res) => this.exerciseState.patch('exerciseGroup', res.body ?? undefined));
                this.exerciseState.patch('course', undefined);
            } else {
                const targetCourseId = params['courseId'];
                this.courseService.find(targetCourseId).subscribe((res) => this.exerciseState.patch('course', res.body ?? undefined));
                this.exerciseState.patch('exerciseGroup', undefined);
            }
            this.exerciseState.update(resetForImport);
        }
    }

    private handleExerciseSettings() {
        if (!this.isExamMode()) {
            this.exerciseCategories.set(this.fileUploadExercise().categories || []);
        } else {
            this.exerciseState.update((exercise) => {
                // Lock individual mode for exam exercises
                exercise.mode = ExerciseMode.INDIVIDUAL;
                exercise.teamAssignmentConfig = undefined;
                exercise.teamMode = false;
                if (exercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
                    exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
                }
            });
        }
    }

    private loadExistingCategories(courseId: number) {
        this.courseService
            .findAllCategoriesOfCourse(courseId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (categoryRes: HttpResponse<string[]>) => {
                    this.existingCategories.set(this.exerciseService.convertExerciseCategoriesAsStringFromServer(categoryRes.body ?? []));
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
    }

    async save() {
        // Flush text-mode Monaco before isSaving disables the child (editable becomes false). A
        // rejected parse aborts the save: the model still holds the previous grading criteria.
        if (this.gradingInstructionsDetails()?.prepareForSave() === false) {
            return;
        }
        this.isSaving.set(true);

        const command = new SaveExerciseCommand(this.modalService, this.popupService, this.fileUploadExerciseService, this.backupExercise(), this.editType());

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
     * Updates categories for file upload exercise
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.exerciseState.patch('categories', categories);
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
