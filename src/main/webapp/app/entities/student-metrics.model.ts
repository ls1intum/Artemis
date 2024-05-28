import { CompetencyTaxonomy } from 'app/entities/competency.model';
import { DifficultyLevel, ExerciseMode, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

export class StudentMetrics {
    public exerciseMetrics?: ExerciseMetrics;
    public lectureUnitStudentMetricsDTO?: LectureUnitStudentMetricsDTO;
    public competencyMetrics?: CompetencyMetrics;
}

export class ExerciseMetrics {
    public exerciseInformation?: { [key: number]: ExerciseInformation };
    public categories?: { [key: number]: (string | null)[] };
    public teamId?: { [key: number]: number };

    // Performance metrics
    public score?: { [key: number]: number };
    public averageScore?: { [key: number]: number };

    // Lateness metrics (relative to start and due date, 0 = on time, 100 = late)
    public latestSubmission?: { [key: number]: number };
    public averageLatestSubmission?: { [key: number]: number };

    // Completed exercises
    public completed?: number[];
}

export class ExerciseInformation {
    public id: number;
    public title: string;
    public shortName?: string;
    public startDate: dayjs.Dayjs;
    public dueDate?: dayjs.Dayjs;
    public maxPoints: number;
    public type: ExerciseType;
    public includedInOverallScore?: IncludedInOverallScore;
    public exerciseMode?: ExerciseMode;
    public categories?: ExerciseCategory[];
    public difficulty?: DifficultyLevel;
    public studentAssignedTeamId?: number;
    public allowOnlineEditor?: boolean;
    public allowOfflineIde?: boolean;
}

export class LectureUnitStudentMetricsDTO {
    public lectureUnitInformation: { [key: number]: LectureUnitInformation };
    public completed: number[];
}

export class LectureUnitInformation {
    public id: number;
    public lectureId: number;
    public name: string;
    public releaseDate: dayjs.Dayjs;
    public type: LectureUnitType;
}

export class CompetencyMetrics {
    public competencyInformation?: { [key: number]: CompetencyInformation };
    public exercises?: { [key: number]: number[] }; // Exercise ID -> Competency IDs
    public lectureUnits?: { [key: number]: number[] }; // Lecture Unit ID -> Competency IDs
    public progress?: { [key: number]: number }; // Competency ID -> progress
    public confidence?: { [key: number]: number }; // Competency ID -> confidence
    public jolValues?: { [key: number]: number }; // Competency ID -> JOL value
}

export class CompetencyInformation {
    public id: number;
    public title: string;
    public description: string;
    public taxonomy: CompetencyTaxonomy;
    public softDueDate?: string;
    public optional: boolean;
    public masteryThreshold: number;
}
