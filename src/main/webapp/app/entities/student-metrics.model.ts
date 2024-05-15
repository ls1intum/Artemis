export class StudentMetrics {
    public exerciseMetrics?: ExerciseMetrics;
}

export class ExerciseMetrics {
    public exerciseInformation: { [key: number]: ExerciseInformation };

    // Performance metrics
    public score: { [key: number]: number };
    public averageScore: { [key: number]: number };

    // Lateness metrics (relative to start and due date, 0 = on time, 100 = late)
    public latestSubmission: { [key: number]: number };
    public averageLatestSubmission: { [key: number]: number };
}

export class ExerciseInformation {
    public id: number;
    public title: string;
    public shortName?: string;
    public start: string;
    public due: string;
    public maxPoints: number;
    public type: string;
}
