import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { DifficultyLevel, ExerciseMode, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';

export interface StudentMetrics {
    exerciseMetrics?: ExerciseMetrics;
    lectureUnitStudentMetricsDTO?: LectureUnitStudentMetricsDTO;
    competencyMetrics?: CompetencyMetrics;
}

export interface ExerciseMetrics {
    exerciseInformation?: { [key: number]: ExerciseInformation };
    categories?: { [key: number]: (string | null)[] };
    teamId?: { [key: number]: number };

    // Performance metrics
    score?: { [key: number]: number };
    averageScore?: { [key: number]: number };

    // Lateness metrics (relative to start and due date, 0 = on time, 100 = late)
    latestSubmission?: { [key: number]: number };
    averageLatestSubmission?: { [key: number]: number };

    // Completed exercises
    completed?: number[];
}

export interface ExerciseInformation {
    id: number;
    title: string;
    shortName?: string;
    startDate: dayjs.Dayjs;
    dueDate?: dayjs.Dayjs;
    maxPoints: number;
    type: ExerciseType;
    includedInOverallScore?: IncludedInOverallScore;
    exerciseMode?: ExerciseMode;
    categories?: ExerciseCategory[];
    difficulty?: DifficultyLevel;
    studentAssignedTeamId?: number;
    allowOnlineEditor?: boolean;
    allowOfflineIde?: boolean;
}

export interface LectureUnitStudentMetricsDTO {
    lectureUnitInformation?: { [key: number]: LectureUnitInformation };
    completed?: number[];
}

export interface LectureUnitInformation {
    id: number;
    lectureId: number;
    lectureTitle: string;
    name: string;
    releaseDate?: dayjs.Dayjs;
    type: LectureUnitType;
}

export interface CompetencyMetrics {
    competencyInformation?: { [key: number]: CompetencyInformation };
    exercises?: { [key: number]: number[] }; // Competency ID -> Exercise IDs
    lectureUnits?: { [key: number]: number[] }; // Competency ID -> Lecture Unit IDs
    progress?: { [key: number]: number }; // Competency ID -> progress
    confidence?: { [key: number]: number }; // Competency ID -> confidence
}

export interface CompetencyInformation {
    id: number;
    title: string;
    description: string;
    taxonomy?: CompetencyTaxonomy;
    softDueDate?: dayjs.Dayjs;
    optional: boolean;
    masteryThreshold: number;
}
