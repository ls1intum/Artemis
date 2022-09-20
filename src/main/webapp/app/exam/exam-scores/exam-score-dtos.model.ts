import { ExerciseType } from 'app/entities/exercise.model';
import { GradeType } from 'app/entities/grading-scale.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { BonusResult } from 'app/entities/bonus.model';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';

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
    public email: string;
    public registrationNumber: string;
    public overallPointsAchieved?: number;
    public overallScoreAchieved?: number;
    public overallPointsAchievedInFirstCorrection?: number;
    public overallGrade?: string;
    public overallGradeInFirstCorrection?: string;
    public hasPassed?: boolean;
    public submitted: boolean;
    public gradeWithBonus?: BonusResult;
    public exerciseGroupIdToExerciseResult: { [key: number]: ExerciseResult };
    public mostSeverePlagiarismVerdict?: PlagiarismVerdict;

    constructor() {}
}

export class StudentExamWithGradeDTO {
    public maxPoints: number;
    public maxBonusPoints: number;
    public gradeType?: GradeType;
    public studentExam?: StudentExam;
    public studentResult: StudentResult;
    public achievedPointsPerExercise: { [exerciseId: number]: number };
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
    public meanPointsSubmitted: number;
    public meanPointsRelativeSubmitted: number;
    public meanPointsTotal: number;
    public meanPointsRelativeTotal: number;
    public meanPointsNonEmpty: number;
    public meanScoreNonEmpty: number;
    public meanPointsSubmittedAndNonEmpty: number;
    public meanScoreSubmittedAndNonEmpty: number;
    public meanGradePassed?: string;
    public meanGradeSubmitted?: string;
    public meanGradeTotal?: string;
    public meanGradeNonEmpty?: string;
    public meanGradeSubmittedAndNonEmpty?: string;
    public medianPassed?: number;
    public medianRelativePassed?: number;
    public medianSubmitted: number;
    public medianRelativeSubmitted: number;
    public medianTotal: number;
    public medianRelativeTotal: number;
    public medianNonEmpty: number;
    public medianScoreNonEmpty: number;
    public medianSubmittedAndNonEmpty: number;
    public medianScoreSubmittedAndNonEmpty: number;
    public medianGradePassed?: string;
    public medianGradeSubmitted?: string;
    public medianGradeTotal?: string;
    public medianGradeNonEmpty?: string;
    public medianGradeSubmittedAndNonEmpty?: string;
    public standardDeviationPassed?: number;
    public standardDeviationSubmitted: number;
    public standardDeviationTotal: number;
    public standardDeviationNonEmpty: number;
    public standardDeviationSubmittedAndNonEmpty: number;
    public standardGradeDeviationPassed?: number;
    public standardGradeDeviationSubmitted?: number;
    public standardGradeDeviationTotal?: number;
    public standardGradeDeviationNonEmpty?: number;
    public standardGradeDeviationSubmittedAndNonEmpty?: number;
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
    public meanPointsNonEmptyInFirstCorrection: number;
    public meanScoreNonEmptyInFirstCorrection: number;
    public meanPointsSubmittedAndNonEmptyInFirstCorrection: number;
    public meanScoreSubmittedAndNonEmptyInFirstCorrection: number;
    public meanGradePassedInFirstCorrection?: string;
    public meanGradeInFirstCorrection?: string;
    public meanGradeTotalInFirstCorrection?: string;
    public meanGradeNonEmptyInFirstCorrection?: string;
    public meanGradeSubmittedAndNonEmptyInFirstCorrection?: string;
    public medianPassedInFirstCorrection?: number;
    public medianRelativePassedInFirstCorrection?: number;
    public medianInFirstCorrection: number;
    public medianRelativeInFirstCorrection: number;
    public medianTotalInFirstCorrection: number;
    public medianRelativeTotalInFirstCorrection: number;
    public medianNonEmptyInFirstCorrection: number;
    public medianScoreNonEmptyInFirstCorrection: number;
    public medianSubmittedAndNonEmptyInFirstCorrection: number;
    public medianScoreSubmittedAndNonEmptyInFirstCorrection: number;
    public medianGradePassedInFirstCorrection?: string;
    public medianGradeInFirstCorrection?: string;
    public medianGradeTotalInFirstCorrection?: string;
    public medianGradeNonEmptyInFirstCorrection?: string;
    public medianGradeSubmittedAndNonEmptyInFirstCorrection?: string;
    public standardDeviationPassedInFirstCorrection?: number;
    public standardDeviationInFirstCorrection: number;
    public standardDeviationTotalInFirstCorrection: number;
    public standardDeviationNonEmptyInFirstCorrection: number;
    public standardDeviationSubmittedAndNonEmptyInFirstCorrection: number;
    public standardGradeDeviationPassedInFirstCorrection?: number;
    public standardGradeDeviationInFirstCorrection?: number;
    public standardGradeDeviationTotalInFirstCorrection?: number;
    public standardGradeDeviationNonEmptyInFirstCorrection?: number;
    public standardGradeDeviationSubmittedAndNonEmptyInFirstCorrection?: number;

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

export class TableState {
    relativeAmountOfPassedExams: string;
    relativeAmountOfSubmittedExams: string;
    absoluteAmountOfSubmittedExams: number;
    absoluteAmountOfTotalExams: number;

    averageScoreSubmitted: string;
    averageScoreTotal: string;
    averageScoreSubmittedInFirstCorrection: string;
    averageScoreTotalInFirstCorrection: string;
    averagePointsSubmitted: string;
    averagePointsTotal: string;
    averagePointsSubmittedInFirstCorrection: string;
    averagePointsTotalInFirstCorrection: string;

    averageGradeSubmitted: string;
    averageGradeTotal: string;
    averageGradeSubmittedInFirstCorrection: string;
    averageGradeTotalInFirstCorrection: string;

    medianScoreSubmitted: string;
    medianScoreTotal: string;
    medianScoreSubmittedInFirstCorrection: string;
    medianScoreTotalInFirstCorrection: string;
    medianPointsSubmitted: string;
    medianPointsTotal: string;
    medianPointsSubmittedInFirstCorrection: string;
    medianPointsTotalInFirstCorrection: string;

    medianGradeSubmitted: string;
    medianGradeTotal: string;
    medianGradeSubmittedInFirstCorrection: string;
    medianGradeTotalInFirstCorrection: string;

    standardDeviationSubmitted: string;
    standardDeviationTotal: string;
    standardDeviationSubmittedInFirstCorrection: string;
    standardDeviationTotalInFirstCorrection: string;

    standardGradeDeviationSubmitted: string;
    standardGradeDeviationTotal: string;
    standardGradeDeviationSubmittedInFirstCorrection: string;
    standardGradeDeviationTotalInFirstCorrection: string;
}
