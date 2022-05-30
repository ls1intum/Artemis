import dayjs from 'dayjs/esm';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/exercises/programming/shared/instructions-render/service/programming-exercise-instruction.service';

describe('ProgrammingExerciseInstructionService', () => {
    let programmingExerciseInstructionService: ProgrammingExerciseInstructionService;

    beforeEach(() => {
        programmingExerciseInstructionService = new ProgrammingExerciseInstructionService();
    });

    it('should determine a successful state for all tasks if the result is successful', () => {
        const result = {
            id: 1,
            completionDate: dayjs('2019-06-06T22:15:29.203+02:00'),
            successful: true,
            feedbacks: [
                { text: 'testBubbleSort', detailText: 'lorem ipsum', positive: true },
                { text: 'testMergeSort', detailText: 'lorem ipsum', positive: true },
            ],
        } as any;
        const testCases = result.feedbacks.map(({ text }: { text: string }) => text);

        const { testCaseState: taskState1, detailed: detailed1 } = programmingExerciseInstructionService.testStatusForTask(testCases.slice(0, 1), result);
        expect(taskState1).toBe(TestCaseState.SUCCESS);
        expect(detailed1).toEqual({ successfulTests: ['testBubbleSort'], failedTests: [], notExecutedTests: [] });

        const { testCaseState: taskState2, detailed: detailed2 } = programmingExerciseInstructionService.testStatusForTask(testCases.slice(1), result);
        expect(taskState2).toBe(TestCaseState.SUCCESS);
        expect(detailed2).toEqual({ successfulTests: ['testMergeSort'], failedTests: [], notExecutedTests: [] });
    });

    it('should determine a failed state for a task if at least one test has failed (non legacy case)', () => {
        const result = {
            id: 1,
            completionDate: dayjs('2019-06-06T22:15:29.203+02:00'),
            successful: false,
            feedbacks: [
                { text: 'testBubbleSort', detailText: 'lorem ipsum', positive: false },
                { text: 'testMergeSort', detailText: 'lorem ipsum', positive: true },
            ],
        } as any;
        const testCases = result.feedbacks.map(({ text }: { text: string }) => text);

        const { testCaseState: taskState1, detailed: detailed1 } = programmingExerciseInstructionService.testStatusForTask(testCases, result);
        expect(taskState1).toBe(TestCaseState.FAIL);
        expect(detailed1).toEqual({ successfulTests: ['testMergeSort'], failedTests: ['testBubbleSort'], notExecutedTests: [] });
    });

    it('should determine a failed state for a task if at least one test has failed (legacy case)', () => {
        const result = {
            id: 1,
            completionDate: dayjs('2018-06-06T22:15:29.203+02:00'),
            successful: false,
            feedbacks: [{ text: 'testBubbleSort', detailText: 'lorem ipsum', positive: false }],
        } as any;
        const testCases = ['testBubbleSort', 'testMergeSort'];

        const { testCaseState: taskState1, detailed: detailed1 } = programmingExerciseInstructionService.testStatusForTask(testCases, result);
        expect(taskState1).toBe(TestCaseState.FAIL);
        expect(detailed1).toEqual({ successfulTests: ['testMergeSort'], failedTests: ['testBubbleSort'], notExecutedTests: [] });
    });

    it('should determine a state if there is no feedback for the specified tests (non legacy only)', () => {
        const result = {
            id: 1,
            completionDate: dayjs('2019-06-06T22:15:29.203+02:00'),
            successful: false,
            feedbacks: [{ text: 'irrelevantTest', detailText: 'lorem ipsum', positive: true }],
        } as any;
        const testCases = ['testBubbleSort', 'testMergeSort'];

        const { testCaseState: taskState1, detailed: detailed1 } = programmingExerciseInstructionService.testStatusForTask(testCases, result);
        expect(taskState1).toBe(TestCaseState.NOT_EXECUTED);
        expect(detailed1).toEqual({ successfulTests: [], failedTests: [], notExecutedTests: ['testBubbleSort', 'testMergeSort'] });
    });
});
