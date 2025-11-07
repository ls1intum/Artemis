import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewChild, effect, inject, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercise/feedback-suggestion/exercise-feedback-suggestion-options.component';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { PresentationScoreComponent } from 'app/exercise/presentation-score/presentation-score.component';
import { GradingInstructionsDetailsComponent } from 'app/exercise/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { ModelingExerciseService } from '../services/modeling-exercise.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseMode, IncludedInOverallScore, resetForImport } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { switchMap, tap } from 'rxjs/operators';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { cloneDeep, isEmpty } from 'lodash-es';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseUpdateWarningService } from 'app/exercise/exercise-update-warning/exercise-update-warning.service';
import { onError } from 'app/shared/util/global.utils';
import { EditType, SaveExerciseCommand } from 'app/exercise/util/exercise.utils';
import { UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { AlertService } from 'app/shared/service/alert.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { scrollToTopOfPage } from 'app/shared/util/utils';
import { Subscription } from 'rxjs';
import { ExerciseTitleChannelNameComponent } from 'app/exercise/exercise-title-channel-name/exercise-title-channel-name.component';
import { FormsModule, NgModel } from '@angular/forms';
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
import { FormSectionStatus, FormStatusBarComponent } from 'app/shared/form/form-status-bar/form-status-bar.component';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { FormFooterComponent } from 'app/shared/form/form-footer/form-footer.component';
import { CalendarService } from 'app/core/calendar/shared/service/calendar.service';

@Component({
    selector: 'jhi-modeling-exercise-update',
    templateUrl: './modeling-exercise-update.component.html',
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
        ModelingEditorComponent,
        FormDateTimePickerComponent,
        IncludedInOverallScorePickerComponent,
        ExerciseFeedbackSuggestionOptionsComponent,
        PresentationScoreComponent,
        GradingInstructionsDetailsComponent,
        FormFooterComponent,
        ArtemisTranslatePipe,
    ],
})
export class ModelingExerciseUpdateComponent implements AfterViewInit, OnDestroy, OnInit {
    private readonly alertService = inject(AlertService);
    private readonly modelingExerciseService = inject(ModelingExerciseService);
    private readonly modalService = inject(NgbModal);
    private readonly popupService = inject(ExerciseUpdateWarningService);
    private readonly courseService = inject(CourseManagementService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly exerciseGroupService = inject(ExerciseGroupService);
    private readonly eventManager = inject(EventManager);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly navigationUtilService = inject(ArtemisNavigationUtilService);
    private readonly changeDetectorRef = inject(ChangeDetectorRef);
    private readonly calendarService = inject(CalendarService);

    exerciseTitleChannelNameComponent = viewChild.required(ExerciseTitleChannelNameComponent);
    @ViewChild(TeamConfigFormGroupComponent) teamConfigFormGroupComponent?: TeamConfigFormGroupComponent;
    @ViewChild(ModelingEditorComponent, { static: false }) modelingEditor?: ModelingEditorComponent;

    @ViewChild('bonusPoints') bonusPoints?: NgModel;
    @ViewChild('points') points?: NgModel;
    @ViewChild('solutionPublicationDate') solutionPublicationDateField?: FormDateTimePickerComponent;
    @ViewChild('releaseDate') releaseDateField?: FormDateTimePickerComponent;
    @ViewChild('startDate') startDateField?: FormDateTimePickerComponent;
    @ViewChild('dueDate') dueDateField?: FormDateTimePickerComponent;
    @ViewChild('assessmentDueDate') assessmentDateField?: FormDateTimePickerComponent;
    @ViewChild('editForm', { read: ElementRef }) editFormEl?: ElementRef<HTMLFormElement>;

    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly documentationType: DocumentationType = 'Model';

    AssessmentType = AssessmentType;
    UMLDiagramType = UMLDiagramType;

    modelingExercise: ModelingExercise;
    backupExercise: ModelingExercise;
    exampleSolution: UMLModel;
    isSaving: boolean;
    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];
    notificationText?: string;
    domainActionsProblemStatement = [new FormulaAction()];
    domainActionsExampleSolution = [new FormulaAction()];
    examCourseId?: number;
    isImport: boolean;
    isExamMode: boolean;
    semiAutomaticAssessmentAvailable = true;

    formSectionStatus: FormSectionStatus[];

    pointsSubscription?: Subscription;
    bonusPointsSubscription?: Subscription;
    teamSubscription?: Subscription;

    get editType(): EditType {
        if (this.isImport) {
            return EditType.IMPORT;
        }

        return this.modelingExercise.id == undefined ? EditType.CREATE : EditType.UPDATE;
    }

