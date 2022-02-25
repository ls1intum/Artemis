import { ExerciseType } from 'app/entities/exercise.model';

export class ExamScoreDTO {
    public examId: number;
    public title: string;
    public maxPoints: number;
    public averagePointsAchieved: number;
    public hasSecondCorrectionAndStarted: boolean;
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
    public exerciseType: string;

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
    public overallPointsAchievedInFirstCorrection?: number;
    public overallGrade?: string;
    public overallGradeInFirstCorrection?: string;
    public hasPassed?: boolean;
    public submitted: boolean;
    public exerciseGroupIdToExerciseResult: Map<number, ExerciseResult>; // no idea if this is possible

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
    public meanGradePassed?: string;
    public meanGrade?: string;
    public meanGradeTotal?: string;
    public medianPassed?: number;
    public medianRelativePassed?: number;
    public median: number;
    public medianRelative: number;
    public medianTotal: number;
    public medianRelativeTotal: number;
    public medianGradePassed?: string;
    public medianGrade?: string;
    public medianGradeTotal?: string;
    public standardDeviationPassed?: number;
    public standardDeviation: number;
    public standardDeviationTotal: number;
    public standardGradeDeviationPassed?: number;
    public standardGradeDeviation?: number;
    public standardGradeDeviationTotal?: number;
    public noOfExamsFilteredForPassed = 0;
    public noOfExamsSubmitted = 0;
    public noOfExamsNonEmpty = 0;
    public noOfExamsSubmittedAndNotEmpty = 0;
    public noOfRegisteredUsers = 0;

    // same for first correction round
    public meanPointsPassedInFirstCorrection?: number;
    public meanPointsRelativePassedInFirstCorrection?: number;
    public meanPointsInFirstCorrection: number;
    public meanPointsRelativeInFirstCorrection: number;
    public meanPointsTotalInFirstCorrection: number;
    public meanPointsRelativeTotalInFirstCorrection: number;
    public meanGradePassedInFirstCorrection?: string;
    public meanGradeInFirstCorrection?: string;
    public meanGradeTotalInFirstCorrection?: string;
    public medianPassedInFirstCorrection?: number;
    public medianRelativePassedInFirstCorrection?: number;
    public medianInFirstCorrection: number;
    public medianRelativeInFirstCorrection: number;
    public medianTotalInFirstCorrection: number;
    public medianRelativeTotalInFirstCorrection: number;
    public medianGradePassedInFirstCorrection?: string;
    public medianGradeInFirstCorrection?: string;
    public medianGradeTotalInFirstCorrection?: string;
    public standardDeviationPassedInFirstCorrection?: number;
    public standardDeviationInFirstCorrection: number;
    public standardDeviationTotalInFirstCorrection: number;
    public standardGradeDeviationPassedInFirstCorrection?: number;
    public standardGradeDeviationInFirstCorrection?: number;
    public standardGradeDeviationTotalInFirstCorrection?: number;

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
    public exerciseType: ExerciseType;
    public totalParticipants: number;
    public noOfParticipantsWithFilter = 0;
    public totalPoints = 0;
    public averagePoints?: number;
    public averagePercentage?: number;

    constructor(exerciseId: number, title: string, maxPoints: number, totalParticipants: number, exerciseType: ExerciseType) {
        this.exerciseId = exerciseId;
        this.title = title;
        this.maxPoints = maxPoints;
        this.totalParticipants = totalParticipants;
        this.exerciseType = exerciseType;
    }
}
