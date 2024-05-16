import { CompetencyTaxonomy } from 'app/entities/competency.model';

export class StudentMetrics {
    public exerciseMetrics?: ExerciseMetrics;
    public lectureUnitStudentMetricsDTO?: LectureUnitStudentMetricsDTO;
    public competencyMetrics?: CompetencyMetrics;
}

export class ExerciseMetrics {
    public exerciseInformation: { [key: number]: ExerciseInformation };

    // Performance metrics
    public score: { [key: number]: number };
    public averageScore: { [key: number]: number };

    // Lateness metrics (relative to start and due date, 0 = on time, 100 = late)
    public latestSubmission: { [key: number]: number };
    public averageLatestSubmission: { [key: number]: number };

    // Completed exercises
    public completed: number[];
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

export class LectureUnitStudentMetricsDTO {
    public lectureUnitInformation: { [key: number]: LectureUnitInformation };
    public completed: number[];
}

export class LectureUnitInformation {
    public id: number;
    public name: string;
    public releaseDate: string;
    public type: string;
}

export class CompetencyMetrics {
    public competencyInformation: { [key: number]: CompetencyInformation };
    public exercises: { [key: number]: number[] }; // Exercise ID -> Competency IDs
    public lectureUnits: { [key: number]: number[] }; // Lecture Unit ID -> Competency IDs
    public progress: { [key: number]: number }; // Competency ID -> progress
    public confidence: { [key: number]: number }; // Competency ID -> confidence
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
