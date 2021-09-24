import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs';
import { splitCamelCase } from 'app/shared/util/utils';
import { map } from 'rxjs/operators';

/**
 * Corresponds to ExerciseScoresDTO.java on the server
 */
export class ExerciseScoresDTO {
    public exerciseId?: number;
    public exerciseTitle?: string;
    public exerciseType?: string;
    public releaseDate?: dayjs.Dayjs;
    public scoreOfStudent?: number;
    public averageScoreAchieved?: number;
    public maxScoreAchieved?: number;
}

/**
 * Service to request the data from the server that is necessary for the exercise-scores-chart.component.ts
 */
@Injectable({ providedIn: 'root' })
export class ExerciseScoresChartService {
    public resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient) {}
    /**
     * Get the course exercise performance statistics necessary for exercise-scores-chart.component.ts
     * @param courseId id of the course
     */
    getExerciseScoresForCourse(courseId: number): Observable<HttpResponse<ExerciseScoresDTO[]>> {
        if (courseId === undefined || courseId === null || courseId < 1) {
            throw new Error('Invalid courseId provided: ' + courseId);
        }

        return this.http.get<ExerciseScoresDTO[]>(`${this.resourceUrl}/courses/${courseId}/charts/exercise-scores`, { observe: 'response' }).pipe(
            map((response: HttpResponse<ExerciseScoresDTO[]>) => {
                if (response.body) {
                    for (const exerciseScoreDTO of response.body) {
                        exerciseScoreDTO.releaseDate = exerciseScoreDTO.releaseDate ? dayjs(exerciseScoreDTO.releaseDate) : undefined;
                        exerciseScoreDTO.exerciseType = exerciseScoreDTO.exerciseType ? splitCamelCase(exerciseScoreDTO.exerciseType) : undefined;
                    }
                }
                return response;
            }),
        );
    }
}
