export enum ExerciseComponent {
    PROBLEM_STATEMENT,
    SOLUTION_REPOSITORY,
    TEMPLATE_REPOSITORY,
    TEST_REPOSITORY,
}
export class IrisExercisePlanComponent {
    id: number;
    exercisePlan: number;
    component: ExerciseComponent;
    instructions: string;
}
