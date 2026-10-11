import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { PresentationScoreComponent } from 'app/exercise/presentation-score/presentation-score.component';
import { GradingInstructionsDetailsComponent } from 'app/exercise/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { TextExerciseService } from '../service/text-exercise.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ExerciseMode, IncludedInOverallScore, ValidationReason, resetForImport } from 'app/exercise/shared/entities/exercise/exercise.model';
import { switchMap, tap } from 'rxjs/operators';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { FormsModule, NgForm } from '@angular/forms';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { ExerciseUpdateWarningService } from 'app/exercise/exercise-update-warning/exercise-update-warning.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { onError } from 'app/foundation/util/global.utils';
import { EditType, SaveExerciseCommand } from 'app/exercise/util/exercise.utils';
import { AlertService } from 'app/foundation/service/alert.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { scrollToTopOfPage } from 'app/foundation/util/utils';
import { ExerciseTitleChannelNameComponent } from 'app/exercise/exercise-title-channel-name/exercise-title-channel-name.component';
import { TeamConfigFormGroupComponent } from 'app/exercise/team-config-form-group/team-config-form-group.component';
import { ExerciseGroupTimelineLockComponent } from 'app/course/manage/exercises/group-timeline-lock/exercise-group-timeline-lock.component';
import { FormulaAction } from 'app/editor/monaco-editor/model/actions/formula.action';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { CategorySelectorPrimengComponent } from 'app/exercise/category-selector-primeng/category-selector-primeng.component';
import { MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DifficultyPickerComponent } from 'app/exercise/difficulty-picker/difficulty-picker.component';
import { loadCourseExerciseCategories } from 'app/exercise/course-exercises/course-utils';
import { ExerciseUpdatePlagiarismComponent } from 'app/plagiarism/manage/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { FormSectionStatus, FormStatusBarComponent } from 'app/shared-ui/form/form-status-bar/form-status-bar.component';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { FormFooterComponent } from 'app/shared-ui/form/form-footer/form-footer.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_PLAGIARISM } from 'app/app.constants';
import { FeatureOverlayComponent } from 'app/shared-ui/components/feature-overlay/feature-overlay.component';
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
    getPlagiarismInvalidReasons,
} from 'app/exercise/util/exercise-validation.util';
import { ExerciseFormState } from 'app/exercise/util/exercise-form-state';
import { deepClone } from 'app/foundation/util/deep-clone.util';

