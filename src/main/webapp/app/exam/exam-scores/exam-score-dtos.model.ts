export class ExamScoreDTO {
    constructor(
        public id: number,
        public title: string,
        public maxPoints: number,
        public averagePointsAchieved: number,
        public exerciseGroups: ExerciseGroup[],
        public studentResults: StudentResult[],
    ) {}
}

export class ExerciseGroup {
    constructor(
        public id: number,
        public title: string,
        public maxPoints: number,
        public averagePointsAchieved: number,
        public containedExercises: string[],
        public containedExerciseIds: number[],
    ) {}
}

export class StudentResult {
    constructor(
        public id: number,
        public name: string,
        public login: string,
        public eMail: string,
        public registrationNumber: string,
        public overallPointsAchieved: number,
        public overallScoreAchieved: number,
        public submitted: boolean,
        public exerciseGroupIdToExerciseResult: Map<number, ExerciseResult>,
    ) {}
}

export class ExerciseResult {
    constructor(
        public id: number,
        public title: string,
        public maxScore: number,
        public achievedScore: number,
        public achievedPoints: number,
        public hasNonEmptySubmission: boolean,
    ) {}
}

export class AggregatedExerciseGroupResult {
    public exerciseGroupId: number;
    public title: string;
    public maxPoints: number;
    public noOfParticipantsWithFilter = 0;
    public totalParticipants = 0;
    public totalPoints = 0;
    public averagePoints = 0;
    public exerciseResults: AggregatedExerciseResult[] = [];

    constructor(exerciseGroupId: number, title: string, maxPoints: number) {
        this.exerciseGroupId = exerciseGroupId;
        this.title = title;
        this.maxPoints = maxPoints;
    }
}

export class AggregatedExerciseResult {
    public exerciseId: number;
    public title: string;
    public noOfParticipantsWithFilter = 0;
    public totalParticipants = 0;
    public totalPoints = 0;
    public averagePoints = 0;

    constructor(exerciseId: number, title: string) {
        this.exerciseId = exerciseId;
        this.title = title;
    }
}
