import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { uniq } from 'lodash-es';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { matchRegexWithLineNumbers, RegExpLineNumberMatchArray } from 'app/shared/util/global.utils';
import {
    AnalysisItem,
    ProblemStatementAnalysis,
    ProblemStatementIssue,
} from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.model';

/**
 * Analyzes the problem statement of a programming-exercise and provides information support concerning potential issues.
 */
@Injectable()
export class ProgrammingExerciseInstructionAnalysisService {
    private readonly TEST_CASE_REGEX = new RegExp('.*?\\((.*)\\)');
    private readonly HINT_REGEX = new RegExp('.*{(.*)}');
    private readonly INVALID_TEST_CASE_TRANSLATION = 'artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase';
    private readonly INVALID_HINT_TRANSLATION = 'artemisApp.programmingExercise.hintsAnalysis.invalidHint';

    constructor(private translateService: TranslateService) {}

    /**
     * Given a programming exercise's problem statement, analyze the test cases and hints contained (or not contained!) in it.
     * Will give out a mixed object that contains singular analysis for test cases / hints and a accumulated analysis object.
     *
     * @param problemStatement  multiline string.
     * @param taskRegex         identifies tasks in a problem statement.
     * @param exerciseTestCases used to check if a test case is valid / missing.
     * @param exerciseHints     used to check if a hint is valid.
     */
    public analyzeProblemStatement = (problemStatement: string, taskRegex: RegExp, exerciseTestCases: string[], exerciseHints: ExerciseHint[]) => {
        // Look for task regex matches in the problem statement including their line numbers.
        const tasksFromProblemStatement = matchRegexWithLineNumbers(problemStatement, taskRegex);

        const { invalidTestCases, missingTestCases, invalidTestCaseAnalysis } = this.analyzeTestCases(tasksFromProblemStatement, exerciseTestCases);
        const { invalidHints, invalidHintAnalysis } = this.analyzeHints(tasksFromProblemStatement, exerciseHints);

        const completeAnalysis: ProblemStatementAnalysis = this.mergeAnalysis(invalidTestCaseAnalysis, invalidHintAnalysis);
        return { invalidTestCases, missingTestCases, invalidHints, completeAnalysis };
    };

    /**
     * Analyze the test cases for the following criteria:
     * - Are test cases in the problem statement that don't exist for the exercise?
     * - Do test cases exist for this exercise that are not part of the problem statement?
     *
     * Will also set the invalidTestCases & missingTestCases attributes of the component.
     *
     * @param tasksFromProblemStatement to analyze.
     * @param exerciseTestCases to double check the test cases found in the problem statement.
     */
    private analyzeTestCases = (tasksFromProblemStatement: RegExpLineNumberMatchArray, exerciseTestCases: string[]) => {
        // Extract the testCase list from the task matches.
        const testCasesInMarkdown = this.extractRegexFromTasks(tasksFromProblemStatement, this.TEST_CASE_REGEX);
        // Look for test cases that are not part of the test repository. Could e.g. be typos.
        const invalidTestCaseAnalysis = testCasesInMarkdown
            .map(
                ([lineNumber, testCases]) =>
                    [
                        lineNumber,
                        testCases.filter((testCase) => !exerciseTestCases.map((exTestcase) => exTestcase.toLowerCase()).includes(testCase.toLowerCase())),
                        ProblemStatementIssue.INVALID_TEST_CASES,
                    ] as AnalysisItem,
            )
            .filter(([, testCases]) => testCases.length);
        // Look for test cases that are part of the test repository but not in the problem statement. Probably forgotten to insert.
        const missingTestCases = exerciseTestCases.filter(
            (testCase) => !testCasesInMarkdown.some(([, foundTestCases]) => foundTestCases.map((foundTestCase) => foundTestCase.toLowerCase()).includes(testCase.toLowerCase())),
        );

        const invalidTestCases = invalidTestCaseAnalysis.flatMap(([, testCases]) => testCases);

        return { missingTestCases, invalidTestCases, invalidTestCaseAnalysis };
    };

    /**
     * Analyze the hints for the following criteria:
     * - Are hints in the problem statement that don't exist for the exercise?
     *
     * Will also set the invalidHints attribute of the component.
     *
     * @param tasksFromProblemStatement to check if they contain hints.
     * @param exerciseHints to double check the exercise hints found in the problem statement.
     */
    private analyzeHints = (tasksFromProblemStatement: RegExpLineNumberMatchArray, exerciseHints: ExerciseHint[]) => {
        const hintsInMarkdown = this.extractRegexFromTasks(tasksFromProblemStatement, this.HINT_REGEX);
        const invalidHintAnalysis = hintsInMarkdown
            .map(
                ([lineNumber, hints]): AnalysisItem => [
                    lineNumber,
                    hints.filter((hint) => !exerciseHints.some((exerciseHint) => exerciseHint.id!.toString(10) === hint)),
                    ProblemStatementIssue.INVALID_HINTS,
                ],
            )
            .filter(([, hints]) => !!hints.length);

        const invalidHints = invalidHintAnalysis.flatMap(([, testCases]) => testCases);

        return { invalidHints, invalidHintAnalysis };
    };

    /**
     * Merges multiple AnalyseItem[] into one accumulated ProblemStatementAnalysis.
     *
     * @param analysis arbitrary number of analysis objects to be merged into one.
     */
    private mergeAnalysis = (...analysis: Array<AnalysisItem[]>): ProblemStatementAnalysis => {
        const reducer = (acc: ProblemStatementAnalysis, [lineNumber, values, issueType]: AnalysisItem): ProblemStatementAnalysis => {
            const lineNumberValues = acc.get(lineNumber);
            const issueValues = lineNumberValues ? lineNumberValues[issueType] || [] : [];
            acc.set(lineNumber, { lineNumber, ...lineNumberValues, [issueType]: [...issueValues, ...values] });
            return acc;
        };

        return analysis
            .flat()
            .map(([lineNumber, values, issueType]: AnalysisItem) => [
                lineNumber,
                values.map((id) => this.translateService.instant(this.getTranslationByIssueType(issueType), { id })),
                issueType,
            ])
            .reduce(reducer, new Map());
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

    /**
     * Extracts the given regex from the task.
     * Value will be null if no match is found!
     *
     * @param tasks that contain the given regex.
     * @param regex to search for in the tasks.
     */
    private extractRegexFromTasks(tasks: [number, string][], regex: RegExp): [number, string[]][] {
        const cleanMatches = (matches: string[]) => uniq(matches.flat().filter(Boolean));

        return tasks
            .filter(([, task]) => !!task)
            .map(([lineNumber, task]) => {
                const extractedValue = task.match(regex);
                return extractedValue && extractedValue.length > 1 ? [lineNumber, extractedValue[1]] : [lineNumber, undefined];
            })
            .filter(([, testCases]) => !!testCases)
            .map(([lineNumber, match]: [number, string]) => {
                const cleanedMatches = cleanMatches(match!.split(/,(?![^(]*?\))/).map((m: string) => m.trim()));
                return [lineNumber, cleanedMatches];
            }) as [number, string[]][];
    }
}
