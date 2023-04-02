export type Task = { id: number; completeString: string; taskName: string; tests: number[] };
export type TaskArray = Array<Task>;
export type TaskArrayWithExercise = {
    exerciseId: number;
    tasks: Array<Task>;
};
