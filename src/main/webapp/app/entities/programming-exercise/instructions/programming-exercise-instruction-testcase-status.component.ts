import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { compose, difference, filter, flatten, intersection, map, uniq } from 'lodash/fp';

@Component({
    selector: 'jhi-programming-exercise-instruction-testcase-status',
    templateUrl: './programming-exercise-instruction-testcase-status.component.html',
})
export class ProgrammingExerciseInstructionTestcaseStatusComponent implements OnChanges {
    @Input()
    exerciseTestCases: string[] = [];
    @Input()
    problemStatement: string;
    @Input()
    taskRegex: RegExp;

    missingTestCases: string[] = [];
    invalidTestCases: string[] = [];

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.problemStatement || (changes.exerciseTestCases && this.problemStatement && this.taskRegex)) {
            this.analyseTestCases();
        }
    }

    /**
     * Checks if test cases are used in the right way in the problem statement.
     * This includes two possible errors:
     * - having invalid test cases (that are not part of the test files)
     * - not using existing test cases in the markup
     * The method makes sure to filter out duplicates in the test case list.
     */
    analyseTestCases() {
        const tasksFromProblemStatement = this.problemStatement.match(this.taskRegex) || [];
        const testCasesInMarkdown = compose(
            uniq,
            flatten,
            map((tests: string) => tests.split(',').map(string => string.trim())),
            flatten,
            filter(m => !!m),
            map((taskMatch: string) => {
                const testCase = taskMatch.match(/.*\((.*)\)/);
                return testCase && testCase.length > 1 ? testCase[1] : null;
            }),
            filter((match: string) => !!match),
        )(tasksFromProblemStatement) as string[];

        this.invalidTestCases = compose(
            difference(testCasesInMarkdown),
            intersection(this.exerciseTestCases),
        )(testCasesInMarkdown);
        this.missingTestCases = difference(this.exerciseTestCases, testCasesInMarkdown);
    }
}
