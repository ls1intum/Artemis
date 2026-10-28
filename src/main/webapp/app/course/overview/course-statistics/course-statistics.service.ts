import { Service, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

export interface LectureLearningStatistics {
    lectureId: number;
    lectureTitle?: string;
    type: 'attachment' | 'text' | 'online';
    total: number;
    completed: number;
}

export interface ExercisePeerScore {
    exerciseId: number;
    exerciseTitle?: string;
    exerciseType?: ExerciseType;
    releaseDate?: string;
    ownScore?: number;
    peerAverage?: number;
    peerCount?: number;
}

export interface ExercisePeerComparison {
    exercises?: ExercisePeerScore[];
    groupCount?: number;
    peerCount?: number;
}

/** Each call requests fresh aggregate statistics; this service retains no data. */
@Service()
export class CourseStatisticsService {
    private readonly http = inject(HttpClient);

    /**
     * Request fresh totals and the current student's completions for released lecture content.
     * @param courseId the course whose content statistics are requested
     */
    getLearningStatistics(courseId: number) {
        return this.http.get<LectureLearningStatistics[]>(`api/lecture/courses/${courseId}/learning-statistics`);
    }

    /**
     * Request own scores and anonymous aggregates from the current similar-results group.
     * @param courseId the course whose exercise statistics are requested
     */
    getPeerComparison(courseId: number) {
        return this.http.get<ExercisePeerComparison>(`api/exercise/courses/${courseId}/charts/peer-comparison`);
    }
}
