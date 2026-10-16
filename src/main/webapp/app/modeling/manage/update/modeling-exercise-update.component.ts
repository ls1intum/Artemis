import { DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, OnDestroy, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UMLDiagramType, UMLModel, importDiagram } from '@tumaet/apollon';
import { CompetencySelectionPrimengComponent } from 'app/atlas/shared/competency-selection-primeng/competency-selection-primeng.component';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { loadCourseExerciseCategories } from 'app/exercise/course-exercises/course-utils';
import { DifficultyPickerComponent } from 'app/exercise/difficulty-picker/difficulty-picker.component';
import { ExerciseTitleChannelNamePrimengComponent } from 'app/exercise/exercise-title-channel-name-primeng/exercise-title-channel-name-primeng.component';
import { ExerciseUpdateWarningService } from 'app/exercise/exercise-update-warning/exercise-update-warning.service';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { PresentationScoreComponent } from 'app/exercise/presentation-score/presentation-score.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { Exercise, ExerciseMode, IncludedInOverallScore, ValidationReason, resetForImport } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradingInstructionsDetailsComponent } from 'app/exercise/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { TeamConfigFormGroupComponent } from 'app/exercise/team-config-form-group/team-config-form-group.component';
import { EditType, SaveExerciseCommand } from 'app/exercise/util/exercise.utils';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ModelingEditorBottomCenterDirective } from 'app/modeling/shared/modeling-editor/modeling-editor-bottom-center.directive';
import { ModelingEditorTopLeftDirective } from 'app/modeling/shared/modeling-editor/modeling-editor-top-left.directive';
import { CategorySelectorPrimengComponent } from 'app/exercise/category-selector-primeng/category-selector-primeng.component';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { FormFooterComponent } from 'app/shared-ui/form/form-footer/form-footer.component';
import { FormSectionStatus, FormStatusBarComponent } from 'app/shared-ui/form/form-status-bar/form-status-bar.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { FormulaAction } from 'app/editor/monaco-editor/model/actions/formula.action';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/foundation/service/alert.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { onError } from 'app/foundation/util/global.utils';
import { parseJson } from 'app/foundation/util/json.util';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { scrollToTopOfPage } from 'app/foundation/util/utils';
import { isEmpty } from 'lodash-es';
import { switchMap, take, tap } from 'rxjs/operators';
import { ModelingExerciseService } from '../services/modeling-exercise.service';
import { ExerciseTimelineComponent } from 'app/exercise/exercise-timeline/exercise-timeline.component';
import { TimelineStatus } from 'app/shared-ui/timeline/timeline.component';
import {
    ExerciseValidationViewState,
    getCommonExerciseInvalidReasons,
    getGeneralSectionInvalidReasons,
    getGradingSectionInvalidReasons,
    getModeSectionInvalidReasons,
} from 'app/exercise/util/exercise-validation.util';
import { ExerciseFormState } from 'app/exercise/util/exercise-form-state';
import { countModelElements } from 'app/modeling/shared/apollon-model.util';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiConfirmDialogComponent, TumAetUiConfirmationService, TumAetUiSelectComponent } from '@tumaet/ui-angular';
import { ModelingMarkdownExplanationEditorComponent } from 'app/modeling/shared/modeling-markdown-explanation-editor/modeling-markdown-explanation-editor.component';
import { toSignal } from '@angular/core/rxjs-interop';
import { ExerciseGroupTimelineLockComponent } from 'app/course/manage/exercises/group-timeline-lock/exercise-group-timeline-lock.component';
import { ExerciseGroupDateNoticeComponent } from 'app/exercise/exercise-group-date-notice/exercise-group-date-notice.component';

@Component({
    selector: 'jhi-modeling-exercise-update',
    templateUrl: './modeling-exercise-update.component.html',
    styleUrls: ['./modeling-exercise-update.component.scss'],
    imports: [
        FormsModule,
        TranslateDirective,
        DocumentationButtonComponent,
        FormStatusBarComponent,
        ExerciseTitleChannelNamePrimengComponent,
        HelpIconComponent,
        CategorySelectorPrimengComponent,
        DifficultyPickerComponent,
        TeamConfigFormGroupComponent,
        MarkdownEditorMonacoComponent,
        CompetencySelectionPrimengComponent,
        ModelingEditorComponent,
        ModelingEditorBottomCenterDirective,
        ModelingEditorTopLeftDirective,
        IncludedInOverallScorePickerComponent,
        PresentationScoreComponent,
        GradingInstructionsDetailsComponent,
        FormFooterComponent,
        ArtemisTranslatePipe,
        ExerciseTimelineComponent,
        TumAetUiConfirmDialogComponent,
        TumAetUiSelectComponent,
        ModelingMarkdownExplanationEditorComponent,
        ExerciseGroupTimelineLockComponent,
        ExerciseGroupDateNoticeComponent,
    ],
    providers: [TumAetUiConfirmationService],
})
export class ModelingExerciseUpdateComponent implements OnDestroy, OnInit {
    private static readonly SCROLL_SNAP_CLASS = 'modeling-exercise-editor-scroll-snap';
    private static readonly DIAGRAM_TYPE_CONFIRMATION_KEY = 'modeling-diagram-type-change';

