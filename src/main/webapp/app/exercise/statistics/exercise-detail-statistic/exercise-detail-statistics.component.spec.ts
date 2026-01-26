import { expect } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { ExerciseDetailStatisticsComponent } from 'app/exercise/statistics/exercise-detail-statistic/exercise-detail-statistics.component';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { DoughnutChartComponent } from 'app/exercise/statistics/doughnut-chart/doughnut-chart.component';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExerciseDetailStatisticsComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<ExerciseDetailStatisticsComponent>;
    let component: ExerciseDetailStatisticsComponent;

    const exercise = {
        id: 1,
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

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockPipe(ArtemisTranslatePipe), MockRouterLinkDirective, MockComponent(DoughnutChartComponent), ExerciseDetailStatisticsComponent],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: TranslateService, useClass: MockTranslateService },
                LocalStorageService,
                SessionStorageService,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseDetailStatisticsComponent);
                component = fixture.componentInstance;
            });
    });

    beforeEach(() => {
        component.exercise = exercise;
        component.doughnutStats = exerciseStatistics;
    });

    it('should initialize chart data', () => {
        fixture.detectChanges();
        expect(component.doughnutStats.absoluteAveragePoints).toBe(5);
        expect(component.doughnutStats.participationsInPercent).toBe(100);
        expect(component.doughnutStats.resolvedPostsInPercent).toBe(50);
    });
});
