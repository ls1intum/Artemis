import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';
import { CourseManagementStatisticsDTO } from 'app/course/manage/course-management-statistics-dto';
import { ExerciseManagementStatisticsDto } from 'app/exercises/shared/statistics/exercise-management-statistics-dto';
import { map } from 'rxjs/operators';
import { round } from 'app/shared/util/utils';
import { convertDateFromServer } from 'app/utils/date.utils';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

@Injectable({ providedIn: 'root' })
export class StatisticsService {
    private resourceUrl = SERVER_API_URL + 'api/management/statistics/';

    constructor(private http: HttpClient) {}

    /**
     * Sends a GET request to retrieve the data for a graph based on the graphType in the last *span* days and the given period
     */
    getChartData(span: SpanType, periodIndex: number, graphType: Graphs): Observable<number[]> {
        const params = new HttpParams()
            .set('span', '' + span)
            .set('periodIndex', '' + periodIndex)
            .set('graphType', '' + graphType);
        return this.http.get<number[]>(`${this.resourceUrl}data`, { params });
    }

    /**
     * Sends a GET request to retrieve the data for a graph based on the graphType in the last *span* days, the given period and the id of the entity (e.g. course, exercise)
     */
    getChartDataForContent(span: SpanType, periodIndex: number, graphType: Graphs, view: StatisticsView, entityId: number): Observable<number[]> {
        const params = new HttpParams()
            .set('span', '' + span)
            .set('periodIndex', '' + periodIndex)
            .set('graphType', '' + graphType)
            .set('view', '' + view)
            .set('entityId', '' + entityId);
        return this.http.get<number[]>(`${this.resourceUrl}data-for-content`, { params });
    }

    /**
     * Sends a GET request to retrieve data needed for the course statistics
     */
    getCourseStatistics(courseId: number): Observable<CourseManagementStatisticsDTO> {
        const params = new HttpParams().set('courseId', '' + courseId);
        return this.http.get<CourseManagementStatisticsDTO>(`${this.resourceUrl}course-statistics`, { params }).pipe(
            map((res: CourseManagementStatisticsDTO) => {
                StatisticsService.convertExerciseCategoriesOfrCourseManagementStatisticsFromServer(res);
                return StatisticsService.convertCourseManagementStatisticDatesFromServer(res);
            }),
        );
    }

    /**
     * Sends a GET request to retrieve data needed for the exercise statistics
     */
    getExerciseStatistics(exerciseId: number): Observable<ExerciseManagementStatisticsDto> {
        const params = new HttpParams().set('exerciseId', '' + exerciseId);
        return this.http
            .get<ExerciseManagementStatisticsDto>(`${this.resourceUrl}exercise-statistics`, { params })
            .pipe(map((res: ExerciseManagementStatisticsDto) => StatisticsService.calculatePercentagesForExerciseStatistics(res)));
    }

    private static calculatePercentagesForExerciseStatistics(stats: ExerciseManagementStatisticsDto): ExerciseManagementStatisticsDto {
        stats.participationsInPercent = stats.numberOfStudentsOrTeamsInCourse > 0 ? round((stats.numberOfParticipations / stats.numberOfStudentsOrTeamsInCourse) * 100, 1) : 0;
        stats.resolvedPostsInPercent = stats.numberOfPosts > 0 ? round((stats.numberOfResolvedPosts / stats.numberOfPosts) * 100, 1) : 0;
        stats.absoluteAveragePoints = round((stats.averageScoreOfExercise * stats.maxPointsOfExercise) / 100, 1);
        return stats;
    }

    private static convertCourseManagementStatisticDatesFromServer(dto: CourseManagementStatisticsDTO): CourseManagementStatisticsDTO {
        dto.averageScoresOfExercises.forEach((averageScores) => {
            averageScores.releaseDate = convertDateFromServer(averageScores.releaseDate);
        });
        return dto;
    }

    private static convertExerciseCategoriesOfrCourseManagementStatisticsFromServer(res: CourseManagementStatisticsDTO): CourseManagementStatisticsDTO {
        res.averageScoresOfExercises.forEach((avgScoresOfExercise) => {
            avgScoresOfExercise.categories = avgScoresOfExercise.categories?.map((category) => JSON.parse(category as string) as ExerciseCategory);
        });
        return res;
    }
}
