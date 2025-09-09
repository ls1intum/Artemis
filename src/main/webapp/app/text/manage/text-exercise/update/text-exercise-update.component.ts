import { AfterViewInit, ChangeDetectionStrategy, Component, OnDestroy, OnInit, ViewChild, effect, inject, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercise/feedback-suggestion/exercise-feedback-suggestion-options.component';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { PresentationScoreComponent } from 'app/exercise/presentation-score/presentation-score.component';
import { GradingInstructionsDetailsComponent } from 'app/exercise/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { TextExerciseService } from '../service/text-exercise.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ExerciseMode, IncludedInOverallScore, resetForImport } from 'app/exercise/shared/entities/exercise/exercise.model';
import { switchMap, tap } from 'rxjs/operators';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { FormsModule, NgForm, NgModel } from '@angular/forms';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { cloneDeep } from 'lodash-es';
import { ExerciseUpdateWarningService } from 'app/exercise/exercise-update-warning/exercise-update-warning.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { onError } from 'app/shared/util/global.utils';
import { EditType, SaveExerciseCommand } from 'app/exercise/util/exercise.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { Subscription } from 'rxjs';
import { scrollToTopOfPage } from 'app/shared/util/utils';
import { ExerciseTitleChannelNameComponent } from 'app/exercise/exercise-title-channel-name/exercise-title-channel-name.component';
import { TeamConfigFormGroupComponent } from 'app/exercise/team-config-form-group/team-config-form-group.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DifficultyPickerComponent } from 'app/exercise/difficulty-picker/difficulty-picker.component';
import { loadCourseExerciseCategories } from 'app/exercise/course-exercises/course-utils';
import { ExerciseUpdatePlagiarismComponent } from 'app/plagiarism/manage/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { FormSectionStatus, FormStatusBarComponent } from 'app/shared/form/form-status-bar/form-status-bar.component';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { FormFooterComponent } from 'app/shared/form/form-footer/form-footer.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_PLAGIARISM } from 'app/app.constants';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';

@Component({
    selector: 'jhi-text-exercise-update',
    templateUrl: './text-exercise-update.component.html',
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
        ExerciseFeedbackSuggestionOptionsComponent,
        ExerciseUpdatePlagiarismComponent,
        PresentationScoreComponent,
        GradingInstructionsDetailsComponent,
        FormFooterComponent,
        ArtemisTranslatePipe,
        FeatureOverlayComponent,
    ],
})
export class TextExerciseUpdateComponent implements OnInit, OnDestroy, AfterViewInit {
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

    @ViewChild('editForm') editForm: NgForm;
    @ViewChild('bonusPoints') bonusPoints: NgModel;
    @ViewChild('points') points: NgModel;
    @ViewChild('solutionPublicationDate') solutionPublicationDateField?: FormDateTimePickerComponent;
    @ViewChild('releaseDate') releaseDateField?: FormDateTimePickerComponent;
    @ViewChild('startDate') startDateField?: FormDateTimePickerComponent;
    @ViewChild('dueDate') dueDateField?: FormDateTimePickerComponent;
    @ViewChild('assessmentDueDate') assessmentDateField?: FormDateTimePickerComponent;
    exerciseUpdatePlagiarismComponent = viewChild(ExerciseUpdatePlagiarismComponent);
    exerciseTitleChannelNameComponent = viewChild.required(ExerciseTitleChannelNameComponent);
    @ViewChild(TeamConfigFormGroupComponent) teamConfigFormGroupComponent: TeamConfigFormGroupComponent;

    examCourseId?: number;
    isExamMode: boolean;
    isImport = false;
    AssessmentType = AssessmentType;
    isPlagiarismEnabled = false;

    textExercise: TextExercise;
    backupExercise: TextExercise;
    isSaving: boolean;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    notificationText?: string;

    domainActionsProblemStatement = [new FormulaAction()];
    domainActionsExampleSolution = [new FormulaAction()];

    formSectionStatus: FormSectionStatus[];

    pointsSubscription?: Subscription;
    bonusPointsSubscription?: Subscription;
    teamSubscription?: Subscription;

    constructor() {
        effect(() => {
            this.updateFormSectionsOnIsValidChange();
        });
    }

    get editType(): EditType {
        if (this.isImport) {
            return EditType.IMPORT;
        }

        return this.textExercise.id == undefined ? EditType.CREATE : EditType.UPDATE;
    }

    /**
     * Triggers {@link calculateFormSectionStatus} whenever a relevant signal changes
     */
    private updateFormSectionsOnIsValidChange() {
        this.exerciseTitleChannelNameComponent().titleChannelNameComponent().isValid(); // trigger the effect
        this.exerciseUpdatePlagiarismComponent()?.isFormValid();
        this.calculateFormSectionStatus();
    }

    ngAfterViewInit() {
        this.pointsSubscription = this.points?.valueChanges?.subscribe(() => this.calculateFormSectionStatus());
        this.bonusPointsSubscription = this.bonusPoints?.valueChanges?.subscribe(() => this.calculateFormSectionStatus());
        this.teamSubscription = this.teamConfigFormGroupComponent.formValidChanges.subscribe(() => this.calculateFormSectionStatus());
    }

