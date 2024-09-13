import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { CompetencyMetrics, ExerciseInformation, LectureUnitInformation, StudentMetrics } from 'app/entities/student-metrics.model';
import { ExerciseType } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { CompetencyJol } from 'app/entities/competency.model';

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
                    if (response.body.competencyMetrics && response.body.competencyMetrics.competencyInformation) {
                        response.body.competencyMetrics.competencyInformation = this.convertToCompetencyInformation(response.body.competencyMetrics.competencyInformation);
                        response.body.competencyMetrics.currentJolValues = this.filterJolWhereMasteryChanged(response.body.competencyMetrics);
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

    private filterJolWhereMasteryChanged(competencyMetrics: CompetencyMetrics): { [key: string]: CompetencyJol } {
        return Object.fromEntries(
            Object.entries(competencyMetrics.currentJolValues ?? {}).filter(([key, value]) => {
                const progress = competencyMetrics?.progress?.[Number(key)] ?? 0;
                const confidence = competencyMetrics?.confidence?.[Number(key)] ?? 1;
                return value.competencyProgress === progress && value.competencyConfidence === confidence;
            }),
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

    private mapToExerciseType(type: string): ExerciseType {
        switch (type) {
            case 'de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise':
                return ExerciseType.PROGRAMMING;
            case 'de.tum.cit.aet.artemis.modeling.domain.ModelingExercise':
                return ExerciseType.MODELING;
            case 'de.tum.cit.aet.artemis.quiz.domain.QuizExercise':
                return ExerciseType.QUIZ;
            case 'de.tum.cit.aet.artemis.text.domain.TextExercise':
                return ExerciseType.TEXT;
            case 'de.tum.cit.aet.artemis.file.domain.FileUploadExercise':
                return ExerciseType.FILE_UPLOAD;
            default:
                throw new Error(`Unknown exercise type: ${type}`);
        }
    }

    private mapToLectureUnitType(type: string): LectureUnitType {
        switch (type) {
            case 'de.tum.cit.aet.artemis.domain.lecture.AttachmentUnit':
                return LectureUnitType.ATTACHMENT;
            case 'de.tum.cit.aet.artemis.domain.lecture.ExerciseUnit':
                return LectureUnitType.EXERCISE;
            case 'de.tum.cit.aet.artemis.domain.lecture.TextUnit':
                return LectureUnitType.TEXT;
            case 'de.tum.cit.aet.artemis.domain.lecture.VideoUnit':
                return LectureUnitType.VIDEO;
            case 'de.tum.cit.aet.artemis.domain.lecture.OnlineUnit':
                return LectureUnitType.ONLINE;
            default:
                throw new Error(`Unknown lecture unit type: ${type}`);
        }
    }
}
