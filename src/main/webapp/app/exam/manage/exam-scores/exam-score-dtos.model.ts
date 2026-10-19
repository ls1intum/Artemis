import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { BonusResult } from 'app/assessment/shared/entities/bonus.model';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';

export interface ExamScoreDTO {
    examId: number;
    title: string;
    maxPoints: number;
    averagePointsAchieved: number;
    hasSecondCorrectionAndStarted: boolean;
    exerciseGroups: ExerciseGroup[];
    studentResults: StudentResult[];
}

/**
 * Instantiated in tests via `new ExerciseGroup()` and populated after construction, hence a class with
 * definite-assignment (!) markers rather than an interface.
 */
export class ExerciseGroup {
    public id!: number;
    public title!: string;
    public maxPoints!: number;
    public numberOfParticipants!: number;
    public containedExercises!: ExerciseInfo[];
}

export interface ExerciseInfo {
    exerciseId: number;
    title: string;
    maxPoints: number;
    numberOfParticipants: number;
    exerciseType: string;
}

export interface StudentResult {
    userId: number;
    name: string;
    login: string;
    email: string;
    registrationNumber: string;
    overallPointsAchieved?: number;
    overallScoreAchieved?: number;
    overallPointsAchievedInFirstCorrection?: number;
    overallGrade?: string;
    overallGradeInFirstCorrection?: string;
    hasPassed?: boolean;
    submitted: boolean;
    gradeWithBonus?: BonusResult;
    exerciseGroupIdToExerciseResult: { [key: number]: ExerciseResult };
    mostSeverePlagiarismVerdict?: PlagiarismVerdict;
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class StudentExamWithGradeDTO {
    public maxPoints!: number;
    public maxBonusPoints!: number;
    public gradeType?: GradeType;
    public studentExam?: StudentExam;
    public studentResult!: StudentResult;
    public achievedPointsPerExercise!: { [exerciseId: number]: number };
}

export interface ExerciseResult {
    exerciseId: number;
    title: string;
    maxScore: number;
    achievedScore?: number;
    achievedPoints?: number;
    hasNonEmptySubmission: boolean;
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class AggregatedExamResult {
    public meanPointsPassed?: number;
    public meanPointsRelativePassed?: number;
    public meanPointsSubmitted!: number;
    public meanPointsRelativeSubmitted!: number;
    public meanPointsTotal!: number;
    public meanPointsRelativeTotal!: number;
    public meanPointsNonEmpty!: number;
    public meanScoreNonEmpty!: number;
    public meanPointsSubmittedAndNonEmpty!: number;
    public meanScoreSubmittedAndNonEmpty!: number;
    public meanGradePassed?: string;
    public meanGradeSubmitted?: string;
    public meanGradeTotal?: string;
    public meanGradeNonEmpty?: string;
    public meanGradeSubmittedAndNonEmpty?: string;
    public medianPassed?: number;
    public medianRelativePassed?: number;
    public medianSubmitted!: number;
    public medianRelativeSubmitted!: number;
    public medianTotal!: number;
    public medianRelativeTotal!: number;
    public medianNonEmpty!: number;
    public medianScoreNonEmpty!: number;
    public medianSubmittedAndNonEmpty!: number;
    public medianScoreSubmittedAndNonEmpty!: number;
    public medianGradePassed?: string;
    public medianGradeSubmitted?: string;
    public medianGradeTotal?: string;
    public medianGradeNonEmpty?: string;
    public medianGradeSubmittedAndNonEmpty?: string;
    public standardDeviationPassed?: number;
    public standardDeviationSubmitted!: number;
    public standardDeviationTotal!: number;
    public standardDeviationNonEmpty!: number;
    public standardDeviationSubmittedAndNonEmpty!: number;
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
    public meanPointsInFirstCorrection!: number;
    public meanPointsRelativeInFirstCorrection!: number;
    public meanPointsTotalInFirstCorrection!: number;
    public meanPointsRelativeTotalInFirstCorrection!: number;
    public meanPointsNonEmptyInFirstCorrection!: number;
    public meanScoreNonEmptyInFirstCorrection!: number;
    public meanPointsSubmittedAndNonEmptyInFirstCorrection!: number;
    public meanScoreSubmittedAndNonEmptyInFirstCorrection!: number;
    public meanGradePassedInFirstCorrection?: string;
    public meanGradeInFirstCorrection?: string;
    public meanGradeTotalInFirstCorrection?: string;
    public meanGradeNonEmptyInFirstCorrection?: string;
    public meanGradeSubmittedAndNonEmptyInFirstCorrection?: string;
    public medianPassedInFirstCorrection?: number;
    public medianRelativePassedInFirstCorrection?: number;
    public medianInFirstCorrection!: number;
    public medianRelativeInFirstCorrection!: number;
    public medianTotalInFirstCorrection!: number;
    public medianRelativeTotalInFirstCorrection!: number;
    public medianNonEmptyInFirstCorrection!: number;
    public medianScoreNonEmptyInFirstCorrection!: number;
    public medianSubmittedAndNonEmptyInFirstCorrection!: number;
    public medianScoreSubmittedAndNonEmptyInFirstCorrection!: number;
    public medianGradePassedInFirstCorrection?: string;
    public medianGradeInFirstCorrection?: string;
    public medianGradeTotalInFirstCorrection?: string;
    public medianGradeNonEmptyInFirstCorrection?: string;
    public medianGradeSubmittedAndNonEmptyInFirstCorrection?: string;
    public standardDeviationPassedInFirstCorrection?: number;
    public standardDeviationInFirstCorrection!: number;
    public standardDeviationTotalInFirstCorrection!: number;
    public standardDeviationNonEmptyInFirstCorrection!: number;
    public standardDeviationSubmittedAndNonEmptyInFirstCorrection!: number;
    public standardGradeDeviationPassedInFirstCorrection?: number;
    public standardGradeDeviationInFirstCorrection?: number;
    public standardGradeDeviationTotalInFirstCorrection?: number;
    public standardGradeDeviationNonEmptyInFirstCorrection?: number;
    public standardGradeDeviationSubmittedAndNonEmptyInFirstCorrection?: number;
}

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
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

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
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

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class TableState {
    relativeAmountOfPassedExams!: string;
    relativeAmountOfSubmittedExams!: string;
    absoluteAmountOfSubmittedExams!: number;
    absoluteAmountOfTotalExams!: number;

    averageScoreSubmitted!: string;
    averageScoreTotal!: string;
    averageScoreSubmittedInFirstCorrection!: string;
    averageScoreTotalInFirstCorrection!: string;
    averagePointsSubmitted!: string;
    averagePointsTotal!: string;
    averagePointsSubmittedInFirstCorrection!: string;
    averagePointsTotalInFirstCorrection!: string;

    averageGradeSubmitted!: string;
    averageGradeTotal!: string;
    averageGradeSubmittedInFirstCorrection!: string;
    averageGradeTotalInFirstCorrection!: string;

    medianScoreSubmitted!: string;
    medianScoreTotal!: string;
    medianScoreSubmittedInFirstCorrection!: string;
    medianScoreTotalInFirstCorrection!: string;
    medianPointsSubmitted!: string;
    medianPointsTotal!: string;
    medianPointsSubmittedInFirstCorrection!: string;
    medianPointsTotalInFirstCorrection!: string;

    medianGradeSubmitted!: string;
    medianGradeTotal!: string;
    medianGradeSubmittedInFirstCorrection!: string;
    medianGradeTotalInFirstCorrection!: string;

    standardDeviationSubmitted!: string;
    standardDeviationTotal!: string;
    standardDeviationSubmittedInFirstCorrection!: string;
    standardDeviationTotalInFirstCorrection!: string;

    standardGradeDeviationSubmitted!: string;
    standardGradeDeviationTotal!: string;
    standardGradeDeviationSubmittedInFirstCorrection!: string;
    standardGradeDeviationTotalInFirstCorrection!: string;
}
