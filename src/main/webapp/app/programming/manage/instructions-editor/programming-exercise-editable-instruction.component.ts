import {
    AfterViewInit,
    Component,
    EventEmitter,
    HostListener,
    Input,
    OnChanges,
    OnDestroy,
    OnInit,
    Output,
    SimpleChanges,
    ViewChild,
    ViewEncapsulation,
    computed,
    inject,
    input,
    output,
    signal,
} from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { Observable, Subject, Subscription, of, throwError } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { ProgrammingExerciseTestCase } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { ProblemStatementAnalysis } from 'app/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { hasExerciseChanged } from 'app/exercise/util/exercise.utils';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseGradingService } from 'app/programming/manage/services/programming-exercise-grading.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { faCheckCircle, faCircleNotch, faExclamationTriangle, faSave, faSpinner } from '@fortawesome/free-solid-svg-icons';
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
import { ActivatedRoute } from '@angular/router';
import { Annotation } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { RewriteResult } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-result';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { InlineRefinementButtonComponent } from 'app/shared/monaco-editor/inline-refinement-button/inline-refinement-button.component';
import { editor } from 'monaco-editor';

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
export class ProgrammingExerciseEditableInstructionComponent implements AfterViewInit, OnChanges, OnDestroy, OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private alertService = inject(AlertService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private testCaseService = inject(ProgrammingExerciseGradingService);
    private profileService = inject(ProfileService);
    protected artemisIntelligenceService = inject(ArtemisIntelligenceService);

    participationValue: Participation | undefined;
    programmingExercise: ProgrammingExercise;

    exerciseTestCases: string[] = [];

    taskRegex = TaskAction.GLOBAL_TASK_REGEX;
    testCaseAction = new TestCaseAction();
    domainActions: TextEditorDomainAction[] = [new FormulaAction(), new TaskAction(), this.testCaseAction];

    courseId: number;
    exerciseId: number;
    irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
    hyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);
    artemisIntelligenceActions = computed(() => {
        const actions = [];
        if (this.hyperionEnabled) {
            actions.push(
                new RewriteAction(
                    this.artemisIntelligenceService,
                    RewritingVariant.PROBLEM_STATEMENT,
                    this.courseId, // Use exerciseId for Hyperion, not courseId
                    signal<RewriteResult>({ result: '', inconsistencies: undefined, suggestions: undefined, improvement: undefined }),
                ),
            );
        }
        return actions;
    });
    protected readonly isAiApplying = computed(() => this.isGeneratingOrRefining() || this.artemisIntelligenceService.isLoading());

    savingInstructions = false;
    unsavedChangesValue = false;

    testCaseSubscription: Subscription;
    forceRenderSubscription: Subscription;

    @ViewChild(MarkdownEditorMonacoComponent, { static: false }) markdownEditorMonaco?: MarkdownEditorMonacoComponent;

    @Input() showStatus = true;
    // If the programming exercise is being created, some features have to be disabled (saving the problemStatement & querying test cases).
    @Input() editMode = true;
    @Input() enableResize = true;
    @Input({ required: true }) initialEditorHeight: MarkdownEditorHeight;
    /**
     * If true, the editor height is managed externally by the parent container.
     * Use this when embedding in a layout that controls height (e.g., code editor view).
     */
    @Input() externalHeight = false;
    @Input() showSaveButton = false;
    /**
     * Whether to show the preview button and default preview in the markdown editor.
     * Set to false when using an external preview component (e.g., in the code editor).
     */
    @Input() showPreview = true;
    @Input() templateParticipation?: Participation;
    @Input() forceRender: Observable<void>;
    readonly consistencyIssues = input<ConsistencyIssue[]>([]);
    /** Whether any refinement is in progress (makes editor read-only) */
    readonly isGeneratingOrRefining = input<boolean>(false);

    /** Editor mode: 'normal' or 'diff' for showing diff view */
    readonly mode = input<MonacoEditorMode>('normal');

    /** Original content for diff mode */
    readonly originalContent = input<string | undefined>(undefined);

    /** Modified content for diff mode */
    readonly modifiedContent = input<string | undefined>(undefined);

    readonly renderSideBySide = input<boolean>(true);

    @Input()
    get exercise() {
        return this.programmingExercise;
    }
    @Input()
    get participation(): Participation | undefined {
        return this.participationValue;
    }

    @Output() participationChange = new EventEmitter<Participation | undefined>();
    @Output() hasUnsavedChanges = new EventEmitter<boolean>();
    @Output() exerciseChange = new EventEmitter<ProgrammingExercise>();
    @Output() instructionChange = new EventEmitter<string>();
    generateHtmlSubject: Subject<void> = new Subject<void>();

    inlineRefinementPosition = signal<{ top: number; left: number } | undefined>(undefined);
    selectedTextForRefinement = signal('');
    selectionPositionInfo = signal<{ startLine: number; endLine: number; startColumn: number; endColumn: number } | undefined>(undefined);
    readonly onInlineRefinement = output<{
        instruction: string;
        startLine: number;
        endLine: number;
        startColumn: number;
        endColumn: number;
    }>();

    /** Emits diff line change information when in diff mode */
    readonly diffLineChange = output<{ ready: boolean; lineChange: LineChange }>();

    set participation(participation: Participation | undefined) {
        this.participationValue = participation;
        this.participationChange.emit(this.participationValue);
    }

    set exercise(exercise: ProgrammingExercise) {
        if (this.programmingExercise && exercise.problemStatement !== this.programmingExercise.problemStatement) {
            this.unsavedChanges = true;
        }
        this.programmingExercise = exercise;
        this.exerciseChange.emit(this.programmingExercise);
    }

    set unsavedChanges(hasChanges: boolean) {
        this.unsavedChangesValue = hasChanges;
        if (hasChanges) {
            this.hasUnsavedChanges.emit(hasChanges);
        }
    }

    // Icons
    faSave = faSave;
    faCheckCircle = faCheckCircle;
    faExclamationTriangle = faExclamationTriangle;
    faCircleNotch = faCircleNotch;
    faSpinner = faSpinner;

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;

    ngOnInit() {
        this.courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        this.exerciseId = Number(this.activatedRoute.snapshot.paramMap.get('exerciseId'));
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (hasExerciseChanged(changes)) {
            this.setupTestCaseSubscription();
        }
    }

    ngOnDestroy(): void {
        if (this.testCaseSubscription) {
            this.testCaseSubscription.unsubscribe();
        }
        if (this.forceRenderSubscription) {
            this.forceRenderSubscription.unsubscribe();
        }
    }

    ngAfterViewInit() {
        // If forced to render, generate the instruction HTML.
        if (this.forceRender) {
            this.forceRenderSubscription = this.forceRender.subscribe(() => this.generateHtml());
        }

        // Trigger initial preview render after view initialization.
        // This ensures the ProgrammingExerciseInstructionComponent renders when first shown.
        if (this.showPreview) {
            // Small delay to allow the instruction component to initialize
            setTimeout(() => this.generateHtmlSubject.next(), 0);
        }
    }

    /** Save the problem statement on the server.
     * @param event
     **/
    saveInstructions(event: any) {
        event.stopPropagation();
        this.savingInstructions = true;
        const problemStatementToSave = this.exercise.problemStatement?.trim() || undefined;
        return this.programmingExerciseService
            .updateProblemStatement(this.exercise.id!, problemStatementToSave)
            .pipe(
                tap(() => {
                    this.unsavedChanges = false;
                }),
                catchError(() => {
                    // TODO: move to programming exercise translations
                    this.alertService.error(`artemisApp.editor.errors.problemStatementCouldNotBeUpdated`);
                    return of(undefined);
                }),
            )
            .subscribe(() => {
                this.savingInstructions = false;
            });
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
        if (this.exercise.problemStatement !== problemStatement) {
            this.exercise = { ...this.exercise, problemStatement };
            this.unsavedChanges = true;
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
        if (this.editMode) {
            this.testCaseSubscription = this.testCaseService
                .subscribeForTestCases(this.exercise.id!)
                .pipe(
                    switchMap((testCases: ProgrammingExerciseTestCase[] | undefined) => {
                        // If there are test cases, map them to their names, sort them and use them for the markdown editor.
                        if (testCases) {
                            const sortedTestCaseNames = testCases
                                .filter((testCase) => testCase.active)
                                .map((testCase) => testCase.testName!)
                                .sort();
                            return of(sortedTestCaseNames);
                        } else if (this.exercise.templateParticipation) {
                            // Legacy case: If there are no test cases, but a template participation, use its feedbacks for generating test names.
                            return this.loadTestCasesFromTemplateParticipationResult(this.exercise.templateParticipation!.id!);
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
            this.inlineRefinementPosition.set(selection.screenPosition);
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
    onInlineRefine(event: { instruction: string; startLine: number; endLine: number; startColumn: number; endColumn: number }): void {
        this.onInlineRefinement.emit(event);
        this.hideInlineRefinementButton();
    }

    /**
     * Applies the refined content to the editor in diff mode.
     * @param refined The new content to show in the modified editor.
     */
    applyRefinedContent(refined: string): void {
        this.markdownEditorMonaco?.applyRefinedContent(refined);
        this.updateProblemStatement(refined);
    }

    /**
     * Reverts all changes in the diff editor (both inline edits and the refinement itself)
     * by restoring the snapshot taken when diff mode was entered.
     */
    revertAll(): void {
        this.markdownEditorMonaco?.revertAll();
        const currentContent = this.markdownEditorMonaco?.monacoEditor?.getText();
        if (currentContent !== undefined) {
            this.updateProblemStatement(currentContent);
        }
    }
}