    ngAfterViewInit() {
        this.pointsSubscription = this.points?.valueChanges?.subscribe(() => this.calculateFormSectionStatus());
        this.bonusPointsSubscription = this.bonusPoints?.valueChanges?.subscribe(() => this.calculateFormSectionStatus());
        this.teamSubscription = this.teamConfigFormGroupComponent?.formValidChanges.subscribe(() => this.calculateFormSectionStatus());
    }

    constructor() {
        effect(() => {
            this.updateFormSectionsOnIsValidChange();
        });
    }

    /**
     * Triggers {@link calculateFormSectionStatus} whenever a relevant signal changes
     */
    private updateFormSectionsOnIsValidChange() {
        this.exerciseTitleChannelNameComponent().titleChannelNameComponent().isValid(); // triggers effect on change

        this.calculateFormSectionStatus().then();
    }

    /**
     * Initializes all relevant data for creating or editing modeling exercise
     */
    ngOnInit(): void {
        scrollToTopOfPage();

        // Get the modelingExercise
        this.activatedRoute.data.subscribe(({ modelingExercise }) => {
            this.modelingExercise = modelingExercise;

            if (this.modelingExercise.exampleSolutionModel != undefined) {
                this.exampleSolution = JSON.parse(this.modelingExercise.exampleSolutionModel);
            }

            this.backupExercise = cloneDeep(this.modelingExercise);
            this.examCourseId = this.modelingExercise.course?.id || this.modelingExercise.exerciseGroup?.exam?.course?.id;
        });

        this.activatedRoute.url
            .pipe(
                tap(
                    (segments) =>
                        (this.isImport = segments.some((segment) => segment.path === 'import', (this.isExamMode = segments.some((segment) => segment.path === 'exercise-groups')))),
                ),
                switchMap(() => this.activatedRoute.params),
                tap((params) => {
                    let courseId;

                    if (!this.isExamMode) {
                        this.exerciseCategories = this.modelingExercise.categories || [];
                        if (this.modelingExercise.course) {
                            courseId = this.modelingExercise.course!.id!;
                        } else {
                            courseId = this.modelingExercise.exerciseGroup!.exam!.course!.id!;
                        }
                    } else {
                        // Lock individual mode for exam exercises
                        this.modelingExercise.mode = ExerciseMode.INDIVIDUAL;
                        this.modelingExercise.teamAssignmentConfig = undefined;
                        this.modelingExercise.teamMode = false;
                        // Exam exercises cannot be not included in the total score
                        if (this.modelingExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
                            this.modelingExercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
                        }
                    }
                    if (this.isImport) {
                        // The target course where we want to import into
                        courseId = params['courseId'];

                        if (this.isExamMode) {
                            // The target exerciseGroupId where we want to import into
                            const exerciseGroupId = params['exerciseGroupId'];
                            const examId = params['examId'];

                            this.exerciseGroupService.find(courseId, examId, exerciseGroupId).subscribe((res) => (this.modelingExercise.exerciseGroup = res.body!));
                            // We reference exam exercises by their exercise group, not their course. Having both would lead to conflicts on the server
                            this.modelingExercise.course = undefined;
                        } else {
                            this.courseService.find(courseId).subscribe((res) => (this.modelingExercise.course = res.body!));
                            // We reference normal exercises by their course, having both would lead to conflicts on the server
                            this.modelingExercise.exerciseGroup = undefined;
                        }
                        resetForImport(this.modelingExercise);
                    }

                    loadCourseExerciseCategories(courseId, this.courseService, this.exerciseService, this.alertService).subscribe((existingCategories) => {
                        this.existingCategories = existingCategories;
                    });
                }),
            )
            .subscribe();

        this.isSaving = false;
        this.notificationText = undefined;
    }

    ngOnDestroy() {
        this.pointsSubscription?.unsubscribe();
        this.bonusPointsSubscription?.unsubscribe();
    }

