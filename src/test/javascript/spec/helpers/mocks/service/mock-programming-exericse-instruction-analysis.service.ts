import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';

export class MockProgrammingExerciseInstructionAnalysisService {
    public analyzeProblemStatement = (problemStatement: string, taskRegex: RegExp, exerciseTestCases: string[], exerciseHints: ExerciseHint[]) => {
        return {};
    };
}
