import { AfterViewInit, Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild, ViewEncapsulation } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import Interactable from '@interactjs/core/Interactable';
import interact from 'interactjs';
import { Observable, of, Subject, Subscription, throwError } from 'rxjs';
import { catchError, map as rxMap, switchMap, tap } from 'rxjs/operators';
import { Participation } from 'app/entities/participation';
import { compose, filter, map, sortBy } from 'lodash/fp';
import { flatten as _flatten } from 'lodash';
import { ProgrammingExercise } from '../programming-exercise.model';
import { DomainCommand } from 'app/markdown-editor/domainCommands';
import { TaskCommand } from 'app/markdown-editor/domainCommands/programming-exercise/task.command';
import { TestCaseCommand } from 'app/markdown-editor/domainCommands/programming-exercise/testCase.command';
import { MarkdownEditorComponent } from 'app/markdown-editor';
import { ProgrammingExerciseParticipationService, ProgrammingExerciseService, ProgrammingExerciseTestCaseService } from 'app/entities/programming-exercise/services';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';
import { Result } from 'app/entities/result';
import { hasExerciseChanged, problemStatementHasChanged } from 'app/entities/exercise';
import { KatexCommand } from 'app/markdown-editor/commands';
import { TaskHintCommand } from 'app/markdown-editor/domainCommands/programming-exercise/task-hint.command';
import { ExerciseHintService } from 'app/entities/exercise-hint';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { ProblemStatementAnalysis } from 'app/entities/programming-exercise';

