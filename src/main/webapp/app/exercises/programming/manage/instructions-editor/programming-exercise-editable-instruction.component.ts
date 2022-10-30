import { AfterViewInit, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild, ViewEncapsulation } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { Interactable } from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { Observable, Subject, Subscription, of, throwError } from 'rxjs';
import { catchError, map as rxMap, switchMap, tap } from 'rxjs/operators';
import { TaskCommand } from 'app/shared/markdown-editor/domainCommands/programming-exercise/task.command';
import { TestCaseCommand } from 'app/shared/markdown-editor/domainCommands/programming-exercise/testCase.command';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ProblemStatementAnalysis } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.model';
import { Participation } from 'app/entities/participation/participation.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { hasExerciseChanged } from 'app/exercises/shared/exercise/exercise.utils';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { DomainCommand } from 'app/shared/markdown-editor/domainCommands/domainCommand';
import { ProgrammingExerciseGradingService } from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { Result } from 'app/entities/result.model';
import { faCheckCircle, faCircleNotch, faExclamationTriangle, faGripLines, faSave } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-programming-exercise-editable-instructions',
    templateUrl: './programming-exercise-editable-instruction.component.html',
    styleUrls: ['./programming-exercise-editable-instruction.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ProgrammingExerciseEditableInstructionComponent implements AfterViewInit, OnChanges, OnDestroy {
    participationValue: Participation;
    programmingExercise: ProgrammingExercise;

    exerciseTestCases: string[] = [];

    taskCommand = new TaskCommand();
    taskRegex = this.taskCommand.getTagRegex('g');
    testCaseCommand = new TestCaseCommand();
    katexCommand = new KatexCommand();
    domainCommands: DomainCommand[] = [this.katexCommand, this.taskCommand, this.testCaseCommand];

    savingInstructions = false;
    unsavedChangesValue = false;

    interactResizable: Interactable;

    testCaseSubscription: Subscription;
    forceRenderSubscription: Subscription;

    @ViewChild(MarkdownEditorComponent, { static: false }) markdownEditor: MarkdownEditorComponent;

    @Input() showStatus = true;
    // If the programming exercise is being created, some features have to be disabled (saving the problemStatement & querying test cases).
    @Input() editMode = true;
    @Input() enableResize = true;
    @Input() showSaveButton = false;
    @Input() templateParticipation: Participation;
    @Input() forceRender: Observable<void>;
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
    generateHtmlSubject: Subject<void> = new Subject<void>();

    set participation(participation: Participation) {
        this.participationValue = participation;
        this.participationChange.emit(this.participationValue);
    }

    set exercise(exercise: ProgrammingExercise) {
        if (this.programmingExercise && exercise.problemStatement !== this.programmingExercise.problemStatement) {
            this.unsavedChanges = true;
        }
        this.programmingExercise = exercise;
        this.domainCommands = [this.katexCommand, this.taskCommand, this.testCaseCommand];
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
    faGripLines = faGripLines;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private alertService: AlertService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private testCaseService: ProgrammingExerciseGradingService,
    ) {}

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
        this.interactResizable = interact('.editable-instruction-container')
            .resizable({
                // Enable resize from top edge; triggered by class rg-top
                edges: { left: false, right: false, bottom: '.rg-bottom', top: false },
                // Set min and max height
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: 0, height: 200 },
                        max: { width: 2000, height: 1200 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
                this.markdownEditor.aceEditorContainer.getEditor().resize();
            })
            .on('resizemove', function (event: any) {
                // The first child is the markdown editor.
                const target = event.target.children && event.target.children[0];
                if (target) {
                    // Update element height
                    target.style.height = event.rect.height + 'px';
                }
            });

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
        return this.programmingExerciseService
            .updateProblemStatement(this.exercise.id!, this.exercise.problemStatement!)
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

    updateProblemStatement(problemStatement: string) {
        if (this.exercise.problemStatement !== problemStatement) {
            this.exercise = { ...this.exercise, problemStatement };
            this.unsavedChanges = true;
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
                                .map((testCase) => testCase.testName)
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
                        this.testCaseCommand.setValues(this.exerciseTestCases.map((value) => ({ value, id: value })));
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
            rxMap((result) => (!result || !result.feedbacks ? throwError(() => new Error('no result available')) : result)),
            rxMap(({ feedbacks }: Result) => feedbacks!.map((feedback) => feedback.text).sort()),
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

        this.markdownEditor.aceEditorContainer.getEditor().getSession().clearAnnotations();
        // We need to wait for the annotations to be removed before we can set the new annotations.
        // Otherwise changes in the editor will trigger the update of the existing annotations.
        setTimeout(() => {
            this.markdownEditor.aceEditorContainer.getEditor().getSession().setAnnotations(lineWarnings);
        }, 0);
    };

    private mapAnalysisToWarnings = (analysis: ProblemStatementAnalysis) => {
        return Array.from(analysis.values()).flatMap(({ lineNumber, invalidTestCases }) => this.mapIssuesToAnnotations(lineNumber, invalidTestCases));
    };

    private mapIssuesToAnnotations = (lineNumber: number, invalidTestCases?: string[]) => {
        const mapIssues = (issues: string[]) => ({ row: lineNumber, column: 0, text: ' - ' + issues.join('\n - '), type: 'warning' });

        const annotations = [];
        if (invalidTestCases) {
            annotations.push(mapIssues(invalidTestCases));
        }

        return annotations;
    };
}
