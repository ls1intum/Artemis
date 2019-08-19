import { Component, Input, OnChanges, SimpleChanges, EventEmitter, Output } from '@angular/core';
import { compose, differenceWith, filter, flatten, intersectionWith, map, uniq, reduce } from 'lodash/fp';
import { unionBy as _unionBy } from 'lodash';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';

export type ProblemStatementAnalysis = Array<{
    task: string;
    invalidTestCases: string[];
    invalidHints: string[];
}>;

enum ProblemStatementIssue {
    INVALID_TEST_CASES = 'invalidTestCases',
    MISSING_TEST_CASES = 'missingTestCases',
    INVALID_HINTS = 'invalidHints',
}

type AnalysisItem = [string, string[], ProblemStatementIssue];

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
        const tasksFromProblemStatement = this.problemStatement.match(this.taskRegex) || [];
        const testCasesInMarkdown = this.extractRegexFromTasks(tasksFromProblemStatement, /.*\((.*)\)/);

        const invalidTestCaseAnalysis: AnalysisItem[] = testCasesInMarkdown
            .map(([task, testCases]) => [task, testCases.filter(testCase => !this.exerciseTestCases.includes(testCase)), ProblemStatementIssue.INVALID_TEST_CASES] as AnalysisItem)
            .filter(([, testCases]) => testCases.length);
        this.missingTestCases = this.exerciseTestCases.filter(testCase => !testCasesInMarkdown.some(([, foundTestCases]) => foundTestCases.includes(testCase)));

        const hintsInMarkdown = this.extractRegexFromTasks(tasksFromProblemStatement, /.*{(.*)}/);
        const invalidHintAnalysis: AnalysisItem[] = hintsInMarkdown
            .map(([task, hints]) => [
                task,
                hints.filter(hint => !this.exerciseHints.some(exerciseHint => exerciseHint.id.toString(10) === hint)),
                ProblemStatementIssue.INVALID_HINTS,
            ])
            .filter(([, hints]) => !!hints.length) as AnalysisItem[];

        this.invalidTestCases = compose(
            flatten,
            map(([, testCases]) => testCases),
        )(invalidTestCaseAnalysis);

        this.invalidHints = compose(
            flatten,
            map(([, testCases]) => testCases),
        )(invalidHintAnalysis);

        const completeAnalysis: ProblemStatementAnalysis = [...invalidTestCaseAnalysis, ...invalidHintAnalysis].reduce((acc, [task, values, issueType]) => {
            const taskValues = acc[task];
            const issueValues = taskValues ? taskValues[issueType] : [];
            return { ...acc, [task]: { ...taskValues, [issueType]: [...issueValues, ...values] } };
        }, {}) as ProblemStatementAnalysis;

        this.problemStatementAnalysis.emit(completeAnalysis);
    }

    private extractRegexFromTasks(tasks: string[], regex: RegExp): AnalysisItem[] {
        return compose(
            map(([task, match]) => {
                const cleanedMatches = compose(
                    uniq,
                    filter(m => !!m),
                    flatten,
                )(match.split(',').map((m: string) => m.trim()));
                return [task, cleanedMatches];
            }),
            filter(([, testCases]) => !!testCases),
            map((task: string) => {
                const extractedValue = task.match(regex);
                return extractedValue && extractedValue.length > 1 ? [task, extractedValue[1]] : [task, null];
            }),
            filter((task: string) => !!task),
        )(tasks) as AnalysisItem[];
    }
}