    /**
     * Initializes all relevant data for creating or editing text exercise
     */
    ngOnInit() {
        scrollToTopOfPage();

        // Get the textExercise
        this.activatedRoute.data.subscribe(({ textExercise }) => {
            this.textExercise = textExercise;

            this.backupExercise = cloneDeep(this.textExercise);
            this.examCourseId = this.textExercise.course?.id || this.textExercise.exerciseGroup?.exam?.course?.id;
        });

        this.activatedRoute.url
            .pipe(
                tap(
                    (segments) =>
                        (this.isImport = segments.some((segment) => segment.path === 'import', (this.isExamMode = segments.some((segment) => segment.path === 'exercise-groups')))),
                ),
                switchMap(() => this.activatedRoute.params),
                tap((params) => {
                    if (!this.isExamMode) {
                        this.exerciseCategories = this.textExercise.categories || [];
                        if (this.examCourseId) {
                            this.loadCourseExerciseCategories(this.examCourseId);
                        }
                    } else {
                        // Lock individual mode for exam exercises
                        this.textExercise.mode = ExerciseMode.INDIVIDUAL;
                        this.textExercise.teamAssignmentConfig = undefined;
                        this.textExercise.teamMode = false;
                        // Exam exercises cannot be not included into the total score
                        if (this.textExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
                            this.textExercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
                        }
                    }
                    if (this.isImport) {
                        const courseId = params['courseId'];

                        if (this.isExamMode) {
                            // The target exerciseId where we want to import into
                            const exerciseGroupId = params['exerciseGroupId'];
                            const examId = params['examId'];

                            this.exerciseGroupService.find(courseId, examId, exerciseGroupId).subscribe((res) => (this.textExercise.exerciseGroup = res.body!));
                            // We reference exam exercises by their exercise group, not their course. Having both would lead to conflicts on the server
                            this.textExercise.course = undefined;
                        } else {
                            // The target course where we want to import into
                            this.courseService.find(courseId).subscribe((res) => (this.textExercise.course = res.body!));
                            // We reference normal exercises by their course, having both would lead to conflicts on the server
                            this.textExercise.exerciseGroup = undefined;
                        }

                        this.loadCourseExerciseCategories(courseId);
                        resetForImport(this.textExercise);
                    }
                }),
            )
            .subscribe();

        this.isPlagiarismEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_PLAGIARISM);

        this.isSaving = false;
        this.notificationText = undefined;
    }

    ngOnDestroy() {
        this.pointsSubscription?.unsubscribe();
        this.bonusPointsSubscription?.unsubscribe();
        this.teamSubscription?.unsubscribe();
    }

    calculateFormSectionStatus() {
        if (this.textExercise) {
            this.formSectionStatus = [
                {
                    title: 'artemisApp.exercise.sections.general',
                    valid: this.exerciseTitleChannelNameComponent().titleChannelNameComponent().isValid(),
                },
                { title: 'artemisApp.exercise.sections.mode', valid: this.teamConfigFormGroupComponent.formValid },
                { title: 'artemisApp.exercise.sections.problem', valid: true, empty: !this.textExercise.problemStatement },
                {
                    title: 'artemisApp.exercise.sections.solution',
                    valid: Boolean(this.isExamMode || (!this.textExercise.exampleSolutionPublicationDateError && this.solutionPublicationDateField?.dateInput.valid)),
                    empty: !this.textExercise.exampleSolution || (!this.isExamMode && !this.textExercise.exampleSolutionPublicationDate),
                },
                {
                    title: 'artemisApp.exercise.sections.grading',
                    valid: Boolean(
                        this.points.valid &&
                            this.bonusPoints.valid &&
                            (this.isExamMode ||
                                (this.exerciseUpdatePlagiarismComponent()?.isFormValid() &&
                                    !this.textExercise.startDateError &&
                                    !this.textExercise.dueDateError &&
                                    !this.textExercise.assessmentDueDateError &&
                                    this.releaseDateField?.dateInput.valid &&
                                    this.startDateField?.dateInput.valid &&
                                    this.dueDateField?.dateInput.valid &&
                                    this.assessmentDateField?.dateInput.valid)),
                    ),
                    empty:
                        !this.isExamMode &&
                        // if a dayjs object contains an empty date, it is considered "invalid"
                        (!this.textExercise.startDate?.isValid() ||
                            !this.textExercise.dueDate?.isValid() ||
                            !this.textExercise.assessmentDueDate?.isValid() ||
                            !this.textExercise.releaseDate?.isValid()),
                },
            ];
        }
    }

    /**
     * Return to the exercise overview page
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.textExercise);
    }

    /**
     * Validates if the date is correct
     */
    validateDate() {
        this.exerciseService.validateDate(this.textExercise);
        this.calculateFormSectionStatus();
    }

    /**
     * Updates the exercise categories
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.textExercise.categories = categories;
        this.exerciseCategories = categories;
    }

    save() {
        this.isSaving = true;

        new SaveExerciseCommand(this.modalService, this.popupService, this.textExerciseService, this.backupExercise, this.editType, this.alertService)
            .save(this.textExercise, this.isExamMode, this.notificationText)
            .subscribe({
                next: (exercise: TextExercise) => this.onSaveSuccess(exercise),
                error: (error: HttpErrorResponse) => this.onSaveError(error),
                complete: () => {
                    this.isSaving = false;
                },
            });
    }

    private loadCourseExerciseCategories(courseId: number) {
        loadCourseExerciseCategories(courseId, this.courseService, this.exerciseService, this.alertService).subscribe((existingCategories) => {
            this.existingCategories = existingCategories;
        });
    }

    private onSaveSuccess(exercise: TextExercise) {
        this.eventManager.broadcast({ name: 'textExerciseListModification', content: 'OK' });
        this.isSaving = false;

        this.navigationUtilService.navigateForwardFromExerciseUpdateOrCreation(exercise);
        this.calendarService.reloadEvents();
    }

    private onSaveError(errorRes: HttpErrorResponse) {
        if (errorRes.error && errorRes.error.title) {
            this.alertService.addErrorAlert(errorRes.error.title, errorRes.error.message, errorRes.error.params);
        } else {
            onError(this.alertService, errorRes);
        }
        this.isSaving = false;
    }
}
