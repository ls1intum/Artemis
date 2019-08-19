import { Component, Input, OnChanges, SimpleChanges, EventEmitter, Output } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { compose, differenceWith, filter, flatten, intersectionWith, map, uniq, reduce } from 'lodash/fp';
import { unionBy as _unionBy } from 'lodash';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';

export type ProblemStatementAnalysis = Array<{
    lineNumber: number;
    invalidTestCases?: string[];
    invalidHints?: string[];
}>;

enum ProblemStatementIssue {
    INVALID_TEST_CASES = 'invalidTestCases',
    MISSING_TEST_CASES = 'missingTestCases',
    INVALID_HINTS = 'invalidHints',
}

type AnalysisItem = [number, string[], ProblemStatementIssue];

@Component({
    selector: 'jhi-programming-exercise-instruction-instructor-analysis',
    templateUrl: './programming-exercise-instruction-instructor-analysis.component.html',
})
export class ProgrammingExerciseInstructionInstructorAnalysisComponent implements OnChanges {
    @Input() exerciseTestCases: string[];
    @Input() exerciseHints: ExerciseHint[];
    @Input() problemStatement: string;
    @Input() taskRegex: RegExp;

    @Output() problemStatementAnalysis = new EventEmitter<ProblemStatementAnalysis>();

    invalidTestCases: string[] = [];
    missingTestCases: string[] = [];

    invalidHints: string[] = [];

    constructor(private translateService: TranslateService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if ((changes.problemStatement || changes.exerciseTestCases) && this.exerciseTestCases && this.exerciseHints && this.problemStatement && this.taskRegex) {
            this.analyzeTasks();
        }
    }

    /**
     * Checks if test cases are used in the right way in the problem statement.
     * This includes two possible errors:
     * - having invalid test cases (that are not part of the test files)
     * - not using existing test cases in the markup
     * The method makes sure to filter out duplicates in the test case list.
     */
    analyzeTasks() {
        const tasksFromProblemStatement: [number, string][] = [];
        let match = this.taskRegex.exec(this.problemStatement);
        while (match) {
            const lineNumber = this.problemStatement.substring(0, match.index + match[1].length + 1).split('\n').length;
            tasksFromProblemStatement.push([lineNumber, match[1]]);
            match = this.taskRegex.exec(this.problemStatement);
        }
        const testCasesInMarkdown = this.extractRegexFromTasks(tasksFromProblemStatement, /.*\((.*)\)/);

        const invalidTestCaseAnalysis = testCasesInMarkdown
            .map(
                ([lineNumber, testCases]) =>
                    [lineNumber, testCases.filter(testCase => !this.exerciseTestCases.includes(testCase)), ProblemStatementIssue.INVALID_TEST_CASES] as AnalysisItem,
            )
            .filter(([, testCases]) => testCases.length);
        this.missingTestCases = this.exerciseTestCases.filter(testCase => !testCasesInMarkdown.some(([, foundTestCases]) => foundTestCases.includes(testCase)));

        const hintsInMarkdown = this.extractRegexFromTasks(tasksFromProblemStatement, /.*{(.*)}/);
        const invalidHintAnalysis = hintsInMarkdown
            .map(
                ([lineNumber, hints]): AnalysisItem => [
                    lineNumber,
                    hints.filter(hint => !this.exerciseHints.some(exerciseHint => exerciseHint.id.toString(10) === hint)),
                    ProblemStatementIssue.INVALID_HINTS,
                ],
            )
            .filter(([, hints]) => !!hints.length);

        this.invalidTestCases = compose(
            flatten,
            map(([, testCases]) => testCases),
        )(invalidTestCaseAnalysis);

        this.invalidHints = compose(
            flatten,
            map(([, testCases]) => testCases),
        )(invalidHintAnalysis);

        const completeAnalysis: ProblemStatementAnalysis = [
            ...(invalidTestCaseAnalysis.map(([lineNumber, values, issueType]) => [
                lineNumber,
                values.map(name => this.translateService.instant('artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase', { name })),
                issueType,
            ]) as AnalysisItem[]),
            ...(invalidHintAnalysis.map(([lineNumber, values, issueType]) => [
                lineNumber,
                values.map(id => this.translateService.instant('artemisApp.programmingExercise.hintsAnalysis.invalidHint', { id })),
                issueType,
            ]) as AnalysisItem[]),
        ].reduce((acc, [lineNumber, values, issueType]) => {
            const lineNumberValues = acc[lineNumber];
            const issueValues = lineNumberValues ? lineNumberValues[issueType] || [] : [];
            return { ...acc, [lineNumber]: { ...lineNumberValues, [issueType]: [...issueValues, ...values] } };
        }, {}) as ProblemStatementAnalysis;

        this.problemStatementAnalysis.emit(completeAnalysis);
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
}