    private readonly document = inject(DOCUMENT);
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
    private readonly calendarService = inject(CalendarService);
    private readonly translateService = inject(TranslateService);
    private readonly confirmationService = inject(TumAetUiConfirmationService);
    private readonly languageChange = toSignal(this.translateService.onLangChange, { initialValue: undefined });
    timelineStatus = signal<TimelineStatus>({ valid: true, empty: false, invalidItems: [] });

    readonly exerciseTitleChannelNameComponent = viewChild(ExerciseTitleChannelNamePrimengComponent);
    readonly modelingEditor = viewChild(ModelingEditorComponent);
    readonly gradingInstructionsDetails = viewChild(GradingInstructionsDetailsComponent);

    readonly editFormEl = viewChild<ElementRef<HTMLFormElement>>('editForm');
    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly documentationType: DocumentationType = 'Model';
    protected readonly diagramTypes = [
        UMLDiagramType.ClassDiagram,
        UMLDiagramType.ActivityDiagram,
        UMLDiagramType.ObjectDiagram,
        UMLDiagramType.UseCaseDiagram,
        UMLDiagramType.CommunicationDiagram,
        UMLDiagramType.ComponentDiagram,
        UMLDiagramType.DeploymentDiagram,
        UMLDiagramType.PetriNet,
        UMLDiagramType.SyntaxTree,
        UMLDiagramType.Flowchart,
        UMLDiagramType.BPMN,
        UMLDiagramType.Sfc,
    ] as const;
    protected readonly diagramTypeOptions = computed(() => {
        this.languageChange();
        return this.diagramTypes.map((diagramType) => ({
            value: diagramType,
            label: this.translateService.instant(`artemisApp.DiagramType.${diagramType}`),
        }));
    });

