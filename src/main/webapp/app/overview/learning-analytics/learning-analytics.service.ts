import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Exercise } from 'app/entities/exercise.model';
import * as moment from 'moment';

/**
 *  The server will always send all the properties and they are never null
 *
 */
export class ExerciseScoresDTO {
    public exercise: Exercise;
    public scoreOfStudent: number;
    public averageScoreAchieved: number;
    public maxScoreAchieved: number;
}

@Injectable({ providedIn: 'root' })
export class LearningAnalyticsService {
    public resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient) {}

    getCourseExerciseScores(courseId: number): Observable<HttpResponse<ExerciseScoresDTO[]>> {
        return this.http
            .get<ExerciseScoresDTO[]>(`${this.resourceUrl}/courses/${courseId}/analytics/exercise-scores`, { observe: 'response' })
            .map((response: HttpResponse<ExerciseScoresDTO[]>) => {
                if (response.body) {
                    for (const exerciseScoreDTO of response.body) {
                        exerciseScoreDTO.exercise.releaseDate = exerciseScoreDTO.exercise.releaseDate ? moment(exerciseScoreDTO.exercise.releaseDate) : undefined;
                        exerciseScoreDTO.exercise.dueDate = exerciseScoreDTO.exercise.dueDate ? moment(exerciseScoreDTO.exercise.dueDate) : undefined;
                        exerciseScoreDTO.exercise.assessmentDueDate = exerciseScoreDTO.exercise.assessmentDueDate ? moment(exerciseScoreDTO.exercise.assessmentDueDate) : undefined;
                    }
                }
                return response;
            });
    }
}
