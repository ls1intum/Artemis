import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { CompetencyInformation, ExerciseInformation, LectureUnitInformation, StudentMetrics } from 'app/atlas/shared/entities/student-metrics.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { ExerciseCategory, SerializedExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { parseJson } from 'app/foundation/util/json.util';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';

/**
 * Wire shape of an {@link ExerciseInformation} entry before conversion: dates are transported as ISO strings
 * (`start` / `due`) and the type as its raw string discriminator, both of which are converted to their typed
 * counterparts ({@link dayjs.Dayjs} / {@link ExerciseType}) by {@link CourseDashboardService.convertToExerciseInformation}.
 */
type RawExerciseInformation = Omit<ExerciseInformation, 'startDate' | 'dueDate' | 'type' | 'categories' | 'studentAssignedTeamId'> & {
    start: string;
    due?: string;
    type: string;
};

/**
 * Wire shape of a {@link LectureUnitInformation} entry before conversion: `releaseDate` is an ISO string and
 * `type` its raw string discriminator.
 */
type RawLectureUnitInformation = Omit<LectureUnitInformation, 'releaseDate' | 'type'> & {
    releaseDate?: string;
    type: string;
};

/**
 * Wire shape of a {@link CompetencyInformation} entry before conversion: `softDueDate` is transported as an ISO string.
 */
type RawCompetencyInformation = Omit<CompetencyInformation, 'softDueDate'> & {
    softDueDate?: string;
};

@Injectable({ providedIn: 'root' })
export class CourseDashboardService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/atlas/metrics';

    getCourseMetricsForUser(courseId: number): Observable<HttpResponse<StudentMetrics>> {
        return this.http.get<StudentMetrics>(`${this.resourceUrl}/courses/${courseId}/student`, { observe: 'response' }).pipe(
            map((response) => {
                if (response.body) {
                    if (response.body.exerciseMetrics && response.body.exerciseMetrics.exerciseInformation) {
                        // The network payload transports the raw wire shape (see RawExerciseInformation) even though the model type is already the converted one.
                        response.body.exerciseMetrics.exerciseInformation = this.convertToExerciseInformation(
                            response.body.exerciseMetrics.exerciseInformation as Record<string, unknown>,
                            response.body.exerciseMetrics?.categories ?? {},
                            response.body.exerciseMetrics.teamId,
                        );
                    }
                    if (response.body.lectureUnitStudentMetricsDTO && response.body.lectureUnitStudentMetricsDTO.lectureUnitInformation) {
                        response.body.lectureUnitStudentMetricsDTO.lectureUnitInformation = this.convertToLectureUnitInformation(
                            response.body.lectureUnitStudentMetricsDTO.lectureUnitInformation as Record<string, unknown>,
                        );
                    }
                    if (response.body.competencyMetrics && response.body.competencyMetrics.competencyInformation) {
                        response.body.competencyMetrics.competencyInformation = this.convertToCompetencyInformation(
                            response.body.competencyMetrics.competencyInformation as Record<string, unknown>,
                        );
                    }
                }
                return response;
            }),
        );
    }

    private convertToExerciseInformation(
        exerciseInformation: Record<string, unknown>,
        categories: { [key: number]: (string | null)[] },
        teamId?: { [key: number]: number },
    ): { [key: string]: ExerciseInformation } {
        return Object.keys(exerciseInformation).reduce(
            (acc, key) => {
                const exerciseCategories =
                    categories[Number(key)]
                        ?.filter((category): category is string => category !== null)
                        .map((category) => {
                            const categoryObj = parseJson<SerializedExerciseCategory>(category);
                            return new ExerciseCategory(categoryObj.category, categoryObj.color);
                        }) || [];
                const exercise = exerciseInformation[key] as RawExerciseInformation;
                acc[key] = {
                    ...exercise,
                    startDate: dayjs(exercise.start),
                    dueDate: exercise.due ? dayjs(exercise.due) : undefined,
                    type: this.mapToExerciseType(exercise.type),
                    categories: exerciseCategories,
                    studentAssignedTeamId: teamId ? teamId?.[Number(key)] : undefined,
                };
                return acc;
            },
            {} as { [key: string]: ExerciseInformation },
        );
    }

    private convertToLectureUnitInformation(lectureUnitInformation: Record<string, unknown>): { [key: string]: LectureUnitInformation } {
        return Object.keys(lectureUnitInformation).reduce(
            (acc, key) => {
                const lectureUnit = lectureUnitInformation[key] as RawLectureUnitInformation;
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

    private convertToCompetencyInformation(competencyInformation: Record<string, unknown>): { [key: string]: CompetencyInformation } {
        return Object.keys(competencyInformation).reduce(
            (acc, key) => {
                const competency = competencyInformation[key] as RawCompetencyInformation;
                acc[key] = {
                    ...competency,
                    softDueDate: competency.softDueDate ? dayjs(competency.softDueDate) : undefined,
                };
                return acc;
            },
            {} as { [key: string]: CompetencyInformation },
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
            case LectureUnitType.ATTACHMENT_VIDEO:
                return LectureUnitType.ATTACHMENT_VIDEO;
            case LectureUnitType.EXERCISE:
                return LectureUnitType.EXERCISE;
            case LectureUnitType.TEXT:
                return LectureUnitType.TEXT;
            case LectureUnitType.ONLINE:
                return LectureUnitType.ONLINE;
            default:
                throw new Error(`Unknown lecture unit type: ${type}`);
        }
    }
}
