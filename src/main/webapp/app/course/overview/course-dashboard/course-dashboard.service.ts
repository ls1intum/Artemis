import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { ExerciseInformation, LectureUnitInformation, StudentMetrics } from 'app/atlas/shared/entities/student-metrics.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';

@Injectable({ providedIn: 'root' })
export class CourseDashboardService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/atlas/metrics';

    getCourseMetricsForUser(courseId: number): Observable<HttpResponse<StudentMetrics>> {
        return this.http.get<StudentMetrics>(`${this.resourceUrl}/courses/${courseId}/student`, { observe: 'response' }).pipe(
            map((response) => {
                if (response.body) {
                    if (response.body.exerciseMetrics && response.body.exerciseMetrics.exerciseInformation) {
                        response.body.exerciseMetrics.exerciseInformation = this.convertToExerciseInformation(
                            response.body.exerciseMetrics.exerciseInformation,
                            response.body.exerciseMetrics?.categories ?? {},
                            response.body.exerciseMetrics.teamId,
                        );
                    }
                    if (response.body.lectureUnitStudentMetricsDTO && response.body.lectureUnitStudentMetricsDTO.lectureUnitInformation) {
                        response.body.lectureUnitStudentMetricsDTO.lectureUnitInformation = this.convertToLectureUnitInformation(
                            response.body.lectureUnitStudentMetricsDTO.lectureUnitInformation,
                        );
                    }
                    if (response.body.competencyMetrics && response.body.competencyMetrics.competencyInformation) {
                        response.body.competencyMetrics.competencyInformation = this.convertToCompetencyInformation(response.body.competencyMetrics.competencyInformation);
                    }
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
                const exerciseCategories = categories[key]?.map((category: string) => JSON.parse(category) as ExerciseCategory) || [];
                const exercise = exerciseInformation[key];
                acc[key] = {
                    ...exercise,
                    startDate: dayjs(exercise.start),
                    dueDate: exercise.due ? dayjs(exercise.due) : undefined,
                    type: this.mapToExerciseType(exercise.type),
                    categories: exerciseCategories,
                    studentAssignedTeamId: teamId ? teamId?.[key] : undefined,
                };
                return acc;
            },
            {} as { [key: string]: ExerciseInformation },
        );
    }

    private convertToLectureUnitInformation(lectureUnitInformation: { [key: string]: any }): { [key: string]: LectureUnitInformation } {
        return Object.keys(lectureUnitInformation).reduce(
            (acc, key) => {
                const lectureUnit = lectureUnitInformation[key];
                acc[key] = {
                    ...lectureUnit,
                    releaseDate: dayjs(lectureUnit.releaseDate),
                    type: this.mapToLectureUnitType(lectureUnit.type),
                };
                return acc;
            },
            {} as { [key: string]: LectureUnitInformation },
        );
    }

    private convertToCompetencyInformation(competencyInformation: { [key: string]: any }): { [key: string]: any } {
        return Object.keys(competencyInformation).reduce(
            (acc, key) => {
                const competency = competencyInformation[key];
                acc[key] = {
                    ...competency,
                    softDueDate: competency.softDueDate ? dayjs(competency.softDueDate) : undefined,
                };
                return acc;
            },
            {} as { [key: string]: any },
        );
    }

    // Validating identity-narrower: the raw string from the network already matches the typed enum's value
    // (server-side ExerciseType uses @JsonValue with these exact discriminators), so the switch is here
    // purely to reject unknown values fail-fast at the network boundary rather than letting them propagate.
    private mapToExerciseType(type: string): ExerciseType {
        // Discriminator values match the server-side ExerciseType enum (@JsonValue) and the entity's @JsonSubTypes names.
        switch (type) {
            case 'programming':
                return ExerciseType.PROGRAMMING;
            case 'modeling':
                return ExerciseType.MODELING;
            case 'quiz':
                return ExerciseType.QUIZ;
            case 'text':
                return ExerciseType.TEXT;
            case 'file-upload':
                return ExerciseType.FILE_UPLOAD;
            default:
                throw new Error(`Unknown exercise type: ${type}`);
        }
    }

    // Validating identity-narrower: see mapToExerciseType for the rationale.
    private mapToLectureUnitType(type: string): LectureUnitType {
        // Discriminator values match the server-side LectureUnitType enum (@JsonValue) and the entity's @JsonSubTypes names.
        switch (type) {
            case 'attachment':
                return LectureUnitType.ATTACHMENT_VIDEO;
            case 'exercise':
                return LectureUnitType.EXERCISE;
            case 'text':
                return LectureUnitType.TEXT;
            case 'online':
                return LectureUnitType.ONLINE;
            default:
                throw new Error(`Unknown lecture unit type: ${type}`);
        }
    }
}
