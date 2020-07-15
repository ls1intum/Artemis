export type Task = { id: number; completeString: string; taskName: string; tests: string[]; hints: string[] };
export type TaskArray = Array<Task>;
export type TaskArrayWithParticipation = {
    participationId: number;
    tasks: Array<Task>;
};
