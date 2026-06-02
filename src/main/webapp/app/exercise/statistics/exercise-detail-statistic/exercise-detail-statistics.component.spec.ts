import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it } from 'vitest';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';

describe('ExerciseDetailStatisticsComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseDetailStatisticsComponent>;
    let component: ExerciseDetailStatisticsComponent;

    const exercise = {
        id: 1,
        type: ExerciseType.TEXT,
        course: {
            id: 2,
        },
    } as Exercise;

    const exerciseStatistics = {
        averageScoreOfExercise: 50,
        maxPointsOfExercise: 10,
        absoluteAveragePoints: 5,
        scoreDistribution: [5, 0, 0, 0, 0, 0, 0, 0, 0, 5],
        numberOfExerciseScores: 10,
        numberOfParticipations: 10,
        numberOfStudentsOrTeamsInCourse: 10,
        participationsInPercent: 100,
        numberOfPosts: 4,
        numberOfResolvedPosts: 2,
        resolvedPostsInPercent: 50,
    } as ExerciseManagementStatisticsDto;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseDetailStatisticsComponent],
        })
            .overrideTemplate(ExerciseDetailStatisticsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseDetailStatisticsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('doughnutStats', exerciseStatistics);
        fixture.componentRef.setInput('exerciseType', ExerciseType.TEXT);
    });

    it('should initialize chart data', () => {
        fixture.detectChanges();

        expect(component.doughnutStats()?.absoluteAveragePoints).toBe(5);
        expect(component.doughnutStats()?.participationsInPercent).toBe(100);
        expect(component.doughnutStats()?.resolvedPostsInPercent).toBe(50);
        expect(component.course.id).toBe(2);
    });
});
