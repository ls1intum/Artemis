import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, tap } from 'rxjs/operators';
import { compose, filter, flatten, map, reduce, uniq } from 'lodash/fp';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { matchRegexWithLineNumbers, RegExpLineNumberMatchArray } from 'app/utils/global.utils';

export type ProblemStatementAnalysis = Array<{
    lineNumber: number;
    invalidTestCases?: string[];
    invalidHints?: string[];
}>;

enum ProblemStatementIssue {
    INVALID_TEST_CASES = 'invalidTestCases',
    INVALID_HINTS = 'invalidHints',
}

type AnalysisItem = [number, string[], ProblemStatementIssue];

@Component({
    selector: 'jhi-programming-exercise-instruction-instructor-analysis',
    templateUrl: './programming-exercise-instruction-instructor-analysis.component.html',
})
export class ProgrammingExerciseInstructionInstructorAnalysisComponent implements OnInit, OnChanges, OnDestroy {
    TEST_CASE_REGEX = new RegExp('.*\\((.*)\\)');
    HINT_REGEX = new RegExp('.*{(.*)}');
    INVALID_TEST_CASE_TRANSLATION = 'artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase';
    INVALID_HINT_TRANSLATION = 'artemisApp.programmingExercise.hintsAnalysis.invalidHint';

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

    constructor(private translateService: TranslateService) {}

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
        // Look for task regex matches in the problem statement including their line numbers.
        const tasksFromProblemStatement = matchRegexWithLineNumbers(this.problemStatement, this.taskRegex);

        const invalidTestCaseAnalysis = this.analyzeTestCases(tasksFromProblemStatement);
        const invalidHintAnalysis = this.analyzeHints(tasksFromProblemStatement);

        const completeAnalysis: ProblemStatementAnalysis = this.mergeAnalysis(invalidTestCaseAnalysis, invalidHintAnalysis);
        this.delayedAnalysisSubject.next(completeAnalysis);
    }

    private emitAnalysis(analysis: ProblemStatementAnalysis) {
        this.problemStatementAnalysis.emit(analysis);
    }

    private extractRegexFromTasks(tasks: [number, string][], regex: RegExp): [number, string[]][] {
        return compose(
            map(([lineNumber, match]) => {
                const cleanedMatches = compose(
                    uniq,
                    filter(m => !!m),
                    flatten,
                )(match.split(',').map((m: string) => m.trim()));
                return [lineNumber, cleanedMatches];
            }),
            filter(([, testCases]) => !!testCases),
            map(([lineNumber, task]) => {
                const extractedValue = task.match(regex);
                return extractedValue && extractedValue.length > 1 ? [lineNumber, extractedValue[1]] : [lineNumber, null];
            }),
            filter(([, task]) => !!task),
        )(tasks) as [number, string[]][];
    }

    /**
     * Analyze the test cases for the following criteria:
     * - Are test cases in the problem statement that don't exist for the exercise?
     * - Do test cases exist for this exercise that are not part of the problem statement?
     *
     * Will also set the invalidTestCases & missingTestCases attributes of the component.
     *
     * @param tasksFromProblemStatement to check if they contain test cases.
     */
    private analyzeTestCases = (tasksFromProblemStatement: RegExpLineNumberMatchArray) => {
        // Extract the testCase list from the task matches.
        const testCasesInMarkdown = this.extractRegexFromTasks(tasksFromProblemStatement, this.TEST_CASE_REGEX);
        // Look for test cases that are not part of the test repository. Could e.g. be typos.
        const invalidTestCaseAnalysis = testCasesInMarkdown
            .map(
                ([lineNumber, testCases]) =>
                    [lineNumber, testCases.filter(testCase => !this.exerciseTestCases.includes(testCase)), ProblemStatementIssue.INVALID_TEST_CASES] as AnalysisItem,
            )
            .filter(([, testCases]) => testCases.length);
        // Look for test cases that are part of the test repository but not in the problem statement. Probably forgotten to insert.
        this.missingTestCases = this.exerciseTestCases.filter(testCase => !testCasesInMarkdown.some(([, foundTestCases]) => foundTestCases.includes(testCase)));

        this.invalidTestCases = compose(
            flatten,
            map(([, testCases]) => testCases),
        )(invalidTestCaseAnalysis);

        return invalidTestCaseAnalysis;
    };

    /**
     * Analyze the hints for the following criteria:
     * - Are hints in the problem statement that don't exist for the exercise?
     *
     * Will also set the invalidHints attribute of the component.
     *
     * @param tasksFromProblemStatement to check if they contain hints.
     */
    private analyzeHints = (tasksFromProblemStatement: RegExpLineNumberMatchArray) => {
        const hintsInMarkdown = this.extractRegexFromTasks(tasksFromProblemStatement, this.HINT_REGEX);
        const invalidHintAnalysis = hintsInMarkdown
            .map(
                ([lineNumber, hints]): AnalysisItem => [
                    lineNumber,
                    hints.filter(hint => !this.exerciseHints.some(exerciseHint => exerciseHint.id.toString(10) === hint)),
                    ProblemStatementIssue.INVALID_HINTS,
                ],
            )
            .filter(([, hints]) => !!hints.length);

        this.invalidHints = compose(
            flatten,
            map(([, testCases]) => testCases),
        )(invalidHintAnalysis);

        return invalidHintAnalysis;
    };

    /**
     * Merges multiple AnalyseItem[] into one accumulated ProblemStatementAnalysis.
     *
     * @param analysis arbitrary number of analysis objects to be merged into one.
     */
    private mergeAnalysis = (...analysis: Array<AnalysisItem[]>) => {
        return compose(
            reduce((acc, [lineNumber, values, issueType]) => {
                const lineNumberValues = acc[lineNumber];
                const issueValues = lineNumberValues ? lineNumberValues[issueType] || [] : [];
                return { ...acc, [lineNumber]: { ...lineNumberValues, [issueType]: [...issueValues, ...values] } };
            }, {}),
            map(([lineNumber, values, issueType]: AnalysisItem) => [
                lineNumber,
                values.map(id => this.translateService.instant(this.getTranslationByIssueType(issueType), { id })),
                issueType,
            ]),
            flatten,
        )(analysis);
    };

    /**
     * Matches the issueType to a translation. A given translation should have {{id}} as a parameter.
     *
     * @param issueType for which to retrieve the fitting translation.
     */
    private getTranslationByIssueType = (issueType: ProblemStatementIssue) => {
        switch (issueType) {
            case ProblemStatementIssue.INVALID_TEST_CASES:
                return this.INVALID_TEST_CASE_TRANSLATION;
            case ProblemStatementIssue.INVALID_HINTS:
                return this.INVALID_HINT_TRANSLATION;
            default:
                return '';
        }
    };
}
