import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { ExerciseInformation, LectureUnitInformation, StudentMetrics } from 'app/entities/student-metrics.model';
import { ExerciseType } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';

@Injectable({ providedIn: 'root' })
export class CourseDashboardService {
    public resourceUrl = 'api/metrics';

    constructor(private http: HttpClient) {}

    getCourseMetricsForUser(courseId: number): Observable<HttpResponse<StudentMetrics>> {
        return this.http.get<StudentMetrics>(`${this.resourceUrl}/course/${courseId}/student`, { observe: 'response' }).pipe(
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
                    allowOnlineEditor: exercise.allowOnlineEditor,
                    allowOfflineIde: exercise.allowOfflineIde,
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
                    id: lectureUnit.id,
                    lectureId: lectureUnit.lectureId,
                    name: lectureUnit.name,
                    releaseDate: dayjs(lectureUnit.releaseDate),
                    type: this.mapToLectureUnitType(lectureUnit.type),
                };
                return acc;
            },
            {} as { [key: string]: LectureUnitInformation },
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

    private mapToLectureUnitType(type: string): LectureUnitType {
        switch (type) {
            case 'de.tum.in.www1.artemis.domain.lecture.AttachmentUnit':
                return LectureUnitType.ATTACHMENT;
            case 'de.tum.in.www1.artemis.domain.lecture.ExerciseUnit':
                return LectureUnitType.EXERCISE;
            case 'de.tum.in.www1.artemis.domain.lecture.TextUnit':
                return LectureUnitType.TEXT;
            case 'de.tum.in.www1.artemis.domain.lecture.VideoUnit':
                return LectureUnitType.VIDEO;
            case 'de.tum.in.www1.artemis.domain.lecture.OnlineUnit':
                return LectureUnitType.ONLINE;
            default:
                throw new Error(`Unknown lecture unit type: ${type}`);
        }
    }
}
