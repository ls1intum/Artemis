export class ExamScoreDTO {
    constructor(
        public examId: number,
        public title: string,
        public maxPoints: number,
        public averagePointsAchieved: number,
        public exerciseGroups: ExerciseGroup[],
        public studentResults: StudentResult[],
    ) {}
}

export class ExerciseGroup {
    constructor(public exerciseGroupId: number, public title: string, public maxPoints: number, public containedExercises: ExerciseInfo[]) {}
}

export class ExerciseInfo {
    constructor(public exerciseId: number, public title: string, public totalParticipants: number) {}
}

export class StudentResult {
    constructor(
        public userId: number,
        public name: string,
        public login: string,
        public eMail: string,
        public registrationNumber: string,
        public overallPointsAchieved: number,
        public overallScoreAchieved: number,
        public submitted: boolean,
        public exerciseGroupIdToExerciseResult: { [key: number]: ExerciseResult },
    ) {}
}

export class ExerciseResult {
    constructor(
        public exerciseId: number,
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
    public averagePoints: number | null = 0;
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
    public totalParticipants: number;
    public noOfParticipantsWithFilter = 0;
    public totalPoints = 0;
    public averagePoints: number | null = 0;

    constructor(exerciseId: number, title: string, totalParticipants: number) {
        this.exerciseId = exerciseId;
        this.title = title;
        this.totalParticipants = totalParticipants;
    }
}
