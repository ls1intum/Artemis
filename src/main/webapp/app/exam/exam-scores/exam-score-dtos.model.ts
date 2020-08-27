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
    constructor(public id: number, public title: string, public maxPoints: number, public numberOfParticipants: number, public containedExercises: ExerciseInfo[]) {}
}

export class ExerciseInfo {
    constructor(public exerciseId: number, public title: string, public maxPoints: number, public numberOfParticipants: number) {}
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

export class AggregatedExamResult {
    public meanPoints: number | null = null;
    public meanPointsRelative: number | null = null;
    public meanPointsTotal: number | null = null;
    public meanPointsRelativeTotal: number | null = null;
    public median: number | null = null;
    public medianRelative: number | null = null;
    public medianTotal: number | null = null;
    public medianRelativeTotal: number | null = null;
    public standardDeviation: number | null = null;
    public standardDeviationTotal: number | null = null;
    public noOfExamsFiltered = 0;
    public noOfRegisteredUsers = 0;

    constructor() {}
}

export class AggregatedExerciseGroupResult {
    public exerciseGroupId: number;
    public title: string;
    public maxPoints: number;
    public totalParticipants: number;
    public noOfParticipantsWithFilter = 0;
    public totalPoints = 0;
    public averagePoints: number | null = null;
    public averagePercentage: number | null = null;
    public exerciseResults: AggregatedExerciseResult[] = [];

    constructor(exerciseGroupId: number, title: string, maxPoints: number, totalParticipants: number) {
        this.exerciseGroupId = exerciseGroupId;
        this.title = title;
        this.maxPoints = maxPoints;
        this.totalParticipants = totalParticipants;
    }
}

export class AggregatedExerciseResult {
    public exerciseId: number;
    public title: string;
    public maxPoints: number;
    public totalParticipants: number;
    public noOfParticipantsWithFilter = 0;
    public totalPoints = 0;
    public averagePoints: number | null = null;
    public averagePercentage: number | null = null;

    constructor(exerciseId: number, title: string, maxPoints: number, totalParticipants: number) {
        this.exerciseId = exerciseId;
        this.title = title;
        this.maxPoints = maxPoints;
        this.totalParticipants = totalParticipants;
    }
}
