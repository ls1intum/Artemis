import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, inject } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, map, tap } from 'rxjs/operators';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.service';
import { ProblemStatementAnalysis } from 'app/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.model';
import { faCheckCircle, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TaskCountWarningComponent } from './task-count-warning/task-count-warning.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-programming-exercise-instruction-instructor-analysis',
    templateUrl: './programming-exercise-instruction-analysis.component.html',
    imports: [FaIconComponent, NgbTooltip, TranslateDirective, TaskCountWarningComponent, ArtemisTranslatePipe],
})
export class ProgrammingExerciseInstructionAnalysisComponent implements OnInit, OnChanges, OnDestroy {
    private analysisService = inject(ProgrammingExerciseInstructionAnalysisService);

    @Input() exerciseTestCases: string[];
    @Input() problemStatement: string;
    @Input() taskRegex: RegExp;

    @Output() problemStatementAnalysis = new EventEmitter<ProblemStatementAnalysis>();
    // Subject now carries the problem statement for distinctUntilChanged comparison
    delayedAnalysisSubject = new Subject<string>();
    analysisSubscription: Subscription;

    invalidTestCases: string[] = [];
    missingTestCases: string[] = [];
    repeatedTestCases: string[] = [];
    numOfTasks = 0;

    // Icons
    faCheckCircle = faCheckCircle;
    faExclamationTriangle = faExclamationTriangle;

    ngOnInit(): void {
        this.analysisSubscription = this.delayedAnalysisSubject
            .pipe(
                debounceTime(500),
                // Skip analysis if problem statement hasn't changed (optimization for rapid edits)
                distinctUntilChanged(),
                map(() => {
                    const { completeAnalysis, missingTestCases, invalidTestCases, repeatedTestCases, numOfTasks } = this.analysisService.analyzeProblemStatement(
                        this.problemStatement,
                        this.taskRegex,
                        this.exerciseTestCases,
                    );
                    this.missingTestCases = missingTestCases;
                    this.invalidTestCases = invalidTestCases;
                    this.repeatedTestCases = repeatedTestCases;
                    this.numOfTasks = numOfTasks;
                    return completeAnalysis;
                }),
                tap((analysis: ProblemStatementAnalysis) => this.emitAnalysis(analysis)),
            )
            .subscribe();
        this.analyzeTasks();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.problemStatement || changes.exerciseTestCases) {
            this.analyzeTasks();
        }
    }

    ngOnDestroy(): void {
        this.analysisSubscription.unsubscribe();
    }

    /**
     * Checks if test cases are used in the right way in the problem statement.
     * This includes three possible errors:
     * - having invalid test cases (that are not part of the test files)
     * - not using existing test cases in the markup
     * - having repeated test cases
     * The method makes sure to filter out duplicates in the test case list.
     */
    analyzeTasks() {
        if (this.exerciseTestCases && this.problemStatement && this.taskRegex) {
            // Pass problem statement to subject for distinctUntilChanged comparison
            this.delayedAnalysisSubject.next(this.problemStatement);
        }
    }

    private emitAnalysis(analysis: ProblemStatementAnalysis) {
        this.problemStatementAnalysis.emit(analysis);
    }
}
