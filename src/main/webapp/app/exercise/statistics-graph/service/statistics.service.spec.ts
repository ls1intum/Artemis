import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { StatisticsService } from 'app/exercise/statistics-graph/service/statistics.service';
import { CourseManagementStatisticsDTO } from 'app/course/shared/entities/course-management-statistics-dto';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Graphs, SpanType, StatisticsView } from 'app/exercise/shared/entities/statistics.model';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';

describe('StatisticsService', () => {
    let service: StatisticsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(StatisticsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should load the data of an Artemis wide graph for the chosen span and period', () => {
        let data: number[] | undefined;
        service.getChartData(SpanType.WEEK, -1, Graphs.SUBMISSIONS).subscribe((result) => (data = result));

        httpMock.expectOne('api/admin/management/statistics/data?span=WEEK&periodIndex=-1&graphType=SUBMISSIONS').flush([1, 2, 3]);

        expect(data).toEqual([1, 2, 3]);
    });

    it('should load the data of a graph of one course or exercise', () => {
        let data: number[] | undefined;
        service.getChartDataForContent(SpanType.MONTH, 0, Graphs.ACTIVE_USERS, StatisticsView.COURSE, 7).subscribe((result) => (data = result));

        httpMock.expectOne('api/core/management/statistics/data-for-content?span=MONTH&periodIndex=0&graphType=ACTIVE_USERS&view=COURSE&entityId=7').flush([4, 5]);

        expect(data).toEqual([4, 5]);
    });

    describe('getExerciseStatistics', () => {
        const url = 'api/core/management/statistics/exercise-statistics?exerciseId=3';
        const statistics = (overrides: Partial<ExerciseManagementStatisticsDto>): ExerciseManagementStatisticsDto => ({
            averageScoreOfExercise: 50,
            maxPointsOfExercise: 10,
            scoreDistribution: [],
            numberOfExerciseScores: 3,
            numberOfParticipations: 1,
            numberOfStudentsOrTeamsInCourse: 3,
            numberOfPosts: 4,
            numberOfResolvedPosts: 1,
            ...overrides,
        });

        it('should add the participation rate, the share of resolved posts and the average points', () => {
            let result: ExerciseManagementStatisticsDto | undefined;
            service.getExerciseStatistics(3).subscribe((response) => (result = response));

            httpMock.expectOne(url).flush(statistics({}));

            expect(result?.participationsInPercent).toBe(33.3);
            expect(result?.resolvedPostsInPercent).toBe(25);
            expect(result?.absoluteAveragePoints).toBe(5);
        });

        it('should report no participations and no resolved posts without students or posts', () => {
            let result: ExerciseManagementStatisticsDto | undefined;
            service.getExerciseStatistics(3).subscribe((response) => (result = response));

            httpMock.expectOne(url).flush(statistics({ numberOfStudentsOrTeamsInCourse: 0, numberOfPosts: 0 }));

            expect(result?.participationsInPercent).toBe(0);
            expect(result?.resolvedPostsInPercent).toBe(0);
        });
    });

    describe('getCourseStatistics', () => {
        const url = 'api/core/management/statistics/course-statistics?courseId=1';

        it('should take the average scores of the exercises with their categories and release dates', () => {
            let statistics: CourseManagementStatisticsDTO | undefined;
            service.getCourseStatistics(1).subscribe((result) => (statistics = result));

            httpMock.expectOne(url).flush({
                averageScoreOfCourse: 75,
                averageScoresOfExercises: [
                    {
                        exerciseId: 2,
                        exerciseName: 'Requirements',
                        releaseDate: '2026-10-01T08:00:00Z',
                        averageScore: 75,
                        exerciseType: ExerciseType.TEXT,
                        categories: [{ category: 'Week 1', color: '#6ae8ac' }],
                    },
                ],
            });

            expect(statistics?.averageScoreOfCourse).toBe(75);
            const [exercise] = statistics!.averageScoresOfExercises;
            expect(dayjs.isDayjs(exercise.releaseDate)).toBe(true);
            expect(exercise.releaseDate!.toISOString()).toBe('2026-10-01T08:00:00.000Z');
            expect(exercise.categories).toEqual([new ExerciseCategory('Week 1', '#6ae8ac')]);
        });

        it('should take a course whose exercises have no average score yet, which the server sends without the list', () => {
            let statistics: CourseManagementStatisticsDTO | undefined;
            service.getCourseStatistics(1).subscribe((result) => (statistics = result));

            httpMock.expectOne(url).flush({ averageScoreOfCourse: 0 });

            expect(statistics).toEqual({ averageScoreOfCourse: 0, averageScoresOfExercises: [] });
        });
    });
});
