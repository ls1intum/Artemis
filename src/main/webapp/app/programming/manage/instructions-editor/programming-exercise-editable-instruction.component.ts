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
    effect,
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
import { faCheckCircle, faCircleNotch, faExclamationTriangle, faSave } from '@fortawesome/free-solid-svg-icons';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
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
import { RewriteAction } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewrite.action';
import { MODULE_FEATURE_HYPERION, PROFILE_IRIS } from 'app/app.constants';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ActivatedRoute } from '@angular/router';
import { Annotation } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { RewriteResult } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-result';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { InlineCommentHostService } from 'app/shared/monaco-editor/service/inline-comment-host.service';
import { InlineComment } from 'app/shared/monaco-editor/model/inline-comment.model';

@Component({
    selector: 'jhi-programming-exercise-editable-instructions',
    templateUrl: './programming-exercise-editable-instruction.component.html',
    styleUrls: ['./programming-exercise-editable-instruction.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [MarkdownEditorMonacoComponent, NgClass, FaIconComponent, TranslateDirective, NgbTooltip, ProgrammingExerciseInstructionAnalysisComponent, ArtemisTranslatePipe],
})
export class ProgrammingExerciseEditableInstructionComponent implements AfterViewInit, OnChanges, OnDestroy, OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private alertService = inject(AlertService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private testCaseService = inject(ProgrammingExerciseGradingService);
    private profileService = inject(ProfileService);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);
    private inlineCommentHostService = inject(InlineCommentHostService);

    participationValue: Participation;
    programmingExercise: ProgrammingExercise;

    exerciseTestCases: string[] = [];

    /** Flag to track if the editor is ready for widgets */
    private editorReady = false;

    /** Map of comment IDs to widget IDs for tracking and updating widgets */
    private commentWidgetMap = new Map<string, string>();

    constructor() {
        // Effect to watch pendingComments and render widgets when they change
        effect(() => {
            // Subscribe to pendingComments signal - reading it triggers re-run when it changes
            this.pendingComments();
            if (this.editorReady && this.markdownEditorMonaco && this.hyperionEnabled) {
                this.renderPendingCommentsAsWidgets();
            }
        });

        // Effect to propagate isAnyApplying changes to all active widgets
        effect(() => {
            const applying = this.isAnyApplying();
            // Update all active widgets with the new applying state
            this.inlineCommentHostService.updateGlobalApplyingState(applying);
        });
    }

    taskRegex = TaskAction.GLOBAL_TASK_REGEX;
    testCaseAction = new TestCaseAction();
    domainActions: TextEditorDomainAction[] = [new FormulaAction(), new TaskAction(), this.testCaseAction];

    courseId: number;
    exerciseId: number;
    irisEnabled = this.profileService.isProfileActive(PROFILE_IRIS);
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

    savingInstructions = false;
    unsavedChangesValue = false;

    testCaseSubscription: Subscription;
    forceRenderSubscription: Subscription;

    @ViewChild(MarkdownEditorMonacoComponent, { static: false }) markdownEditorMonaco?: MarkdownEditorMonacoComponent;

    @Input() showStatus = true;
    // If the programming exercise is being created, some features have to be disabled (saving the problemStatement & querying test cases).
    @Input() editMode = true;
    @Input() enableResize = true;
    @Input({ required: true }) initialEditorHeight: MarkdownEditorHeight | 'external';
    @Input() showSaveButton = false;
    @Input() templateParticipation: Participation;
    @Input() forceRender: Observable<void>;
    readonly consistencyIssues = input<ConsistencyIssue[]>([]);
    /** Pending inline comments to display in the editor */
    readonly pendingComments = input<InlineComment[]>([]);
    /** Whether any apply operation is in progress globally */
    readonly isAnyApplying = input<boolean>(false);
    /** Whether any refinement is in progress (makes editor read-only) */
    readonly isRefining = input<boolean>(false);

    @Input()
    get exercise() {
        return this.programmingExercise;
    }
    @Input()
    get participation() {
        return this.participationValue;
    }

    @Output() participationChange = new EventEmitter<Participation>();
    @Output() hasUnsavedChanges = new EventEmitter<boolean>();
    @Output() exerciseChange = new EventEmitter<ProgrammingExercise>();
    @Output() instructionChange = new EventEmitter<string>();
    /** Emits when user wants to create an inline comment for selected lines */
    readonly onCreateInlineComment = output<{ startLine: number; endLine: number }>();
    /** Emits when user saves a comment for later */
    readonly onInlineCommentSave = output<InlineComment>();
    /** Emits when user wants to apply a comment with AI */
    readonly onInlineCommentApply = output<InlineComment>();
    /** Emits when user deletes a comment */
    readonly onInlineCommentDelete = output<string>();
    /** Emits when user cancels an in-progress apply operation */
    readonly onInlineCommentCancelApply = output<void>();
    generateHtmlSubject: Subject<void> = new Subject<void>();

    // Inline comment selection state
    currentSelection: { startLine: number; endLine: number } | null = null;

    set participation(participation: Participation) {
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

    /**
     * Cleanup when component is destroyed.
     * Closes all active inline comment widgets to prevent stale state.
     */
    ngOnDestroy(): void {
        if (this.testCaseSubscription) {
            this.testCaseSubscription.unsubscribe();
        }
        // Forcefully clear all active widgets - editor may already be destroyed
        this.inlineCommentHostService.clearAllWidgets();
        this.commentWidgetMap.clear();
    }

    ngAfterViewInit() {
        // If forced to render, generate the instruction HTML.
        if (this.forceRender) {
            this.forceRenderSubscription = this.forceRender.subscribe(() => this.generateHtml());
        }

        // Set up the gutter hover button for inline comments (GitHub-style)
        if (this.markdownEditorMonaco && this.hyperionEnabled) {
            this.editorReady = true;
            this.setupGutterButton();
            // Render any existing pending comments as collapsed widgets
            this.renderPendingCommentsAsWidgets();
        }
    }

    /**
     * Sets up the gutter hover button (+) for adding inline comments.
     */
    private setupGutterButton(): void {
        this.markdownEditorMonaco?.monacoEditor?.setLineDecorationsHoverButton('inline-comment-add-gutter-button', (lineNumber: number) => this.onGutterButtonClick(lineNumber));
    }

    /**
     * Handles click on the gutter (+) button.
     * Opens inline comment widget at the clicked line.
     */
    private onGutterButtonClick(lineNumber: number): void {
        // Check if there's already a widget at this line
        if (this.inlineCommentHostService.hasWidgetAtLine(lineNumber)) {
            return;
        }

        // Use current selection if available, otherwise just the clicked line
        const selection = this.currentSelection;
        const startLine = selection?.startLine ?? lineNumber;
        const endLine = selection?.endLine ?? lineNumber;

        this.doOpenInlineCommentWidget(startLine, endLine);
    }

    /**
     * Shared helper to open an inline comment widget with consistent callbacks.
     * Used by both gutter button clicks and selection-based widget opening.
     * Returns early if editor is not initialized or Hyperion is not enabled.
     */
    private doOpenInlineCommentWidget(startLine: number, endLine: number, existingComment?: InlineComment): void {
        // Guard: ensure editor is initialized and Hyperion is enabled
        if (!this.markdownEditorMonaco || !this.hyperionEnabled) {
            return;
        }

        // Check if there's already a widget at this line range
        if (this.inlineCommentHostService.hasWidgetAtLine(startLine)) {
            return;
        }

        // Open the inline comment widget
        this.inlineCommentHostService.openWidget(
            this.markdownEditorMonaco,
            startLine,
            endLine,
            existingComment,
            {
                onSave: (comment: InlineComment) => {
                    this.onCreateInlineComment.emit({ startLine: comment.startLine, endLine: comment.endLine });
                    // Also emit the full comment for the parent to handle
                    this.onInlineCommentSave.emit(comment);
                },
                onApply: (comment: InlineComment) => {
                    this.onInlineCommentApply.emit(comment);
                },
                onCancel: () => {
                    // Clear selection
                    this.currentSelection = null;
                },
                onCancelApply: () => {
                    this.onInlineCommentCancelApply.emit();
                },
                onDelete: (commentId: string) => {
                    this.onInlineCommentDelete.emit(commentId);
                },
            },
            { globalApplying: this.isAnyApplying() },
        );

        // Clear selection after opening widget
        this.currentSelection = null;
    }

    /**
     * Renders pending comments as collapsed widgets in the editor.
     * Called on component init and when pending comments change.
     * Also handles cleanup when comments are cleared.
     */
    private renderPendingCommentsAsWidgets(): void {
        if (!this.markdownEditorMonaco || !this.hyperionEnabled) {
            return;
        }
        const comments = this.pendingComments();

        // If no pending comments (e.g., after Clear All), close all widgets
        if (comments.length === 0) {
            this.inlineCommentHostService.closeAllWidgets(this.markdownEditorMonaco);
            this.commentWidgetMap.clear();
            return;
        }

        // Build set of current comment IDs for cleanup
        const currentCommentIds = new Set(comments.map((c) => c.id));

        // Close widgets for comments that no longer exist
        for (const [commentId, widgetId] of this.commentWidgetMap.entries()) {
            if (!currentCommentIds.has(commentId)) {
                this.inlineCommentHostService.closeWidget(widgetId, this.markdownEditorMonaco);
                this.commentWidgetMap.delete(commentId);
            }
        }

        for (const comment of comments) {
            // Skip comments that are currently being applied - preserve their existing widget
            // to show the loading state, don't close or re-create it
            if (comment.status === 'applying') {
                continue;
            }

            // Skip if there's already a widget at this line (e.g., from context menu)
            if (this.inlineCommentHostService.hasWidgetAtLine(comment.startLine)) {
                continue;
            }

            // Check if widget exists for this comment in our tracking map
            const existingWidgetId = this.commentWidgetMap.get(comment.id);
            if (existingWidgetId) {
                // Widget exists - only close/refresh if status is NOT pending
                // This prevents unnecessary widget recreation for unchanged comments
                if (comment.status !== 'pending') {
                    this.inlineCommentHostService.closeWidget(existingWidgetId, this.markdownEditorMonaco);
                    this.commentWidgetMap.delete(comment.id);
                } else {
                    // Already has a widget and is pending - skip to avoid duplicate
                    continue;
                }
            }

            const widgetId = this.inlineCommentHostService.openWidget(
                this.markdownEditorMonaco,
                comment.startLine,
                comment.endLine,
                comment,
                {
                    onSave: (updatedComment: InlineComment) => {
                        this.onInlineCommentSave.emit(updatedComment);
                    },
                    onApply: (applyComment: InlineComment) => {
                        this.onInlineCommentApply.emit(applyComment);
                    },
                    onCancel: () => {
                        // Keep the comment but close widget - parent handles state
                    },
                    onCancelApply: () => {
                        this.onInlineCommentCancelApply.emit();
                    },
                    onDelete: (commentId: string) => {
                        this.onInlineCommentDelete.emit(commentId);
                    },
                },
                { collapsed: true, globalApplying: this.isAnyApplying() },
            );

            // Track the widget ID for this comment
            this.commentWidgetMap.set(comment.id, widgetId);
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
    saveOnControlAndS(event: KeyboardEvent) {
        if (!navigator.userAgent.includes('Mac')) {
            event.preventDefault();
            this.saveInstructions(event);
        }
    }

    @HostListener('document:keydown.meta.s', ['$event'])
    saveOnCommandAndS(event: KeyboardEvent) {
        if (navigator.userAgent.includes('Mac')) {
            event.preventDefault();
            this.saveInstructions(event);
        }
    }

    updateProblemStatement(problemStatement: string) {
        if (this.exercise.problemStatement !== problemStatement) {
            this.exercise = { ...this.exercise, problemStatement };
            this.unsavedChanges = true;
        }
        this.instructionChange.emit(problemStatement);
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
     */
    onEditorSelectionChange(selection: { startLine: number; endLine: number } | null): void {
        this.currentSelection = selection;
    }

    /**
     * Opens the inline comment widget for the current selection.
     * Uses the shared helper for consistent behavior with gutter button clicks.
     */
    openInlineCommentWidget(): void {
        if (this.currentSelection) {
            this.doOpenInlineCommentWidget(this.currentSelection.startLine, this.currentSelection.endLine);
        }
    }
}
