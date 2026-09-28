import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { StatisticsService } from 'app/exercise/statistics-graph/service/statistics.service';
import { CourseManagementStatisticsDTO } from 'app/course/shared/entities/course-management-statistics-dto';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

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
