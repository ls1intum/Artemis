import { AfterViewInit, Component, HostListener, OnDestroy, ViewChild, ViewEncapsulation, computed, effect, inject, input, output, signal } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { EMPTY, Observable, Subject, Subscription, of, throwError } from 'rxjs';
import { catchError, finalize, map, switchMap, tap } from 'rxjs/operators';
import { ProgrammingExerciseTestCase } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { ProblemStatementAnalysis } from 'app/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseGradingService } from 'app/programming/manage/services/programming-exercise-grading.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { faCheckCircle, faCircleNotch, faExclamationTriangle, faSave } from '@fortawesome/free-solid-svg-icons';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { MonacoEditorMode } from 'app/shared/monaco-editor/monaco-editor.component';
import { LineChange } from 'app/programming/shared/utils/diff.utils';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { TaskAction } from 'app/shared/monaco-editor/model/actions/task.action';
import { TestCaseAction } from 'app/shared/monaco-editor/model/actions/test-case.action';
import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseInstructionAnalysisComponent } from './analysis/programming-exercise-instruction-analysis.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';

import { RewriteAction } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewrite.action';
import { MODULE_FEATURE_HYPERION, MODULE_FEATURE_IRIS } from 'app/app.constants';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { Annotation } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { RewriteResult } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-result';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ProblemStatementSyncService, ProblemStatementSyncState } from 'app/programming/manage/services/problem-statement-sync.service';
import { INLINE_REFINEMENT_PROMPT_WIDTH_PX, InlineRefinementEvent, InstructionSelectionPosition } from 'app/programming/manage/shared/problem-statement.utils';
import { editor } from 'monaco-editor';
import { MonacoBinding } from 'y-monaco';
import { InlineRefinementButtonComponent } from 'app/shared/monaco-editor/inline-refinement-button/inline-refinement-button.component';

@Component({
    selector: 'jhi-programming-exercise-editable-instructions',
    templateUrl: './programming-exercise-editable-instruction.component.html',
    styleUrls: ['./programming-exercise-editable-instruction.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        MarkdownEditorMonacoComponent,
        NgClass,
        FaIconComponent,
        TranslateDirective,
        NgbTooltip,
        ProgrammingExerciseInstructionAnalysisComponent,
        ArtemisTranslatePipe,
        ProgrammingExerciseInstructionComponent,
        InlineRefinementButtonComponent,
    ],
})
export class ProgrammingExerciseEditableInstructionComponent implements AfterViewInit, OnDestroy {
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private alertService = inject(AlertService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private testCaseService = inject(ProgrammingExerciseGradingService);
    private profileService = inject(ProfileService);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);
    private problemStatementSyncService = inject(ProblemStatementSyncService);

    /**
     * Legacy manual diff state used inside the `effect()` below.
     * This is intentionally mutable and not a signal; therefore it does not retrigger the effect.
     * Caveat: if `exercise()` changes multiple times synchronously, intermediate states can be skipped.
     * Keep this in mind when extending the effect logic.
     */
    private previousExercise?: ProgrammingExercise;

    unsavedChangesValue = false;

    exerciseTestCases: string[] = [];

    taskRegex = TaskAction.GLOBAL_TASK_REGEX;
    testCaseAction = new TestCaseAction();
    domainActions: TextEditorDomainAction[] = [new FormulaAction(), new TaskAction(), this.testCaseAction];

    irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
    hyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);
    artemisIntelligenceActions = computed(() => {
        const actions = [];
        const courseId = this.exercise().course?.id;
        if (this.hyperionEnabled && !!courseId) {
            actions.push(
                new RewriteAction(
                    this.artemisIntelligenceService,
                    RewritingVariant.PROBLEM_STATEMENT,
                    courseId, // courseId is required by Hyperion API.
                    signal<RewriteResult>({ result: '', inconsistencies: undefined, suggestions: undefined, improvement: undefined }),
                ),
            );
        }
        return actions;
    });
    protected readonly isAiApplying = computed(() => this.isGeneratingOrRefining() || this.artemisIntelligenceService.isLoading());

    savingInstructions = false;

    testCaseSubscription: Subscription;
    forceRenderSubscription: Subscription;
    private problemStatementStateReplacementSubscription?: Subscription;
    private problemStatementSyncState?: ProblemStatementSyncState;
    private problemStatementBinding?: MonacoBinding;
    private problemStatementBindingDestroyed = false;

    @ViewChild(MarkdownEditorMonacoComponent, { static: false }) markdownEditorMonaco?: MarkdownEditorMonacoComponent;

    readonly showStatus = input<boolean>(true);
    // If the programming exercise is being created, some features have to be disabled (saving the problemStatement & querying test cases).
    readonly editMode = input<boolean>(true);
    readonly enableResize = input<boolean>(true);
    readonly initialEditorHeight = input.required<MarkdownEditorHeight>();
    /**
     * If true, the editor height is managed externally by the parent container.
     * Use this when embedding in a layout that controls height (e.g., code editor view).
     */
    readonly externalHeight = input<boolean>(false);
    readonly showSaveButton = input<boolean>(false);
    /**
     * Whether to show the preview button and default preview in the markdown editor.
     * Set to false when using an external preview component (e.g., in the code editor).
     */
    readonly consistencyIssues = input<ConsistencyIssue[]>([]);

    readonly isGeneratingOrRefining = input<boolean>(false);

    readonly mode = input<MonacoEditorMode>('normal');

    readonly renderSideBySide = input<boolean>(true);

    readonly exercise = input.required<ProgrammingExercise>();
    /**
     * Optional participation to use for the preview. Falls back to exercise().templateParticipation.
     * In the code editor view, this is the currently selected participation (template/solution/student).
     */
    readonly participation = input<Participation>();
    readonly forceRender = input<Observable<void> | undefined>();
    readonly showPreview = input<boolean>(true);
    readonly enableExerciseReviewComments = input<boolean>(false);

    readonly hasUnsavedChanges = output<boolean>();
    readonly instructionChange = output<string>();
    readonly onProblemStatementSaved = output<void>();
    generateHtmlSubject: Subject<void> = new Subject<void>();

    inlineRefinementPosition = signal<{ top: number; left: number } | undefined>(undefined);
    selectedTextForRefinement = signal('');
    selectionPositionInfo = signal<InstructionSelectionPosition | undefined>(undefined);
    readonly onInlineRefinement = output<InlineRefinementEvent>();

    /** Emits diff line change information when in diff mode */
    readonly diffLineChange = output<{ ready: boolean; lineChange: LineChange }>();

    set unsavedChanges(hasChanges: boolean) {
        this.unsavedChangesValue = hasChanges;
        // Why emit only `true` transitions? Once an exercise is saved, the page would automatically re-navigate to the exercise page.
        // This would unmount this component and clear the unsaved changes indicator.
        if (hasChanges) {
            this.hasUnsavedChanges.emit(hasChanges);
        }
    }

    constructor() {
        /**
         * React to exercise changes.
         * Note: this effect mutates `previousExercise` as an implementation detail for manual diffing.
         * This is a known legacy pattern and can be fragile for bursty synchronous updates.
         */
        effect(() => {
            const currentExercise = this.exercise();

            if (this.previousExercise && currentExercise.problemStatement !== this.previousExercise.problemStatement) {
                this.unsavedChanges = true;
            }

            const hasExerciseIdChanged = !this.previousExercise || this.previousExercise.id !== currentExercise.id;
            if (hasExerciseIdChanged && currentExercise.id) {
                this.setupTestCaseSubscription();
            }

            this.previousExercise = currentExercise;
        });
    }

    // Icons
    faSave = faSave;
    faCheckCircle = faCheckCircle;
    faExclamationTriangle = faExclamationTriangle;
    faCircleNotch = faCircleNotch;

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;

    ngOnDestroy(): void {
        if (this.testCaseSubscription) {
            this.testCaseSubscription.unsubscribe();
        }
        if (this.forceRenderSubscription) {
            this.forceRenderSubscription.unsubscribe();
        }
        this.teardownProblemStatementSync();
    }

    ngAfterViewInit() {
        // If forced to render, generate the instruction HTML.
        const forceRender = this.forceRender();
        if (forceRender) {
            this.forceRenderSubscription = forceRender.subscribe(() => this.generateHtml());
        }

        // Trigger initial preview render after view initialization.
        // This ensures the ProgrammingExerciseInstructionComponent renders when first shown.
        if (this.showPreview()) {
            // Small delay to allow the instruction component to initialize
            setTimeout(() => this.generateHtmlSubject.next(), 0);
        }

        const exercise = this.exercise();
        if (!exercise.id) {
            return;
        }
        this.initializeProblemStatementSync(exercise.id, exercise.problemStatement ?? '');
    }

    /** Save the problem statement on the server.
     * @param event
     **/
    saveInstructions(event: any) {
        event.stopPropagation();
        this.savingInstructions = true;
        const problemStatementToSave = this.exercise().problemStatement?.trim() || undefined;
        return this.programmingExerciseService
            .updateProblemStatement(this.exercise().id!, problemStatementToSave)
            .pipe(
                tap(() => {
                    this.unsavedChanges = false;
                    this.onProblemStatementSaved.emit();
                }),
                catchError(() => {
                    // TODO: move to programming exercise translations
                    this.alertService.error(`artemisApp.editor.errors.problemStatementCouldNotBeUpdated`);
                    return EMPTY;
                }),
                finalize(() => {
                    this.savingInstructions = false;
                }),
            )
            .subscribe();
    }

    @HostListener('document:keydown.control.s', ['$event'])
    saveOnControlAndS(event: Event) {
        if (!navigator.userAgent.includes('Mac')) {
            event.preventDefault();
            this.saveInstructions(event);
        }
    }

    @HostListener('document:keydown.meta.s', ['$event'])
    saveOnCommandAndS(event: Event) {
        if (navigator.userAgent.includes('Mac')) {
            event.preventDefault();
            this.saveInstructions(event);
        }
    }

    updateProblemStatement(problemStatement: string) {
        if (this.exercise().problemStatement !== problemStatement) {
            this.unsavedChanges = true;
            // parent component should update `problemStatement` in `exercise`
            this.instructionChange.emit(problemStatement);
            // Trigger preview update when showPreview is enabled
            this.generateHtmlSubject.next();
        }
    }

    /**
     * Signal that the markdown should be rendered into html.
     */
    generateHtml() {
        this.generateHtmlSubject.next();
    }

    private setupTestCaseSubscription() {
        if (this.testCaseSubscription) {
            this.testCaseSubscription.unsubscribe();
        }

        // Only set up a subscription for test cases if the exercise already exists.
        if (this.editMode()) {
            this.testCaseSubscription = this.testCaseService
                .subscribeForTestCases(this.exercise().id!)
                .pipe(
                    switchMap((testCases: ProgrammingExerciseTestCase[] | undefined) => {
                        // If there are test cases, map them to their names, sort them and use them for the markdown editor.
                        if (testCases) {
                            const sortedTestCaseNames = testCases
                                .filter((testCase) => testCase.active)
                                .map((testCase) => testCase.testName!)
                                .sort();
                            return of(sortedTestCaseNames);
                        } else if (this.exercise().templateParticipation) {
                            // Legacy case: If there are no test cases, but a template participation, use its feedbacks for generating test names.
                            return this.loadTestCasesFromTemplateParticipationResult(this.exercise().templateParticipation!.id!);
                        }
                        return of();
                    }),
                    tap((testCaseNames: string[]) => {
                        this.exerciseTestCases = testCaseNames;
                        const cases = this.exerciseTestCases.map((value) => ({ value, id: value }));
                        this.testCaseAction.setValues(cases);
                    }),
                    catchError(() => of()),
                )
                .subscribe();
        }
    }

    /**
     * Generate test case names from the feedback of the exercise's templateParticipation.
     * This is the fallback for older programming exercises without test cases in the database.
     * @param templateParticipationId
     */
    loadTestCasesFromTemplateParticipationResult = (templateParticipationId: number): Observable<Array<string | undefined>> => {
        // Fallback for exercises that don't have test cases yet.
        return this.programmingExerciseParticipationService.getLatestResultWithFeedback(templateParticipationId).pipe(
            map((result) => (!result?.feedbacks ? throwError(() => new Error('no result available')) : result)),
            // use the text (legacy case) or the name of the provided test case attribute
            map(({ feedbacks }: Result) => feedbacks!.map((feedback) => feedback.text ?? feedback.testCase?.testName).sort()),
            catchError(() => of([])),
        );
    };

    /**
     * On every update of the problem statement analysis, update the appropriate line numbers of the editor with the results of the analysis.
     * Will show warning symbols for every item.
     *
     * @param analysis that contains the resulting issues of the problem statement.
     */
    onAnalysisUpdate = (analysis: ProblemStatementAnalysis) => {
        const lineWarnings = this.mapAnalysisToWarnings(analysis);
        this.markdownEditorMonaco?.monacoEditor?.setAnnotations(lineWarnings as Annotation[]);
    };

    /**
     * Gets the current content from the editor.
     * In diff mode, returns the modified (right) side content.
     * In normal mode, returns the current editor content.
     *
     * @returns The current editor content, or undefined if editor is not available.
     */
    getCurrentContent(): string | undefined {
        const monacoEditor = this.markdownEditorMonaco?.monacoEditor;
        if (!monacoEditor) {
            return undefined;
        }
        return monacoEditor.getText();
    }

    /**
     * Sets the editor text directly.
     * Use this to revert content in the editor.
     *
     * @param text The text to set in the editor.
     */
    setText(text: string): void {
        const monacoEditor = this.markdownEditorMonaco?.monacoEditor;
        if (!monacoEditor) {
            return;
        }

        monacoEditor.setText(text);
        this.updateProblemStatement(text);
    }

    /**
     * Scrolls the Monaco editor to the specified line immediately.
     *
     * @param {number} lineNumber
     *        The line to reveal in the editor.
     */
    jumpToLine(lineNumber: number) {
        this.markdownEditorMonaco?.monacoEditor.revealLine(lineNumber, editor.ScrollType.Immediate);
    }

    clearReviewCommentDrafts(): void {
        this.markdownEditorMonaco?.clearReviewCommentDrafts();
    }

    private mapAnalysisToWarnings = (analysis: ProblemStatementAnalysis) => {
        return Array.from(analysis.values()).flatMap(({ lineNumber, invalidTestCases, repeatedTestCases }) =>
            this.mapIssuesToAnnotations(lineNumber, invalidTestCases, repeatedTestCases),
        );
    };

    private mapIssuesToAnnotations = (lineNumber: number, invalidTestCases?: string[], repeatedTestCases?: string[]) => {
        const mapIssues = (issues: string[]) => ({ row: lineNumber, column: 0, text: ' - ' + issues.join('\n - '), type: 'warning' });

        const annotations = [];
        if (invalidTestCases) {
            annotations.push(mapIssues(invalidTestCases));
        }

        if (repeatedTestCases) {
            annotations.push(mapIssues(repeatedTestCases));
        }

        return annotations;
    };

    /**
     * Handles selection changes from the markdown editor.
     * Shows floating refinement button when text is selected.
     */
    onEditorSelectionChange(
        selection:
            | {
                  startLine: number;
                  endLine: number;
                  startColumn: number;
                  endColumn: number;
                  selectedText: string;
                  screenPosition: { top: number; left: number };
              }
            | undefined,
    ): void {
        // Show/hide inline refinement button based on selection
        if (selection && selection.selectedText && selection.selectedText.trim().length > 0 && this.hyperionEnabled && !this.isAiApplying()) {
            const viewportWidth = window.innerWidth;
            const viewportHeight = window.innerHeight;
            const clampedLeft = Math.max(8, Math.min(selection.screenPosition.left, viewportWidth - INLINE_REFINEMENT_PROMPT_WIDTH_PX - 8));
            const clampedTop = Math.max(8, Math.min(selection.screenPosition.top, viewportHeight - 60));
            this.inlineRefinementPosition.set({
                top: clampedTop,
                left: clampedLeft,
            });
            this.selectedTextForRefinement.set(selection.selectedText);
            this.selectionPositionInfo.set({
                startLine: selection.startLine,
                endLine: selection.endLine,
                startColumn: selection.startColumn,
                endColumn: selection.endColumn,
            });
        } else {
            this.hideInlineRefinementButton();
        }
    }

    /**
     * Hides the floating inline refinement button.
     */
    hideInlineRefinementButton(): void {
        this.inlineRefinementPosition.set(undefined);
        this.selectedTextForRefinement.set('');
        this.selectionPositionInfo.set(undefined);
    }

    /**
     * Handles inline refinement submission.
     * Emits the event for parent to process the refinement.
     */
    onInlineRefine(event: InlineRefinementEvent): void {
        this.onInlineRefinement.emit(event);
        this.hideInlineRefinementButton();
    }

    /**
     * Applies the refined content to the editor in diff mode.
     * @param refined The new content to show in the modified editor.
     */
    applyRefinedContent(refined: string): void {
        this.markdownEditorMonaco?.applyRefinedContent(refined);
        // No explicit updateProblemStatement call needed: Monaco's onDidChangeModelContent
        // event propagates the change via textChanged → markdownChange → updateProblemStatement.
    }

    /**
     * Reverts all changes in the diff editor by restoring the snapshot taken when diff mode was entered.
     */
    revertAll(): void {
        this.markdownEditorMonaco?.revertAll();
        const currentContent = this.markdownEditorMonaco?.monacoEditor?.getText();
        if (currentContent !== undefined) {
            this.updateProblemStatement(currentContent);
        }
    }

    /**
     * Set up collaborative Yjs synchronization for the markdown editor.
     * Creates the Yjs document/binding and wires Monaco to the shared text.
     *
     * Note: This method is only called once during ngAfterViewInit(). The component is always
     * destroyed and recreated when navigating to a different exercise, so there is no need to
     * watch for exercise ID changes or reinitialize sync when the exercise input changes.
     *
     * @param exerciseId The exercise id to scope synchronization updates.
     * @param initialText The initial problem statement content used for seeding.
     */
    private initializeProblemStatementSync(exerciseId: number, initialText: string) {
        if (!this.editMode() || this.problemStatementBinding) {
            return;
        }
        if (!this.markdownEditorMonaco?.monacoEditor) {
            return;
        }
        const model = this.markdownEditorMonaco.monacoEditor.getModel();
        const editorInstance = this.markdownEditorMonaco.monacoEditor.getEditor();
        if (!model || !editorInstance) {
            return;
        }
        this.teardownProblemStatementSync();
        this.problemStatementSyncState = this.problemStatementSyncService.init(exerciseId, initialText);
        this.createProblemStatementBinding(this.problemStatementSyncState, model, editorInstance);
        this.problemStatementStateReplacementSubscription = this.problemStatementSyncService.stateReplaced$.subscribe((syncState) => {
            this.problemStatementSyncState = syncState;
            // Force model content to the replacement Yjs state to avoid merge/appending when rebinding.
            model.setValue(syncState.text.toString());
            this.createProblemStatementBinding(syncState, model, editorInstance);
        });
    }

    /**
     * Tear down Yjs synchronization and release Monaco binding resources.
     */
    private teardownProblemStatementSync() {
        this.problemStatementStateReplacementSubscription?.unsubscribe();
        this.problemStatementStateReplacementSubscription = undefined;
        this.problemStatementBinding?.destroy();
        this.problemStatementBinding = undefined;
        this.problemStatementBindingDestroyed = false;
        this.problemStatementSyncState = undefined;
        this.problemStatementSyncService.reset();
    }

    /**
     * Create (or recreate) the Monaco <-> Yjs binding for the problem statement editor.
     *
     * This is called on initial setup and whenever the sync service replaces the Y.Doc
     * after accepting a late winning full-content response. Recreating the binding keeps
     * Monaco attached to the active Y.Text/Y.Awareness objects.
     *
     * @param syncState Current synchronized Yjs primitives.
     * @param model Monaco text model backing the editor.
     * @param editorInstance Monaco editor instance bound to the model.
     */
    private createProblemStatementBinding(syncState: ProblemStatementSyncState, model: editor.ITextModel, editorInstance: editor.IStandaloneCodeEditor) {
        this.problemStatementBinding?.destroy();
        const binding = new MonacoBinding(syncState.text, model, new Set([editorInstance]), syncState.awareness);
        // Monaco may or may not dispose its model and call destroy(); this is a guard against a second call from ngOnDestroy.
        const originalDestroy = binding.destroy.bind(binding);
        this.problemStatementBindingDestroyed = false;
        binding.destroy = () => {
            if (this.problemStatementBindingDestroyed) {
                return;
            }
            this.problemStatementBindingDestroyed = true;
            originalDestroy();
        };
        this.problemStatementBinding = binding;
    }
}
