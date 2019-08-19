import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { compose, difference, filter, flatten, intersection, map, uniq } from 'lodash/fp';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';

@Component({
    selector: 'jhi-programming-exercise-instruction-instructor-analysis',
    templateUrl: './programming-exercise-instruction-instructor-analysis.component.html',
})
export class ProgrammingExerciseInstructionInstructorAnalysisComponent implements OnChanges {
    @Input() exerciseTestCases: string[];
    @Input() exerciseHints: ExerciseHint[];
    @Input() problemStatement: string;
    @Input() taskRegex: RegExp;

    missingTestCases: string[] = [];
    invalidTestCases: string[] = [];

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

        this.invalidTestCases = compose(
            difference(testCasesInMarkdown),
            intersection(this.exerciseTestCases),
        )(testCasesInMarkdown);
        this.missingTestCases = difference(this.exerciseTestCases, testCasesInMarkdown);

        const hintsInMarkdown = this.extractRegexFromTasks(tasksFromProblemStatement, /.*{(.*)}/);

        this.invalidHints = compose(
            difference(hintsInMarkdown),
            intersection(this.exerciseHints.map(({ id }) => id.toString(10))),
        )(hintsInMarkdown);
    }

    private extractRegexFromTasks(tasks: string[], regex: RegExp) {
        return compose(
            uniq,
            filter(hints => !!hints),
            flatten,
            map((match: string) => match.split(',').map(string => string.trim())),
            flatten,
            filter(match => !!match),
            map((taskMatch: string) => {
                const extractedValue = taskMatch.match(regex);
                return extractedValue && extractedValue.length > 1 ? extractedValue[1] : null;
            }),
            filter((match: string) => !!match),
        )(tasks) as string[];
    }
}