    /** Every write to the exercise goes through this, so that {@link invalidReasons} and {@link formSectionStatus} follow it. */
    readonly exerciseState = new ExerciseFormState<ModelingExercise>();
    get modelingExercise(): ModelingExercise {
        return this.exerciseState.exercise();
    }
    set modelingExercise(value: ModelingExercise) {
        this.exerciseState.set(value);
    }
    backupExercise!: ModelingExercise;
    /** The example solution the exercise was opened with, imported for the editor; cleared when the diagram type changes. */
    readonly exampleSolution = signal<UMLModel | undefined>(undefined);
    /** The example solution as the editor last reported it, which is undefined until the editor reports a change. */
    private readonly editedExampleSolution = signal<UMLModel | undefined>(undefined);
    private readonly hasExampleSolutionDiagram = computed(() => !isEmpty((this.editedExampleSolution() ?? this.exampleSolution())?.nodes));
    protected readonly hasExampleSolution = computed(() => this.hasExampleSolutionDiagram() || !!this.exerciseState.exercise()?.exampleSolutionExplanation);
    protected readonly selectedDiagramType = signal<UMLDiagramType>(UMLDiagramType.ClassDiagram);
    readonly isSaving = signal(false);
    readonly exerciseCategories = signal<ExerciseCategory[]>([]);
    readonly existingCategories = signal<ExerciseCategory[]>([]);
    notificationText?: string;
    domainActionsProblemStatement = [new FormulaAction()];
    domainActionsExampleSolution = [new FormulaAction()];
    readonly isImport = signal<boolean>(undefined!);
    readonly isExamMode = signal<boolean>(undefined!);

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
        return exercise ? getCommonExerciseInvalidReasons(exercise, this.validationViewState()) : [];
    });

    readonly formSectionStatus = computed<FormSectionStatus[]>(() => {
        const exercise = this.exerciseState.exercise();
        if (!exercise) {
            return [];
        }
        const viewState = this.validationViewState();
        return [
            { title: 'artemisApp.exercise.sections.general', valid: getGeneralSectionInvalidReasons(exercise, viewState).length === 0 },
            { title: 'artemisApp.exercise.sections.mode', valid: getModeSectionInvalidReasons(exercise).length === 0 },
            { title: 'artemisApp.exercise.sections.problem', valid: true, empty: !exercise.problemStatement },
            { title: 'artemisApp.exercise.sections.solution', valid: true, empty: !this.hasExampleSolutionDiagram() || !exercise.exampleSolutionExplanation },
            {
                title: 'artemisApp.exercise.sections.grading',
                valid: getGradingSectionInvalidReasons(exercise, viewState).length === 0,
                empty: !viewState.isExamMode && viewState.timelineStatus.empty,
            },
        ];
    });

    get editType(): EditType {
        if (this.isImport()) {
            return EditType.IMPORT;
        }

        return this.modelingExercise.id == undefined ? EditType.CREATE : EditType.UPDATE;
    }

    ngOnInit(): void {
        this.document.documentElement.classList.add(ModelingExerciseUpdateComponent.SCROLL_SNAP_CLASS);
        scrollToTopOfPage();

        this.activatedRoute.data.subscribe(({ modelingExercise }) => {
            this.modelingExercise = modelingExercise;
            this.selectedDiagramType.set(this.modelingExercise.diagramType ?? UMLDiagramType.ClassDiagram);
            if (this.modelingExercise.exampleSolutionModel != undefined) {
                this.exampleSolution.set(importDiagram(parseJson(this.modelingExercise.exampleSolutionModel)));
            }

            this.backupExercise = deepClone(this.modelingExercise);
        });

        this.activatedRoute.url
            .pipe(
                tap((segments) => {
                    this.isExamMode.set(segments.some((segment) => segment.path === 'exercise-groups'));
                    this.isImport.set(segments.some((segment) => segment.path === 'import'));
                }),
                switchMap(() => this.activatedRoute.params),
                tap((params) => {
                    let courseId;

                    if (!this.isExamMode()) {
                        this.exerciseCategories.set(this.modelingExercise.categories || []);
                        if (this.modelingExercise.course) {
                            courseId = this.modelingExercise.course.id!;
                        } else {
                            courseId = this.modelingExercise.exerciseGroup!.exam!.course!.id!;
                        }
                    } else {
                        this.exerciseState.update((exercise) => {
                            exercise.mode = ExerciseMode.INDIVIDUAL;
                            exercise.teamAssignmentConfig = undefined;
                            exercise.teamMode = false;
                            if (exercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
                                exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
                            }
                        });
                    }
                    if (this.isImport()) {
                        courseId = params['courseId'];

                        if (this.isExamMode()) {
                            const { exerciseGroupId, examId } = params;

                            this.exerciseGroupService.find(courseId, examId, exerciseGroupId).subscribe((res) => this.exerciseState.patch('exerciseGroup', res.body!));
                            this.exerciseState.patch('course', undefined);
                        } else {
                            this.courseService.find(courseId).subscribe((res) => this.exerciseState.patch('course', res.body!));
                            this.exerciseState.patch('exerciseGroup', undefined);
                        }
                        this.exerciseState.update(resetForImport);
                    }

                    loadCourseExerciseCategories(courseId, this.courseService, this.exerciseService, this.alertService).subscribe((existingCategories) => {
                        this.existingCategories.set(existingCategories);
                    });
                }),
            )
            .subscribe();

        this.isSaving.set(false);
        this.notificationText = undefined;
    }

    ngOnDestroy() {
        this.document.documentElement.classList.remove(ModelingExerciseUpdateComponent.SCROLL_SNAP_CLASS);
    }

    updateCategories(categories: ExerciseCategory[]): void {
        this.exerciseState.patch('categories', categories);
        this.exerciseCategories.set(categories);
    }

    protected requestDiagramTypeChange(nextDiagramType: UMLDiagramType): void {
        const currentDiagramType = this.modelingExercise.diagramType ?? UMLDiagramType.ClassDiagram;
        if (nextDiagramType === currentDiagramType) {
            return;
        }

        this.selectedDiagramType.set(nextDiagramType);
        const currentModel = this.modelingEditor()?.getCurrentModel() ?? this.exampleSolution();
        if (countModelElements(currentModel) === 0) {
            this.applyDiagramTypeChange(nextDiagramType);
            return;
        }

        const translationKeys = {
            title: 'artemisApp.modelingExercise.diagramTypeChange.title',
            message: 'artemisApp.modelingExercise.diagramTypeChange.message',
            confirm: 'artemisApp.modelingExercise.diagramTypeChange.confirm',
            cancel: 'entity.action.cancel',
        } as const;
        this.translateService
            .get(Object.values(translationKeys))
            .pipe(take(1))
            .subscribe((translations) => {
                // Ignore an obsolete translation response if the pending diagram type changed.
                if (this.selectedDiagramType() !== nextDiagramType || (this.modelingExercise.diagramType ?? UMLDiagramType.ClassDiagram) !== currentDiagramType) {
                    return;
                }

                this.confirmationService.confirm({
                    key: ModelingExerciseUpdateComponent.DIAGRAM_TYPE_CONFIRMATION_KEY,
                    header: translations[translationKeys.title],
                    message: translations[translationKeys.message],
                    acceptLabel: translations[translationKeys.confirm],
                    rejectLabel: translations[translationKeys.cancel],
                    acceptSeverity: 'danger',
                    icon: faTriangleExclamation,
                    accept: () => this.applyDiagramTypeChange(nextDiagramType),
                    reject: () => this.selectedDiagramType.set(currentDiagramType),
                });
            });
    }

    private applyDiagramTypeChange(diagramType: UMLDiagramType): void {
        this.exampleSolution.set(undefined);
        this.editedExampleSolution.set(undefined);
        this.exerciseState.patch('diagramType', diagramType);
        this.selectedDiagramType.set(diagramType);
    }

    handleEnterKeyNavigation(event: Event): void {
        event.preventDefault();
        event.stopPropagation();
        const activeElement = document.activeElement as HTMLElement;

        if (activeElement?.tagName === 'TEXTAREA' || activeElement?.isContentEditable) {
            return;
        }

        const formRoot = this.editFormEl()?.nativeElement as HTMLElement | undefined;
        if (!formRoot) {
            return;
        }

        const apollonContainer = formRoot.querySelector('.apollon-container');
        if (apollonContainer?.contains(activeElement)) {
            return;
        }

        const focusableElements = Array.from(
            formRoot.querySelectorAll<HTMLElement>(
                'input:not([disabled]):not([readonly]):not([tabindex="-1"]):not([hidden]):not([type="hidden"]), ' + 'select:not([disabled]):not([tabindex="-1"]):not([hidden])',
            ),
        );

        const currentIndex = focusableElements.indexOf(activeElement);
        if (currentIndex >= 0 && currentIndex < focusableElements.length - 1) {
            focusableElements[currentIndex + 1].focus();
        }
    }

    save() {
        this.exerciseState.patch('exampleSolutionModel', JSON.stringify(this.modelingEditor()?.getCurrentModel()));
        // Flush text-mode Monaco before isSaving disables the child (editable becomes false). A
        // rejected parse aborts the save: the model still holds the previous grading criteria.
        if (this.gradingInstructionsDetails()?.prepareForSave() === false) {
            return;
        }
        this.isSaving.set(true);
        // The save trims the title as well, but in place; doing it here first keeps the reasons in step after a failed save.
        this.exerciseState.update(Exercise.sanitize);

        new SaveExerciseCommand(this.modalService, this.popupService, this.modelingExerciseService, this.backupExercise, this.editType)
            .save(this.modelingExercise, this.isExamMode(), this.notificationText)
            .subscribe({
                next: (exercise: ModelingExercise) => this.onSaveSuccess(exercise),
                error: (error: HttpErrorResponse) => this.onSaveError(error),
                complete: () => {
                    this.isSaving.set(false);
                },
            });
    }

    /** Copies the latest modeling-editor content to the exercise before criteria generation. */
    readonly synchronizeForAssessmentCriteriaGeneration = () => {
        this.exerciseState.patch('exampleSolutionModel', JSON.stringify(this.modelingEditor()?.getCurrentModel()));
    };

    /**
     * Keeps the serialized example solution synchronized with modeling-editor changes.
     * @param model Latest model emitted by the editor.
     */
    readonly onModelChanged = (model: UMLModel): void => {
        this.editedExampleSolution.set(model);
        this.exerciseState.patch('exampleSolutionModel', JSON.stringify(model));
    };

    /** Supplies modeling-specific example-solution context for assessment-criteria generation. */
    readonly assessmentCriteriaAdditionalContext = () =>
        [
            `Diagram type:\n${this.modelingExercise.diagramType ?? ''}`,
            `Serialized example solution model:\n${this.modelingExercise.exampleSolutionModel ?? ''}`,
            `Example solution explanation:\n${this.modelingExercise.exampleSolutionExplanation ?? ''}`,
        ].join('\n\n');

    /**
     * Return to the exercise overview page
     */
    previousState() {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.modelingExercise);
    }

    private onSaveSuccess(exercise: ModelingExercise): void {
        this.eventManager.broadcast({ name: 'modelingExerciseListModification', content: 'OK' });
        this.isSaving.set(false);

        this.navigationUtilService.navigateForwardFromExerciseUpdateOrCreation(exercise);
        this.calendarService.reloadEvents();
    }

    private onSaveError(errorRes: HttpErrorResponse): void {
        if (errorRes.error && errorRes.error.title) {
            this.alertService.addErrorAlert(errorRes.error.title, errorRes.error.message, errorRes.error.params);
        } else {
            onError(this.alertService, errorRes);
        }
        this.isSaving.set(false);
    }
}
