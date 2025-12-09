import { AfterViewInit, Component, HostListener, Input, OnDestroy, ViewChild, ViewEncapsulation, computed, effect, inject, input, output, signal } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { Observable, Subject, Subscription, of, throwError } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { ProgrammingExerciseTestCase } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { ProblemStatementAnalysis } from 'app/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
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
import { Annotation } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { RewriteResult } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-result';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ProblemStatementSyncService } from 'app/programming/manage/services/problem-statement-sync.service';

@Component({
    selector: 'jhi-programming-exercise-editable-instructions',
    templateUrl: './programming-exercise-editable-instruction.component.html',
    styleUrls: ['./programming-exercise-editable-instruction.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [MarkdownEditorMonacoComponent, NgClass, FaIconComponent, TranslateDirective, NgbTooltip, ProgrammingExerciseInstructionAnalysisComponent, ArtemisTranslatePipe],
})
export class ProgrammingExerciseEditableInstructionComponent implements AfterViewInit, OnDestroy {
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private alertService = inject(AlertService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private testCaseService = inject(ProgrammingExerciseGradingService);
    private profileService = inject(ProfileService);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);
    private problemStatementSyncService = inject(ProblemStatementSyncService);

    // Track previous values for change detection in effect
    private previousExercise?: ProgrammingExercise;

    unsavedChangesValue = false;

    exerciseTestCases: string[] = [];

    taskRegex = TaskAction.GLOBAL_TASK_REGEX;
    testCaseAction = new TestCaseAction();
    domainActions: TextEditorDomainAction[] = [new FormulaAction(), new TaskAction(), this.testCaseAction];

    irisEnabled = this.profileService.isProfileActive(PROFILE_IRIS);
    hyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);
    artemisIntelligenceActions = computed(() => {
        const actions = [];
        const courseId = this.exercise().course?.id;
        if (this.hyperionEnabled && !!courseId) {
            actions.push(
                new RewriteAction(
                    this.artemisIntelligenceService,
                    RewritingVariant.PROBLEM_STATEMENT,
                    courseId, // Use exerciseId for Hyperion, not courseId
                    signal<RewriteResult>({ result: '', inconsistencies: undefined, suggestions: undefined, improvement: undefined }),
                ),
            );
        }
        return actions;
    });

    savingInstructions = false;

    testCaseSubscription: Subscription;
    forceRenderSubscription: Subscription;
    incomingSyncSubscription?: Subscription;

    private isApplyingRemoteUpdate = false;

    @ViewChild(MarkdownEditorMonacoComponent, { static: false }) markdownEditorMonaco?: MarkdownEditorMonacoComponent;

    @Input() showStatus = true;
    // If the programming exercise is being created, some features have to be disabled (saving the problemStatement & querying test cases).
    @Input() editMode = true;
    @Input() enableResize = true;
    @Input({ required: true }) initialEditorHeight: MarkdownEditorHeight | 'external';
    @Input() showSaveButton = false;
    @Input() forceRender: Observable<void>;

    readonly exercise = input.required<ProgrammingExercise>();
    readonly consistencyIssues = input<ConsistencyIssue[]>([]);
    readonly hasUnsavedChanges = output<boolean>();
    readonly instructionChange = output<string>();

    generateHtmlSubject: Subject<void> = new Subject<void>();

    set unsavedChanges(hasChanges: boolean) {
        this.unsavedChangesValue = hasChanges;
        if (hasChanges) {
            this.hasUnsavedChanges.emit(hasChanges);
        }
    }

    constructor() {
        // React to exercise changes
        effect(() => {
            const currentExercise = this.exercise();

            if (this.previousExercise && currentExercise.problemStatement !== this.previousExercise.problemStatement) {
                this.unsavedChanges = true;
            }

            const hasExerciseIdChanged = !this.previousExercise || this.previousExercise.id !== currentExercise.id;
            if (hasExerciseIdChanged && currentExercise.id) {
                this.initializeProblemStatementSync(currentExercise.id, currentExercise.problemStatement);
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
        if (this.forceRender) {
            this.forceRenderSubscription = this.forceRender.subscribe(() => this.generateHtml());
        }
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
        if (this.exercise().problemStatement !== problemStatement) {
            this.unsavedChanges = true;
        }
        if (!this.isApplyingRemoteUpdate) {
            this.problemStatementSyncService.queueLocalChange(problemStatement);
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

    private initializeProblemStatementSync(exerciseId: number, initialContent?: string) {
        if (!this.editMode) {
            return;
        }
        this.teardownProblemStatementSync();
        this.incomingSyncSubscription = this.problemStatementSyncService.patchedContentObserable$.subscribe((updatedContent) =>
            this.applyRemoteProblemStatementUpdate(updatedContent),
        );
        this.problemStatementSyncService.init(exerciseId, initialContent ?? this.exercise()?.problemStatement ?? '');
    }

    private teardownProblemStatementSync() {
        this.incomingSyncSubscription?.unsubscribe();
        this.incomingSyncSubscription = undefined;
        this.problemStatementSyncService.dispose();
    }

    private applyRemoteProblemStatementUpdate(updatedContent: string) {
        this.isApplyingRemoteUpdate = true;
        this.unsavedChanges = true;
        this.instructionChange.emit(updatedContent);
        this.isApplyingRemoteUpdate = false;
    }
}
