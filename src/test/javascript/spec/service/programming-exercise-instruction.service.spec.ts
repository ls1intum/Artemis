import { Result } from 'app/entities/result.model';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';
import dayjs from 'dayjs/esm';

describe('ProgrammingExerciseInstructionService', () => {
    let programmingExerciseInstructionService: ProgrammingExerciseInstructionService;

    beforeEach(() => {
        programmingExerciseInstructionService = new ProgrammingExerciseInstructionService();
    });

    it('should determine a successful state for all tasks if the result is successful', () => {
        const result: Result = {
            id: 1,
            completionDate: dayjs('2019-06-06T22:15:29.203+02:00'),
            successful: true,
            feedbacks: [
                { testCase: { testName: 'testBubbleSort', id: 1 }, detailText: 'lorem ipsum', positive: true },
                { testCase: { testName: 'testMergeSort', id: 2 }, detailText: 'lorem ipsum', positive: true },
            ],
        };
        const testCases = result.feedbacks!.map((feedback) => feedback.testCase!.id!);

        const { testCaseState: taskState1, detailed: detailed1 } = programmingExerciseInstructionService.testStatusForTask(testCases.slice(0, 1), result);
        expect(taskState1).toBe(TestCaseState.SUCCESS);
        expect(detailed1).toEqual({ successfulTests: [1], failedTests: [], notExecutedTests: [] });

        const { testCaseState: taskState2, detailed: detailed2 } = programmingExerciseInstructionService.testStatusForTask(testCases.slice(1), result);
        expect(taskState2).toBe(TestCaseState.SUCCESS);
        expect(detailed2).toEqual({ successfulTests: [2], failedTests: [], notExecutedTests: [] });

        const { testCaseState: taskState3, detailed: detailed3 } = programmingExerciseInstructionService.testStatusForTask([], result);
        expect(taskState3).toBe(TestCaseState.NOT_EXECUTED);
        expect(detailed3).toEqual({ successfulTests: [], failedTests: [], notExecutedTests: [] });
    });

    it('should determine a failed state for a task if at least one test has failed', () => {
        const result: Result = {
            id: 1,
            completionDate: dayjs('2019-06-06T22:15:29.203+02:00'),
            successful: false,
            feedbacks: [
                { testCase: { testName: 'testBubbleSort', id: 1 }, detailText: 'lorem ipsum', positive: false },
                { testCase: { testName: 'testMergeSort', id: 2 }, detailText: 'lorem ipsum', positive: true },
            ],
        };
        const testCases = result.feedbacks!.map((feedback) => feedback.testCase!.id!);

        const { testCaseState: taskState1, detailed: detailed1 } = programmingExerciseInstructionService.testStatusForTask(testCases, result);
        expect(taskState1).toBe(TestCaseState.FAIL);
        expect(detailed1).toEqual({ successfulTests: [2], failedTests: [1], notExecutedTests: [] });
    });

    it('should determine a state if there is no feedback for the specified tests', () => {
        const result: Result = {
            id: 1,
            completionDate: dayjs('2019-06-06T22:15:29.203+02:00'),
            successful: false,
            feedbacks: [{ testCase: { testName: 'irrelevantTest', id: 3 }, detailText: 'lorem ipsum', positive: true }],
        };
        const testCases = [1, 2];

        const { testCaseState: taskState1, detailed: detailed1 } = programmingExerciseInstructionService.testStatusForTask(testCases, result);
        expect(taskState1).toBe(TestCaseState.NOT_EXECUTED);
        expect(detailed1).toEqual({ successfulTests: [], failedTests: [], notExecutedTests: [1, 2] });
    });
});
