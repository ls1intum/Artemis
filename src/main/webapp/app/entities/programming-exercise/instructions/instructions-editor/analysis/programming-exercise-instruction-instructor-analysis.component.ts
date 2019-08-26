import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, tap } from 'rxjs/operators';
import { compose, filter, flatten, map, reduce, uniq } from 'lodash/fp';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { matchRegexWithLineNumbers, RegExpLineNumberMatchArray } from 'app/utils/global.utils';
import {
    ProblemStatementAnalysis,
    ProgrammingExerciseInstructionAnalysisService,
} from 'app/entities/programming-exercise/instructions/instructions-editor/analysis/programming-exercise-instruction-analysis.service';

@Component({
    selector: 'jhi-programming-exercise-instruction-instructor-analysis',
    templateUrl: './programming-exercise-instruction-instructor-analysis.component.html',
})
export class ProgrammingExerciseInstructionInstructorAnalysisComponent implements OnInit, OnChanges, OnDestroy {
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

    ngOnInit(): void {
        this.analysisSubscription = this.delayedAnalysisSubject
            .pipe(
                debounceTime(500),
                tap((analysis: ProblemStatementAnalysis) => this.emitAnalysis(analysis)),
            )
            .subscribe();
    }

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
        const { completeAnalysis, missingTestCases, invalidTestCases, invalidHints } = this.analysisService.analyzeProblemStatement(
            this.problemStatement,
            this.taskRegex,
            this.exerciseTestCases,
            this.exerciseHints,
        );
        this.missingTestCases = missingTestCases;
        this.invalidTestCases = invalidTestCases;
        this.invalidHints = invalidHints;
        this.delayedAnalysisSubject.next(completeAnalysis);
    }

    private emitAnalysis(analysis: ProblemStatementAnalysis) {
        this.problemStatementAnalysis.emit(analysis);
    }
}
