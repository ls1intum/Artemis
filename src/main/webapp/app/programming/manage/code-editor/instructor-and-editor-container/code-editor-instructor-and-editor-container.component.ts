import { AfterViewChecked, Component, OnDestroy, ViewChild, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { CodeEditorInstructorBaseContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-base-container.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { faBan, faCircleNotch, faPaperPlane, faPlus, faSave, faSpinner, faTimes, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from '../../status/programming-exercise-instructor-exercise-status.component';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CodeGenerationRequestDTO } from 'app/openapi/model/codeGenerationRequestDTO';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { facArtemisIntelligence } from 'app/shared/icons/icons';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { HyperionWebsocketService } from 'app/hyperion/services/hyperion-websocket.service';
import { CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { Subscription, catchError, finalize, of, take } from 'rxjs';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faCheckDouble } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ConsistencyCheckError } from 'app/programming/shared/entities/consistency-check-result.model';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ProblemStatementRefinementRequest } from 'app/openapi/model/problemStatementRefinementRequest';
import { InlineComment as ApiInlineComment } from 'app/openapi/model/inlineComment';
import { MarkdownDiffEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-diff-editor-monaco.component';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { FullscreenAction } from 'app/shared/monaco-editor/model/actions/fullscreen.action';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { TaskAction } from 'app/shared/monaco-editor/model/actions/task.action';
import { TestCaseAction } from 'app/shared/monaco-editor/model/actions/test-case.action';
import { HyperionCodeGenerationApiService } from 'app/openapi/api/hyperionCodeGenerationApi.service';

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor-and-editor-container.component.html',
    styleUrl: 'code-editor-instructor-and-editor-container.scss',
    imports: [
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        CodeEditorContainerComponent,
        IncludedInScoreBadgeComponent,
        ProgrammingExerciseInstructorExerciseStatusComponent,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownButtonItem,
        NgbDropdownItem,
        NgbTooltip,
        UpdatingResultComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammingExerciseEditableInstructionComponent,
        ProgrammingExerciseInstructionComponent,
        MarkdownDiffEditorMonacoComponent,
        FormsModule,
    ],
})
export class CodeEditorInstructorAndEditorContainerComponent extends CodeEditorInstructorBaseContainerComponent implements OnDestroy, AfterViewChecked {
    @ViewChild(UpdatingResultComponent, { static: false }) resultComp: UpdatingResultComponent;
    @ViewChild(ProgrammingExerciseEditableInstructionComponent, { static: false }) editableInstructions: ProgrammingExerciseEditableInstructionComponent;
    @ViewChild('diffEditor') diffEditor?: MarkdownDiffEditorMonacoComponent;

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly MarkdownEditorHeight = MarkdownEditorHeight;
    readonly consistencyIssues = signal<ConsistencyIssue[]>([]);