    async calculateFormSectionStatus() {
        await this.modelingEditor?.apollonEditor?.nextRender;
        this.formSectionStatus = [
            {
                title: 'artemisApp.exercise.sections.general',
                valid: this.exerciseTitleChannelNameComponent().titleChannelNameComponent().isValid(),
            },
            { title: 'artemisApp.exercise.sections.mode', valid: Boolean(this.teamConfigFormGroupComponent?.formValid) },
            { title: 'artemisApp.exercise.sections.problem', valid: true, empty: !this.modelingExercise.problemStatement },
            {
                title: 'artemisApp.exercise.sections.solution',
                valid: Boolean(this.isExamMode || (!this.modelingExercise.exampleSolutionPublicationDateError && this.solutionPublicationDateField?.dateInput.valid)),
                empty:
                    isEmpty(this.modelingEditor?.getCurrentModel()?.elements) ||
                    (!this.isExamMode && !this.modelingExercise.exampleSolutionPublicationDate) ||
                    !this.modelingExercise.exampleSolutionExplanation,
            },
            {
                title: 'artemisApp.exercise.sections.grading',
                valid: Boolean(
                    this.points?.valid &&
                        this.bonusPoints?.valid &&
                        (this.isExamMode ||
                            (!this.modelingExercise.startDateError &&
                                !this.modelingExercise.dueDateError &&
                                !this.modelingExercise.assessmentDueDateError &&
                                this.releaseDateField?.dateInput.valid &&
                                this.startDateField?.dateInput.valid &&
                                this.dueDateField?.dateInput.valid &&
                                this.assessmentDateField?.dateInput.valid)),
                ),
                empty:
                    !this.isExamMode &&
                    // if a dayjs object contains an empty date, it is considered "invalid"
                    (!this.modelingExercise.startDate?.isValid() ||
                        !this.modelingExercise.dueDate?.isValid() ||
                        !this.modelingExercise.assessmentDueDate?.isValid() ||
                        !this.modelingExercise.releaseDate?.isValid()),
            },
        ];

        // otherwise, the change detection does not work on the initial load
        this.changeDetectorRef.detectChanges();
    }

    /**
     * Updates the exercise categories
     * @param categories list of exercise categories
     */
    updateCategories(categories: ExerciseCategory[]): void {
        this.modelingExercise.categories = categories;
        this.exerciseCategories = categories;
    }

    /**
     * Validates if the date is correct
     */
    validateDate(): void {
        this.exerciseService.validateDate(this.modelingExercise);
        this.calculateFormSectionStatus();
    }

    handleEnterKeyNavigation(event: Event): void {
        event.preventDefault();
        event.stopPropagation();
        const activeElement = document.activeElement as HTMLElement;

        if (activeElement?.tagName === 'TEXTAREA' || activeElement?.isContentEditable) {
            return;
        }

        const formRoot = this.editFormEl?.nativeElement as HTMLElement | undefined;
        if (!formRoot) {
            return;
        }

        const apollonContainer = formRoot.querySelector('.apollon-container');
        if (apollonContainer?.contains(activeElement)) {
            return;
        }

        const focusableElements = Array.from(
            formRoot.querySelectorAll(
                'input:not([disabled]):not([readonly]):not([tabindex="-1"]):not([hidden]):not([type="hidden"]), ' + 'select:not([disabled]):not([tabindex="-1"]):not([hidden])',
            ),
        ) as HTMLElement[];

        const currentIndex = focusableElements.indexOf(activeElement);
        if (currentIndex >= 0 && currentIndex < focusableElements.length - 1) {
            focusableElements[currentIndex + 1].focus();
        }
    }

    save() {
        this.modelingExercise.exampleSolutionModel = JSON.stringify(this.modelingEditor?.getCurrentModel());
        this.isSaving = true;

        new SaveExerciseCommand(this.modalService, this.popupService, this.modelingExerciseService, this.backupExercise, this.editType, this.alertService)
            .save(this.modelingExercise, this.isExamMode, this.notificationText)
            .subscribe({
                next: (exercise: ModelingExercise) => this.onSaveSuccess(exercise),
                error: (error: HttpErrorResponse) => this.onSaveError(error),
                complete: () => {
                    this.isSaving = false;
                },
            });
    }

    /**
     * Return to the exercise overview page
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.modelingExercise);
    }

    private onSaveSuccess(exercise: ModelingExercise): void {
        this.eventManager.broadcast({ name: 'modelingExerciseListModification', content: 'OK' });
        this.isSaving = false;

        this.navigationUtilService.navigateForwardFromExerciseUpdateOrCreation(exercise);
        this.calendarService.reloadEvents();
    }

    private onSaveError(errorRes: HttpErrorResponse): void {
        if (errorRes.error && errorRes.error.title) {
            this.alertService.addErrorAlert(errorRes.error.title, errorRes.error.message, errorRes.error.params);
        } else {
            onError(this.alertService, errorRes);
        }
        this.isSaving = false;
    }

    /**
     * When the diagram type changes, we need to check whether {@link AssessmentType.SEMI_AUTOMATIC} is available for the type. If not, we revert to {@link AssessmentType.MANUAL}
     */
    diagramTypeChanged() {
        if (!this.semiAutomaticAssessmentAvailable) {
            this.modelingExercise.assessmentType = AssessmentType.MANUAL;
        }
    }
}
