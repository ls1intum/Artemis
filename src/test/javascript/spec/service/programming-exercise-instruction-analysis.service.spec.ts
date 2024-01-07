import { problemStatement } from '../helpers/sample/problemStatement.json';
import { problemStatementRepeatedTestCases } from '../helpers/sample/problemStatement.json';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.service';

describe('ProgrammingExerciseInstructionAnalysisService', () => {
    const taskRegex = /\[task\](.*)/g;

    let analysisService: ProgrammingExerciseInstructionAnalysisService;

    beforeEach(() => {
        // @ts-ignore
        analysisService = new ProgrammingExerciseInstructionAnalysisService(new MockTranslateService());
    });

    it('should analyse problem statement without any issues correctly', () => {
        const testCases = ['testMergeSort', 'testBubbleSort'];
        const { invalidTestCases, missingTestCases, repeatedTestCases, completeAnalysis } = analysisService.analyzeProblemStatement(problemStatement, taskRegex, testCases);

        expect(invalidTestCases).toHaveLength(0);
        expect(missingTestCases).toHaveLength(0);
        expect(repeatedTestCases).toHaveLength(0);
        expect(completeAnalysis).toEqual(new Map());
    });

    it('should analyse problem statement with issues correctly', () => {
        const testCases = ['testBubbleSortNew']; // test name was changed, the new test name is missing.
        const expectedAnalysis = new Map();
        expectedAnalysis.set(0, { lineNumber: 0, invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'] });
        expectedAnalysis.set(2, {
            lineNumber: 2,
            invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'],
        });

        const { invalidTestCases, missingTestCases, repeatedTestCases, completeAnalysis } = analysisService.analyzeProblemStatement(problemStatement, taskRegex, testCases);

        expect(invalidTestCases).toEqual(['testBubbleSort', 'testMergeSort']);
        expect(missingTestCases).toEqual(['testBubbleSortNew']);
        expect(repeatedTestCases).toHaveLength(0);
        expect(completeAnalysis).toEqual(expectedAnalysis);
    });

    it('should analyse problem statement with repeated test cases', () => {
        const testCases = ['testBubbleSort'];
        const expectedAnalysis = new Map();
        expectedAnalysis.set(0, { lineNumber: 0, repeatedTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.repeatedTestCase'] });
        expectedAnalysis.set(2, { lineNumber: 2, repeatedTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.repeatedTestCase'] });

        const { invalidTestCases, missingTestCases, repeatedTestCases, completeAnalysis } = analysisService.analyzeProblemStatement(
            problemStatementRepeatedTestCases,
            taskRegex,
            testCases,
        );

        expect(invalidTestCases).toHaveLength(0);
        expect(missingTestCases).toHaveLength(0);
        expect(repeatedTestCases).toEqual(['testBubbleSort']);
        expect(completeAnalysis).toEqual(expectedAnalysis);
    });
});
