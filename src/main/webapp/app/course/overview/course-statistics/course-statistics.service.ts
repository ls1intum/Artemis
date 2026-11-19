import { Service, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { LectureApi } from 'app/openapi/api/lecture-api';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

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
    private readonly lectureApi = inject(LectureApi);

    /**
     * Request fresh totals and the current student's completions for released lecture content.
     * @param courseId the course whose content statistics are requested
     */
    getLearningStatistics(courseId: number) {
        return this.lectureApi.getLearningStatistics(courseId);
    }

    /**
     * Request own scores and anonymous aggregates from the current similar-results group.
     * @param courseId the course whose exercise statistics are requested
     */
    getPeerComparison(courseId: number) {
        return this.http.get<ExercisePeerComparison>(`api/exercise/courses/${courseId}/charts/peer-comparison`);
    }
}
