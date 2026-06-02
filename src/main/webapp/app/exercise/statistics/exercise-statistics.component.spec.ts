import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { SpanType } from 'app/exercise/shared/entities/statistics.model';
import { StatisticsService } from 'app/exercise/statistics-graph/service/statistics.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { ExerciseStatisticsComponent } from 'app/exercise/statistics/exercise-statistics.component';
import { ExerciseManagementStatisticsDto } from 'app/exercise/statistics/exercise-management-statistics-dto';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { provideNoopAnimationsForTests } from 'test/helpers/animations';

describe('ExerciseStatisticsComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseStatisticsComponent>;
    let component: ExerciseStatisticsComponent;
    let service: StatisticsService;
    let exerciseService: ExerciseService;

    let statisticsSpy: ReturnType<typeof vi.spyOn>;
    let exerciseSpy: ReturnType<typeof vi.spyOn>;

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
            imports: [ExerciseStatisticsComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ exerciseId: 123 }) },
                provideHttpClient(),
                provideHttpClientTesting(),
                provideNoopAnimationsForTests(),
            ],
        })
            .overrideTemplate(ExerciseStatisticsComponent, '<button id="option3" (click)="onTabChanged(SpanType.MONTH)"></button>')
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseStatisticsComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(StatisticsService);
        exerciseService = TestBed.inject(ExerciseService);
        statisticsSpy = vi.spyOn(service, 'getExerciseStatistics').mockReturnValue(of(exerciseStatistics));
        exerciseSpy = vi.spyOn(exerciseService, 'find').mockReturnValue(of({ body: exercise } as HttpResponse<Exercise>));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();

        expect(component).not.toBeNull();
        expect(statisticsSpy).toHaveBeenCalledTimes(1);
        expect(exerciseSpy).toHaveBeenCalledTimes(1);
        expect(component.exerciseStatistics.participationsInPercent).toBe(100);
        expect(component.exerciseStatistics.resolvedPostsInPercent).toBe(50);
        expect(component.exerciseStatistics.absoluteAveragePoints).toBe(5);
    });

    it('should trigger when tab changed', () => {
        const tabSpy = vi.spyOn(component, 'onTabChanged');
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#option3');
        button.click();

        expect(tabSpy).toHaveBeenCalledTimes(1);
        expect(component.currentSpan).toBe(SpanType.MONTH);
        expect(statisticsSpy).toHaveBeenCalledTimes(1);
        expect(exerciseSpy).toHaveBeenCalledTimes(1);
        expect(component.exerciseStatistics.participationsInPercent).toBe(100);
        expect(component.exerciseStatistics.resolvedPostsInPercent).toBe(50);
        expect(component.exerciseStatistics.absoluteAveragePoints).toBe(5);
    });
});
