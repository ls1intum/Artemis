export class ExamScoreDTO {
    public examId: number;
    public title: string;
    public maxPoints: number;
    public averagePointsAchieved: number;
    public hasMultipleCorrectionRounds: boolean;
    public exerciseGroups: ExerciseGroup[];
    public studentResults: StudentResult[];

    constructor() {}
}

export class ExerciseGroup {
    public id: number;
    public title: string;
    public maxPoints: number;
    public numberOfParticipants: number;
    public containedExercises: ExerciseInfo[];

    constructor() {}
}

export class ExerciseInfo {
    public exerciseId: number;
    public title: string;
    public maxPoints: number;
    public numberOfParticipants: number;

    constructor() {}
}

export class StudentResult {
    public userId: number;
    public name: string;
    public login: string;
    public eMail: string;
    public registrationNumber: string;
    public overallPointsAchieved?: number;
    public overallScoreAchieved?: number;
    public overallGrade?: string;
    public hasPassed?: boolean;
    public submitted: boolean;
    public exerciseGroupIdToExerciseResult: { [key: number]: ExerciseResult };

    constructor() {}
}

export class ExerciseResult {
    public exerciseId: number;
    public title: string;
    public maxScore: number;
    public achievedScore?: number;
    public achievedPoints?: number;
    public hasNonEmptySubmission: boolean;

    constructor() {}
}

export class AggregatedExamResult {
    public meanPointsPassed?: number;
    public meanPointsRelativePassed?: number;
    public meanPoints: number;
    public meanPointsRelative: number;
    public meanPointsTotal: number;
    public meanPointsRelativeTotal: number;
    public meanPointsFirstCorrectionTotal = 1;
    public meanPointsFirstCorrectionRelative = 1;
    public meanGradePassed?: string;
    public meanGrade?: string;
    public meanGradeTotal?: string;
    public medianPassed?: number;
    public medianRelativePassed?: number;
    public median: number;
    public medianRelative: number;
    public medianTotal: number;
    public medianRelativeTotal: number;
    public medianFirstCorrectionTotal = 1;
    public medianFirstCorrectionRelative = 1;
    public medianGradePassed?: string;
    public medianGrade?: string;
    public medianGradeTotal?: string;
    public standardDeviationPassed?: number;
    public standardDeviation: number;
    public standardDeviationTotal: number;
    public standardDeviationFirstCorrectionTotal = 1;
    public noOfExamsFilteredForPassed = 0;
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
    public averagePoints?: number;
    public averagePercentage?: number;
    public averageGrade?: string;
    public standardDeviation?: number;
    public exerciseResults: AggregatedExerciseResult[] = [];

    constructor(exerciseGroupId: number, title: string, maxPoints: number, totalParticipants: number) {
        this.exerciseGroupId = exerciseGroupId;
        this.title = title;
        this.maxPoints = maxPoints;
        this.totalParticipants = totalParticipants;
        this.standardDeviation = 10;
    }
}

export class AggregatedExerciseResult {
    public exerciseId: number;
    public title: string;
    public maxPoints: number;
    public totalParticipants: number;
    public noOfParticipantsWithFilter = 0;
    public totalPoints = 0;
    public averagePoints?: number;
    public averagePercentage?: number;
    public standardDeviation?: number;

    constructor(exerciseId: number, title: string, maxPoints: number, totalParticipants: number) {
        this.exerciseId = exerciseId;
        this.title = title;
        this.maxPoints = maxPoints;
        this.totalParticipants = totalParticipants;
        this.standardDeviation = 10;
    }
}
