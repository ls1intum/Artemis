export enum ExerciseComponent {
    PROBLEM_STATEMENT = 'Problem statement',
    SOLUTION_REPOSITORY = 'Solution repository',
    TEMPLATE_REPOSITORY = 'Template repository',
    TEST_REPOSITORY = 'Test repository',
}
export class IrisExercisePlanComponent {
    id?: number;
    exercisePlan: number;
    component: ExerciseComponent;
    instructions: string;
}