@Component({
    selector: 'jhi-programming-exercise-editable-instructions',
    templateUrl: './programming-exercise-editable-instruction.component.html',
    styleUrls: ['./programming-exercise-editable-instruction.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ProgrammingExerciseEditableInstructionComponent implements AfterViewInit, OnChanges {
    participationValue: Participation;
    exerciseValue: ProgrammingExercise;

    exerciseTestCases: string[] = [];
    exerciseHints: ExerciseHint[];

    taskCommand = new TaskCommand();
    taskRegex = this.taskCommand.getTagRegex('g');
    testCaseCommand = new TestCaseCommand();
    taskHintCommand = new TaskHintCommand();
    katexCommand = new KatexCommand();
    domainCommands: DomainCommand[] = [this.katexCommand, this.taskCommand, this.testCaseCommand, this.taskHintCommand];

    savingInstructions = false;
    unsavedChangesValue = false;

    interactResizable: Interactable;

    testCaseSubscription: Subscription;

    @ViewChild(MarkdownEditorComponent, { static: false }) markdownEditor: MarkdownEditorComponent;

    @Input() showStatus = true;
    // If the programming exercise is being created, some features have to be disabled (saving the problemStatement & querying test cases).
    @Input() editMode = true;
    @Input() enableResize = true;
    @Input() showSaveButton = false;
    @Input() templateParticipation: Participation;
    @Input()
    get exercise() {
        return this.exerciseValue;
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
        if (this.exerciseValue && exercise.problemStatement !== this.exerciseValue.problemStatement) {
            this.unsavedChanges = true;
        }
        this.exerciseValue = exercise;
        this.exerciseChange.emit(this.exerciseValue);
    }

    set unsavedChanges(hasChanges: boolean) {
        this.unsavedChangesValue = hasChanges;
        if (hasChanges) {
            this.hasUnsavedChanges.emit(hasChanges);
        }
    }

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        private jhiAlertService: JhiAlertService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private testCaseService: ProgrammingExerciseTestCaseService,
        private exerciseHintService: ExerciseHintService,
    ) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (problemStatementHasChanged(changes)) {
            this.generateHtml();
        }
        if (hasExerciseChanged(changes)) {
            this.setupTestCaseSubscription();
            this.loadExerciseHints(this.exercise.id);
        }
    }

    ngAfterViewInit() {
        this.interactResizable = interact('.editable-instruction-container')
            .resizable({
                // Enable resize from top edge; triggered by class rg-top
                edges: { left: false, right: false, bottom: '.rg-bottom', top: false },
                // Set min and max height
                restrictSize: {
                    min: { height: 200 },
                    max: { height: 1200 },
                },
                inertia: true,
            })
            .on('resizestart', function(event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', (event: any) => {
                event.target.classList.remove('card-resizable');
                this.markdownEditor.aceEditorContainer.getEditor().resize();
            })
            .on('resizemove', function(event: any) {
                // The first child is the markdown editor.
                const target = event.target.children && event.target.children[0];
                if (target) {
                    // Update element height
                    target.style.height = event.rect.height + 'px';
                }
            });
    }

    loadExerciseHints(exerciseId: number) {
        this.exerciseHintService
            .findByExerciseId(exerciseId)
            .pipe(rxMap(({ body }) => body || []))
            .subscribe((exerciseHints: ExerciseHint[]) => {
                this.exerciseHints = exerciseHints;
                this.taskHintCommand.setValues(this.exerciseHints.map(({ id, title }) => ({ id: id.toString(10), value: title })));
            });
    }

    /* Save the problem statement on the server.
     * @param $event
     */
    saveInstructions($event: any) {
        $event.stopPropagation();
        this.savingInstructions = true;
        return this.programmingExerciseService
            .updateProblemStatement(this.exercise.id, this.exercise.problemStatement!)
            .pipe(
                tap(() => {
                    this.unsavedChanges = false;
                }),
                catchError(() => {
                    // TODO: move to programming exercise translations
                    this.jhiAlertService.error(`artemisApp.editor.errors.problemStatementCouldNotBeUpdated`);
                    return of(null);
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
                .subscribeForTestCases(this.exercise.id)
                .pipe(
                    switchMap((testCases: ProgrammingExerciseTestCase[] | null) => {
                        // If there are test cases, map them to their names, sort them and use them for the markdown editor.
                        if (testCases) {
                            const sortedTestCaseNames = compose(
                                map(({ testName }) => testName),
                                filter(({ active }) => active),
                                sortBy('testName'),
                            )(testCases);
                            return of(sortedTestCaseNames);
                        } else if (this.exercise.templateParticipation) {
                            // Legacy case: If there are no test cases, but a template participation, use its feedbacks for generating test names.
                            return this.loadTestCasesFromTemplateParticipationResult(this.exercise.templateParticipation.id);
                        }
                        return of();
                    }),
                    tap((testCaseNames: string[]) => {
                        this.exerciseTestCases = testCaseNames;
                        this.testCaseCommand.setValues(this.exerciseTestCases.map(value => ({ value, id: value })));
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
    loadTestCasesFromTemplateParticipationResult = (templateParticipationId: number): Observable<string[]> => {
        // Fallback for exercises that don't have test cases yet.
        return this.programmingExerciseParticipationService.getLatestResultWithFeedback(templateParticipationId).pipe(
            rxMap((result: Result | null) => (!result || !result.feedbacks ? throwError('no result available') : result)),
            rxMap(({ feedbacks }: Result) =>
                compose(
                    map(({ text }) => text),
                    sortBy('text'),
                )(feedbacks),
            ),
            catchError(() => of([])),
        );
    };

    onAnalysisUpdate = (analysis: ProblemStatementAnalysis) => {
        const lineWarnings = _flatten(
            (Object.entries(analysis) as [string, any][]).map(([lineNumber, issues]) =>
                Object.values(issues).map((issues: string[]) => ({ row: lineNumber, column: 0, text: ' - ' + issues.join('\n - '), type: 'warning' })),
            ),
        );

        this.markdownEditor.aceEditorContainer
            .getEditor()
            .getSession()
            .clearAnnotations();
        setTimeout(() => {
            this.markdownEditor.aceEditorContainer
                .getEditor()
                .getSession()
                .setAnnotations(lineWarnings);
        }, 0);
    };
}
