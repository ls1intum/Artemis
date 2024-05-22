import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { ExerciseInformation, StudentMetrics } from 'app/entities/student-metrics.model';
import { ExerciseType } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

@Injectable({ providedIn: 'root' })
export class CourseDashboardService {
    public resourceUrl = 'api/metrics';

    constructor(private http: HttpClient) {}

    getCourseMetricsForUser(courseId: number): Observable<HttpResponse<StudentMetrics>> {
        return this.http.get<StudentMetrics>(`${this.resourceUrl}/course/${courseId}/student`, { observe: 'response' }).pipe(
            map((response) => {
                if (response.body && response.body.exerciseMetrics && response.body.exerciseMetrics.exerciseInformation) {
                    response.body.exerciseMetrics.exerciseInformation = this.convertToExerciseInformation(
                        response.body.exerciseMetrics.exerciseInformation,
                        response.body.exerciseMetrics.categories,
                        response.body.exerciseMetrics.teamId,
                    );
                }
                return response;
            }),
        );
    }

    private convertToExerciseInformation(
        exerciseInformation: { [key: string]: any },
        categories: { [key: string]: any },
        teamId?: { [key: string]: number },
    ): { [key: string]: ExerciseInformation } {
        return Object.keys(exerciseInformation).reduce(
            (acc, key) => {
                const exerciseCategories = (categories[key] as (string | null)[]).flatMap((category) => (category ? (JSON.parse(category) as ExerciseCategory) : []));
                const exercise = exerciseInformation[key];
                acc[key] = {
                    id: exercise.id,
                    title: exercise.title,
                    shortName: exercise.shortName,
                    startDate: dayjs(exercise.start),
                    dueDate: dayjs(exercise.due),
                    maxPoints: exercise.maxPoints,
                    type: this.mapToExerciseType(exercise.type),
                    includedInOverallScore: exercise.includedInOverallScore,
                    exerciseMode: exercise.exerciseMode,
                    categories: exerciseCategories,
                    difficulty: exercise.difficulty,
                    studentAssignedTeamId: teamId ? teamId?.[key] : undefined,
                };
                return acc;
            },
            {} as { [key: string]: ExerciseInformation },
        );
    }

    private mapToExerciseType(type: string): ExerciseType {
        switch (type) {
            case 'de.tum.in.www1.artemis.domain.ProgrammingExercise':
                return ExerciseType.PROGRAMMING;
            case 'de.tum.in.www1.artemis.domain.modeling.ModelingExercise':
                return ExerciseType.MODELING;
            case 'de.tum.in.www1.artemis.domain.quiz.QuizExercise':
                return ExerciseType.QUIZ;
            case 'de.tum.in.www1.artemis.domain.TextExercise':
                return ExerciseType.TEXT;
            case 'de.tum.in.www1.artemis.domain.FileUploadExercise':
                return ExerciseType.FILE_UPLOAD;
            default:
                throw new Error(`Unknown exercise type: ${type}`);
        }
    }
}