    private consistencyCheckService = inject(ConsistencyCheckService);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);
    private profileService = inject(ProfileService);
    private hyperionApiService = inject(HyperionProblemStatementApiService);

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;
    faSave = faSave;
    faBan = faBan;

    faSpinner = faSpinner;
    facArtemisIntelligence = facArtemisIntelligence;

    hyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);

    protected readonly RepositoryType = RepositoryType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faCheckDouble = faCheckDouble;
    private codeGenAlertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private hyperionWs = inject(HyperionWebsocketService);
    private repoService = inject(CodeEditorRepositoryService);
    private hyperionCodeGenerationApi = inject(HyperionCodeGenerationApiService);
    isGeneratingCode = signal(false);
    private jobSubscription?: Subscription;
    private jobTimeoutHandle?: number;

    /**
     * Starts Hyperion code generation after user confirmation.
     */
    generateCode(): void {
        if (!this.exercise?.id || this.isGeneratingCode()) {
            return;
        }

        if (this.selectedRepository !== RepositoryType.TEMPLATE && this.selectedRepository !== RepositoryType.SOLUTION && this.selectedRepository !== RepositoryType.TESTS) {
            this.codeGenAlertService.addAlert({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.unsupportedRepository' });
            return;
        }
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'md' });
        modalRef.componentInstance.title = 'artemisApp.programmingExercise.codeGeneration.confirmTitle';
        modalRef.componentInstance.text = 'artemisApp.programmingExercise.codeGeneration.confirmText';
        modalRef.componentInstance.translateText = true;
        modalRef.result.then(() => this.startCodeGeneration()).catch(() => {});
    }

    /**
     * Triggers the async generation endpoint and subscribes to job updates.
     */
    private startCodeGeneration() {
        this.isGeneratingCode.set(true);
        const repositoryType = this.selectedRepository as CodeGenerationRequestDTO.RepositoryTypeEnum;
        const exerciseId = this.exercise!.id!;
        this.hyperionCodeGenerationApi.generateCode(exerciseId, { repositoryType }).subscribe({
            next: (res) => {
                if (!res?.jobId) {
                    this.isGeneratingCode.set(false);
                    this.codeGenAlertService.addAlert({
                        type: AlertType.DANGER,
                        translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                    });
                    return;
                }
                this.subscribeToJob(res.jobId);
            },
            error: (err) => {
                this.isGeneratingCode.set(false);
                this.codeGenAlertService.addAlert({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                });
            },
            complete: () => {},
        });
    }

    /**
     * Subscribes to job updates, refreshes files on updates, and stops spinner on terminal events.
     * @param jobId job identifier
     */
    private subscribeToJob(jobId: string) {
        const cleanup = () => {
            this.isGeneratingCode.set(false);
            this.hyperionWs.unsubscribeFromJob(jobId);
            this.jobSubscription?.unsubscribe();
            if (this.jobTimeoutHandle) {
                clearTimeout(this.jobTimeoutHandle);
                this.jobTimeoutHandle = undefined;
            }
        };

        this.jobSubscription = this.hyperionWs.subscribeToJob(jobId).subscribe({
            next: (event) => {
                switch (event.type) {
                    case 'STARTED':
                        // spinner already on; just log
                        break;

                    case 'PROGRESS':
                        break;

                    case 'FILE_UPDATED':
                    case 'NEW_FILE':
                        this.repoService
                            .pull()
                            .pipe(
                                take(1),
                                catchError(() => {
                                    return of(void 0);
                                }),
                            )
                            .subscribe(() => {});
                        break;

                    case 'DONE':
                        this.codeEditorContainer?.actions?.executeRefresh();
                        cleanup();
                        this.codeGenAlertService.addAlert({
                            type: event.success ? AlertType.SUCCESS : AlertType.WARNING,
                            translationKey: event.success
                                ? 'artemisApp.programmingExercise.codeGeneration.success'
                                : 'artemisApp.programmingExercise.codeGeneration.partialSuccess',
                            translationParams: { repositoryType: this.selectedRepository },
                        });
                        break;

                    case 'ERROR':
                        cleanup();
                        this.codeGenAlertService.addAlert({
                            type: AlertType.DANGER,
                            translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                            translationParams: { repositoryType: this.selectedRepository },
                        });
                        break;

                    default:
                }
            },
            error: () => {
                cleanup();
                this.codeGenAlertService.addAlert({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                    translationParams: { repositoryType: this.selectedRepository },
                });
            },
            complete: () => {
                // don't auto-stop spinner here; DONE/ERROR/timeout handle it
            },
        });

        // Safety timeout (20 minutes)
        this.jobTimeoutHandle = window.setTimeout(() => {
            if (this.isGeneratingCode()) {
                cleanup();
                this.codeGenAlertService.addAlert({
                    type: AlertType.WARNING,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.timeout',
                });
            }
        }, 1_200_000);
    }

    // Inline refinement state (selection-based refinement)
    protected isInlineRefining = signal(false);
    protected isRefining = computed(() => this.isInlineRefining());
    private currentRefinementSubscription: Subscription | undefined;

    // Diff mode properties
    showDiff = signal(false);
    originalProblemStatement = signal('');
    refinedProblemStatement = signal('');
    private diffContentSet = false;

    // Domain actions for diff editor toolbar
    private readonly testCaseAction: TextEditorDomainAction = new TestCaseAction();
    domainActions: TextEditorDomainAction[] = [new FormulaAction(), new TaskAction(), this.testCaseAction];
    metaActions: TextEditorAction[] = [new FullscreenAction()];

    // Full problem statement refinement prompt state
    showRefinementPrompt = signal(false);
    refinementPrompt = '';
    protected readonly faPaperPlane = faPaperPlane;

    /**
     * Lifecycle hook called after every check of the component's view.
     * Used to set diff editor content when it becomes available.
     */
    constructor() {
        super();
    }

    /**
     * Sets diff editor content when it becomes available.
     * ngAfterViewChecked is used because @ViewChild is not available in effect().
     */
    ngAfterViewChecked(): void {
        if (this.showDiff() && this.diffEditor && !this.diffContentSet) {
            this.diffEditor.setFileContents(this.originalProblemStatement(), this.refinedProblemStatement(), 'original.md', 'refined.md');
            this.diffContentSet = true;
        }
    }

    /**
     * Override applyDomainChange to initialize inline comment service after exercise is loaded.
     */
    protected override applyDomainChange(domainType: any, domainValue: any): void {
        super.applyDomainChange(domainType, domainValue);
    }

    override ngOnDestroy(): void {
        super.ngOnDestroy();
        this.currentRefinementSubscription?.unsubscribe();
    }

    /**
     * Checks whether a consistency check operation is currently running.
     *
     * @returns {boolean} `true` if either the rewrite or consistency check process is currently loading; otherwise `false`.
     */
    isCheckingConsistency(): boolean {
        return this.artemisIntelligenceService.isLoading();
    }

    /**
     * Runs a consistency check for the given programming exercise.
     *
     * First verifies that all required repositories are set up correctly.
     * If no setup issues are found, performs a full content consistency check.
     * Displays alerts for errors, warnings, or successful results.
     *
     * @param {ProgrammingExercise} exercise - The exercise to check.
     */
    checkConsistencies(exercise: ProgrammingExercise) {
        // Clear previous consistency issues
        this.consistencyIssues.set([]);

        if (!exercise.id) {
            this.alertService.error(this.translateService.instant('artemisApp.consistencyCheck.checkFailedAlert'));
            return;
        }

        this.consistencyCheckService.checkConsistencyForProgrammingExercise(exercise.id!).subscribe({
            // This first consistency check ensures, that the exercise has all repositories set up
            // This does not yet check the actual content of the exercise
            next: (inconsistencies: ConsistencyCheckError[]) => {
                if (inconsistencies.length > 0) {
                    for (const inconsistency of inconsistencies) {
                        this.alertService.error(this.translateService.instant(`artemisApp.consistencyCheck.error.${inconsistency.type}`));
                    }
                    return;
                }

                // Now the content is checked
                this.artemisIntelligenceService.consistencyCheck(exercise.id!).subscribe({
                    next: (response: ConsistencyCheckResponse) => {
                        this.consistencyIssues.set(response.issues ?? []);

                        if (this.consistencyIssues().length === 0) {
                            this.alertService.success(this.translateService.instant('artemisApp.consistencyCheck.noInconsistencies'));
                        } else {
                            this.alertService.warning(this.translateService.instant('artemisApp.consistencyCheck.inconsistenciesFoundAlert'));
                        }
                    },
                    error: () => {
                        this.alertService.error(this.translateService.instant('artemisApp.consistencyCheck.checkFailedAlert'));
                    },
                });
            },
            error: () => {
                this.alertService.error(this.translateService.instant('artemisApp.consistencyCheck.checkFailedAlert'));
            },
        });
    }

    // Diff Editor Methods

    /**
     * Accepts the refined problem statement and applies the changes.
     */
    acceptRefinement(): void {
        const refined = this.refinedProblemStatement();
        if (refined?.trim()) {
            this.exercise.problemStatement = refined;
            this.editableInstructions?.updateProblemStatement(refined);
            this.closeDiff();
            this.alertService.success('artemisApp.programmingExercise.problemStatement.changesApplied');
        }
    }

    /**
     * Rejects the refined problem statement and keeps the original.
     */
    rejectRefinement(): void {
        this.closeDiff();
    }

    /**
     * Closes the diff view and resets diff state.
     */
    closeDiff(): void {
        this.showDiff.set(false);
        this.originalProblemStatement.set('');
        this.refinedProblemStatement.set('');
        this.diffContentSet = false;
    }

    /**
     * Handles inline refinement request from editor selection.
     * Calls the Hyperion API with the selected text and instruction, then shows diff.
     */
    onInlineRefinement(event: { selectedText: string; instruction: string }): void {
        const courseId = this.exercise?.course?.id ?? this.exercise?.exerciseGroup?.exam?.course?.id;

        if (!courseId || !this.exercise?.problemStatement?.trim()) {
            this.alertService.error('artemisApp.programmingExercise.inlineComment.applyError');
            return;
        }

        // Find the line range of the selected text
        const lines = this.exercise.problemStatement.split('\n');
        let startLine = 1;
        let endLine = lines.length;
        let currentPos = 0;
        for (let i = 0; i < lines.length; i++) {
            if (currentPos + lines[i].length >= this.exercise.problemStatement.indexOf(event.selectedText)) {
                startLine = i + 1;
                break;
            }
            currentPos += lines[i].length + 1;
        }
        // Find end line
        const selectedLines = event.selectedText.split('\n').length;
        endLine = startLine + selectedLines - 1;

        this.isInlineRefining.set(true);

        const apiComment: ApiInlineComment = {
            startLine,
            endLine,
            instruction: event.instruction,
        };

        const request: ProblemStatementRefinementRequest = {
            problemStatementText: this.exercise.problemStatement,
            inlineComments: [apiComment],
        };

        this.currentRefinementSubscription = this.hyperionApiService
            .refineProblemStatement(courseId, request)
            .pipe(
                finalize(() => {
                    this.isInlineRefining.set(false);
                    this.currentRefinementSubscription = undefined;
                }),
            )
            .subscribe({
                next: (response) => {
                    if (response.refinedProblemStatement && response.refinedProblemStatement.trim() !== '') {
                        // Store original and refined content for diff view
                        this.originalProblemStatement.set(this.exercise.problemStatement || '');
                        this.refinedProblemStatement.set(response.refinedProblemStatement);
                        this.diffContentSet = false;
                        this.showDiff.set(true);
                        this.alertService.success('artemisApp.programmingExercise.inlineRefine.success');
                    } else {
                        this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
                    }
                },
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
                },
            });
    }

    /**
     * Toggles the refinement prompt visibility.
     */
    toggleRefinementPrompt(): void {
        this.showRefinementPrompt.update((value) => !value);
        if (!this.showRefinementPrompt()) {
            this.refinementPrompt = '';
        }
    }

    /**
     * Submits the full problem statement refinement.
     * Uses the user prompt to refine the entire problem statement.
     */
    submitRefinement(): void {
        const prompt = this.refinementPrompt.trim();
        if (!prompt) return;

        const courseId = this.exercise?.course?.id ?? this.exercise?.exerciseGroup?.exam?.course?.id;
        if (!courseId || !this.exercise?.problemStatement?.trim()) {
            this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
            return;
        }

        this.isInlineRefining.set(true);
        this.showRefinementPrompt.set(false);

        const request: ProblemStatementRefinementRequest = {
            problemStatementText: this.exercise.problemStatement,
            userPrompt: prompt,
            inlineComments: [],
        };

        this.currentRefinementSubscription = this.hyperionApiService
            .refineProblemStatement(courseId, request)
            .pipe(
                finalize(() => {
                    this.isInlineRefining.set(false);
                    this.refinementPrompt = '';
                    this.currentRefinementSubscription = undefined;
                }),
            )
            .subscribe({
                next: (response) => {
                    if (response.refinedProblemStatement && response.refinedProblemStatement.trim() !== '') {
                        this.originalProblemStatement.set(this.exercise.problemStatement || '');
                        this.refinedProblemStatement.set(response.refinedProblemStatement);
                        this.diffContentSet = false;
                        this.showDiff.set(true);
                        this.alertService.success('artemisApp.programmingExercise.inlineRefine.success');
                    } else {
                        this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
                    }
                },
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
                },
            });
    }
}
