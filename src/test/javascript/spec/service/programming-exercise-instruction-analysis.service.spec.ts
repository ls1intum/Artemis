import { problemStatement } from '../helpers/sample/problemStatement.json';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/exercises/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.service';

describe('ProgrammingExerciseInstructionAnalysisService', () => {
    const taskRegex = /\[task\](.*)/g;

    let analysisService: ProgrammingExerciseInstructionAnalysisService;

    beforeEach(() => {
        // @ts-ignore
        analysisService = new ProgrammingExerciseInstructionAnalysisService(new MockTranslateService());
    });

    it('should analyse problem statement without any issues correctly', () => {
        const exerciseHints = [{ id: 33 }, { id: 44 }] as ExerciseHint[];
        const testCases = ['testMergeSort', 'testBubbleSort'];
        const { invalidHints, invalidTestCases, missingTestCases, completeAnalysis } = analysisService.analyzeProblemStatement(
            problemStatement,
            taskRegex,
            testCases,
            exerciseHints,
        );

        expect(invalidHints).toHaveLength(0);
        expect(invalidTestCases).toHaveLength(0);
        expect(missingTestCases).toHaveLength(0);
        expect(completeAnalysis).toEqual(new Map());
    });

    it('should analyse problem statement with issues correctly', () => {
        const exerciseHints = [{ id: 33 }, { id: 45 }] as ExerciseHint[]; // Typo in hint id (44 vs 45).
        const testCases = ['testBubbleSortNew']; // test name was changed, the new test name is missing.
        const expectedAnalysis = new Map();
        expectedAnalysis.set(0, { lineNumber: 0, invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'] });
        expectedAnalysis.set(2, {
            lineNumber: 2,
            invalidHints: ['artemisApp.programmingExercise.hintsAnalysis.invalidHint'],
            invalidTestCases: ['artemisApp.programmingExercise.testCaseAnalysis.invalidTestCase'],
        });

        const { invalidHints, invalidTestCases, missingTestCases, completeAnalysis } = analysisService.analyzeProblemStatement(
            problemStatement,
            taskRegex,
            testCases,
            exerciseHints,
        );

        expect(invalidHints).toEqual(['44']);
        expect(invalidTestCases).toEqual(['testBubbleSort', 'testMergeSort']);
        expect(missingTestCases).toEqual(['testBubbleSortNew']);
        expect(completeAnalysis).toEqual(expectedAnalysis);
    });
});