@Component({
    selector: 'jhi-text-exercise-update',
    templateUrl: './text-exercise-update.component.html',
    styleUrl: './text-exercise-update.component.scss',
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
        ExerciseUpdatePlagiarismComponent,
        PresentationScoreComponent,
        GradingInstructionsDetailsComponent,
        FormFooterComponent,
        ArtemisTranslatePipe,
        FeatureOverlayComponent,
        ExerciseTimelineComponent,
        ExerciseGroupDateNoticeComponent,
    ],
})
export class TextExerciseUpdateComponent implements OnInit {
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly alertService = inject(AlertService);
    private readonly textExerciseService = inject(TextExerciseService);
    private readonly modalService = inject(NgbModal);
    private readonly popupService = inject(ExerciseUpdateWarningService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly exerciseGroupService = inject(ExerciseGroupService);
    private readonly courseService = inject(CourseManagementService);
    private readonly eventManager = inject(EventManager);
    private readonly navigationUtilService = inject(ArtemisNavigationUtilService);
    private readonly profileService = inject(ProfileService);
    private readonly calendarService = inject(CalendarService);

    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly documentationType: DocumentationType = 'Text';

    editForm = viewChild<NgForm>('editForm');
    exerciseUpdatePlagiarismComponent = viewChild(ExerciseUpdatePlagiarismComponent);
    exerciseTitleChannelNameComponent = viewChild(ExerciseTitleChannelNameComponent);
    gradingInstructionsDetails = viewChild(GradingInstructionsDetailsComponent);

    examCourseId?: number;
    readonly isExamMode = signal<boolean>(undefined!);
    readonly isImport = signal(false);
    AssessmentType = AssessmentType;
    readonly isPlagiarismEnabled = signal(false);

    /** Every write to the exercise goes through this, so that {@link invalidReasons} and {@link formSectionStatus} follow it. */
    readonly exerciseState = new ExerciseFormState<TextExercise>();
    get textExercise(): TextExercise {
        return this.exerciseState.exercise();
    }
    set textExercise(value: TextExercise) {
        this.exerciseState.set(value);
    }
    backupExercise!: TextExercise; // set in ngOnInit() from the route-resolved exercise before save() reads it
    readonly isSaving = signal(false);
    readonly timelineStatus = signal<TimelineStatus>({ valid: true, empty: false, invalidItems: [] });
    readonly exerciseCategories = signal<ExerciseCategory[]>([]);
    readonly existingCategories = signal<ExerciseCategory[]>([]);
    notificationText?: string;

    domainActionsProblemStatement = [new FormulaAction()];
    domainActionsExampleSolution = [new FormulaAction()];

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

    /** Every reason the exercise cannot be saved; drives the footer's disabled state and its tooltip. */
    readonly invalidReasons = computed<ValidationReason[]>(() => {
        const exercise = this.exerciseState.exercise();
        if (!exercise) {
            return [];
        }
        return [...getCommonExerciseInvalidReasons(exercise, this.validationViewState()), ...getPlagiarismInvalidReasons(this.exerciseUpdatePlagiarismComponent())];
    });

    readonly formSectionStatus = computed<FormSectionStatus[]>(() => {
        const exercise = this.exerciseState.exercise();
        if (!exercise) {
            return [];
        }
        const viewState = this.validationViewState();
        const isExamMode = viewState.isExamMode;
        return [
            { title: 'artemisApp.exercise.sections.general', valid: getGeneralSectionInvalidReasons(exercise, viewState).length === 0 },
            { title: 'artemisApp.exercise.sections.mode', valid: getModeSectionInvalidReasons(exercise).length === 0 },
            { title: 'artemisApp.exercise.sections.problem', valid: true, empty: !exercise.problemStatement },
            { title: 'artemisApp.exercise.sections.solution', valid: true, empty: !exercise.exampleSolution },
            {
                // The example solution publication date lives in the timeline (as for programming exercises), so
                // its validity is part of the grading section.
                title: 'artemisApp.exercise.sections.grading',
                valid: getGradingSectionInvalidReasons(exercise, viewState).length === 0 && (isExamMode || !!this.exerciseUpdatePlagiarismComponent()?.isFormValid()),
                empty: !isExamMode && viewState.timelineStatus.empty,
            },
        ];
    });

    get editType(): EditType {
        if (this.isImport()) {
            return EditType.IMPORT;
        }

        return this.textExercise.id == undefined ? EditType.CREATE : EditType.UPDATE;
    }

    /**
     * Initializes all relevant data for creating or editing text exercise
     */
    ngOnInit() {
        scrollToTopOfPage();

        // Get the textExercise
        this.activatedRoute.data.subscribe(({ textExercise }) => {
            this.textExercise = textExercise;

            this.backupExercise = deepClone(this.textExercise);
            this.examCourseId = this.textExercise.course?.id || this.textExercise.exerciseGroup?.exam?.course?.id;
        });

        this.activatedRoute.url
            .pipe(
                tap((segments) => {
                    this.isExamMode.set(segments.some((segment) => segment.path === 'exercise-groups'));
                    this.isImport.set(segments.some((segment) => segment.path === 'import'));
                }),
                switchMap(() => this.activatedRoute.params),
                tap((params) => {
                    if (!this.isExamMode()) {
                        this.exerciseCategories.set(this.textExercise.categories || []);
                        if (this.examCourseId) {
                            this.loadCourseExerciseCategories(this.examCourseId);
                        }
                    } else {
                        this.exerciseState.update((exercise) => {
                            // Lock individual mode for exam exercises
                            exercise.mode = ExerciseMode.INDIVIDUAL;
                            exercise.teamAssignmentConfig = undefined;
                            exercise.teamMode = false;
                            // Exam exercises cannot be not included into the total score
                            if (exercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
                                exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
                            }
                        });
                    }
                    if (this.isImport()) {
                        const courseId = params['courseId'];

                        if (this.isExamMode()) {
                            // The target exerciseId where we want to import into
                            const exerciseGroupId = params['exerciseGroupId'];
                            const examId = params['examId'];

                            this.exerciseGroupService.find(courseId, examId, exerciseGroupId).subscribe((res) => this.exerciseState.patch('exerciseGroup', res.body!));
                            // We reference exam exercises by their exercise group, not their course. Having both would lead to conflicts on the server
                            this.exerciseState.patch('course', undefined);
                        } else {
                            // The target course where we want to import into
                            this.courseService.find(courseId).subscribe((res) => this.exerciseState.patch('course', res.body!));
                            // We reference normal exercises by their course, having both would lead to conflicts on the server
                            this.exerciseState.patch('exerciseGroup', undefined);
                        }

                        this.loadCourseExerciseCategories(courseId);
                        this.exerciseState.update(resetForImport);
                    }
                }),
            )
            .subscribe();

        this.isPlagiarismEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_PLAGIARISM));

        this.isSaving.set(false);
        this.notificationText = undefined;
    }

    /**
     * Return to the exercise overview page
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.textExercise);
    }

    /**
     * Updates the exercise categories
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.exerciseState.patch('categories', categories);
        this.exerciseCategories.set(categories);
    }

    save() {
        // Flush text-mode Monaco before isSaving disables the child (editable becomes false). A
        // rejected parse aborts the save: the model still holds the previous grading criteria.
        if (this.gradingInstructionsDetails()?.prepareForSave() === false) {
            return;
        }
        this.isSaving.set(true);

        new SaveExerciseCommand(this.modalService, this.popupService, this.textExerciseService, this.backupExercise, this.editType)
            .save(this.textExercise, this.isExamMode(), this.notificationText)
            .subscribe({
                next: (exercise: TextExercise) => this.onSaveSuccess(exercise),
                error: (error: HttpErrorResponse) => this.onSaveError(error),
                complete: () => {
                    this.isSaving.set(false);
                },
            });
    }

    private loadCourseExerciseCategories(courseId: number) {
        loadCourseExerciseCategories(courseId, this.courseService, this.exerciseService, this.alertService).subscribe((existingCategories) => {
            this.existingCategories.set(existingCategories);
        });
    }

    private onSaveSuccess(exercise: TextExercise) {
        this.eventManager.broadcast({ name: 'textExerciseListModification', content: 'OK' });
        this.isSaving.set(false);

        this.navigationUtilService.navigateForwardFromExerciseUpdateOrCreation(exercise);
        this.calendarService.reloadEvents();
    }

    private onSaveError(errorRes: HttpErrorResponse) {
        if (errorRes.error && errorRes.error.title) {
            this.alertService.addErrorAlert(errorRes.error.title, errorRes.error.message, errorRes.error.params);
        } else {
            onError(this.alertService, errorRes);
        }
        this.isSaving.set(false);
    }
}
