import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, map as rxMap, tap } from 'rxjs/operators';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.service';
import { ProblemStatementAnalysis } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.model';

@Component({
    selector: 'jhi-programming-exercise-instruction-instructor-analysis',
    templateUrl: './programming-exercise-instruction-analysis.component.html',
})
export class ProgrammingExerciseInstructionAnalysisComponent implements OnInit, OnChanges, OnDestroy {
    @Input() exerciseTestCases: string[];
    @Input() exerciseHints: ExerciseHint[];
    @Input() problemStatement: string;
    @Input() taskRegex: RegExp;

    @Output() problemStatementAnalysis = new EventEmitter<ProblemStatementAnalysis>();
    delayedAnalysisSubject = new Subject<ProblemStatementAnalysis>();
    analysisSubscription: Subscription;

    invalidTestCases: string[] = [];
    missingTestCases: string[] = [];

    invalidHints: string[] = [];

    constructor(private analysisService: ProgrammingExerciseInstructionAnalysisService) {}

    /**
     * On component initialization, gets the singular analysis for test cases / hints
     * and an accumulated analysis object from the analysis service. Updates the variables
     * of this instance accordingly and sets the analysis subscription to the complete analysis.
     * Also emits the analysis.
     */
    ngOnInit(): void {
        this.analysisSubscription = this.delayedAnalysisSubject
            .pipe(
                debounceTime(500),
                rxMap(() => {
                    const { completeAnalysis, missingTestCases, invalidTestCases, invalidHints } = this.analysisService.analyzeProblemStatement(
                        this.problemStatement,
                        this.taskRegex,
                        this.exerciseTestCases,
                        this.exerciseHints,
                    );
                    this.missingTestCases = missingTestCases;
                    this.invalidTestCases = invalidTestCases;
                    this.invalidHints = invalidHints;
                    return completeAnalysis;
                }),
                tap((analysis: ProblemStatementAnalysis) => this.emitAnalysis(analysis)),
            )
            .subscribe();
    }

    /**
     * If the inputs of this components are initialized AND
     * changes are made to the problem statement or test cases or hints
     * calls analyze tasks
     * @param {SimpleChanges} changes - Changes made
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (
            (changes.problemStatement || changes.exerciseTestCases || changes.exerciseHints) &&
            this.exerciseTestCases &&
            this.exerciseHints &&
            this.problemStatement &&
            this.taskRegex
        ) {
            this.analyzeTasks();
        }
    }

    /**
     * Ends the subscription on the analysisSubscription object on component destruction
     */
    ngOnDestroy(): void {
        this.analysisSubscription.unsubscribe();
    }

    /**
     * Checks if test cases are used in the right way in the problem statement.
     * This includes two possible errors:
     * - having invalid test cases (that are not part of the test files)
     * - not using existing test cases in the markup
     * The method makes sure to filter out duplicates in the test case list.
     */
    analyzeTasks() {
        this.delayedAnalysisSubject.next();
    }

    private emitAnalysis(analysis: ProblemStatementAnalysis) {
        this.problemStatementAnalysis.emit(analysis);
    }
}
